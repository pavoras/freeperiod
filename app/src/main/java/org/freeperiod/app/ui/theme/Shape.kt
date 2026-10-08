package org.freeperiod.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

object FpShapes {
    val card = RoundedCornerShape(topStart = 4.dp, topEnd = 24.dp, bottomEnd = 4.dp, bottomStart = 24.dp)
    val button = RoundedCornerShape(topStart = 4.dp, topEnd = 14.dp, bottomEnd = 4.dp, bottomStart = 14.dp)
    val selectedChip = RoundedCornerShape(topStart = 5.dp, topEnd = 12.dp, bottomEnd = 5.dp, bottomStart = 12.dp)
    val chip = RoundedCornerShape(5.dp)
    val nav = RoundedCornerShape(topStart = 4.dp, topEnd = 12.dp, bottomEnd = 4.dp, bottomStart = 12.dp)
    val bar = RoundedCornerShape(2.dp)
}

val FpMaterialShapes = Shapes(extraSmall = FpShapes.chip, small = FpShapes.button,
    medium = FpShapes.button, large = FpShapes.card, extraLarge = FpShapes.card)
