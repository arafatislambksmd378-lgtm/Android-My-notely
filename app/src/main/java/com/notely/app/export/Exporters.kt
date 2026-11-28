package com.notely.app.export

import com.notely.app.util.SafeLinks

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import android.os.Build
import com.notely.app.doc.BlockType
import com.notely.app.doc.DocumentBlock
import com.notely.app.doc.DocumentSerializer
import com.notely.app.doc.PageSetup
import com.notely.app.doc.RunEditing
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.abs
import kotlin.math.min

/**
 * Real export engines: PDF, DOCX, ODT, RTF, HTML, Markdown, TXT — no extension renaming.
 * Images and drawings are embedded when a [Context] is available; every engine preserves
 * the closest representation the target format supports and never drops the source data
 * (the original document stays in Room).
 */
object Exporters {

    const val MIME_PDF = "application/pdf"
    const val MIME_DOCX = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    const val MIME_ODT = "application/vnd.oasis.opendocument.text"
    const val MIME_RTF = "application/rtf"
    const val MIME_HTML = "text/html"
    const val MIME_MD = "text/markdown"
    const val MIME_TXT = "text/plain"

    data class Format(val label: String, val ext: String, val mime: String)

    val formats = listOf(
        Format("PDF", "pdf", MIME_PDF),
        Format("Word (DOCX)", "docx", MIME_DOCX),
        Format("OpenDocument (ODT)", "odt", MIME_ODT),
        Format("Rich Text (RTF)", "rtf", MIME_RTF),
        Format("Web page (HTML)", "html", MIME_HTML),
        Format("Markdown", "md", MIME_MD),
        Format("Plain text", "txt", MIME_TXT)
    )

    fun exportDir(context: Context): File {
        val dir = File(context.filesDir, "exports")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    /**
     * Real image container type, sniffed from the file header. Camera/out-of-app
     * sources often produce JPEGs or WebPs whose name we do not control, and
     * DOCX/ODF readers trust the declared media type over the extension.
     */
    private fun imageKind(path: String): Pair<String, String> {
        val head = runCatching {
            File(path).inputStream().use { input ->
                val buf = ByteArray(12)
                val read = input.read(buf)
                if (read > 0) buf.copyOf(read) else ByteArray(0)
            }
        }.getOrNull() ?: ByteArray(0)
        fun startsWith(vararg bytes: Int): Boolean =
            head.size >= bytes.size && bytes.indices.all { (head[it].toInt() and 0xFF) == bytes[it] }
        return when {
            startsWith(0x89, 0x50, 0x4E, 0x47) -> "png" to "image/png"
            startsWith(0xFF, 0xD8, 0xFF) -> "jpg" to "image/jpeg"
            startsWith(0x47, 0x49, 0x46, 0x38) -> "gif" to "image/gif"
            startsWith(0x52, 0x49, 0x46, 0x46) &&
                head.size >= 12 && String(head, 8, 4, Charsets.US_ASCII) == "WEBP" -> "webp" to "image/webp"
            startsWith(0x42, 0x4D) -> "bmp" to "image/bmp"
            else -> "png" to "image/png"
        }
    }

    fun safeName(title: String): String {
        // Reserved names and control characters break FAT/exFAT and some file pickers.
        val reserved = setOf("CON", "PRN", "AUX", "NUL", "COM1", "COM2", "LPT1", "LPT2")
        val cleaned = title.trim()
            .filterNot { it.code < 0x20 || it in "\\/:*?\"<>|" }
            .filterNot { it in "!@#$%^&(){}[];'\u2019\u201c\u201d~`+=," }
            .trim()
            .trimEnd('.')
        val underscored = cleaned.replace(Regex("\\s+"), "_")
        val limited = underscored.ifBlank { "note" }.take(60).trimEnd('.', ' ')
        return if (limited.uppercase() in reserved) "${limited}_note" else limited.ifBlank { "note" }
    }

    /** Writes every supported format; returns the file. */
    fun export(context: Context, title: String, blocks: List<DocumentBlock>, pageSetup: PageSetup, format: Format): File {
        val base = safeName(title)
        val bytes = when (format.ext) {
            "pdf" -> toPdf(blocks, pageSetup, context)
            "docx" -> toDocx(blocks, pageSetup, context)
            "odt" -> toOdt(blocks, pageSetup, context)
            "rtf" -> toRtf(blocks, pageSetup).toByteArray(Charsets.UTF_8)
            "html" -> toHtml(blocks, pageSetup).toByteArray(Charsets.UTF_8)
            "md" -> toMarkdown(blocks).toByteArray(Charsets.UTF_8)
            else -> toPlainText(blocks).toByteArray(Charsets.UTF_8)
        }
        // Timestamped names keep repeated exports from silently overwriting the
        // file the user just shared.
        val stamp = java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.US).format(java.util.Date())
        return writeBytes(context, "$base-$stamp.${format.ext}", bytes)
    }

    fun writeBytes(context: Context, fileName: String, bytes: ByteArray): File {
        val f = File(exportDir(context), fileName)
        FileOutputStream(f).use { it.write(bytes) }
        return f
    }

    fun writeBitmap(context: Context, dirName: String, fileName: String, bmp: Bitmap): File {
        val dir = File(context.filesDir, dirName)
        if (!dir.exists()) dir.mkdirs()
        val f = File(dir, fileName)
        FileOutputStream(f).use { out -> bmp.compress(Bitmap.CompressFormat.PNG, 100, out) }
        return f
    }

    fun uriFor(context: Context, file: File) =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

    fun share(context: Context, file: File, mime: String, subject: String) {
        val uri = uriFor(context, file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, subject)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = android.content.ClipData.newRawUri(file.name, uri)
        }
        context.startActivity(Intent.createChooser(send, "Share ${file.name}").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun open(context: Context, file: File, mime: String) {
        val uri = uriFor(context, file)
        val view = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(view)
        } catch (e: Exception) {
            share(context, file, mime, file.name)
        }
    }

    // ---------- Plain text ----------
    fun toPlainText(blocks: List<DocumentBlock>): String =
        blocks.joinToString("\n") { b ->
            when (b.type) {
                BlockType.HEADING -> b.plainText()
                BlockType.BULLET -> "• ${b.plainText()}"
                BlockType.NUMBERED -> "- ${b.plainText()}"
                BlockType.CHECKLIST -> (if (b.checked) "[x] " else "[ ] ") + b.plainText()
                BlockType.TABLE -> b.table?.let { t ->
                    (0 until t.rows).joinToString("\n") { r ->
                        (0 until t.cols).joinToString(" | ") { c -> t.cells.getOrNull(r * t.cols + c) ?: "" }
                    }
                } ?: ""
                BlockType.FOOTNOTE -> "[${b.footnoteRef}] ${b.footnoteText}"
                BlockType.DIVIDER -> "---"
                BlockType.PAGE_BREAK -> "\n[Page break]\n"
                BlockType.CHART -> b.chart?.let { ch ->
                    ch.title + "\n" + ch.labels.indices.joinToString("\n") { i ->
                        "${ch.labels.getOrNull(i) ?: ""}: ${ch.values.getOrNull(i) ?: 0.0}"
                    }
                } ?: ""
                BlockType.TOC -> ""
                else -> b.plainText()
            }
        }

