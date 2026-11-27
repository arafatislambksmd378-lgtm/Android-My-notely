package com.notely.app.ui.editor

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.notely.app.doc.*
import com.notely.app.export.Exporters
import com.notely.app.ui.components.ChartView
import com.notely.app.ui.components.ClayCard
import com.notely.app.ui.components.ClayIconTile
import com.notely.app.ui.components.ClayPill
import com.notely.app.ui.components.ClayPrimaryButton
import com.notely.app.ui.components.ClaySoftButton
import com.notely.app.ui.components.ColorDot
import com.notely.app.ui.theme.CardColors
import com.notely.app.ui.theme.LocalNotelyColors
import com.notely.app.util.ImageOps
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Builds the styled AnnotatedString for a block, used by read-only previews. */
fun blockToAnnotated(block: DocumentBlock): AnnotatedString = buildAnnotatedString {
    block.runs.forEach { r ->
        withStyle(spanFor(r)) { append(r.text) }
    }
}

fun spanFor(r: TextRun) = SpanStyle(
    fontWeight = if (r.bold) FontWeight.Bold else FontWeight.Normal,
    fontStyle = if (r.italic) FontStyle.Italic else FontStyle.Normal,
    textDecoration = when {
        r.underline && r.strikethrough -> TextDecoration.Underline + TextDecoration.LineThrough
        r.underline -> TextDecoration.Underline
        r.strikethrough -> TextDecoration.LineThrough
        else -> TextDecoration.None
    },
    color = Color(r.colorArgb),
    background = r.highlightArgb?.let { Color(it) } ?: Color.Transparent,
    fontSize = r.fontSizeSp.sp,
    baselineShift = when {
        r.superscript -> BaselineShift.Superscript
        r.subscript -> BaselineShift.Subscript
        else -> null
    },
    fontFamily = when (r.fontFamily) {
        "Serif" -> androidx.compose.ui.text.font.FontFamily.Serif
        "Mono" -> androidx.compose.ui.text.font.FontFamily.Monospace
        else -> null
    }
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlockEditor(
    block: DocumentBlock,
    index: Int,
    readOnly: Boolean,
    focused: Boolean,
    spellEnabled: Boolean,
    allBlocks: List<DocumentBlock>,
    onFocus: () -> Unit,
    onChange: (DocumentBlock) -> Unit,
    onAddBelow: () -> Unit,
    onDelete: () -> Unit,
    onOpenUrl: (String) -> Unit,
    /** Character range to select and reveal, e.g. a find & replace match. */
    selection: IntRange? = null,
    onSelectionConsumed: () -> Unit = {}
) {
    val c = LocalNotelyColors.current
    val context = LocalContext.current
    var menuOpen by remember { mutableStateOf(false) }
    var showChartEditor by remember { mutableStateOf(false) }
    var showImageTools by remember { mutableStateOf(false) }
    var showCrop by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxWidth()) {
        when (block.type) {
            BlockType.IMAGE -> ImageBlock(
                block = block,
                readOnly = readOnly,
                onTools = { showImageTools = true },
                onDelete = onDelete
            )
            BlockType.DRAWING, BlockType.SIGNATURE -> DrawingBlock(block, onDelete)
            BlockType.TABLE -> TableBlock(block, readOnly, onChange, onDelete)
            BlockType.CHART -> {
                Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    ChartView(
                        chart = block.chart ?: ChartData("bar", "", emptyList(), emptyList()),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .border(1.dp, c.border, RoundedCornerShape(16.dp))
                            .padding(12.dp)
                            .clickable(enabled = !readOnly) { showChartEditor = true }
                    )
                    if (!readOnly) {
                        Row {
                            TextButton(onClick = { showChartEditor = true }) { Text("Edit chart") }
                            TextButton(onClick = onDelete) {
                                Text("Remove", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
            BlockType.DIVIDER -> Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                HorizontalDivider(color = c.border, thickness = 1.5.dp)
                if (!readOnly) TextButton(onClick = onDelete) {
                    Text("Remove divider", fontSize = 12.sp, color = c.text2)
                }
            }
            BlockType.FOOTNOTE -> Row(
                Modifier.fillMaxWidth().padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("[${block.footnoteRef}] ", fontSize = 12.sp, color = c.primary)
                BasicTextField(
                    value = block.footnoteText,
                    onValueChange = { onChange(block.copy(footnoteText = it)) },
                    enabled = !readOnly,
                    textStyle = TextStyle(fontSize = 12.sp, color = c.text2),
                    modifier = Modifier.weight(1f)
                )
                if (!readOnly) TextButton(onClick = onDelete) {
                    Text("✕", fontSize = 12.sp, color = c.text2)
                }
            }
            BlockType.PAGE_BREAK -> Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    HorizontalDivider(Modifier.weight(1f), color = c.primary.copy(alpha = 0.4f))
                    Text("  Page break  ", fontSize = 10.sp, color = c.text2)
                    HorizontalDivider(Modifier.weight(1f), color = c.primary.copy(alpha = 0.4f))
                }
                if (!readOnly) TextButton(onClick = onDelete) {
                    Text("Remove break", fontSize = 12.sp, color = c.text2)
                }
            }
            BlockType.ATTACHMENT, BlockType.AUDIO, BlockType.VIDEO -> AttachmentBlock(block, onDelete)
            BlockType.SHAPE -> ShapeBlock(block, onChange, onDelete)
            BlockType.TOC -> TocBlock(allBlocks, onDelete)
            else -> TextBlock(
                block = block,
                index = index,
                readOnly = readOnly,
                focused = focused,
                spellEnabled = spellEnabled,
                onFocus = onFocus,
                onChange = onChange,
                onAddBelow = onAddBelow,
                onMenu = { menuOpen = true },
                onOpenUrl = onOpenUrl,
                selection = selection,
                onSelectionConsumed = onSelectionConsumed
            )
        }
    }

    // Block-level context menu (edit/insert/delete)
    if (menuOpen) {
        BlockMenuSheet(
            block = block,
            readOnly = readOnly,
            onDismiss = { menuOpen = false },
            onChange = onChange,
            onAddBelow = onAddBelow,
            onDelete = onDelete
        )
    }
    if (showChartEditor) {
        ChartDialog(
            initial = block.chart,
            onDismiss = { showChartEditor = false },
            onInsert = { kind, title, labels, values ->
                onChange(block.copy(type = BlockType.CHART, chart = ChartData(kind, title, labels, values)))
                showChartEditor = false
            }
        )
    }
    if (showImageTools) {
        ImageToolsSheet(
            block = block,
            onDismiss = { showImageTools = false },
            onChange = onChange,
            onCrop = { showImageTools = false; showCrop = true }
        )
    }
    if (showCrop) {
        CropDialog(
            block = block,
            onDismiss = { showCrop = false },
            onChange = { newPath -> onChange(block.copy(imagePath = newPath)); showCrop = false }
        )
    }
}

// ---------------- Text block ----------------

@Composable
private fun TextBlock(
    block: DocumentBlock,
    index: Int,
    readOnly: Boolean,
    focused: Boolean,
    spellEnabled: Boolean,
    onFocus: () -> Unit,
    onChange: (DocumentBlock) -> Unit,
    onAddBelow: () -> Unit,
    onMenu: () -> Unit,
    onOpenUrl: (String) -> Unit,
    selection: IntRange? = null,
    onSelectionConsumed: () -> Unit = {}
) {
    val c = LocalNotelyColors.current
    val initial = remember(block.id) {
        TextFieldValue(
            annotatedString = blockToAnnotated(block),
            selection = TextRange(block.runs.joinToString("") { it.text }.length)
        )
    }
    var field by remember(block.id) { mutableStateOf(initial) }

    // Find & replace (or a bookmark jump) asks for a specific range to be revealed.
    LaunchedEffect(selection) {
        val range = selection ?: return@LaunchedEffect
        val len = block.runs.joinToString("").length
        val start = range.first.coerceIn(0, len)
        val end = (range.last + 1).coerceIn(start, len)
        if (end > start) {
            field = field.copy(selection = TextRange(start, end))
            onSelectionConsumed()
        }
    }

    // Keep the field in sync when the model changes from elsewhere (toolbar, undo, AI insert).
    LaunchedEffect(block.runs, block.type, block.headingLevel) {
        val incoming = blockToAnnotated(block)
        val modelText = block.runs.joinToString("") { it.text }
        if (field.text != modelText) {
            val caret = field.selection.end.coerceAtMost(modelText.length)
            field = TextFieldValue(incoming, TextRange(caret))
        } else if (field.annotatedString != incoming) {
            field = field.copy(annotatedString = incoming)
        }
    }

    val align = when (block.align) {
        1 -> TextAlign.Center
        2 -> TextAlign.End
        3 -> TextAlign.Justify
        else -> TextAlign.Start
    }
    val baseSize = block.runs.firstOrNull()?.fontSizeSp ?: when {
        block.type == BlockType.HEADING -> (30 - block.headingLevel * 2).coerceAtLeast(14).toFloat()
        else -> 16f
    }
    val lineHeight = (baseSize * block.lineSpacing.coerceIn(0.9f, 2.5f)).sp
    val issues = if (spellEnabled && !readOnly) {
        com.notely.app.util.SpellCheck.rememberIssues(true, block.runs.joinToString("") { it.text })
    } else emptyList()
    val visual = remember(issues) { SpellUnderlineTransformation(issues) }

    val textStyle = TextStyle(
        fontSize = baseSize.sp,
        color = c.text,
        textAlign = align,
        lineHeight = lineHeight,
        fontFamily = when (block.runs.firstOrNull()?.fontFamily) {
            "Serif" -> androidx.compose.ui.text.font.FontFamily.Serif
            "Mono" -> androidx.compose.ui.text.font.FontFamily.Monospace
            else -> null
        }
    )

    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        when (block.type) {
            BlockType.BULLET -> Text("•", fontSize = baseSize.sp, color = c.text, modifier = Modifier.padding(top = 3.dp, end = 8.dp))
            BlockType.NUMBERED -> Text("${index + 1}.", fontSize = baseSize.sp, color = c.text, modifier = Modifier.padding(top = 3.dp, end = 8.dp))
            BlockType.CHECKLIST -> Checkbox(
                checked = block.checked,
                onCheckedChange = { onChange(block.copy(checked = it)) },
                enabled = !readOnly,
                modifier = Modifier.size(30.dp).padding(end = 4.dp)
            )
            else -> if (block.type == BlockType.HEADING) {
                Box(Modifier.width(3.dp).height((baseSize * 1.35f).dp).background(c.primary.copy(alpha = 0.25f), RoundedCornerShape(2.dp)))
                Spacer(Modifier.width(10.dp))
            }
        }
        BasicTextField(
            value = field,
            onValueChange = { v ->
                val caret = v.selection.end
                val newRuns = RunEditing.reflow(block.runs, v.text)
                field = TextFieldValue(
                    annotatedString = buildAnnotatedString {
                        newRuns.forEach { r -> withStyle(spanFor(r)) { append(r.text) } }
                    },
                    selection = TextRange(caret.coerceAtMost(v.text.length))
                )
                onChange(block.copy(runs = newRuns, type = block.type))
            },
            textStyle = textStyle,
            cursorBrush = androidx.compose.ui.graphics.SolidColor(c.primary),
            enabled = !readOnly,
            visualTransformation = visual,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = { onAddBelow() }),
            decorationBox = { inner ->
                Box(Modifier.fillMaxWidth()) {
                    if (field.text.isEmpty()) {
                        Text(
                            when {
                                block.type == BlockType.HEADING -> headingHint(block.headingLevel)
                                block.type == BlockType.CHECKLIST -> "To-do"
                                block.type == BlockType.TEXTBOX -> "Text box"
                                else -> "Tap to keep writing"
                            },
                            fontSize = if (block.type == BlockType.HEADING) baseSize.sp else 14.sp,
                            color = c.text2.copy(alpha = 0.8f)
                        )
                    }
                    inner()
                }
            },
            modifier = Modifier
                .weight(1f)
                .padding(start = (block.indentLevel * 18).dp, top = 2.dp, bottom = 2.dp)
                .onFocusChangedCompat { isFocused -> if (isFocused) onFocus() }
        )
        if (!readOnly) {
            Box(
                Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onMenu),
                contentAlignment = Alignment.Center
            ) { Text("⋮", fontSize = 15.sp, color = c.text2) }
        }
    }

    // Linked runs are tappable: show the link under the block so users can edit/remove it.
    val link = block.runs.firstOrNull { it.link != null }?.link
    if (link != null) {
        Text(
            "🔗 $link",
            fontSize = 11.sp,
            color = c.primary,
            modifier = Modifier.padding(start = 24.dp, top = 2.dp).clickable { onOpenUrl(link) }
        )
    }
}

