package com.khereba1.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class RkIc { CAMERA, VIDEO, MIC, PEN, FILE, PLAY, PAUSE, GALLERY, PLUS }

/** Small hand-drawn line icons, so every icon has the same weight and colour (no emoji). */
@Composable
fun RkIcon(ic: RkIc, tint: Color, size: Dp = 22.dp) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val sw = w * 0.085f
        val st = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round)
        when (ic) {
            RkIc.CAMERA -> {
                drawRoundRect(tint, Offset(w * 0.08f, h * 0.28f), Size(w * 0.84f, h * 0.58f), CornerRadius(w * 0.12f), style = st)
                val bump = Path()
                bump.moveTo(w * 0.32f, h * 0.28f)
                bump.lineTo(w * 0.40f, h * 0.14f)
                bump.lineTo(w * 0.60f, h * 0.14f)
                bump.lineTo(w * 0.68f, h * 0.28f)
                drawPath(bump, tint, style = st)
                drawCircle(tint, w * 0.19f, Offset(w * 0.5f, h * 0.57f), style = st)
            }
            RkIc.VIDEO -> {
                drawRoundRect(tint, Offset(w * 0.06f, h * 0.24f), Size(w * 0.58f, h * 0.52f), CornerRadius(w * 0.12f), style = st)
                val tri = Path()
                tri.moveTo(w * 0.70f, h * 0.45f)
                tri.lineTo(w * 0.94f, h * 0.30f)
                tri.lineTo(w * 0.94f, h * 0.70f)
                tri.lineTo(w * 0.70f, h * 0.55f)
                tri.close()
                drawPath(tri, tint, style = st)
            }
            RkIc.MIC -> {
                drawRoundRect(tint, Offset(w * 0.36f, h * 0.06f), Size(w * 0.28f, h * 0.50f), CornerRadius(w * 0.14f), style = st)
                drawArc(tint, 0f, 180f, false, Offset(w * 0.22f, h * 0.26f), Size(w * 0.56f, h * 0.46f), style = st)
                drawLine(tint, Offset(w * 0.5f, h * 0.72f), Offset(w * 0.5f, h * 0.92f), sw, StrokeCap.Round)
                drawLine(tint, Offset(w * 0.34f, h * 0.92f), Offset(w * 0.66f, h * 0.92f), sw, StrokeCap.Round)
            }
            RkIc.PEN -> {
                val p = Path()
                p.moveTo(w * 0.18f, h * 0.70f)
                p.lineTo(w * 0.62f, h * 0.26f)
                p.lineTo(w * 0.76f, h * 0.40f)
                p.lineTo(w * 0.32f, h * 0.84f)
                p.lineTo(w * 0.14f, h * 0.88f)
                p.close()
                drawPath(p, tint, style = st)
                drawLine(tint, Offset(w * 0.54f, h * 0.34f), Offset(w * 0.68f, h * 0.48f), sw, StrokeCap.Round)
                drawLine(tint, Offset(w * 0.52f, h * 0.90f), Offset(w * 0.90f, h * 0.90f), sw, StrokeCap.Round)
            }
            RkIc.FILE -> {
                val p = Path()
                p.moveTo(w * 0.24f, h * 0.08f)
                p.lineTo(w * 0.60f, h * 0.08f)
                p.lineTo(w * 0.80f, h * 0.28f)
                p.lineTo(w * 0.80f, h * 0.90f)
                p.lineTo(w * 0.24f, h * 0.90f)
                p.close()
                drawPath(p, tint, style = st)
                val f = Path()
                f.moveTo(w * 0.58f, h * 0.08f)
                f.lineTo(w * 0.58f, h * 0.30f)
                f.lineTo(w * 0.80f, h * 0.30f)
                drawPath(f, tint, style = st)
                drawLine(tint, Offset(w * 0.36f, h * 0.52f), Offset(w * 0.68f, h * 0.52f), sw, StrokeCap.Round)
                drawLine(tint, Offset(w * 0.36f, h * 0.68f), Offset(w * 0.68f, h * 0.68f), sw, StrokeCap.Round)
            }
            RkIc.PLAY -> {
                val p = Path()
                p.moveTo(w * 0.28f, h * 0.18f)
                p.lineTo(w * 0.82f, h * 0.50f)
                p.lineTo(w * 0.28f, h * 0.82f)
                p.close()
                drawPath(p, tint, style = Fill)
            }
            RkIc.PAUSE -> {
                drawRoundRect(tint, Offset(w * 0.24f, h * 0.18f), Size(w * 0.18f, h * 0.64f), CornerRadius(w * 0.05f), style = Fill)
                drawRoundRect(tint, Offset(w * 0.58f, h * 0.18f), Size(w * 0.18f, h * 0.64f), CornerRadius(w * 0.05f), style = Fill)
            }
            RkIc.PLUS -> {
                val pw = w * 0.136f
                drawLine(tint, Offset(w * 0.5f, h * 0.12f), Offset(w * 0.5f, h * 0.88f), pw, StrokeCap.Round)
                drawLine(tint, Offset(w * 0.12f, h * 0.5f), Offset(w * 0.88f, h * 0.5f), pw, StrokeCap.Round)
            }
            RkIc.GALLERY -> {
                drawRoundRect(tint, Offset(w * 0.08f, h * 0.14f), Size(w * 0.84f, h * 0.72f), CornerRadius(w * 0.12f), style = st)
                drawCircle(tint, w * 0.08f, Offset(w * 0.32f, h * 0.36f), style = Fill)
                val m = Path()
                m.moveTo(w * 0.10f, h * 0.80f)
                m.lineTo(w * 0.40f, h * 0.52f)
                m.lineTo(w * 0.60f, h * 0.70f)
                m.lineTo(w * 0.72f, h * 0.58f)
                m.lineTo(w * 0.90f, h * 0.76f)
                drawPath(m, tint, style = st)
            }
        }
    }
}

/** A pill with an icon and a label; equal width inside a Row (like SegPill). */
@Composable
fun RowScope.IconPill(ic: RkIc, label: String, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier
            .weight(1f)
            .clip(RoundedCornerShape(50))
            .background(cs.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
    ) {
        RkIcon(ic, cs.onSurfaceVariant, 18.dp)
        Spacer(Modifier.width(6.dp))
        Text(label, color = cs.onSurfaceVariant, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}
