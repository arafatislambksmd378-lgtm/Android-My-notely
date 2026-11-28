package com.notely.app.doc

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Structured document model shared by editor, persistence, preview and export.
 * A document is a list of blocks; text blocks hold runs with inline formatting.
 */
enum class BlockType {
    PARAGRAPH, HEADING, BULLET, NUMBERED, CHECKLIST,
    IMAGE, TABLE, CHART, DIVIDER, DRAWING, FOOTNOTE, PAGE_BREAK,
    TEXTBOX, SHAPE, ATTACHMENT, VIDEO, AUDIO, SIGNATURE, TOC
}

data class TextRun(
    val text: String,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val strikethrough: Boolean = false,
    val fontFamily: String = "Default",
    val fontSizeSp: Float = 16f,
    val colorArgb: Int = 0xFF24232B.toInt(),
    val highlightArgb: Int? = null,
    val superscript: Boolean = false,
    val subscript: Boolean = false,
    val link: String? = null
) {
    fun toJson(): JSONObject = JSONObject()
        .put("t", text).put("b", bold).put("i", italic).put("u", underline)
        .put("s", strikethrough).put("ff", fontFamily).put("fs", fontSizeSp.toDouble())
        .put("c", colorArgb).put("h", highlightArgb ?: JSONObject.NULL)
        .put("sup", superscript).put("sub", subscript)
        .put("lk", link ?: JSONObject.NULL)

    /** Style equality ignoring the text itself (used to merge adjacent runs). */
    fun sameStyle(other: TextRun): Boolean =
        bold == other.bold && italic == other.italic && underline == other.underline &&
            strikethrough == other.strikethrough && fontFamily == other.fontFamily &&
            fontSizeSp == other.fontSizeSp && colorArgb == other.colorArgb &&
            highlightArgb == other.highlightArgb && superscript == other.superscript &&
            subscript == other.subscript && link == other.link

    companion object {
        fun fromJson(o: JSONObject): TextRun = TextRun(
            text = o.optString("t", ""),
            bold = o.optBoolean("b", false), italic = o.optBoolean("i", false),
            underline = o.optBoolean("u", false), strikethrough = o.optBoolean("s", false),
            fontFamily = o.optString("ff", "Default"), fontSizeSp = o.optDouble("fs", 16.0).toFloat(),
            colorArgb = o.optInt("c", 0xFF24232B.toInt()),
            highlightArgb = if (o.isNull("h")) null else o.optInt("h", 0),
            superscript = o.optBoolean("sup", false), subscript = o.optBoolean("sub", false),
            link = if (o.isNull("lk")) null else o.optString("lk", null)
        )
    }
}

data class TableData(
    val rows: Int, val cols: Int, val headerRow: Boolean, val cells: List<String>,
    val headerBgArgb: Int? = null
)

data class ChartData(val kind: String, val title: String, val labels: List<String>, val values: List<Double>)

