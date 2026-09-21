package io.github.vedant7007.katori.ui.v2

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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.vedant7007.katori.R
import io.github.vedant7007.katori.ui.ScanViewModel
import io.github.vedant7007.katori.ui.Sentences
import io.github.vedant7007.katori.ui.theme.micro
import io.github.vedant7007.katori.ui.theme.sans
import io.github.vedant7007.katori.ui.theme.scheme
import io.github.vedant7007.katori.ui.theme.serif

/**
 * The scan in the v2 dress, on Arjun's `ScanViewModel` unchanged: the serif title and the
 * instruction line, the camera in a 24 dp card (the design's "Report preview"), the Capture
 * pill, the PDF pill (the system picker, Arjun's path); while the reader works,
 * the stage row (one named stage, 0026, no sweep line animating during inference); then the
 * values read, one row each with the PRINTED range under the name and the value on the right,
 * a tick circle to keep or drop it, "Retake" and "Save N values".
 *
 * NOT AS DRAWN, and why: the design's three scanning steps ("Finding the results table",
 * "Reading 14 markers", "Matching to your profile") are one stage in the reader; the row
 * carries that one. The sweep line over the preview animates while the reader runs, which is
 * inference, so it is not drawn.
 */
@Composable
fun ScanScreen2(onBack: () -> Unit, modifier: Modifier = Modifier, vm: ScanViewModel = hiltViewModel()) {
    val s = scheme()
    val state by vm.state.collectAsState()
    val context = LocalContext.current
    var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(Unit) { if (!granted) ask.launch(Manifest.permission.CAMERA) }

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp)) {
        Row(Modifier.clickable(onClick = onBack).padding(bottom = 18.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon2(Glyphs.arrowLeft, 14.dp, s.text2)
            T(stringResource(R.string.v2_back), sans(13.5f, FontWeight.Normal, 1.2f), color = s.text2)
        }
        T(stringResource(R.string.scan_title), serif(27f, 1.2f), color = s.text)
        T(stringResource(R.string.scan_instruction), sans(13f, FontWeight.Normal, 1.5f), color = s.text2, modifier = Modifier.padding(top = 5.dp, bottom = 20.dp))
        val report = state.report
        when {
            state.reading -> {
                Card2(Modifier.fillMaxWidth().height(250.dp).padding(bottom = 22.dp), radius = 24.dp, bg = s.card2) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { T(stringResource(R.string.scan_reading), sans(13f, FontWeight.Normal, 1.2f), color = s.dim) }
                }
                // The reader has no clock on the ViewModel (v1 showed none either): the name alone.
                StageRows(listOf(io.github.vedant7007.katori.domain.Stage.READING_TEXT), remember { mutableStateOf(0) }, counter = false)
            }
            report == null && state.failure == null && state.notBuilt == null -> {
                if (granted) {
                    Camera(onCaptured = vm::captured, onFailed = vm::captureFailed)
                } else {
                    T(stringResource(R.string.camera_permission_needed), sans(14.5f, FontWeight.Normal, 1.5f), color = s.text, modifier = Modifier.padding(bottom = 16.dp))
                }
                // The PDF path (Arjun's `ScanViewModel.pdf`): the system picker's one-file grant, no permission.
                val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(vm::pdf) }
                Pill2(stringResource(R.string.scan_open_pdf), onClick = { pick.launch(arrayOf("application/pdf")) }, filled = false, modifier = Modifier.fillMaxWidth().padding(top = 12.dp), size = 15f, vertical = 17.dp)
            }
            else -> {
                state.failure?.let { T(stringResource(Sentences.unavailable(it)), sans(14.5f, FontWeight.Normal, 1.5f), color = s.warm, modifier = Modifier.padding(bottom = 12.dp)) }
                state.failureDetail?.let { T(it, sans(12.5f, FontWeight.Normal, 1.45f), color = s.text3, modifier = Modifier.padding(bottom = 12.dp)) }
                state.notBuilt?.let { T(stringResource(R.string.not_built, it), sans(14.5f, FontWeight.Normal, 1.5f), color = s.text, modifier = Modifier.padding(bottom = 12.dp)) }
                state.savedCount?.let { T(stringResource(R.string.scan_saved, it), sans(14.5f, FontWeight.SemiBold, 1.5f), color = s.accent, modifier = Modifier.padding(bottom = 12.dp)) }
                if (report != null) {
                    T(
                        (report.reportDate?.let { stringResource(R.string.scan_report_date, it.toString()) } ?: stringResource(R.string.scan_report_date_unknown)).uppercase(),
                        micro(11.5f, 0.14.em, FontWeight.SemiBold), color = s.text3, modifier = Modifier.padding(bottom = 12.dp),
                    )
                    if (state.fields.isEmpty()) T(stringResource(R.string.scan_no_fields), sans(14.5f, FontWeight.Normal, 1.5f), color = s.text, modifier = Modifier.padding(bottom = 12.dp))
                    else Card2(Modifier.fillMaxWidth().padding(bottom = 16.dp), radius = 26.dp) {
                        Column(Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) {
                            state.fields.forEachIndexed { i, f ->
                                val lo = f.field.referenceLow
                                val hi = f.field.referenceHigh
                                val range = when {
                                    lo != null && hi != null -> stringResource(R.string.scan_range_both, labValue(lo), labValue(hi))
                                    hi != null -> stringResource(R.string.scan_range_high_only, labValue(hi))
                                    lo != null -> stringResource(R.string.scan_range_low_only, labValue(lo))
                                    else -> stringResource(R.string.scan_range_none)
                                }
                                Column(Modifier.fillMaxWidth().clickable { vm.toggle(i) }) {
                                    Box(Modifier.fillMaxWidth().height(1.dp).background(s.text.copy(alpha = 0.06f)))
                                    Row(Modifier.fillMaxWidth().padding(vertical = 13.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Box(Modifier.size(22.dp).then(if (f.ticked) Modifier.background(s.accent, CircleShape) else Modifier).border(1.5.dp, if (f.ticked) s.accent else s.text.copy(alpha = 0.2f), CircleShape), contentAlignment = Alignment.Center) {
                                            if (f.ticked) Icon2(Glyphs.tick, 12.dp, s.onAccent)
                                        }
                                        Column(Modifier.weight(1f)) {
                                            T(f.field.testName, sans(14.5f, FontWeight.Normal, 1.3f), color = if (f.ticked) s.text else s.text3)
                                            T(range, sans(12f, FontWeight.Normal, 1.3f), color = s.text3, modifier = Modifier.padding(top = 2.dp))
                                        }
                                        T(listOfNotNull(labValue(f.field.value), f.field.unit).joinToString(" "), sans(15f, FontWeight.SemiBold, 1.2f), color = if (f.ticked) s.text else s.text3, maxLines = 1)
                                    }
                                }
                            }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Pill2(stringResource(R.string.scan_retake), onClick = vm::retake, filled = false, modifier = Modifier.weight(1f), horizontal = 16.dp)
                    val ticked = state.fields.count { it.ticked }
                    if (ticked > 0 && state.savedCount == null) {
                        Pill2(stringResource(R.string.scan_save, ticked), onClick = vm::save, enabled = !state.saving, modifier = Modifier.weight(1f), horizontal = 16.dp)
                    }
                }
            }
        }
        T(stringResource(R.string.safety_not_medical_advice), sans(12.5f, FontWeight.Normal, 1.5f), color = s.text3, modifier = Modifier.padding(top = 20.dp, bottom = 8.dp))
    }
}

@Composable
private fun Camera(onCaptured: (android.graphics.Bitmap, Int) -> Unit, onFailed: (String) -> Unit) {
    val s = scheme()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val imageCapture = remember { ImageCapture.Builder().build() }
    var provider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    DisposableEffect(Unit) { onDispose { provider?.unbindAll() } }
    val shape = RoundedCornerShape(24.dp)
    AndroidView(
        modifier = Modifier.fillMaxWidth().height(250.dp).clip(shape).border(1.dp, s.text.copy(alpha = 0.08f), shape),
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
    Pill2(
        stringResource(R.string.scan_capture),
        onClick = {
            imageCapture.takePicture(ContextCompat.getMainExecutor(context), object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    val bitmap = image.toBitmap()
                    val rotation = image.imageInfo.rotationDegrees
                    image.close()
                    onCaptured(bitmap, rotation)
                }
                override fun onError(exception: ImageCaptureException) = onFailed(exception.toString())
            })
        },
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp), size = 15f, vertical = 17.dp,
    )
}
