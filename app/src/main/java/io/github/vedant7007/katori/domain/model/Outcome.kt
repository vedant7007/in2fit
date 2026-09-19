package io.github.vedant7007.katori.domain.model

/**
 * The single result type for every pipeline in this app.
 *
 * CONTRACT
 * - Every engine, lookup and use case returns [Outcome]. There is no nullable-return
 *   convention and no exception-as-control-flow convention anywhere in this codebase.
 * - There is deliberately no `Partial` and no `Estimated` variant. A pipeline either
 *   produced a real value from real data, or it did not.
 * - The UI must exhaustively handle [Unavailable] and [NotImplemented]. Because this is a
 *   sealed interface, a `when` that forgets one does not compile. That is the point: it is
 *   the structural guard against stub data becoming demo data (spec risk 8).
 * - [NotImplemented] is the ONLY legitimate placeholder in this codebase. A pipeline that is
 *   not built yet returns it. It must never be replaced by a hardcoded sample value, a mock
 *   number, or a "typical" figure, in any build variant, at any time.
 *
 * FAILURE MODES
 * - Callers must not map [Unavailable] to a zero, an empty list, or a default. Doing so
 *   converts "we do not know" into "there is none", which is the most consequential class of
 *   bug available in a health app (see [NutrientValue]).
 * - An [Unavailable] carries a machine-readable [UnavailableReason]. The user-facing string is
 *   chosen by the UI layer from the reason, never assembled from `detail`, which is for logs
 *   and bug reports only and may contain technical text.
 */
sealed interface Outcome<out T> {

    /** The pipeline ran and produced a real value derived from real data. */
    data class Ok<out T>(val value: T) : Outcome<T>

    /**
     * The pipeline is implemented but could not produce a value for this input, right now.
     * Retrying later, or with different input, may succeed.
     */
    data class Unavailable(
        val reason: UnavailableReason,
        /** Technical detail for logs and bug reports. Never rendered to the user verbatim. */
        val detail: String? = null,
    ) : Outcome<Nothing>

    /**
     * This pipeline does not exist yet. The UI must show an explicit "not implemented" state.
     *
     * @param component stable identifier of the missing piece, e.g. "ml.vision.DishClassifier".
     */
    data class NotImplemented(val component: String) : Outcome<Nothing>
}

/**
 * Why a value could not be produced. Every value here must map to a user-facing sentence that
 * states a limitation, never a number and never a guess.
 */
enum class UnavailableReason {
    /** The required model is not resident and could not be admitted. See ModelArbiter. */
    MODEL_NOT_LOADED,

    /** The model file is present but failed to initialise. */
    MODEL_LOAD_FAILED,

    /** Admitting this model would exceed the device memory ceiling even after eviction. */
    INSUFFICIENT_MEMORY,

    /** Nothing in the food database matched, above the match threshold. */
    NO_MATCH,

    /** A match exists but is too weak to present. The system asks rather than guesses (spec 10.7). */
    BELOW_CONFIDENCE_THRESHOLD,

    /** The user has not granted a runtime permission this pipeline needs (mic, camera). */
    PERMISSION_DENIED,

    /** This device cannot run this pipeline at all. Distinct from a transient memory failure. */
    HARDWARE_UNSUPPORTED,

    /** Audio too short, image too blurry, no text found: the input itself was not usable. */
    INPUT_NOT_USABLE,

    /** The user or the system cancelled the operation. */
    CANCELLED,

    /** The LLM returned output that failed schema validation after the permitted re-asks. */
    SCHEMA_VALIDATION_FAILED,

    /** An unexpected internal error. `detail` carries the diagnostic. */
    INTERNAL_ERROR,
}

/** Convenience: the value when [Outcome.Ok], otherwise null. Never use this to substitute a default. */
inline fun <T> Outcome<T>.okOrNull(): T? = (this as? Outcome.Ok<T>)?.value
