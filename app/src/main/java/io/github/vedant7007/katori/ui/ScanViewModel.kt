package io.github.vedant7007.katori.ui

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.vedant7007.katori.domain.LabValue
import io.github.vedant7007.katori.domain.Orchestrator
import io.github.vedant7007.katori.domain.OrchestratorEvent
import io.github.vedant7007.katori.domain.UserIntent
import io.github.vedant7007.katori.domain.model.Outcome
import io.github.vedant7007.katori.domain.model.UnavailableReason
import io.github.vedant7007.katori.ml.vision.FrameStore
import io.github.vedant7007.katori.ml.vision.LabField
import io.github.vedant7007.katori.ml.vision.LabReport
import io.github.vedant7007.katori.ml.vision.LabReportExtractor
import io.github.vedant7007.katori.ml.vision.OcrEngine
import io.github.vedant7007.katori.ml.vision.RecognisedText
import io.github.vedant7007.katori.ml.vision.TextBlock
import io.github.vedant7007.katori.ui.demo.DemoFeed
import io.github.vedant7007.katori.ui.demo.ScriptedOrchestrator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Beat 3. Capture is this side of the boundary; the write is the orchestrator's.
 *
 * A captured frame goes through [OcrEngine] and [LabReportExtractor]; every field is shown
 * beside the row it was read from, ticked, for the person to untick. Saving hands the ticked
 * values to the orchestrator. Until `UserIntent.SaveLabReport` lands (COORDINATION.md, 14:30)
 * the existing `ScanLabReport` intent is sent and its NotImplemented is shown as such.
 */
@HiltViewModel
class ScanViewModel @Inject constructor(
    private val frames: FrameStore,
    private val ocr: OcrEngine,
    private val orchestrator: Orchestrator,
) : ViewModel() {

    data class Field(val field: LabField, val ticked: Boolean)

    data class State(
        val reading: Boolean = false,
        val report: LabReport? = null,
        val fields: List<Field> = emptyList(),
        val failure: UnavailableReason? = null,
        val failureDetail: String? = null,
        val notBuilt: String? = null,
        val saving: Boolean = false,
        val savedCount: Int? = null,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    fun captured(bitmap: Bitmap, rotationDegrees: Int) {
        _state.update { State(reading = true) }
        viewModelScope.launch(Dispatchers.Default) {
            val ref = frames.hold(bitmap, rotationDegrees)
            // The scripted feed (0027) replaces the recogniser's output with the scripted report's
            // lines; the extractor that reads them is the real one, and the banner is on.
            val outcome = if (DemoFeed.enabled.value) Outcome.Ok(scriptedReport()) else ocr.readText(ref)
            when (val o = outcome) {
                is Outcome.Ok -> {
                    val report = LabReportExtractor.extract(o.value)
                    _state.update { State(report = report, fields = report.fields.map { Field(it, ticked = true) }) }
                }
                is Outcome.Unavailable -> _state.update { State(failure = o.reason, failureDetail = o.detail) }
                is Outcome.NotImplemented -> _state.update { State(notBuilt = o.component) }
            }
        }
    }

    fun captureFailed(detail: String) = _state.update { State(failure = UnavailableReason.INPUT_NOT_USABLE, failureDetail = detail) }

    fun toggle(index: Int) = _state.update { s ->
        s.copy(fields = s.fields.mapIndexed { i, f -> if (i == index) f.copy(ticked = !f.ticked) else f })
    }

    fun retake() = _state.update { State() }

    fun save() {
        if (state.value.saving) return
        _state.update { it.copy(saving = true, notBuilt = null, failure = null) }
        val confirmed = state.value.fields.filter { it.ticked }.map { it.field }
        val date = state.value.report?.reportDate
        viewModelScope.launch(Dispatchers.Default) {
            if (DemoFeed.enabled.value) {
                // The script keeps the confirmed values so beat 4 evaluates against them.
                DemoFeed.labValues += confirmed.map { LabValue(it.testName, it.value, it.unit.orEmpty(), it.referenceLow, it.referenceHigh, date ?: ScriptedOrchestrator.REPORT_DATE) }
                _state.update { it.copy(saving = false, savedCount = confirmed.size) }
                return@launch
            }
            // The intent that carries the confirmed values is Rao's to add (agreed 14:55). Until
            // then this is the contract's own not-built path.
            orchestrator.handle(UserIntent.ScanLabReport).collect { ev ->
                when (ev) {
                    is OrchestratorEvent.NotImplemented -> _state.update { it.copy(notBuilt = ev.component) }
                    is OrchestratorEvent.Failed -> _state.update { it.copy(failure = ev.reason, failureDetail = ev.detail) }
                    else -> Unit
                }
            }
            _state.update { it.copy(saving = false) }
        }
    }

    /** The scripted report as recognised lines, one box per cell, for the real extractor. */
    private fun scriptedReport(): RecognisedText {
        val lines = mutableListOf(
            TextBlock("Reported on ${ScriptedOrchestrator.REPORT_DATE.dayOfMonth}/${ScriptedOrchestrator.REPORT_DATE.monthValue}/${ScriptedOrchestrator.REPORT_DATE.year}", 40, 40, 500, 76),
        )
        ScriptedOrchestrator.REPORT_ROWS.forEachIndexed { i, (name, value, range) ->
            val y = 140 + i * 60
            lines += TextBlock(name, 40, y, 40 + 14 * name.length, y + 36)
            lines += TextBlock(value, 520, y, 520 + 14 * value.length, y + 36)
            lines += TextBlock(range, 760, y, 760 + 14 * range.length, y + 36)
        }
        return RecognisedText(lines)
    }
}
