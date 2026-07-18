package com.harding.feeds.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.harding.feeds.R

/**
 * Fraunces (bundled variable font) is the app's voice: a soft optical serif that makes the
 * hero clock read as a bedside instrument rather than a system readout. One variable file
 * drives every weight via the wght axis (minSdk 26 supports FontVariation); Compose maps the
 * opsz axis to the text size automatically. Body and controls stay on the platform sans so
 * running text and small labels keep their machine legibility.
 */
@OptIn(ExperimentalTextApi::class)
private fun frauncesWeight(weight: FontWeight): Font = Font(
    resId = R.font.fraunces,
    weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
)

val Fraunces: FontFamily = FontFamily(
    frauncesWeight(FontWeight.Normal),
    frauncesWeight(FontWeight.Medium),
    frauncesWeight(FontWeight.SemiBold),
)

/**
 * Material's baseline scale, with the display and headline roles reset to Fraunces. Titles,
 * body and labels keep the default sans deliberately - the serif is a highlight, not the whole
 * page.
 */
val AppTypography: Typography = Typography().run {
    copy(
        displayLarge = displayLarge.copy(fontFamily = Fraunces, fontWeight = FontWeight.Normal),
        displayMedium = displayMedium.copy(fontFamily = Fraunces, fontWeight = FontWeight.Normal),
        displaySmall = displaySmall.copy(fontFamily = Fraunces, fontWeight = FontWeight.Normal),
        headlineLarge = headlineLarge.copy(fontFamily = Fraunces, fontWeight = FontWeight.Normal),
        headlineMedium = headlineMedium.copy(fontFamily = Fraunces, fontWeight = FontWeight.Medium),
        headlineSmall = headlineSmall.copy(fontFamily = Fraunces, fontWeight = FontWeight.Medium),
    )
}
