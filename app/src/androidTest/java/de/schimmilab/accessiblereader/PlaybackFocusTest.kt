package de.schimmilab.accessiblereader

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Deterministic focus tests, complemented by real TalkBack double-taps on the emulator. */
class PlaybackFocusTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private lateinit var model: ReaderViewModel

    private fun startChapter(index: Int = 0) {
        compose.activityRule.scenario.onActivity {
            model = ViewModelProvider(it)[ReaderViewModel::class.java]
        }
        compose.waitUntil(25_000) { model.state.value.connected && model.state.value.voices.isNotEmpty() }
        compose.runOnIdle { model.demo(); model.chapter(index); model.play() }
        // The minimum lead means two parts are synthesized before the first sound; the emulator needs minutes for that.
        compose.waitUntil(240_000) { model.state.value.playing || model.state.value.error != null }
        assertTrue(model.state.value.error.orEmpty(), model.state.value.playing)
        compose.runOnIdle { model.seek(-1000) }
        compose.waitUntil(5_000) { model.state.value.positionMs < 1000 && model.state.value.playing }
    }

    private fun withFocus(gain: Int, block: (AudioManager, AudioFocusRequest) -> Unit) {
        val manager = compose.activity.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val request = AudioFocusRequest.Builder(gain)
            .setAudioAttributes(AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            .setOnAudioFocusChangeListener { }.build()
        try {
            compose.runOnIdle { assertEquals(AudioManager.AUDIOFOCUS_REQUEST_GRANTED, manager.requestAudioFocus(request)) }
            compose.waitUntil(5_000) { !model.state.value.playing }
            block(manager, request)
        } finally {
            compose.runOnIdle { manager.abandonAudioFocusRequest(request); model.pause() }
        }
    }

    @Test fun temporarySpeechKeepsPauseButtonAndResumesWithoutAnotherTap() {
        startChapter()
        withFocus(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK) { manager, request ->
            assertTrue("A spoken interruption is not a user pause", model.state.value.playbackRequested)
            compose.onNodeWithText("Pause").assertIsDisplayed()
            compose.onNodeWithText("Vorlesen").assertDoesNotExist()
            val position = model.state.value.positionMs
            compose.runOnIdle { manager.abandonAudioFocusRequest(request) }
            compose.waitUntil(5_000) { model.state.value.playing && model.state.value.positionMs > position + 750 }
            compose.onNodeWithText("Pause").assertIsDisplayed()
        }
    }

    @Test fun pauseDuringSpeechStaysPausedAfterFocusReturns() {
        startChapter()
        withFocus(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK) { manager, request ->
            compose.onNodeWithText("Pause").performClick()
            compose.waitUntil(5_000) { !model.state.value.playbackRequested }
            compose.runOnIdle { manager.abandonAudioFocusRequest(request) }
            Thread.sleep(1500)
            compose.runOnIdle {
                assertFalse(model.state.value.playing)
                assertFalse(model.state.value.playbackRequested)
            }
            compose.onNodeWithText("Vorlesen").assertIsDisplayed()
        }
    }

    @Test fun permanentFocusLossDoesNotAutomaticallyRestart() {
        startChapter()
        withFocus(AudioManager.AUDIOFOCUS_GAIN) { manager, request ->
            compose.waitUntil(5_000) { !model.state.value.playbackRequested }
            compose.onNodeWithText("Vorlesen").assertIsDisplayed()
            compose.runOnIdle { manager.abandonAudioFocusRequest(request) }
            Thread.sleep(1500)
            compose.runOnIdle { assertFalse(model.state.value.playing); assertFalse(model.state.value.playbackRequested) }
        }
    }

    @Test fun lastChapterEndOffersRestart() {
        val last = lastChapter()
        startChapter(last)
        try {
            // Running out of parts during preparation is deliberately not a chapter end.
            compose.waitUntil(240_000) { !model.state.value.preparing }
            compose.runOnIdle { model.seek(1000) }
            compose.waitUntil(5_000) { !model.state.value.playbackRequested && !model.state.value.playing }
            assertEquals(last, model.state.value.chapter)
            compose.onNodeWithText("Vorlesen").assertIsDisplayed()
            // Through the model: a UI click on a fresh install first opens the notification permission dialog.
            compose.runOnIdle { model.togglePlayback() }
            compose.waitUntil(5_000) { model.state.value.playing && model.state.value.positionMs < 3000 }
        } finally { compose.runOnIdle { model.pause() } }
    }

    @Test fun chapterEndContinuesWithNextChapterWithoutStatusAnnouncement() {
        startChapter(0)
        try {
            compose.waitUntil(240_000) { !model.state.value.preparing }
            compose.runOnIdle { model.seek(1000) }
            val status = model.state.value.status // the seek itself announces; nothing after it may
            compose.waitUntil(120_000) { model.state.value.chapter == 1 && model.state.value.playing }
            assertTrue(model.state.value.playbackRequested)
            assertEquals("Auto-advance must not trigger a TalkBack live-region announcement", status, model.state.value.status)
            compose.onNodeWithText("Pause").assertIsDisplayed()
        } finally { compose.runOnIdle { model.pause() } }
    }

    private fun lastChapter(): Int = de.schimmilab.accessiblereader.core.demoDocument().chapters.lastIndex
}