private fun headingHint(level: Int) = when (level) {
    1 -> "Title"
    2 -> "Subtitle"
    in 3..8 -> "Heading ${level - 2}"
    else -> "Heading"
}

/** Draws a soft underline under words the platform spell checker flags. */
private class SpellUnderlineTransformation(
    private val issues: List<com.notely.app.util.SpellCheck.Issue>
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        if (issues.isEmpty()) return TransformedText(text, OffsetMapping.Identity)
        val styled = buildAnnotatedString {
            append(text)
            issues.forEach { issue ->
                if (issue.start in 0..text.length && issue.end in 0..text.length && issue.start < issue.end) {
                    addStyle(
                        SpanStyle(
                            textDecoration = TextDecoration.Underline,
                            color = Color(0xFFD32F2F)
                        ),
                        issue.start, issue.end
                    )
                }
            }
        }
        return TransformedText(styled, OffsetMapping.Identity)
    }
}

private fun Modifier.onFocusChangedCompat(onFocus: (Boolean) -> Unit): Modifier =
    this.onFocusChanged { onFocus(it.isFocused) }

// ---------------- Image / drawing ----------------

@Composable
private fun ImageBlock(block: DocumentBlock, readOnly: Boolean, onTools: () -> Unit, onDelete: () -> Unit) {
    val c = LocalNotelyColors.current
    val path = block.imagePath
    val bitmap by produceState<Bitmap?>(null, path, block.rotationDegrees) {
        value = withContextBitmap(path, block.rotationDegrees)
    }
    val bmp = bitmap
    Column(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .fillMaxWidth(block.imageWidthPercent.coerceIn(20, 100) / 100f)
                .clip(RoundedCornerShape(14.dp))
                .background(c.surface2)
                .clickable(enabled = !readOnly, onClick = onTools),
            contentAlignment = Alignment.Center
        ) {
            if (bmp != null) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = block.caption.ifBlank { "Inserted image" },
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🖼", fontSize = 28.sp)
                    Text("Image unavailable", fontSize = 12.sp, color = c.text2)
                }
            }
        }
        if (block.caption.isNotBlank()) {
            Text(block.caption, fontSize = 12.sp, color = c.text2, modifier = Modifier.padding(top = 6.dp))
        }
        if (!readOnly) {
            Row {
                TextButton(onClick = onTools) { Text("Resize · rotate · crop · caption", fontSize = 12.sp) }
                TextButton(onClick = onDelete) { Text("Remove", fontSize = 12.sp, color = MaterialTheme.colorScheme.error) }
            }
        }
    }
}

