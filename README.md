# Mounjaro Log

Eine Android-App, die nur auf dem Gerät speichert: Gewicht, Mounjaro-Spritzen und Tagesnotizen.

## Version 2.0

### Gestaltung
- Stil „Plakat“: Tinte auf Weiß, ein Akzent (Zinnoberrot) nur für Aktionen und „heute“
- Schriften Funnel Display und Funnel Sans (SIL Open Font License, siehe `app/src/main/assets/funnel-fonts-OFL.txt`)
- Gewichtsänderungen sind nie rot oder grün gefärbt; Dosen sind Abstufungen einer Farbe
- Komplett auf Deutsch

### Aufbau
Vier Bereiche: **Heute · Verlauf · Auswertung · Mehr**.

- **Heute**: Der geglättete Trend ist die Hauptzahl, die heutige Messung steht klein daneben. Bei Sprüngen ab 0,5 kg erklärt die App die Wasserschwankung. Außerdem zu sehen: Seit-Start-Werte, der Anteil der Wochen im Minus, eine Prognose zum Ziel, 5-%-Meilensteine und der 7-Tage-Zyklus mit nächstem Termin.
- **Plateau**: Bewegt sich der Trend 3 Wochen lang um höchstens ±0,5 kg, zeigt Heute einen eigenen Zustand. Er zeigt das gehaltene Gewicht, Taille und Appetit und bietet „Frage notieren“ an.
- **Eintragen** öffnet ein Fenster von unten mit drei Reitern:
  - Gewicht: vorausgefüllt mit dem letzten Wert, ±0,1-Tasten, Spritze optional am selben Tag
  - Spritze: letzte Dosis vorausgewählt, Vorschlag für die nächste Einstichstelle
  - Notiz: Appetit, Nebenwirkungen, Taillenumfang, Notiz, Frage für den Arzt
- **Verlauf**: Liste mit Filter oder Monatskalender, Tippen bearbeitet einen Eintrag. Mehrere Wägungen pro Tag sind möglich, der Trend nutzt dann den Tagesdurchschnitt.
- **Auswertung**:
  - Trenddiagramm mit Zeitraum, Dosiswechseln und Zielen
  - Wochenmittel
  - kg pro Woche je Dosis (am Trend gemessen)
  - Zyklus-Muster: Abweichung vom Trend nach Tag seit der Spritze
  - Appetit an Tag 1–3 gegenüber Tag 5–7
  - Tage mit Nebenwirkungen je Dosis
- **Mehr**: Ziele, gesammelte Arztfragen, Spritzen-Erinnerung (Benachrichtigung um 9 Uhr am Fälligkeitstag), Sicherung und Export.

### Homescreen-Widgets
Schnelleintrag, Verlauf, Kompakt und Übersicht. Alle sind auf Deutsch, im neuen Farbschema und nutzen für die Summen den Trend.

### Daten und Datenschutz
- SQLite im privaten App-Speicher, keine Internet-Berechtigung, keine Cloud-Sicherung
- Die Datenbank wird beim Update automatisch auf Version 2 migriert (Taille und Arztfrage kommen dazu); bestehende Daten bleiben erhalten
- Nach jeder Änderung schreibt die App automatisch eine JSON-Sicherung; die letzten fünf werden behalten
- JSON-Export und -Wiederherstellung, CSV-Export; ältere Sicherungen (Version 1) lassen sich weiter einlesen

> Eine Deinstallation kann die App-Daten löschen. Vorher eine JSON-Sicherung exportieren.

## Build

API 35, minimal API 26, Java 17.

    gradle :app:testDebugUnitTest :app:assembleDebug

Die GitHub Action signiert das APK mit dem gespeicherten Schlüssel, damit es sich über die installierte Version aktualisieren lässt. Das Ergebnis steht als Artefakt **mounjaro-log-debug-apk** bereit.
