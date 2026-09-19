package io.github.vedant7007.katori.domain

import io.github.vedant7007.katori.domain.model.Outcome
import kotlinx.coroutines.flow.StateFlow

/**
 * Owns which models are resident in memory. Nothing else loads a model.
 *
 * WHY THIS EXISTS
 * Spec 9.3 says models stay warm rather than reloading per request. Spec 11.3 says the primary test
 * device is mid-range. Those two statements are in tension: an LLM plus ASR plus TTS plus MediaPipe
 * all resident will exhaust memory on an 8 GB phone, and demo beat 3 loads camera, OCR and the LLM
 * together. That is risk 4 and it is the case that will crash on stage. One component has to decide
 * what is resident and what gets evicted, and this is it.
 *
 * THE CENTRAL DESIGN DECISION: LEASES, NOT HANDLES
 * [withModel] hands the caller a loaded model for the duration of a block and takes it back at the
 * end. There is deliberately no `get()` that returns a model reference the caller can keep, because
 * a retained reference is exactly how eviction pulls memory out from under a running inference and
 * produces a native crash that no Kotlin try/catch will catch.
 *
 * While a lease is held the model is PINNED and cannot be evicted.
 *
 * LEASE DURATION: ONE ROUND TRIP, NOT ONE INFERENCE
 * An earlier draft of this contract said a lease covers one inference. That was wrong, and it would
 * have broken beat 1. Meal logging is ASR, then LLM extraction, then TTS confirmation. Three
 * sequential leases leave two gaps in which an unrelated pipeline can trigger eviction, and the
 * reload that follows blows the 3.5 s budget on its own.
 *
 * The rule is therefore: A LEASE SPANS ONE USER-FACING ROUND TRIP, AND EVERY MODEL THAT ROUND TRIP
 * NEEDS IS ACQUIRED ATOMICALLY UP FRONT via [withModels]. The voice round trip holds ASR, LLM and
 * TTS together for its full duration, roughly 3.5 s. Beat 3 holds OCR and the LLM together the same
 * way.
 *
 * What a lease still may NOT span: waiting for user input. A lease that is open while the app waits
 * for someone to read a screen and decide is a lease that starves every other pipeline for an
 * unbounded time. Release, wait, re-acquire.
 *
 * CO-RESIDENCY IS A MEASUREMENT, NOT AN ASSUMPTION
 * Whether ASR, LLM and TTS co-fit at all on the weakest test device is an open question, and it is a
 * stage-1 item. [canCoReside] exists to answer it on real hardware before any UI depends on it. If
 * they do not co-fit, that is a tier-degradation finding to report and design around, for example by
 * dropping spoken confirmation to on-screen confirmation on that tier. It is not a bug to work
 * around by splitting the lease back up, because splitting it is what causes the reload.
 *
 * ADMISSION AND EVICTION
 * - Each [ModelHandle] declares [ModelHandle.estimatedResidentBytes]. Admission is decided against
 *   [memoryCeilingBytes], which is measured on this device at first run, never a constant and never
 *   keyed off a chipset name (spec 11.3).
 * - To admit a model that does not fit, the arbiter evicts UNPINNED residents in least-recently-used
 *   order until it fits.
 * - If it still does not fit once every unpinned resident is gone, the call returns
 *   [Outcome.Unavailable] with [io.github.vedant7007.katori.domain.model.UnavailableReason.INSUFFICIENT_MEMORY].
 *   It does NOT wait, and it does NOT try anyway. Returning a clean failure that the UI can explain
 *   is the entire point; an OOM kill mid-demo is the failure this class exists to prevent.
 *
 * THREADING
 * - Every member is suspending or a flow. Model init is heavy and blocking, so it runs on a
 *   dedicated dispatcher owned by the implementation, never on Dispatchers.Main (spec 9.3: loading
 *   a multi-gigabyte model on the main thread is an immediate ANR).
 * - Debug builds assert that no arbiter call is made from the main thread.
 * - [residency] is observable so the UI can show a real loading state rather than a spinner that
 *   means nothing.
 *
 * DEADLOCK HAZARD, STATED EXPLICITLY
 * Two pipelines holding leases on models that cannot co-fit is the obvious way to hang this design.
 * The contract closes it: a lease request that cannot be satisfied alongside the currently PINNED
 * set fails fast with INSUFFICIENT_MEMORY rather than waiting for a pin to release. Waiting is never
 * correct here, because the thing being waited on may itself be waiting. Callers that need two
 * models at once must request them through [withModels], which admits the whole set atomically or
 * fails, so it cannot half-acquire.
 *
 * FAILURE MODES
 * - Model file missing or corrupt: MODEL_LOAD_FAILED, with the path in `detail`. Not retried in a loop.
 * - Device cannot run this model at all, e.g. the tier does not support it: HARDWARE_UNSUPPORTED.
 * - Block throws: the lease is released before the exception propagates. A leak here pins memory
 *   for the process lifetime, so implementations use try/finally, not manual release.
 * - Cancellation: the lease is released, and an in-flight load is cancelled if the runtime supports
 *   it. A cancelled load must not leave a half-initialised model marked resident.
 */
interface ModelArbiter {

