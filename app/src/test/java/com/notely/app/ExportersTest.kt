package com.notely.app

import com.notely.app.doc.*
import com.notely.app.export.Exporters
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

// Robolectric supplies real android.graphics implementations (Paint, PdfDocument)
// so the PDF path is exercised for real instead of hitting "not mocked" stubs.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ExportersTest {

    private val blocks = listOf(
        DocumentBlock(type = BlockType.HEADING, headingLevel = 2, runs = listOf(TextRun("Title", bold = true))),
        DocumentBlock(runs = listOf(TextRun("Body ", bold = false), TextRun("text", bold = true))),
        DocumentBlock(type = BlockType.BULLET, runs = listOf(TextRun("Point"))),
        DocumentBlock(type = BlockType.TABLE, table = TableData(2, 2, true, listOf("H1", "H2", "a", "b"))),
        DocumentBlock(type = BlockType.FOOTNOTE, footnoteText = "note", footnoteRef = 1),
        DocumentBlock(type = BlockType.CHART, chart = ChartData("bar", "Sales", listOf("Q1", "Q2"), listOf(3.0, 5.0))),
        DocumentBlock(type = BlockType.PAGE_BREAK)
    )

    private fun zipNames(bytes: ByteArray): List<String> {
        val names = mutableListOf<String>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { z ->
            var e = z.nextEntry
            while (e != null) { names.add(e.name); e = z.nextEntry }
        }
        return names
    }

    private fun zipEntry(bytes: ByteArray, name: String): String? {
        ZipInputStream(ByteArrayInputStream(bytes)).use { z ->
            var e = z.nextEntry
            while (e != null) {
                if (e.name == name) return z.readBytes().toString(Charsets.UTF_8)
                e = z.nextEntry
            }
        }
        return null
    }

    @Test
    fun plainText() {
        val out = Exporters.toPlainText(blocks)
        assertTrue(out.contains("Title"))
        assertTrue(out.contains("• Point"))
        assertTrue(out.contains("H1"))
        assertTrue(out.contains("[1] note"))
        assertTrue(out.contains("Sales"))
    }

    @Test
    fun markdown() {
        val out = Exporters.toMarkdown(blocks)
        assertTrue(out.contains("**Title**"))
        assertTrue(out.contains("## "))          // heading level 2
        assertTrue(out.contains("**text**"))
        assertTrue(out.contains("- Point"))
        assertTrue(out.contains("| H1 | H2 |"))
        assertTrue(out.contains("[^1]: note"))
    }

    @Test
    fun markdownEscapesUserText() {
        val out = Exporters.toMarkdown(
            listOf(DocumentBlock(runs = listOf(TextRun("2 * 3 = 6 and snake_case"))))
        )
        // Literal asterisks/underscores must not become emphasis.
        assertTrue(out.contains("2 \\* 3 = 6"))
        assertTrue(out.contains("snake\\_case"))
    }

    @Test
    fun html() {
        val out = Exporters.toHtml(blocks, PageSetup())
        assertTrue(out.contains("<h2"))
        assertTrue(out.contains("<b>text</b>"))
        assertTrue(out.contains("<table"))
        assertTrue(out.contains("<sup>1</sup>"))
        assertTrue(out.startsWith("<!DOCTYPE html>"))
    }

    @Test
    fun rtf() {
        val out = Exporters.toRtf(blocks, PageSetup(header = "Head", pageNumbers = true))
        assertTrue(out.startsWith("{\\rtf1"))
        assertTrue(out.contains("Title"))
        assertTrue(out.contains("{\\header"))
        assertTrue(out.contains("PAGE"))
        assertTrue(out.endsWith("}\n") || out.endsWith("}"))
    }

    @Test
    fun rtfEscapesUnicodeForWord() {
        val out = Exporters.toRtf(
            listOf(DocumentBlock(runs = listOf(TextRun("বাংলা & {braces} \\")))), PageSetup()
        )
        // Pure ASCII output with numeric escapes and no raw braces leaking out.
        assertTrue(out.all { it.code < 128 })
        assertTrue(out.contains("\\u"))
        assertTrue(out.contains("\\{"))
        assertTrue(out.contains("\\}"))
        assertTrue(out.contains("\\\\"))
    }

    @Test
    fun htmlEscapesTableOfContentsHeadings() {
        val html = Exporters.toHtml(
            listOf(
                DocumentBlock(type = BlockType.TOC),
                DocumentBlock(type = BlockType.HEADING, headingLevel = 2, runs = listOf(TextRun("<script>alert(1)</script>")))
            ),
            PageSetup()
        )
        assertFalse(html.contains("<script>"))
        assertTrue(html.contains("&lt;script&gt;"))
    }

    @Test
    fun docxIsValidPackageWithContent() {
        val bytes = Exporters.toDocx(blocks, PageSetup(header = "H", pageNumbers = true))
        val names = zipNames(bytes)
        assertTrue(names.contains("word/document.xml"))
        assertTrue(names.contains("[Content_Types].xml"))
        assertTrue(names.contains("_rels/.rels"))
        assertTrue(names.contains("word/_rels/document.xml.rels"))
        assertTrue(names.contains("word/styles.xml"))
        assertTrue(names.contains("word/numbering.xml"))
        assertTrue(names.contains("word/header1.xml"))
        val doc = zipEntry(bytes, "word/document.xml")!!
        assertTrue(doc.contains("Heading2"))
        assertTrue(doc.contains("Title"))
        assertTrue(doc.contains("<w:tbl>"))
        assertTrue(doc.contains("w:type=\"page\""))
        // Every r:id used in the document must exist in the rels part.
        val rels = zipEntry(bytes, "word/_rels/document.xml.rels")!!
        Regex("r:id=\"(rId[^\"]+)\"").findAll(doc).map { it.groupValues[1] }.forEach { id ->
            assertTrue("missing relationship $id", rels.contains("Id=\"$id\""))
        }
    }

    @Test
    fun odtIsValidPackageWithContent() {
        val bytes = Exporters.toOdt(blocks, PageSetup(paperSize = "A5"))
        val names = zipNames(bytes)
        assertTrue(names.contains("content.xml"))
        assertTrue(names.contains("mimetype"))
        // ODF requires mimetype to be the first entry, stored uncompressed.
        assertEquals("mimetype", names.first())
        val content = zipEntry(bytes, "content.xml")!!
        assertTrue(content.contains("<office:document-content"))
        assertTrue(content.contains("<text:h"))
        assertTrue(content.contains("<table:table"))
        assertEquals("application/vnd.oasis.opendocument.text", zipEntry(bytes, "mimetype"))
    }

    // PDF output is covered by PdfExportInstrumentedTest (androidTest): the native
    // PdfDocument writer is unavailable on the JVM, so it cannot be asserted here.

    @Test
    fun safeName() {
        assertEquals("My_Note", Exporters.safeName("My Note!"))
        assertEquals("note", Exporters.safeName("!!!"))
        assertFalse(Exporters.safeName("a/b:c*d").contains("/"))
        assertTrue(Exporters.safeName("a".repeat(300)).length <= 60)
        assertEquals("CON_note", Exporters.safeName("CON"))
        assertEquals("Trailing", Exporters.safeName("Trailing."))
        assertEquals("বাংলা_নোট", Exporters.safeName("বাংলা নোট"))
    }
}
