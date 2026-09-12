package com.share.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.share.app.domain.model.ThemePreference

/**
 * The web client's design tokens. Colour is used to mean something: the brand
 * mark, the direction of a transfer, one hue per step.
 */
@Immutable
data class KnoticColors(
    val bg: Color,
    val surface: Color,
    val surface2: Color,
    val border: Color,
    val borderStrong: Color,
    val text: Color,
    val textMuted: Color,
    val textFaint: Color,
    val accent: Color,
    val accentSoft: Color,
    val success: Color,
    val successSoft: Color,
    val warn: Color,
    val warnSoft: Color,
    val danger: Color,
    val dangerSoft: Color,
    val brandFrom: Color,
    val brandTo: Color,
    val violet: Color,
    val violetSoft: Color,
    val cyan: Color,
    val cyanSoft: Color,
    val emerald: Color,
    val emeraldSoft: Color,
    val pink: Color,
    val pinkSoft: Color,
    val mesh1: Color,
    val mesh2: Color,
    val mesh3: Color,
    val isDark: Boolean,
)

private fun rgba(r: Int, g: Int, b: Int, a: Float) = Color(r, g, b, (a * 255).toInt())

val LightKnoticColors = KnoticColors(
    bg = Color(0xFFF6F7F9),
    surface = Color(0xFFFFFFFF),
    surface2 = Color(0xFFF1F3F7),
    border = rgba(12, 18, 32, 0.09f),
    borderStrong = rgba(12, 18, 32, 0.18f),
    text = Color(0xFF0D1117),
    textMuted = Color(0xFF5A6274),
    textFaint = Color(0xFF8B93A5),
    accent = Color(0xFF2563EB),
    accentSoft = rgba(37, 99, 235, 0.10f),
    success = Color(0xFF0E9F6E),
    successSoft = rgba(14, 159, 110, 0.12f),
    warn = Color(0xFFB45309),
    warnSoft = rgba(180, 83, 9, 0.12f),
    danger = Color(0xFFDC2626),
    dangerSoft = rgba(220, 38, 38, 0.10f),
    brandFrom = Color(0xFF6366F1),
    brandTo = Color(0xFF06B6D4),
    violet = Color(0xFF7C3AED),
    violetSoft = rgba(124, 58, 237, 0.10f),
    cyan = Color(0xFF0891B2),
    cyanSoft = rgba(8, 145, 178, 0.10f),
    emerald = Color(0xFF059669),
    emeraldSoft = rgba(5, 150, 105, 0.10f),
    pink = Color(0xFFDB2777),
    pinkSoft = rgba(219, 39, 119, 0.10f),
    mesh1 = rgba(99, 102, 241, 0.18f),
    mesh2 = rgba(6, 182, 212, 0.16f),
    mesh3 = rgba(219, 39, 119, 0.10f),
    isDark = false,
)

val DarkKnoticColors = KnoticColors(
    bg = Color(0xFF08090C),
    surface = Color(0xFF101218),
    surface2 = Color(0xFF171A22),
    border = rgba(255, 255, 255, 0.08f),
    borderStrong = rgba(255, 255, 255, 0.18f),
    text = Color(0xFFE9ECF3),
    textMuted = Color(0xFF949BAD),
    textFaint = Color(0xFF6C7386),
    accent = Color(0xFF5B93FF),
    accentSoft = rgba(91, 147, 255, 0.14f),
    success = Color(0xFF34D399),
    successSoft = rgba(52, 211, 153, 0.14f),
    warn = Color(0xFFFBBF24),
    warnSoft = rgba(251, 191, 36, 0.14f),
    danger = Color(0xFFF87171),
    dangerSoft = rgba(248, 113, 113, 0.14f),
    brandFrom = Color(0xFF818CF8),
    brandTo = Color(0xFF22D3EE),
    violet = Color(0xFFA78BFA),
    violetSoft = rgba(167, 139, 250, 0.14f),
    cyan = Color(0xFF22D3EE),
    cyanSoft = rgba(34, 211, 238, 0.14f),
    emerald = Color(0xFF34D399),
    emeraldSoft = rgba(52, 211, 153, 0.14f),
    pink = Color(0xFFF472B6),
    pinkSoft = rgba(244, 114, 182, 0.14f),
    mesh1 = rgba(99, 102, 241, 0.22f),
    mesh2 = rgba(6, 182, 212, 0.18f),
    mesh3 = rgba(219, 39, 119, 0.12f),
    isDark = true,
)

val LocalKnoticColors = staticCompositionLocalOf { LightKnoticColors }

object KnoticTheme {
    val colors: KnoticColors
        @Composable @ReadOnlyComposable get() = LocalKnoticColors.current
}

private val KnoticTypography = Typography(
    headlineSmall = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.02).em),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.01).em),
    titleSmall = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
    bodyMedium = TextStyle(fontSize = 13.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 11.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
    labelSmall = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.08.em),
)

val CodeFontFamily: FontFamily = FontFamily.Monospace

@Composable
fun KnoticTheme(
    preference: ThemePreference = ThemePreference.SYSTEM,
    content: @Composable () -> Unit,
) {
    val dark = when (preference) {
        ThemePreference.SYSTEM -> isSystemInDarkTheme()
        ThemePreference.LIGHT -> false
        ThemePreference.DARK -> true
    }
    val colors = if (dark) DarkKnoticColors else LightKnoticColors

    val scheme = if (dark) {
        darkColorScheme(
            primary = colors.accent,
            onPrimary = Color.White,
            secondary = colors.brandTo,
            background = colors.bg,
            onBackground = colors.text,
            surface = colors.surface,
            onSurface = colors.text,
            surfaceVariant = colors.surface2,
            onSurfaceVariant = colors.textMuted,
            outline = colors.borderStrong,
            outlineVariant = colors.border,
            error = colors.danger,
        )
    } else {
        lightColorScheme(
            primary = colors.accent,
            onPrimary = Color.White,
            secondary = colors.brandTo,
            background = colors.bg,
            onBackground = colors.text,
            surface = colors.surface,
            onSurface = colors.text,
            surfaceVariant = colors.surface2,
            onSurfaceVariant = colors.textMuted,
            outline = colors.borderStrong,
            outlineVariant = colors.border,
            error = colors.danger,
        )
    }

    CompositionLocalProvider(LocalKnoticColors provides colors) {
        MaterialTheme(colorScheme = scheme, typography = KnoticTypography) {
            SystemBarsEffect(darkIcons = !dark)
            content()
        }
    }
}

/** Keeps status and navigation bar icons legible against the theme. */
@Composable
internal expect fun SystemBarsEffect(darkIcons: Boolean)
