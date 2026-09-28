# The House Always Wins (Challenge 50): Konzept & Umsetzung

> Branch `26.3-the-house-always-wins`, basierend auf `26.3` (Minecraft 26.3, Mojmap, Fabric 0.161).
> Stand: Umsetzung v1. Alle Zahlen sind nachgerechnet; die Skripte liegen unter `scripts/casino/`.

---

## 1. Kurz erklärt

Du spielst ganz normal Minecraft und bekommst alle Blöcke und Drops wie immer. Jedes Item hat einen
**Wert in Jetons** (übernommen aus ProjectE). Beim **Croupier am Spawn** tauschst du Items gegen Jetons,
kaufst Items zurück und kaufst die Casino-Geräte. **Alle 10 Minuten** kassiert das Haus eine steigende
Gebühr vom **gemeinsamen Guthaben des Teams**. Kann das Team nicht zahlen, **gewinnt das Haus und der
Run ist verloren**.

Zocken kannst du an drei Geräten: Spielautomat, Crash-Startrampe und Roulette-Tisch, jeweils mit
**100 % RTP**. Verlorene Wetten schicken dir Monster auf den Hals. Jeder Tod kostet **25 % deiner Jetons**,
und eine Hand **Blackjack** entscheidet, ob du mit deinem Inventar zurückkommst.

---

## 2. Deine Entscheidungen (umgesetzt)

| Frage | Entscheidung |
|---|---|
| Rohe Erze | **wertlos** wie in ProjectE, erst schmelzen |
| Items kaufen | **nur Items, die man schon einmal eingezahlt hat** |
| Roulette | **klassisch** (einfache Null, Null gibt den Einsatz zurück) |
| Blackjack | **nur als Todesmechanik** |
| Hardcore | **keine Wiederbelebung** per Blackjack |
| 25 % beim Tod | gilt **immer**, auch wenn man Blackjack gewinnt |
| Einsatzlimit | **All-in erlaubt** |
| Croupier | **fest am Spawn** in einem kleinen Stand |
| Geräte | beim Croupier **mit Rohstoffen kaufen**; der Kauf schaltet das Gerät **und sein Rezept** frei |
| Konten | **getrennt pro Spieler**, die Gebühr wird aber vom **Team-Gesamtguthaben** bezahlt, **anteilig** |
| Gebühr nicht zahlbar | **Run verloren** |
| Gebühren-Intervall | **alle 10 Minuten** |
| Gebühren-Deckel | **deutlich höher** als im ersten Entwurf (siehe 4) |

---

## 3. Wirtschaft

### 3.1 Werte (ProjectE)

Die Werte werden **offline** berechnet (`scripts/casino/emc_calc.py`) und liegen als Tabelle in der Mod
(`data/challengecraft/casino/emc_values.json`, **1 290 Items**). Grundlage:

- die ~200 Basiswerte aus ProjectE (`pe_custom_conversions/defaults.json`, `metals.json`, MIT-Lizenz),
- **alle echten 26.3-Rezepte** (Crafting, Ofen, Schmelzofen, Räucherofen, Lagerfeuer, Steinsäge,
  Schmiedetisch), Datenquelle ist der offizielle 26.3-Datendump (`misode/mcmeta`, Tag `26.3-data-json`),
- ProjectE-Regeln: Das günstigste Rezept gewinnt, eine Zutat mit Auswahl zählt ihre billigste Option,
  Behälter (Eimer, Flaschen) werden zurückgerechnet, **Erze und Rohmaterialien sind wertlos**, Werte
  werden abgerundet und unter 1 wertlos.

Zur Laufzeit kommen die ProjectE-Stack-Regeln dazu: Beschädigte Werkzeuge verlieren anteilig an Wert,
Verzauberungen bringen `+5000 / Gewicht × Stufe`.

Beispiele: Planke 8 · Stock 4 · Stamm 32 · Kohle 128 · Eisen 256 · Gold 2 048 · Lapis 864 · Diamant 8 192 ·
Smaragd 16 384 · Netherit 57 344 · Enderauge 1 792 · Brot 72 · Rohes Eisen / Erze **0**.

### 3.2 Konto und Jeton-Beutel

