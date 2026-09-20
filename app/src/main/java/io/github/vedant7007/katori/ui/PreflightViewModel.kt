package io.github.vedant7007.katori.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.StatFs
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.vedant7007.katori.domain.ModelArbiter
import io.github.vedant7007.katori.domain.ModelHandle
import io.github.vedant7007.katori.domain.model.Outcome
import io.github.vedant7007.katori.ml.asr.AsrModels
import io.github.vedant7007.katori.ml.asr.SpeechLanguage
import io.github.vedant7007.katori.ml.llm.LlmModels
import io.github.vedant7007.katori.ml.tts.PiperVoices
import io.github.vedant7007.katori.ml.tts.TtsEngine
import io.github.vedant7007.katori.ui.demo.DemoFeed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale
import javax.inject.Inject

/**
 * The pre-flight check: what a person setting this phone up on the morning of the demo needs
 * to see, and nothing a judge should. Reached by a long-press only.
 *
 * Every line is a fact read off THIS device now: the file is there or it is not, at that size,
 * at that path; a model loads or the arbiter says why not; a permission is granted or not.
 * Nothing here is estimated. "Load" is a real load through the arbiter and takes what it takes.
 *
 * [ModelArbiter] and [TtsEngine] are injected here and nowhere else in `ui/`, for diagnostics
 * only: this screen never handles a turn.
 */
@HiltViewModel
class PreflightViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val arbiter: ModelArbiter,
    private val tts: TtsEngine,
) : ViewModel() {

    data class ModelRow(
        val handle: ModelHandle,
        val file: File,
        val present: Boolean,
        val bytes: Long,
        /** Null until Load is pressed; then the arbiter's verdict, verbatim. */
        val load: String? = null,
        val loading: Boolean = false,
    )

    data class State(
        val appVersion: String = "",
        val applicationId: String = "",
        val device: String = "",
        val locale: String = "",
        val micGranted: Boolean = false,
        val cameraGranted: Boolean = false,
        val modelsDir: String = "",
        val freeBytes: Long = 0L,
        val models: List<ModelRow> = emptyList(),
        val voices: String = "",
        val residency: String = "",
        val demo: Boolean = false,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private val modelsDir = File(context.externalMediaDirs.firstOrNull() ?: context.filesDir, "models")

    init { refresh() }

    fun refresh() {
        val pkg = context.packageManager.getPackageInfo(context.packageName, 0)
        val handles = SpeechLanguage.entries.map { AsrModels.handleFor(it) } + PiperVoices.byLanguage.values + LlmModels.QWEN_2_5_1_5B_Q4_K_M
        val snapshot = arbiter.residency.value
        _state.update { s ->
            s.copy(
                appVersion = pkg.versionName.orEmpty(),
                applicationId = context.packageName,
                device = "${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}), ${Build.SUPPORTED_ABIS.joinToString()}",
                locale = Locale.getDefault().toLanguageTag(),
                micGranted = granted(Manifest.permission.RECORD_AUDIO),
                cameraGranted = granted(Manifest.permission.CAMERA),
                modelsDir = modelsDir.absolutePath,
                freeBytes = runCatching { StatFs((modelsDir.takeIf { it.exists() } ?: context.filesDir).absolutePath).availableBytes }.getOrDefault(0L),
                models = handles.map { h ->
                    val f = File(modelsDir, h.relativePath)
                    val old = s.models.firstOrNull { it.handle == h }
                    ModelRow(h, f, present = f.isFile && f.length() > 0L, bytes = f.length(), load = old?.load, loading = old?.loading ?: false)
                },
                voices = tts.supportedLanguages.joinToString { it.tag }.ifEmpty { "-" },
                residency = "tier ${snapshot.tier}, ceiling ${mb(snapshot.ceilingBytes)}, used ${mb(snapshot.usedBytes)}, resident " +
                    (snapshot.resident.joinToString { it.handle.id }.ifEmpty { "none" }),
                demo = DemoFeed.enabled.value,
            )
        }
    }

    /** A real load through the arbiter. The verdict is the outcome, verbatim. */
    fun load(handle: ModelHandle) {
        _state.update { s -> s.copy(models = s.models.map { if (it.handle == handle) it.copy(loading = true, load = null) else it }) }
        viewModelScope.launch(Dispatchers.Default) {
            val started = System.currentTimeMillis()
            val verdict = when (val o = arbiter.preload(handle)) {
                is Outcome.Ok -> "loaded in ${System.currentTimeMillis() - started} ms"
                is Outcome.Unavailable -> "${o.reason}: ${o.detail ?: ""}"
                is Outcome.NotImplemented -> "not implemented: ${o.component}"
            }
            _state.update { s -> s.copy(models = s.models.map { if (it.handle == handle) it.copy(loading = false, load = verdict) else it }) }
            refresh()
        }
    }

    fun setDemo(on: Boolean) {
        DemoFeed.set(on)
        _state.update { it.copy(demo = on) }
    }

    private fun granted(permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    companion object {
        fun mb(bytes: Long): String = "%.1f MB".format(Locale.ROOT, bytes / 1_048_576.0)
    }
}