    // ---------- Markdown ----------
    fun toMarkdown(blocks: List<DocumentBlock>): String {
        // User text must not be able to inject Markdown syntax (a literal "*" in a
        // title would otherwise italicise the rest of the page).
        fun mdEsc(s: String): String = s
            .replace("\\", "\\\\")
            .replace("*", "\\*").replace("_", "\\_").replace("`", "\\`")
            .replace("#", "\\#").replace("|", "\\|").replace("[", "\\[").replace("]", "\\]")

        return blocks.joinToString("\n\n") { b ->
            val text = b.runs.joinToString("") { r ->
                var t = mdEsc(r.text)
                if (r.bold) t = "**$t**"
                if (r.italic) t = "*$t*"
                if (r.underline) t = "<u>$t</u>"
                if (r.strikethrough) t = "~~$t~~"
                if (r.link != null && SafeLinks.isAllowed(r.link)) t = "[$t](${r.link})"
                t
            }
            when (b.type) {
                BlockType.HEADING -> "#".repeat(b.headingLevel.coerceIn(1, 6)) + " " + text
                BlockType.BULLET -> "- $text"
                BlockType.NUMBERED -> "1. $text"
                BlockType.CHECKLIST -> (if (b.checked) "- [x] " else "- [ ] ") + text
                BlockType.TABLE -> b.table?.let { t ->
                    val lines = (0 until t.rows).map { r ->
                        "| " + (0 until t.cols).joinToString(" | ") { c ->
                            mdEsc(t.cells.getOrNull(r * t.cols + c) ?: "")
                        } + " |"
                    }
                    if (t.headerRow && lines.isNotEmpty())
                        lines.first() + "\n|" + List(t.cols) { " --- " }.joinToString("|") + "|\n" +
                            lines.drop(1).joinToString("\n")
                    else lines.joinToString("\n")
                } ?: ""
                BlockType.FOOTNOTE -> "[^${b.footnoteRef}]: ${b.footnoteText}"
                BlockType.DIVIDER -> "---"
                BlockType.PAGE_BREAK -> "\\pagebreak"
                BlockType.IMAGE -> "![${b.caption}](${b.imagePath ?: ""})"
                BlockType.DRAWING, BlockType.SIGNATURE -> "![image](${b.drawingPath ?: b.extra})"
                BlockType.ATTACHMENT -> "📎 ${File(b.extra).name}"
                BlockType.CHART -> b.chart?.let { ch ->
                    "### ${ch.title}\n\n" + ch.labels.indices.joinToString("\n") { i ->
                        "- ${ch.labels.getOrNull(i) ?: ""}: ${ch.values.getOrNull(i) ?: 0.0}"
                    }
                } ?: ""
                // A real, clickable table of contents (previously a bare HTML comment).
                BlockType.TOC -> buildString {
                    append("**Contents**\n\n")
                    DocumentSerializer.headings(blocks).forEach { (level, title) ->
                        append("  ".repeat((level - 1).coerceIn(0, 5)))
                        append("- ").append(mdEsc(title)).append('\n')
                    }
                }
                BlockType.TEXTBOX -> "> $text"
                else -> text
            }
        }
    }

    // ---------- HTML ----------
    fun toHtml(blocks: List<DocumentBlock>, pageSetup: PageSetup): String {
        fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
            .replace("\"", "&quot;")
        val (wMm, _) = pageSetup.sizeMm()
        val body = blocks.joinToString("\n") { b ->
            val runs = b.runs.joinToString("") { r ->
                var t = esc(r.text)
                if (r.bold) t = "<b>$t</b>"
                if (r.italic) t = "<i>$t</i>"
                if (r.underline) t = "<u>$t</u>"
                if (r.strikethrough) t = "<s>$t</s>"
                if (r.superscript) t = "<sup>$t</sup>"
                if (r.subscript) t = "<sub>$t</sub>"
                if (r.highlightArgb != null) t = "<mark>$t</mark>"
                if (r.link != null && SafeLinks.isAllowed(r.link)) t = "<a href=\"${esc(r.link)}\">$t</a>"
                t
            }
            val style = "text-align:${listOf("left", "center", "right", "justify")[b.align.coerceIn(0, 3)]};" +
                "margin-left:${b.indentLevel * 24}px;line-height:${b.lineSpacing}"
            when (b.type) {
                BlockType.HEADING -> "<h${b.headingLevel.coerceIn(1, 6)} style=\"$style\">$runs</h${b.headingLevel.coerceIn(1, 6)}>"
                BlockType.BULLET -> "<ul style=\"$style\"><li>$runs</li></ul>"
                BlockType.NUMBERED -> "<ol style=\"$style\"><li>$runs</li></ol>"
                BlockType.CHECKLIST -> "<p style=\"$style\">${if (b.checked) "☑" else "☐"} $runs</p>"
                BlockType.TABLE -> b.table?.let { t ->
                    val rows = (0 until t.rows).joinToString("") { r ->
                        "<tr>" + (0 until t.cols).joinToString("") { c ->
                            val cell = esc(t.cells.getOrNull(r * t.cols + c) ?: "")
                            if (t.headerRow && r == 0) "<th>$cell</th>" else "<td>$cell</td>"
                        } + "</tr>"
                    }
                    "<table border=\"1\" cellspacing=\"0\" cellpadding=\"6\" style=\"border-collapse:collapse\">$rows</table>"
                } ?: ""
                BlockType.FOOTNOTE -> "<p style=\"font-size:12px\"><sup>${b.footnoteRef}</sup> ${esc(b.footnoteText)}</p>"
                BlockType.DIVIDER -> "<hr/>"
                BlockType.IMAGE -> "<figure><img src=\"${esc(b.imagePath ?: "")}\" alt=\"${esc(b.caption)}\" style=\"width:${b.imageWidthPercent}%\"/>" +
                    (if (b.caption.isNotBlank()) "<figcaption>${esc(b.caption)}</figcaption>" else "") + "</figure>"
                BlockType.DRAWING, BlockType.SIGNATURE -> b.drawingPath?.let { p ->
                    "<img src=\"${esc(p)}\" alt=\"drawing\" style=\"width:${b.imageWidthPercent}%\"/>"
                } ?: ""
                BlockType.CHART -> b.chart?.let { ch ->
                    "<table border=\"1\" cellspacing=\"0\" cellpadding=\"4\"><caption><b>${esc(ch.title)}</b></caption>" +
                        "<tr><th>Label</th><th>Value</th></tr>" +
                        ch.labels.indices.joinToString("") { i ->
                            "<tr><td>${esc(ch.labels.getOrNull(i) ?: "")}</td><td>${ch.values.getOrNull(i) ?: 0.0}</td></tr>"
                        } + "</table>"
                } ?: ""
                BlockType.PAGE_BREAK -> "<div style=\"page-break-after:always\"></div>"
                BlockType.ATTACHMENT -> "<p>📎 ${esc(File(b.extra).name)}</p>"
                // Heading text is user input and must be escaped — otherwise a heading
                // containing markup injects arbitrary HTML into the exported page.
                BlockType.TOC -> "<nav><h3>Contents</h3>" + DocumentSerializer.headings(blocks).joinToString("") { (lv, t) ->
                    "<div style=\"margin-left:${(lv - 1) * 16}px\">${esc(t)}</div>"
                } + "</nav>"
                else -> "<p style=\"$style\">$runs</p>"
            }
        }
        return """<!DOCTYPE html><html><head><meta charset="utf-8"/><title>Notely document</title>
<style>body{font-family:Arial,Helvetica,sans-serif;background:${hex(pageSetup.pageColorArgb)};margin:0;padding:24px;}
.page{max-width:${wMm}mm;margin:0 auto;padding:${pageSetup.marginTop}px ${pageSetup.marginRight}px ${pageSetup.marginBottom}px ${pageSetup.marginLeft}px;background:#fff;box-shadow:0 0 0 1px #eceaf1;}
img{max-width:100%;}table{margin:8px 0;}p{margin:6px 0;}</style></head>
<body><div class="page">$body</div></body></html>"""
    }

    private fun hex(argb: Int): String = String.format("#%06X", argb and 0xFFFFFF)