- Das Guthaben liegt serverseitig in **Centi-Jetons** (1/100). Dadurch gibt es keinerlei Rundungsverluste
  bei Linienbruchteilen, Item-Einsätzen und anteiligen Gebühren.
- Jeder Spieler hat genau einen **Jeton-Beutel** im Inventar. Sein Name zeigt das Guthaben
  („Jeton-Beutel · 12 480 Jetons“). Er kann weder gedroppt noch dupliziert werden und wird bei Verlust
  wieder ausgeteilt. Rechtsklick öffnet die Kontoansicht.

### 3.3 Der Croupier und sein Stand

- Beim ersten Laden der Spawn-Region baut die Mod **4 Blöcke östlich des Weltspawns** einen kleinen Stand:
  5×5-Plattform aus poliertem Schwarzstein mit rotem Teppich, Goldblock-Ecken mit Laternenpfosten,
  drei **Kassentresen** (eigenes Modell mit Marmorplatte, Messingleiste und Tischglocke) und dahinter
  der **Croupier** (eigenes Modell und eigener Skin: Frack, rote Weste, Fliege, Zylinder, Schnurrbart).
- Der Croupier dreht den Kopf zum nächsten Spieler, atmet, trommelt mit den Fingern und macht Gesten:
  winken beim Öffnen, nehmen beim Einzahlen, auszahlen beim Kaufen, Hut ziehen beim Gerätekauf.
  Er ist unverwundbar und wird ersetzt, falls er verschwindet.
- **Kasse** (Rechtsklick auf Croupier oder Tresen), vier Tabs:
  - **Einzahlen:** Inventar-Raster, Stacks anklicken, Wert live, „Auswahl einzahlen“ oder
    „Alles Wertvolle (ohne Hotbar)“.
  - **Items kaufen:** alle bekannten Items mit Suche, ×1 / ×16 / ×64 / Max. Der Preis entspricht dem
    Einzahlwert, das Haus nimmt keinen Aufschlag.
  - **Konto:** nächste Gebühr, Countdown, Team-Gesamt, jeder Spieler mit Guthaben und Anteil.
  - **Geräte:** die drei Geräte mit Preis, eigenem Bestand und Kauf-Button.
- **Schnell-Einzahlen ohne Menü:** Schleich-Rechtsklick mit einem Stack auf den Croupier.

### 3.4 Geräte kaufen und craften

| Gerät | Preis beim Croupier | Rezept danach (Werkbank) |
|---|---|---|
| **Spielautomat** | 12 Eisen, 8 Redstone, 1 Gold | `IOI / IGL / IRI` (Eisen, Gold, Glasscheibe, Hebel, Redstone-Block) |
| **Crash-Startrampe** | 8 Gold, 16 Schwarzpulver, 8 Papier | `GFG / GTG / SSS` (Gold, Feuerwerksrakete, TNT, Glatter-Stein-Stufe) |
| **Roulette-Tisch** | 4 Diamanten, 8 Gold, 1 Smaragd | `WWW / DED / P P` (grüne Wolle, Diamant, Smaragd, Bretter) |

Der erste Kauf schaltet das **Rezept für diesen Spieler** frei. Vorher lässt sich das Ergebnis aus dem
Crafting-Feld nicht herausnehmen (mit Hinweis in der Actionbar).

---

## 4. Die Hausgebühr

- **Alle 10 Minuten Run-Zeit** (Run-Timer, Pause stoppt ihn) wird die Gebühr fällig.
- Bezahlt wird aus dem **Team-Gesamtguthaben**, und zwar **anteilig**: Wer doppelt so viel hat, zahlt
  doppelt so viel. Rundungsreste trägt der Reichste.
- **Reicht das Gesamtguthaben nicht: Bankrott.** Titel „DAS HAUS GEWINNT“, ein Gong, alle Spieler werden
  Zuschauer, der Run ist verloren (wie ein gescheiterter Hardcore-Run: keine XP).
- 60 Sekunden vorher erscheinen ein Banner und eine Glocke. Die HUD-Karte zeigt Countdown, Betrag und
  Kontostand und wird rot, wenn das Team die nächste Gebühr **nicht** decken könnte.

