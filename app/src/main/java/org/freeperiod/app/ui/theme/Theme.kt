package org.freeperiod.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

@Composable
fun FreePeriodTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    accent: Accent = Accent.CORAL,
    content: @Composable () -> Unit,
) {
    val t = daylightTokens(accent, darkTheme)
    val base = if (darkTheme) darkColorScheme() else lightColorScheme()
    val colors = base.copy(
        primary = t.actionText, onPrimary = t.background,
        primaryContainer = t.accent.container, onPrimaryContainer = t.accent.onContainer,
        secondary = t.actionText, onSecondary = t.background,
        secondaryContainer = t.accent.container, onSecondaryContainer = t.accent.onContainer,
        tertiary = t.predicted, onTertiary = t.background,
        tertiaryContainer = t.accent.container, onTertiaryContainer = t.accent.onContainer,
        background = t.background, onBackground = t.ink, surface = t.surface, onSurface = t.ink,
        surfaceVariant = t.accent.container, onSurfaceVariant = t.muted,
        surfaceDim = t.background, surfaceBright = t.surface, surfaceContainerLowest = t.surface,
        surfaceContainerLow = t.surface, surfaceContainer = t.background,
        surfaceContainerHigh = t.accent.container, surfaceContainerHighest = t.accent.container,
        surfaceTint = t.accent.accent, outline = t.control, outlineVariant = t.line,
    )
    CompositionLocalProvider(LocalDaylight provides t) {
        MaterialTheme(colorScheme = colors, typography = FpTypography, shapes = FpMaterialShapes, content = content)
    }
}
