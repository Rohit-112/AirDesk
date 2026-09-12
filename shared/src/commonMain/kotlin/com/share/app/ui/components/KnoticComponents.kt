package com.share.app.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.share.app.ui.theme.KnoticTheme

/** The main surface: white (or near black) with a hairline border and soft shadow. */
@Composable
fun KnoticCard(
    modifier: Modifier = Modifier,
    borderColor: Color = KnoticTheme.colors.border,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = KnoticTheme.colors
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .shadow(elevation = if (colors.isDark) 0.dp else 6.dp, shape = shape, ambientColor = Color(0x14000000), spotColor = Color(0x14000000))
            .clip(shape)
            .background(colors.surface)
            .border(1.dp, borderColor, shape)
            .padding(contentPadding),
        content = content,
    )
}

/** A recessed surface for secondary content. */
@Composable
fun QuietCard(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = KnoticTheme.colors
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.surface2)
            .border(1.dp, colors.border, shape)
            .padding(contentPadding),
        content = content,
    )
}

enum class KnoticButtonStyle { PRIMARY, SECONDARY, GHOST, SUCCESS }

@Composable
fun KnoticButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    style: KnoticButtonStyle = KnoticButtonStyle.PRIMARY,
    enabled: Boolean = true,
    compact: Boolean = false,
) {
    val colors = KnoticTheme.colors
    val (container, content, border) = when (style) {
        KnoticButtonStyle.PRIMARY -> Triple(colors.accent, Color.White, Color.Transparent)
        KnoticButtonStyle.SUCCESS -> Triple(colors.success, Color.White, Color.Transparent)
        KnoticButtonStyle.SECONDARY -> Triple(colors.surface2, colors.text, colors.border)
        KnoticButtonStyle.GHOST -> Triple(Color.Transparent, colors.textMuted, Color.Transparent)
    }
    val height = if (compact) 34.dp else 42.dp
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = height),
        shape = CircleShape,
        border = if (border == Color.Transparent) null else androidx.compose.foundation.BorderStroke(1.dp, border),
        colors = ButtonDefaults.buttonColors(
            containerColor = container,
            contentColor = content,
            disabledContainerColor = container.copy(alpha = container.alpha * 0.45f),
            disabledContentColor = content.copy(alpha = 0.45f),
        ),
        contentPadding = PaddingValues(horizontal = if (compact) 12.dp else 16.dp, vertical = 0.dp),
        elevation = null,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(if (compact) 14.dp else 16.dp))
            Box(Modifier.size(6.dp))
        }
        Text(text, fontSize = if (compact) 12.sp else 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
fun KnoticIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = KnoticTheme.colors.textMuted,
    size: Dp = 40.dp,
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.size(size),
        colors = IconButtonDefaults.iconButtonColors(contentColor = tint, disabledContentColor = tint.copy(alpha = 0.4f)),
    ) {
        Icon(icon, contentDescription = contentDescription, modifier = Modifier.size(size * 0.45f))
    }
}

/** A small uppercase label, used for section eyebrows. */
@Composable
fun Eyebrow(text: String, modifier: Modifier = Modifier, color: Color = KnoticTheme.colors.textFaint) {
    Text(
        text = text.uppercase(),
        modifier = modifier,
        color = color,
        fontSize = 10.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.9.sp,
    )
}

enum class ChipTone { LIVE, WAITING, IDLE, DANGER }

/** One glance should answer "can I send right now?". */
@Composable
fun StatusChip(label: String, tone: ChipTone, modifier: Modifier = Modifier) {
    val colors = KnoticTheme.colors
    val toneColor = when (tone) {
        ChipTone.LIVE -> colors.success
        ChipTone.WAITING -> colors.warn
        ChipTone.IDLE -> colors.textFaint
        ChipTone.DANGER -> colors.danger
    }
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(colors.surface2)
            .border(1.dp, colors.border, CircleShape)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        StatusDot(color = toneColor, pulsing = tone == ChipTone.LIVE)
        Text(
            text = label.uppercase(),
            color = if (tone == ChipTone.IDLE) colors.textMuted else toneColor,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.7.sp,
            maxLines = 1,
        )
    }
}

@Composable
private fun StatusDot(color: Color, pulsing: Boolean) {
    Box(Modifier.size(8.dp), contentAlignment = Alignment.Center) {
        if (pulsing) {
            val transition = rememberInfiniteTransition(label = "ping")
            val progress by transition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(2200), RepeatMode.Restart),
                label = "ping-progress",
            )
            Box(
                Modifier
                    .size(8.dp)
                    .scale(1f + progress * 1.6f)
                    .graphicsLayer { alpha = (0.7f * (1f - progress / 0.7f)).coerceAtLeast(0f) }
                    .clip(CircleShape)
                    .background(color),
            )
        }
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
    }
}

/** A transient message about the last failed action. */
@Composable
fun AlertBanner(message: String, onDismiss: (() -> Unit)?, modifier: Modifier = Modifier) {
    val colors = KnoticTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.dangerSoft)
            .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = message,
            color = colors.danger,
            fontSize = 13.sp,
            modifier = Modifier.weight(1f).padding(vertical = 8.dp),
        )
        if (onDismiss != null) {
            KnoticIconButton(Icons.Rounded.Close, "Dismiss", onDismiss, tint = colors.danger, size = 36.dp)
        }
    }
}

@Composable
fun ProgressTrack(fraction: Float?, brush: Brush, modifier: Modifier = Modifier) {
    val colors = KnoticTheme.colors
    Box(
        modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(CircleShape)
            .background(colors.surface2),
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction?.coerceIn(0f, 1f) ?: 1f)
                .height(6.dp)
                .graphicsLayer { alpha = if (fraction == null) 0.4f else 1f }
                .clip(CircleShape)
                .drawBehind { drawRect(brush) },
        )
    }
}

/** A round tinted badge holding an icon. */
@Composable
fun ToneBadge(icon: ImageVector, background: Color, tint: Color, modifier: Modifier = Modifier, size: Dp = 36.dp, rounded: Boolean = true) {
    Box(
        modifier
            .size(size)
            .clip(if (rounded) CircleShape else RoundedCornerShape(12.dp))
            .background(background),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.46f))
    }
}
