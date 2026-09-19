package io.github.vedant7007.katori.domain

/**
 * The three things [DefaultModelArbiter] needs from outside itself, behind seams.
 *
 * Same reasoning as `FoodDbSource` and `LlamaRuntime`, which both earned it: everything worth
 * testing in the arbiter is admission arithmetic, eviction order and lease bookkeeping, and none
 * of that involves Android or a gigabyte of weights. Behind these three interfaces it all runs as
 * a plain JVM test. What is left on the other side is thin enough to read.
 */

/**
 * What this device says about its own memory, read at the moment it is asked.
 *
 * Every figure here is MEASURED on the device. Nothing is keyed off a chipset name: the primary
 * test device is a MediaTek MT6835 with no Hexagon NPU, and a Snapdragon-keyed table would
 * misclassify the phone the app is developed on (spec 11.3).
 */
interface DeviceMemory {
    /** Total physical RAM. */
    fun totalBytes(): Long

    /** Free RAM right now. Moves constantly; only meaningful at the instant it is read. */
    fun availableBytes(): Long

    /**
     * The level at which the system starts killing background processes. Read from the platform
     * rather than assumed, because it differs by device and by OEM.
     */
    fun lowMemoryThresholdBytes(): Long

    /**
     * Proportional set size of this process.
     *
     * OVER-COUNTS MEMORY-MAPPED MODEL WEIGHTS. A GGUF is mapped, not read, so most of it is
     * file-backed and the kernel can drop those pages under pressure. PSS counts them anyway. So
     * this figure is the right one for observing what a model COSTS and the wrong one for deciding
     * what the device can SPARE, which is exactly why [ModelArbiter.canCoReside] admits the set and
     * looks rather than doing arithmetic on estimates.
     */
    fun processPssBytes(): Long
}

/** Loads and unloads the actual model files. The only part that knows about native runtimes. */
interface ModelLoader {
    /**
     * Load [handle] and return its runtime-specific session or pointer.
     *
     * Blocking and slow, seconds for an LLM. Called off the main thread by the arbiter. Throws if
     * the file is missing or fails to initialise; the arbiter turns that into MODEL_LOAD_FAILED
     * rather than letting it escape.
     */
    suspend fun load(handle: ModelHandle): Any

    /** Release native memory. Must tolerate being called once and only once per loaded object. */
    fun unload(handle: ModelHandle, native: Any)
}

/**
 * An APPEND-ONLY record of every memory measurement this app has taken on this device.
 *
 * WHY APPEND-ONLY. `0013` records co-residency peak moving from 1,482 MB to 2,230 MB across a
 * build change that only altered which CPU instructions were used, with no explanation
 * established. A log that each run overwrites hides exactly that: the trend is the finding, and a
 * single latest figure cannot show a trend. sherpa-onnx and Piper wrappers are still to come and
 * will move it again.
 *
 * One row per measurement, never edited, never truncated.
 */
interface MeasurementLog {
    fun append(row: MeasurementRow)
    fun rows(): List<MeasurementRow>
}

/**
 * One measurement. Deliberately flat and dumb: it is written once and read by a person comparing
 * it with the rows above it.
 *
 * [buildTag] is what makes the trajectory legible. A peak that moves between two rows with the
 * same tag is noise on the device; a peak that moves between two different tags is something the
 * build did, which is the case `0013` is open on.
 */
data class MeasurementRow(
    val atEpochMs: Long,
    val buildTag: String,
    val kind: Kind,
    val handleIds: List<String>,
    val measuredPeakBytes: Long,
    val ceilingBytes: Long,
    val totalRamBytes: Long,
    val fits: Boolean,
) {
    enum class Kind {
        /** The ceiling was calibrated for this device. */
        CEILING_CALIBRATION,

        /** A set of models was admitted together and its real footprint observed. */
        CO_RESIDENCY,
    }

    /** One line, stable field order, so two rows can be read against each other by eye. */
    fun toLine(): String = listOf(
        atEpochMs.toString(),
        buildTag,
        kind.name,
        handleIds.joinToString("+").ifEmpty { "-" },
        measuredPeakBytes.toString(),
        ceilingBytes.toString(),
        totalRamBytes.toString(),
        if (fits) "FITS" else "DOES_NOT_FIT",
    ).joinToString("\t")

    companion object {
        fun parse(line: String): MeasurementRow? {
            val f = line.split('\t')
            if (f.size != 8) return null
            return runCatching {
                MeasurementRow(
                    atEpochMs = f[0].toLong(),
                    buildTag = f[1],
                    kind = Kind.valueOf(f[2]),
                    handleIds = if (f[3] == "-") emptyList() else f[3].split('+'),
                    measuredPeakBytes = f[4].toLong(),
                    ceilingBytes = f[5].toLong(),
                    totalRamBytes = f[6].toLong(),
                    fits = f[7] == "FITS",
                )
            }.getOrNull()
        }
    }
}
