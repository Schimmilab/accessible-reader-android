package de.schimmilab.accessiblereader

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

class ReaderUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private lateinit var model: ReaderViewModel

    @Test fun contentsChangesChapterAndExposesNamedControls() {
        compose.onNodeWithText("Leseprobe").performClick()
        compose.onNodeWithText("Vorlesen").assertHasClickAction()
        compose.onNodeWithText("30 Sekunden\nzurück").assertHasClickAction()
        compose.onNodeWithText("30 Sekunden\nvor").assertHasClickAction()
        compose.onNodeWithText("Inhaltsverzeichnis").performScrollTo().performClick()
        compose.onNodeWithText("2. Unterwegs").performClick()
        compose.onNodeWithText("Unterwegs").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("ABSCHNITT 2 VON 3").assertExists()
    }

    @Test fun playsRealAudioAndSeeksThirtySeconds() {
        lateinit var model: ReaderViewModel
        compose.activityRule.scenario.onActivity { activity ->
            model = ViewModelProvider(activity)[ReaderViewModel::class.java]
            model.demo()
        }
        compose.waitUntil(25_000) { model.state.value.connected && model.state.value.voices.isNotEmpty() }
        assumeTrue(model.state.value.voices.isNotEmpty())
        compose.runOnIdle { model.chapter(0); model.play() }
        compose.waitUntil(120_000) { model.state.value.playing || model.state.value.error != null }
        assertTrue(model.state.value.error.orEmpty(), model.state.value.playing)
        compose.waitUntil(240_000) { !model.state.value.preparing } // a 30 s jump needs the whole chapter prepared
        compose.runOnIdle { model.pause(); model.seek(-1000) }
        compose.waitUntil(5_000) { model.state.value.positionMs < 500 && !model.state.value.playing }
        compose.runOnIdle { model.seek(30) }
        compose.waitUntil(5_000) {
            android.util.Log.i("ReaderSeekTest", "position=${model.state.value.positionMs}, duration=${model.state.value.durationMs}, busy=${model.state.value.busy}, status=${model.state.value.status}")
            model.state.value.positionMs in 29_500L..30_500L
        }
        assertTrue(model.state.value.durationMs > 30_000)
        compose.activityRule.scenario.recreate()
        compose.runOnIdle { model.seek(-30) }
        compose.waitUntil(5_000) { model.state.value.positionMs < 500 }
    }

    /**
     * Setting the reading speed has to be usable through a screen reader: small enough steps to aim at, and the
     * new value said out loud, because the reader keeps its focus on the button and never reads what changed.
     */
    @Test fun theSpeedCanBeAimedAtAndIsSpoken() {
        compose.activityRule.scenario.onActivity { model = ViewModelProvider(it)[ReaderViewModel::class.java] }
        compose.waitUntil(30_000) { model.state.value.connected }
        compose.runOnIdle { model.speed(1f) }

        // The controls live in a lazy list, so the list has to be scrolled, not the node.
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Schneller"))
        compose.onNodeWithText("Schneller").performClick()
        compose.waitForIdle()
        assertEquals("One press has to be a tenth, not a quarter", 1.1f, model.state.value.speed, 0.001f)
        assertEquals("Geschwindigkeit 1,1 fach.", model.state.value.status)
        compose.onNodeWithText("1,1×").assertExists()

        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Langsamer"))
        compose.onNodeWithText("Langsamer").performClick()
        compose.waitForIdle()
        assertEquals(1f, model.state.value.speed, 0.001f)
        assertEquals("Geschwindigkeit normal.", model.state.value.status)
    }
}
