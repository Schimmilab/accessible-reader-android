package de.schimmilab.accessiblereader.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.schimmilab.accessiblereader.ReaderState
import de.schimmilab.accessiblereader.ReaderViewModel
import de.schimmilab.accessiblereader.core.OnlineVoicePolicy
import de.schimmilab.accessiblereader.core.SPEED_MAX
import de.schimmilab.accessiblereader.core.SPEED_MIN
import de.schimmilab.accessiblereader.core.speedLabel
import de.schimmilab.accessiblereader.core.positionAnnouncement
import de.schimmilab.accessiblereader.core.positionLabel
import de.schimmilab.accessiblereader.core.voicesHeading
import java.util.Locale

private val LightColors = lightColorScheme(primary = Color(0xFF075E52), onPrimary = Color.White,
    background = Color(0xFFF7F5EF), surface = Color(0xFFF7F5EF), surfaceContainer = Color(0xFFEAECE3),
    onSurface = Color(0xFF202720), onSurfaceVariant = Color(0xFF455047), outline = Color(0xFF727970),
    primaryContainer = Color(0xFFD2E9DC), outlineVariant = Color(0xFFBCC8BD))
private val DarkColors = darkColorScheme(primary = Color(0xFF8FD4BE), onPrimary = Color(0xFF00382F),
    background = Color(0xFF141B17), surface = Color(0xFF141B17), surfaceContainer = Color(0xFF263029),
    onSurface = Color(0xFFF1F2E8), onSurfaceVariant = Color(0xFFC3CFC5),
    primaryContainer = Color(0xFF355146), outlineVariant = Color(0xFF63756B))

@Composable
fun ReaderTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        typography = Typography(
            headlineLarge = Typography().headlineLarge.copy(fontFamily = FontFamily.Serif, fontSize = 34.sp),
            headlineMedium = Typography().headlineMedium.copy(fontFamily = FontFamily.Serif, fontSize = 28.sp),
            bodyLarge = Typography().bodyLarge.copy(fontSize = 18.sp, lineHeight = 27.sp),
            labelLarge = Typography().labelLarge.copy(fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        ), content = content)
}