@Composable
private fun DrawingBlock(block: DocumentBlock, onDelete: () -> Unit) {
    val path = block.drawingPath ?: block.extra.ifBlank { null }
    val bitmap by produceState<Bitmap?>(null, path) { value = withContextBitmap(path, 0) }
    val bmp = bitmap
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        if (bmp != null) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = if (block.type == BlockType.SIGNATURE) "Signature" else "Drawing",
                contentScale = ContentScale.FillWidth,
                modifier = Modifier
                    .fillMaxWidth(block.imageWidthPercent.coerceIn(20, 100) / 100f)
                    .clip(RoundedCornerShape(12.dp))
            )
        } else {
            Text("✍ drawing", fontSize = 12.sp, color = LocalNotelyColors.current.text2)
        }
        TextButton(onClick = onDelete) {
            Text("Remove ${if (block.type == BlockType.SIGNATURE) "signature" else "drawing"}", fontSize = 12.sp, color = LocalNotelyColors.current.text2)
        }
    }
}

private suspend fun withContextBitmap(path: String?, rotation: Int): Bitmap? =
    withContext(Dispatchers.IO) {
        val bmp = ImageOps.decode(path) ?: return@withContext null
        if (rotation == 0) bmp else {
            val m = android.graphics.Matrix().apply { postRotate(rotation.toFloat()) }
            val out = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
            if (out !== bmp) bmp.recycle()
            out
        }
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ImageToolsSheet(
    block: DocumentBlock,
    onDismiss: () -> Unit,
    onChange: (DocumentBlock) -> Unit,
    onCrop: () -> Unit
) {
    val c = LocalNotelyColors.current
    val context = LocalContext.current
    var caption by remember { mutableStateOf(block.caption) }
    var rotation by remember { mutableStateOf(block.rotationDegrees) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = c.surface) {
        Column(Modifier.fillMaxWidth().padding(20.dp)) {
            com.notely.app.ui.components.SheetHeader("Image", "Size, rotation, crop and caption", onDismiss)
            Spacer(Modifier.height(12.dp))
            Text("Width: ${block.imageWidthPercent}%", fontSize = 13.sp, color = c.text2)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(25, 50, 75, 100).forEach { p ->
                    ClayPill("$p%", block.imageWidthPercent == p) {
                        onChange(block.copy(imageWidthPercent = p, caption = caption))
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Text("Rotate", fontSize = 13.sp, color = c.text2)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // One model update per tap: the sheet's `block` is re-read from the
                // document on every recomposition, so a second copy() built from the
                // stale parameter would silently discard this change.
                ClaySoftButton("↺ 90°") {
                    rotation = (rotation + 270) % 360
                    onChange(block.copy(rotationDegrees = rotation, caption = caption))
                }
                ClaySoftButton("↻ 90°") {
                    rotation = (rotation + 90) % 360
                    onChange(block.copy(rotationDegrees = rotation, caption = caption))
                }
                ClaySoftButton("Reset") {
                    rotation = 0
                    onChange(block.copy(rotationDegrees = 0, caption = caption))
                }
            }
            Spacer(Modifier.height(10.dp))
            Text("Align", fontSize = 13.sp, color = c.text2)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Left" to 0, "Center" to 1, "Right" to 2).forEach { (label, align) ->
                    ClayPill(label, block.align == align) {
                        onChange(block.copy(align = align, caption = caption))
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            ClaySoftButton("Crop…") { onCrop() }
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                caption, { caption = it },
                label = { Text("Caption") }, singleLine = true,
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)
            )
            Spacer(Modifier.height(8.dp))
            ClayPrimaryButton("Apply") { onChange(block.copy(caption = caption)); onDismiss() }
            Spacer(Modifier.height(26.dp))
        }
    }
}

@Composable
private fun CropDialog(block: DocumentBlock, onDismiss: () -> Unit, onChange: (String) -> Unit) {
    val context = LocalContext.current
    var pct by remember { mutableFloatStateOf(85f) }
    var status by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val bmp = remember(block.imagePath) { ImageOps.decode(block.imagePath, 900) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Crop image") },
        text = {
            Column {
                Text(
                    "Keep the central ${pct.toInt()}% of the image.",
                    fontSize = 13.sp, color = LocalNotelyColors.current.text2
                )
                Slider(
                    value = pct, onValueChange = { pct = it }, valueRange = 40f..100f,
                    modifier = Modifier.fillMaxWidth()
                )
                if (bmp != null) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "Crop preview",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(12.dp))
                    )
                }
                if (status.isNotBlank()) Text(status, fontSize = 12.sp, color = androidx.compose.material3.MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            ClayPrimaryButton("Crop") {
                val srcPath = block.imagePath
                if (srcPath == null) {
                    status = "Image missing"
                } else {
                    // Decode/crop on IO, then publish the result back on Main: Compose
                    // snapshot state must not be written from a background thread.
                    scope.launch {
                        val out = withContext(Dispatchers.IO) {
                            runCatching {
                                val src = ImageOps.decode(srcPath, 4000)
                                if (src == null) {
                                    null
                                } else {
                                    val side = (minOf(src.width, src.height) * pct / 100f).toInt().coerceAtLeast(16)
                                    val left = (src.width - side) / 2
                                    val top = (src.height - side) / 2
                                    val path = ImageOps.crop(context, srcPath, left, top, side, side)
                                    src.recycle()
                                    path
                                }
                            }.getOrNull()
                        }
                        if (out != null) onChange(out) else status = "Crop failed"
                    }
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

// ---------------- Table ----------------

@Composable
private fun TableBlock(block: DocumentBlock, readOnly: Boolean, onChange: (DocumentBlock) -> Unit, onDelete: () -> Unit) {
    val c = LocalNotelyColors.current
    val t = block.table ?: return
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).border(1.dp, c.border, RoundedCornerShape(12.dp))
        ) {
            for (r in 0 until t.rows) {
                Row(Modifier.fillMaxWidth()) {
                    for (col in 0 until t.cols) {
                        val cellIndex = r * t.cols + col
                        val cellText = t.cells.getOrNull(cellIndex) ?: ""
                        val isHeader = t.headerRow && r == 0
                        BasicTextField(
                            value = cellText,
                            onValueChange = { v ->
                                val newCells = t.cells.toMutableList()
                                while (newCells.size <= cellIndex) newCells.add("")
                                newCells[cellIndex] = v
                                onChange(block.copy(table = t.copy(cells = newCells)))
                            },
                            enabled = !readOnly,
                            textStyle = TextStyle(
                                fontSize = 12.sp,
                                color = if (isHeader) c.text else c.text,
                                fontWeight = if (isHeader) FontWeight.SemiBold else FontWeight.Normal
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .background(if (isHeader) c.primaryContainer else c.surface)
                                .border(0.5.dp, c.border)
                                .padding(6.dp)
                        )
                    }
                }
            }
        }
        if (!readOnly) {
            Row {
                TextButton(onClick = {
                    val newCells = t.cells + List(t.cols) { "" }
                    onChange(block.copy(table = t.copy(rows = t.rows + 1, cells = newCells)))
                }) { Text("+ Row", fontSize = 12.sp) }
                TextButton(onClick = {
                    val newCells = t.cells + List(t.rows) { "" }
                    onChange(block.copy(table = t.copy(cols = t.cols + 1, cells = newCells)))
                }) { Text("+ Col", fontSize = 12.sp) }
                TextButton(onClick = {
                    if (t.rows > 1) {
                        val newCells = t.cells.take((t.rows - 1) * t.cols)
                        onChange(block.copy(table = t.copy(rows = t.rows - 1, cells = newCells)))
                    }
                }) { Text("− Row", fontSize = 12.sp) }
                TextButton(onClick = {
                    if (t.cols > 1) {
                        val newCells = mutableListOf<String>()
                        for (r in 0 until t.rows) for (col in 0 until t.cols - 1) {
                            newCells.add(t.cells.getOrNull(r * t.cols + col) ?: "")
                        }
                        onChange(block.copy(table = t.copy(cols = t.cols - 1, cells = newCells)))
                    }
                }) { Text("− Col", fontSize = 12.sp) }
                var headerColorIndex by remember(block.id) { mutableStateOf(0) }
                TextButton(onClick = {
                    headerColorIndex = (headerColorIndex + 1) % CardColors.size
                    onChange(block.copy(table = t.copy(headerBgArgb = CardColors[headerColorIndex].value.toLong().toInt())))
                }) { Text("Header color", fontSize = 12.sp) }
                TextButton(onClick = onDelete) {
                    Text("Remove table", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                }
            }
            Text("Tip: header row styling follows the first row.", fontSize = 10.sp, color = c.text2)
        }
    }
}

// ---------------- Attachment / shape / TOC ----------------

@Composable
private fun AttachmentBlock(block: DocumentBlock, onDelete: () -> Unit) {
    val c = LocalNotelyColors.current
    val context = LocalContext.current
    val path = block.extra
    val file = remember(path) { if (path.isNullOrBlank()) null else File(path) }
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp)
            .clip(RoundedCornerShape(14.dp)).background(c.surface2).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            when (block.type) {
                BlockType.AUDIO -> "🎵"
                BlockType.VIDEO -> "🎬"
                else -> "📎"
            }, fontSize = 20.sp
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(file?.name ?: "Attachment", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = c.text)
            Text(
                when {
                    file == null -> "File reference missing"
                    !file.exists() -> "File no longer available"
                    else -> "%.1f KB · tap to open".format(file.length() / 1024.0)
                },
                fontSize = 11.sp, color = c.text2
            )
        }
        if (file != null && file.exists()) {
            TextButton(onClick = {
                try {
                    val uri = Exporters.uriFor(context, file)
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, ImageOps.mimeFor(file.name))
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                } catch (e: Exception) {
                    try {
                        Exporters.share(context, file, ImageOps.mimeFor(file.name), file.name)
                    } catch (_: Exception) { }
                }
            }) { Text("Open", fontSize = 12.sp) }
        }
        TextButton(onClick = onDelete) { Text("✕", fontSize = 12.sp, color = c.text2) }
    }
}

