# The House Always Wins (Challenge 50): Konzept & Umsetzung

> Branch `26.3-the-house-always-wins`, basierend auf `26.3` (Minecraft 26.3, Mojmap, Fabric 0.161).
> Stand: Umsetzung v2 (Spiele ohne Menüs, 96 % RTP, Plinko). Alle Zahlen sind nachgerechnet; die Skripte
> liegen unter `scripts/casino/`.

---

## 1. Kurz erklärt

Du spielst ganz normal Minecraft und bekommst alle Blöcke und Drops wie immer. Jedes Item hat einen
**Wert in Jetons** (übernommen aus ProjectE). Beim **Croupier am Spawn** tauschst du Items gegen Jetons,
kaufst Items zurück und kaufst die Casino-Geräte. **Alle 10 Minuten** kassiert das Haus eine steigende
Gebühr vom **gemeinsamen Guthaben des Teams**. Kann das Team nicht zahlen, **gewinnt das Haus und der
Run ist verloren**.

Zocken kannst du an vier Geräten: Spielautomat, Plinko, Crash-Startrampe und Roulette-Tisch. **Das Haus
gewinnt immer:** Jedes Spiel zahlt im Schnitt **96 %** der Einsätze zurück. Wer die Gebühr sicher zahlen
will, muss also **farmen**; Glücksspiel ist die schnelle, aber auf Dauer teure Abkürzung. Verlorene Wetten
schicken dir Monster auf den Hals. Jeder Tod kostet **25 % deiner Jetons**, und eine Hand **Blackjack**
entscheidet, ob du mit deinem Inventar zurückkommst.

Gespielt wird **direkt an den Geräten**, ohne Menüs: Einsatz-Jetons liegen auf jedem Gerät, Ergebnisse
stehen auf seinen Anzeigen, beim Roulette setzt du auf dem Filz des Tisches. Wer einer Welt mit dieser
Challenge beitritt, bekommt im Chat einen kurzen **Hinweis zu Glücksspielsucht** mit Links zu Hilfsangeboten.

---

## 2. Deine Entscheidungen (umgesetzt)

| Frage | Entscheidung |
|---|---|
| Rohe Erze | **wertlos** wie in ProjectE, erst schmelzen |
| Items kaufen | **nur Items, die man schon einmal eingezahlt hat** |
| Roulette | **klassisch** (einfache Null, die Null verliert) |
| RTP | **96 %** in allen vier Spielen |
| Bedienung | **ohne Menüs**, alles am Gerät; die alten Screens bleiben als Reserve im Code |
| Blackjack | **nur als Todesmechanik** |
| Hardcore | **keine Wiederbelebung** per Blackjack |
| 25 % beim Tod | gilt **immer**, auch wenn man Blackjack gewinnt |
| Einsatzlimit | **All-in erlaubt** |
| Croupier | **fest am Spawn** in einem **unzerstörbaren** Stand |
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
- Der Croupier dreht den Kopf zum nächsten Spieler, atmet, richtet ab und zu seine Manschetten und macht
  Gesten: winken beim Öffnen, nehmen beim Einzahlen, auszahlen beim Kaufen, Hut ziehen beim Gerätekauf.
  Die Frackschöße hängen hinten an seiner Taille.
- **Unzerstörbar:** Stand und Croupier bestehen aus eigenen Blöcken, die wie die Vanilla-Blöcke aussehen,
  aber nicht abbaubar sind (auch nicht im Kreativmodus), jeder Explosion standhalten, nicht brennen, von
  Kolben nicht bewegt werden und keine Flüssigkeit hineinlassen. Im Stand (5×5, bis 4 Blöcke hoch) kann
  niemand Blöcke, Wasser, Lava oder Feuer platzieren. Der Croupier ist unverwundbar, wird weder geschoben
  noch von Wasser oder Explosionen bewegt und kehrt an seinen Platz zurück, falls ihn doch etwas versetzt.
  Ein Stand aus einer älteren Version wird automatisch neu gebaut.
