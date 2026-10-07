package org.freeperiod.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/** Warm coral seed (spec: calm & minimal, one warm accent). */
val Coral = Color(0xFFE8735A)

private val LightColors = lightColorScheme(
    primary = Color(0xFFA23F2A),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDAD2),
    onPrimaryContainer = Color(0xFF3D0700),
    secondary = Color(0xFF77574F),
    secondaryContainer = Color(0xFFFFDAD2),
    background = Color(0xFFFFFBFF),
    surface = Color(0xFFFFFBFF),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFFB4A3),
    onPrimary = Color(0xFF611301),
    primaryContainer = Color(0xFF822915),
    onPrimaryContainer = Color(0xFFFFDAD2),
    secondary = Color(0xFFE7BDB3),
    secondaryContainer = Color(0xFF5D3F38),
    background = Color(0xFF201A19),
    surface = Color(0xFF201A19),
)

@Composable
fun FreePeriodTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colors = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && dynamicColor) {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else if (darkTheme) DarkColors else LightColors
    MaterialTheme(colorScheme = colors, content = content)
}
