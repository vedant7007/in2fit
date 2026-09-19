package io.github.vedant7007.katori.ml.vision

import io.github.vedant7007.katori.domain.model.Outcome
import kotlinx.coroutines.flow.Flow

/**
 * Four camera jobs, three reliable and one not. The contracts differ accordingly (spec 12).
 */

/**
 * Reads printed text from a lab report or a nutrition label. On-device, offline.
 *
 * This carries demo beat 3 and is the highest-value, lowest-risk camera feature in the project.
 *
 * CONTRACT
 * - Returns what it read plus where it read it. It does NOT interpret. Turning a recognised line
 *   into a test name, value, unit and reference range is layout and regex work done in code, and
 *   explaining it in plain language is ml/llm's phrasing path. This separation is what keeps a
 *   misread character from becoming a confident medical statement.
 * - The reference range comes from the report itself, never from a range the app carries.
 * - A value whose range cannot be read is returned WITHOUT a range. Downstream, no rule fires on a
 *   value with no printed range, so an unreadable range degrades to silence rather than to a
 *   comparison against an assumed range.
 *
 * FAILURE MODES
 * - Blurry, angled or poorly lit capture: INPUT_NOT_USABLE, and the UI asks for another photo.
 * - Text recognised but no test-value pairs found: an empty result, not a fabricated one.
 * - Camera permission missing: PERMISSION_DENIED.
 */
interface OcrEngine {
    suspend fun readText(image: ImageRef): Outcome<RecognisedText>
}

data class RecognisedText(val blocks: List<TextBlock>)

data class TextBlock(
    val text: String,
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
)

/** An opaque reference to a captured frame. Implementations resolve it; callers do not inspect it. */
@JvmInline
value class ImageRef(val id: String)

/**
 * Pose landmarks for exercise form checking.
 *
 * CONTRACT
 * - Emits landmarks only. Joint angles are computed in code and checked against hand-written
 *   per-movement thresholds; the model detects and code decides (spec 12.3). No learned judgement
 *   about form lives behind this interface.
 * - Streaming, and must sustain a usable frame rate on the weakest test device or the feature is
 *   cut rather than shipped slow.
 *
 * FAILURE MODES
 * - No person in frame, or partially out of frame: emits [PoseFrame.NoSubject] rather than
 *   landmarks with invented positions.
 * - Frame rate falls below the usable threshold: emits [PoseFrame.TooSlow] so the UI can stop
 *   claiming live feedback it is not delivering.
 */
interface PoseEngine {
    fun track(): Flow<PoseFrame>
}

sealed interface PoseFrame {
    data class Landmarks(val points: List<Landmark>, val timestampMs: Long) : PoseFrame
    data object NoSubject : PoseFrame
    data class TooSlow(val measuredFps: Int) : PoseFrame
    data class Unavailable(val outcome: Outcome.Unavailable) : PoseFrame
}

data class Landmark(val index: Int, val x: Float, val y: Float, val z: Float, val visibility: Float)

/**
 * First-pass dish identification from a photo. The weakest component in the project, deliberately.
 *
 * CONTRACT
 * - Its output is a GUESS and the type says so. It is never presented as the answer. The UI shows
 *   the camera guess, then the corrected figure after voice input, with the delta visible, because
 *   that visible correction is the design thesis rather than an apology (spec 2.3, 12.4).
 * - Every figure derived from this path carries
 *   [io.github.vedant7007.katori.domain.model.ConfidenceReason.UNCORRECTED_CAMERA_GUESS] until voice corrects it,
 *   which caps it at ROUGH.
 * - It returns ranked candidates, never a single answer, so the UI cannot accidentally present one
 *   as certain.
 *
 * FAILURE MODES
 * - Nothing recognised above threshold: an empty candidate list. The app asks the user to say what
 *   it is, which is the primary input path anyway.
 * - Not built yet: [Outcome.NotImplemented], and the UI shows that state. Never a sample dish.
 */
interface DishClassifier {
    suspend fun guess(image: ImageRef, maxCandidates: Int = 3): Outcome<List<DishGuess>>
}

data class DishGuess(
    val dishCode: String,
    val displayName: String,
    /** Model score, internal only. Never shown; it is not a confidence the user can act on. */
    val score: Float,
)
