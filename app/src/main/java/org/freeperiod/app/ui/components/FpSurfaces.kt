package org.freeperiod.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.freeperiod.app.ui.theme.*

@Composable
fun FpCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val t = LocalDaylight.current
    Surface(modifier, shape = FpShapes.card, color = t.surface, contentColor = t.ink,
        border = BorderStroke(1.dp, t.line)) { Column(content = content) }
}

@Composable
fun FpPanel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val a = LocalDaylight.current.accent
    Surface(modifier, shape = FpShapes.card, color = a.accent, contentColor = a.onAccent) {
        Column(content = content)
    }
}

@Composable
fun FpButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    primary: Boolean = true,
    border: BorderStroke? = BorderStroke(1.dp, if (primary) LocalDaylight.current.accent.periodBorder else LocalDaylight.current.control),
    colors: ButtonColors? = null, content: @Composable RowScope.() -> Unit) {
    val t = LocalDaylight.current
    Button(onClick, modifier.heightIn(min = FpSpacing.touch), enabled = enabled, shape = FpShapes.button,
        border = border,
        colors = colors ?: ButtonDefaults.buttonColors(containerColor = if (primary) t.accent.accent else t.surface,
            contentColor = if (primary) t.accent.onAccent else t.ink), content = content)
}
