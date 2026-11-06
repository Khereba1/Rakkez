package com.khereba1.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin

fun Modifier.panel(color: Color, radius: Dp = 20.dp): Modifier =
    this.clip(RoundedCornerShape(radius)).background(color)

/** Round checkbox drawn by hand (no icon library needed). */
@Composable
fun RkCheck(checked: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val primary = MaterialTheme.colorScheme.primary
    val onPrimary = MaterialTheme.colorScheme.onPrimary
    val outline = MaterialTheme.colorScheme.outline
    Box(
        modifier = modifier
            .size(44.dp)
            .clip(CircleShape)
            .clickable(onClick = onToggle),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(26.dp)) {
            val w = 2.2.dp.toPx()
            if (checked) {
                drawCircle(color = primary)
                val p = Path()
                p.moveTo(size.width * 0.28f, size.height * 0.52f)
                p.lineTo(size.width * 0.44f, size.height * 0.68f)
                p.lineTo(size.width * 0.74f, size.height * 0.34f)
                drawPath(p, onPrimary, style = Stroke(width = 2.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            } else {
                drawCircle(color = outline, radius = size.minDimension / 2f - w / 2f, style = Stroke(width = w))
            }
        }
    }
}

@Composable
fun PlayButton(onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val tri = cs.onPrimaryContainer
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(cs.primaryContainer)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(14.dp)) {
            val p = Path()
            p.moveTo(size.width * 0.15f, 0f)
            p.lineTo(size.width, size.height / 2f)
            p.lineTo(size.width * 0.15f, size.height)
            p.close()
            drawPath(p, tri)
        }
    }
}

@Composable
fun Pill(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Color = MaterialTheme.colorScheme.primary,
    onAccent: Color = MaterialTheme.colorScheme.onPrimary,
) {
    val bg = if (selected) accent else MaterialTheme.colorScheme.surfaceVariant
    val fg = if (selected) onAccent else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = fg, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

@Composable
fun StepBtn(glyph: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(glyph, fontSize = 20.sp, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
fun Stepper(label: String, value: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(label, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp)
        StepBtn("−", onMinus)
        Text(
            value,
            Modifier.widthIn(min = 64.dp),
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        StepBtn("+", onPlus)
    }
}

/** The timer face: 60 ticks that go dark one by one as time passes. */
@Composable
fun DotRing(
    progress: Float,
    lit: Color,
    dim: Color,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val n = 60
            val r = size.minDimension / 2f - 6.dp.toPx()
            val consumed = (progress * n).toInt().coerceIn(0, n)
            for (i in 0 until n) {
                val ang = (i.toDouble() / n.toDouble()) * 2.0 * Math.PI - Math.PI / 2.0
                val cosA = cos(ang).toFloat()
                val sinA = sin(ang).toFloat()
                val major = i % 5 == 0
                val len = if (major) r * 0.15f else r * 0.09f
                val start = Offset(center.x + cosA * (r - len), center.y + sinA * (r - len))
                val end = Offset(center.x + cosA * r, center.y + sinA * r)
                drawLine(
                    color = if (i >= consumed) lit else dim,
                    start = start,
                    end = end,
                    strokeWidth = if (major) 5.dp.toPx() else 3.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }
        }
        content()
    }
}