data class DocumentBlock(
    val id: String = UUID.randomUUID().toString(),
    val type: BlockType = BlockType.PARAGRAPH,
    val headingLevel: Int = 0, // 0 = normal, 1..8 = Heading 1..8
    val runs: List<TextRun> = emptyList(),
    val align: Int = 0, // 0 left, 1 center, 2 right, 3 justify
    val lineSpacing: Float = 1.55f,
    val indentLevel: Int = 0,
    val checked: Boolean = false,
    val imagePath: String? = null,
    val imageWidthPercent: Int = 100,
    val rotationDegrees: Int = 0,
    val caption: String = "",
    val table: TableData? = null,
    val chart: ChartData? = null,
    val drawingPath: String? = null,
    val footnoteText: String = "",
    val footnoteRef: Int = 0,
    val textColorArgb: Int? = null,
    val extra: String = "" // shape kind / attachment path / textbox text / video-audio uri etc.
) {
    fun plainText(): String = when (type) {
        BlockType.IMAGE -> caption
        BlockType.TABLE -> table?.cells?.joinToString(" ") ?: ""
        BlockType.CHART -> chart?.title ?: ""
        BlockType.FOOTNOTE -> footnoteText
        BlockType.DIVIDER, BlockType.PAGE_BREAK, BlockType.TOC -> ""
        BlockType.DRAWING, BlockType.SIGNATURE -> caption
        BlockType.ATTACHMENT, BlockType.AUDIO, BlockType.VIDEO -> ""
        else -> runs.joinToString("") { it.text }
    }

    /** Structural (non-text) blocks are skipped by text statistics. */
    fun isTextBlock(): Boolean = when (type) {
        BlockType.DIVIDER, BlockType.PAGE_BREAK, BlockType.IMAGE, BlockType.CHART,
        BlockType.DRAWING, BlockType.SIGNATURE, BlockType.ATTACHMENT, BlockType.AUDIO,
        BlockType.VIDEO, BlockType.TOC -> false
        else -> true
    }

    fun toJson(): JSONObject {
        val o = JSONObject()
        o.put("id", id).put("type", type.name).put("hl", headingLevel)
        o.put("align", align).put("ls", lineSpacing.toDouble()).put("ind", indentLevel).put("chk", checked)
        o.put("img", imagePath ?: JSONObject.NULL).put("iw", imageWidthPercent)
        o.put("rot", rotationDegrees).put("cap", caption)
        o.put("draw", drawingPath ?: JSONObject.NULL)
        o.put("fn", footnoteText).put("fnr", footnoteRef)
        o.put("tc", textColorArgb ?: JSONObject.NULL).put("ex", extra)
        val ra = JSONArray(); runs.forEach { ra.put(it.toJson()) }; o.put("runs", ra)
        table?.let { t ->
            o.put("tbl", JSONObject().put("r", t.rows).put("c", t.cols).put("hr", t.headerRow)
                .put("cells", JSONArray(t.cells)).put("hbg", t.headerBgArgb ?: JSONObject.NULL))
        }
        chart?.let { c ->
            o.put("cht", JSONObject().put("k", c.kind).put("t", c.title)
                .put("lb", JSONArray(c.labels)).put("vl", JSONArray(c.values)))
        }
        return o
    }

    companion object {
        fun fromJson(o: JSONObject): DocumentBlock {
            val runs = mutableListOf<TextRun>()
            val ra = o.optJSONArray("runs")
            if (ra != null) for (i in 0 until ra.length()) runs.add(TextRun.fromJson(ra.getJSONObject(i)))
            val tbl = if (o.isNull("tbl")) null else o.optJSONObject("tbl")?.let { t ->
                val cells = mutableListOf<String>()
                val ca = t.optJSONArray("cells")
                if (ca != null) for (i in 0 until ca.length()) cells.add(ca.optString(i, ""))
                TableData(t.optInt("r", 1), t.optInt("c", 1), t.optBoolean("hr", true), cells,
                    if (t.isNull("hbg")) null else t.optInt("hbg", 0))
            }
            val cht = if (o.isNull("cht")) null else o.optJSONObject("cht")?.let { c ->
                val lb = mutableListOf<String>(); val la = c.optJSONArray("lb")
                if (la != null) for (i in 0 until la.length()) lb.add(la.optString(i, ""))
                val vl = mutableListOf<Double>(); val va = c.optJSONArray("vl")
                if (va != null) for (i in 0 until va.length()) vl.add(va.optDouble(i))
                ChartData(c.optString("k", "bar"), c.optString("t", ""), lb, vl)
            }
            return DocumentBlock(
                id = o.optString("id", UUID.randomUUID().toString()),
                type = runCatching { BlockType.valueOf(o.optString("type", "PARAGRAPH")) }.getOrDefault(BlockType.PARAGRAPH),
                headingLevel = o.optInt("hl", 0), runs = runs,
                align = o.optInt("align", 0), lineSpacing = o.optDouble("ls", 1.55).toFloat(),
                indentLevel = o.optInt("ind", 0), checked = o.optBoolean("chk", false),
                imagePath = if (o.isNull("img")) null else o.optString("img", null),
                imageWidthPercent = o.optInt("iw", 100),
                rotationDegrees = o.optInt("rot", 0),
                caption = o.optString("cap", ""),
                table = tbl, chart = cht,
                drawingPath = if (o.isNull("draw")) null else o.optString("draw", null),
                footnoteText = o.optString("fn", ""), footnoteRef = o.optInt("fnr", 0),
                textColorArgb = if (o.isNull("tc")) null else o.optInt("tc", 0),
                extra = o.optString("ex", "")
            )
        }
    }
}

