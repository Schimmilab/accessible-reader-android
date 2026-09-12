package de.schimmilab.accessiblereader

import android.content.ComponentName
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import de.schimmilab.accessiblereader.playback.ReaderPlaybackService
import org.junit.After
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.TimeUnit

/** Headset keys and the media notification reach the service as plain Player calls; they must act on chapters. */
class MediaButtonTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private lateinit var model: ReaderViewModel
    private lateinit var remote: MediaController

    @Before fun connectAndPlayFirstChapter() {
        compose.activityRule.scenario.onActivity { model = ViewModelProvider(it)[ReaderViewModel::class.java] }
        compose.waitUntil(25_000) { model.state.value.connected && model.state.value.voices.isNotEmpty() }
        assumeTrue(model.state.value.voices.isNotEmpty())
        lateinit var future: ListenableFuture<MediaController>
        compose.runOnIdle {
            val context = compose.activity.applicationContext
            future = MediaController.Builder(context, SessionToken(context, ComponentName(context, ReaderPlaybackService::class.java))).buildAsync()
        }
        remote = future.get(10, TimeUnit.SECONDS)
        compose.runOnIdle { model.demo(); model.chapter(0); model.play() }
        // The minimum lead means two parts are synthesized before the first sound; the emulator needs minutes for that.
        compose.waitUntil(240_000) { model.state.value.playing || model.state.value.error != null }
        assertNull(model.state.value.error)
        compose.waitUntil(240_000) { !model.state.value.preparing }
    }

    @After fun release() { compose.runOnIdle { model.pause(); remote.release() } }

    @Test fun thirtySecondButtonsUseTheChapterTimelineAcrossParts() {
        // Demo chapter 0 = spoken intro (part 0, a few seconds) + one text part.
        compose.runOnIdle { model.seek(-1000); model.seek(20) }
        compose.waitUntil(5_000) { model.state.value.positionMs in 19_500L..20_500L }
        compose.runOnIdle { assertTrue(remote.isCommandAvailable(Player.COMMAND_SEEK_BACK)); remote.seekBack() }
        compose.waitUntil(5_000) { model.state.value.positionMs < 500 } // 20 s - 30 s clamps to the chapter start, inside part 0
        compose.runOnIdle { remote.seekForward() }
        compose.waitUntil(5_000) { model.state.value.positionMs in 29_500L..30_500L } // crosses from part 0 into part 1
        assertEquals(0, model.state.value.chapter)
    }

    @Test fun nextAndPreviousActOnChaptersNotParts() {
        compose.runOnIdle {
            assertTrue("Next must stay available on the last part", remote.isCommandAvailable(Player.COMMAND_SEEK_TO_NEXT))
            assertTrue(remote.isCommandAvailable(Player.COMMAND_SEEK_TO_PREVIOUS))
            remote.seekToNext()
        }
        // The minimum lead means the next chapter needs two parts synthesized before it sounds.
        compose.waitUntil(240_000) { model.state.value.chapter == 1 && model.state.value.playing }
        compose.waitUntil(240_000) { !model.state.value.preparing }
        compose.runOnIdle { model.seek(10) }
        compose.waitUntil(5_000) { model.state.value.positionMs > 5_000 }
        compose.runOnIdle { remote.seekToPrevious() } // well into the chapter: back to its start
        compose.waitUntil(5_000) { model.state.value.positionMs < 1_000 && model.state.value.chapter == 1 }
        compose.runOnIdle { model.pause(); remote.seekToPrevious() } // at the start: previous chapter
        compose.waitUntil(10_000) { model.state.value.chapter == 0 }
        assertFalse("Previous while paused must not start playback", model.state.value.playbackRequested)
    }
}
