# Konzept: „The House Always Wins“ (Challenge 50)

> Stand: Konzept v1, basierend auf Branch `26.3` (Minecraft 26.3, Mojmap, Fabric 0.161).
> Noch kein Code, nur Design, Zahlen und Integrationsplan. Alle Wahrscheinlichkeiten sind nachgerechnet,
> die Skripte liegen unter `scripts/casino/`.

---

## 1. Die Idee in drei Sätzen

Du spielst ganz normal Minecraft und bekommst alle Blöcke und Drops wie immer. Jedes Item hat aber einen
**Wert in Jetons** (übernommen aus ProjectE). Das Haus kassiert alle 20 Minuten eine **steigende Hausgebühr**,
also musst du Items beim Croupier eintauschen. Mit den Jetons kannst du spielen (Slots, Roulette, Crash),
um schneller an Items zu kommen. Wer Wetten verliert, bekommt Monster geschickt. Wer stirbt, verliert
25 % seiner Jetons und darf um sein Leben Blackjack spielen.

**Ziel:** wie immer den Enderdrachen besiegen. Die Welt bleibt danach dauerhaft spielbar, weil die Gebühr
bei einem Maximum stehen bleibt.

---

## 2. Die Kernschleife

```
   Welt spielen ──► Items sammeln ──► beim Croupier einzahlen ──► Jetons (Konto)
        ▲                                                           │
        │                                   ┌───────────────────────┼─────────────────────┐
        │                                   ▼                       ▼                     ▼
        │                           Hausgebühr (alle 20 min)   Spielen (Slots,       Auszahlen:
        │                           wird automatisch            Roulette, Crash)      Items kaufen
        │                           abgebucht                   RTP 100 %             zum selben Wert
        │                                   │                       │                     │
        │                   nicht bezahlt ──► Schulden & Inkasso    Verlust ──► Mobwelle  │
        └───────────────────────────────────────────────────────────────────────────────┘
                                   Tod ──► −25 % Jetons ──► Blackjack um die Wiederbelebung
```

Warum sollte man überhaupt spielen, wenn die Spiele im Schnitt nichts einbringen (RTP 100 %)?

1. **Umwandlung:** Beim Croupier kann man wertlosen Kram (Bruchstein, verrottetes Fleisch, Knochen)
   gegen Jetons tauschen und dafür nützliche Items kaufen (siehe 3.4). Das funktioniert auch ohne Spielen.
2. **Abkürzungen durch Varianz:** Ein guter Treffer bei Crash oder in den Freispielen bringt früh Diamanten
   oder Obsidian. Genau dieses „Nur noch ein Spin“-Gefühl soll die Challenge ausmachen.
3. **Druck durch die Gebühr:** Fehlen einem kurz vor der Abbuchung 800 Jetons, ist ein Crash-Einsatz
   mit Ziel 2x eine echte, spannende Entscheidung.

---

## 3. Wirtschaft

### 3.1 Jetons und der Jeton-Beutel (ein Inventarslot)

- Das Guthaben liegt **serverseitig auf dem Spielerkonto** (`CasinoSavedData`), nicht in Items. Dadurch kann
  man nichts duplizieren, nichts verlieren und nichts in Truhen stecken.
- Im Inventar liegt genau ein Item, der **Jeton-Beutel**. Er zeigt statt einer Stackzahl das Guthaben an
  (`950`, `12.4K`, `3.1M`) und wird damit „immer mehr“. Er kann nicht gedroppt oder dupliziert werden
  und wird automatisch wieder ausgeteilt, genau wie beim Würfel (`Chal_46_Dice.ensureDice`).
- Rechtsklick auf den Beutel öffnet eine kleine Kontoübersicht: Guthaben, Schulden, nächste Gebühr, Statistik.
- Intern wird in **Centi-Jetons** (1/100) gerechnet. Angezeigt werden ganze Jetons. So gehen bei
  Item-Einsätzen und Linienbruchteilen keine Rundungsverluste verloren und die 100 % RTP gelten wirklich.
- **Auszahlen** heißt: Jetons gegen echte Items beim Croupier tauschen (3.4).

### 3.2 Item-Werte: übernommen von ProjectE

Recherchiert im aktuellen ProjectE-Quellcode (`sinkillerj/ProjectE`, MIT-Lizenz, Datei
`pe_custom_conversions/defaults.json` + `metals.json`). ProjectE funktioniert so:

1. **~200 Basiswerte** sind fest vorgegeben (Auszug unten).
2. **Alles andere wird aus Rezepten berechnet** (Crafting, Ofen, Steinsäge, Schmiedetisch …):
   `Wert(Ergebnis) = Summe(Zutaten) / Anzahl`. Gibt es mehrere Rezepte, zählt das **günstigste**.
   Dadurch erzeugt Craften nie neuen Wert.
3. **Erze und Rohmaterialien sind wertlos** (`OreBlacklistMapper`, `RawMaterialsBlacklistMapper`).
   Man muss erst schmelzen. So bringt Glück (Fortune) keinen Wert-Exploit.