data class PageSetup(
    val orientation: String = "portrait", // portrait | landscape
    val paperSize: String = "A4", // A4 A3 A5 Letter Legal Custom
    val customWidthMm: Float = 210f, val customHeightMm: Float = 297f,
    val marginTop: Int = 48, val marginBottom: Int = 48, val marginLeft: Int = 48, val marginRight: Int = 48,
    val pageColorArgb: Int = 0xFFFFFFFF.toInt(),
    val pageBorder: Boolean = false,
    val borderColorArgb: Int = 0xFFECEAF1.toInt(),
    val header: String = "", val footer: String = "",
    val pageNumbers: Boolean = false, val pageNumberPosition: String = "bottom", // bottom | top
    val lineSpacing: Float = 1.55f, val watermark: String = ""
) {
    /** Portrait page width/height in millimetres. */
    fun sizeMm(): Pair<Float, Float> {
        val (w, h) = when (paperSize) {
            "A3" -> 297f to 420f
            "A5" -> 148f to 210f
            "Letter" -> 215.9f to 279.4f
            "Legal" -> 215.9f to 355.6f
            "Custom" -> customWidthMm to customHeightMm
            else -> 210f to 297f
        }
        return if (orientation == "landscape") h to w else w to h
    }

    fun toJson(): JSONObject = JSONObject()
        .put("ori", orientation).put("paper", paperSize)
        .put("cw", customWidthMm.toDouble()).put("ch", customHeightMm.toDouble())
        .put("mt", marginTop).put("mb", marginBottom).put("ml", marginLeft).put("mr", marginRight)
        .put("pc", pageColorArgb).put("pb", pageBorder).put("pbc", borderColorArgb)
        .put("hd", header).put("ft", footer)
        .put("pn", pageNumbers).put("pnp", pageNumberPosition).put("ls", lineSpacing.toDouble()).put("wm", watermark)

    companion object {
        fun fromJson(o: JSONObject?): PageSetup = if (o == null) PageSetup() else PageSetup(
            orientation = o.optString("ori", "portrait"), paperSize = o.optString("paper", "A4"),
            customWidthMm = o.optDouble("cw", 210.0).toFloat(), customHeightMm = o.optDouble("ch", 297.0).toFloat(),
            marginTop = o.optInt("mt", 48), marginBottom = o.optInt("mb", 48),
            marginLeft = o.optInt("ml", 48), marginRight = o.optInt("mr", 48),
            pageColorArgb = o.optInt("pc", 0xFFFFFFFF.toInt()),
            pageBorder = o.optBoolean("pb", false), borderColorArgb = o.optInt("pbc", 0xFFECEAF1.toInt()),
            header = o.optString("hd", ""), footer = o.optString("ft", ""),
            pageNumbers = o.optBoolean("pn", false), pageNumberPosition = o.optString("pnp", "bottom"),
            lineSpacing = o.optDouble("ls", 1.55).toFloat(), watermark = o.optString("wm", "")
        )
    }
}

object DocumentSerializer {
    fun serialize(blocks: List<DocumentBlock>, pageSetup: PageSetup): String {
        val root = JSONObject()
        val arr = JSONArray(); blocks.forEach { arr.put(it.toJson()) }
        root.put("v", 1).put("blocks", arr).put("page", pageSetup.toJson())
        return root.toString()
    }

    /** Strict read for any editable document. Never turn lost/unknown content into a blank save. */
    fun deserializeForEditing(json: String?): Pair<List<DocumentBlock>, PageSetup> {
        if (json.isNullOrBlank()) return emptyList<DocumentBlock>() to PageSetup()
        val root = JSONObject(json)
        require(root.optInt("v", 1) == 1) { "Unsupported document version" }
        val arr = root.getJSONArray("blocks")
        val ids = mutableSetOf<String>()
        val blocks = (0 until arr.length()).map { index ->
            val obj = arr.getJSONObject(index)
            // Unknown block types must not silently become paragraphs.
            BlockType.valueOf(obj.getString("type"))
            val block = DocumentBlock.fromJson(obj)
            require(block.id.isNotBlank() && ids.add(block.id)) { "Invalid or duplicate block ID" }
            block.table?.let { table ->
                require(table.rows > 0 && table.cols > 0 &&
                    table.rows.toLong() * table.cols == table.cells.size.toLong()) { "Invalid table structure" }
            }
            block.chart?.let { chart ->
                require(chart.labels.size == chart.values.size && chart.values.all { it.isFinite() }) {
                    "Invalid chart structure"
                }
            }
            block
        }
        require(!root.has("page") || root.optJSONObject("page") != null) { "Invalid page setup" }
        return blocks to PageSetup.fromJson(root.optJSONObject("page"))
    }

