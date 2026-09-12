package de.schimmilab.accessiblereader

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/**
 * The helper on the target device reported that no report could be created. End to end: the diagnosis has to
 * finish, produce a readable report, and must not lock the screen someone is standing in front of.
 */
class DiagnosticsTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private lateinit var model: ReaderViewModel

    /**
     * The helper could not find the button after the engine list was added above it, and reported that no report
     * could be created. It has to be on screen the moment the settings open, without scrolling inside the dialog.
     */
    @Test fun theDiagnosisButtonIsOnScreenAsSoonAsTheSettingsOpen() {
        compose.activityRule.scenario.onActivity { model = ViewModelProvider(it)[ReaderViewModel::class.java] }
        compose.waitUntil(25_000) { model.state.value.connected }
        compose.runOnIdle { model.settings(true) }
        compose.onNodeWithText("Sprachausgabe prüfen").assertIsDisplayed()
    }

    @Test fun theDiagnosisFinishesAndLeavesTheScreenUsable() {
        compose.activityRule.scenario.onActivity { model = ViewModelProvider(it)[ReaderViewModel::class.java] }
        compose.waitUntil(25_000) { model.state.value.connected }
        compose.runOnIdle { model.runDiagnostics() }
        compose.waitUntil(10_000) { model.state.value.diagnosing || model.state.value.report.isNotBlank() }
        assertFalse("A running diagnosis must not disable the rest of the screen", model.state.value.busy)

        compose.waitUntil(180_000) { !model.state.value.diagnosing }
        assertNull(model.state.value.error)
        val report = model.state.value.report
        assertTrue("A report has to be produced, was: '$report'", report.startsWith("Bericht zur Sprachausgabe"))
        assertTrue("Every installed engine has to appear", report.contains("Sprachmaschine 1:"))
        assertTrue("The decisive line has to be there", report.contains("Audio in eine Datei schreiben:"))
    }

    @Test fun askingTwiceWhileRunningDoesNotStartASecondDiagnosis() {
        compose.activityRule.scenario.onActivity { model = ViewModelProvider(it)[ReaderViewModel::class.java] }
        compose.waitUntil(25_000) { model.state.value.connected }
        compose.runOnIdle { model.runDiagnostics(); model.runDiagnostics(); model.runDiagnostics() }
        compose.waitUntil(180_000) { !model.state.value.diagnosing && model.state.value.report.isNotBlank() }
        assertNull(model.state.value.error)
        assertEquals("The report must list each engine once", 1, Regex("Sprachmaschine 1:").findAll(model.state.value.report).count())
    }
}
