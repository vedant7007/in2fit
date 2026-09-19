package io.github.vedant7007.katori.domain

import io.github.vedant7007.katori.domain.model.Outcome
import io.github.vedant7007.katori.domain.model.UnavailableReason
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * The only thing in this app that loads a model.
 *
 * Read the contract on [ModelArbiter] first; it states the obligations and this file is built
 * around them rather than around convenience.
 *
 * ### The ceiling is calibrated on this device, and never a constant
 *
 * `0001` requires the ceiling to be measured at first run rather than keyed off a device name. The
 * probe printed a provisional "half of device RAM" and said so. This replaces it with a figure
 * derived from what the device itself reports:
 *
 *     ceiling = totalRam - (lowMemoryThreshold * THRESHOLD_HEADROOM) - baselinePss
 *     capped at totalRam * MAX_SHARE
 *
 * Every term but the two named policy constants is measured. The low-memory threshold is the
 * level at which this OEM's system starts killing processes, so leaving several multiples of it
 * free is what keeps this app from being the one that gets killed. The baseline is this process
 * before any model is loaded, so the ceiling describes room for WEIGHTS rather than for the app.
 *
 * On the test device, 20 Sep 2026: total 7,619 MB, threshold 432 MB, baseline about 126 MB gives
 * 6,197 MB, capped by MAX_SHARE to about 4,190 MB. The measured three-model peak was 2,230 MB, so
 * it fits with room. Those figures are VALIDATION, not the constant: the calibration runs on
 * whatever device the app is on and writes its answer to the [MeasurementLog].
 *
 * ### Why the fit check is a measurement and not arithmetic
 *
 * [canCoReside] admits the set and looks, because [ModelHandle.estimatedResidentBytes] is a
 * manifest figure and manifest figures are what that call exists to check. `0013` is open on a
 * 750 MB move in the observed peak across a build change that should not have cost memory, which
 * is the reason every measurement here is appended as a row rather than replacing the last one.
 *
 * ### Known ceiling of this implementation
 *
 * ponytail: one mutex over all state, held across the model load. A load is seconds, so a second
 * caller arriving mid-load waits rather than failing fast, which is not what the contract's
 * deadlock note asks for. It is correct, it is simple, and a round trip holds its models for its
 * whole duration anyway, so contention is rare by design. Split into a per-handle load latch if a
 * real caller is ever observed waiting.
 */