    /** What is resident right now, what is pinned, and how much headroom is left. */
    val residency: StateFlow<ResidencySnapshot>

    /** The measured memory ceiling for models on this device, in bytes. */
    fun memoryCeilingBytes(): Long

    /**
     * Run [block] with [handle] loaded and pinned.
     *
     * The model is guaranteed resident for the duration of [block] and must not be referenced after
     * it returns. Keep the block short: one inference, not a session.
     *
     * @return the block's result wrapped in [Outcome.Ok], or an [Outcome.Unavailable] if the model
     *         could not be admitted. If [block] itself throws, the exception propagates after the
     *         lease is released.
     */
    suspend fun <T> withModel(
        handle: ModelHandle,
        block: suspend (LoadedModel) -> T,
    ): Outcome<T>

    /**
     * Atomically admit several models and run [block] with all of them pinned. Either every model in
     * [handles] is admitted, or none is and the call returns INSUFFICIENT_MEMORY.
     *
     * This is the normal way to acquire models, not the exception. The voice round trip passes ASR,
     * LLM and TTS; beat 3 passes OCR and the LLM. Acquiring one at a time risks the second failing
     * after the first is pinned, and risks eviction in the gap between them.
     */
    suspend fun <T> withModels(
        handles: List<ModelHandle>,
        block: suspend (Map<ModelHandle, LoadedModel>) -> T,
    ): Outcome<T>

    /**
     * Load [handle] now and keep it resident, without holding a lease.
     *
     * Used for demo warm-up (spec 7.4: cold model load is what will make the demo look slow). A
     * preloaded model is resident but UNPINNED, so it can still be evicted under pressure.
     */
    suspend fun preload(handle: ModelHandle): Outcome<Unit>

    /**
     * Drop [handle] from memory if resident and unpinned.
     *
     * A pinned model is not released; the call returns without error and eviction happens when the
     * lease ends. Releasing out from under a lease is never allowed, whatever the caller asks for.
     */
    suspend fun release(handle: ModelHandle)

    /** Evict everything unpinned. Called on onTrimMemory and when the app goes to background. */
    suspend fun releaseAllUnpinned()

    /**
     * Can this set of models be resident at the same time on THIS device?
     *
     * Answers the co-residency question by measurement rather than arithmetic on manifest figures:
     * implementations actually admit the set, record the real footprint, and release. Used as a
     * stage-1 hardware check and at first run to choose tier behaviour.
     *
     * This is the call that decides whether the voice round trip can speak its confirmation or must
     * fall back to showing it. Never inferred from a device name.
     */
    suspend fun canCoReside(handles: List<ModelHandle>): Outcome<CoResidencyReport>
}

/**
 * The measured result of a co-residency check. [measuredPeakBytes] is what was actually observed,
 * not the sum of the manifest estimates, because estimates are what this call exists to check.
 */
data class CoResidencyReport(
    val handles: List<ModelHandle>,
    val fits: Boolean,
    val measuredPeakBytes: Long,
    val ceilingBytes: Long,
    /** When [fits] is false, the models that had to be dropped for the rest to fit. */
    val wouldNeedToDrop: List<ModelHandle>,
)

/** Identity and cost of one model file. Value type, so it is safe as a map key. */
data class ModelHandle(
    /** Stable id, e.g. "asr.whisper-small-hi". Used in logs, residency and the model manifest. */
    val id: String,
    val family: ModelFamily,
    /** Path inside the app's model directory. Resolved by the implementation, not by callers. */
    val relativePath: String,
    /**
     * Expected resident footprint in bytes, from the model manifest.
     *
     * This is a declared figure from the manifest, not a measurement, and it is used only for
     * admission arithmetic. It is never shown to the user as a fact about their device.
     */
    val estimatedResidentBytes: Long,
    /** The minimum device tier that may load this model at all. */
    val minimumTier: DeviceTier,
)

enum class ModelFamily { ASR, TTS, LLM, VISION_OCR, VISION_POSE, VISION_CLASSIFIER }

/**
 * Device capability tier, assigned at first run by MEASUREMENT: available RAM plus a short
 * throughput benchmark. Never keyed off a chipset name, because the primary test device is MediaTek
 * with no Hexagon NPU and a Snapdragon-keyed table would misclassify the device the app is
 * developed on (spec 11.3).
 */
enum class DeviceTier { LOW, MID, HIGH }

/**
 * An admitted, initialised model.
 *
 * Valid only inside the [ModelArbiter.withModel] block that produced it. Implementations mark it
 * invalid on release, and using it afterwards fails loudly rather than reading freed native memory.
 */
interface LoadedModel {
    val handle: ModelHandle
    /** The runtime-specific native pointer or session object, cast by the owning ml/ package only. */
    val native: Any
}

data class ResidencySnapshot(
    val resident: List<ResidentModel>,
    val ceilingBytes: Long,
    val usedBytes: Long,
    val tier: DeviceTier,
)

data class ResidentModel(
    val handle: ModelHandle,
    val pinned: Boolean,
    /** Monotonic tick of last use, for LRU. Not a wall-clock time and not shown to the user. */
    val lastUsedTick: Long,
)
