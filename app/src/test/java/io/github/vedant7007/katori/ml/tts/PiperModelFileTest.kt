package io.github.vedant7007.katori.ml.tts

import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * The metadata reader is what stands between a raw piper-voices download and sherpa-onnx's
 * `_Exit(-1)`, so it is checked against bytes built by hand here and, when the stamped voice is
 * on this machine, against the real 63 MB file.
 */
class PiperModelFileTest {

    // --- a protobuf writer small enough to trust -------------------------------------------------

    private fun varint(n: Long): ByteArray {
        val out = ByteArrayOutputStream()
        var v = n
        while (true) {
            val b = (v and 0x7F).toInt()
            v = v ushr 7
            if (v == 0L) { out.write(b); return out.toByteArray() }
            out.write(b or 0x80)
        }
    }

    private fun bytesField(number: Int, data: ByteArray) = varint(((number shl 3) or 2).toLong()) + varint(data.size.toLong()) + data
    private fun stringField(number: Int, s: String) = bytesField(number, s.toByteArray())
    private fun varintField(number: Int, n: Long) = varint((number shl 3).toLong()) + varint(n)
    private fun entry(key: String, value: String) = bytesField(14, stringField(1, key) + stringField(2, value))

    private fun modelProto(vararg meta: Pair<String, String>): ByteArray =
        varintField(1, 9) +                                          // ir_version
            stringField(2, "pytorch") +                              // producer_name
            bytesField(7, ByteArray(100_000) { 0x08 }) +             // a "graph" to seek over
            bytesField(8, varintField(2, 17)) +                      // opset_import
            meta.fold(ByteArray(0)) { acc, (k, v) -> acc + entry(k, v) }

    private fun write(bytes: ByteArray): File = File.createTempFile("voice", ".onnx").apply {
        deleteOnExit()
        writeBytes(bytes)
    }

    // --- the reader ----------------------------------------------------------------------------

    @Test
    fun `reads every metadata entry and nothing else`() {
        val f = write(modelProto("model_type" to "vits", "comment" to "piper", "sample_rate" to "22050"))
        assertEquals(
            mapOf("model_type" to "vits", "comment" to "piper", "sample_rate" to "22050"),
            PiperModelFile.metadata(f),
        )
    }

    @Test
    fun `an unstamped model reads as empty, which is what the loader refuses`() {
        assertEquals(emptyMap<String, String>(), PiperModelFile.metadata(write(modelProto())))
    }

    @Test
    fun `non-ascii values survive`() {
        val f = write(modelProto("language" to "తెలుగు"))
        assertEquals("తెలుగు", PiperModelFile.metadata(f)["language"])
    }

    @Test
    fun `the real stamped Telugu voice, when it is on this machine, reads as sherpa-onnx expects`() {
        val projectDir = System.getProperty("katori.projectDir") ?: return
        val voice = File(projectDir, "data-sources/models/tts/sherpa/te_IN-padmavathi-medium/model.onnx")
        assumeTrue("stamped voice not present at ${voice.path}; run stamp_piper_voice.py", voice.isFile)

        val meta = PiperModelFile.metadata(voice)
        assertEquals("piper", meta["comment"])
        assertEquals("vits", meta["model_type"])
        assertEquals("22050", meta["sample_rate"])
        assertEquals("1", meta["n_speakers"])
        assertEquals("te", meta["voice"])
    }
}
