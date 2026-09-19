package io.github.vedant7007.katori.ml.tts

import android.content.Context
import android.content.res.AssetManager
import java.io.File

/**
 * Puts the shipped `assets/espeak-ng-data` on disk, where espeak-ng can read it.
 *
 * Piper voices phonemise through espeak-ng, and espeak-ng opens its tables with plain file
 * calls, so an asset inside the APK is no use to it. The app ships the directory as an asset
 * (about 1 MB: the phoneme tables plus the English, Telugu and Hindi dictionaries; the other
 * 17 MB of dictionaries in the upstream tarball are for languages this app does not speak) and
 * copies it out once.
 *
 * THE COPY IS KEYED ON THE INSTALLED APK, NOT ON A CONSTANT. `HANDOVER.md` §7 bug 2 was a copy
 * "keyed by version" that was really keyed by a hard-coded filename and would have served a stale
 * database forever. This writes the APK's `lastUpdateTime` next to the copy and re-copies when it
 * differs, so a new build with new data replaces the old data on first use.
 *
 * If the asset directory is not in the APK at all, nothing is copied and nothing is thrown: the
 * loader's own check then reports the missing files as MODEL_LOAD_FAILED with the path, which is
 * the honest state and the one the UI can show.
 */
object EspeakData {

    const val ASSET_DIR = "espeak-ng-data"

    /** What espeak-ng cannot start without. Checked by the loader before sherpa-onnx is called. */
    val REQUIRED_FILES = listOf("phontab", "phonindex", "phondata", "intonations")

    private const val MARKER = ".installed-from-apk"

    fun install(context: Context, target: File): File {
        val assets = context.assets
        if (assets.list(ASSET_DIR).isNullOrEmpty()) return target

        val installed = context.packageManager.getPackageInfo(context.packageName, 0).lastUpdateTime.toString()
        val marker = File(target, MARKER)
        val current = marker.isFile && marker.readText() == installed &&
            REQUIRED_FILES.all { File(target, it).isFile }
        if (current) return target

        target.deleteRecursively()
        copy(assets, ASSET_DIR, target)
        marker.writeText(installed)
        return target
    }

    private fun copy(assets: AssetManager, path: String, into: File) {
        val children = assets.list(path).orEmpty()
        if (children.isEmpty()) {
            // AssetManager.list is empty for a file. espeak-ng-data has no empty directories.
            into.parentFile?.mkdirs()
            assets.open(path).use { src -> into.outputStream().use { dst -> src.copyTo(dst) } }
            return
        }
        into.mkdirs()
        for (child in children) copy(assets, "$path/$child", File(into, child))
    }
}