    /** Best-effort preview only. NEVER use this result to overwrite the stored document. */
    fun deserialize(json: String?): Pair<List<DocumentBlock>, PageSetup> {
        if (json.isNullOrBlank()) return emptyList<DocumentBlock>() to PageSetup()
        return runCatching {
            val root = JSONObject(json)
            val arr = root.optJSONArray("blocks") ?: JSONArray()
            val blocks = (0 until arr.length()).mapNotNull { i ->
                arr.optJSONObject(i)?.let { runCatching { DocumentBlock.fromJson(it) }.getOrNull() }
            }
            blocks to PageSetup.fromJson(root.optJSONObject("page"))
        }.getOrElse { emptyList<DocumentBlock>() to PageSetup() }
    }

    fun plainText(blocks: List<DocumentBlock>): String =
        blocks.filter { it.isTextBlock() }.map { it.plainText() }.filter { it.isNotBlank() }.joinToString("\n")

    fun preview(blocks: List<DocumentBlock>): String =
        blocks.firstOrNull { it.plainText().isNotBlank() }?.plainText()?.take(160) ?: ""

    /** Headings used by the automatic table of contents. */
    fun headings(blocks: List<DocumentBlock>): List<Pair<Int, String>> =
        blocks.filter { it.type == BlockType.HEADING && it.headingLevel in 1..8 && it.plainText().isNotBlank() }
            .map { it.headingLevel to it.plainText() }
}

data class DocStats(
    val words: Int, val chars: Int, val charsNoSpaces: Int,
    val sentences: Int, val paragraphs: Int, val pages: Int
)

object DocumentStats {
    fun compute(blocks: List<DocumentBlock>, pageSetup: PageSetup): DocStats {
        val text = DocumentSerializer.plainText(blocks)
        val words = if (text.isBlank()) 0 else text.trim().split(Regex("\\s+")).count { it.isNotBlank() }
        val chars = text.length
        val charsNoSpaces = text.replace(Regex("\\s"), "").length
        val sentences = if (text.isBlank()) 0 else text.split(Regex("[.!?।]+")).count { it.isNotBlank() }
        val paragraphs = blocks.count { it.isTextBlock() && it.plainText().isNotBlank() }
        // Approximate pagination: usable line width depends on paper size/orientation.
        val (wMm, hMm) = pageSetup.sizeMm()
        val usableMm = (hMm - pageSetup.marginTop - pageSetup.marginBottom).coerceAtLeast(40f)
        val linesPerPage = (usableMm / 6.5f).toInt().coerceAtLeast(8)
        val charsPerLine = (((wMm - pageSetup.marginLeft - pageSetup.marginRight).coerceAtLeast(40f)) / 2.3f).toInt().coerceAtLeast(12)
        val totalLines = blocks.sumOf { b ->
            if (!b.isTextBlock() || b.plainText().isBlank()) 0
            else maxOf(1, (b.plainText().length / charsPerLine) + 1)
        } + blocks.count { it.type == BlockType.PAGE_BREAK } * linesPerPage
        val pages = maxOf(1, (totalLines + linesPerPage - 1) / linesPerPage)
        return DocStats(words, chars, charsNoSpaces, sentences, paragraphs, pages)
    }
}

/**
 * Run-level editing helpers: keep inline formatting while typing, and apply a style
 * to exactly the selected range (or the whole block when there is no selection).
 */
object RunEditing {

    fun fullText(runs: List<TextRun>): String = runs.joinToString("") { it.text }

    fun merge(runs: List<TextRun>): List<TextRun> {
        val out = mutableListOf<TextRun>()
        runs.filter { it.text.isNotEmpty() }.forEach { r ->
            val last = out.lastOrNull()
            if (last != null && last.sameStyle(r)) out[out.size - 1] = last.copy(text = last.text + r.text)
            else out.add(r)
        }
        return out
    }

    /** Splits runs so that a boundary exists at [index]; returns (before, after). */
    fun splitAt(runs: List<TextRun>, index: Int): Pair<List<TextRun>, List<TextRun>> {
        val before = mutableListOf<TextRun>()
        val after = mutableListOf<TextRun>()
        var pos = 0
        for (r in runs) {
            val start = pos
            val end = pos + r.text.length
            pos = end
            when {
                end <= index -> before.add(r)
                start >= index -> after.add(r)
                else -> {
                    val cut = index - start
                    before.add(r.copy(text = r.text.substring(0, cut)))
                    after.add(r.copy(text = r.text.substring(cut)))
                }
            }
        }
        return before to after
    }

    fun runAt(runs: List<TextRun>, index: Int): TextRun? {
        var pos = 0
        for (r in runs) {
            if (index < pos + r.text.length) return r
            pos += r.text.length
        }
        return runs.lastOrNull()
    }