- **Kasse** (Rechtsklick auf Croupier oder Tresen), vier Tabs:
  - **Einzahlen:** Inventar-Raster, Stacks anklicken, Wert live, „Auswahl einzahlen“ oder
    „Alles Wertvolle (ohne Hotbar)“.
  - **Items kaufen:** alle bekannten Items mit Suche, ×1 / ×16 / ×64 / Max. Der Preis entspricht dem
    Einzahlwert, das Haus nimmt keinen Aufschlag.
  - **Konto:** nächste Gebühr, Countdown, Team-Gesamt, jeder Spieler mit Guthaben und Anteil.
  - **Geräte:** die vier Geräte mit Preis, eigenem Bestand und Kauf-Button.
  - Alle Texte passen in ihre Kästen: Zu lange Zeilen (vor allem auf Deutsch) werden verkleinert statt
    abgeschnitten.
- **Einzahlen am Tresen (ohne Menü):** Rechtsklick mit einem Stack auf den mittleren Tresen (oder den
  Croupier) legt ihn auf die Marmorplatte. Dort steht pro Stack und in Summe, was das Haus dafür zahlen
  würde. Ein Klick auf einen liegenden Stack nimmt ihn zurück, bis zu 9 Stacks passen drauf; ein Klick
  auf einen freien Platz der Platte legt dazu. **Die Glocke** schließt den Deal ab: Der Croupier nimmt
  die Ware, schiebt die Jetons herüber, und die Chips fliegen sichtbar zu dir, und zwar genau die Jetons,
  aus denen sich der Betrag zusammensetzt (größte zuerst, jeder mit seinem Wert; bis 30 Jetons, ein Rest
  unter 10 steht nur auf dem Konto). Wertloses (z. B. Rohstoffe ohne EMC) und gefüllte Shulkerkisten oder
  Bündel zeigen 0 und kommen beim Deal zurück ins Inventar (oder vor die Füße, wenn es voll ist). Jeder
  Spieler sieht nur seinen eigenen Stapel. Nichts geht verloren: Beim Ausloggen, beim Server-Stopp und
  beim Bankrott kommt alles, was noch auf dem Tresen liegt, zurück.
- **Schnell-Einzahlen:** Schleich-Rechtsklick mit einem Stack auf den Croupier.

### 3.4 Geräte kaufen und craften

| Gerät | Preis beim Croupier | Rezept danach (Werkbank) |
|---|---|---|
| **Spielautomat** | 12 Eisen, 8 Redstone, 1 Gold | `IOI / IGL / IRI` (Eisen, Gold, Glasscheibe, Hebel, Redstone-Block) |
| **Plinko-Brett** | 8 Eisen, 4 Gold, 12 Lapis | `PNP / NLN / IGI` (Bretter, Eisennugget, Lapisblock, Eisen, Gold) |
| **Crash-Startrampe** | 8 Gold, 16 Schwarzpulver, 8 Papier | `GFG / GTG / SSS` (Gold, Feuerwerksrakete, TNT, Glatter-Stein-Stufe) |
| **Roulette-Tisch** | 4 Diamanten, 8 Gold, 1 Smaragd | `WWW / DED / P P` (grüne Wolle, Diamant, Smaragd, Bretter) |

Der erste Kauf schaltet das **Rezept für diesen Spieler** frei. Vorher lässt sich das Ergebnis aus dem
Crafting-Feld nicht herausnehmen (mit Hinweis in der Actionbar).

