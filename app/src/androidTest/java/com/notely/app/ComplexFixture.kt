package com.notely.app

import android.content.Context
import android.graphics.Bitmap
import com.notely.app.doc.*
import java.io.File
import java.util.UUID

/** Fixture coverage is separate from UI insertion coverage. Sections/cell rich text aren't modeled. */
object ComplexFixture {
    fun create(context: Context): Pair<List<DocumentBlock>, PageSetup> {
        val folder = File(context.filesDir, "attachments/test_${UUID.randomUUID()}").apply { check(mkdirs()) }
        val image = File(folder, "image.png")
        val bmp = Bitmap.createBitmap(12, 12, Bitmap.Config.ARGB_8888)
        image.outputStream().use { check(bmp.compress(Bitmap.CompressFormat.PNG, 100, it)) }; bmp.recycle()
        val drawing = File(folder, "drawing.png").apply { image.copyTo(this) }
        val attachment = File(folder, "attachment.txt").apply { writeText("private test attachment বাংলা") }
        val blocks = mutableListOf(DocumentBlock(id = "body", runs = listOf(TextRun("Initial paragraph"))))
        blocks += DocumentBlock(id = "title", type = BlockType.HEADING, headingLevel = 1, runs = listOf(TextRun("Title", fontSizeSp = 30f)))
        blocks += DocumentBlock(id = "subtitle", type = BlockType.HEADING, headingLevel = 2, runs = listOf(TextRun("Subtitle", fontSizeSp = 24f)))
        (1..8).forEach { blocks += DocumentBlock(id = "heading$it", type = BlockType.HEADING, headingLevel = it, runs = listOf(TextRun("Heading $it"))) }
        blocks += DocumentBlock(id = "styles", align = 2, indentLevel = 2, lineSpacing = 1.8f, runs = listOf(
            TextRun("bold", bold = true), TextRun("italic", italic = true), TextRun("under", underline = true),
            TextRun("strike", strikethrough = true), TextRun("font", fontFamily = "Serif", fontSizeSp = 22f, colorArgb = 0xFF8950F5.toInt(), highlightArgb = 0xFFF2C94C.toInt()),
            TextRun("sup", superscript = true), TextRun("sub", subscript = true), TextRun("link বাংলা", link = "https://example.com")))
        blocks += DocumentBlock(id = "bullet", type = BlockType.BULLET, indentLevel = 2, runs = listOf(TextRun("nested")))
        blocks += DocumentBlock(id = "number", type = BlockType.NUMBERED, runs = listOf(TextRun("numbered")))
        blocks += DocumentBlock(id = "check", type = BlockType.CHECKLIST, checked = true, runs = listOf(TextRun("done")))
        blocks += DocumentBlock(id = "image", type = BlockType.IMAGE, imagePath = image.path, imageWidthPercent = 65, rotationDegrees = 90, caption = "test image")
        blocks += DocumentBlock(id = "table", type = BlockType.TABLE, table = TableData(2, 2, true, listOf("A", "B", "1", "2"), 0xFFF0E9FF.toInt()))
        blocks += DocumentBlock(id = "chart", type = BlockType.CHART, chart = ChartData("bar", "Data", listOf("A", "B"), listOf(1.0, 2.0)))
        blocks += DocumentBlock(id = "shape", type = BlockType.SHAPE, extra = "rectangle")
        blocks += DocumentBlock(id = "textbox", type = BlockType.TEXTBOX, runs = listOf(TextRun("textbox")))
        blocks += DocumentBlock(id = "drawing", type = BlockType.DRAWING, drawingPath = drawing.path, caption = "handwriting raster")
        blocks += DocumentBlock(id = "attachment", type = BlockType.ATTACHMENT, extra = attachment.path)
        blocks += DocumentBlock(id = "footnote", type = BlockType.FOOTNOTE, footnoteRef = 1, footnoteText = "Footnote বাংলা")
        blocks += DocumentBlock(id = "break", type = BlockType.PAGE_BREAK)
        return blocks to PageSetup(orientation = "landscape", paperSize = "Legal", marginTop = 12, marginLeft = 24,
            header = "Header বাংলা", footer = "Footer", pageNumbers = true, pageBorder = true, watermark = "TEST")
    }
}
