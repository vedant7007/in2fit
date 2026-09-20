package io.github.vedant7007.katori.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.vedant7007.katori.R
import io.github.vedant7007.katori.ui.components.FailedCard
import io.github.vedant7007.katori.ui.components.Label
import io.github.vedant7007.katori.ui.components.PrimaryButton
import io.github.vedant7007.katori.ui.components.ReportField
import io.github.vedant7007.katori.ui.components.ScreenTitle
import io.github.vedant7007.katori.ui.components.SecondaryButton
import io.github.vedant7007.katori.ui.components.StageIndicator
import io.github.vedant7007.katori.ui.theme.In2fitColors
import io.github.vedant7007.katori.ui.theme.In2fitText
import io.github.vedant7007.katori.ui.theme.Space

/**
 * Beat 3: the camera, then every value read off the report beside its row, for the person to
 * confirm. State and the boundary are Arjun's `ScanViewModel`; the look is `ui/components` (Ira).
 */
@Composable
fun ScanScreen(vm: ScanViewModel = hiltViewModel()) {
    val state by vm.state.collectAsState()
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(Unit) { if (!granted) ask.launch(Manifest.permission.CAMERA) }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = Space.l), verticalArrangement = Arrangement.spacedBy(Space.s)) {
        ScreenTitle(stringResource(R.string.scan_title), modifier = Modifier.padding(top = Space.m))
        when {
            !granted -> Text(stringResource(R.string.camera_permission_needed), style = In2fitText.body)
            // 0026's rule holds here too: the wait is named, with a counter, never a bare bar.
            state.reading -> StageIndicator(done = emptyList(), current = stringResource(R.string.scan_reading), elapsed = null)
            state.report == null && state.failure == null && state.notBuilt == null ->
                CameraCapture(onCaptured = vm::captured, onFailed = vm::captureFailed)
            else -> Results(state, onToggle = vm::toggle, onRetake = vm::retake, onSave = vm::save)
        }
    }
}

@Composable
private fun CameraCapture(onCaptured: (android.graphics.Bitmap, Int) -> Unit, onFailed: (String) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val imageCapture = remember { ImageCapture.Builder().build() }
    var provider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    DisposableEffect(Unit) { onDispose { provider?.unbindAll() } }

    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(Space.m)) {
        Text(stringResource(R.string.scan_instruction), style = In2fitText.bodySmall, color = In2fitColors.inkSecondary)
        AndroidView(
            modifier = Modifier.weight(1f).fillMaxWidth().clip(MaterialTheme.shapes.medium),
            factory = { ctx ->
                PreviewView(ctx).also { view ->
                    val future = ProcessCameraProvider.getInstance(ctx)
                    future.addListener({
                        val p = future.get()
                        val preview = Preview.Builder().build()
                        preview.setSurfaceProvider(view.surfaceProvider)
                        p.unbindAll()
                        p.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture)
                        provider = p
                    }, ContextCompat.getMainExecutor(ctx))
                }
            },
        )
        PrimaryButton(
            label = stringResource(R.string.scan_capture),
            modifier = Modifier.fillMaxWidth().padding(bottom = Space.l),
            onClick = {
                imageCapture.takePicture(ContextCompat.getMainExecutor(context), object : ImageCapture.OnImageCapturedCallback() {
                    override fun onCaptureSuccess(image: ImageProxy) {
                        // CameraX does not bake the rotation into the pixels; it travels with the frame.
                        val bitmap = image.toBitmap()
                        val rotation = image.imageInfo.rotationDegrees
                        image.close()
                        onCaptured(bitmap, rotation)
                    }
                    override fun onError(exception: ImageCaptureException) = onFailed(exception.toString())
                })
            },
        )
    }
}

@Composable
private fun Results(state: ScanViewModel.State, onToggle: (Int) -> Unit, onRetake: () -> Unit, onSave: () -> Unit) {
    val report = state.report
    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(Space.s)) {
        state.failure?.let { FailedCard(stringResource(Sentences.unavailable(it)), state.failureDetail) }
        state.notBuilt?.let { Text(stringResource(R.string.not_built, it), style = In2fitText.bodySmall) }
        state.savedCount?.let { Text(stringResource(R.string.scan_saved, it), style = In2fitText.body) }
        if (report != null) {
            Label(
                report.reportDate?.let { stringResource(R.string.scan_report_date, it.toString()) }
                    ?: stringResource(R.string.scan_report_date_unknown),
            )
            if (state.fields.isEmpty()) Text(stringResource(R.string.scan_no_fields), style = In2fitText.body)
            LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.s)) {
                itemsIndexed(state.fields, key = { i, _ -> i }) { i, f -> ReportField(f.field, f.ticked, onToggle = { onToggle(i) }) }
            }
        }
        Row(modifier = Modifier.fillMaxWidth().padding(bottom = Space.l), horizontalArrangement = Arrangement.spacedBy(Space.s)) {
            SecondaryButton(stringResource(R.string.scan_retake), onClick = onRetake, modifier = Modifier.weight(1f))
            val ticked = state.fields.count { it.ticked }
            if (ticked > 0 && state.savedCount == null) {
                PrimaryButton(stringResource(R.string.scan_save, ticked), onClick = onSave, enabled = !state.saving, modifier = Modifier.weight(1f))
            }
        }
    }
}