4. **Verzauberungen** erhöhen den Wert: `+5000 / Seltenheitsgewicht × Stufe` pro Verzauberung.
5. **Beschädigte Werkzeuge** verlieren anteilig an Wert: `Wert × (1 − Schaden/Max)`.
6. Kein Umtauschverlust beim Kaufen oder Verkaufen (ProjectE-Standard `covalenceLoss = 1.0`).

Für die Challenge übernehmen wir die Basiswerte 1:1 als Java-Tabelle (mit MIT-Hinweis) und schreiben einen
kleinen Rezept-Auflöser, der beim Serverstart läuft und über die Rezepte von 26.3 iteriert, bis sich nichts
mehr ändert. So stimmen die Werte automatisch auch für neue Items der Version.

| Item | Jetons | | Item | Jetons |
|---|---:|---|---|---:|
| Bruchstein, Erde, Sand, Netherrack | 1 | | Kohle | 128 |
| Kies | 4 | | Kupferbarren | 128 |
| Planke | 8 | | Eisenbarren | 256 |
| Stock | 4 | | Goldbarren | 2 048 |
| Stamm (alle Holzarten) | 32 | | Lapislazuli | 864 |
| Fackel | 33 | | Redstone | 64 |
| Weizen | 24 | | Diamant | 8 192 |
| Brot | 72 | | Smaragd | 16 384 |
| Rohes Fleisch (Rind, Schwein …) | 64 | | Netheritbarren | 57 344 |
| Verrottetes Fleisch | 32 | | Obsidian | 64 |
| Knochen | 144 | | Enderperle | 1 024 |
| Faden | 12 | | Lohenrute | 1 536 |
| Schwarzpulver | 192 | | Enderauge | 1 792 |
| Leder | 64 | | Eisenspitzhacke | 776 |
| Wolle | 48 | | Diamantspitzhacke | 24 584 |
| Rohes Eisen / Eisenerz | **0** (erst schmelzen) | | Netherstern | 139 264 |

### 3.3 Der Croupier (Casino-Dealer)

- Ein NPC mit eigenem Skin (Anzug, Fliege, Schirmmütze). Er ist ein humanoides Modell, gerendert wie ein
  Spieler, also **kein Modell-Aufwand**, nur eine 64×64-Skin-Textur.
- **Wo ist er?** Jeder Spieler bekommt zum Start eine **Tischglocke**. Rechtsklick lässt den Croupier in
  einer Rauchwolke neben dir erscheinen. Er bleibt 60 Sekunden oder bis du dich entfernst. Cooldown:
  3 Minuten. Damit funktioniert die Challenge auch zusammen mit Würfel, Kissen oder Only Down, wo man nicht
  zu einem festen Casino laufen kann.
- **Schnell einzahlen ohne Menü:** Schleich-Rechtsklick mit einem Item in der Hand zahlt den ganzen Stack ein.
  Über dem Kopf des Croupiers fliegen Jetons hoch, dazu ein Kassen-Sound.
- **Kassen-Menü** (normaler Rechtsklick):
  - *Einzahlen:* 27 Slots, der Wert wird live angezeigt, wertlose Items werden rot markiert. Button „Einzahlen“:
    Die Items fliegen in die Kasse, der Zähler rollt hoch wie ein Kilometerzähler.
  - *Auszahlen:* Durchsuchbares Raster **aller Items, die du schon einmal eingezahlt hast** (wie das
    „Wissen“ beim ProjectE-Transmutationstisch). Kaufen mit ×1, ×16, ×64 oder Max. Nicht kaufbar sind unter
    anderem Drachenei, Spawn-Eier, Kommandoblöcke, Grundgestein und Challenge-Items.
  - *Casino-Shop:* Hier kauft man die Spielgeräte (Kapitel 5). Das ist die erste Anschaffung im Run.

### 3.4 Warum „nur schon bekannte Items kaufen“?

Ohne diese Regel könnte man mit dem ersten Crash-Glückstreffer sofort Netherit kaufen, ohne je im Nether
gewesen zu sein. Mit der Regel muss man ein Item erst einmal selbst gefunden und eingezahlt haben. Danach
darf man es nachkaufen. Die Varianz beschleunigt den Run also, überspringt ihn aber nicht.

---

## 4. Die Hausgebühr

### 4.1 Rhythmus

- **Alle 20 Minuten Laufzeit** (Run-Timer, nicht Tageszeit) wird die Gebühr automatisch vom Konto abgebucht.
  Schlafen beschleunigt also nichts, und Pausieren im Einzelspieler stoppt den Zähler.
- Der HUD-Stack (`HudStack`) bekommt eine **Casino-Karte**: Guthaben (rollender Zähler), Countdown bis zur
  nächsten Gebühr, Höhe der Gebühr und Schulden (rot pulsierend).
- 60 Sekunden vor der Abbuchung erklingt eine Glocke. Bei der Abbuchung ertönt ein „Cha-Ching“ und es
  erscheint ein Toast „Das Haus kassiert: 4 384“.

### 4.2 Skalierung: S-Kurve mit festem Deckel