    // ---------- RTF ----------
    fun toRtf(blocks: List<DocumentBlock>, pageSetup: PageSetup): String {
        /**
         * RTF escaping. Every code unit >= 128 becomes `\uN?` — the `?` matters:
         * RTF requires a fallback character after the numeric escape, and Word
         * renders `\uN` followed by a space as the space, mangling text. The
         * escaped output is pure ASCII, so `\ansicpg1252` stays valid and
         * Bengali/Arabic/Cyrillic documents survive.
         */
        fun esc(s: String): String {
            val sb = StringBuilder()
            for (ch in s) {
                when (ch) {
                    '{' -> sb.append("\\{")
                    '}' -> sb.append("\\}")
                    '\\' -> sb.append("\\\\")
                    '\n' -> sb.append("\\par ")
                    '\r' -> Unit
                    '\t' -> sb.append("\\tab ")
                    else -> if (ch.code > 127) sb.append("\\u").append(ch.code).append('?') else sb.append(ch)
                }
            }
            return sb.toString()
        }
        val body = blocks.joinToString("\n") { b ->
            val runs = b.runs.joinToString("") { r ->
                var t = esc(r.text)
                if (r.bold) t = "{\\b $t}"
                if (r.italic) t = "{\\i $t}"
                if (r.underline) t = "{\\ul $t}"
                if (r.strikethrough) t = "{\\strike $t}"
                if (r.superscript) t = "{\\super $t}"
                if (r.subscript) t = "{\\sub $t}"
                if (r.colorArgb != 0xFF24232B.toInt()) t = "{\\cf2 $t}"
                t
            }
            when (b.type) {
                BlockType.HEADING -> "\\pard{\\fs${(36 - b.headingLevel * 2).coerceAtLeast(20)} \\b $runs}\\par "
                BlockType.BULLET -> "\\pard\\bullet\\tx360 $runs\\par "
                BlockType.NUMBERED -> "\\pard\\tx360 $runs\\par "
                BlockType.DIVIDER -> "\\pard\\brdrb\\brdrs\\brdrw10\\brsp20 \\par "
                BlockType.FOOTNOTE -> "\\pard{\\super ${b.footnoteRef}} ${esc(b.footnoteText)}\\par "
                BlockType.TABLE -> b.table?.let { t ->
                    (0 until t.rows).joinToString("") { r ->
                        (0 until t.cols).joinToString("\\tab ") { c -> esc(t.cells.getOrNull(r * t.cols + c) ?: "") } + "\\par "
                    }
                } ?: ""
                BlockType.PAGE_BREAK -> "\\page "
                else -> "\\pard $runs\\par "
            }
        }
        val header = if (pageSetup.header.isNotBlank()) "{\\header \\pard\\qc ${esc(pageSetup.header)}\\par}" else ""
        val footerText = buildString {
            if (pageSetup.footer.isNotBlank()) append(esc(pageSetup.footer)).append("  ")
            if (pageSetup.pageNumbers) append("Page {\\field{\\*\\fldinst PAGE}}")
        }
        val footer = if (footerText.isNotBlank()) "{\\footer \\pard\\qc $footerText\\par}" else ""
        return "{\\rtf1\\ansi\\ansicpg1252\\deff0{\\fonttbl{\\f0 Arial;}}{\\colortbl;\\red36\\green35\\blue43;\\red137\\green80\\blue245;}" +
            header + footer + "\\f0\\fs28 $body}"
    }

