package com.share.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.share.app.ui.theme.KnoticTheme

/** Two interlocking arcs around a dot. The dot takes the text colour. */
@Composable
fun BrandLogo(size: Dp = 28.dp, modifier: Modifier = Modifier) {
    val dotColor = KnoticTheme.colors.text
    Canvas(modifier.size(size)) {
        val unit = this.size.minDimension / 80f
        // viewBox 10 10 80 80
        fun p(x: Float, y: Float) = Offset((x - 10f) * unit, (y - 10f) * unit)
        val stroke = Stroke(width = 11f * unit, cap = StrokeCap.Round)
        drawArc(
            color = Color(0xFF4F7CFF),
            startAngle = 90f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = p(16f, 18f),
            size = Size(56f * unit, 52f * unit),
            style = stroke,
        )
        drawArc(
            color = Color(0xFF22D3A6),
            startAngle = -90f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = p(28f, 30f),
            size = Size(56f * unit, 52f * unit),
            style = stroke,
        )
        drawCircle(color = dotColor, radius = 6.5f * unit, center = p(50f, 50f))
    }
}

/** A browser window and a phone with a file moving between them. */
@Composable
fun HeroGraphic(modifier: Modifier = Modifier) {
    val colors = KnoticTheme.colors
    val transition = rememberInfiniteTransition(label = "hero")
    val travel by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(3400), RepeatMode.Restart), label = "packet")
    val dash by transition.animateFloat(0f, -24f, infiniteRepeatable(tween(1600, easing = LinearEasing)), label = "dash")

    Canvas(modifier.fillMaxWidth().aspectRatio(320f / 132f)) {
        scale(size.width / 320f, pivot = Offset.Zero) {
            val brand = Brush.linearGradient(listOf(colors.brandFrom, colors.brandTo), Offset(0f, 0f), Offset(320f, 132f))
            val hairline = Stroke(width = 1.5f)

            // Browser window
            drawRoundRect(colors.surface, Offset(6f, 20f), Size(116f, 84f), CornerRadius(10f))
            drawRoundRect(colors.borderStrong, Offset(6f, 20f), Size(116f, 84f), CornerRadius(10f), style = hairline)
            drawLine(colors.borderStrong, Offset(6f, 34f), Offset(122f, 34f), strokeWidth = 1.5f)
            drawCircle(colors.pink, 2.5f, Offset(18f, 27f))
            drawCircle(colors.warn, 2.5f, Offset(27f, 27f))
            drawCircle(colors.emerald, 2.5f, Offset(36f, 27f))
            pill(colors.borderStrong, 20f, 48f, 60f, 7f)
            pill(colors.border, 20f, 62f, 88f, 7f)
            pill(colors.border, 20f, 76f, 44f, 7f)
            pill(colors.border, 6f, 110f, 116f, 5f)

            // The link between them
            drawLine(
                brush = brand,
                start = Offset(128f, 62f),
                end = Offset(196f, 62f),
                strokeWidth = 2.5f,
                cap = StrokeCap.Round,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 7f), dash),
            )

            // The packet crossing
            val alpha = when {
                travel < 0.12f -> travel / 0.12f
                travel > 0.88f -> (1f - travel) / 0.12f
                else -> 1f
            }
            translate(left = 96f * travel) {
                drawRoundRect(brand, Offset(126f, 54f), Size(16f, 16f), CornerRadius(5f), alpha = alpha)
                drawLine(Color.White.copy(alpha = alpha), Offset(130.5f, 62f), Offset(137.5f, 62f), 1.6f, StrokeCap.Round)
                drawLine(Color.White.copy(alpha = alpha), Offset(134f, 58.5f), Offset(134f, 65.5f), 1.6f, StrokeCap.Round)
            }

            // Phone
            drawRoundRect(colors.surface, Offset(206f, 10f), Size(62f, 112f), CornerRadius(14f))
            drawRoundRect(colors.borderStrong, Offset(206f, 10f), Size(62f, 112f), CornerRadius(14f), style = hairline)
            pill(colors.borderStrong, 228f, 17f, 18f, 3.5f)
            drawRoundRect(brand, Offset(216f, 32f), Size(42f, 34f), CornerRadius(8f), alpha = 0.16f)
            drawRoundRect(colors.brandTo.copy(alpha = 0.7f), Offset(224f, 44f), Size(26f, 5f), CornerRadius(2.5f))
            pill(colors.border, 216f, 76f, 42f, 5f)
            pill(colors.border, 216f, 88f, 30f, 5f)
            pill(colors.borderStrong, 226f, 108f, 22f, 4f)

            drawCircle(colors.cyanSoft, 5f, Offset(286f, 30f))
            drawCircle(colors.violetSoft, 3f, Offset(296f, 46f))
            drawCircle(colors.pinkSoft, 4f, Offset(292f, 96f))
        }
    }
}