Die Gebühr wächst am Anfang langsam, in der Mitte des Runs schnell und läuft dann gegen einen festen
Deckel **C**. Sie wird nie „unendlich unerreichbar“:

```
Gebühr(n) = C / (1 + (7 / (n − 1))³)     für die n-te Periode (n ≥ 2), gerundet auf 16
Periode 1 ist gebührenfrei (Eingewöhnung).
C = 16 384 (Normal)   ≈ 2 Diamanten bzw. 1 Smaragd pro 20 Minuten
```

| Periode | Laufzeit | Gebühr | in Items etwa | Summe bisher |
|---:|---|---:|---|---:|
| 1 | 0–20 min | 0 | – | 0 |
| 2 | 20–40 min | 48 | 1½ Stämme | 48 |
| 3 | 40–60 min | 368 | 1½ Eisen | 416 |
| 4 | 1:00–1:20 | 1 200 | 5 Eisen | 1 616 |
| 5 | 1:20–1:40 | 2 576 | 1 Gold + 2 Eisen | 4 192 |
| 6 | 1:40–2:00 | 4 384 | 5 Lapis | 8 576 |
| 7 | 2:00–2:20 | 6 336 | 4 Lohenruten | 14 912 |
| 8 | 2:20–2:40 | 8 192 | 1 Diamant | 23 104 |
| 10 | 3:00–3:20 | 11 136 | 1⅓ Diamanten | 44 048 |
| 12 | 3:40–4:00 | 13 024 | 1½ Diamanten | 69 264 |
| 15 | 4:40–5:00 | 14 560 | | 111 664 |
| 20+ | ab 6:20 | ~15 600 → 16 384 | 2 Diamanten | |

**Einschätzung:** Ein normaler Drachen-Run (3–5 h) kostet insgesamt etwa 45–110 k Jetons, also etwa
5–13 Diamanten an Wert. Das spürt man, es ist aber gut machbar, und nach dem Drachen bleibt es konstant.
Für die Challenge-Auswahl gibt es einen **Regler „Hausgebühr“**: *Niedrig* (C = 8 192), *Normal*
(16 384), *Hoch* (32 768), *Wucher* (65 536). Dafür wird ein neuer Eintrag in
`ChallengeCode.TUNABLE_IDS` angehängt (nur anhängen, nie umsortieren).

Im Mehrspieler hat jeder Spieler sein eigenes Konto und zahlt seine eigene Gebühr. Überweisungen zwischen
Spielern gehen über den Croupier.

### 4.3 Nicht bezahlt: Schulden und Inkasso

- Reicht das Guthaben nicht, bucht das Haus alles ab und der Rest wird zu **Schulden** (+10 % Zinsen pro Periode).
- Solange man Schulden hat:
  - Das **Inkasso** kommt: Alle 3 Minuten erscheint ein Trupp aus Vindicators und Pillagern. Die Größe
    richtet sich nach der Schuld im Verhältnis zur aktuellen Gebühr (max. 6 Mobs).
  - **Auszahlen ist gesperrt**, spielen darf man weiter (verzweifeltes Zocken ist gewollt).
  - Jede Einzahlung tilgt zuerst die Schulden.
- Die Schulden bringen nie direkt den Tod. Sie sorgen für Druck durch Kämpfe, und die Gebühr ist gedeckelt.
  Eine Abwärtsspirale ohne Ausweg gibt es also nicht.

---

## 5. Verlorene Wetten: „Das Haus schickt Grüße“

Deine Regel: Wer verliert, bekommt Monster, und je mehr man verliert, desto schlimmer.
Umsetzung mit einem **Pechkonto**:

- Jeder verlorene Einsatz landet auf dem Pechkonto, Gewinne in derselben Sitzung ziehen ihn wieder ab.
  Es zählt also der **Nettoverlust**. Ohne diese Regel würde jeder verlorene Slot-Spin (~72 % der Spins)
  eine Welle auslösen und das Spiel würde im Mob-Spam untergehen.
- **Ausgelöst** wird die Welle, sobald man sich vom Gerät entfernt oder spätestens 60 Sekunden nach dem
  ersten Verlust. Sie erscheint **um den Spieler herum**, 5–10 Blöcke entfernt, mit Rauch- und Portal-Effekt,
  goldenen Jeton-Partikeln und dem Titel *„Das Haus schickt Grüße.“*
- Die **Stärke** hängt vom Nettoverlust **relativ zur aktuellen Hausgebühr R** ab (mindestens 256). Dadurch
  skaliert die Strafe automatisch mit dem Fortschritt:

| Nettoverlust / R | Welle | Beispiel-Mobs |
|---|---|---|
| < 5 % | keine | – |
| 5–25 % | Stufe 1: 2 Mobs | Zombie, Skelett, Spinne |
| 25–100 % | Stufe 2: 3–4 Mobs | + Creeper, Husk, Stray |
| 100–400 % | Stufe 3: 5–6 Mobs | + Hexe, Vindicator, Pillager |
| > 400 % | Stufe 4: 6–8 Mobs | + Evoker, 1 Ravager (im Nether: Piglin-Brutes, Lohen) |