@Composable
private fun ShapeBlock(block: DocumentBlock, onChange: (DocumentBlock) -> Unit, onDelete: () -> Unit) {
    val c = LocalNotelyColors.current
    var label by remember(block.id) { mutableStateOf(block.extra.ifBlank { "rectangle" }) }
    var colorIndex by remember(block.id) { mutableStateOf(0) }
    val color = CardColors[colorIndex % CardColors.size]
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        val shapeModifier = Modifier
            .size(110.dp)
            .rotate(block.rotationDegrees.toFloat())
            .background(color.copy(alpha = 0.85f), shapeFor(label))
            .clickable { label = nextShape(label); onChange(block.copy(extra = label)) }
        Box(shapeModifier, contentAlignment = Alignment.Center) {
            Text(label.replaceFirstChar { it.uppercase() }, color = Color.White, fontSize = 11.sp)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            CardColors.forEachIndexed { i, col ->
                Box(
                    Modifier.size(20.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .background(col)
                        .clickable { colorIndex = i }
                )
            }
        }
        Row {
            ClaySoftButton("Rotate") { onChange(block.copy(rotationDegrees = (block.rotationDegrees + 45) % 360)) }
            Spacer(Modifier.width(8.dp))
            ClaySoftButton("Remove") { onDelete() }
        }
        Text("Tap the shape to cycle: rectangle → circle → triangle → diamond → arrow", fontSize = 10.sp, color = c.text2)
    }
}

