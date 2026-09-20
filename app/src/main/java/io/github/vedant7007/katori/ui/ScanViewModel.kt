package io.github.vedant7007.katori.ui

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
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
            when (val o = ocr.readText(ref)) {
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
        viewModelScope.launch(Dispatchers.Default) {
            // The confirmed values are state.value.fields.filter { it.ticked }; the intent that
            // carries them is Rao's to add. Until then this is the contract's own not-built path.
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
}
