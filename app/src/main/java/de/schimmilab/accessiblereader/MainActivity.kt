package de.schimmilab.accessiblereader

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.schimmilab.accessiblereader.ui.ReaderScreen

class MainActivity : ComponentActivity() {
    private val model: ReaderViewModel by viewModels()
    private var recognizer: SpeechRecognizer? = null
    private val filePicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(model::importDocument) }
    private val microphone = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) listen() else model.showError("Ohne Mikrofonfreigabe kannst du alle Funktionen über die beschrifteten Tasten bedienen.")
    }
    private val notifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) { model.togglePlayback() }
    private val voiceSettings = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { model.refreshVoices() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ReaderScreen(model.state.collectAsStateWithLifecycle().value, model,
                onOpen = { filePicker.launch(arrayOf("application/pdf", "application/epub+zip")) }, onPlay = ::play,
                onListen = ::requestSpeech, onVoiceSettings = {
                    runCatching { voiceSettings.launch(Intent("com.android.settings.TTS_SETTINGS")) }
                        .onFailure { voiceSettings.launch(Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA)) }
                }, onShareReport = ::shareReport)
        }
        if (savedInstanceState == null) importIntent(intent)
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); importIntent(intent) }
    @Suppress("DEPRECATION")
    private fun importIntent(intent: Intent) {
        val uri = when (intent.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                else intent.getParcelableExtra(Intent.EXTRA_STREAM)
            else -> null
        }
        uri?.let(model::importDocument)
    }
    /** The report describes the device's speech engine only. It contains nothing from the user's documents. */
    private fun shareReport(text: String) {
        runCatching {
            startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "Accessible Reader: Bericht zur Sprachausgabe")
                putExtra(Intent.EXTRA_TEXT, text)
            }, "Bericht teilen"))
        }.onFailure { model.showError("Es wurde keine App zum Teilen gefunden.") }
    }

    private fun play() {
        if (!model.state.value.playbackRequested && Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
            !getPreferences(MODE_PRIVATE).getBoolean("notificationAsked", false)) {
            getPreferences(MODE_PRIVATE).edit().putBoolean("notificationAsked", true).apply()
            notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else model.togglePlayback()
    }
    private fun requestSpeech() {
        if (model.state.value.listening) { recognizer?.cancel(); model.listening(false); return }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
            microphone.launch(Manifest.permission.RECORD_AUDIO)
        else listen()
    }
    private fun listen() {
        if (Build.VERSION.SDK_INT < 31 || !SpeechRecognizer.isOnDeviceRecognitionAvailable(this)) {
            model.showError("Lokale Spracherkennung ist auf diesem Gerät noch nicht verfügbar. Bitte deutsche Offline-Sprachdaten installieren. Alle Tasten funktionieren auch mit TalkBack.")
            return
        }
        recognizer?.destroy()
        recognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(this).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) { model.listening(true) }
                override fun onBeginningOfSpeech() = Unit
                override fun onRmsChanged(rmsdB: Float) = Unit
                override fun onBufferReceived(buffer: ByteArray?) = Unit
                override fun onEndOfSpeech() = Unit
                override fun onPartialResults(partialResults: Bundle?) = Unit
                override fun onEvent(eventType: Int, params: Bundle?) = Unit
                override fun onError(error: Int) {
                    val detail = when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Keinen Befehl gehört. Bitte erneut versuchen."
                        SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE, SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED -> "Das deutsche Offline-Sprachmodell fehlt. Bitte in den Google-Spracheinstellungen herunterladen."
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Bitte Mikrofonzugriff für Accessible Reader erlauben."
                        else -> "Die Spracheingabe ist gerade nicht verfügbar. Prüfe Sprachdaten und Mikrofon."
                    }
                    model.showError(detail)
                }
                override fun onResults(results: Bundle?) {
                    val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                    if (text.isNullOrBlank()) model.showError("Keinen Befehl erkannt.") else model.command(text)
                }
            })
        }
        model.listening(true)
        recognizer?.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "de-DE")
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        })
    }
    override fun onStop() { recognizer?.cancel(); if (model.state.value.listening) model.listening(false); super.onStop() }
    override fun onDestroy() { recognizer?.destroy(); super.onDestroy() }
}