private fun nextShape(current: String): String = when (current) {
    "rectangle" -> "circle"
    "circle" -> "triangle"
    "triangle" -> "diamond"
    "diamond" -> "arrow"
    else -> "rectangle"
}

private fun shapeFor(label: String) = when (label) {
    "circle" -> androidx.compose.foundation.shape.CircleShape
    "triangle", "diamond", "arrow" -> RoundedCornerShape(10.dp)
    else -> RoundedCornerShape(14.dp)
}

@Composable
private fun TocBlock(allBlocks: List<DocumentBlock>, onDelete: () -> Unit) {
    val c = LocalNotelyColors.current
    val headings = DocumentSerializer.headings(allBlocks)
    Column(
        Modifier.fillMaxWidth().padding(vertical = 6.dp)
            .clip(RoundedCornerShape(14.dp)).border(1.dp, c.border, RoundedCornerShape(14.dp))
            .padding(12.dp)
    ) {
        Text("Contents", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = c.text)
        Spacer(Modifier.height(4.dp))
        if (headings.isEmpty()) {
            Text("Add headings (Heading 1–8) and they will appear here automatically.", fontSize = 12.sp, color = c.text2)
        } else {
            headings.forEach { (level, title) ->
                Text(
                    title,
                    fontSize = (14 - (level - 1) * 0.5f).coerceAtLeast(10f).sp,
                    color = c.text,
                    modifier = Modifier.padding(start = ((level - 1) * 12).dp, top = 2.dp)
                )
            }
        }
        TextButton(onClick = onDelete) { Text("Remove contents", fontSize = 12.sp, color = c.text2) }
        Text("${headings.size} heading(s) · updates automatically", fontSize = 10.sp, color = c.text2)
    }
}

