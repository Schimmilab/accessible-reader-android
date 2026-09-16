package de.schimmilab.accessiblereader

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import android.util.Log
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*

/**
 * The rules this project wrote down after learning them the hard way, checked against the real screen instead of
 * being trusted. The documentation claimed for weeks that an accessibility framework was running here. None was.
 *
 * Every rule below is one a screen reader user actually runs into:
 * - a control with no name is announced as "Schaltfläche" and nothing else
 * - a control under 48 by 48 density pixels is hard to hit without seeing it
 * - two controls with the same name cannot be told apart by ear, which cost this project a release once
 */
class AccessibilityRulesTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    /** The label a screen reader would read: the description if there is one, otherwise the text. */
    private fun SemanticsNodeInteraction.label(): String {
        val node = fetchSemanticsNode()
        val described = node.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(" ")
        val text = node.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ")
        return (described ?: text).orEmpty().trim()
    }

    private fun checkScreen(where: String) {
        val controls = compose.onAllNodes(hasClickAction() and isEnabled())
        val count = controls.fetchSemanticsNodes().size
        assertTrue("$where has no controls at all", count > 0)

        val names = mutableListOf<String>()
        val nameless = mutableListOf<String>()
        val small = mutableListOf<String>()
        for (index in 0 until count) {
            val control = controls[index]
            val name = control.label()
            val node = control.fetchSemanticsNode()
            if (name.isBlank()) nameless += "Element $index bei ${node.positionInRoot}" else names += name
            // Only controls the listener can actually reach are measured; a node scrolled out of view reports
            // no size of its own.
            val width = with(compose.density) { node.size.width.toDp() }
            val height = with(compose.density) { node.size.height.toDp() }
            if (name.isNotBlank() && node.size.width > 0 && (width < 48.dp || height < 48.dp)) {
                small += "$name ist ${width.value.toInt()} mal ${height.value.toInt()} dp"
            }
        }
        Log.i("ReaderA11y", "$where: $count Bedienelemente, Namen: ${names.joinToString(" · ")}")

        assertTrue("$where: controls a screen reader cannot name: $nameless", nameless.isEmpty())
        assertTrue("$where: controls too small to hit without seeing them: $small", small.isEmpty())
        val duplicates = names.groupBy { it }.filterValues { it.size > 1 }.keys
        assertTrue("$where: two controls carry the same name, which cannot be told apart by ear: $duplicates",
            duplicates.isEmpty())
    }

    @Test fun theMainScreenFollowsTheRules() {
        compose.onNodeWithText("Leseprobe").performClick()
        compose.waitForIdle()
        checkScreen("Hauptbildschirm")
    }

    @Test fun theContentsFollowTheRules() {
        compose.onNodeWithText("Leseprobe").performClick()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Inhaltsverzeichnis"))
        compose.onNodeWithText("Inhaltsverzeichnis").performClick()
        compose.waitForIdle()
        checkScreen("Inhaltsverzeichnis")
    }

    @Test fun theSettingsFollowTheRules() {
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Stimme & Tempo"))
        compose.onNodeWithText("Stimme und Einstellungen").performClick()
        compose.waitForIdle()
        checkScreen("Einstellungen")
    }

    /** A screen reader moves by heading. A screen without them can only be walked through one control at a time. */
    @Test fun everyScreenOffersHeadingsToJumpBetween() {
        compose.onNodeWithText("Leseprobe").performClick()
        compose.waitForIdle()
        val headings = compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
            .fetchSemanticsNodes().size
        Log.i("ReaderA11y", "Überschriften auf dem Hauptbildschirm: $headings")
        assertTrue("A screen reader needs headings to move by, found $headings", headings >= 3)
    }
}
