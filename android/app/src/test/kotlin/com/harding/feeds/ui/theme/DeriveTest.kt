package com.harding.feeds.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The theme editor lets a parent pick six colours. It does not let them pick the text colour,
 * and this is the reason: every foreground the app paints is measured, not chosen.
 *
 * These tests are the guard on that promise. A palette that passes them cannot hide its own
 * words, whichever six colours produced it.
 */
class DeriveTest {

    /** WCAG AA for body text. Below this, text on that ground is not reliably readable. */
    private val readable = 4.5f

    @Test
    fun `every preset can read its own main text`() {
        Presets.all.forEach { palette ->
            val ratio = contrastRatio(palette.background, palette.foreground)
            assertTrue(
                "${palette.name} main text is ${ratio.round()}:1",
                ratio >= readable,
            )
        }
    }

    @Test
    fun `every preset can read its own dim text`() {
        Presets.all.forEach { palette ->
            val ratio = contrastRatio(palette.background, palette.dimForeground)
            assertTrue(
                "${palette.name} dim text is ${ratio.round()}:1",
                ratio >= readable,
            )
        }
    }

    /**
     * A glyph sits on top of every accent: the side letter, the bottle, the moon, the pill
     * label. The old code used one fixed near-black for all of them, which held only because
     * every accent it shipped was light.
     */
    @Test
    fun `every accent of every preset can carry a glyph`() {
        Presets.all.forEach { palette ->
            val grounds = palette.eventAccents + palette.accent + palette.stop
            grounds.forEach { ground ->
                val ratio = contrastRatio(ground, onColorFor(ground, palette))
                assertTrue(
                    "${palette.name} accent ${ground.toHex()} carries ${ratio.round()}:1",
                    ratio >= readable,
                )
            }
        }
    }

    @Test
    fun `a dark theme takes the pale tone and a light theme takes the ink tone`() {
        Presets.all.forEach { palette ->
            val expected = if (palette.isDark) palette.paleTone else palette.inkTone
            assertEquals(palette.name, expected.toHex(), palette.foreground.toHex())
        }
    }

    /**
     * The worst ground a parent can pick is one that sits between the two tones. The rule must
     * still return the better of the two, not simply fail.
     */
    @Test
    fun `a mid-grey ground still gets the higher-contrast tone`() {
        val grey = Color(0.5f, 0.5f, 0.5f)
        val palette = Presets.Candlelight.copy(background = grey)

        val chosen = onColorFor(grey, palette)
        val rejected = if (chosen == palette.inkTone) palette.paleTone else palette.inkTone

        assertTrue(contrastRatio(grey, chosen) >= contrastRatio(grey, rejected))
    }

    @Test
    fun `a card is raised toward the text on a dark theme and lowered on a light one`() {
        val dark = Presets.Candlelight
        val light = Presets.Paper

        assertTrue(
            "dark card must be lighter than its ground",
            dark.surfaceAt(0.05f).toHsv().value > dark.background.toHsv().value,
        )
        assertTrue(
            "light card must be darker than its ground",
            light.surfaceAt(0.05f).toHsv().value < light.background.toHsv().value,
        )
    }

    @Test
    fun `hsv survives a round trip through rgb`() {
        Presets.all.flatMap { it.eventAccents + it.background }.forEach { original ->
            val hsv = original.toHsv()
            val rebuilt = hsvColor(hsv.hue, hsv.saturation, hsv.value)
            assertEquals(original.toHex(), rebuilt.toHex())
        }
    }

    @Test
    fun `contrast runs from one to twenty-one`() {
        val black = Color(0f, 0f, 0f)
        val white = Color(1f, 1f, 1f)

        assertEquals(1.0f, contrastRatio(black, black), 0.001f)
        assertEquals(21.0f, contrastRatio(black, white), 0.05f)
    }

    private fun Float.round(): String = "%.1f".format(this)
}
