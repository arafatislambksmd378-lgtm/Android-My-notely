package com.notely.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.notely.app.doc.*
import com.notely.app.export.Exporters
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * PDF export cannot be tested on the JVM: even a bare PdfDocument lifecycle throws
 * IllegalStateException("document is closed!") under Robolectric's shadow, because
 * the native PDF writer is not available. This test runs on a device or emulator.
 *
 *   ./gradlew connectedDebugAndroidTest
 */
@RunWith(AndroidJUnit4::class)
class PdfExportInstrumentedTest {

    private fun blocks() = listOf(
        DocumentBlock(type = BlockType.HEADING, headingLevel = 1, runs = listOf(TextRun("Title"))),
        DocumentBlock(runs = listOf(TextRun("Body text that should wrap across lines ".repeat(20)))),
        DocumentBlock(type = BlockType.TABLE, table = TableData(2, 2, true, listOf("A", "B", "1", "2"))),
        DocumentBlock(type = BlockType.PAGE_BREAK),
        DocumentBlock(runs = listOf(TextRun("After the break")))
    )

    @Test
    fun pdfIsWellFormedAndPaginated() {
        val pdf = Exporters.toPdf(
            blocks(),
            PageSetup(header = "Head", footer = "Foot", pageNumbers = true, watermark = "DRAFT")
        )
        assertTrue(pdf.size > 1000)
        val text = String(pdf, Charsets.ISO_8859_1)
        assertEquals("%PDF", text.substring(0, 4))
        val pages = Regex("/Type\\s*/Page[^s]").findAll(text).count()
        assertTrue("expected at least 2 pages, got $pages", pages >= 2)
    }

    @Test
    fun pdfHandlesEmptyAndHugeDocuments() {
        val empty = Exporters.toPdf(emptyList(), PageSetup())
        assertTrue(String(empty, Charsets.ISO_8859_1).startsWith("%PDF"))

        val huge = (1..400).map { DocumentBlock(runs = listOf(TextRun("Line $it " + "x ".repeat(60)))) }
        val bytes = Exporters.toPdf(huge, PageSetup())
        assertTrue(bytes.size > 5000)
    }

    @Test
    fun everyPageSizeExports() {
        for (paper in listOf("A3", "A4", "A5", "Letter", "Legal")) {
            for (orientation in listOf("portrait", "landscape")) {
                val pdf = Exporters.toPdf(
                    blocks(),
                    PageSetup(paperSize = paper, orientation = orientation)
                )
                assertTrue("$paper $orientation", pdf.size > 500)
            }
        }
    }
}