    /**
     * Rebuilds runs after the plain text changed, preserving the formatting of the
     * untouched prefix/suffix and styling newly typed text like the run at the caret.
     */
    fun reflow(runs: List<TextRun>, newText: String): List<TextRun> {
        val oldText = fullText(runs)
        if (oldText == newText) return runs
        if (runs.isEmpty()) return listOf(TextRun(newText))
        var prefix = 0
        val maxPrefix = minOf(oldText.length, newText.length)
        while (prefix < maxPrefix && oldText[prefix] == newText[prefix]) prefix++
        var suffix = 0
        val maxSuffix = minOf(oldText.length - prefix, newText.length - prefix)
        while (suffix < maxSuffix &&
            oldText[oldText.length - 1 - suffix] == newText[newText.length - 1 - suffix]
        ) suffix++
        val (left, rest) = splitAt(runs, prefix)
        val (_, right) = splitAt(rest, oldText.length - prefix - suffix)
        val inserted = newText.substring(prefix, newText.length - suffix)
        val style = runAt(runs, prefix.coerceAtMost((oldText.length - 1).coerceAtLeast(0))) ?: TextRun("")
        val out = mutableListOf<TextRun>()
        out.addAll(left)
        if (inserted.isNotEmpty()) out.add(style.copy(text = inserted))
        out.addAll(right)
        return merge(out)
    }

    /** Applies [transform] to runs overlapping [start, end) — or to the whole block when empty. */
    fun applyToRange(
        runs: List<TextRun>,
        start: Int,
        end: Int,
        transform: (TextRun) -> TextRun
    ): List<TextRun> {
        val full = fullText(runs)
        val s = start.coerceIn(0, full.length)
        val e = end.coerceIn(0, full.length)
        if (runs.isEmpty()) return runs
        if (s >= e) return runs.map(transform)
        val out = mutableListOf<TextRun>()
        var pos = 0
        for (r in runs) {
            val rs = pos
            val re = pos + r.text.length
            pos = re
            when {
                re <= s || rs >= e -> out.add(r)
                else -> {
                    val a = maxOf(rs, s)
                    val b = minOf(re, e)
                    if (a > rs) out.add(r.copy(text = r.text.substring(0, a - rs)))
                    if (b > a) out.add(transform(r.copy(text = r.text.substring(a - rs, b - rs))))
                    if (b < re) out.add(r.copy(text = r.text.substring(b - rs)))
                }
            }
        }
        return merge(out)
    }

    /** Replaces an absolute text range, preserving surrounding formatting. */
    fun replaceRange(runs: List<TextRun>, start: Int, end: Int, replacement: String): List<TextRun> {
        val full = fullText(runs)
        if (start < 0 || end > full.length || start > end) return runs
        val newText = full.substring(0, start) + replacement + full.substring(end)
        return reflow(runs, newText)
    }
}

/** Find & replace over the plain-text concatenation of blocks (block-aware). */
object FindReplace {
    data class Match(val blockIndex: Int, val start: Int, val end: Int)

    fun find(blocks: List<DocumentBlock>, query: String, matchCase: Boolean): List<Match> {
        if (query.isEmpty()) return emptyList()
        val out = mutableListOf<Match>()
        blocks.forEachIndexed { bi, b ->
            if (b.runs.isEmpty()) return@forEachIndexed
            val full = RunEditing.fullText(b.runs)
            var from = 0
            while (from <= full.length - query.length) {
                val idx = if (matchCase) full.indexOf(query, from) else full.indexOf(query, from, ignoreCase = true)
                if (idx < 0) break
                out.add(Match(bi, idx, idx + query.length))
                from = idx + maxOf(1, query.length)
            }
        }
        return out
    }

    fun replaceInBlock(block: DocumentBlock, start: Int, end: Int, replacement: String): DocumentBlock =
        block.copy(runs = RunEditing.replaceRange(block.runs, start, end, replacement))

    fun replaceAll(blocks: List<DocumentBlock>, query: String, replacement: String, matchCase: Boolean): List<DocumentBlock> {
        if (query.isEmpty()) return blocks
        return blocks.map { b ->
            if (b.runs.isEmpty()) return@map b
            val full = RunEditing.fullText(b.runs)
            val newText = if (matchCase) full.replace(query, replacement)
            else full.replace(Regex(Regex.escape(query), RegexOption.IGNORE_CASE), replacement)
            if (newText == full) b else b.copy(runs = RunEditing.reflow(b.runs, newText))
        }
    }
}
