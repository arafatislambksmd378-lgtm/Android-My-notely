package com.notely.app.ui.editor

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.notely.app.ui.components.ClayPill
import com.notely.app.ui.components.ClayPrimaryButton
import com.notely.app.ui.components.ClaySoftButton
import com.notely.app.ui.components.SheetHeader
import com.notely.app.ui.theme.LocalNotelyColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Drawing / handwriting workspace.
 * Tools: pen, highlighter, eraser, line and shape (rect/ellipse), color, stroke width,
 * undo, redo, clear. Saves a real PNG of the strokes and inserts it into the note.
 */
data class StrokePath(
    val path: Path,
    val color: Color,
    val width: Float,
    val shape: String = "free", // free | line | rect | ellipse
    val highlighter: Boolean = false,
    val start: Offset = Offset.Zero,
    val end: Offset = Offset.Zero
)

private enum class Tool { PEN, HIGHLIGHTER, ERASER, LINE, RECT, ELLIPSE }

@Composable
fun DrawingScreen(onDismiss: () -> Unit, onSave: (String) -> Unit) {
    val c = LocalNotelyColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var strokes by remember { mutableStateOf<List<StrokePath>>(emptyList()) }
    var redo by remember { mutableStateOf<List<StrokePath>>(emptyList()) }
    var current by remember { mutableStateOf<StrokePath?>(null) }
    var tool by remember { mutableStateOf(Tool.PEN) }
    var color by remember { mutableStateOf(Color(0xFF7040E8)) }
    var width by remember { mutableStateOf(6f) }
    var status by remember { mutableStateOf("") }
    var canvasSize by remember { mutableStateOf(Size(1f, 1f)) }
    // Non-zero start point: a single tap must still produce a visible dot.
    fun freehand(p: Offset) = Path().apply { moveTo(p.x, p.y); lineTo(p.x + 0.5f, p.y + 0.5f) }

    fun add(stroke: StrokePath) {
        strokes = strokes + stroke
        redo = emptyList()
    }

    fun clearAll() {
        strokes = emptyList()
        redo = emptyList()
    }

    Column(Modifier.fillMaxSize().background(c.bg).padding(14.dp)) {
        SheetHeader("Drawing", "Finger & stylus supported · saved into your note", onDismiss)
        Spacer(Modifier.height(8.dp))

        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(
                listOf(
                    Tool.PEN to "✒ Pen",
                    Tool.HIGHLIGHTER to "🖍 Highlight",
                    Tool.ERASER to "◻ Eraser",
                    Tool.LINE to "╱ Line",
                    Tool.RECT to "▭ Rect",
                    Tool.ELLIPSE to "◯ Ellipse"
                )
            ) { (t, label) ->
                ClayPill(label, tool == t) { tool = t }
            }
        }
        Spacer(Modifier.height(8.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            listOf(
                Color(0xFF7040E8), Color(0xFF24232B), Color(0xFFD32F2F),
                Color(0xFF438CEB), Color(0xFF39B980), Color(0xFFF2C94C)
            ).forEach { col ->
                Box(
                    Modifier.size(26.dp).clip(RoundedCornerShape(9.dp))
                        .background(col)
                        .border(2.dp, if (color == col) c.primary else Color.Transparent, RoundedCornerShape(9.dp))
                        .clickable { color = col },
                    contentAlignment = Alignment.Center
                ) { if (color == col) Text("✓", fontSize = 10.sp, color = Color.White) }
                Spacer(Modifier.width(6.dp))
            }
            Spacer(Modifier.weight(1f))
            ClaySoftButton("−") { width = (width - 2f).coerceAtLeast(2f) }
            Text(" ${width.toInt()} ", fontSize = 13.sp, color = c.text)
            ClaySoftButton("+") { width = (width + 2f).coerceAtMost(40f) }
        }
        Spacer(Modifier.height(8.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ClaySoftButton("Undo") {
                if (strokes.isNotEmpty()) {
                    redo = redo + strokes.last()
                    strokes = strokes.dropLast(1)
                }
            }
            ClaySoftButton("Redo") {
                if (redo.isNotEmpty()) {
                    strokes = strokes + redo.last()
                    redo = redo.dropLast(1)
                }
            }
            ClaySoftButton("Clear") { clearAll() }
        }
        Spacer(Modifier.height(10.dp))

        Box(
            Modifier.weight(1f).fillMaxWidth()
                .onSizeChanged { canvasSize = Size(it.width.toFloat(), it.height.toFloat()) }
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White)
                .border(1.dp, c.border, RoundedCornerShape(16.dp))
                .pointerInput(tool, color, width) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            current = when (tool) {
                                Tool.PEN -> StrokePath(Path().apply { moveTo(offset.x, offset.y) }, color, width)
                                Tool.HIGHLIGHTER -> StrokePath(
                                    Path().apply { moveTo(offset.x, offset.y) }, color.copy(alpha = 0.35f), width * 3,
                                    highlighter = true
                                )
                                Tool.ERASER -> StrokePath(Path().apply { moveTo(offset.x, offset.y) }, Color.White, width * 4)
                                Tool.LINE -> StrokePath(Path(), color, width, shape = "line", start = offset, end = offset)
                                Tool.RECT -> StrokePath(Path(), color, width, shape = "rect", start = offset, end = offset)
                                Tool.ELLIPSE -> StrokePath(Path(), color, width, shape = "ellipse", start = offset, end = offset)
                            }
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            val cur = current ?: return@detectDragGestures
                            current = when (cur.shape) {
                                "free" -> {
                                    // Copy instead of mutating: Compose paths are not observed
                                    // when mutated in place, so a fresh stroke forces redraw.
                                    val p = Path().apply {
                                        addPath(cur.path)
                                        lineTo(change.position.x, change.position.y)
                                    }
                                    cur.copy(path = p)
                                }
                                "line" -> {
                                    val p = Path().apply {
                                        moveTo(cur.start.x, cur.start.y)
                                        lineTo(change.position.x, change.position.y)
                                    }
                                    cur.copy(path = p, end = change.position)
                                }
                                "rect" -> {
                                    val p = Path().apply {
                                        addRect(
                                            androidx.compose.ui.geometry.Rect(
                                                cur.start, androidx.compose.ui.geometry.Offset(
                                                    (change.position.x - cur.start.x).coerceAtLeast(1f) + cur.start.x,
                                                    (change.position.y - cur.start.y).coerceAtLeast(1f) + cur.start.y
                                                )
                                            )
                                        )
                                    }
                                    cur.copy(path = p, end = change.position)
                                }
                                "ellipse" -> {
                                    val p = Path().apply {
                                        addOval(
                                            androidx.compose.ui.geometry.Rect(
                                                cur.start, androidx.compose.ui.geometry.Offset(
                                                    (change.position.x - cur.start.x).coerceAtLeast(1f) + cur.start.x,
                                                    (change.position.y - cur.start.y).coerceAtLeast(1f) + cur.start.y
                                                )
                                            )
                                        )
                                    }
                                    cur.copy(path = p, end = change.position)
                                }
                                else -> cur
                            }
                        },
                        onDragEnd = {
                            current?.let { add(it) }
                            current = null
                        },
                        onDragCancel = { current = null }
                    )
                }
        ) {
            Canvas(Modifier.fillMaxSize()) {
                fun drawAll(list: List<StrokePath>) {
                    list.forEach { s ->
                        drawPath(
                            path = s.path,
                            color = s.color,
                            style = Stroke(
                                width = s.width,
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round
                            )
                        )
                    }
                }
                drawAll(strokes)
                current?.let { drawAll(listOf(it)) }
            }
        }

        Spacer(Modifier.height(10.dp))
        ClayPrimaryButton("Save drawing & insert", enabled = strokes.isNotEmpty()) {
            scope.launch(Dispatchers.IO) {
                val result = runCatching {
                    val dir = File(context.filesDir, "drawings").apply { mkdirs() }
                    val dest = File(dir, "draw_${System.currentTimeMillis()}.png")
                    val wPx = 1080
                    val safeW = canvasSize.width.coerceAtLeast(1f)
                    val safeH = canvasSize.height.coerceAtLeast(1f)
                    val hPx = ((wPx.toFloat() * safeH / safeW).toInt()).coerceIn(200, 4000)
                    val bmp = Bitmap.createBitmap(wPx, hPx, Bitmap.Config.ARGB_8888)
                    val canvas = android.graphics.Canvas(bmp)
                    canvas.drawColor(android.graphics.Color.WHITE)
                    val sx = wPx / safeW
                    val sy = hPx / safeH
                    val paint = android.graphics.Paint().apply {
                        isAntiAlias = true
                        style = android.graphics.Paint.Style.STROKE
                        strokeCap = android.graphics.Paint.Cap.ROUND
                        strokeJoin = android.graphics.Paint.Join.ROUND
                    }
                    strokes.forEach { s ->
                        canvas.save()
                        canvas.scale(sx, sy)
                        paint.color = s.color.toArgb()
                        paint.strokeWidth = s.width
                        paint.alpha = (s.color.alpha * 255).toInt().coerceIn(0, 255)
                        if (s.highlighter) paint.alpha = 90
                        canvas.drawPath(s.path.asAndroidPath(), paint)
                        canvas.restore()
                    }
                    java.io.FileOutputStream(dest).use { out ->
                        bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
                    }
                    bmp.recycle()
                    dest.absolutePath
                }
                withContext(Dispatchers.Main) {
                    result.onSuccess { onSave(it) }
                        .onFailure { status = "Save failed: ${it.message}" }
                }
            }
        }
        if (status.isNotBlank()) Text(status, fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
        Text(
            "Drawings are saved as a high-quality PNG and embedded in the note. " +
                "The PNG is also exported with PDF/DOCX/ODT documents.",
            fontSize = 10.sp, color = c.text2
        )
    }
}
