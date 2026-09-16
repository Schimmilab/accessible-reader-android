package de.schimmilab.accessiblereader

import android.content.Context
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import de.schimmilab.accessiblereader.core.SleepOption
import de.schimmilab.accessiblereader.playback.ReaderPlaybackService
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import kotlin.math.abs

/**
 * Setting the sleep timer: what the app writes down for the playback service, which is the part that will still
 * be there once the phone has been put down and the app swiped away.
 *
 * Needs no speech engine, so it runs in CI. What happens when the time is actually up needs one and lives in
 * `SleepTimerFlowTest`.
 */
class SleepTimerSettingTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private lateinit var model: ReaderViewModel
    private val prefs by lazy { compose.activity.getSharedPreferences("reader", Context.MODE_PRIVATE) }

    @After fun tearDown() {
        prefs.edit().remove(ReaderPlaybackService.KEY_SLEEP_UNTIL)
            .putBoolean(ReaderPlaybackService.KEY_SLEEP_AT_SECTION_END, false).commit()
    }

    /** Needs no speech engine: writing down when to stop is not playback. */
    @Test fun theTimerIsWrittenWhereTheServiceCanSeeIt() {
        compose.activityRule.scenario.onActivity { model = ViewModelProvider(it)[ReaderViewModel::class.java] }

        compose.runOnIdle { model.sleepTimer(SleepOption.AFTER_30) }
        val until = prefs.getLong(ReaderPlaybackService.KEY_SLEEP_UNTIL, 0)
        assertTrue("expected roughly half an hour from now, got ${until - System.currentTimeMillis()} ms",
            abs(until - System.currentTimeMillis() - 30 * 60_000L) < 5_000)
        assertEquals(SleepOption.AFTER_30, model.state.value.sleep)
        assertTrue("the listener has to be told what was set", model.state.value.status.startsWith("Einschlaftimer: 30 Minuten"))

        // The end of a section is not a time, so no time may be left lying around for the service to act on.
        compose.runOnIdle { model.sleepTimer(SleepOption.END_OF_SECTION) }
        assertEquals(0L, prefs.getLong(ReaderPlaybackService.KEY_SLEEP_UNTIL, 0))
        assertTrue(prefs.getBoolean(ReaderPlaybackService.KEY_SLEEP_AT_SECTION_END, false))

        compose.runOnIdle { model.sleepTimer(SleepOption.OFF) }
        assertEquals(0L, prefs.getLong(ReaderPlaybackService.KEY_SLEEP_UNTIL, 0))
        assertFalse(prefs.getBoolean(ReaderPlaybackService.KEY_SLEEP_AT_SECTION_END, true))
        assertEquals(SleepOption.OFF, model.state.value.sleep)
    }

    @Test fun theTimerIsReachableByVoice() {
        compose.activityRule.scenario.onActivity { model = ViewModelProvider(it)[ReaderViewModel::class.java] }
        compose.runOnIdle { model.command("Einschlaftimer") }
        assertTrue(model.state.value.showSleep)
        compose.runOnIdle { model.sleepDialog(false) }
    }

}
