package com.harding.feeds.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import kotlin.math.abs
import kotlin.math.roundToInt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Two promises about the preset range.
 *
 * The first is to the family who already use the app: Candlelight is what they have now, and an
 * upgrade must not repaint their screen. The second is to every parent: within one theme the
 * four event accents stay far enough apart that a nap row never reads as a side.
 */
class PaletteTest {

    /**
     * The exact colours the app shipped before the theme screen existed. Candlelight must hold
     * every one of them.
     */
    @Test
    fun `Candlelight holds the colours the app shipped with`() {
        val shipped = Presets.Candlelight

        assertEquals(Color(0xFF14100E), shipped.background)
        assertEquals(Color(0xFFE6A45C), shipped.accent)
        assertEquals(Color(0xFF82AED2), shipped.left)
        assertEquals(Color(0xFFE6A45C), shipped.right)
        assertEquals(Color(0xFF9CBF8E), shipped.bottle)
        assertEquals(Color(0xFFBFA8D9), shipped.nap)
        assertEquals(Color(0xFFCF7367), shipped.stop)
    }

    /**
     * The derived scheme replaces a hand-tuned one. The value steps in Derive.kt are calibrated
     * against exactly these numbers, so a change to them that repaints the app fails here.
     *
     * The drift is per channel, out of 255, and it is not zero. The old scheme let its warmth
     * drift slot by slot, and one rule cannot follow a hand's drift exactly.
     *
     * Measured across the 15 slots the app reads: 7 land exactly, 6 drift by 2, `outline` drifts
     * by 3, and the two text slots drift by 5. The 5 is all in the green channel. The old
     * foreground sat at hue 33 degrees while its own ground sat at 20, and the rule takes the
     * ground's hue.
     *
     * The test reports every slot rather than stopping at the first, so one run shows the whole
     * picture instead of one failure at a time.
     */
    @Test
    fun `the derived Candlelight scheme reproduces the hand-tuned one`() {
        val scheme = schemeFor(Presets.Candlelight)

        val drifts = handTunedCandlelight.map { (slot, expected) ->
            slot to scheme.driftFrom(slot, expected)
        }
        val tooFar = drifts.filter { (_, drift) -> drift > MAX_DRIFT }

        assertTrue(
            tooFar.joinToString(prefix = "slots past $MAX_DRIFT/255: ") { (slot, drift) ->
                "$slot ${drift.roundToInt()}"
            },
            tooFar.isEmpty(),
        )
    }

    /**
     * The theme editor warns a parent when two event accents would read as one. A preset must
     * never break the advice the editor gives, so both use `looksAlike`.
     */
    @Test
    fun `no preset puts two event accents too close to tell apart`() {
        Presets.all.forEach { palette ->
            val accents = palette.eventAccents
            for (first in accents.indices) {
                for (second in first + 1 until accents.size) {
                    val one = accents[first]
                    val other = accents[second]
                    assertTrue(
                        "${palette.name}: ${one.toHex()} and ${other.toHex()} are only " +
                            "${hueGap(one, other).roundToInt()} degrees apart",
                        !looksAlike(one, other),
                    )
                }
            }
        }
    }

    /**
     * The one thing that does not vary across themes. A parent who changes theme should not have
     * to start reading the letter on the chip, so L stays on the cool half of the wheel and R on
     * the warm half in every preset.
     *
     * Ember is the exception, and for a stated reason: the cool half is the band that palette
     * exists to avoid, so its L takes a dusty rose instead.
     */
    @Test
    fun `L is cool and R is warm in every preset but Ember`() {
        Presets.all.filter { it != Presets.Ember }.forEach { palette ->
            val left = palette.left.toHsv().hue
            val right = palette.right.toHsv().hue
            assertTrue(
                "${palette.name} L is at ${left.roundToInt()} degrees, off the cool half",
                left in COOL,
            )
            assertTrue(
                "${palette.name} R is at ${right.roundToInt()} degrees, off the warm half",
                right >= WARM_FROM || right <= WARM_TO,
            )
        }
    }