/** The empty inbox: an open tray with something drifting toward it. */
@Composable
fun WaitingGraphic(active: Boolean, modifier: Modifier = Modifier) {
    val colors = KnoticTheme.colors
    val transition = rememberInfiniteTransition(label = "waiting")
    val drop by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(2800), RepeatMode.Restart), label = "drop")

    Canvas(modifier.size(width = 96.dp, height = 60.dp)) {
        scale(size.width / 96f, pivot = Offset.Zero) {
            val groupAlpha = if (active) 1f else 0.45f
            val brand = Brush.linearGradient(listOf(colors.brandFrom, colors.brandTo))

            val (offsetY, packetAlpha) = if (!active) {
                0f to 1f
            } else {
                when {
                    drop < 0.25f -> (-6f + 20f * (drop / 0.7f)) to (drop / 0.25f)
                    drop < 0.7f -> (-6f + 20f * (drop / 0.7f)) to 1f
                    else -> (14f + 6f * ((drop - 0.7f) / 0.3f)) to (1f - (drop - 0.7f) / 0.3f)
                }
            }
            translate(top = offsetY) {
                drawRoundRect(brand, Offset(40f, 4f), Size(16f, 16f), CornerRadius(5f), alpha = packetAlpha * groupAlpha)
                val arrow = Path().apply {
                    moveTo(48f, 9f); lineTo(48f, 15f)
                    moveTo(45f, 12f); lineTo(48f, 15f); lineTo(51f, 12f)
                }
                drawPath(arrow, Color.White.copy(alpha = packetAlpha * groupAlpha), style = Stroke(1.6f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }

            val tray = Path().apply {
                moveTo(14f, 34f); lineTo(34f, 34f); lineTo(38f, 41f); lineTo(58f, 41f); lineTo(62f, 34f); lineTo(82f, 34f)
                lineTo(82f, 48f); quadraticTo(82f, 52f, 78f, 52f); lineTo(18f, 52f); quadraticTo(14f, 52f, 14f, 48f); close()
            }
            drawPath(tray, colors.surface, alpha = groupAlpha)
            drawPath(tray, colors.borderStrong, alpha = groupAlpha, style = Stroke(1.5f, join = StrokeJoin.Round))
            val back = Path().apply {
                moveTo(22f, 34f); lineTo(22f, 26f); quadraticTo(22f, 23f, 25f, 23f); lineTo(71f, 23f)
                quadraticTo(74f, 23f, 74f, 26f); lineTo(74f, 34f)
            }
            drawPath(back, colors.border, alpha = groupAlpha, style = Stroke(1.5f))
            drawCircle(colors.cyanSoft, 2f, Offset(20f, 16f))
            drawCircle(colors.violetSoft, 2.5f, Offset(78f, 22f))
        }
    }
}

/** Slow, very low contrast colour behind everything. */
@Composable
fun BackgroundMesh(modifier: Modifier = Modifier) {
    val colors = KnoticTheme.colors
    Canvas(modifier.fillMaxSize()) {
        val vmax = maxOf(size.width, size.height) / 100f
        blob(colors.mesh1, Offset(11f * vmax, 5f * vmax), 30f * vmax)
        blob(colors.mesh2, Offset(size.width - 5f * vmax, 9f * vmax), 26f * vmax)
        blob(colors.mesh3, Offset(size.width * 0.3f + 15f * vmax, 55f * vmax), 22f * vmax)
    }
}

private fun DrawScope.blob(color: Color, center: Offset, radius: Float) {
    drawCircle(
        brush = Brush.radialGradient(listOf(color, color.copy(alpha = 0f)), center = center, radius = radius),
        radius = radius,
        center = center,
    )
}

private fun DrawScope.pill(color: Color, x: Float, y: Float, width: Float, height: Float) {
    drawRoundRect(color, Offset(x, y), Size(width, height), CornerRadius(height / 2f))
}