    // ---------- PDF ----------
    fun toPdf(blocks: List<DocumentBlock>, pageSetup: PageSetup, context: Context? = null): ByteArray {
        val doc = PdfDocument()
        val landscape = pageSetup.orientation == "landscape"
        // 1 mm ≈ 2.8346 pt; use A4-equivalent canvas scaled to the paper size.
        val (wMm, hMm) = pageSetup.sizeMm()
        val pageW = (wMm * 2.8346f).toInt().coerceIn(240, 2000)
        val pageH = (hMm * 2.8346f).toInt().coerceIn(240, 2400)
        val ml = pageSetup.marginLeft
        val mr = pageSetup.marginRight
        val mt = pageSetup.marginTop
        val mb = pageSetup.marginBottom
        val maxW = (pageW - ml - mr).toFloat()
        val body = Paint().apply { textSize = 14f; isAntiAlias = true; color = Color.rgb(36, 35, 43) }
        val headingPaint = Paint(body).apply { isFakeBoldText = true }
        val small = Paint(body).apply { textSize = 11f; color = Color.rgb(120, 118, 132) }
        val linePaint = Paint(body).apply { strokeWidth = 1f; color = Color.rgb(200, 198, 210) }
        val fillPaint = Paint(body).apply { style = Paint.Style.FILL }
        val strokePaint = Paint(body).apply { style = Paint.Style.STROKE; strokeWidth = 1.5f }

        var pageNum = 0
        var page: PdfDocument.Page? = null
        var hasLivePage = false
        var canvas: Canvas? = null
        var y = 0f
        var firstBodyPage = true

        fun watermarkAndBorder(c: Canvas) {
            if (pageSetup.pageBorder) {
                strokePaint.color = pageSetup.borderColorArgb
                strokePaint.strokeWidth = 2f
                c.drawRect(ml / 2f, mt / 2f, pageW - mr / 2f, pageH - mb / 2f, strokePaint)
            }
            if (pageSetup.watermark.isNotBlank()) {
                val wm = Paint(body).apply {
                    color = Color.argb(40, 137, 80, 245); textSize = 72f; isFakeBoldText = true
                }
                c.save()
                c.rotate(-35f, pageW / 2f, pageH / 2f)
                c.drawText(pageSetup.watermark, pageW / 2f - wm.measureText(pageSetup.watermark) / 2f, pageH / 2f, wm)
                c.restore()
            }
            val wantsTopNumbers = pageSetup.pageNumbers && pageSetup.pageNumberPosition == "top"
            if (pageSetup.header.isNotBlank() || wantsTopNumbers) {
                val hdr = buildString {
                    if (pageSetup.header.isNotBlank()) append(pageSetup.header)
                    if (wantsTopNumbers) append(if (isEmpty()) "" else "    ").append("Page ").append(pageNum)
                }
                c.drawText(hdr, ml.toFloat(), (mt / 2f) + 4f, small)
            }
            val wantsBottomNumbers = pageSetup.pageNumbers && pageSetup.pageNumberPosition != "top"
            if (pageSetup.footer.isNotBlank() || wantsBottomNumbers) {
                val ftr = buildString {
                    if (pageSetup.footer.isNotBlank()) append(pageSetup.footer)
                    if (wantsBottomNumbers) append(if (isEmpty()) "" else "    ").append("Page ").append(pageNum)
                }
                c.drawText(ftr, ml.toFloat(), pageH - (mb / 2f), small)
            }
        }

        fun startPage() {
            page?.let { doc.finishPage(it) }
            page = null
            hasLivePage = false
            pageNum++
            val info = PdfDocument.PageInfo.Builder(pageW, pageH, pageNum).create()
            val p = doc.startPage(info)
            val c = p.canvas
            c.drawColor(pageSetup.pageColorArgb)
            page = p
            hasLivePage = true
            canvas = c
            watermarkAndBorder(c)
            y = mt.toFloat()
            firstBodyPage = false
        }

        fun newPage() = startPage()

        /**
         * Guarantees a live page. Previously `ensureSpace` could run right after the
         * document had been closed for writing, or request a page at y==top and
         * immediately ask for another — both produced a second finishPage() on a
         * finished page and threw IllegalStateException("document is closed!").
         */
        fun ensureSpace(needed: Float) {
            if (page == null || !hasLivePage) startPage()
            // Clamp absurd requests (e.g. a very tall image) so the loop below can
            // never ask for more room than a page physically has.
            val need = needed.coerceAtMost((pageH - mt - mb).toFloat())
            if (y + need > pageH - mb) {
                startPage()
                y = mt.toFloat()
            }
        }

        fun drawWrapped(text: String, p: Paint, lineH: Float, indent: Float = 0f) {
            if (text.isEmpty()) { y += lineH; return }
            val usable = maxW - indent
            var rest = text
            while (rest.isNotEmpty()) {
                ensureSpace(lineH)
                if (p.measureText(rest) <= usable) {
                    canvas?.drawText(rest, ml + indent, y, p); y += lineH; rest = ""
                } else {
                    var cut = rest.length
                    while (cut > 1 && p.measureText(rest.substring(0, cut)) > usable) cut--
                    // Prefer a word boundary.
                    val space = rest.substring(0, cut).lastIndexOf(' ')
                    if (space > 0) cut = space
                    canvas?.drawText(rest.substring(0, cut), ml + indent, y, p)
                    y += lineH
                    rest = rest.substring(cut).trimStart()
                }
            }
        }

        fun drawImage(path: String?, widthPercent: Int) {
            if (path == null) return
            val bmp = runCatching { BitmapFactory.decodeFile(path) }.getOrNull() ?: return
            val targetW = maxW * (widthPercent.coerceIn(20, 100) / 100f)
            val ratio = bmp.height.toFloat() / bmp.width.toFloat()
            val targetH = targetW * ratio
            var scaledW = targetW
            var scaledH = targetH
            if (scaledH > (pageH - mt - mb) * 0.85f) {
                scaledH = (pageH - mt - mb) * 0.85f
                scaledW = scaledH / ratio
            }
            ensureSpace(scaledH + 12f)
            val dst = RectF(ml.toFloat(), y, ml + scaledW, y + scaledH)
            canvas?.drawBitmap(bmp, null, dst, body)
            bmp.recycle()
            y += scaledH + 8f
        }

        startPage()

        for (b in blocks) {
            when (b.type) {
                BlockType.HEADING -> {
                    val size = (30 - b.headingLevel * 2).coerceAtLeast(14).toFloat()
                    val p = Paint(headingPaint).apply { textSize = size }
                    ensureSpace(size + 14f)
                    y += 4f
                    drawWrapped(b.plainText(), p, size + 8f)
                    y += 4f
                }
                BlockType.DIVIDER -> {
                    ensureSpace(16f)
                    canvas?.drawLine(ml.toFloat(), y, (pageW - mr).toFloat(), y, linePaint)
                    y += 16f
                }
                BlockType.PAGE_BREAK -> {
                    // Defer: only break when more content follows, so the document
                    // never ends with a blank page.
                    if (blocks.lastOrNull() !== b) newPage()
                }
                BlockType.FOOTNOTE -> {
                    ensureSpace(20f)
                    drawWrapped("[${b.footnoteRef}] ${b.footnoteText}", small, 16f)
                }
                BlockType.TABLE -> {
                    val t = b.table ?: continue
                    val colW = maxW / maxOf(1, t.cols)
                    for (r in 0 until t.rows) {
                        ensureSpace(22f)
                        for (c in 0 until t.cols) {
                            val cell = t.cells.getOrNull(r * t.cols + c) ?: ""
                            val paint = if (t.headerRow && r == 0) Paint(body).apply { isFakeBoldText = true } else body
                            var txt = cell
                            while (paint.measureText(txt) > colW - 8f && txt.length > 1) {
                                txt = txt.dropLast(1)
                            }
                            canvas?.drawText(txt, ml + c * colW + 4f, y, paint)
                        }
                        y += 22f
                        canvas?.drawLine(ml.toFloat(), y - 16f, (pageW - mr).toFloat(), y - 16f, linePaint)
                    }
                    y += 6f
                }
                BlockType.IMAGE -> {
                    drawImage(b.imagePath, b.imageWidthPercent)
                    if (b.caption.isNotBlank()) drawWrapped(b.caption, small, 16f)
                }
                BlockType.DRAWING, BlockType.SIGNATURE -> {
                    drawImage(b.drawingPath ?: b.extra.ifBlank { null }, b.imageWidthPercent)
                }
                BlockType.CHART -> {
                    val ch = b.chart
                    if (ch == null || ch.values.isEmpty()) continue
                    ensureSpace(170f)
                    drawWrapped(ch.title, Paint(headingPaint).apply { textSize = 15f }, 22f)
                    val chartH = 130f
                    val chartY = y
                    val chartW = maxW
                    val maxV = ch.values.maxOrNull()?.coerceAtLeast(0.0001) ?: 1.0
                    when (ch.kind) {
                        "pie" -> {
                            val total = ch.values.sum().let { if (it <= 0) 1.0 else it }
                            val colors = intArrayOf(0xFF8950F5.toInt(), 0xFF438CEB.toInt(), 0xFF39B980.toInt(),
                                0xFFF2C94C.toInt(), 0xFFEC5A9E.toInt(), 0xFFFB8C00.toInt())
                            val size = chartH
                            var start = -90f
                            val rect = RectF(ml.toFloat(), chartY, ml + size, chartY + size)
                            ch.values.forEachIndexed { i, v ->
                                fillPaint.color = colors[i % colors.size]
                                val sweep = (v / total * 360f).toFloat()
                                canvas?.drawArc(rect, start, sweep, true, fillPaint)
                                start += sweep
                            }
                            // Legend
                            var ly = chartY + 14f
                            ch.labels.forEachIndexed { i, lb ->
                                fillPaint.color = colors[i % colors.size]
                                canvas?.drawRect(ml + size + 12f, ly - 9f, ml + size + 26f, ly + 3f, fillPaint)
                                canvas?.drawText("${lb.take(14)}  ${ch.values.getOrNull(i) ?: 0.0}", ml + size + 32f, ly, small)
                                ly += 20f
                            }
                            y = chartY + chartH + 14f
                        }
                        "line", "area" -> {
                            val axisY = chartY + chartH
                            canvas?.drawLine(ml.toFloat(), axisY, ml + chartW, axisY, linePaint)
                            val step = chartW / maxOf(1, ch.values.size - 1)
                            var prev: android.graphics.PointF? = null
                            val pts = ch.values.mapIndexed { i, v ->
                                android.graphics.PointF(ml + step * i, axisY - (v / maxV * (chartH - 16f)).toFloat())
                            }
                            if (ch.kind == "area" && pts.size > 1) {
                                val path = android.graphics.Path()
                                path.moveTo(pts.first().x, axisY)
                                pts.forEach { path.lineTo(it.x, it.y) }
                                path.lineTo(pts.last().x, axisY)
                                path.close()
                                fillPaint.color = Color.argb(60, 137, 80, 245)
                                canvas?.drawPath(path, fillPaint)
                            }
                            strokePaint.color = 0xFF8950F5.toInt()
                            strokePaint.strokeWidth = 2.5f
                            prev = null
                            pts.forEach { pt ->
                                prev?.let { canvas?.drawLine(it.x, it.y, pt.x, pt.y, strokePaint) }
                                fillPaint.color = 0xFF8950F5.toInt()
                                canvas?.drawCircle(pt.x, pt.y, 3f, fillPaint)
                                prev = pt
                            }
                            ch.labels.forEachIndexed { i, lb ->
                                canvas?.drawText(lb.take(10), ml + step * i - 8f, axisY + 14f, small)
                            }
                            y = axisY + 22f
                        }
                        else -> { // bar / column
                            val n = ch.values.size
                            val slot = maxW / maxOf(1, n)
                            val barW = slot * 0.55f
                            ch.values.forEachIndexed { i, v ->
                                val h = (v / maxV * (chartH - 10f)).toFloat()
                                fillPaint.color = 0xFF8950F5.toInt()
                                canvas?.drawRoundRect(
                                    RectF(ml + slot * i + (slot - barW) / 2f, chartY + chartH - h,
                                        ml + slot * i + (slot + barW) / 2f, chartY + chartH),
                                    4f, 4f, fillPaint
                                )
                                canvas?.drawText((ch.labels.getOrNull(i) ?: "").take(10), ml + slot * i + 2f, chartY + chartH + 14f, small)
                            }
                            canvas?.drawLine(ml.toFloat(), chartY + chartH, ml + chartW, chartY + chartH, linePaint)
                            y = chartY + chartH + 24f
                        }
                    }
                }
                BlockType.TOC -> {
                    drawWrapped("Contents", Paint(headingPaint).apply { textSize = 16f }, 22f)
                    DocumentSerializer.headings(blocks).forEach { (lv, t) ->
                        drawWrapped(t, body, 18f, indent = (lv - 1) * 14f)
                    }
                }
                BlockType.ATTACHMENT -> drawWrapped("📎 ${File(b.extra).name}", small, 18f)
                else -> {
                    val prefix = when (b.type) {
                        BlockType.BULLET -> "• "
                        BlockType.NUMBERED -> "• "
                        BlockType.CHECKLIST -> if (b.checked) "☑ " else "☐ "
                        else -> ""
                    }
                    val txt = prefix + b.plainText()
                    if (txt.isNotBlank()) {
                        val paint = Paint(body).apply { textSize = 14f }
                        drawWrapped(txt, paint, 14f * b.lineSpacing.coerceIn(1f, 2.2f), indent = b.indentLevel * 16f)
                    }
                    y += 4f
                }
            }
        }
        // Finish the final page exactly once, before writeTo(). Finishing twice throws
        // IllegalStateException("document is closed!") and truncates the file.
        page?.let { doc.finishPage(it) }
        page = null
        val out = ByteArrayOutputStream()
        doc.writeTo(out)
        doc.close()
        return out.toByteArray()
    }

