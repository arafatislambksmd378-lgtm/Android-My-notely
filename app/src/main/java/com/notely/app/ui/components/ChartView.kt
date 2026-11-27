package com.notely.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.notely.app.doc.ChartData
import com.notely.app.ui.theme.AccentBlue
import com.notely.app.ui.theme.AccentGreen
import com.notely.app.ui.theme.AccentOrange
import com.notely.app.ui.theme.AccentPink
import com.notely.app.ui.theme.AccentYellow
import com.notely.app.ui.theme.LocalNotelyColors
import com.notely.app.ui.theme.PrimaryPurple

private val chartPalette = listOf(PrimaryPurple, AccentBlue, AccentGreen, AccentYellow, AccentPink, AccentOrange)

/** Vector chart rendering for bar / column / line / area / pie — drawn from the stored ChartData. */
@Composable
fun ChartView(chart: ChartData, modifier: Modifier = Modifier) {
    val c = LocalNotelyColors.current
    val values = chart.values
    val labels = chart.labels
    if (values.isEmpty()) {
        Text("No chart data", fontSize = 12.sp, color = c.text2, modifier = modifier)
        return
    }
    val maxV = values.maxOrNull()?.takeIf { it > 0 } ?: 1.0
    Column(modifier) {
        if (chart.title.isNotBlank()) {
            Text(chart.title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = c.text)
            Spacer(Modifier.height(6.dp))
        }
        if (chart.kind == "pie") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Canvas(Modifier.size(130.dp)) {
                    val total = values.sum().let { if (it <= 0) 1.0 else it }
                    var start = -90f
                    val side = minOf(size.width, size.height)
                    values.forEachIndexed { i, v ->
                        val sweep = (v / total * 360.0).toFloat()
                        drawArc(
                            color = chartPalette[i % chartPalette.size],
                            startAngle = start, sweepAngle = sweep, useCenter = true,
                            topLeft = Offset((size.width - side) / 2f, (size.height - side) / 2f),
                            size = Size(side, side)
                        )
                        start += sweep
                    }
                }
                Spacer(Modifier.width(10.dp))
                Column {
                    labels.forEachIndexed { i, l ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier.size(10.dp)
                                    .background(chartPalette[i % chartPalette.size], androidx.compose.foundation.shape.RoundedCornerShape(3.dp))
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("$l  ${values.getOrNull(i) ?: 0.0}", fontSize = 11.sp, color = c.text2)
                        }
                    }
                }
            }
            return@Column
        }
        Canvas(Modifier.fillMaxWidth().height(150.dp)) {
            val w = size.width
            val h = size.height
            val baseline = h - 18f
            val chartH = baseline - 6f
            val n = values.size
            val slot = w / n.coerceAtLeast(1)
            // Baseline
            drawLine(c.border, Offset(0f, baseline), Offset(w, baseline), strokeWidth = 1.5f)

            when (chart.kind) {
                "line", "area" -> {
                    val step = if (n > 1) (w - 8f) / (n - 1) else 0f
                    val pts = values.mapIndexed { i, v ->
                        Offset(4f + step * i, baseline - (v / maxV * chartH).toFloat())
                    }
                    if (chart.kind == "area" && pts.size > 1) {
                        val path = Path().apply {
                            moveTo(pts.first().x, baseline)
                            pts.forEach { lineTo(it.x, it.y) }
                            lineTo(pts.last().x, baseline)
                            close()
                        }
                        drawPath(path, PrimaryPurple.copy(alpha = 0.18f))
                    }
                    for (i in 1 until pts.size) {
                        drawLine(PrimaryPurple, pts[i - 1], pts[i], strokeWidth = 4f)
                    }
                    pts.forEach { drawCircle(PrimaryPurple, radius = 5f, center = it) }
                    labels.forEachIndexed { i, _ ->
                        if (i < pts.size) drawCircle(c.text2.copy(alpha = 0.0f), radius = 0f, center = pts[i])
                    }
                }
                else -> {
                    val barW = slot * 0.55f
                    values.forEachIndexed { i, v ->
                        val barH = (v / maxV * chartH).toFloat()
                        val left = slot * i + (slot - barW) / 2f
                        drawRoundRect(
                            color = chartPalette[i % chartPalette.size],
                            topLeft = Offset(left, baseline - barH),
                            size = Size(barW, barH),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f, 6f)
                        )
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            labels.forEach { l ->
                Text(l.take(12), fontSize = 10.sp, color = c.text2)
            }
        }
    }
}