Aufgestellte Geräte lassen sich **mit der Hand abbauen** (etwa so lange wie eine Werkbank, eine Axt
geht nicht schneller), dabei fällt das Gerät als Item heraus und kann woanders wieder aufgestellt
werden. Nur die Tresen des Croupiers sind unzerstörbar. Wird ein Roulette-Tisch oder eine Crash-Rampe
abgebaut, solange noch Einsätze angenommen werden, bekommt jeder seine Jetons zurück; läuft die Kugel
oder fliegt die Rakete schon, wird die Runde normal zu Ende gespielt.

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
4. Sonst wird der Tod abgefangen, und du stehst plötzlich **am Stand des Croupiers**, vor dem linken oder
   rechten Tresen („Das Haus bietet dir ein Spiel an“, mit Untertitel, was zu tun ist). Dein Inventar
   hält das Haus solange fest, du bist eingefroren und unverwundbar, du kannst weder weglaufen noch Items
   benutzen oder an den Geräten spielen. Der Croupier winkt, teilt die Karten sichtbar auf den Tresen aus,
   deckt seine verdeckte Karte um, zieht nach und zahlt aus oder sammelt ein. Du spielst, indem du die
   Schilder **ZIEHEN / HALTEN / VERDOPPELN / TEILEN** auf dem Tresen anklickst (nicht erlaubte sind
   ausgegraut). Eine Tafel zeigt beide Hände mit Summe und die Restzeit, links am Bildschirm erklärt eine
   Anleitung, was gerade passiert und was von dir erwartet wird. Andere Spieler in der Nähe sehen die
   Hand mit. Zwei Spieler können gleichzeitig spielen (je ein Tresen). Sind beide Plätze belegt oder
   fehlt der Stand, wird die Hand wie früher im Blackjack-Fenster am Todesort gespielt:
   - **Gewonnen:** Du stehst **genau dort, wo du gestorben bist** (gleiche Blickrichtung), mit
     **komplettem Inventar und XP**, voller Gesundheit und vollem Hunger, dazu Totem-Animation und
     Sound, ohne Todesbildschirm. Negative Effekte sind weg, und für ein paar Sekunden schützen dich
     Resistenz und Feuerresistenz. Ist der Todesort selbst tödlich (Lava, in einem Block, Feuer), kommst
     du an die nächste sichere Stelle in bis zu 6 Blöcken; Wasser zählt nicht als tödlich. Nur wenn es
     keine sichere Stelle gibt (z. B. ins Void gefallen), respawnst du am Spawnpunkt.
   - **Verloren:** Du stirbst normal dort, wo du gefallen bist (auch wenn am Tresen gespielt wurde);
     die Items liegen dort.
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

**Ohne Menüs:** Jedes Spiel wird am Gerät selbst gespielt. Worauf du zielst, sagt eine Zeile oben unter
der Gebühren-Karte (welcher Jeton, welche Wette mit Quote, was der Knopf tut).

**Einsatz:** Jedes Gerät hat eine **Jeton-Ablage** mit fünf Jetons rund um deinen aktuellen Einsatz
(10 … 1 000 000, danach **All-in**). Rechtsklick auf einen Jeton macht ihn zu deinem Einsatz; der gewählte
liegt erhöht auf einem Goldring, der anvisierte hebt sich leicht. Die Ablage zeigt jedem Spieler **seinen
eigenen** Einsatz. Die Tasten **`+` / `-`** (und eine All-in-Taste) funktionieren weiterhin als Reserve.

**Mehrspieler:** Konten, Einsätze, Wetten, Plinko-Kugeln und Crash-Plätze gehören immer einem Spieler.
Ein Automat dreht immer nur ein Spiel, aber ein Spieler kann **mehrere Automaten gleichzeitig** laufen
lassen. Roulette- und Crash-Runden sind gemeinsam, jeder sieht seine eigenen Jetons hervorgehoben.

### 7.1 Spielautomat „Minenfieber“

- 5 Walzen × 3 Reihen, 10 Linien, **Wild** = Totem, **Scatter** = Netherstern, **10 Freispiele** mit einem
  zufälligen **expandierenden Glücksitem** (Retrigger +10), Maximalgewinn 5 000× Einsatz.
- Gewinntabelle (× Linieneinsatz): Totem 10/200/2 000/10 000 · Netherit 10/100/1 000/5 000 ·
  Diamant 5/40/400/2 000 · Smaragd, Gold –/30/100/750 · Lapis, Eisen –/5/40/150 · Kupfer, Kohle –/5/25/100.
  Scatter zahlt 2/17/500 × Gesamteinsatz.