**Kurve:** `Gebühr(k) = 65 536 / (1 + (12 / k)³)` für die k-te Abbuchung, gerundet auf 16,
× (1 + 0,5 pro weiterem Spieler). Die Gebühr beginnt klein, wird in der zweiten Stunde ernst und läuft
gegen den Deckel von **65 536 = 8 Diamanten pro 10 Minuten**, wächst also nie ins Unendliche.

| Abbuchung | Run-Zeit | Gebühr (Solo) | etwa |
|---:|---|---:|---|
| 1 | 0:10 | 32 | 1 Stamm |
| 2 | 0:20 | 304 | 1 Eisen |
| 3 | 0:30 | 1 008 | 4 Eisen |
| 4 | 0:40 | 2 336 | 9 Eisen |
| 5 | 0:50 | 4 416 | 5 Lapis |
| 6 | 1:00 | 7 280 | knapp 1 Diamant |
| 9 | 1:30 | 19 440 | 2,4 Diamanten |
| 12 | 2:00 | 32 768 | 4 Diamanten |
| 15 | 2:30 | 43 344 | 5,3 Diamanten |
| 18 | 3:00 | 50 560 | 6,2 Diamanten |
| 24 | 4:00 | 58 256 | 7 Diamanten |
| ∞ | | → 65 536 | 8 Diamanten |

Summe bis 2 h ≈ 146 k, bis 3 h ≈ 411 k, bis 4 h ≈ 745 k. Das ist bewusst hart: Ab der zweiten Stunde
braucht man Farmen (Eisen, Gold, Mobs) oder Glück am Automaten. **Zum Einstellen beim Testen:**
`/casino feescale <Prozent>` skaliert die ganze Kurve (z. B. 50 = halb so teuer).

---

## 5. Verlorene Wetten: „Das Haus schickt Grüße“

- Nettoverluste landen auf dem **Pechkonto**, Gewinne ziehen ihn wieder ab.
- Nach 10 Sekunden Spielpause (spätestens 60 Sekunden nach dem ersten Verlust) wird das Pechkonto als
  **Mobwelle** ausgezahlt: 5–10 Blöcke um den Spieler, auf seiner Höhe, mit Rauch, Portalpartikeln,
  Titel und Unheil-Sound.
- Die Stärke richtet sich nach dem Verlust **relativ zur aktuellen Gebühr** (mindestens 256):
  < 5 % keine · 5–25 % Stufe 1 (2 Mobs) · 25–100 % Stufe 2 (3–4) · 100–400 % Stufe 3 (5–6, Hexe,
  Vindicator, Pillager) · > 400 % Stufe 4 (6–8, Evoker **plus genau ein Ravager**). Im Nether kommen
  Piglin-Brutes, Lohen und Wither-Skelette, im End Endermen dazu.
- **Nie:** Wither, Warden, Drache, Großer Wächter. Casino-Mobs **droppen keine Beute**.

---

## 6. Tod und Blackjack

1. Bei jedem Tod behält das Haus **25 % des Guthabens** (mit Banner und Sound).
2. **Totem in der Hand:** Das Totem rettet dich, es ist kein Tod und es gibt keine Abgabe.
3. **Hardcore aktiv:** Du stirbst normal, es gibt keine Hand Blackjack.
4. Sonst wird der Tod abgefangen. Du bist an Ort und Stelle eingefroren und unverwundbar, und es öffnet
   sich der **Blackjack-Tisch** („Das Haus bietet dir ein Spiel an“):
   - **Gewonnen:** Du respawnst an **deinem Spawnpunkt** (Bett, Anker oder Weltspawn) mit
     **komplettem Inventar und XP**, dazu Totem-Animation, Sound und ohne Todesbildschirm.
   - **Verloren:** Du stirbst normal dort, wo du gefallen bist; die Items liegen dort.
   - **Unentschieden:** Es wird neu gegeben.
   - Ziehen, Halten, Verdoppeln und Teilen sind möglich. Pro Entscheidung hast du 20 Sekunden, danach
     wird automatisch gehalten. Wer das Spiel verlässt, verliert die Hand.
5. Regeln (per Simulation auf ~100 % gewählt): 1 Deck, nach jeder Hand gemischt, Dealer steht auf 17,
   Blackjack zahlt 3:2, kein Verdoppeln nach Teilen, bis 4 Hände, geteilte Asse bekommen je eine Karte.
   Du überlebst etwa **47,7 %** der Hände.