    /**
     * The variation the range exists for. Nine backgrounds carrying one palette is not a range,
     * and that is what the first version of these presets was.
     *
     * One pair is exempt and named. Daybreak is Candlelight's light twin on purpose: the app
     * ships in Candlelight, so the likeliest want from a light theme is the same app, readable
     * in daylight. Every other pair must differ.
     */
    @Test
    fun `no two presets share an accent set, except the one deliberate twin`() {
        val twin = setOf(Presets.Candlelight.id, Presets.Daybreak.id)

        for (first in Presets.all.indices) {
            for (second in first + 1 until Presets.all.size) {
                val one = Presets.all[first]
                val other = Presets.all[second]
                if (setOf(one.id, other.id) == twin) continue

                val sameHues = one.eventAccents.zip(other.eventAccents)
                    .all { (a, b) -> hueGap(a, b) < SAME_HUE }
                assertTrue(
                    "${one.name} and ${other.name} carry the same four hues",
                    !sameHues,
                )
            }
        }
    }

    @Test
    fun `the range holds six dark themes and three light ones`() {
        assertEquals(6, Presets.all.count { it.isDark })
        assertEquals(3, Presets.all.count { !it.isDark })
    }

    @Test
    fun `Candlelight is the default and comes first`() {
        assertEquals(Presets.Candlelight, Presets.default)
        assertEquals(Presets.Candlelight, Presets.all.first())
    }

    @Test
    fun `every preset id is unique and findable`() {
        val ids = Presets.all.map { it.id }

        assertEquals(ids.size, ids.toSet().size)
        ids.forEach { assertEquals(it, Presets.byId(it)?.id) }
        assertEquals(null, Presets.byId("no-such-theme"))
    }

    /** The largest per-channel difference between one derived slot and its hand-tuned value. */
    private fun ColorScheme.driftFrom(slot: String, expected: Long): Float {
        val actual = when (slot) {
            "primary" -> primary
            "background" -> background
            "surface" -> surface
            "onSurface" -> onSurface
            "onBackground" -> onBackground
            "surfaceVariant" -> surfaceVariant
            "onSurfaceVariant" -> onSurfaceVariant
            "surfaceContainerLowest" -> surfaceContainerLowest
            "surfaceContainerLow" -> surfaceContainerLow
            "surfaceContainer" -> surfaceContainer
            "surfaceContainerHigh" -> surfaceContainerHigh
            "surfaceContainerHighest" -> surfaceContainerHighest
            "outline" -> outline
            "outlineVariant" -> outlineVariant
            "error" -> error
            else -> throw IllegalArgumentException("unknown slot $slot")
        }
        val wanted = Color(expected)
        return maxOf(
            abs(actual.red - wanted.red),
            abs(actual.green - wanted.green),
            abs(actual.blue - wanted.blue),
        ) * 255f
    }

    private companion object {

        /** Measured, not guessed. See the note on the reproduction test. */
        const val MAX_DRIFT = 5f

        /** Two hues closer than this are the same colour for the purpose of comparing sets. */
        const val SAME_HUE = 12f

        /** The cool half of the wheel, where every L sits. */
        val COOL = 150f..280f

        /** And the warm half, which wraps past 360, where every R sits. */
        const val WARM_FROM = 300f
        const val WARM_TO = 60f

        /** Every slot of the hand-tuned scheme that the app reads. */
        val handTunedCandlelight = listOf(
            "primary" to 0xFFE6A45C,
            "background" to 0xFF14100E,
            "surface" to 0xFF14100E,
            "onSurface" to 0xFFF3E9DD,
            "onBackground" to 0xFFF3E9DD,
            "surfaceVariant" to 0xFF251D18,
            "onSurfaceVariant" to 0xFFA89384,
            "surfaceContainerLowest" to 0xFF100C0A,
            "surfaceContainerLow" to 0xFF1C1613,
            "surfaceContainer" to 0xFF211A16,
            "surfaceContainerHigh" to 0xFF2A211B,
            "surfaceContainerHighest" to 0xFF302620,
            "outline" to 0xFF3A2E26,
            "outlineVariant" to 0xFF2A211B,
            "error" to 0xFFCF7367,
        )
    }
}