- Die Walzen und die Tabelle ergeben 99,998 %; ausgezahlt werden davon **96 %** jedes Gewinns, also
  **RTP 95,998 %** (exakt). Trefferquote 27,8 %, Freispiele etwa alle 151 Spins. Die Java-Mathematik ist
  gegen die Python-Rechnung geprüft, und der Server schreibt den RTP beim Start ins Log.
- **Bedienung:** Rechtsklick irgendwo auf den Automaten dreht mit dem aktuellen Einsatz; dabei wird der
  **Hebel** an der rechten Seite sichtbar gezogen (nach vorne unten, mit Klick) und federt zurück. **Item-Einsatz:** Rechtsklick mit einem
  Item in der Hand nennt den Wert, ein **zweiter Klick** innerhalb von 3 Sekunden setzt den ganzen Stack.
  Gewinne kommen dann **als dieses Item** zurück und schießen als **Fontäne** aus dem Automaten
  (bis 40 Stacks, der Rest geht als Jetons aufs Konto). Große Gewinne (≥ 50×) werden im Chat angekündigt.
- **Darstellung (nur am Automaten):** Die Walzen drehen sich als **Trommeln hinter dem Glas** und halten
  **von links nach rechts** an, mit Bremsen und Nachfedern, **Spannungsphase** bei 2 Scattern, leuchtenden
  Gewinnlinien und expandierenden Items. Die **Anzeige unter den Walzen** zeigt „VIEL GLÜCK“, den
  hochzählenden Gewinn, Freispiele mit Zähler oder „KEIN GEWINN“ und darunter deinen Einsatz. Das Ergebnis
  **bleibt stehen**, bis der Automat das nächste Mal gedreht wird.
- **Freispiele:** Vor den Walzen klappt eine Tafel auf, auf der die acht Items als Band durchlaufen; das
  Band wird langsamer (mit einem Klick pro Item) und hält im goldenen Rahmen auf dem **Glücksitem**, das
  aufblinkt („GLÜCK: Diamant breitet sich aus!“).
- **Ausbreiten:** Steht das Glücksitem auf genug Walzen, färben sich diese Walzen golden, und das Item
  **breitet sich Feld für Feld aus**: Von dem Feld, auf dem es steht, drehen sich die Felder darüber und
  darunter wie Karten um und kommen als Glücksitem wieder hoch, Walze für Walze. Danach laufen die zehn
  Linien, auf denen es zahlt, nacheinander über das Glas und leuchten zum Schluss alle zusammen. Das ist
  nur vorübergehend: Im nächsten Freispiel zeigen die Walzen wieder ihre eigenen Symbole.
- **Gewinnfeier in Stufen** (nach dem Vielfachen des Einsatzes, jede Stufe bringt die der vorigen mit):

  | Stufe | ab | Anzeige | Show |
  |---|---|---|---|
  | Großer Gewinn | 5× | Gold | Goldnugget-Fontäne aus der goldenen Kappe oben auf dem Automaten, Funken an der Anzeige |
  | Mega-Gewinn | 20× | Gold/Orange blinkend | dazu Smaragde, Diamanten, Goldbarren und Totem-Funkeln, zweite Fanfare |
  | Epischer Gewinn | 50× | Pink/Türkis blinkend | dazu Feuerwerk über dem Automaten |
  | Legendär | 200× | alle Farben | dazu eine Lichtspirale um den Automaten, Noten und goldenes Dauerfeuerwerk, zweite Fanfare |

  Münzgeklimper und Dauer wachsen mit der Stufe (1,5 bis 5,5 Sekunden). Jeder in der Nähe sieht die Show.

### 7.2 Plinko

