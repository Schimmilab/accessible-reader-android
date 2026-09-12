package de.schimmilab.accessiblereader

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

class ReaderUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

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
}
