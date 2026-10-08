package org.freeperiod.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import org.freeperiod.app.R

@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
private fun variableFamily(resource: Int, display: Boolean) = FontFamily(
    listOf(400, 500, 600, 700).map { weight ->
        Font(resource, weight = FontWeight(weight), variationSettings = FontVariation.Settings(
            *if (display) arrayOf(FontVariation.weight(weight), FontVariation.Setting("opsz", 24f), FontVariation.Setting("wdth", 100f))
            else arrayOf(FontVariation.weight(weight), FontVariation.Setting("opsz", 14f))))
    }
)

val Bricolage = variableFamily(R.font.bricolage_grotesque, true)
val DmSans = variableFamily(R.font.dm_sans, false)
private fun heading(size: Int, height: Int, weight: FontWeight = FontWeight.SemiBold) =
    TextStyle(fontFamily = Bricolage, fontWeight = weight, fontSize = size.sp, lineHeight = height.sp, letterSpacing = (-0.4).sp,
        hyphens = Hyphens.Auto, lineBreak = LineBreak.Heading)
private fun body(size: Int, height: Int, weight: FontWeight = FontWeight.Normal) =
    TextStyle(fontFamily = DmSans, fontWeight = weight, fontSize = size.sp, lineHeight = height.sp,
        hyphens = Hyphens.Auto, lineBreak = LineBreak.Paragraph)

val FpTypography = Typography(
    displayLarge = heading(72, 76), displayMedium = heading(40, 46), displaySmall = heading(38, 44),
    headlineLarge = heading(34, 40), headlineMedium = heading(30, 36), headlineSmall = heading(25, 32),
    titleLarge = heading(20, 26), titleMedium = heading(15, 20), titleSmall = heading(14, 20),
    bodyLarge = body(15, 22), bodyMedium = body(14, 20), bodySmall = body(12, 18),
    labelLarge = body(14, 20, FontWeight.Medium), labelMedium = body(12, 16, FontWeight.Medium),
    labelSmall = body(11, 15, FontWeight.Medium),
)