- **Nie** gespawnt werden: Wither, Warden, Enderdrache, Großer Wächter.
- Casino-Mobs **droppen nichts und geben keine XP**. Sonst würde eine verlorene Wette Vindicators
  bringen, die Smaragde (16 384 Jetons) droppen, und aus der Strafe würde ein Gewinn.

---

## 6. Tod: 25 % Verlust und Blackjack um das Leben

1. **Allgemeine Regel:** Bei jedem Tod gehen **25 % des Guthabens** an das Haus (Schulden bleiben unverändert).
2. Statt des Todesbildschirms erscheint **„Das Haus bietet dir ein Spiel an.“** Technisch wird der Tod über
   Fabrics `ServerLivingEntityEvents.ALLOW_DEATH` abgefangen, ohne Mixin. Der Spieler wird an Ort und Stelle
   unverwundbar und unsichtbar eingefroren.
3. Es folgt **eine Hand Blackjack** gegen den Croupier:
   - **Gewonnen:** Respawn am Spawnpunkt **mit dem kompletten Inventar**, dazu Totem-Animation und -Sound.
   - **Verloren:** Der Tod passiert ganz normal an der ursprünglichen Stelle, Items droppen dort, danach
     kommt der normale Todesbildschirm.
   - **Unentschieden (Push):** Es wird neu gegeben.
   - Man darf Hit, Stand, Double und Split spielen. Bei einem Split zählt die Summe aller Hände
     (positiv = überlebt, 0 = neue Hand).
   - Pro Entscheidung gibt es 20 Sekunden, danach wird automatisch gestanden. So hält niemand eine
     Mehrspieler-Runde auf.
4. **Überlebenschance:** etwa **47,7 %** (simuliert, Pushes neu gegeben). Das ist eine echte Münzwurf-Spannung.
5. **Bonus:** Wer mit einem **Blackjack oder einem gewonnenen Double** überlebt, bekommt die 25 % zurück.
   *(Offene Frage, siehe 11.)*

---

## 7. Die Spiele

Alle Zufallsergebnisse entstehen **auf dem Server**. Der Client bekommt nur das Ergebnis und animiert dorthin.
Beim Crash wird der Crash-Punkt **erst im Moment des Crashs** gesendet, damit man ihn nicht vorher auslesen kann.

### 7.1 Slots: „Minenfieber“ (5 Walzen × 3 Reihen, 10 Linien)

Ein richtiger Video-Slot im Stil der „Book of …“-Automaten, mit Minecraft-Items als Symbolen.

**Symbole und Gewinntabelle** (Vielfache des **Linien**-Einsatzes, Gesamteinsatz = 10 Linien):

| Symbol | 2× | 3× | 4× | 5× |
|---|---:|---:|---:|---:|
| **Totem der Unsterblichkeit** (Wild, ersetzt alles außer Scatter) | 10 | 200 | 2 000 | 10 000 |
| Netheritbarren | 10 | 100 | 1 000 | 5 000 |
| Diamant | 5 | 40 | 400 | 2 000 |
| Smaragd | – | 30 | 100 | 750 |
| Goldbarren | – | 30 | 100 | 750 |
| Lapislazuli | – | 5 | 40 | 150 |
| Eisenbarren | – | 5 | 40 | 150 |
| Kupferbarren | – | 5 | 25 | 100 |
| Kohle | – | 5 | 25 | 100 |
| **Netherstern** (Scatter, zahlt überall, × **Gesamt**einsatz) | – | 2 + Freispiele | 17 + Freispiele | 500 + Freispiele |

- **Linien:** Gewinne zählen von links nach rechts. Es gibt 10 feste Linien: 3 gerade, 2 V-Formen,
  5 Zickzack.
- **Wild:** Das Totem ersetzt jedes Item-Symbol und hat eigene Gewinne. Pro Linie wird der höhere Gewinn gezahlt.
- **Freispiele:** Ab 3 Netherstern-Scattern gibt es **10 Freispiele**. Davor wird per Glücksrad eines der
  8 Items zum **expandierenden Glückssymbol** bestimmt. Landet es in einem Freispiel auf genügend Walzen
  (Netherit/Diamant ab 2, sonst ab 3), füllt es die ganzen Walzen aus und zahlt auf **allen 10 Linien**,
  auch ohne dass die Walzen nebeneinander liegen. **Retrigger:** 3 Scatter in den Freispielen geben +10 Spins.
- **Maximalgewinn:** 5 000 × Gesamteinsatz pro Spin inklusive Feature.

**Mathematik** (exakt berechnet über jede Stopp-Position aller fünf Walzen und per Monte Carlo mit
1,2 Mio. Spins gegengeprüft, Skript `scripts/casino/slot_final.py`):

| Kennzahl | Wert |
|---|---|
| **RTP gesamt** | **99,998 %** (exakt 92754935089733 / 92757105698400) |
| davon Liniengewinne | 55,5 % |
| davon Scatter-Gewinne | 2,1 % |
| davon Freispiele | 42,4 % |
| Trefferquote (irgendein Gewinn) | 27,8 % der Spins |
| Freispiele im Schnitt alle | 151 Spins |
| Ø Länge einer Freispiel-Runde | 10,7 Spins |
| Ø Wert einer Freispiel-Runde | ≈ 64 × Einsatz |
| Volatilität (σ pro Spin) | ≈ 10,3 × Einsatz, also mittel bis hoch wie echte „Book“-Slots |
| Walzenlängen | 65 / 66 / 66 / 66 / 65 Symbole |

