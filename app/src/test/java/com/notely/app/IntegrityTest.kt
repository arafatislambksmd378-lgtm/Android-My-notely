package com.notely.app

import com.notely.app.doc.*
import com.notely.app.util.Security
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Data-integrity tests for the parts of the document pipeline that touch
 * Android framework classes (base64 in the PIN hash, PDF generation).
 *
 * These run on the JVM through Robolectric, so they need no emulator.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class IntegrityTest {

    // ---------- PIN storage ----------

    @Test
    fun pinHashIsSaltedAndVerifiable() {
        val a = Security.hashPin("1234")
        val b = Security.hashPin("1234")
        assertNotEquals("the same PIN must not produce the same hash", a, b)
        assertTrue(a.startsWith("pbkdf2\$"))
        assertTrue(Security.verifyPin("1234", a))
        assertTrue(Security.verifyPin("1234", b))
        assertFalse(Security.verifyPin("1235", a))
        assertFalse(Security.verifyPin("", a))
    }

    @Test
    fun legacySha256HashStillUnlocksAndIsDetected() {
        val legacy = java.security.MessageDigest.getInstance("SHA-256")
            .digest("4321".toByteArray()).joinToString("") { "%02x".format(it) }
        assertTrue(Security.isLegacyHash(legacy))
        assertTrue(Security.verifyPin("4321", legacy))
        assertFalse(Security.verifyPin("0000", legacy))
        assertFalse(Security.isLegacyHash(Security.hashPin("4321")))
    }

    @Test
    fun corruptStoredHashNeverUnlocks() {
        assertFalse(Security.verifyPin("1234", "pbkdf2\$notanumber\$abc\$def"))
        assertFalse(Security.verifyPin("1234", "pbkdf2\$1000\$!!!\$!!!"))
        assertFalse(Security.verifyPin("1234", ""))
    }

    // ---------- Document round-trip with every element ----------

    @Test
    fun everyBlockTypeSurvivesTwoSaveLoadCycles() {
        val blocks = listOf(
            DocumentBlock(type = BlockType.HEADING, headingLevel = 1,
                runs = listOf(TextRun("Chapter One", bold = true, fontSizeSp = 28f, colorArgb = 0xFF8950F5.toInt()))),
            DocumentBlock(runs = listOf(
                TextRun("plain "),
                TextRun("bold", bold = true),
                TextRun(" italic", italic = true),
                TextRun(" underline", underline = true),
                TextRun(" strike", strikethrough = true),
                TextRun(" link", link = "https://example.com"),
                TextRun(" sup", superscript = true),
                TextRun(" sub", subscript = true),
                TextRun(" highlight", highlightArgb = 0xFFFFF59D.toInt()),
                TextRun(" labelled", fontFamily = "Serif", fontSizeSp = 20f)
            )),
            DocumentBlock(type = BlockType.BULLET, indentLevel = 2, runs = listOf(TextRun("bullet"))),
            DocumentBlock(type = BlockType.NUMBERED, runs = listOf(TextRun("number"))),
            DocumentBlock(type = BlockType.CHECKLIST, checked = true, runs = listOf(TextRun("done"))),
            DocumentBlock(type = BlockType.IMAGE, imagePath = "/tmp/a.jpg", caption = "cap",
                imageWidthPercent = 80, rotationDegrees = 90, align = 1),
            DocumentBlock(type = BlockType.DRAWING, drawingPath = "/tmp/d.png", caption = "sketch"),
            DocumentBlock(type = BlockType.SIGNATURE, drawingPath = "/tmp/s.png"),
            DocumentBlock(type = BlockType.TABLE,
                table = TableData(2, 3, true, listOf("h1", "h2", "h3", "a", "b", "c"))),
            DocumentBlock(type = BlockType.CHART,
                chart = ChartData("pie", "Share", listOf("x", "y"), listOf(1.5, 2.5))),
            DocumentBlock(type = BlockType.SHAPE, extra = "triangle"),
            DocumentBlock(type = BlockType.TEXTBOX, runs = listOf(TextRun("boxed"))),
            DocumentBlock(type = BlockType.FOOTNOTE, footnoteText = "note", footnoteRef = 3),
            DocumentBlock(type = BlockType.DIVIDER),
            DocumentBlock(type = BlockType.PAGE_BREAK),
            DocumentBlock(type = BlockType.TOC),
            DocumentBlock(type = BlockType.ATTACHMENT, extra = "/tmp/file.pdf"),
            DocumentBlock(type = BlockType.AUDIO, extra = "/tmp/a.mp3"),
            DocumentBlock(type = BlockType.VIDEO, extra = "/tmp/v.mp4"),
            DocumentBlock(type = BlockType.PARAGRAPH, runs = listOf(TextRun("tail")))
        )
        val page = PageSetup(
            orientation = "landscape", paperSize = "Legal", customWidthMm = 210f, customHeightMm = 297f,
            marginTop = 10, marginBottom = 20, marginLeft = 30, marginRight = 40,
            pageColorArgb = 0xFFF8F7FB.toInt(), pageBorder = true, borderColorArgb = 0xFF8950F5.toInt(),
            header = "Header", footer = "Footer", pageNumbers = true, pageNumberPosition = "top",
            lineSpacing = 1.8f, watermark = "CONFIDENTIAL"
        )

        // Save -> load -> save -> load, exactly what an app restart does.
        val first = DocumentSerializer.serialize(blocks, page)
        val (loadedOnce, pageOnce) = DocumentSerializer.deserialize(first)
        val second = DocumentSerializer.serialize(loadedOnce, pageOnce)
        val (loadedTwice, pageTwice) = DocumentSerializer.deserialize(second)

        assertEquals(blocks.size, loadedOnce.size)
        assertEquals(blocks.size, loadedTwice.size)
        assertEquals(blocks.map { it.type }, loadedTwice.map { it.type })
        assertEquals(second, DocumentSerializer.serialize(loadedTwice, pageTwice))

        val runs = loadedTwice[1].runs
        assertEquals(10, runs.size)
        assertEquals("bold", runs[1].text.also { assertTrue(runs[1].bold) }.let { it })
        assertTrue(runs[2].italic)
        assertTrue(runs[3].underline)
        assertTrue(runs[4].strikethrough)
        assertEquals("https://example.com", runs[5].link)
        assertTrue(runs[6].superscript)
        assertTrue(runs[7].subscript)
        assertEquals(0xFFFFF59D.toInt(), runs[8].highlightArgb)
        assertEquals("Serif", runs[9].fontFamily)
        assertEquals(20f, runs[9].fontSizeSp, 0.01f)

        assertEquals(2, loadedTwice[2].indentLevel)
        assertTrue(loadedTwice[4].checked)
        assertEquals("cap", loadedTwice[5].caption)
        assertEquals(80, loadedTwice[5].imageWidthPercent)
        assertEquals(90, loadedTwice[5].rotationDegrees)
        assertEquals("/tmp/d.png", loadedTwice[6].drawingPath)
        assertEquals(3, loadedTwice[8].table?.cols)
        assertEquals("c", loadedTwice[8].table?.cells?.get(5))
        assertEquals("pie", loadedTwice[9].chart?.kind)
        assertEquals(2.5, loadedTwice[9].chart?.values?.get(1) ?: 0.0, 0.001)
        assertEquals("triangle", loadedTwice[10].extra)
        assertEquals(3, loadedTwice[12].footnoteRef)

        assertEquals("landscape", pageTwice.orientation)
        assertEquals("Legal", pageTwice.paperSize)
        assertEquals(10, pageTwice.marginTop)
        assertEquals(40, pageTwice.marginRight)
        assertEquals(0xFFF8F7FB.toInt(), pageTwice.pageColorArgb)
        assertTrue(pageTwice.pageBorder)
        assertEquals(0xFF8950F5.toInt(), pageTwice.borderColorArgb)
        assertEquals("top", pageTwice.pageNumberPosition)
        assertEquals(1.8f, pageTwice.lineSpacing, 0.01f)
        assertEquals("CONFIDENTIAL", pageTwice.watermark)
    }

    @Test
    fun corruptElementIsSkippedNotFatal() {
        val good = DocumentSerializer.serialize(listOf(DocumentBlock(runs = listOf(TextRun("keep me")))), PageSetup())
        // Splice a non-object entry into the blocks array.
        val broken = good.replaceFirst("\"blocks\":[", "\"blocks\":[42,")
        val (blocks, _) = DocumentSerializer.deserialize(broken)
        assertEquals(1, blocks.size)
        assertEquals("keep me", blocks[0].plainText())
    }

    // ---------- Exporters that need the framework ----------

    @Test
    fun pdfExporterProducesMultiplePagesWithHeaderAndFooter() {
        val many = (1..120).map { DocumentBlock(runs = listOf(TextRun("Line $it " + "content ".repeat(8)))) }
        val bytes = com.notely.app.export.Exporters.toPdf(
            many, PageSetup(header = "Notely", footer = "Confidential", pageNumbers = true)
        )
        assertTrue("pdf should be non-trivial", bytes.size > 2000)
        val text = String(bytes, Charsets.ISO_8859_1)
        assertEquals("%PDF", text.substring(0, 4))
        val pageCount = Regex("/Type\\s*/Page[^s]").findAll(text).count()
        assertTrue("expected several pages, found $pageCount", pageCount >= 2)
    }

    @Test
    fun drawingExportIsAnActualPng() {
        // A real 1x1 PNG, written the way DrawingScreen writes drawings.
        val png = java.util.Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg=="
        )
        val file = java.io.File.createTempFile("draw_", ".png")
        file.writeBytes(png)
        val blocks = listOf(
            DocumentBlock(type = BlockType.DRAWING, drawingPath = file.absolutePath),
            DocumentBlock(type = BlockType.IMAGE, imagePath = file.absolutePath)
        )
        val docx = com.notely.app.export.Exporters.toDocx(blocks, PageSetup())
        assertTrue("docx must embed the media part", containsEntry(docx, "word/media/image1.png"))
        val odt = com.notely.app.export.Exporters.toOdt(blocks, PageSetup())
        assertTrue("odt must embed the picture", containsEntry(odt, "Pictures/image1.png"))
        file.delete()
    }

    private fun containsEntry(zip: ByteArray, name: String): Boolean {
        java.util.zip.ZipInputStream(java.io.ByteArrayInputStream(zip)).use { z ->
            var e = z.nextEntry
            while (e != null) {
                if (e.name == name) return true
                e = z.nextEntry
            }
        }
        return false
    }
}
