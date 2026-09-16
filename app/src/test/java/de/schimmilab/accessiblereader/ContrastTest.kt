package de.schimmilab.accessiblereader

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import de.schimmilab.accessiblereader.ui.DarkColors
import de.schimmilab.accessiblereader.ui.LightColors
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.pow

/**
 * The colours of this app, measured instead of trusted.
 *
 * Blind is not one thing. The listener this app was written for has no sight at all, but the same app is used by
 * people who read the screen with very little of it, and by everyone else in sunlight. `docs/TESTING.md` said
 * contrast was unchecked; this checks the pairs the app really draws, in both the light and the dark scheme.
 *
 * What it cannot do is look at the screen. It reads the colours the theme defines, so a text drawn on the wrong
 * background would still pass. The manual walk in `docs/TESTING.md` remains the place where that shows up.
 */
class ContrastTest {
    /** WCAG 2.1 relative luminance, the same formula the accessibility guidelines define. */
    private fun luminance(color: Color): Double {
        fun channel(value: Float): Double {
            val c = value.toDouble()
            return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(color.red) + 0.7152 * channel(color.green) + 0.0722 * channel(color.blue)
    }

    private fun contrast(a: Color, b: Color): Double {
        val one = luminance(a)
        val other = luminance(b)
        return (maxOf(one, other) + 0.05) / (minOf(one, other) + 0.05)
    }

    private fun ColorScheme.textPairs() = listOf(
        "Fließtext auf der Fläche" to (onSurface to surface),
        "Fließtext auf der Karte" to (onSurface to surfaceContainer),
        "Nebentext auf der Fläche" to (onSurfaceVariant to surface),
        "Nebentext auf der Karte" to (onSurfaceVariant to surfaceContainer),
        "Beschriftung auf dem Knopf" to (onPrimary to primary),
        "Hervorhebung auf dem Hintergrund" to (primary to background),
        "Hervorhebung auf der Karte" to (primary to surfaceContainer),
    )

    /**
     * 7 to 1 is the strictest level the guidelines name, and this app has no reason to aim lower: it holds long
     * German prose read by people who chose it because reading is hard.
     */
    @Test fun everyTextIsReadableAtTheStrictestLevel() {
        for ((name, scheme) in listOf("hell" to LightColors, "dunkel" to DarkColors)) {
            for ((what, pair) in scheme.textPairs()) {
                val ratio = contrast(pair.first, pair.second)
                assertTrue("$name: $what only reaches ${"%.2f".format(ratio)} to 1, the guidelines ask for 7",
                    ratio >= 7.0)
            }
        }
    }

    /**
     * The border of an outlined button is the only thing that says where the button is. The guidelines ask 3 to 1
     * for the outline of a control, less than for text because a shape is easier to make out than a letter.
     */
    @Test fun theBorderOfAButtonIsVisible() {
        for ((name, scheme) in listOf("hell" to LightColors, "dunkel" to DarkColors)) {
            for (background in listOf("Hintergrund" to scheme.background, "Karte" to scheme.surfaceContainer)) {
                val ratio = contrast(scheme.outline, background.second)
                assertTrue("$name: die Umrandung auf ${background.first} erreicht nur ${"%.2f".format(ratio)} zu 1",
                    ratio >= 3.0)
            }
        }
    }

    /**
     * Deliberately not asserted: the divider inside the card and the track of the progress bar. Both are
     * decoration in this app — the progress bar is marked as nothing for a screen reader and the same position is
     * written out as text right below it — and holding them to 3 to 1 would darken the card for no gain.
     */
    @Test fun theDecorationIsNamedSoItIsNotMistakenForAnOversight() {
        assertTrue(contrast(LightColors.outlineVariant, LightColors.surfaceContainer) < 3.0)
    }
}