**Dein Item-Einsatz („Holz rein, Inventar voll“):**
Mit einem Item in der Hand rechtsklickt man den Automaten. Der ganze Stack wird zum Einsatz
(Wert = ProjectE-Wert × Anzahl), und **Gewinne werden im selben Item ausgezahlt**. Sie schießen als
**Item-Fontäne** aus dem Automaten, der Rest geht als Jetons aufs Konto.
Beispiel mit **1 Stamm** (32 Jetons) als Einsatz:

| Ergebnis | Gewinn |
|---|---|
| 3 Diamanten auf einer Linie | 4 Stämme |
| 5 Diamanten | 200 Stämme (≈ 3 Stacks) |
| 5 Netherit | 500 Stämme (≈ 8 Stacks) |
| Freispiele mit Netherit als Glückssymbol auf 5 Walzen | bis 5 000 Stämme, **mehr als ein volles Inventar** (2 304) |

Deine Beispiele („3 Diamanten = 3 Stacks“) wären bei 100 % RTP zu großzügig. Bei den häufigen Treffern
muss der Gewinn klein sein, damit die seltenen richtig groß sein können.

### 7.2 Roulette: klassisch und trotzdem 100 %

Europäischer Kessel mit 37 Fächern (0–36). Alle üblichen Wetten (Plein, Cheval, Transversale, Carré,
Sixain, Dutzend, Kolonne, Rot/Schwarz, Gerade/Ungerade, Manque/Passe) mit den **normalen Casino-Quoten**.

**Hausregel für 100 % RTP:** Fällt die **0**, bekommt **jeder Einsatz, der nicht auf der 0 liegt, seinen
Einsatz zurück**. Ein Plein auf die 0 zahlt 36:1 statt 35:1.

Nachgerechnet: Eine Wette auf k Zahlen gewinnt mit k/37 das 36/k-fache und bekommt mit 1/37 den Einsatz zurück,
also `36/37 + 1/37 = 100 %` für **jede** Wettart.

| Wette | Gewinnchance | Auszahlung (inkl. Einsatz) | RTP |
|---|---:|---:|---:|
| Plein (1 Zahl) | 2,7 % | 36× | 100 % |
| Cheval (2) | 5,4 % | 18× | 100 % |
| Transversale (3) | 8,1 % | 12× | 100 % |
| Carré (4) | 10,8 % | 9× | 100 % |
| Sixain (6) | 16,2 % | 6× | 100 % |
| Dutzend / Kolonne (12) | 32,4 % | 3× | 100 % |
| Einfache Chance (18) | 48,6 % (+2,7 % Push) | 2× | 100 % |

**Welche Rolle hat Roulette?** Es ist das Spiel mit dem **wählbaren Risiko**: Auf Rot/Schwarz
verdoppelt man fast sicher-unsicher, auf Plein landet man den großen Treffer. Außerdem ist es das
**Mehrspieler-Spiel**, bei dem alle am Tisch auf denselben Wurf setzen. Einen Weltmodifikator gibt es nicht.

*Optionaler Twist zur Entscheidung:* **„Doppelt oder nichts“ auf die Hausgebühr.** In den letzten
60 Sekunden vor der Abbuchung darf man die kommende Gebühr auf eine einfache Chance setzen.
Gewinn: Die Gebühr fällt weg. Verlust: Sie wird doppelt fällig. Erwartungswert neutral, aber sehr dramatisch.

### 7.3 Crash: „Die Rakete“ (klassisch)

- Verteilung wie bei den bekannten Crash-Spielen, nur ohne Hausvorteil: Der Crash-Punkt ist `M = 1/U`
  mit U gleichverteilt in (0, 1]. Damit ist **P(M ≥ x) = 1/x**.
  Wer bei x aussteigt, bekommt `x · 1/x = 1`, also **exakt 100 % RTP für jede Strategie**.
- Der Multiplikator wächst mit `e^(0,07·t)`. Deckel bei 1 000×: Die Rakete „erreicht das All“ und alle,
  die noch dabei sind, werden automatisch mit 1 000× ausgezahlt (EV bleibt 100 %).

| Aussteigen bei | Chance | Flugzeit bis dahin |
|---:|---:|---:|
| 1,5× | 66,7 % | 5,8 s |
| 2× | 50 % | 9,9 s |
| 5× | 20 % | 23 s |
| 10× | 10 % | 33 s |
| 100× | 1 % | 66 s |
| 1 000× | 0,1 % | 99 s |

- Jede Runde hat 10 Sekunden Einsatzphase mit Countdown. Danach wird per Rechtsklick auf die Startrampe
  (oder Taste) ausgestiegen. Optional gibt es ein Auto-Cashout-Ziel. Im Mehrspieler fliegen alle in derselben
  Runde, und man sieht, wer bei welchem Wert aussteigt („Kasax 2,31×“).