- 12 Reihen Stifte, 13 Fächer; jede Reihe lenkt die Kugel mit 50 % nach links oder rechts.
  Multiplikatoren `30 · 10 · 3 · 2 · 1,3 · 0,5 · 0,3 · 0,5 · 1,3 · 2 · 3 · 10 · 30` →
  **RTP 96,001 %** (exakt 393 220 / 409 600).
- Das Brett ist 2 × 2 Blöcke groß. **Rechtsklick lässt eine Kugel** mit deinem Einsatz fallen; mehrere
  Kugeln (auch mehrerer Spieler) fallen gleichzeitig. Deine Kugeln sind golden, die der anderen rosa.
  Jeder Stift tickt leise, das getroffene Fach leuchtet auf. Unter dem Brett zeigt die Anzeige deinen
  Einsatz und dein letztes Ergebnis.

### 7.3 Crash

- Crash-Punkt `M = 0,96/U` (mindestens 1,00), also **P(M > x) = 0,96/x → 96 % RTP für jede Strategie**.
  4 % der Raketen platzen schon auf der Rampe. Der Multiplikator wächst mit `e^(0,07·s)`, der Deckel liegt
  bei 1 000× („ins All“, alle an Bord bekommen 1 000×).
- Die Station ist 2 Blöcke breit: links die Rampe, rechts ein **Pult mit Monitor**, großem Knopf und
  Jeton-Ablage. **Rechtsklick auf Knopf oder Rampe** setzt ein bzw. zahlt aus; `G` zahlt von überall aus.
- **Darstellung:** Die Rakete ist ein kleines **3D-Modell, aufrecht auf der Rampe** (sieht von allen Seiten
  gleich aus), zittert im Countdown, startet mit Donnern und steigt mit Funken und Rauchspur. Der
  **Monitor** zeigt Multiplikator, Flugkurve, Countdown, Mitspieler mit Ausstiegspunkten und die letzten
  Crash-Punkte; der Knopf ist rot („SETZEN“) oder pulsiert grün („AUSZAHLEN“).

### 7.4 Roulette

- Europäischer Kessel, klassische Quoten (Plein 35:1 … einfache Chancen 1:1), **die Null verliert**. Das
  ergibt 36/37 = 97,3 %; das Haus behält zusätzlich 1/75 jeder Auszahlung → **genau 96 %** für jede Wette.
- **Großer Tisch** (4 × 2 Blöcke) mit Kessel, Tableau, Jeton-Ablage, „ZURÜCK“-Schild und einer Tafel
  hinter dem Kessel (Countdown, gezogene Zahl, deine Jetons und Auszahlung, die letzten Zahlen).
- **Setzen direkt auf dem Filz:** Zielen zeigt, welche Wette es wäre, und hebt alle abgedeckten Zahlen
  hervor. Auf eine Zahl → Plein, auf die Linie zwischen zwei Zahlen → Cheval, auf eine Kreuzung → Carré,
  an den Rand über einer Spalte → Transversale, über einer Linie → Sixain; dazu Dutzende, Kolonnen und
  einfache Chancen. **Rechtsklick** legt den gewählten Jeton hin, **Linksklick** auf deine Jetons nimmt
  sie von dieser Stelle zurück, „ZURÜCK“ nimmt alle deine Jetons. Überall sonst baut Linksklick den
  Tisch normal ab (mit Schleichen auch über deinen Jetons).
- **Die Jetons liegen so da, wie du sie gesetzt hast:** Jeder Klick legt genau einen Jeton des gewählten
  Werts oben auf den Stapel (100K und dann 10 ergibt einen 100K-Jeton mit einem 10er obendrauf), der
  oberste trägt seinen Wert. Ein „Alles“-Einsatz liegt als die Jetons da, aus denen er sich zusammensetzt.
- Gemeinsame Runden: 25 Sekunden Einsätze, 8 Sekunden Kugellauf, Ergebnis. Die Kugel rattert über die
  Rauten und fällt ins richtige Fach, die Gewinnzahl blinkt auf dem Tableau.

---

## 8. Sounds und Grafiken

