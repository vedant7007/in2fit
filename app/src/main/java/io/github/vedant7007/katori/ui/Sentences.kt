package io.github.vedant7007.katori.ui

import androidx.annotation.StringRes
import io.github.vedant7007.katori.R
import io.github.vedant7007.katori.domain.Stage
import io.github.vedant7007.katori.domain.model.ConfidenceBand
import io.github.vedant7007.katori.domain.model.UnavailableReason

/**
 * Enum to string key, one `when` per enum, exhaustive so a new constant fails the build here
 * rather than rendering nothing. The sentences live in res/values/strings.xml (0017).
 */
object Sentences {

    @StringRes
    fun stage(stage: Stage): Int = when (stage) {
        Stage.RECORDING -> R.string.stage_recording
        Stage.TRANSCRIBING -> R.string.stage_transcribing
        Stage.CLASSIFYING -> R.string.stage_classifying
        Stage.EXTRACTING -> R.string.stage_extracting
        Stage.MATCHING_FOODS -> R.string.stage_matching_foods
        Stage.COMPUTING -> R.string.stage_computing
        Stage.SAVING -> R.string.stage_saving
        Stage.EVALUATING_RULES -> R.string.stage_evaluating_rules
        Stage.RETRIEVING_FACTS -> R.string.stage_retrieving_facts
        Stage.PHRASING -> R.string.stage_phrasing
        Stage.SPEAKING -> R.string.stage_speaking
        Stage.CAPTURING -> R.string.stage_capturing
        Stage.READING_TEXT -> R.string.stage_reading_text
    }

    /** A limitation, never a number and never a guess (Outcome.kt). */
    @StringRes
    fun unavailable(reason: UnavailableReason): Int = when (reason) {
        UnavailableReason.MODEL_NOT_LOADED -> R.string.unavailable_model_not_loaded
        UnavailableReason.MODEL_LOAD_FAILED -> R.string.unavailable_model_load_failed
        UnavailableReason.INSUFFICIENT_MEMORY -> R.string.unavailable_insufficient_memory
        UnavailableReason.NO_MATCH -> R.string.unavailable_no_match
        UnavailableReason.KNOWN_ITEM_NO_DATA -> R.string.unavailable_known_item_no_data
        UnavailableReason.BELOW_CONFIDENCE_THRESHOLD -> R.string.unavailable_below_confidence_threshold
        UnavailableReason.PERMISSION_DENIED -> R.string.unavailable_permission_denied
        UnavailableReason.HARDWARE_UNSUPPORTED -> R.string.unavailable_hardware_unsupported
        UnavailableReason.INPUT_NOT_USABLE -> R.string.unavailable_input_not_usable
        UnavailableReason.CANCELLED -> R.string.unavailable_cancelled
        UnavailableReason.SCHEMA_VALIDATION_FAILED -> R.string.unavailable_schema_validation_failed
        UnavailableReason.INTERNAL_ERROR -> R.string.unavailable_internal_error
    }

    @StringRes
    fun band(band: ConfidenceBand): Int = when (band) {
        ConfidenceBand.GOOD -> R.string.confidence_band_good
        ConfidenceBand.APPROXIMATE -> R.string.confidence_band_approximate
        ConfidenceBand.ROUGH -> R.string.confidence_band_rough
    }

    /** 2 stays 2 and 1.5 stays 1.5; a spoken quantity is never shown as 2.0. */
    fun number(value: Double): String =
        if (value == Math.rint(value)) value.toLong().toString() else value.toString()
}
