# Knuffi – dein modernes Taschen-Haustier 🥚✨

Knuffi ist ein Tamagotchi für Android im modernen Gewand: Ein kleines Wesen schlüpft aus einem Ei,
wächst mit deiner Pflege heran und entwickelt sich je nach Pflege in eine von vielen Formen.
Dazu gibt es ein Homescreen-Widget, einen Live-Hintergrund, ein Haustier, das über deinen Bildschirm läuft,
Benachrichtigungen, Minispiele, einen Shop, Erfolge und einen Schrittzähler.

## APK herunterladen

Jeder Push baut die App automatisch über GitHub Actions.
Die neueste **Knuffi.apk** liegt unter **Releases → „Knuffi (neuester Build)“**.
Du kannst sie direkt auf dem Handy herunterladen und installieren (Android 8.0 oder neuer).
Eventuell musst du dafür die Installation aus unbekannten Quellen erlauben.
Eine neue Version lässt sich einfach über die alte installieren, dein Spielstand bleibt erhalten.

## Funktionen

- **Plastischer 3D-Look**: Haustier, Zimmer und Requisiten werden mit Licht, Schatten, Glanzpunkten und
  weichen Verläufen gezeichnet. Dazu kommen Tag und Nacht, Wetter (Sonne, Wolken, Regen, Schnee) und Raumlicht.
- **Hell, Dunkel oder System**: in den Einstellungen wählbar. Das Widget folgt der Wahl.
- **Lebendiges Haustier**: Es läuft im Zimmer herum, schaut sich um, tanzt, winkt, gähnt, niest,
  spricht in Sprechblasen und zeigt, was ihm fehlt. Tippen stupst es an, Wischen streichelt es, und es
  folgt deinem Finger.
- **Pflege-Animationen**: Beim Füttern läuft es zum Napf und isst Bissen für Bissen, beim Putzen gibt es
  ein Schaumbad mit Quietscheente, beim Spielen einen Ball, abends geht es ins Bett und bekommt eine Schlafmütze.
- **Evolution**: Ei → Baby → Kind → Teenager → Erwachsen. Welche von 12 Formen entsteht, hängt
  von deiner Pflege ab (Pflegefehler, Stimmung, Verhältnis von Aktivität zu Essen).
- **Werte**: Sattheit, Laune, Energie, Hygiene und Gesundheit. Die Werte sinken in Echtzeit, auch wenn
  die App geschlossen ist. Dein Haustier schläft, wird krank und macht Häufchen 💩.
- **Schwierigkeit** (umschaltbar): *Entspannt* (kann nicht sterben) oder *Klassisch* (schnellerer Verfall,
  bei Vernachlässigung reist das Haustier zu den Sternen, danach gibt es ein neues Ei).
- **Minispiele** mit Countdown, Combos und Effekten:
  - *Futterfang*: Power-ups (Magnet, Zeitlupe, Extraleben), Level, Explosionen
  - *Memory*: 3D-Karten, die sich umdrehen, und Serien-Bonus
  - *Blitz-Tap*: Garten mit Erdhügeln, Hammer, goldenes Haustier und Gewitterwolken
- **Shop**: Essen, Medizin und Pflege, Accessoires zum Anprobieren und Zimmer
  (Zauberwald, Unterwasserwelt, Weltall, Candyland), jeweils mit Live-Vorschau.
- **Gamification**: Level und XP, tägliche Login-Belohnung mit Serie (7-Tage-Zyklus), 3 Tagesaufgaben plus Bonus,
  30 Erfolge, Statistik und „Sternenhimmel“ für frühere Haustiere.
- **Gassi gehen**: Der Hardware-Schrittzähler macht dein Haustier mit echten Schritten fitter.
  Dazu gibt es Meilensteine mit Belohnungen und ein einstellbares Tagesziel.
- **Auf deinem Bildschirm** (beides einzeln in den Einstellungen aktivierbar):
  - *Live-Hintergrund*: Dein Haustier lebt auf deinem Startbildschirm. Antippen streichelt es.
  - *Schwebendes Haustier*: Es läuft unten über den Bildschirm, auch über anderen Apps.
    Tippen = streicheln, ziehen = tragen (und werfen!), lange drücken = App öffnen.
    Dafür braucht die App die Berechtigung „Über anderen Apps einblenden“.
- **Widget**: animiertes Haustier, Werte als Balken und Schnellknöpfe (füttern, spielen, putzen, schlafen).
- **Benachrichtigungen**: bei Hunger, Krankheit, Schmutz, Langeweile oder Müdigkeit, mit Direkt-Aktionen
  wie „Füttern“. Außerdem Hinweise auf tägliche Belohnungen und Schrittziele. Es gibt eine Nachtruhe, und
  die Hinweise haben Abklingzeiten, damit sie nicht nerven.

## Projektstruktur

```
core/   Reine Kotlin-Spiellogik (Simulation, Evolution, Quests, Erfolge, Shop) + Unit-Tests
app/    Android-App (Jetpack Compose)
  render/   Canvas-Grafik-Engine: Haustier, Posen, Verhalten, Zimmer, Requisiten, Partikel
  ui/       Bildschirme, Minispiele, Overlays, Theme (hell/dunkel)
  screen/   Live-Hintergrund und schwebendes Haustier
  widget/   Homescreen-Widget (RemoteViews, animiert per ViewFlipper)
  notify/   Benachrichtigungen + Aktionen
  work/     Hintergrund-Worker (alle 15 Minuten)
  steps/    Schrittzähler
scripts/ci-emulator.sh   Screenshots aller Bildschirme (hell und dunkel) + Absturztest auf dem CI-Emulator
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

Schrift: [Fredoka](https://github.com/hafontia/Fredoka-One) unter der SIL Open Font License 1.1
(siehe `licenses/OFL.txt`).