---

## 7. Die Spiele

Alle Ergebnisse entstehen auf dem Server. Der Crash-Punkt wird erst bei der Explosion gesendet, die
Hole-Card des Dealers erst beim Aufdecken. Slot-Gewinne werden erst gutgeschrieben, wenn die Animation
fertig ist.

**Einsatz:** Mit **`+` / `-`** (Tasten frei belegbar, Kategorie „Challenge Craft · Casino“) wählst du die
Stufe: 10 … 1 000 000. Nach der höchsten Stufe kommt **All-in**; es gibt auch eine eigene All-in-Taste.
Schaust du auf ein Gerät, zeigt ein Hinweis unter dem Fadenkreuz Einsatz und Bedienung.

### 7.1 Spielautomat „Minenfieber“

- 5 Walzen × 3 Reihen, 10 Linien, **Wild** = Totem, **Scatter** = Netherstern, **10 Freispiele** mit einem
  zufälligen **expandierenden Glücksitem** (Retrigger +10), Maximalgewinn 5 000× Einsatz.
- Gewinntabelle (× Linieneinsatz): Totem 10/200/2 000/10 000 · Netherit 10/100/1 000/5 000 ·
  Diamant 5/40/400/2 000 · Smaragd, Gold –/30/100/750 · Lapis, Eisen –/5/40/150 · Kupfer, Kohle –/5/25/100.
  Scatter zahlt 2/17/500 × Gesamteinsatz.
- **RTP 99,998 %** (exakt), Trefferquote 27,8 %, Freispiele etwa alle 151 Spins. Die Java-Mathematik ist
  gegen die Python-Rechnung geprüft, und der Server schreibt den RTP beim Start ins Log.
- **Bedienung:** Rechtsklick dreht mit dem aktuellen Einsatz. **Item-Einsatz:** Rechtsklick mit einem
  Item in der Hand nennt den Wert, ein **zweiter Klick** innerhalb von 3 Sekunden setzt den ganzen Stack.
  Gewinne kommen dann **als dieses Item** zurück und schießen als **Fontäne** aus dem Automaten
  (bis 40 Stacks, der Rest geht als Jetons aufs Konto). Große Gewinne (≥ 50×) werden im Chat angekündigt.
- **Darstellung:** Die Walzen drehen sich als **Trommeln hinter dem Glas** des Automaten, mit leuchtenden
  Gewinnlinien und expandierenden Items. Zusätzlich erscheint über der Hotbar ein **großes Walzen-Panel**
  (kein Menü, du kannst dich weiter bewegen): versetzter Start, Bremsen mit Nachfedern,
  **Spannungsphase** bei 2 Scattern, Linien-Highlights, hochzählender Gewinn, Freispiel-Intro mit
  Glücksitem-Auswahl, Big/Mega/Epic-Win-Banner.

### 7.2 Crash

- Crash-Punkt `M = 1/U`, also **P(M ≥ x) = 1/x → 100 % RTP für jede Strategie**. Der Multiplikator wächst
  mit `e^(0,07·s)`, der Deckel liegt bei 1 000× („ins All“, alle an Bord bekommen 1 000×).
- Rechtsklick auf die Startrampe setzt deinen Einsatz. Die erste Wette startet einen 10-Sekunden-Countdown
  mit Ticken. **Aussteigen:** Rechtsklick auf die Rampe **oder Taste `G` von überall**.
- **Darstellung:** Die Rakete zittert auf der Rampe, startet mit Donnern und steigt mit Funken und
  Rauchspur in den Himmel. Beim Crash explodiert sie als Feuerwerk mit Knall. Rechts oben zeigt ein Panel
  Multiplikator, Flugkurve, Mitspieler mit Ausstiegspunkten und den Verlauf.

### 7.3 Roulette

- Europäischer Kessel, normale Quoten, **Null = Einsatz zurück** (Plein auf die Null zahlt 36:1).
  Alle **21 967** möglichen Wetten haben exakt 100 % RTP (geprüft).
