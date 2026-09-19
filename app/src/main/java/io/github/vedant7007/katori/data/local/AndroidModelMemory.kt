package io.github.vedant7007.katori.data.local

import android.app.ActivityManager
import android.content.Context
import android.os.Process
import io.github.vedant7007.katori.domain.DeviceMemory
import io.github.vedant7007.katori.domain.MeasurementLog
import io.github.vedant7007.katori.domain.MeasurementRow
import java.io.File

/**
 * [DeviceMemory] from the platform. Every figure is read at the moment it is asked for, because
 * available memory moves constantly and a cached one would be a lie by the time it was used.
 */
class AndroidDeviceMemory(context: Context) : DeviceMemory {

    private val activityManager =
        context.applicationContext.getSystemService(ActivityManager::class.java)

    private fun info() = ActivityManager.MemoryInfo().also { activityManager.getMemoryInfo(it) }

    override fun totalBytes(): Long = info().totalMem
    override fun availableBytes(): Long = info().availMem
    override fun lowMemoryThresholdBytes(): Long = info().threshold

    override fun processPssBytes(): Long =
        activityManager.getProcessMemoryInfo(intArrayOf(Process.myPid()))
            .firstOrNull()?.totalPss?.toLong()?.times(1024L) ?: 0L
}

/**
 * [MeasurementLog] as a plain append-only text file, one row per line.
 *
 * ### Where it lives, and why not in the Room database
 *
 * This is a log, not user data. It is written before anything else is ready, it must survive a
 * schema migration going wrong, and a person needs to be able to pull it off the device and read
 * it against the rows above it without a database viewer. A file does all of that and a table does
 * none of it.
 *
 * ### Where it lives, and why not internal storage
 *
 * The app's external media directory, so `adb pull` can fetch it without root. That directory is
 * wiped when the app is uninstalled, which is a real limitation and is stated rather than hidden:
 * `0012` records that gradle's `connectedAndroidTest` uninstalls on completion and therefore
 * destroys exactly this kind of evidence, which is why the probe is run with `am instrument`
 * instead. Pull the file before uninstalling.
 *
 * NEVER TRUNCATED. `0013` is open on a co-residency peak that moved 750 MB across a build change
 * with no explanation established. A log that each run overwrites cannot show that; the trend is
 * the finding.
 */
class FileMeasurementLog(context: Context) : MeasurementLog {

    private val file: File by lazy {
        val dir = context.applicationContext.externalMediaDirs.firstOrNull()
            ?: context.applicationContext.filesDir
        File(dir, FILE_NAME).also { it.parentFile?.mkdirs() }
    }

    override fun append(row: MeasurementRow) {
        // A measurement that cannot be recorded must not take down the thing being measured, so a
        // write failure is swallowed here. It is the only place in this codebase that swallows one,
        // and it is swallowed because the alternative is an arbiter that refuses to admit a model
        // because its diary is full.
        runCatching { file.appendText(row.toLine() + "\n") }
    }

    override fun rows(): List<MeasurementRow> =
        runCatching { file.readLines().mapNotNull(MeasurementRow::parse) }.getOrDefault(emptyList())

    private companion object {
        const val FILE_NAME = "katori-memory-measurements.tsv"
    }
}