    // ---------- DOCX (OOXML, hand-built, valid package) ----------
    fun toDocx(blocks: List<DocumentBlock>, pageSetup: PageSetup, context: Context? = null): ByteArray {
        fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

        // Media
        val media = mutableListOf<Pair<String, ByteArray>>()
        val imageRelIds = mutableMapOf<String, String>()
        val links = linkedSetOf<String>()
        blocks.forEach { b ->
            val path = when (b.type) {
                BlockType.IMAGE -> b.imagePath
                BlockType.DRAWING, BlockType.SIGNATURE -> b.drawingPath
                else -> null
            }
            if (path != null && media.none { it.first == path } && File(path).exists()) {
                val bytes = runCatching { File(path).readBytes() }.getOrNull()
                if (bytes != null) {
                    media.add(path to bytes)
                    imageRelIds[path] = "rIdImg${media.size}"
                }
            }
            b.runs.forEach { r -> r.link?.takeIf { SafeLinks.isAllowed(it) }?.let { links.add(it) } }
        }
        val linkRelIds = links.withIndex().associate { (i, url) -> url to "rIdLnk${i + 1}" }

        fun drawingXml(path: String): String {
            val relId = imageRelIds[path] ?: return ""
            val bmp = runCatching { BitmapFactory.decodeFile(path) }.getOrNull()
            val w = bmp?.width ?: 400
            val h = bmp?.height ?: 300
            bmp?.recycle()
            val maxCx = 4_000_000L
            var cx = w.toLong() * 9525L
            var cy = h.toLong() * 9525L
            if (cx > maxCx) {
                cy = cy * maxCx / cx
                cx = maxCx
            }
            return """<w:r><w:drawing><wp:inline distT="0" distB="0" distL="0" distR="0" xmlns:wp="http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing"><wp:extent cx="$cx" cy="$cy"/><wp:docPr id="${abs(path.hashCode()) % 100000}" name="image"/><a:graphic xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main"><a:graphicData uri="http://schemas.openxmlformats.org/drawingml/2006/picture"><pic:pic xmlns:pic="http://schemas.openxmlformats.org/drawingml/2006/picture"><pic:nvPicPr><pic:cNvPr id="0" name="image"/><pic:cNvPicPr/></pic:nvPicPr><pic:blipFill><a:blip r:embed="$relId"/><a:stretch><a:fillRect/></a:stretch></pic:blipFill><pic:spPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="$cx" cy="$cy"/></a:xfrm><a:prstGeom prst="rect"><a:avLst/></a:prstGeom></pic:spPr></pic:pic></a:graphicData></a:graphic></wp:inline></w:drawing></w:r>"""
        }

        fun runsXml(b: DocumentBlock): String = b.runs.joinToString("") { r ->
            val props = mutableListOf<String>()
            if (r.bold) props.add("<w:b/>")
            if (r.italic) props.add("<w:i/>")
            if (r.underline) props.add("<w:u w:val=\"single\"/>")
            if (r.strikethrough) props.add("<w:strike/>")
            if (r.colorArgb != 0xFF24232B.toInt()) props.add("<w:color w:val=\"${String.format("%06X", r.colorArgb and 0xFFFFFF)}\"/>")
            if (r.highlightArgb != null) props.add("<w:highlight w:val=\"yellow\"/>")
            if (r.superscript) props.add("<w:vertAlign w:val=\"superscript\"/>")
            if (r.subscript) props.add("<w:vertAlign w:val=\"subscript\"/>")
            if (r.fontSizeSp != 16f) props.add("<w:sz w:val=\"${(r.fontSizeSp * 2).toInt()}\"/>")
            val rPr = if (props.isNotEmpty()) "<w:rPr>${props.joinToString("")}</w:rPr>" else ""
            val run = "<w:r>$rPr<w:t xml:space=\"preserve\">${esc(r.text)}</w:t></w:r>"
            val linkId = r.link?.let { linkRelIds[it] }
            if (linkId != null) "<w:hyperlink r:id=\"$linkId\">$run</w:hyperlink>" else run
        }

        /**
         * `w:pPr` children must follow the schema sequence
         * (pStyle → numPr → spacing → ind → jc) and may appear at most once, so
         * the block-level line spacing wins over the page default instead of
         * emitting a second `w:spacing` (which makes Word repair the file).
         */
        fun paraProps(b: DocumentBlock): String {
            val heading = if (b.type == BlockType.HEADING)
                "<w:pStyle w:val=\"Heading${b.headingLevel.coerceIn(1, 8)}\"/>" else ""
            val list = if (b.type == BlockType.BULLET || b.type == BlockType.NUMBERED)
                "<w:numPr><w:ilvl w:val=\"${b.indentLevel.coerceIn(0, 8)}\"/><w:numId w:val=\"${if (b.type == BlockType.BULLET) 1 else 2}\"/></w:numPr>"
            else ""
            val effectiveSpacing = if (b.type == BlockType.HEADING) b.lineSpacing else pageSetup.lineSpacing
            val spacing = if (effectiveSpacing != 1.55f)
                "<w:spacing w:line=\"${(effectiveSpacing * 240).toInt()}\" w:lineRule=\"auto\"/>" else ""
            val indent = if (b.indentLevel > 0 && list.isEmpty()) "<w:ind w:left=\"${b.indentLevel * 360}\"/>" else ""
            val jc = when (b.align) {
                1 -> "<w:jc w:val=\"center\"/>"
                2 -> "<w:jc w:val=\"right\"/>"
                3 -> "<w:jc w:val=\"both\"/>"
                else -> ""
            }
            return if (heading.isEmpty() && list.isEmpty() && spacing.isEmpty() && indent.isEmpty() && jc.isEmpty())
                "" else "<w:pPr>$heading$list$spacing$indent$jc</w:pPr>"
        }

        val paras = blocks.joinToString("\n") { b ->
            when (b.type) {
                BlockType.HEADING -> "<w:p>${paraProps(b)}${runsXml(b)}</w:p>"
                BlockType.TABLE -> b.table?.let { t ->
                    val rows = (0 until t.rows).joinToString("") { r ->
                        "<w:tr>" + (0 until t.cols).joinToString("") { c ->
                            "<w:tc><w:tcPr><w:tcW w:w=\"0\" w:type=\"auto\"/></w:tcPr><w:p><w:r><w:t xml:space=\"preserve\">${esc(t.cells.getOrNull(r * t.cols + c) ?: "")}</w:t></w:r></w:p></w:tc>"
                        } + "</w:tr>"
                    }
                    "<w:tbl><w:tblPr><w:tblStyle w:val=\"TableGrid\"/><w:tblW w:w=\"0\" w:type=\"auto\"/><w:tblBorders><w:top w:val=\"single\" w:sz=\"4\"/><w:left w:val=\"single\" w:sz=\"4\"/><w:bottom w:val=\"single\" w:sz=\"4\"/><w:right w:val=\"single\" w:sz=\"4\"/><w:insideH w:val=\"single\" w:sz=\"4\"/><w:insideV w:val=\"single\" w:sz=\"4\"/></w:tblBorders></w:tblPr>$rows</w:tbl>"
                } ?: ""
                BlockType.DIVIDER -> "<w:p><w:pPr><w:pBdr><w:bottom w:val=\"single\" w:sz=\"6\" w:space=\"1\"/></w:pBdr></w:pPr></w:p>"
                BlockType.PAGE_BREAK -> "<w:p><w:r><w:br w:type=\"page\"/></w:r></w:p>"
                BlockType.FOOTNOTE -> "<w:p><w:r><w:rPr><w:vertAlign w:val=\"superscript\"/></w:rPr><w:t>${b.footnoteRef}</w:t></w:r><w:r><w:t xml:space=\"preserve\"> ${esc(b.footnoteText)}</w:t></w:r></w:p>"
                BlockType.IMAGE -> "<w:p><w:pPr><w:jc w:val=\"center\"/></w:pPr>${drawingXml(b.imagePath ?: "")}" +
                    (if (b.caption.isNotBlank()) "<w:r><w:t xml:space=\"preserve\"> ${esc(b.caption)}</w:t></w:r>" else "") + "</w:p>"
                BlockType.DRAWING, BlockType.SIGNATURE -> "<w:p><w:pPr><w:jc w:val=\"center\"/></w:pPr>${drawingXml(b.drawingPath ?: "")}</w:p>"
                BlockType.CHART -> b.chart?.let { ch ->
                    val rows = "<w:tr><w:tc><w:p><w:r><w:t>${esc(ch.title)}</w:t></w:r></w:p></w:tc></w:tr>" +
                        ch.labels.indices.joinToString("") { i ->
                            "<w:tr><w:tc><w:p><w:r><w:t>${esc(ch.labels.getOrNull(i) ?: "")}</w:t></w:r></w:p></w:tc><w:tc><w:p><w:r><w:t>${ch.values.getOrNull(i) ?: 0.0}</w:t></w:r></w:p></w:tc></w:tr>"
                        }
                    "<w:tbl><w:tblPr><w:tblStyle w:val=\"TableGrid\"/><w:tblW w:w=\"0\" w:type=\"auto\"/><w:tblBorders><w:top w:val=\"single\" w:sz=\"4\"/><w:left w:val=\"single\" w:sz=\"4\"/><w:bottom w:val=\"single\" w:sz=\"4\"/><w:right w:val=\"single\" w:sz=\"4\"/><w:insideH w:val=\"single\" w:sz=\"4\"/><w:insideV w:val=\"single\" w:sz=\"4\"/></w:tblBorders></w:tblPr>$rows</w:tbl>"
                } ?: ""
                BlockType.TOC -> "<w:p><w:r><w:rPr><w:b/></w:rPr><w:t>Contents</w:t></w:r></w:p>" +
                    DocumentSerializer.headings(blocks).joinToString("") { (lv, t) ->
                        "<w:p><w:pPr><w:ind w:left=\"${(lv - 1) * 360}\"/></w:pPr><w:r><w:t xml:space=\"preserve\">${esc(t)}</w:t></w:r></w:p>"
                    }
                BlockType.ATTACHMENT -> "<w:p><w:r><w:t xml:space=\"preserve\">📎 ${esc(File(b.extra).name)}</w:t></w:r></w:p>"
                else -> "<w:p>${paraProps(b)}${runsXml(b)}</w:p>"
            }
        }

        val (wMm, hMm) = pageSetup.sizeMm()
        val twipsW = (wMm * 56.6929f).toInt()
        val twipsH = (hMm * 56.6929f).toInt()
        val landscape = pageSetup.orientation == "landscape"

        // Header/footer parts
        val wantHeader = pageSetup.header.isNotBlank()
        val wantFooter = pageSetup.footer.isNotBlank() || pageSetup.pageNumbers
        val headerXml = if (wantHeader) """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:hdr xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:p><w:r><w:t xml:space="preserve">${esc(pageSetup.header)}</w:t></w:r></w:p></w:hdr>""" else null
        val footerXml = if (wantFooter) {
            val pageField = if (pageSetup.pageNumbers) """<w:r><w:fldChar w:fldCharType="begin"/></w:r><w:r><w:instrText xml:space="preserve"> PAGE </w:instrText></w:r><w:r><w:fldChar w:fldCharType="end"/></w:r>""" else ""
            """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:ftr xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:p><w:r><w:t xml:space="preserve">${esc(pageSetup.footer)} </w:t></w:r>$pageField</w:p></w:ftr>"""
        } else null

        val headerRef = if (wantHeader) "<w:headerReference w:type=\"default\" r:id=\"rIdHeader\"/>" else ""
        val footerRef = if (wantFooter) "<w:footerReference w:type=\"default\" r:id=\"rIdFooter\"/>" else ""
        val documentXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><w:body>
$paras
<w:sectPr>$headerRef$footerRef<w:pgSz w:w="$twipsW" w:h="$twipsH"${if (landscape) " w:orient=\"landscape\"" else ""}/><w:pgMar w:top="${pageSetup.marginTop * 20}" w:right="${pageSetup.marginRight * 20}" w:bottom="${pageSetup.marginBottom * 20}" w:left="${pageSetup.marginLeft * 20}" w:header="720" w:footer="720" w:gutter="0"/></w:sectPr>
</w:body></w:document>"""

        val contentTypes = StringBuilder("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Default Extension="png" ContentType="image/png"/><Default Extension="jpg" ContentType="image/jpeg"/><Default Extension="jpeg" ContentType="image/jpeg"/><Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/><Override PartName="/word/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml"/><Override PartName="/word/numbering.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.numbering+xml"/>""")
        if (wantHeader) contentTypes.append("""<Override PartName="/word/header1.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.header+xml"/>""")
        if (wantFooter) contentTypes.append("""<Override PartName="/word/footer1.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.footer+xml"/>""")
        contentTypes.append("</Types>")

