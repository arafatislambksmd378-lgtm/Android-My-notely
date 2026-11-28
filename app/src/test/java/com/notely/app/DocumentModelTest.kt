package com.notely.app

import com.notely.app.doc.*
import org.junit.Assert.*
import org.junit.Test

class DocumentModelTest {

    private fun sample(): List<DocumentBlock> = listOf(
        DocumentBlock(type = BlockType.HEADING, headingLevel = 1, runs = listOf(TextRun("Hello", bold = true, fontSizeSp = 28f))),
        DocumentBlock(runs = listOf(TextRun("World "), TextRun("bold", bold = true))),
        DocumentBlock(type = BlockType.BULLET, runs = listOf(TextRun("Item one"))),
        DocumentBlock(type = BlockType.TABLE, table = TableData(2, 2, true, listOf("A", "B", "1", "2"))),
        DocumentBlock(type = BlockType.CHART, chart = ChartData("bar", "T", listOf("X"), listOf(3.0))),
        DocumentBlock(type = BlockType.FOOTNOTE, footnoteText = "ref text", footnoteRef = 1),
        DocumentBlock(type = BlockType.DIVIDER)
    )

    @Test
    fun serializeDeserializeRoundtrip() {
        val blocks = sample()
        val page = PageSetup(
            orientation = "landscape", paperSize = "A3", header = "H", footer = "F",
            pageNumbers = true, watermark = "DRAFT", pageBorder = true, marginTop = 72
        )
        val json = DocumentSerializer.serialize(blocks, page)
        val (restored, restoredPage) = DocumentSerializer.deserialize(json)
        assertEquals(blocks.size, restored.size)
        assertEquals(BlockType.HEADING, restored[0].type)
        assertEquals(1, restored[0].headingLevel)
        assertEquals("Hello", restored[0].runs[0].text)
        assertTrue(restored[0].runs[0].bold)
        assertEquals("World bold", restored[1].plainText())
        assertTrue(restored[1].runs[1].bold)
        assertEquals(2, restored[3].table?.rows)
        assertEquals("B", restored[3].table?.cells?.get(1))
        assertEquals("bar", restored[4].chart?.kind)
        assertEquals(3.0, restored[4].chart?.values?.get(0) ?: -1.0, 0.001)
        assertEquals("landscape", restoredPage.orientation)
        assertEquals("A3", restoredPage.paperSize)
        assertEquals("H", restoredPage.header)
        assertEquals("F", restoredPage.footer)
        assertEquals("DRAFT", restoredPage.watermark)
        assertTrue(restoredPage.pageBorder)
        assertEquals(72, restoredPage.marginTop)
        assertTrue(restoredPage.pageNumbers)
    }

    @Test
    fun imageBlockRoundtrip() {
        val block = DocumentBlock(
            type = BlockType.IMAGE, imagePath = "/data/img.jpg", caption = "Sunset",
            imageWidthPercent = 75, rotationDegrees = 90, align = 1
        )
        val (restored, _) = DocumentSerializer.deserialize(DocumentSerializer.serialize(listOf(block), PageSetup()))
        assertEquals(BlockType.IMAGE, restored[0].type)
        assertEquals("/data/img.jpg", restored[0].imagePath)
        assertEquals("Sunset", restored[0].caption)
        assertEquals(75, restored[0].imageWidthPercent)
        assertEquals(90, restored[0].rotationDegrees)
        assertEquals(1, restored[0].align)
    }

    @Test
    fun deserializeBlankIsEmpty() {
        assertTrue(DocumentSerializer.deserialize("").first.isEmpty())
        assertTrue(DocumentSerializer.deserialize(null).first.isEmpty())
        assertTrue(DocumentSerializer.deserialize("{not json").first.isEmpty())
    }

