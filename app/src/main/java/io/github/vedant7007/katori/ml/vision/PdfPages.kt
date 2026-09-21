package io.github.vedant7007.katori.ml.vision

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import io.github.vedant7007.katori.domain.model.Outcome
import io.github.vedant7007.katori.domain.model.UnavailableReason

/**
 * A LAB REPORT AS A PDF (21 Sep, Ira's (ac)): the pages rendered to bitmaps by the platform's
 * own `PdfRenderer`, for the same recogniser and the same extractor the camera path uses. No
 * library, no permission: the person picks the file through the system's document picker
 * (`ACTION_OPEN_DOCUMENT`), which grants this one read, and the page is rendered flat, so the
 * angle that reads 0 of 8 on a photograph does not exist here.
 *
 * WHAT IS RENDERED. Up to [MAX_PAGES] pages, each at a scale that puts the page's width at about
 * [TARGET_WIDTH_PX] (an A4 page at ~200 dpi), on white, because a PDF page's transparent
 * background renders black and ML Kit reads nothing off it. A scanned-image PDF is rendered the
 * same way and read by OCR like a photograph without the angle; a text PDF is read the same way
 * rather than through its text layer, so both kinds go through the one path that is measured.
 *
 * FAILURES ARE NAMED, never guessed around: a password-protected file, a file that is not a
 * PDF, a file the picker no longer grants, each an [Outcome.Unavailable] with the reason.
 */
object PdfPages {
    const val MAX_PAGES = 3
    const val TARGET_WIDTH_PX = 1700

    fun render(resolver: ContentResolver, uri: Uri, maxPages: Int = MAX_PAGES): Outcome<List<Bitmap>> {
        val fd = try {
            resolver.openFileDescriptor(uri, "r")
        } catch (e: Exception) {
            return Outcome.Unavailable(UnavailableReason.INPUT_NOT_USABLE, "the file could not be opened: ${e::class.java.simpleName}")
        } ?: return Outcome.Unavailable(UnavailableReason.INPUT_NOT_USABLE, "the file could not be opened")

        val renderer = try {
            PdfRenderer(fd)
        } catch (e: SecurityException) {
            fd.close()
            return Outcome.Unavailable(UnavailableReason.INPUT_NOT_USABLE, "the PDF is password-protected")
        } catch (e: Exception) {
            fd.close()
            return Outcome.Unavailable(UnavailableReason.INPUT_NOT_USABLE, "not a readable PDF: ${e::class.java.simpleName}")
        }

        return try {
            if (renderer.pageCount == 0) {
                Outcome.Unavailable(UnavailableReason.INPUT_NOT_USABLE, "the PDF has no pages")
            } else {
                Outcome.Ok((0 until minOf(renderer.pageCount, maxPages)).map { i -> renderer.openPage(i).use(::page) })
            }
        } finally {
            renderer.close()
            fd.close()
        }
    }

    private fun page(page: PdfRenderer.Page): Bitmap {
        val scale = TARGET_WIDTH_PX.toFloat() / page.width
        val bitmap = Bitmap.createBitmap((page.width * scale).toInt().coerceAtLeast(1), (page.height * scale).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        Canvas(bitmap).drawColor(Color.WHITE)
        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        return bitmap
    }
}
