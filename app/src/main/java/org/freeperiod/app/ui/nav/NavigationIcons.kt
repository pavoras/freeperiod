package org.freeperiod.app.ui.nav

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

object NavigationIcons {
    val Today = icon("Today") {
        moveTo(4f, 5f); lineTo(20f, 5f); lineTo(20f, 21f); lineTo(4f, 21f); close()
        moveTo(4f, 10f); lineTo(20f, 10f)
        moveTo(8f, 2f); lineTo(8f, 7f)
        moveTo(16f, 2f); lineTo(16f, 7f)
        moveTo(8f, 15f); lineTo(16f, 15f)
    }
    val History = icon("History") {
        moveTo(5f, 6f); lineTo(19f, 6f)
        moveTo(5f, 12f); lineTo(19f, 12f)
        moveTo(5f, 18f); lineTo(15f, 18f)
    }
    val Settings = icon("Settings") {
        moveTo(4f, 7f); lineTo(20f, 7f)
        moveTo(4f, 17f); lineTo(20f, 17f)
        moveTo(9f, 4f); lineTo(9f, 10f)
        moveTo(15f, 14f); lineTo(15f, 20f)
    }
    val Previous = icon("Previous") { moveTo(15f, 6f); lineTo(9f, 12f); lineTo(15f, 18f) }
    val Next = icon("Next") { moveTo(9f, 6f); lineTo(15f, 12f); lineTo(9f, 18f) }

    private fun icon(name: String, path: androidx.compose.ui.graphics.vector.PathBuilder.() -> Unit) =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            path(fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f, pathBuilder = path)
        }.build()
}