    @Test
    fun statsCompute() {
        val stats = DocumentStats.compute(sample(), PageSetup())
        assertTrue(stats.words >= 5)
        assertTrue(stats.chars > 0)
        assertTrue(stats.charsNoSpaces <= stats.chars)
        assertTrue(stats.paragraphs >= 2)
        assertTrue(stats.pages >= 1)
    }

    @Test
    fun pageSetupSizes() {
        assertEquals(210f, PageSetup(paperSize = "A4").sizeMm().first, 0.1f)
        assertEquals(297f, PageSetup(paperSize = "A4", orientation = "landscape").sizeMm().first, 0.1f)
        assertEquals(297f, PageSetup(paperSize = "A3").sizeMm().first, 0.1f)
        assertEquals(148f, PageSetup(paperSize = "A5").sizeMm().first, 0.1f)
        assertEquals(215.9f, PageSetup(paperSize = "Letter").sizeMm().first, 0.2f)
        assertEquals(200f, PageSetup(paperSize = "Custom", customWidthMm = 200f).sizeMm().first, 0.1f)
    }

    @Test
    fun findAndReplace() {
        val blocks = listOf(DocumentBlock(runs = listOf(TextRun("Hello hello HELLO"))))
        assertEquals(3, FindReplace.find(blocks, "hello", matchCase = false).size)
        assertEquals(1, FindReplace.find(blocks, "hello", matchCase = true).size)
        assertEquals("bye bye bye", FindReplace.replaceAll(blocks, "hello", "bye", matchCase = false)[0].plainText())
        assertEquals("Hi hello HELLO", FindReplace.replaceInBlock(blocks[0], 0, 5, "Hi").plainText())
        assertEquals(0, FindReplace.find(blocks, "", matchCase = false).size)
    }

    @Test
    fun replaceAllPreservesRunFormatting() {
        val block = DocumentBlock(runs = listOf(TextRun("plain "), TextRun("boldword", bold = true)))
        val out = FindReplace.replaceAll(listOf(block), "boldword", "replaced", matchCase = true)
        val runs = out[0].runs
        // Surrounding text keeps its own formatting; the replaced text inherits the matched run.
        assertTrue(runs.any { it.bold })
        assertEquals("plain replaced", out[0].plainText())
    }

    @Test
    fun reflowPreservesFormattingWhileTyping() {
        val runs = listOf(TextRun("Hello "), TextRun("World", bold = true))
        val typed = RunEditing.reflow(runs, "Hello World!")
        assertEquals("Hello World!", typed.joinToString("") { it.text })
        assertTrue(typed.last().bold)
        assertFalse(typed.first().bold)
    }

    @Test
    fun applyToRangeStylesOnlySelection() {
        val runs = listOf(TextRun("abcdef"))
        val bolded = RunEditing.applyToRange(runs, 1, 3, { it.copy(bold = true) })
        assertEquals("abcdef", bolded.joinToString("") { it.text })
        assertEquals(3, bolded.size)
        assertFalse(bolded[0].bold)
        assertTrue(bolded[1].bold)
        assertFalse(bolded[2].bold)
    }

    @Test
    fun headingsFeedTableOfContents() {
        val blocks = listOf(
            DocumentBlock(type = BlockType.HEADING, headingLevel = 1, runs = listOf(TextRun("One"))),
            DocumentBlock(type = BlockType.HEADING, headingLevel = 3, runs = listOf(TextRun("Three"))),
            DocumentBlock(runs = listOf(TextRun("Body")))
        )
        val toc = DocumentSerializer.headings(blocks)
        assertEquals(2, toc.size)
        assertEquals(1 to "One", toc[0])
        assertEquals(3 to "Three", toc[1])
    }

    @Test
    fun plainTextAndPreview() {
        val blocks = sample()
        val text = DocumentSerializer.plainText(blocks)
        assertTrue(text.contains("Hello"))
        assertTrue(text.contains("Item one"))
        assertEquals("Hello", DocumentSerializer.preview(blocks))
    }
}