// ---------------- Block menu ----------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BlockMenuSheet(
    block: DocumentBlock,
    readOnly: Boolean,
    onDismiss: () -> Unit,
    onChange: (DocumentBlock) -> Unit,
    onAddBelow: () -> Unit,
    onDelete: () -> Unit
) {
    val c = LocalNotelyColors.current
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = c.surface) {
        Column(Modifier.fillMaxWidth().padding(20.dp)) {
            com.notely.app.ui.components.SheetHeader("Block options", block.type.name.lowercase().replace('_', ' '), onDismiss)
            Spacer(Modifier.height(10.dp))
            Text("Alignment", fontSize = 13.sp, color = c.text2)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Left" to 0, "Center" to 1, "Right" to 2, "Justify" to 3).forEach { (label, a) ->
                    ClayPill(label, block.align == a) { onChange(block.copy(align = a)) }
                }
            }
            Spacer(Modifier.height(10.dp))
            Text("Line spacing", fontSize = 13.sp, color = c.text2)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(1.0f, 1.3f, 1.55f, 1.8f, 2.0f).forEach { v ->
                    ClayPill("${v}×", block.lineSpacing == v) { onChange(block.copy(lineSpacing = v)) }
                }
            }
            Spacer(Modifier.height(10.dp))
            Text("Indent", fontSize = 13.sp, color = c.text2)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ClaySoftButton("←") { onChange(block.copy(indentLevel = (block.indentLevel - 1).coerceAtLeast(0))) }
                ClaySoftButton("→") { onChange(block.copy(indentLevel = (block.indentLevel + 1).coerceAtMost(6))) }
            }
            Spacer(Modifier.height(12.dp))
            if (!readOnly) {
                ClayPrimaryButton("Insert paragraph below") { onAddBelow(); onDismiss() }
                Spacer(Modifier.height(8.dp))
                ClaySoftButton("Delete block") { onDelete(); onDismiss() }
            } else {
                ClaySoftButton("Close") { onDismiss() }
            }
            Spacer(Modifier.height(26.dp))
        }
    }
}