### 7.4 Blackjack

Hauptrolle ist die **Wiederbelebung** (Kapitel 6). Die gleiche Engine kann optional auch als
normaler Jeton-Tisch laufen (kaum Mehraufwand).

**Regeln** (so gewählt, dass der RTP bei Basisstrategie bei ~100 % liegt), simuliert mit je 20 Mio. Händen
(`scripts/casino/bj_sim.py`):

| Regelwerk | RTP (95 %-Intervall ±0,05) |
|---|---:|
| 6 Decks, Dealer steht auf Soft 17, Double nach Split, Late Surrender (übliches Casino) | 99,66 % |
| 2 Decks, S17, DAS, Late Surrender | 99,86 % |
| 1 Deck, Dealer **zieht** auf Soft 17, DAS | 99,95 % |
| 1 Deck, S17, DAS | 100,12 % (Vorteil für den Spieler) |
| **✅ 1 Deck, S17, kein Double nach Split, keine Surrender, BJ zahlt 3:2** | **99,99 %** |

Gewählt wird die letzte Zeile. Mischen nach jeder Hand (wie eine Mischmaschine), damit Kartenzählen nichts bringt.
Der Dealer schaut bei Ass oder Zehn nach Blackjack (Peek). Splits bis 4 Hände, Asse nach Split bekommen nur eine Karte.

---

## 8. Das UI-Problem: „Ich will nicht, dass man nur im Menü gammelt“

Deine Sorge ist berechtigt. Mein Lösungsvorschlag besteht aus drei Teilen:

**A) Die Spiele stehen in der Welt, nicht in Menüs.**
Die Geräte sind Blöcke, die man beim Croupier kauft und in die eigene Basis stellt. Die Basis wird so
nach und nach zum eigenen Casino, und das ist gleichzeitig ein Progressionsziel.

| Gerät | Kosten | So spielt man es |
|---|---:|---|
| **Spielautomat** (1×2 Blöcke) | 1 024 | Die Walzen laufen **auf der Vorderseite des Blocks** (Block-Entity-Renderer mit Item-Icons). Man schaut den Automaten an, Einsatz per Schleichen + Mausrad, Spin per Rechtsklick auf den Hebel. Eine schmale HUD-Leiste zeigt Einsatz, Guthaben und letzten Gewinn. |
| **Roulette-Tisch** (2×2) | 2 048 | Der Kessel dreht sich **auf dem Tisch**, die Kugel springt sichtbar. Man setzt, indem man auf das Feld auf dem Filz schaut und rechtsklickt. **3D-Jeton-Stapel** liegen dann auf dem Tisch. |
| **Crash-Startrampe** | 1 024 | Eine **echte Rakete** startet aus dem Block in den Himmel, mit Rauchspur. Der Multiplikator schwebt als großer Text darüber. Beim Crash explodiert sie als Feuerwerk. |
| **Blackjack** | – | Einziges Vollbild-Menü, und das nur, während man tot ist. |

**B) Die Zeit läuft weiter.** Kein Menü pausiert. Wer am Automaten steht, steht in der Welt, und die
Monster aus der letzten Pechwelle stehen direkt daneben. Spielen hat damit ein echtes Risiko.

**C) Bei 100 % RTP lohnt sich Dauerzocken nicht.** Die Gebühr bezahlt man aus dem, was man draußen
sammelt. Spielen ist Würze und Abkürzung, keine Einkommensquelle. Erfahrungsgemäß führt das zu kurzen
Zock-Phasen („noch 5 Spins vor der Abbuchung“) statt zu Dauersitzungen.

**Modell-Aufwand:** Alles ist mit dem machbar, was die Mod schon kann. Es gibt Blockmodelle als JSON-Quader,
Block-Entity-Renderer wie beim Würfel (`DiceEntityRenderer`), eine Rakete als Entity mit
Feuerwerksraketen-Item-Render und den Croupier als humanoides Modell mit eigenem Skin. Aufwendige Tiermodelle
braucht es nicht.

---

## 9. Design: Sound, Animation, Optik

**Sound:** Die Mod komponiert ihre Sounds bisher aus Vanilla-Noten (`SoundCues`). Für ein gutes Casino-Gefühl
schlage ich zusätzlich **echte Aufnahmen** vor, und zwar lizenzfrei:
[Kenney „Casino Audio“](https://kenney.nl/assets/casino-audio) (CC0, 54 OGG-Dateien: Karten mischen/ziehen,
Jetons stapeln/werfen, Würfel). Nennung nicht nötig, keinerlei Lizenzsorgen. Jingles und Fanfaren (Gewinn,
Big Win, Freispiel-Musik) komponieren wir weiter aus Notenblock-Instrumenten wie bei `SoundCues`, damit sie
zum Stil der Mod passen. Dafür ist ein neues `sounds.json` mit eigenen `SoundEvent`s nötig; bisher liefert
die Mod keine eigenen Sounddateien aus.