- **Sounds** (`scripts/casino/sound_mix.py`, 47 Dateien): echte Aufnahmen für Karten und Jetons
  (Kenney Boardgame Pack), Münzen (Kenney RPG Audio, StarNinjas) und Metall (Kenney Impact); synthetisiert
  sind Walzen, Hebel, Spannung, Fanfaren, Freispiel-Glissando, Kugellauf, Raketenstart, Explosion,
  Registrierkasse, Glocke, Unheil-Stinger, Wiederbelebung und Gong.
  Alles ist mono, getrimmt, hat Fades gegen Klicks, ist per RMS auf einheitliche Zielpegel gemischt, mit
  Limiter bei −1 dBFS und leichtem Raumhall für musikalische Cues. Alle Aufnahmen sind **CC0**.
- **Lautstärke und Ort:** Gerätesounds kommen **vom Gerät** (sie werden mit der Entfernung leiser und sind
  nach etwa 12–16 Blöcken weg) und sind deutlich leiser gemischt (Walzen 22 %, Stopps 35 %, sonst 45 %).
  Persönliche Klicks (Einsatz wählen) hört nur der jeweilige Spieler.
- **Grafiken** (`scripts/casino/make_textures.py`, `make_models.py`): 21 Blocktexturen als Pixel-Art im
  Minecraft-Stil, 4 Blockmodelle, der Croupier-Skin, der Beutel, das Roulette-Rad (hochaufgelöst),
  Filz sowie Karten- und Jeton-Atlas (Kenney Boardgame Pack, CC0).

---

## 9. Testen

Die Challenge ist Level 8 und steht in der Kategorie „Chaos“. Für schnelle Tests gibt es Admin-Befehle:

| Befehl | Wirkung |
|---|---|
| `/casino chips <Anzahl>` | Jetons gutschreiben (negativ: abziehen) |
| `/casino unlock` | alle vier Geräte freischalten und ins Inventar legen |
| `/casino fee` | nächste Gebühr sofort abbuchen |
| `/casino feescale <Prozent>` | Gebührenkurve skalieren |
| `/casino booth` | Stand am Spawn neu bauen |
| `/casino status` | Konten, Einsätze, Gebühr im Chat |
| `/casino slotforce <freespins\|big\|mega\|epic\|legendary>` | der nächste eigene Spin am Automaten bringt dieses Ergebnis (ein echter, ausgewürfelter Spin dieser Art) |

Beim Serverstart schreibt das Log `[Casino] RTP slot 95.9978 %, plinko 96.0010 %, roulette 96.0000 %,
crash 96.0000 %` und `[Casino] 1290 item values loaded`.

---

## 10. Was geprüft ist

Die Mod baut gegen 26.3 und wurde im echten Client (Software-OpenGL, ohne Soundkarte) gespielt: alle vier
Geräte platziert und gespielt, zwei Automaten gleichzeitig, mehrere Plinko-Kugeln, Crash über Knopf und
Taste, Roulette-Wetten auf dem Filz inklusive Auszahlung, Blackjack nach dem Tod, der Stand gegen TNT,
Wasser, Lava und Abbauen im Kreativmodus, die Kasse auf Englisch und Deutsch.

Dazu im Einzelspieler: Einzahlen am Tresen (wertlose Items und gefüllte Shulkerkisten kommen zurück,
Stapel kommt beim Ausloggen zurück) und Blackjack am Tresen (Gewinn und Verlust, Items liegen am Todesort).

Mehrspieler mit dediziertem Server und zwei Clients: Hinweis beim Beitreten, Roulette mit Einsätzen beider
Spieler, anteilige Gebühr, eine Crash-Runde mit zwei Spielern (einer zahlt aus, der andere bleibt drin),
mehrere Plinko-Kugeln. Noch offen im Mehrspieler: gleichzeitiges Einzahlen am Tresen und zwei
gleichzeitige Blackjack-Hände. Nicht geprüft: das **Hören** der Sounds (keine Soundkarte).
