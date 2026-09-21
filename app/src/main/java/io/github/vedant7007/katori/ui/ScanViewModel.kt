package io.github.vedant7007.katori.ui

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.vedant7007.katori.domain.LabValue
import io.github.vedant7007.katori.domain.ContextText
import io.github.vedant7007.katori.domain.Orchestrator
import io.github.vedant7007.katori.domain.RulesEngine
import io.github.vedant7007.katori.domain.TriggerText
import io.github.vedant7007.katori.domain.OrchestratorEvent
import io.github.vedant7007.katori.domain.UserIntent
import io.github.vedant7007.katori.domain.model.Outcome
import io.github.vedant7007.katori.domain.model.UnavailableReason
import io.github.vedant7007.katori.ml.vision.FrameStore
import io.github.vedant7007.katori.ml.vision.LabField
import io.github.vedant7007.katori.ml.vision.LabReport
import io.github.vedant7007.katori.ml.vision.LabReportExtractor
import io.github.vedant7007.katori.ml.vision.OcrEngine
import io.github.vedant7007.katori.ml.vision.PdfPages
import io.github.vedant7007.katori.ui.demo.DemoFeed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/**
 * Beat 3. Capture is this side of the boundary; the write is the orchestrator's.
 *
 * A captured frame goes through [OcrEngine] and [LabReportExtractor]; every field is shown
 * beside the row it was read from, ticked, for the person to untick. Saving hands the ticked
 * values to the orchestrator as `UserIntent.SaveLabReport` (COORDINATION.md, agreed 14:55),
 * which writes them and answers with how many.
 */
@HiltViewModel
class ScanViewModel @Inject constructor(
    private val frames: FrameStore,
    private val ocr: OcrEngine,
    private val orchestrator: Orchestrator,
    rules: RulesEngine,
    contextText: ContextText,
    triggerText: TriggerText,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    /** The scripted feed's twin, so a scripted save lands where the scripted advise-again reads (0027). Null in the demo build. */
    private val scripted: Orchestrator? = DemoFeed.orchestrator(rules, contextText, triggerText, emptyMap())

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
            val outcome = DemoFeed.scriptedReport()?.takeIf { DemoFeed.enabled.value }?.let { Outcome.Ok(it) } ?: ocr.readText(ref)
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

    /**
     * A report as a PDF, picked through the system's document picker (Ira's (ac), 21 Sep): every
     * page rendered flat by the platform, read by the SAME recogniser and extractor as a photograph,
     * the pages merged into one report. The scripted feed, when on, replaces the first page's
     * recognised text the way it replaces a capture's, and the banner is on.
     */
    fun pdf(uri: Uri) {
        _state.update { State(reading = true) }
        viewModelScope.launch(Dispatchers.Default) {
            val pages = when (val r = PdfPages.render(context.contentResolver, uri)) {
                is Outcome.Ok -> r.value
                is Outcome.Unavailable -> { _state.update { State(failure = r.reason, failureDetail = r.detail) }; return@launch }
                is Outcome.NotImplemented -> { _state.update { State(notBuilt = r.component) }; return@launch }
            }
            val reports = mutableListOf<LabReport>()
            for ((i, bitmap) in pages.withIndex()) {
                val ref = frames.hold(bitmap, 0)
                val outcome = DemoFeed.scriptedReport()?.takeIf { i == 0 && DemoFeed.enabled.value }?.let { Outcome.Ok(it) } ?: ocr.readText(ref)
                when (outcome) {
                    is Outcome.Ok -> reports += LabReportExtractor.extract(outcome.value)
                    // A page with no text is a blank or a picture page, skipped; anything else ends the read.
                    is Outcome.Unavailable -> if (outcome.reason != UnavailableReason.INPUT_NOT_USABLE) {
                        _state.update { State(failure = outcome.reason, failureDetail = outcome.detail) }; return@launch
                    }
                    is Outcome.NotImplemented -> { _state.update { State(notBuilt = outcome.component) }; return@launch }
                }
                bitmap.recycle()
            }
            if (reports.isEmpty()) {
                _state.update { State(failure = UnavailableReason.INPUT_NOT_USABLE, failureDetail = "no text recognised on ${pages.size} page(s)") }
                return@launch
            }
            val report = LabReportExtractor.merge(reports)
            _state.update { State(report = report, fields = report.fields.map { Field(it, ticked = true) }) }
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
        // The write is the orchestrator's (agreed 14:55, landed 18:07). A row whose unit was not
        // read is saved with an empty unit: its value is still comparable to its own printed range.
        val values = confirmed.map { LabValue(it.testName, it.value, it.unit.orEmpty(), it.referenceLow, it.referenceHigh, date ?: LocalDate.now()) }
        val source = scripted?.takeIf { DemoFeed.enabled.value } ?: orchestrator
        viewModelScope.launch(Dispatchers.Default) {
            source.handle(UserIntent.SaveLabReport(values)).collect { ev ->
                when (ev) {
                    is OrchestratorEvent.LabReportSaved -> _state.update { it.copy(savedCount = ev.count) }
                    is OrchestratorEvent.NotImplemented -> _state.update { it.copy(notBuilt = ev.component) }
                    is OrchestratorEvent.Failed -> _state.update { it.copy(failure = ev.reason, failureDetail = ev.detail) }
                    else -> Unit
                }
            }
            _state.update { it.copy(saving = false) }
        }
    }

}