**Slots**
- Die Walzen starten versetzt (je 0,1 s) mit Bewegungsunschärfe und stoppen von links nach rechts mit leichtem
  Nachfedern (`easeOutBack`), jede mit einem dumpfen „Klonk“.
- **Spannung:** Liegen schon 2 Scatter, drehen die restlichen Walzen langsamer und länger weiter, dazu ein
  steigender Ton.
- Gewinnlinien leuchten als farbige Linien über den Walzen. Der Gewinn zählt beschleunigt hoch, begleitet von
  Jeton-Klackern.
- Gewinnstufen: *Big Win* ab 20×, *Mega Win* ab 50×, *Epic Win* ab 100×. Dazu Feuerwerkspartikel aus dem
  Automaten, ein Titel und eine Fanfare.
- Freispiele: Lauflichter auf dem Automatenschild, ein Glücksrad wählt das Symbol, eine eigene Musikschleife.
  Das Expandieren sieht aus, als würde das Item-Icon die ganze Walze hochwachsen.
- Item-Einsatz: Beim Gewinn **spritzen die Items als Fontäne** aus dem Automaten.

**Roulette:** Der Kessel dreht sich, die Kugel läuft gegen die Drehrichtung, wird langsamer, springt über
die Rauten und fällt in das vom Server bestimmte Fach. Die Gewinnzahl leuchtet auf dem Filz. Verlorene
Jetons werden abgeräumt (Slide-Animation), Gewinne stapeln sich daneben. Eine Leiste zeigt die letzten 12 Zahlen.

**Crash:** Countdown mit Ticken, Start mit Feuerwerk-Startsound, Rauchspur. Im HUD wächst eine Kurve.
Die Zahl wird größer und wechselt die Farbe (weiß → grün → gold → lila). Beim Aussteigen gibt es Münzregen-Sound,
beim Crash eine große rote Feuerwerksexplosion, einen Knall und den Titel „CRASH @ 3,47×“.
Eine Leiste zeigt die letzten 10 Crash-Werte farbig.

**Blackjack:** Der Bildschirm blendet auf grünen Filz über, oben ist das Porträt des Croupiers. Karten
gleiten aus dem Schlitten (Flick-Sound), das Umdrehen ist als Skalierungsanimation gebaut. Die Grafiken sind
Kenney-Spielkarten (CC0). Ein Zeitbalken läuft für jede Entscheidung. Bei einem Sieg gibt es goldenes Licht,
Totem-Partikel und Totem-Sound. Bei einer Niederlage zerfallen die Karten und der normale Todesbildschirm folgt.

**Kasse / HUD:** Kilometerzähler-Animation beim Guthaben, Items fliegen in die Kasse. Alles nutzt die
Farben und Bausteine aus `CraftUI` (Gold `0xFFE3B35A` als Casino-Akzent), damit es zum Rest der Mod passt.

**Jeton-Farben** (wie im echten Casino, auch für die 3D-Stapel): 1 weiß · 5 rot · 25 grün · 100 schwarz ·
500 lila · 1K gelb · 5K braun · 25K hellblau.

---

## 10. Integration in den Code (Stand `26.3`)

Challenge-ID **50**. Die Checkliste stammt aus der Analyse, wie die Challenges 46–49 eingebunden sind:

| Datei | Änderung |
|---|---|
| `challenges/Chal_50_HouseAlwaysWins.java` | neu: `register`, `setActive`, Tick (Gebühr, Inkasso, Pechkonto) |
| `challenges/casino/*` | neu: `EmcValues` (ProjectE-Tabelle + Rezept-Auflöser), `CasinoAccount`, `SlotMath`, `RouletteMath`, `CrashMath`, `BlackjackEngine`, `LossWaveSpawner` |
| `data/CasinoSavedData.java` | neu, nach dem Vorbild von `ForceItemBattleSavedData`: Konten, Schulden, Wissen, Statistik |
| `ChallengeManager` | Schwierigkeit `case 50`, Einträge in `CONFLICTS`, `getActiveIds`, `setAllActive`, Aktivierungs-`switch`, Sync beim Beitritt |
| `LevelManager.getRequiredLevel` | 50 → **Level 8** (Vorschlag) |
| `ChallengeBrowser.categoryOf` | 50 → `CHAOS` |
| `ChallengeTab` + `ChallengeSelectionScreen` | ID in die Reihenfolge-Arrays eintragen |
| `ChallengeIconProvider` | `ICONS.put(50, ModItems.CASINO_CHIP)` |
| `ChallengeRewardOverlay:173`, `LevelingScreen:585` | ⚠️ Schleifen `id <= 49` fest verdrahtet, auf 50 anheben (am besten eine Konstante `MAX_CHALLENGE_ID`) |
| `code/ChallengeCode` | Regler „Hausgebühr“ an `TUNABLE_IDS` **anhängen** |
| `daily/DailyChallenges` | erstmal nicht in den Daily-Pool, später nach dem Balancing |
| `ChallengeCraft` / `ChallengeCraftClient` | Registrierung, Payloads, HUD-Quelle, Renderer, Reset beim Trennen |
| `network/` | `CasinoSyncPacket`, `CasinoActionPacket` (C2S), `SlotResultPacket`, `RouletteStatePacket`, `CrashStatePacket`, `BlackjackStatePacket`, `CashierOpenPacket` |
| `item/ModItems` | Jeton-Beutel, Tischglocke, Block-Items |
| `block/` | `SlotMachineBlock`, `RouletteTableBlock`, `CrashPadBlock` + Block-Entities + Datagen (Modelle, Loot) |
| `entity/ModEntities` | `CroupierEntity`, `CasinoRocketEntity` |
| Mixin | Guthaben-Anzeige statt Stackzahl am Beutel; Drops und XP der Casino-Mobs unterdrücken |
| `client/SoundCues` + `assets/.../sounds.json` + `sounds/*.ogg` | neue Cues und Kenney-Sounds |
| `lang/en_us.json` + `lang/de_de.json` | Name, Beschreibung, alle UI-Texte |
| `network/RunSummary` | Casino-Statistik: größter Gewinn, Einsatz gesamt, Netto, bezahlte Gebühren, Wiederbelebungen |
| `client/tutorial` | kurze Einführung beim ersten Start (Croupier, Gebühr, Geräte) |