class DefaultModelArbiter(
    private val memory: DeviceMemory,
    private val loader: ModelLoader,
    private val log: MeasurementLog,
    private val buildTag: String,
    private val clockMs: () -> Long = System::currentTimeMillis,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ModelArbiter {

    private class Entry(
        val handle: ModelHandle,
        val native: Any,
        var pins: Int,
        var lastUsedTick: Long,
    )

    private val lock = Mutex()
    private val resident = mutableMapOf<ModelHandle, Entry>()
    private var tick = 0L
    private var ceiling: Long = -1L

    private val _residency = MutableStateFlow(
        ResidencySnapshot(emptyList(), 0L, 0L, DeviceTier.MID)
    )
    override val residency: StateFlow<ResidencySnapshot> = _residency.asStateFlow()

    // --- ceiling and tier -----------------------------------------------------------------

    override fun memoryCeilingBytes(): Long {
        if (ceiling < 0) ceiling = calibrate()
        return ceiling
    }

    private fun calibrate(): Long {
        val total = memory.totalBytes()
        val threshold = memory.lowMemoryThresholdBytes()
        val baseline = memory.processPssBytes()

        val derived = total - (threshold * THRESHOLD_HEADROOM) - baseline
        val capped = minOf(derived, (total * MAX_SHARE_NUMERATOR) / MAX_SHARE_DENOMINATOR)
        // A device too small for any of this still gets a non-negative ceiling; admission then
        // refuses every model cleanly rather than dividing by a negative and admitting nonsense.
        val result = maxOf(capped, 0L)

        log.append(
            MeasurementRow(
                atEpochMs = clockMs(),
                buildTag = buildTag,
                kind = MeasurementRow.Kind.CEILING_CALIBRATION,
                handleIds = emptyList(),
                measuredPeakBytes = baseline,
                ceilingBytes = result,
                totalRamBytes = total,
                fits = result > 0,
            )
        )
        return result
    }

    /**
     * Tier from measured RAM.
     *
     * INCOMPLETE, AND SAID SO RATHER THAN HIDDEN. The contract asks for RAM plus a short
     * throughput benchmark, because two devices with the same RAM and very different cores are not
     * the same tier, and this phone is the case in point: eight cores of which six are small A55s. The
     * benchmark is not written. Until it is, this classifies on RAM alone and will over-rate a
     * device with plenty of memory and slow cores.
     */
    private fun tier(): DeviceTier {
        val gib = memory.totalBytes() / (1024.0 * 1024.0 * 1024.0)
        return when {
            gib >= 7.0 -> DeviceTier.HIGH
            gib >= 3.5 -> DeviceTier.MID
            else -> DeviceTier.LOW
        }
    }

    // --- leases ----------------------------------------------------------------------------

    override suspend fun <T> withModel(handle: ModelHandle, block: suspend (LoadedModel) -> T): Outcome<T> =
        withModels(listOf(handle)) { loaded -> block(loaded.getValue(handle)) }

    override suspend fun <T> withModels(
        handles: List<ModelHandle>,
        block: suspend (Map<ModelHandle, LoadedModel>) -> T,
    ): Outcome<T> {
        if (handles.isEmpty()) return Outcome.Unavailable(UnavailableReason.INTERNAL_ERROR, "no models requested")

        val wanted = handles.distinct()
        val tooBig = wanted.filter { it.minimumTier.ordinal > tier().ordinal }
        if (tooBig.isNotEmpty()) {
            return Outcome.Unavailable(
                UnavailableReason.HARDWARE_UNSUPPORTED,
                "this device is ${tier()} and ${tooBig.map { it.id }} need a higher tier",
            )
        }

        val admitted: Map<ModelHandle, LoadedModel> = when (val a = admit(wanted)) {
            is Outcome.Ok -> a.value
            is Outcome.Unavailable -> return a
            is Outcome.NotImplemented -> return a
        }

        // try/finally, not a manual release: a block that throws must not leave models pinned for
        // the life of the process, and the exception still propagates afterwards.
        try {
            return Outcome.Ok(block(admitted))
        } finally {
            unpin(wanted)
        }
    }

    /** Atomic: every handle is admitted and pinned, or none is and nothing was loaded. */
    private suspend fun admit(wanted: List<ModelHandle>): Outcome<Map<ModelHandle, LoadedModel>> =
        lock.withLock {
            val missing = wanted.filterNot { resident.containsKey(it) }
            val needed = missing.sumOf { it.estimatedResidentBytes }

            if (!makeRoomFor(needed, keep = wanted)) {
                return@withLock Outcome.Unavailable(
                    UnavailableReason.INSUFFICIENT_MEMORY,
                    "need ${needed} B for ${missing.map { it.id }}; " +
                        "ceiling ${memoryCeilingBytes()} B, pinned ${pinnedBytes()} B",
                )
            }

            // Loaded here, one at a time, and rolled back as a set. A half-acquired lease is the
            // failure withModels exists to prevent, so a failure on the third model releases the
            // first two rather than leaving them resident and unreferenced.
            val loadedNow = mutableListOf<ModelHandle>()
            for (h in missing) {
                val native = try {
                    withContext(dispatcher) { loader.load(h) }
                } catch (e: Throwable) {
                    loadedNow.forEach { drop(it) }
                    return@withLock Outcome.Unavailable(
                        UnavailableReason.MODEL_LOAD_FAILED,
                        "${h.id} at ${h.relativePath}: ${e.message}",
                    )
                }
                resident[h] = Entry(h, native, pins = 0, lastUsedTick = ++tick)
                loadedNow += h
            }

            wanted.forEach { h ->
                val e = resident.getValue(h)
                e.pins++
                e.lastUsedTick = ++tick
            }
            publish()
            Outcome.Ok(wanted.associateWith { Lease(resident.getValue(it)) })
        }

    private suspend fun unpin(handles: List<ModelHandle>) = lock.withLock {
        handles.forEach { h ->
            resident[h]?.let {
                if (it.pins > 0) it.pins--
                it.lastUsedTick = ++tick
            }
        }
        publish()
    }

    /**
     * Evict UNPINNED residents in least-recently-used order until [needed] more bytes fit.
     *
     * Returns false without evicting anything further once every unpinned resident is gone and it
     * still does not fit. The caller then fails cleanly. It never waits for a pin to release:
     * waiting is how two callers holding models that cannot co-fit hang each other, and the
     * contract closes that door explicitly.
     */
    private fun makeRoomFor(needed: Long, keep: List<ModelHandle>): Boolean {
        val ceilingBytes = memoryCeilingBytes()
        if (usedBytes() + needed <= ceilingBytes) return true

        val evictable = resident.values
            .filter { it.pins == 0 && it.handle !in keep }
            .sortedBy { it.lastUsedTick }

        for (victim in evictable) {
            drop(victim.handle)
            if (usedBytes() + needed <= ceilingBytes) return true
        }
        return usedBytes() + needed <= ceilingBytes
    }

    private fun drop(handle: ModelHandle) {
        val e = resident.remove(handle) ?: return
        loader.unload(handle, e.native)
    }

    // --- the rest of the contract ----------------------------------------------------------

    override suspend fun preload(handle: ModelHandle): Outcome<Unit> {
        val r = withModels(listOf(handle)) { }
        // withModels unpins on exit, which is exactly what preload wants: resident but evictable.
        return when (r) {
            is Outcome.Ok -> Outcome.Ok(Unit)
            is Outcome.Unavailable -> r
            is Outcome.NotImplemented -> r
        }
    }

    override suspend fun release(handle: ModelHandle) = lock.withLock {
        val e = resident[handle] ?: return@withLock
        // A pinned model is never released out from under its lease, whatever the caller asked.
        if (e.pins > 0) return@withLock
        drop(handle)
        publish()
    }

    override suspend fun releaseAllUnpinned() = lock.withLock {
        resident.values.filter { it.pins == 0 }.map { it.handle }.forEach { drop(it) }
        publish()
    }

    override suspend fun canCoReside(handles: List<ModelHandle>): Outcome<CoResidencyReport> {
        val wanted = handles.distinct()
        // Start from a clean slate so the peak describes THIS set and not whatever was warm.
        releaseAllUnpinned()
        val before = memory.processPssBytes()

        var peak = before
        val outcome = withModels(wanted) {
            peak = maxOf(peak, memory.processPssBytes())
        }

        val ceilingBytes = memoryCeilingBytes()
        val fits = outcome is Outcome.Ok && peak <= ceilingBytes

        log.append(
            MeasurementRow(
                atEpochMs = clockMs(),
                buildTag = buildTag,
                kind = MeasurementRow.Kind.CO_RESIDENCY,
                handleIds = wanted.map { it.id },
                measuredPeakBytes = peak,
                ceilingBytes = ceilingBytes,
                totalRamBytes = memory.totalBytes(),
                fits = fits,
            )
        )

        if (outcome is Outcome.Unavailable) {
            // Could not even admit the set. That is a real answer to the question, and it is NOT
            // the same as a peak that overshot, so the report says which models would have to go.
            return Outcome.Ok(
                CoResidencyReport(
                    handles = wanted,
                    fits = false,
                    measuredPeakBytes = peak,
                    ceilingBytes = ceilingBytes,
                    wouldNeedToDrop = dropCandidates(wanted, ceilingBytes),
                )
            )
        }

        return Outcome.Ok(
            CoResidencyReport(
                handles = wanted,
                fits = fits,
                measuredPeakBytes = peak,
                ceilingBytes = ceilingBytes,
                wouldNeedToDrop = if (fits) emptyList() else dropCandidates(wanted, ceilingBytes),
            )
        )
    }

    /** Largest-first, because dropping one big model beats dropping three small ones. */
    private fun dropCandidates(wanted: List<ModelHandle>, ceilingBytes: Long): List<ModelHandle> {
        val drop = mutableListOf<ModelHandle>()
        var total = wanted.sumOf { it.estimatedResidentBytes }
        for (h in wanted.sortedByDescending { it.estimatedResidentBytes }) {
            if (total <= ceilingBytes) break
            drop += h
            total -= h.estimatedResidentBytes
        }
        return drop
    }

    // --- bookkeeping -------------------------------------------------------------------------

    private fun usedBytes(): Long = resident.keys.sumOf { it.estimatedResidentBytes }

    private fun pinnedBytes(): Long =
        resident.values.filter { it.pins > 0 }.sumOf { it.handle.estimatedResidentBytes }

    private fun publish() {
        _residency.value = ResidencySnapshot(
            resident = resident.values
                .sortedBy { it.lastUsedTick }
                .map { ResidentModel(it.handle, pinned = it.pins > 0, lastUsedTick = it.lastUsedTick) },
            ceilingBytes = memoryCeilingBytes(),
            usedBytes = usedBytes(),
            tier = tier(),
        )
    }

    /**
     * A model handed to a lease block.
     *
     * Invalidated when the arbiter drops it, so using it after the block fails loudly instead of
     * reading freed native memory, which is the crash no Kotlin try/catch would have caught.
     */
    private inner class Lease(private val entry: Entry) : LoadedModel {
        override val handle: ModelHandle get() = entry.handle
        override val native: Any
            get() {
                check(resident[entry.handle] === entry) {
                    "${entry.handle.id} was released; a LoadedModel is only valid inside the " +
                        "withModel block that produced it"
                }
                return entry.native
            }
    }

    private companion object {
        /**
         * How many multiples of the system's own low-memory threshold to leave free.
         *
         * Three, because at one multiple the app is sitting exactly on the line the low-memory
         * killer watches, and this app is the largest thing on the device when a model is loaded,
         * so it is the first one reached for.
         */
        const val THRESHOLD_HEADROOM = 3L

        /** Never take more than this share of the device, whatever the arithmetic above allows. */
        const val MAX_SHARE_NUMERATOR = 55L
        const val MAX_SHARE_DENOMINATOR = 100L
    }
}
