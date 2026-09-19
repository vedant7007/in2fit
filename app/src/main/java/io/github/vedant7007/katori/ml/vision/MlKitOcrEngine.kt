package io.github.vedant7007.katori.ml.vision

import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import io.github.vedant7007.katori.domain.model.Outcome
import io.github.vedant7007.katori.domain.model.UnavailableReason
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * [OcrEngine] over ML Kit Text Recognition (Latin), on-device.
 *
 * VERIFIED ON HARDWARE, once, in the demo build with the INTERNET permission removed: the realme
 * RMX3780 rendered "Haemoglobin 9.8 g/dL" and ML Kit returned "Haemoglobin 9.8g/dL", one block
 * (`docs/decisions/0004`, `logs/hw-report-run3.txt`). That is the whole basis for trusting this
 * path, and it is a single synthetic line, not a photographed report. Everything this class does
 * beyond that call is untested on a device until the probe in `androidTest/.../ml/vision` runs.
 *
 * ONE [TextBlock] PER ML KIT *LINE*, NOT PER ML KIT BLOCK. ML Kit groups text into blocks by
 * proximity, and on a printed table that follows the columns: a block is "Haemoglobin / PCV /
 * RBC" and the values sit in another block. Row reconstruction needs the finer unit with its own
 * box, and a line is the finest unit that keeps a single baseline. [LabReportExtractor] relies on
 * this granularity.
 *
 * The recogniser client is created per call, which is the exact sequence the hardware probe
 * verified. Holding one open would save its initialisation on every scan but is a lifecycle
 * question nobody has measured; this path has no latency budget yet, so it stays on the verified
 * sequence.
 *
 * ML Kit's own model is not admitted through the ModelArbiter. It is a few megabytes, loaded by
 * Google Play Services' own lifecycle, and closed on every call; the arbiter exists for the
 * gigabyte-class weights that compete for the ceiling. `ModelFamily.VISION_OCR` stays declared
 * for the day that changes.
 */
class MlKitOcrEngine(private val frames: FrameStore) : OcrEngine {

    override suspend fun readText(image: ImageRef): Outcome<RecognisedText> {
        val frame = frames.take(image)
            ?: return Outcome.Unavailable(UnavailableReason.INPUT_NOT_USABLE, "no frame held for ${image.id}")

        val recogniser = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val result: Result<Text> = try {
            suspendCancellableCoroutine { cont ->
                recogniser.process(InputImage.fromBitmap(frame.bitmap, frame.rotationDegrees))
                    .addOnSuccessListener { cont.resume(Result.success(it)) }
                    .addOnFailureListener { cont.resume(Result.failure(it)) }
            }
        } finally {
            recogniser.close()
        }

        val text = result.getOrElse { e ->
            return Outcome.Unavailable(UnavailableReason.INTERNAL_ERROR, "${e::class.java.simpleName}: ${e.message}")
        }

        // A line without a box has no position, and a positionless line would land in the wrong
        // row downstream. ML Kit documents the box as nullable; on the one hardware run it was not.
        val lines = text.textBlocks.asSequence().flatMap { it.lines.asSequence() }.mapNotNull { line ->
            val box = line.boundingBox ?: return@mapNotNull null
            TextBlock(line.text, box.left, box.top, box.right, box.bottom)
        }.toList()

        if (lines.isEmpty()) {
            return Outcome.Unavailable(UnavailableReason.INPUT_NOT_USABLE, "no text recognised")
        }
        return Outcome.Ok(RecognisedText(lines))
    }
}