        val docRels = StringBuilder("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rIdStyles" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/><Relationship Id="rIdNumbering" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/numbering" Target="numbering.xml"/>""")
        if (wantHeader) docRels.append("""<Relationship Id="rIdHeader" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/header" Target="header1.xml"/>""")
        if (wantFooter) docRels.append("""<Relationship Id="rIdFooter" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/footer" Target="footer1.xml"/>""")
        media.forEachIndexed { i, (path, _) ->
            val ext = path.substringAfterLast('.', "png").lowercase()
            docRels.append("""<Relationship Id="rIdImg${i + 1}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/image" Target="media/image${i + 1}.$ext"/>""")
        }
        links.forEachIndexed { i, url ->
            docRels.append("""<Relationship Id="rIdLnk${i + 1}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/hyperlink" Target="${url.replace("&", "&amp;").replace("\"", "&quot;")}" TargetMode="External"/>""")
        }
        docRels.append("</Relationships>")

        val styles = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:styles xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:docDefaults><w:rPrDefault><w:rPr><w:rFonts w:ascii="Arial" w:hAnsi="Arial"/><w:sz w:val="28"/></w:rPr></w:rPrDefault></w:docDefaults><w:style w:type="paragraph" w:default="1" w:styleId="Normal"><w:name w:val="Normal"/></w:style>""" +
            (1..8).joinToString("") { lvl ->
                val size = when (lvl) { 1 -> 56; 2 -> 44; 3 -> 40; else -> 32 - (lvl - 3) * 2 }
                """<w:style w:type="paragraph" w:styleId="Heading$lvl"><w:name w:val="heading $lvl"/><w:basedOn w:val="Normal"/><w:pPr><w:outlineLvl w:val="${lvl - 1}"/></w:pPr><w:rPr><w:b/><w:sz w:val="$size"/></w:rPr></w:style>"""
            } +
            """<w:style w:type="table" w:styleId="TableGrid"><w:name w:val="Table Grid"/></w:style></w:styles>"""

        val numbering = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:numbering xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:abstractNum w:abstractNumId="0"><w:lvl w:ilvl="0"><w:start w:val="1"/><w:numFmt w:val="bullet"/><w:lvlText w:val="•"/><w:lvlJc w:val="left"/></w:lvl><w:lvl w:ilvl="1"><w:start w:val="1"/><w:numFmt w:val="bullet"/><w:lvlText w:val="◦"/></w:lvl><w:lvl w:ilvl="2"><w:start w:val="1"/><w:numFmt w:val="bullet"/><w:lvlText w:val="▪"/></w:lvl><w:lvl w:ilvl="3"><w:start w:val="1"/><w:numFmt w:val="bullet"/><w:lvlText w:val="•"/></w:lvl></w:abstractNum><w:abstractNum w:abstractNumId="1"><w:lvl w:ilvl="0"><w:start w:val="1"/><w:numFmt w:val="decimal"/><w:lvlText w:val="%1."/><w:lvlJc w:val="left"/></w:lvl><w:lvl w:ilvl="1"><w:start w:val="1"/><w:numFmt w:val="lowerLetter"/><w:lvlText w:val="%2."/></w:lvl><w:lvl w:ilvl="2"><w:start w:val="1"/><w:numFmt w:val="lowerRoman"/><w:lvlText w:val="%3."/></w:lvl><w:lvl w:ilvl="3"><w:start w:val="1"/><w:numFmt w:val="decimal"/><w:lvlText w:val="%4."/></w:lvl></w:abstractNum><w:num w:numId="1"><w:abstractNumId w:val="0"/></w:num><w:num w:numId="2"><w:abstractNumId w:val="1"/></w:num></w:numbering>"""

