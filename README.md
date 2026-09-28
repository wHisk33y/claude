# Knuffi – dein modernes Taschen-Haustier 🥚✨

Knuffi ist ein Tamagotchi für Android im modernen Gewand: Ein kleines Wesen schlüpft aus einem Ei,
wächst mit deiner Pflege heran und entwickelt sich je nach Pflege in eine von vielen Formen.
Dazu gibt es ein Homescreen-Widget, Benachrichtigungen, Minispiele, einen Shop, Erfolge und einen Schrittzähler.

## APK herunterladen

Jeder Push baut die App automatisch über GitHub Actions.
Die neueste **Knuffi.apk** liegt unter **Releases → „Knuffi (neuester Build)“**.
Du kannst sie direkt auf dem Handy herunterladen und installieren (Android 8.0 oder neuer).
Eventuell musst du dafür die Installation aus unbekannten Quellen erlauben.

## Funktionen

- **Drei Grafikstile**, wählbar beim Start und jederzeit in den Einstellungen:
  - *Kawaii*: Pastellfarben, Verläufe, weiche Formen, Fredoka-Schrift
  - *Pixel-Retro*: echte Pixel-Art mit Pixel-Umrandung, Retro-Schriften
  - *Minimal*: flach und ruhig, übernimmt die Material-You-Farben deines Handys
- **Evolution**: Ei → Baby → Kind → Teenager → Erwachsen. Welche von 12 Formen entsteht, hängt
  von deiner Pflege ab (Pflegefehler, Stimmung, Verhältnis von Aktivität zu Essen).
  Beispiele: sportliche Pflege → Drako, der Drache; viel Futter → Mochi-König; ausgewogen → Stellaris;
  vernachlässigt → Schattling.
- **Werte**: Sattheit, Laune, Energie, Hygiene und Gesundheit. Die Werte sinken in Echtzeit, auch wenn
  die App geschlossen ist. Dein Haustier schläft, wird krank und macht Häufchen 💩.
- **Schwierigkeit** (umschaltbar): *Entspannt* (kann nicht sterben) oder *Klassisch* (schnellerer Verfall,
  bei Vernachlässigung reist das Haustier zu den Sternen, danach gibt es ein neues Ei).
- **Animationen**: Atmen, Blinzeln, Hüpfen, Umherlaufen, Augen folgen deinem Finger, Streicheln per Wischen,
  Herzchen, Sterne, Seifenblasen, Konfetti, Zzz, Tag-Nacht-Zyklus mit Sonne, Mond und Sternen,
  Level-up- und Evolutions-Sequenzen.
- **Minispiele**: Futterfang, Memory, Blitz-Tap. Sie bringen Münzen und XP.
- **Shop**: Essen, Medizin und Pflege, Accessoires (Hüte, Brillen, Schals …) und Zimmer
  (Zauberwald, Unterwasserwelt, Weltall, Candyland), jeweils mit Live-Vorschau.
- **Gamification**: Level und XP, tägliche Login-Belohnung mit Serie (7-Tage-Zyklus), 3 Tagesaufgaben plus Bonus,
  30 Erfolge, Statistik und „Sternenhimmel“ für frühere Haustiere.
- **Gassi gehen**: Der Hardware-Schrittzähler macht dein Haustier mit echten Schritten fitter.
  Dazu kommen Schritt-Belohnungen und ein einstellbares Tagesziel.
- **Widget**: animiertes Haustier, Werte als Balken und Schnellknöpfe (füttern, spielen, putzen, schlafen).
  Die Größe ist anpassbar, und das Widget folgt dem gewählten Stil.
- **Benachrichtigungen**: bei Hunger, Krankheit, Schmutz, Langeweile oder Müdigkeit, mit Direkt-Aktionen
  wie „Füttern“. Außerdem Hinweise auf tägliche Belohnungen und Schrittziele. Es gibt eine Nachtruhe, und
  die Hinweise haben Abklingzeiten, damit sie nicht nerven.

## Projektstruktur

```
core/   Reine Kotlin-Spiellogik (Simulation, Evolution, Quests, Erfolge, Shop) + Unit-Tests
app/    Android-App (Jetpack Compose)
  render/   Canvas-Grafik-Engine: Haustier, Räume, Partikel, Pixel-Art-Renderer
  ui/       Bildschirme, Minispiele, Overlays, Themes
  widget/   Homescreen-Widget (RemoteViews, animiert per ViewFlipper)
  notify/   Benachrichtigungen + Aktionen
  work/     Hintergrund-Worker (alle 15 Minuten)
  steps/    Schrittzähler
scripts/ci-emulator.sh   Screenshots aller Bildschirme + Absturztest auf dem CI-Emulator
```

## Selbst bauen

Voraussetzungen: JDK 17 und das Android SDK (Plattform 35).

```bash
./gradlew :core:test :app:assembleRelease
# APK: app/build/outputs/apk/release/app-release.apk
```

Die App wird mit einem festen Schlüssel (`app/knuffi.keystore`) signiert, damit sich jede neue Version
als Update über die alte installieren lässt und der Spielstand erhalten bleibt.
Für eine Veröffentlichung im Play Store solltest du einen eigenen, geheimen Schlüssel verwenden.

## Lizenzen

Schriften: [Fredoka](https://github.com/hafontia/Fredoka-One), VT323 und Press Start 2P,
jeweils unter der SIL Open Font License 1.1 (siehe `licenses/OFL.txt`).
