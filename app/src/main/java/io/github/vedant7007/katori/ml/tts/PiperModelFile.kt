package io.github.vedant7007.katori.ml.tts

import java.io.File
import java.io.RandomAccessFile

/**
 * Reads the metadata sherpa-onnx will look for in a voice file, so a voice without it is refused
 * with a sentence instead of a dead process.
 *
 * sherpa-onnx reads `sample_rate`, `n_speakers` and friends from the ONNX metadata, and when a key
 * is missing it logs one line and calls `_Exit(-1)` (`csrc/macros.h`, SHERPA_ONNX_READ_META_DATA).
 * On Android that is the app process gone, with nothing for the arbiter to catch and turn into
 * MODEL_LOAD_FAILED. The file rhasspy/piper-voices publishes has no metadata at all, so the
 * unstamped download is exactly the file that would do it. This check runs first.
 *
 * An ONNX file is a protobuf `ModelProto`. Metadata is field 14, a repeated message of
 * (key = field 1, value = field 2). Reading it needs only the top-level field walk; the graph,
 * tens of megabytes, is seeked over. Mirrors `read_metadata` in `stamp_piper_voice.py`.
 */
object PiperModelFile {

    private const val METADATA_FIELD = 14

    fun metadata(file: File): Map<String, String> = RandomAccessFile(file, "r").use { raf ->
        val found = LinkedHashMap<String, String>()
        val end = raf.length()
        while (raf.filePointer < end) {
            val tag = raf.varint()
            val number = (tag shr 3).toInt()
            when (val wire = (tag and 7).toInt()) {
                0 -> raf.varint()
                1 -> raf.seek(raf.filePointer + 8)
                5 -> raf.seek(raf.filePointer + 4)
                2 -> {
                    val length = raf.varint()
                    if (number == METADATA_FIELD) {
                        val bytes = ByteArray(length.toInt()).also { raf.readFully(it) }
                        entry(bytes)?.let { (k, v) -> found[k] = v }
                    } else {
                        raf.seek(raf.filePointer + length)
                    }
                }
                else -> throw IllegalArgumentException("$file: wire type $wire at byte ${raf.filePointer}; not an ONNX file")
            }
        }
        found
    }

    private fun RandomAccessFile.varint(): Long {
        var shift = 0
        var n = 0L
        while (true) {
            val b = readByte().toInt()
            n = n or ((b and 0x7F).toLong() shl shift)
            if (b and 0x80 == 0) return n
            shift += 7
        }
    }

    /** One StringStringEntryProto. */
    private fun entry(bytes: ByteArray): Pair<String, String>? {
        var key: String? = null
        var value: String? = null
        var i = 0
        fun varint(): Int {
            var shift = 0
            var n = 0
            while (true) {
                val b = bytes[i++].toInt()
                n = n or ((b and 0x7F) shl shift)
                if (b and 0x80 == 0) return n
                shift += 7
            }
        }
        while (i < bytes.size) {
            val tag = varint()
            val length = varint()
            val text = String(bytes, i, length, Charsets.UTF_8)
            i += length
            when (tag shr 3) {
                1 -> key = text
                2 -> value = text
            }
        }
        return key?.let { it to (value ?: "") }
    }
}