        val entries = mutableListOf<Pair<String, ByteArray>>(
            "[Content_Types].xml" to contentTypes.toString().toByteArray(),
            "_rels/.rels" to """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/></Relationships>""".toByteArray(),
            "word/document.xml" to documentXml.toByteArray(),
            "word/_rels/document.xml.rels" to docRels.toString().toByteArray(),
            "word/styles.xml" to styles.toByteArray(),
            "word/numbering.xml" to numbering.toByteArray()
        )
        headerXml?.let { entries.add("word/header1.xml" to it.toByteArray()) }
        footerXml?.let { entries.add("word/footer1.xml" to it.toByteArray()) }
        media.forEachIndexed { i, (path, bytes) ->
            entries.add("word/media/image${i + 1}.${imageKind(path).first}" to bytes)
        }
        return zipOfBytes(entries.toTypedArray())
    }

    // ---------- ODT (OpenDocument, hand-built, valid package) ----------
    fun toOdt(blocks: List<DocumentBlock>, pageSetup: PageSetup, context: Context? = null): ByteArray {
        fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
            .replace("\"", "&quot;")

        // path -> (internal ODF name, media type); extension follows the real file type
        val pictures = mutableListOf<Triple<String, ByteArray, String>>()
        val picNames = mutableMapOf<String, String>()
        blocks.forEach { b ->
            val path = when (b.type) {
                BlockType.IMAGE -> b.imagePath
                BlockType.DRAWING, BlockType.SIGNATURE -> b.drawingPath
                else -> null
            }
            if (path != null && picNames[path] == null && File(path).exists()) {
                runCatching { File(path).readBytes() }.getOrNull()?.let { bytes ->
                    val (ext, mime) = imageKind(path)
                    pictures.add(Triple(path, bytes, mime))
                    picNames[path] = "Pictures/image${pictures.size}.$ext"
                }
            }
        }

        fun runsXml(b: DocumentBlock): String = b.runs.joinToString("") { r ->
            var t = esc(r.text)
            if (r.bold) t = """<text:span text:style-name="TBold">$t</text:span>"""
            if (r.italic) t = """<text:span text:style-name="TItalic">$t</text:span>"""
            if (r.underline) t = """<text:span text:style-name="TUnder">$t</text:span>"""
            if (r.strikethrough) t = """<text:span text:style-name="TStrike">$t</text:span>"""
            if (r.link != null && SafeLinks.isAllowed(r.link)) t = """<text:a xlink:href="${esc(r.link)}">$t</text:a>"""
            t
        }

        fun imageXml(path: String, widthPercent: Int): String {
            val name = picNames[path] ?: return ""
            val bmp = runCatching { BitmapFactory.decodeFile(path) }.getOrNull()
            val wCm = 16.0 * (widthPercent.coerceIn(20, 100) / 100.0)
            val hCm = bmp?.let { wCm * it.height / it.width } ?: (wCm * 0.75)
            bmp?.recycle()
            return """<text:p text:style-name="P1"><draw:frame draw:name="$name" text:anchor-type="as-char" svg:width="${"%.2f".format(wCm)}cm" svg:height="${"%.2f".format(hCm)}cm"><draw:image xlink:href="$name" xlink:type="simple" xlink:show="embed" xlink:actuate="onLoad"/></draw:frame></text:p>"""
        }

        val paras = blocks.joinToString("\n") { b ->
            when (b.type) {
                BlockType.HEADING -> """<text:h text:outline-level="${b.headingLevel.coerceIn(1, 10)}">${runsXml(b)}</text:h>"""
                BlockType.BULLET, BlockType.NUMBERED, BlockType.CHECKLIST -> {
                    val style = if (b.type == BlockType.BULLET) "LBullet" else "LNumber"
                    """<text:p text:style-name="$style">${if (b.type == BlockType.CHECKLIST) (if (b.checked) "☑ " else "☐ ") else ""}${runsXml(b)}</text:p>"""
                }
                BlockType.TABLE -> b.table?.let { t ->
                    val rows = (0 until t.rows).joinToString("") { r ->
                        "<table:table-row>" + (0 until t.cols).joinToString("") { c ->
                            "<table:table-cell><text:p>${esc(t.cells.getOrNull(r * t.cols + c) ?: "")}</text:p></table:table-cell>"
                        } + "</table:table-row>"
                    }
                    """<table:table table:name="Table${abs(b.id.hashCode()) % 9999}"><table:table-column table:number-columns-repeated="${t.cols}"/>$rows</table:table><text:p/>"""
                } ?: ""
                BlockType.DIVIDER -> """<text:p text:style-name="PHr"/>"""
                BlockType.PAGE_BREAK -> """<text:p text:style-name="PBrake"/>"""
                BlockType.FOOTNOTE -> """<text:p text:style-name="PFootnote"><text:note text:note-class="footnote" text:id="ftn${b.footnoteRef}"><text:note-citation>${b.footnoteRef}</text:note-citation><text:note-body><text:p>${esc(b.footnoteText)}</text:p></text:note-body></text:note></text:p>"""
                BlockType.IMAGE -> imageXml(b.imagePath ?: "", b.imageWidthPercent) +
                    (if (b.caption.isNotBlank()) "<text:p>${esc(b.caption)}</text:p>" else "")
                BlockType.DRAWING, BlockType.SIGNATURE -> imageXml(b.drawingPath ?: "", b.imageWidthPercent)
                BlockType.CHART -> b.chart?.let { ch ->
                    val rows = "<table:table-row>" + (0 until 2).joinToString("") { c ->
                        "<table:table-cell><text:p><text:span text:style-name=\"TBold\">${esc(if (c == 0) ch.title else "Value")}</text:span></text:p></table:table-cell>"
                    } + "</table:table-row>" + ch.labels.indices.joinToString("") { i ->
                        "<table:table-row><table:table-cell><text:p>${esc(ch.labels.getOrNull(i) ?: "")}</text:p></table:table-cell><table:table-cell><text:p>${ch.values.getOrNull(i) ?: 0.0}</text:p></table:table-cell></table:table-row>"
                    }
                    """<table:table table:name="Chart${abs(b.id.hashCode()) % 9999}"><table:table-column table:number-columns-repeated="2"/>$rows</table:table><text:p/>"""
                } ?: ""
                BlockType.TOC -> "<text:p><text:span text:style-name=\"TBold\">Contents</text:span></text:p>" +
                    DocumentSerializer.headings(blocks).joinToString("") { (_, t) -> "<text:p>${esc(t)}</text:p>" }
                BlockType.ATTACHMENT -> "<text:p>📎 ${esc(File(b.extra).name)}</text:p>"
                else -> """<text:p text:style-name="P1">${runsXml(b)}</text:p>"""
            }
        }

        val (wMm, hMm) = pageSetup.sizeMm()
        val contentXml = """<?xml version="1.0" encoding="UTF-8"?>
<office:document-content xmlns:office="urn:oasis:names:tc:opendocument:xmlns:office:1.0"
 xmlns:text="urn:oasis:names:tc:opendocument:xmlns:text:1.0"
 xmlns:table="urn:oasis:names:tc:opendocument:xmlns:table:1.0"
 xmlns:style="urn:oasis:names:tc:opendocument:xmlns:style:1.0"
 xmlns:fo="urn:oasis:names:tc:opendocument:xmlns:xsl-fo-compatible:1.0"
 xmlns:draw="urn:oasis:names:tc:opendocument:xmlns:drawing:1.0"
 xmlns:svg="urn:oasis:names:tc:opendocument:xmlns:svg-compatible:1.0"
 xmlns:xlink="http://www.w3.org/1999/xlink" office:version="1.2">
<office:automatic-styles>
<style:style style:name="P1" style:family="paragraph" style:parent-style-name="Standard"/>
<style:style style:name="PHr" style:family="paragraph"><style:paragraph-properties fo:border-bottom="0.5pt solid #c8c6d2"/></style:style>
<style:style style:name="PBrake" style:family="paragraph"><style:paragraph-properties fo:break-before="page"/></style:style>
<style:style style:name="PFootnote" style:family="paragraph"><style:text-properties fo:font-size="9pt"/></style:style>
<style:style style:name="TBold" style:family="text"><style:text-properties fo:font-weight="bold"/></style:style>
<style:style style:name="TItalic" style:family="text"><style:text-properties fo:font-style="italic"/></style:style>
<style:style style:name="TUnder" style:family="text"><style:text-properties style:text-underline-style="solid" style:text-underline-width="auto"/></style:style>
<style:style style:name="TStrike" style:family="text"><style:text-properties style:text-line-through-style="solid"/></style:style>
<style:style style:name="LBullet" style:family="paragraph"><style:paragraph-properties fo:margin-left="0.5cm" fo:text-indent="-0.5cm"/><style:text-properties/><style:list-level-properties/></style:style>
<style:style style:name="LNumber" style:family="paragraph"><style:paragraph-properties fo:margin-left="0.5cm" fo:text-indent="-0.5cm"/></style:style>
<style:page-layout style:name="pm1"><style:page-layout-properties fo:page-width="${"%.1f".format(wMm / 10f)}cm" fo:page-height="${"%.1f".format(hMm / 10f)}cm" fo:margin-top="${"%.2f".format(pageSetup.marginTop / 10f * 2.54f)}cm" fo:margin-bottom="${"%.2f".format(pageSetup.marginBottom / 10f * 2.54f)}cm" fo:margin-left="${"%.2f".format(pageSetup.marginLeft / 10f * 2.54f)}cm" fo:margin-right="${"%.2f".format(pageSetup.marginRight / 10f * 2.54f)}cm" style:writing-mode="lr-tb" fo:background-color="${hex(pageSetup.pageColorArgb)}"/></style:page-layout>
</office:automatic-styles>
<office:body><office:text>
$paras
</office:text></office:body></office:document-content>"""

        val stylesXml = """<?xml version="1.0" encoding="UTF-8"?>
<office:document-styles xmlns:office="urn:oasis:names:tc:opendocument:xmlns:office:1.0"
 xmlns:style="urn:oasis:names:tc:opendocument:xmlns:style:1.0"
 xmlns:text="urn:oasis:names:tc:opendocument:xmlns:text:1.0"
 xmlns:fo="urn:oasis:names:tc:opendocument:xmlns:xsl-fo-compatible:1.0" office:version="1.2">
<office:styles>
<style:default-style style:family="paragraph"><style:paragraph-properties fo:margin-top="0.1cm" fo:margin-bottom="0.1cm"/></style:default-style>
<style:style style:name="Standard" style:family="paragraph" style:class="text"/>
${(1..8).joinToString("") { l -> """<style:style style:name="Heading$l" style:family="paragraph" style:parent-style-name="Standard" style:class="text"><style:paragraph-properties fo:keep-with-next="always"/><style:text-properties fo:font-size="${(26 - l * 2).coerceAtLeast(11)}pt" fo:font-weight="bold"/></style:style>""" }}
</office:styles>
<office:automatic-styles><style:page-layout style:name="pm1"><style:page-layout-properties fo:page-width="${"%.1f".format(wMm / 10f)}cm" fo:page-height="${"%.1f".format(hMm / 10f)}cm"/></style:page-layout></office:automatic-styles>
<office:master-styles><style:master-page style:name="Standard" style:page-layout-name="pm1"/></office:master-styles>
</office:document-styles>"""

        val manifestEntries = StringBuilder("""<manifest:file-entry manifest:full-path="/" manifest:version="1.2" manifest:media-type="application/vnd.oasis.opendocument.text"/><manifest:file-entry manifest:full-path="content.xml" manifest:media-type="text/xml"/><manifest:file-entry manifest:full-path="styles.xml" manifest:media-type="text/xml"/><manifest:file-entry manifest:full-path="meta.xml" manifest:media-type="text/xml"/>""")
        pictures.forEachIndexed { i, pic ->
            val name = picNames[pic.first] ?: "Pictures/image${i + 1}.png"
            manifestEntries.append("""<manifest:file-entry manifest:full-path="$name" manifest:media-type="${pic.third}"/>""")
        }
        val manifest = """<?xml version="1.0" encoding="UTF-8"?>
<manifest:manifest xmlns:manifest="urn:oasis:names:tc:opendocument:xmlns:manifest:1.0" manifest:version="1.2">$manifestEntries</manifest:manifest>"""
        val meta = """<?xml version="1.0" encoding="UTF-8"?>
<office:document-meta xmlns:office="urn:oasis:names:tc:opendocument:xmlns:office:1.0" xmlns:meta="urn:oasis:names:tc:opendocument:xmlns:meta:1.0" office:version="1.2"><office:meta><meta:generator>Notely 1.0</meta:generator></office:meta></office:document-meta>"""

        val entries = mutableListOf<Pair<String, ByteArray>>(
            "mimetype" to "application/vnd.oasis.opendocument.text".toByteArray(Charsets.UTF_8),
            "META-INF/manifest.xml" to manifest.toByteArray(),
            "content.xml" to contentXml.toByteArray(),
            "styles.xml" to stylesXml.toByteArray(),
            "meta.xml" to meta.toByteArray()
        )
        pictures.forEach { (path, bytes, _) ->
            entries.add((picNames[path] ?: "Pictures/image.png") to bytes)
        }
        return zipOfBytes(entries.toTypedArray(), storeMimetypeFirst = true)
    }

    private fun zipOfBytes(entries: Array<Pair<String, ByteArray>>, storeMimetypeFirst: Boolean = false): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z ->
            entries.forEach { (name, bytes) ->
                val entry = ZipEntry(name)
                if (storeMimetypeFirst && name == "mimetype") {
                    // ODF requires the mimetype entry to be first and stored (uncompressed).
                    entry.method = ZipEntry.STORED
                    entry.size = bytes.size.toLong()
                    entry.compressedSize = bytes.size.toLong()
                    val crc = java.util.zip.CRC32()
                    crc.update(bytes)
                    entry.crc = crc.value
                }
                z.putNextEntry(entry)
                z.write(bytes)
                z.closeEntry()
            }
        }
        return out.toByteArray()
    }

    private fun zipOf(vararg entries: Pair<String, String>): ByteArray =
        zipOfBytes(entries.map { (n, c) -> n to c.toByteArray(Charsets.UTF_8) }.toTypedArray())
}

/** Statistic helpers shared by the editor and tests. */
object DocText {
    fun words(blocks: List<DocumentBlock>): Int =
        DocumentSerializer.plainText(blocks).let { if (it.isBlank()) 0 else it.trim().split(Regex("\\s+")).size }

    fun runText(block: DocumentBlock): String = RunEditing.fullText(block.runs)
}
