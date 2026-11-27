package com.notely.app

import android.graphics.pdf.PdfDocument
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream

/**
 * Minimal probe: does the PdfDocument lifecycle itself work under this runtime?
 * If this passes but the exporter fails, the bug is in the exporter; if it fails,
 * the harness cannot exercise the platform PDF writer.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PdfSmokeTest {

    @Test
    fun rawPdfDocumentLifecycle() {
        val doc = PdfDocument()
        val page = doc.startPage(PdfDocument.PageInfo.Builder(595, 842, 1).create())
        page.canvas.drawText("hello", 40f, 60f, android.graphics.Paint())
        doc.finishPage(page)
        val out = ByteArrayOutputStream()
        doc.writeTo(out)
        doc.close()
        assertTrue(out.size() > 100)
        assertTrue(String(out.toByteArray(), Charsets.ISO_8859_1).startsWith("%PDF"))
    }
}