@Composable
fun ReaderScreen(s: ReaderState, model: ReaderViewModel, onOpen: () -> Unit, onPlay: () -> Unit,
                 onListen: () -> Unit, onVoiceSettings: () -> Unit, onShareReport: (String) -> Unit = {}) = ReaderTheme {
    val chapter = s.document.chapters[s.chapter]
    var commandHelp by remember { mutableStateOf(false) }
    Scaffold { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(22.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("ACCESSIBLE READER", style = MaterialTheme.typography.labelMedium, letterSpacing = 2.sp, color = MaterialTheme.colorScheme.primary)
                    Text("Zeit zum Zuhören.", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.semantics { heading() })
                    Text("Deine Dokumente. In deinem Tempo.", style = MaterialTheme.typography.bodyMedium)
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Button(onClick = onOpen, enabled = !s.busy && !s.listening, modifier = Modifier.weight(1f).heightIn(min = 56.dp)) { Text("PDF öffnen") }
                        TextButton(onClick = model::demo, enabled = !s.busy && !s.listening, modifier = Modifier.heightIn(min = 56.dp)) { Text("Leseprobe") }
                    }
                    OutlinedButton(onClick = { model.library(true) }, enabled = !s.busy && !s.listening,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("Bibliothek") }
                }
            }
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text(s.document.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                        HorizontalDivider()
                        Text("ABSCHNITT ${s.chapter + 1} VON ${s.document.chapters.size}", style = MaterialTheme.typography.labelSmall, letterSpacing = 1.sp)
                        Text(chapter.title, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
                        Text(if (chapter.firstPage == chapter.lastPage) "Seite ${chapter.firstPage}" else "Seiten ${chapter.firstPage} bis ${chapter.lastPage}", style = MaterialTheme.typography.bodyMedium)
                        LinearProgressIndicator(progress = { if (s.durationMs > 0) (s.positionMs.toFloat() / s.durationMs).coerceIn(0f, 1f) else 0f },
                            modifier = Modifier.fillMaxWidth().height(6.dp).clearAndSetSemantics { })
                        Text(positionLabel(time(s.positionMs), time(s.durationMs), s.durationMs, s.preparing),
                            modifier = Modifier.semantics {
                                contentDescription = positionAnnouncement(time(s.positionMs), time(s.durationMs), s.durationMs, s.preparing)
                            }, style = MaterialTheme.typography.bodyMedium)
                        Button(onClick = onPlay, enabled = !s.busy && s.connected && !s.listening,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).semantics { stateDescription = if (s.playbackRequested) "Vorlesen aktiviert" else "Wiedergabe pausiert" }) {
                            Text(if (s.playbackRequested) "Pause" else "Vorlesen", fontSize = 21.sp)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedButton(onClick = { model.seek(-30) }, enabled = !s.busy && s.durationMs > 0 && !s.listening, modifier = Modifier.weight(1f).heightIn(min = 64.dp)) { Text("30 Sekunden\nzurück") }
                            OutlinedButton(onClick = { model.seek(30) }, enabled = !s.busy && s.durationMs > 0 && !s.listening, modifier = Modifier.weight(1f).heightIn(min = 64.dp)) { Text("30 Sekunden\nvor") }
                        }
                    }
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (s.busy) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                        TextButton(onClick = model::cancelWork) { Text("Vorgang abbrechen") }
                    }
                    Text(s.status, style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Im Dokument", style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(onClick = { model.chapter(s.chapter - 1) }, enabled = !s.busy && s.chapter > 0 && !s.listening, modifier = Modifier.weight(1f).heightIn(min = 60.dp)) { Text("Vorheriger\nAbschnitt") }
                        OutlinedButton(onClick = { model.chapter(s.chapter + 1) }, enabled = !s.busy && s.chapter < s.document.chapters.lastIndex && !s.listening, modifier = Modifier.weight(1f).heightIn(min = 60.dp)) { Text("Nächster\nAbschnitt") }
                    }
                    OutlinedButton(onClick = { model.contents(true) }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("Inhaltsverzeichnis") }
                    OutlinedButton(onClick = model::announcePosition, enabled = !s.busy && !s.listening, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("Wo bin ich? Position vorlesen") }
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Mit deiner Stimme", style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
                    Button(onClick = onListen, enabled = !s.busy, modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp)) {
                        Text(if (s.listening) "Zuhören beenden" else "Sprachbefehl geben")
                    }
                    Text("Zum Beispiel: „30 Sekunden zurück“ oder „nächstes Kapitel“. Das Buch pausiert während der Eingabe.", style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = { commandHelp = true }) { Text("Alle Befehle und Texteingabe") }
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Stimme & Tempo", style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
                    Text("Lokale Teststimme · keine Cloud-Kosten", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = model::slower, enabled = s.speed > SPEED_MIN,
                            modifier = Modifier.weight(1f).heightIn(min = 56.dp)
                                .semantics { stateDescription = "jetzt ${speedLabel(s.speed)} fach" }) { Text("Langsamer") }
                        Text("${speedLabel(s.speed)}×",
                            modifier = Modifier.semantics { contentDescription = "Geschwindigkeit ${speedLabel(s.speed)} fach" })
                        OutlinedButton(onClick = model::faster, enabled = s.speed < SPEED_MAX,
                            modifier = Modifier.weight(1f).heightIn(min = 56.dp)
                                .semantics { stateDescription = "jetzt ${speedLabel(s.speed)} fach" }) { Text("Schneller") }
                    }
                    OutlinedButton(onClick = { model.settings(true) }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("Stimme und Einstellungen") }
                }
            }
            item {
                Text(s.document.notice, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))
                Text("Prototyp 0.4 · Android zuerst", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    if (s.showContents) AlertDialog(onDismissRequest = { model.contents(false) }, title = { Text("Inhaltsverzeichnis") },
        text = {
            LazyColumn(Modifier.heightIn(max = 450.dp)) {
                item { TextButton(onClick = model::readContents, enabled = !s.busy) { Text("Übersicht vorlesen") } }
                itemsIndexed(s.document.chapters) { index, c ->
                    Row(Modifier.fillMaxWidth().heightIn(min = 64.dp)
                        .selectable(selected = index == s.chapter, enabled = !s.busy && !s.listening, role = Role.RadioButton, onClick = { model.chapter(index) }).padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = index == s.chapter, onClick = null)
                        Column(Modifier.padding(start = 8.dp)) { Text("${index + 1}. ${c.title}"); Text("Ab Seite ${c.firstPage}", style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }
        }, confirmButton = { TextButton(onClick = { model.contents(false) }) { Text("Schließen") } })
    if (s.showLibrary) AlertDialog(onDismissRequest = { model.library(false) }, title = { Text("Bibliothek") },
        text = {
            if (s.library.isEmpty()) Text("Noch keine PDFs importiert. Über „PDF öffnen“ kommt dein erstes Dokument hierher.")
            else LazyColumn(Modifier.heightIn(max = 450.dp)) {
                itemsIndexed(s.library) { _, entry ->
                    // Opening and removing are separate focus targets; nesting them would trap TalkBack.
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Row(Modifier.weight(1f).heightIn(min = 64.dp)
                            .selectable(selected = entry.current, enabled = !s.busy && !s.listening,
                                role = Role.RadioButton, onClick = { model.openFromLibrary(entry.id) })
                            .padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = entry.current, onClick = null)
                            Text(entry.label, Modifier.padding(start = 8.dp))
                        }
                        TextButton(onClick = { model.askRemoval(entry.id) }, enabled = !s.busy && !s.listening,
                            modifier = Modifier.heightIn(min = 56.dp).semantics { contentDescription = "${entry.title} entfernen" }) { Text("Entfernen") }
                    }
                }
            }
        }, confirmButton = { TextButton(onClick = { model.library(false) }) { Text("Schließen") } })
    s.pendingRemoval?.let { id ->
        val name = s.library.firstOrNull { it.id == id }?.title ?: "Dieses Dokument"
        AlertDialog(onDismissRequest = { model.askRemoval(null) }, title = { Text("Wirklich entfernen?") },
            text = { Text("$name wird aus der Bibliothek entfernt. Der vorgelesene Text und die Hörposition gehen verloren. Deine PDF-Datei auf dem Gerät bleibt unberührt.") },
            // Not "Entfernen" again: two identically named buttons are indistinguishable by ear.
            confirmButton = { TextButton(onClick = model::confirmRemoval) { Text("Ja, entfernen") } },
            dismissButton = { TextButton(onClick = { model.askRemoval(null) }) { Text("Abbrechen") } })
    }
    if (s.showSettings) AlertDialog(onDismissRequest = { model.settings(false) }, title = { Text("Stimme und Einstellungen") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.heightIn(max = 460.dp)) {
                // First in the dialog on purpose: a helper looked for it below the engine list and could not find it.
                item {
                    OutlinedButton(onClick = model::runDiagnostics, enabled = !s.diagnosing,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                        Text(if (s.diagnosing) "Prüfung läuft, bitte warten" else "Sprachausgabe prüfen")
                    }
                }
                if (s.diagnosing) item { Text(s.status, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
                if (s.report.isNotBlank()) {
                    item {
                        OutlinedButton(onClick = { onShareReport(s.report) }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                            Text("Bericht teilen")
                        }
                    }
                    item { Text(s.report, style = MaterialTheme.typography.bodySmall) }
                }
                item { Text("Die Android-Stimme dient zum Prüfen der Bedienung. Google Chirp 3 HD, Neural2 und natürlichere lokale Stimmen folgen separat. Es wird kein PDF an einen Cloud-Dienst gesendet.") }
                item { Text("„Probe“ spielt bei jeder Stimme denselben Text mit Zahlen, Datum und Abkürzungen.", style = MaterialTheme.typography.bodyMedium) }
                item { Text("Sprachausgabe", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() }) }
                item { Text("Nicht jede Sprachausgabe gibt ihre Stimmen preis. Findet die App hier keine Stimme, hilft eine andere Sprachausgabe.", style = MaterialTheme.typography.bodyMedium) }
                itemsIndexed(s.engines.entries.toList()) { _, engine ->
                    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).selectable(selected = s.engineId == engine.key, enabled = !s.busy,
                        role = Role.RadioButton, onClick = { model.engine(engine.key) }), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = s.engineId == engine.key, onClick = null)
                        Text(engine.value, Modifier.padding(start = 8.dp))
                    }
                }
                // Names the engine on purpose: a report listed 17 voices for one engine while the app offered the
                // four of another, and nothing on this screen said which engine the list belonged to.
                item {
                    Text(voicesHeading(s.engines[s.engineId], s.voices.size),
                        style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                }
                if (s.voices.isEmpty()) item { Text("Diese Sprachausgabe meldet keine deutsche Stimme. Bitte oben eine andere wählen.") }
                itemsIndexed(s.voices) { _, voice ->
                    // Two separate focus targets: choosing the voice, and hearing it. Nesting them would trap TalkBack.
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Row(Modifier.weight(1f).heightIn(min = 56.dp).selectable(selected = s.voiceId == voice.id, enabled = !s.busy,
                            role = Role.RadioButton, onClick = { model.voice(voice.id) }), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = s.voiceId == voice.id, onClick = null)
                            Text(voice.label, Modifier.padding(start = 8.dp))
                        }
                        TextButton(onClick = { model.previewVoice(voice.id) }, enabled = !s.busy,
                            modifier = Modifier.heightIn(min = 56.dp).semantics { contentDescription = "Hörprobe für ${voice.label}" }) { Text("Probe") }
                    }
                }
                // Only appears once the app has actually timed this voice. A voice that cannot keep up with
                // listening decides whether a book plays through, and nothing else on this screen shows it.
                s.voiceSpeed?.let { note -> item { Text(note, style = MaterialTheme.typography.bodyMedium) } }
                item { Text("Stimmen aus dem Internet", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() }) }
                item {
                    Text("Manche Stimmen holen ihre Sprache aus dem Internet und klingen besser. Die App selbst geht " +
                        "nie online, das erledigt die Sprachausgabe. Über mobile Daten kostet das Datenvolumen.",
                        style = MaterialTheme.typography.bodyMedium)
                }
                itemsIndexed(listOf(
                    OnlineVoicePolicy.WIFI_ONLY to "Nur im WLAN",
                    OnlineVoicePolicy.ALWAYS to "Auch über mobile Daten",
                    OnlineVoicePolicy.NEVER to "Gar nicht, nur Offline-Stimmen")) { _, (policy, label) ->
                    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).selectable(selected = s.onlineVoices == policy,
                        enabled = !s.busy, role = Role.RadioButton, onClick = { model.onlineVoices(policy) }),
                        verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = s.onlineVoices == policy, onClick = null)
                        Text(label, Modifier.padding(start = 8.dp))
                    }
                }
                item { TextButton(onClick = onVoiceSettings, enabled = !s.busy) { Text("Android-Sprachdaten öffnen") } }
                item { TextButton(onClick = model::refreshVoices) { Text("Stimmen neu laden") } }
                item { Text("Erzeugtes Audio: ${s.cacheBytes / (1024 * 1024)} MB. Ab ${ReaderViewModel.CACHE_BUDGET_BYTES / 1024 / 1024} MB räumt der Reader das älteste selbst weg. Wird es wieder gebraucht, erzeugt er es neu.") }
                item { OutlinedButton(onClick = model::clearCache, enabled = !s.busy) { Text("Erzeugtes Audio löschen") } }
            }
        }, confirmButton = { TextButton(onClick = { model.settings(false) }) { Text("Fertig") } })
    if (commandHelp) {
        var input by remember { mutableStateOf("") }
        AlertDialog(onDismissRequest = { commandHelp = false }, title = { Text("Sprachbefehle") }, text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Vorlesen · Pause · 30 Sekunden zurück · 30 Sekunden vor · nächstes Kapitel · vorheriges Kapitel · Inhaltsverzeichnis · Bibliothek · wo bin ich · Kapitel 2")
                Text("Zum Testen lässt sich ein Befehl auch eingeben.")
                OutlinedTextField(value = input, onValueChange = { input = it }, label = { Text("Befehl eingeben") })
            }
        }, confirmButton = { TextButton(onClick = { commandHelp = false; model.command(input) }, enabled = input.isNotBlank() && !s.busy) { Text("Ausführen") } },
            dismissButton = { TextButton(onClick = { commandHelp = false }) { Text("Schließen") } })
    }
    s.error?.let { message -> AlertDialog(onDismissRequest = model::dismissError, title = { Text("Hinweis") }, text = { Text(message) },
        confirmButton = { TextButton(onClick = model::dismissError) { Text("Verstanden") } }) }
}

private fun time(ms: Long): String = String.format(Locale.GERMAN, "%d:%02d", ms / 60000, (ms / 1000) % 60)