**Schwierigkeit (Vorschlag): 2,5.** Die Gebühr, die Mobwellen und die 25 % sind harte Strafen. Dem stehen
Vorteile gegenüber: eine zweite Chance beim Tod (~48 %) und die Umwandlung von Items. Zum Vergleich:
Floor is Lava 2,5, Skyblock 2,5.

**Konflikte:**
- **Lockout Bingo (40)** und **Force Item Battle (45):** Das sind Minispiele, und bei Force Item könnte man
  das Ziel-Item einfach kaufen.
- **Hardcore (21):** Kein Konflikt, aber **keine Wiederbelebung per Blackjack**, sonst wäre Hardcore keins mehr.
  *(siehe offene Fragen)*
- **Limited Inventory (12):** Kein Konflikt. Der Beutel belegt einen der knappen Slots, das ist gewollt härter.

---

## 11. Offene Entscheidungen (bitte kurz durchgehen)

1. **Rohe Erze wertlos wie in ProjectE?** Empfehlung: **ja**, erst schmelzen. Das verhindert den Fortune-Exploit.
2. **Nur „bekannte“ Items kaufbar?** Empfehlung: **ja** (siehe 3.4).
3. **Roulette:** nur klassisch, oder zusätzlich „Doppelt oder nichts“ auf die Hausgebühr (7.2)?
4. **Blackjack auch als normaler Jeton-Tisch?** Kostet kaum Mehraufwand.
5. **Hardcore + Blackjack:** Wiederbelebung deaktivieren (Empfehlung) oder als einzige Rettung erlauben?
6. **25 % Todesstrafe auch bei gewonnenem Blackjack?** Deine Regel sagt „generell“, also ja. Vorschlag:
   Ein Sieg mit Blackjack oder Double erstattet die 25 %.
7. **Einsatzlimits:** frei (All-in erlaubt) oder max. 50 × aktuelle Gebühr (mind. 5 000)?
8. **Herkunft der Geräte:** beim Croupier kaufen (Empfehlung), craften oder ein festes Casino am Spawn?
9. **Mehrspieler:** getrennte Konten (Empfehlung) oder ein gemeinsamer Team-Topf?
10. **Gebühren-Deckel „Normal“ = 16 384:** passt das Gefühl, oder lieber strenger?

---

## 12. Umsetzungsreihenfolge

Jede Phase ist einzeln spielbar und testbar:

1. **Wirtschaftskern:** Werte (ProjectE), Konto, Jeton-Beutel, Croupier und Kasse, Hausgebühr, Schulden,
   Inkasso, 25 % beim Tod, HUD-Karte.
2. **Slots:** Mathematik, Automat in der Welt, Freispiele, Item-Einsatz mit Fontäne, Effekte.
3. **Crash:** gemeinsame Runden, Rakete, Rampe.
4. **Roulette:** Tisch, Kessel und Kugel, Einsatzfelder.
5. **Blackjack-Wiederbelebung.**
6. **Feinschliff:** Sounds, Run-Zusammenfassung, Tutorial, Übersetzungen, Balancing-Durchgang.

---

### Anhang: Nachweise

- ProjectE-Werte: `github.com/sinkillerj/ProjectE`, `src/datagen/generated/data/projecte/pe_custom_conversions/defaults.json`
  und `metals.json`, Regeln in `emc/mappers/OreBlacklistMapper.java`, `RawMaterialsBlacklistMapper.java`,
  `emc/components/processor/EnchantmentProcessor.java`, `DamageProcessor.java`.
- `scripts/casino/slot_math.py`: exakte RTP-Berechnung (Brüche) und Monte-Carlo-Simulation für den Slot.
- `scripts/casino/slot_final.py`: die finale Walzen- und Gewinnkonfiguration. Nach jeder Änderung neu laufen lassen.
- `scripts/casino/bj_sim.py`: Blackjack-Simulation mit Basisstrategie für beliebige Regelwerke.
- Roulette und Crash: analytisch, Rechenwege stehen oben im Text.
