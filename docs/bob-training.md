# Bob trainieren – Anleitung für den Laptop

Diese Anleitung beschreibt den Trainingsprozess für Bob. Du startest ihn auf dem Laptop mit einem
Doppelklick. Er läuft, bis du ihn stoppst, und nutzt so viele Minecraft-Server gleichzeitig, wie der
Rechner gerade stabil trägt.

## Was der Prozess macht

Er deckt die fünf besprochenen Punkte ab:

1. **Laptop einrichten und messen.** Beim ersten Start vermisst er den Rechner: wie viele Server mit
   Arenen gleichzeitig laufen und wie schnell. Das Ergebnis steht in `runs\train\calibration.md`.
   Danach richtet sich die Obergrenze der Server.
2. **Datenbrücke.** Bob schreibt jede Entscheidung mit Lage und Ausgang in ein Journal
   (`runs\train\journal\…\*.jsonl`, eine JSON-Zeile je Eintrag):
   - **Deckung:** ein Kampf, der schlecht läuft. Gespeichert werden die Lage, die möglichen und die
     gewählten Auswege und das Ergebnis (überlebt?, Herzen verloren, Dauer).
   - **Ziel:** eine Lockout-Kachel. Gespeichert werden die Schätzung des Plans, die tatsächliche Zeit
     und ob es geklappt hat.
   - **Tod:** was ihn getötet hat und wobei.
   - **Spiel:** Liga-Ergebnis mit Kacheln, Toden und Knöpfen.

   Das Journal schreibt Bob auch beim normalen Spielen in IntelliJ, nach `run\bob-journal\`.
3. **Pilot: Kampf und Deckung.** Auf Arena-Servern kämpft Bob in echter Landschaft:
   - **Orte:** Wiese, Wald, Berg, Höhle oder Wasser.
   - **Ausrüstung:** zufällig, von nichts bis zum Diamantschwert.
   - **Gegner:** zufällige Monster, zum Beispiel Zombies, Skelette, Spinnen, Creeper, Endermen,
     Plünderer oder Hexen.

   Wenn der Kampf kippt, wählt er seinen Ausweg zufällig unter den möglichen: weiterkämpfen,
   einmauern, Säule, Säule mit Kampf von oben, weglaufen oder eingraben. So sieht das Modell jeden
   Ausweg in jeder Lage. Ein neues Modell muss zweimal bestehen:
   - **Offline:** auf Kämpfen, die es nicht kennt.
   - **In der Arena:** dieselben Startlagen, einmal nach den heutigen Regeln, einmal mit dem Modell.

   Nur wenn es dabei weniger Tode und weniger Schaden hat als die Regeln, wird es eingesetzt.
4. **Gelernte Zielschätzungen im Routenplaner.** Aus den Ziel-Einträgen lernt ein zweites Modell, wie
   lange eine Kachel wirklich dauert und wie oft sie schiefgeht. Gemeint sind Fälle wie „der Kessel
   dauert immer viermal so lange wie geschätzt“. Der Routenplaner nutzt dann diese Zeit statt der
   reinen Planschätzung. Das Modell wird nur eingesetzt, wenn es auf ungesehenen Zielen mindestens
   5 % genauer ist als der Plan.
5. **Strategie im Spiel gegen sich selbst.** Auf Liga-Servern spielt der Champion gegen einen
   Herausforderer:
   - Der **Champion** spielt mit Bobs aktuellen Knöpfen.
   - Der **Herausforderer** hat dieselben Knöpfe, aber einen oder zwei davon verändert.
   - Beide spielen ein ganzes Lockout-Spiel auf demselben Brett in derselben Welt, Spiel um Spiel.

   Der Herausforderer übernimmt erst, wenn er klar mehr Felder holt (Tode zählen dagegen). Die
   gewinnenden Knöpfe stehen dann in `champion.json`. Diese Knöpfe werden durchsucht:

   | Knopf | Bedeutung |
   | --- | --- |
   | `guardian` | Wächter an oder aus |
   | `quick` | schnelle Kacheln zuerst |
   | `shield` | Schild früh |
   | `foodcap`, `foodstock` | Essensvorrat |
   | `nightarmour` | Rüstung für die Nacht |
   | `copper` | Kupferrüstung |
   | `descent` | Treppe im Plan |
   | `worldsense` | Weltsinn |
   | `cover` | Deckungsmodell an oder aus |
   | `goalmodel` | Zielzeit-Modell an oder aus |

Was eingesetzt wird, kopiert der Prozess auch nach `run\bob-models\`. Damit spielt dein Bob in
IntelliJ beim nächsten Start mit dem Gelernten.

## Einmalig einrichten

- **Java 25.** IntelliJ hat es schon. Das Skript sucht selbst, unter anderem in `%USERPROFILE%\.jdks`
  und `C:\Program Files\…`. Findet es keins, setze `BOB_JAVA` auf den vollen Pfad der `java.exe`.
- **Python 3.10 oder neuer** von <https://www.python.org>. Beim Installieren „Add python.exe to PATH“
  anhaken. Mehr braucht es nicht: `train.bat` richtet beim ersten Start eine eigene Umgebung ein
  (`runs\train\venv`) und installiert dort `numpy` und `psutil`.
- **Grafikkarte (optional).** `scripts\train\gpu.bat` installiert PyTorch mit CUDA (etwa 3 GB). Danach
  lernt das Deckungsmodell auf der RTX 3070 Ti. Ohne PyTorch lernt es mit numpy auf der CPU. Bei
  diesen kleinen Netzen ist das kaum langsamer, die meiste Zeit kosten ohnehin die Minecraft-Server.

## Starten, beobachten, stoppen

| Was | Wie |
| --- | --- |
| Starten | Doppelklick auf `scripts\train\train.bat` |
| Stand ansehen | `scripts\train\status.bat`, ausführlich in `runs\train\report.md` (alle 5 Minuten neu) |
| Stoppen | `scripts\train\stop.bat`: sauber, die Server speichern und enden |
| Neu vermessen | `scripts\train\calibrate.bat`, z. B. nach einem RAM-Upgrade |

Beim **ersten Start** baut `train.bat` Bob und vermisst den Rechner (15 bis 30 Minuten). Danach läuft
das Training von selbst weiter.

Beim **Stoppen** gilt:

- Schließt du einfach das Fenster, beenden sich die Server innerhalb von etwa einer halben Minute
  selbst. Jeder Server prüft, ob der Trainer noch lebt.
- Ein späterer Start macht dort weiter, wo der letzte aufgehört hat. Journal, Modelle und Liga-Stand
  bleiben erhalten.

Die Server laufen nur auf `127.0.0.1`. Fragt die Windows-Firewall beim ersten Mal nach Java, darfst du
„Abbrechen“ wählen: Es wird kein Netz gebraucht.

## Wie viele Server, und wann

Der Prozess startet einen weiteren Server, wenn alle drei Bedingungen gelten:

- Die CPU war in der letzten Minute unter 72 %.
- Es ist genug Speicher frei.
- Die Obergrenze aus der Kalibrierung ist noch nicht erreicht.

Er nimmt den jüngsten Arena-Server wieder weg, wenn die CPU über 92 % steigt oder weniger als 1,5 GB
frei sind, zum Beispiel weil du nebenbei selbst spielst. Ein halb gespieltes Liga-Spiel bleibt dabei
stehen.

Etwa ein Drittel der Server spielt Liga, der Rest Arena. Ist ein neues Deckungsmodell zu prüfen, wird
ein Arena-Server zum Prüfserver.

Alle Werte stehen in `runs\train\config.json` und können dort geändert werden. Sie gelten beim
nächsten Start:

| Wert | Standard | Bedeutung |
| --- | --- | --- |
| `arena_bots` | 6 | Bots pro Arena-Server |
| `arena_mb` / `league_mb` | 2048 / 3072 | Arbeitsspeicher je Server |
| `league_seconds` | 1800 | Länge eines Liga-Spiels (Spielzeit) |
| `league_share` | 0.34 | Anteil der Liga-Server |
| `league_view` | 12 | Sichtweite der Liga-Spiele (Chunks) |
| `train_every_min` | 10 | so oft wird trainiert |
| `eval_pairs` | 120 | Kampfpaare, bevor ein neues Deckungsmodell beurteilt wird |
| `cpu_low` / `cpu_high` | 72 / 92 | ab hier einer mehr / einer weniger |
| `min_free_mb` | 1500 | so viel Speicher bleibt frei |
| `max_instances` | 0 | 0 = aus der Kalibrierung, sonst feste Obergrenze |
| `journal_max_gb` | 3.0 | darüber werden die ältesten Journal-Dateien gelöscht |
| `deploy` | true | Eingesetztes auch nach `run\bob-models` (Bob in IntelliJ) |
| `torch` | auto | auto / on / off: PyTorch (GPU) nutzen, wenn installiert |

## Was wo liegt

Alles liegt unter `runs\train\` (nicht in Git):

| Pfad | Inhalt |
| --- | --- |
| `report.md`, `status.json` | der Stand |
| `calibration.md`, `calibration.json` | die Messung des Rechners |
| `journal\<server>\*.jsonl` | Bobs Entscheidungen mit Lage und Ausgang |
| `models\cover.json`, `models\goal.json`, `models\champion.json` | die eingesetzten Modelle |
| `models\candidate-cover.json` | ein Deckungsmodell in der Prüfung |
| `models\history\` | ältere und abgelehnte Modelle |
| `league\results.jsonl` | jedes Liga-Spiel |
| `instances\<server>\` | die Server: Welt, `server.out` (Log) |
| `bobtrain.log` | das Protokoll des Trainers |

## Bob in IntelliJ: mit oder ohne Gelerntes

Bob nutzt die Dateien in `run\bob-models\`, wenn sie da sind. Abschalten kannst du sie so:

- alle auf einmal mit der Umgebungsvariable `BOB_LEARNED=0`;
- einzeln mit den Knöpfen `cover=0` (Deckung nach Regeln) und `goalmodel=0` (Zielzeiten nach Plan);
- oder indem du die Datei löschst.

Das Journal lässt sich mit `BOB_JOURNAL=off` abschalten.

## Wenn etwas nicht geht

| Problem | Lösung |
| --- | --- |
| „Kein Java 25 gefunden“ | `BOB_JAVA` setzen, z. B. `set BOB_JAVA=C:\Users\…\.jdks\…\bin\java.exe` vor `train.bat` |
| „Python 3 wurde nicht gefunden“ | Python installieren, „Add to PATH“ anhaken |
| Gradle-Fehler beim Bauen | einmal in IntelliJ bauen (`runClient` startet), dann `train.bat` erneut |
| Server stürzen ab | `runs\train\instances\<server>\server.out` ansehen. Nach fünf Abstürzen in zehn Minuten startet der Trainer vorerst keine neuen. |
| Festplatte füllt sich | Welten werden nach jedem Liga-Spiel und alle 30 Minuten pro Arena-Server gelöscht. Das Journal ist auf 3 GB begrenzt. |

## Schnellere Server (optional)

Lege die Fabric-Versionen für 26.3 von Lithium, C2ME und FerriteCore (von modrinth.com) als `.jar`
nach `runs\train\mods\`. Jeder Trainingsserver lädt sie beim nächsten Start. Bob läuft damit wie
bisher. Startet ein Server nicht mehr, nimm den zuletzt hinzugefügten Mod wieder heraus.
