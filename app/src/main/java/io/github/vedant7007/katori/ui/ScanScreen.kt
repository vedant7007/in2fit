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
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.vedant7007.katori.R
import io.github.vedant7007.katori.ml.vision.LabField

/** Beat 3: the camera, then every value read off the report beside its row, for the person to confirm. */
@Composable
fun ScanScreen(vm: ScanViewModel = hiltViewModel()) {
    val state by vm.state.collectAsState()
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(Unit) { if (!granted) ask.launch(Manifest.permission.CAMERA) }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.scan_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 8.dp))
        when {
            !granted -> Text(stringResource(R.string.camera_permission_needed))
            state.reading -> {
                Text(stringResource(R.string.scan_reading))
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
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

    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AndroidView(
            modifier = Modifier.weight(1f).fillMaxWidth(),
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
        Button(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
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
        ) { Text(stringResource(R.string.scan_capture)) }
    }
}

@Composable
private fun Results(state: ScanViewModel.State, onToggle: (Int) -> Unit, onRetake: () -> Unit, onSave: () -> Unit) {
    val report = state.report
    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        state.failure?.let {
            Text(stringResource(Sentences.unavailable(it)))
            state.failureDetail?.let { d -> Text(d, style = MaterialTheme.typography.bodySmall) }
        }
        state.notBuilt?.let { Text(stringResource(R.string.not_built, it)) }
        if (report != null) {
            Text(
                report.reportDate?.let { stringResource(R.string.scan_report_date, it.toString()) }
                    ?: stringResource(R.string.scan_report_date_unknown),
                style = MaterialTheme.typography.labelLarge,
            )
            if (state.fields.isEmpty()) Text(stringResource(R.string.scan_no_fields))
            LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(state.fields) { i, f -> FieldRow(f.field, f.ticked) { onToggle(i) } }
            }
        }
        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onRetake, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.scan_retake)) }
            val ticked = state.fields.count { it.ticked }
            if (ticked > 0) {
                Button(onClick = onSave, enabled = !state.saving, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.scan_save, ticked))
                }
            }
        }
    }
}

@Composable
private fun FieldRow(field: LabField, ticked: Boolean, onToggle: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Checkbox(checked = ticked, onCheckedChange = { onToggle() })
        Column {
            Text(stringResource(R.string.scan_field_line, field.testName, Sentences.number(field.value), field.unit.orEmpty()))
            val lo = field.referenceLow
            val hi = field.referenceHigh
            val range = when {
                lo != null && hi != null -> stringResource(R.string.scan_range_both, Sentences.number(lo), Sentences.number(hi))
                hi != null -> stringResource(R.string.scan_range_high_only, Sentences.number(hi))
                lo != null -> stringResource(R.string.scan_range_low_only, Sentences.number(lo))
                else -> stringResource(R.string.scan_range_none)
            }
            Text(range, style = MaterialTheme.typography.bodySmall)
            // The row as read, so the person can check the parse against the print.
            Text(field.sourceRow, style = MaterialTheme.typography.labelSmall)
        }
    }
}