- Rechtsklick auf den Tisch öffnet das Tableau (die Welt läuft weiter). Klick auf eine Zahl setzt Plein,
  auf die Linie zwischen zwei Zahlen Cheval, auf eine Ecke Carré, auf den Streifen unter einer Spalte eine
  Transversale, unter der Linie zwischen zwei Spalten ein Sixain. Dazu Dutzende, Kolonnen und einfache
  Chancen. Jetons wählst du unten oder mit dem Mausrad. Beim Überfahren siehst du eine Vorschau mit Quote.
- Gemeinsame Runden: 25 Sekunden Einsätze, 8 Sekunden Kugellauf, Ergebnis. Das Rad dreht sich **im Tableau
  und auf dem Tisch in der Welt synchron**, die Kugel rattert über die Rauten und fällt ins richtige Fach.

---

## 8. Sounds und Grafiken

- **Sounds** (`scripts/casino/sound_mix.py`, 47 Dateien): echte Aufnahmen für Karten und Jetons
  (Kenney Boardgame Pack), Münzen (Kenney RPG Audio, StarNinjas) und Metall (Kenney Impact); synthetisiert
  sind Walzen, Hebel, Spannung, Fanfaren, Freispiel-Glissando, Kugellauf, Raketenstart, Explosion,
  Registrierkasse, Glocke, Unheil-Stinger, Wiederbelebung und Gong.
  Alles ist mono (wird mit der Entfernung leiser), getrimmt, hat Fades gegen Klicks, ist per RMS auf
  einheitliche Zielpegel gemischt, mit Limiter bei −1 dBFS und leichtem Raumhall für musikalische Cues.
  Alle Aufnahmen sind **CC0**.
- **Grafiken** (`scripts/casino/make_textures.py`, `make_models.py`): 21 Blocktexturen als Pixel-Art im
  Minecraft-Stil, 4 Blockmodelle, der Croupier-Skin, der Beutel, das Roulette-Rad (hochaufgelöst),
  Filz sowie Karten- und Jeton-Atlas (Kenney Boardgame Pack, CC0).

---

## 9. Testen

Die Challenge ist Level 8 und steht in der Kategorie „Chaos“. Für schnelle Tests gibt es Admin-Befehle:

| Befehl | Wirkung |
|---|---|
| `/casino chips <Anzahl>` | Jetons gutschreiben (negativ: abziehen) |
| `/casino unlock` | alle drei Geräte freischalten und ins Inventar legen |
| `/casino fee` | nächste Gebühr sofort abbuchen |
| `/casino feescale <Prozent>` | Gebührenkurve skalieren |
| `/casino booth` | Stand am Spawn neu bauen |
| `/casino status` | Konten, Einsätze, Gebühr im Chat |

Beim Serverstart schreibt das Log `[Casino] slot machine RTP ... 99.9977 %` und
`[Casino] 1290 item values loaded`.

---

## 10. Was ich hier nicht prüfen konnte

In dieser Umgebung sind `maven.fabricmc.net` und die Mojang-Server gesperrt, außerdem gibt es nur Java 21.
**Die Mod ließ sich daher nicht kompilieren oder starten.** Um das Risiko klein zu halten:

- Jeder Minecraft- und Fabric-Aufruf folgt einem Muster, das es im 26.3-Code der Mod schon gibt, oder
  ist gegen Fabric API 26.3, das Fabric-Referenzmod (26.2) oder eine 26.1-API-Übersicht abgeglichen.
- Die reine Spiellogik (Slot, Roulette, Crash, Blackjack) ist mit Java 21 kompiliert und getestet.
- Ohne Minecraft-Klassen parst `javac` alle Dateien fehlerfrei, und alle Verweise zwischen den eigenen
  Klassen sind konsistent.

Aufrufe, deren genaue 26.3-Signatur ich nicht direkt belegen konnte (falls der Build meckert, liegt es
am ehesten hier): `Entity.kill(ServerLevel)`, `Mob.setPersistenceRequired`/
`setTarget`, `Entity.addTag`, `ServerLevel.sendParticles`, `BlockState.isFaceSturdy`,
`Level.playLocalSound`, `pose().rotate(float)` im GUI,
`Screen.rebuildWidgets()`, `EditBox.setResponder`, `SoundEvents.FIREWORK_ROCKET_LARGE_BLAST`.
