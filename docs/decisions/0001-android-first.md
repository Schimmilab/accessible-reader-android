# Entscheidung 0001: Android zuerst

Datum: 9. September 2026

Status: angenommen

## Ausgangslage

Der Reader soll langfristig auch fuer iPhone und iPad interessant sein. TalkBack und VoiceOver unterscheiden sich jedoch bei Fokus, Gesten, Rueckmeldungen, Mediensteuerung und Tests. Eine gemeinsame Oberflaeche wuerde den Start verlangsamen und koennte zu Kompromissen bei der Barrierefreiheit fuehren.

## Entscheidung

Android ist die fuehrende Plattform.

- Das erste benutzbare Produkt ist eine native Android-App.
- Produktentscheidungen werden zuerst mit TalkBack und der Nutzung durch die Testnutzerin geprueft.
- Das Android-MVP wird nicht auf eine gemeinsame iOS-Codebasis ausgerichtet.
- Android verwendet Kotlin, Jetpack Compose, Media3 und die Android-Zugaenglichkeitsfunktionen.
- Eine iOS-Version folgt spaeter als eigene native App mit SwiftUI, VoiceOver, AVFoundation und PDFKit.
- Cloud-Schnittstellen, Testtexte, Test-PDFs und dokumentierte Bedienregeln duerfen beide Plattformen teilen.
- Gemeinsamer Programmcode wird erst eingefuehrt, wenn ein konkreter Vorteil nachgewiesen ist und keine Plattform dadurch schlechter bedienbar wird.

## Folgen

Der erste Prototyp erreicht die Testnutzerin frueher. TalkBack kann ohne Ruecksicht auf eine gemeinsame Oberflaeche sauber umgesetzt werden. Android-spezifische Funktionen wie MediaSession, lokale Spracherkennung und Hintergrunddienste lassen sich direkt nutzen.

Eine spaetere iOS-Version braucht eigene Oberflaechen- und Medienschnittstellen. Das bedeutet mehr Entwicklungsarbeit. Sie kann dafuer VoiceOver und die Staerken von iOS ohne Ruecksicht auf Android-Konventionen nutzen.

Das Repository behaelt vorerst den Namen `accessible-reader-android`. Falls spaeter eine iOS-App entsteht, kann sie in einem eigenen Repository oder in einem uebergeordneten Projekt organisiert werden.
