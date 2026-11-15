package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Immutable
data class TelemetrySemanticColors(
    val rootBadge: Color,
    val rootBadgeContainer: Color,
    val nonRootBadge: Color,
    val nonRootBadgeContainer: Color,
    val protectedAccent: Color,
    val protectedContainer: Color,
    val stopAction: Color,
    val stopActionContainer: Color,
    val consoleSurface: Color,
    val consoleText: Color
)

private val DarkSemanticColors = TelemetrySemanticColors(
    rootBadge = CyanPulse,
    rootBadgeContainer = CyanContainerDark,
    nonRootBadge = AmberCaution,
    nonRootBadgeContainer = AmberContainerDark,
    protectedAccent = EmeraldShield,
    protectedContainer = EmeraldContainerDark,
    stopAction = CoralStop,
    stopActionContainer = CoralContainerDark,
    consoleSurface = Color(0xFF070A0E),
    consoleText = Color(0xFFA6F7FF)
)

private val LightSemanticColors = TelemetrySemanticColors(
    rootBadge = CyanPrimaryLight,
    rootBadgeContainer = CyanContainerLight,
    nonRootBadge = AmberLight,
    nonRootBadgeContainer = AmberContainerLight,
    protectedAccent = EmeraldLight,
    protectedContainer = EmeraldContainerLight,
    stopAction = CoralLight,
    stopActionContainer = CoralContainerLight,
    consoleSurface = Color(0xFF0F172A),
    consoleText = Color(0xFF67E8F9)
)

val LocalTelemetryColors = staticCompositionLocalOf { DarkSemanticColors }

private val DarkColorScheme = darkColorScheme(
    primary = CyanPulse,
    onPrimary = CyanPulseDark,
    primaryContainer = CyanContainerDark,
    onPrimaryContainer = CyanOnContainerDark,
    secondary = EmeraldShield,
    onSecondary = EmeraldOnDark,
    secondaryContainer = EmeraldContainerDark,
    onSecondaryContainer = EmeraldOnContainerDark,
    tertiary = AmberCaution,
    onTertiary = AmberOnDark,
    tertiaryContainer = AmberContainerDark,
    onTertiaryContainer = AmberOnContainerDark,
    error = CoralStop,
    onError = CoralOnDark,
    errorContainer = CoralContainerDark,
    onErrorContainer = CoralOnContainerDark,
    background = ObsidianBg,
    onBackground = TextPrimaryDark,
    surface = SlateSurface,
    onSurface = TextPrimaryDark,
    surfaceVariant = SlateSurfaceElevated,
    onSurfaceVariant = TextSecondaryDark,
    surfaceContainerLow = SlateSurface,
    surfaceContainer = SlateSurfaceElevated,
    surfaceContainerHigh = SlateSurfaceHigh,
    outline = SlateOutline,
    outlineVariant = SlateOutlineVariant
)

private val LightColorScheme = lightColorScheme(
    primary = CyanPrimaryLight,
    onPrimary = Color.White,
    primaryContainer = CyanContainerLight,
    onPrimaryContainer = CyanOnContainerLight,
    secondary = EmeraldLight,
    onSecondary = Color.White,
    secondaryContainer = EmeraldContainerLight,
    onSecondaryContainer = EmeraldOnContainerLight,
    tertiary = AmberLight,
    onTertiary = Color.White,
    tertiaryContainer = AmberContainerLight,
    onTertiaryContainer = AmberOnContainerLight,
    error = CoralLight,
    onError = Color.White,
    errorContainer = CoralContainerLight,
    onErrorContainer = CoralOnContainerLight,
    background = IceBgLight,
    onBackground = TextPrimaryLight,
    surface = SurfaceLight,
    onSurface = TextPrimaryLight,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = TextSecondaryLight,
    surfaceContainerLow = SurfaceLight,
    surfaceContainer = SurfaceVariantLight,
    surfaceContainerHigh = Color(0xFFDDE6F0),
    outline = OutlineLight,
    outlineVariant = OutlineVariantLight
)

val TaskPulseShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun TaskPulseTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val semanticColors = if (darkTheme) DarkSemanticColors else LightSemanticColors

    CompositionLocalProvider(LocalTelemetryColors provides semanticColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = TaskPulseTypography,
            shapes = TaskPulseShapes,
            content = content
        )
    }
}
