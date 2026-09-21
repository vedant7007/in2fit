package io.github.vedant7007.katori.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.vedant7007.katori.R
import io.github.vedant7007.katori.ui.components.SecondaryButton

/**
 * "Open a PDF report": the system's document picker, PDFs only, one read granted for the file
 * the person picks. No permission of our own, no library; the render is the platform's
 * (`PdfPages`). Drop it wherever a report can be added; it needs nothing from the screen but
 * [onPicked], which is `ScanViewModel.pdf`.
 */
@Composable
fun PdfPickButton(onPicked: (Uri) -> Unit, modifier: Modifier = Modifier) {
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(onPicked) }
    SecondaryButton(stringResource(R.string.scan_open_pdf), onClick = { pick.launch(arrayOf("application/pdf")) }, modifier = modifier)
}
