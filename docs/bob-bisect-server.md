# Bob auf dem BisectHosting-Server trainieren

Diese Anleitung richtet den **Bob-Autopiloten** auf deinem gemieteten Minecraft-Server ein. Danach
spielt Bob dort rund um die Uhr Lockout gegen sich selbst, genau wie beim Training auf dem Laptop.
Du brauchst dafür kein Python und keine Kommandozeile: nur das Panel im Browser (Dateien hochladen,
Server-Jar wählen, Start und Stopp).

## Was der Autopilot macht

- Er startet selbst kleine Minecraft-Server mit Challenge Craft, **einen pro Spiel**, jeden mit einer
  frischen Welt und einem neuen Seed. Nach dem Spiel wird die Welt gelöscht, damit die Festplatte
  nicht volläuft.
- In jedem Spiel spielen zwei Bobs eine ganze Lockout-Partie (90 Spielminuten, hart). Der eine spielt
  mit Bobs aktuellen Einstellungen (**Champion**), der andere mit einer leicht veränderten
  (**Herausforderer**). Gewinnt der Herausforderer über mehrere Spiele klar, wird er der neue Champion.
  Das ist dieselbe Liga wie auf dem Laptop; sie beginnt mit dem Champion vom Laptop-Training.
- Die Spiele laufen so schnell, wie der Server kann (meist schneller als Echtzeit). Bob plant dabei
  in Spielzeit, er spielt also nicht schlechter, nur weil das Spiel schneller läuft.
- Auf Wunsch laufen zusätzlich **Arena-Server**: Dort kämpft Bob ständig gegen zufällige Monster.
  Daraus lernt das Deckungsmodell, wie er sich in schlechten Kämpfen retten soll.
- Alles, was dabei herauskommt (Ergebnisse, Bobs Entscheidungs-Tagebuch, die Spiel-Logs), packt der
  Autopilot in ein **Paket zum Herunterladen**. Das lädst du im Chat hoch; daraus wird Bob besser.

**Sicherheit:** Der Autopilot baut keine Verbindung nach außen auf und braucht keine Passwörter.
Die Spiele laufen nur intern auf dem Server (127.0.0.1); niemand kann ihnen beitreten. Deine eigene
Welt, deine `server.properties` und deine Spielerlisten fasst er nicht an. Er schreibt nur in den
Ordner `bob-autopilot` und in die Datei `bob-autopilot.properties`.

**Wichtig:** Solange der Autopilot läuft, ist dein normaler Server aus. Niemand kann dann auf deiner
Welt spielen. Zum normalen Spielen stellst du einfach die Server-Jar zurück (siehe „Stoppen und
wieder normal spielen“).

## Was du brauchst

| Datei | Woher | Wohin auf dem Server |
| --- | --- | --- |
| `challengecraft-1.1.4.jar` (mit Bob) | aus dem Chat (nicht die Version von CurseForge: die kennt Bob noch nicht) | Ordner `mods` |
| `fabric-api-….jar` für Minecraft 26.3 | aus dem Chat, oder von Modrinth/CurseForge („Fabric API“, Version für 26.3) | Ordner `mods` |
| `bob-autopilot.jar` | aus dem Chat | Hauptordner (dort, wo `server.properties` liegt) |
| `bob-autopilot.properties` | legst du selbst an (Inhalt unten) | Hauptordner |

Außerdem: **Fabric für Minecraft 26.3** und **Java 25** auf dem Server (Schritt 1 und 2).

## Schritt für Schritt

Die Namen der Reiter und Felder stammen aus der Hilfe von BisectHosting. Heißen sie in deinem Panel
etwas anders, such nach dem ähnlichsten.

### 1. Fabric 26.3 installieren

Server stoppen. Im Reiter **Minecraft Tools** das Menü **Minecraft Jar** öffnen, **Fabric** wählen
und die Version für Minecraft **26.3** installieren.

Falls es Fabric für 26.3 dort nicht gibt:

1. Lade auf <https://fabricmc.net/use/server/> den Fabric-Server für Minecraft **26.3** herunter
   („Executable Server (.jar)“).
2. Lade die Datei im Panel in den Hauptordner hoch.
3. Trage ihren Namen im Reiter **Startup** im Feld **Server Jar File** ein.

### 2. Java 25 einstellen

Minecraft 26.3 braucht **Java 25**. Im Reiter **Home** unter **Java Version** die Version
**Java 25** wählen (BisectHosting nennt sie „Java 25 Adoptium“).

### 3. Den Server einmal normal starten

Starte den Server einmal mit der Fabric-Jar und warte, bis in der Konsole `Done (…)! For help, type
"help"` steht. Dabei lädt Fabric Minecraft herunter. Fragt das Panel nach der **EULA**, bestätige sie.
Dann **stoppe** den Server wieder.

### 4. Die Dateien hochladen

Unter **Dateien**:

1. In den Ordner `mods`: `challengecraft-1.1.4.jar` und die Fabric-API-Jar.
   - Es darf nur **eine** Challenge-Craft-Jar im Ordner liegen. Eine alte vorher löschen.
   - Andere Mods im Ordner werden mitgeladen. Für das Training ist ein Server mit nur diesen beiden
     Mods am besten.
2. In den Hauptordner: `bob-autopilot.jar`.
3. Im Hauptordner eine neue Datei `bob-autopilot.properties` anlegen, mit diesem Inhalt:

   ```properties
   # Wie viele Liga-Spiele gleichzeitig laufen (auto = so viele, wie der Server traegt).
   league_games=auto
   # Arena-Server zusaetzlich (Kampftraining). 0 = keine.
   arena_servers=0
   # Laenge eines Spiels in Spielminuten.
   game_minutes=90
   # Alle wie viele Stunden ein Paket zum Herunterladen entsteht.
   export_hours=12
   ```

   Legst du die Datei nicht an, schreibt der Autopilot sie beim ersten Start selbst, mit allen
   Einstellungen und einer Erklärung zu jeder.

### 5. Die Server-Jar auf den Autopiloten stellen

Im Reiter **Startup** im Feld **Server Jar File** den Namen der Fabric-Jar (zum Beispiel
`fabric-server-launch.jar`) durch **`bob-autopilot.jar`** ersetzen. Notiere dir den alten Namen:
Du brauchst ihn zum normalen Spielen wieder. Den Arbeitsspeicher und die anderen Startparameter
lässt du, wie sie sind; der Autopilot teilt den Speicher selbst auf.

Lässt sich das Feld nicht ändern (eigene Jars gibt es bei manchen Tarifen nicht), geht es auch
über die Dateinamen:

1. Die Jar, deren Name im Feld **Server Jar File** steht, umbenennen, zum Beispiel von
   `fabric-server-launch.jar` in `fabric-original.jar`. Das ist die Fabric-Jar.
2. `bob-autopilot.jar` in genau den Namen umbenennen, der im Feld steht.

Der Autopilot findet die Fabric-Jar an ihrem Inhalt, egal wie sie heißt. Zum normalen Spielen
benennst du beide wieder zurück.

### 6. Starten

Starte den Server. In der Konsole sollte nach wenigen Sekunden etwa das stehen:

```text
[Bob-Autopilot] Bob-Autopilot 1.0 (Challenge Craft) im Ordner /home/container
[Bob-Autopilot] Fabric: fabric-server-launch.jar, Mod: challengecraft-1.1.4.jar (Version 1.1.4, Commit ...)
[Bob-Autopilot] Rechner: 8 Kerne, 22,0 GB Arbeitsspeicher. Plan: 3 Liga-Spiel(e) zu 4096 MB, 0 Arena(s) zu 3072 MB
[Bob-Autopilot] Liga beginnt mit dem Champion vom Laptop-Training: copper=1,cover=1,...
[Bob-Autopilot] Done (0.152s)! For help, type "help"
[Bob-Autopilot] Stand: bob-autopilot/STATUS.txt. Befehle: status, export, finish, stop (help erklaert sie)
[Bob-Autopilot] Autopilot gestartet: 3 Liga-Spiel(e) gleichzeitig, 0 Arena(s), 90 Spielminuten je Spiel
[Bob-Autopilot] liga-1: Spiel 1 startet (Seed ...): BobA copper=1,... gegen BobB copper=1,...
```

Jedes Spiel braucht etwa 15 Sekunden bis zum Start; die Spiele starten nacheinander. Beim nächsten
Start steht statt „Liga beginnt …“ die Zeile „Liga geladen: … Spiele bisher …“: Die Liga geht weiter. Steht
stattdessen eine **WARNUNG** mit „Der Autopilot startet nicht“, sagt die Zeile davor, was fehlt
(siehe „Wenn etwas nicht geht“).

## Läuft es?

- In der **Konsole** steht alle zehn Minuten ein kurzer Stand, und nach jedem Spiel eine Zeile wie
  `liga-1: Spiel 3 fertig - Champion 6 Felder, 0 Tode; Herausforderer 4 Felder, 1 Tod (37 echte Minuten)`.
- Der Befehl **`status`** in der Konsole zeigt den ganzen Stand.
- Die Datei **`bob-autopilot/STATUS.txt`** zeigt ihn auch (jede Minute neu). Darin steht unter
  anderem:
  - wie viele Spiele fertig sind und wie viele abgebrochen,
  - der Champion und der laufende Herausforderer mit seinem Zwischenstand,
  - jedes laufende Spiel: Spielminute, Felder je Bob und **Tempo** (2,0x heißt: doppelt so schnell
    wie Echtzeit),
  - die letzten Spiele und Ereignisse.

Ein 90-Minuten-Spiel dauert je nach Tempo etwa eine halbe bis eine Stunde, manchmal länger: Steckt
ein Bob irgendwo fest (etwa im Wasser), rechnet er an seinem Weg, und das Spiel läuft eine Weile
langsamer als Echtzeit. Die Uhrzeiten stehen in
der Zeitzone des Servers. Oft ist das UTC: Dann zeigen sie im Winter eine Stunde weniger als die
deutsche Uhr, im Sommer zwei.

## Die Ergebnisse herunterladen und mir schicken

1. Tippe in der Konsole **`export`**. Nach ein paar Sekunden steht dort:
   `Paket zum Herunterladen (auf Befehl): bob-autopilot/export/bob-export-….zip`.
2. Öffne unter **Dateien** den Ordner `bob-autopilot/export` und lade die neueste
   `bob-export-….zip` herunter.
3. Lade die Zip-Datei hier im Chat hoch.

Alle zwölf Stunden legt der Autopilot ohnehin ein neues Paket an; die letzten sechs bleiben liegen.
Jedes Paket enthält alles Neue seit dem vorigen. Hast du ein paar Tage nicht hineingeschaut, lade
einfach alle Pakete hoch, die im Ordner liegen. Ein Paket ist höchstens etwa 25 MB groß; was nicht
hineinpasst, kommt ins nächste.

## Stoppen und wieder normal spielen

- **Stopp-Knopf** im Panel (oder `stop` in der Konsole): Alle Spiele enden innerhalb einer halben
  Minute. Halb gespielte Spiele zählen nicht. Beim nächsten Start geht die Liga weiter, wo sie war.
- **`finish`** in der Konsole: Die laufenden Spiele werden zu Ende gespielt, dann hört der Autopilot
  auf. Das dauert bis zu einer Stunde.
- **Wieder normal spielen:** Server stoppen, im Reiter **Startup** im Feld **Server Jar File** wieder
  den Namen der Fabric-Jar eintragen (oder die Jars zurückbenennen), starten. Der Ordner `bob-autopilot` stört dabei nicht; du kannst ihn liegen
  lassen (dann geht die Liga beim nächsten Mal weiter) oder löschen.

Geplante Neustarts des Panels schaden nicht. Nur das gerade laufende Spiel geht dabei verloren.

## Arbeitsspeicher: wie viele Spiele gleichzeitig

Jedes Spiel ist ein eigener Minecraft-Server. Er darf höchstens den Speicher aus `game_memory_mb`
(4096 MB) für die Welt nehmen; dazu kommt, was Java selbst belegt (gemessen gut 0,7 GB, der
Autopilot rechnet vorsichtig mit 1,3 GB). Ein Spiel braucht also höchstens etwa **5,3 GB**, ein
Arena-Server (3072 MB) etwa **4,3 GB**. Mit `league_games=auto` rechnet der Autopilot das selbst aus
und lässt 1,5 GB frei. Der Autopilot selbst braucht weniger als 0,1 GB.

| Einstellung bei 22 GB | belegt höchstens | Empfehlung |
| --- | --- | --- |
| `league_games=auto` (ergibt 3), `arena_servers=0` | 16 GB | **Standard**: am meisten Liga-Spiele |
| `league_games=2`, `arena_servers=1` | 15 GB | Liga und Kampftraining |
| `league_games=2`, `arena_servers=0` | 11 GB | wenn der Server langsam ist (siehe unten) |
| `league_games=4` | 21 GB | **zu viel** (dazu 1,5 GB Reserve): der Autopilot startet dann nur 3 |

Wichtiger als der Speicher ist oft der **Prozessor**. Jedes Spiel nutzt etwa zwei Kerne, wenn es
schnell läuft. Mit `auto` startet der Autopilot höchstens so viele Spiele, wie Kerne für sie da sind.
Schau nach ein paar Stunden in `STATUS.txt` auf das **Tempo** der Spiele:

- Liegt es über 1,5x, ist alles gut.
- Liegt es bei mehreren Spielen dauerhaft **unter 1,0x**, teilen sich zu viele Spiele zu wenige
  Kerne. Dann `league_games=2` eintragen und neu starten.

## Eine neue Bob-Version einspielen

Bekommst du im Chat eine neue `challengecraft-1.1.4.jar`:

1. Server stoppen.
2. Im Ordner `mods` die alte Jar löschen und die neue hochladen.
3. Starten.

Die Liga läuft weiter. Jedes Ergebnis merkt sich, mit welcher Bob-Version es gespielt wurde.
Kommt im Chat auch eine neue `bob-autopilot.jar` mit, ersetzt du sie im Hauptordner genauso.

## Wenn etwas nicht geht

| Meldung oder Problem | Lösung |
| --- | --- |
| „Minecraft 26.3 braucht Java 25“ | Im Reiter **Home** unter **Java Version** Java 25 wählen. |
| „Die Minecraft-EULA ist … noch nicht akzeptiert“ | Schritt 3: einmal normal starten und die EULA bestätigen, oder in `eula.txt` `eula=true` eintragen. |
| „Keine Fabric-Server-Jar im Hauptordner gefunden“ | Schritt 1. Oder in `bob-autopilot.properties` `server_jar=<Name der Fabric-Jar>` eintragen. |
| „Im Mods-Ordner ist keine Challenge-Craft-Jar“ / „fehlt Fabric API“ | Schritt 4. |
| „… kennt Bob noch nicht“ | Das ist die Version von CurseForge. Bitte die Jar aus dem Chat nehmen. |
| „Zwei Challenge-Craft-Jars im Mods-Ordner“ | Die ältere löschen. |
| „Server beim Start beendet“ / „Spiel zu langsam“ / „Server hängt“ | Der Autopilot versucht es nach einer Minute neu. Nach fünf Fehlern in 30 Minuten macht er 15 Minuten Pause. Den Grund zeigt die Zeile in der Konsole. Die Logs liegen in `bob-autopilot/games` und kommen ins nächste Paket. Schick mir das Paket. |
| „Die Startparameter geben dem Autopiloten selbst … GB“ | Im Reiter **Startup** zusätzliche Java-Parameter (zum Beispiel „Aikar's Flags“) abschalten. Der Autopilot braucht selbst kaum Speicher; die Spiele bekommen ihren eigenen. |
| „OutOfMemoryError“ in einer Fehlerzeile | `game_memory_mb=5120` eintragen und `league_games` um eins senken. |
| Die Konsole zeigt „Starting“ statt „Running“ | Das ist nur die Anzeige. Der Autopilot meldet sich mit `Done (…)` wie ein Minecraft-Server; manche Panels warten auf einen anderen Text. |
| Die Festplatte wird voll | In `bob-autopilot.properties` `journal_max_mb` und `game_logs_max_mb` kleiner machen. |

## Alle Einstellungen

Alle Einstellungen stehen in `bob-autopilot.properties` im Hauptordner. Sie gelten beim nächsten
Start. Fehlt eine, gilt ihr Standard. Ein falscher Wert wird in der Konsole gemeldet; dann gilt
ebenfalls der Standard.

| Einstellung | Standard | Bedeutung |
| --- | --- | --- |
| `league_games` | `auto` | Liga-Spiele gleichzeitig: `auto` oder eine Zahl (`0` = keine) |
| `arena_servers` | `0` | Arena-Server zusätzlich |
| `game_minutes` | `90` | Länge eines Liga-Spiels in Spielminuten |
| `game_memory_mb` | `4096` | Speicher je Liga-Spiel (MB) |
| `arena_memory_mb` | `3072` | Speicher je Arena-Server (MB) |
| `journal_max_mb` | `3000` | höchstens so viel Platz für Bobs Tagebuch |
| `game_logs_max_mb` | `1500` | höchstens so viel Platz für die Spiel-Logs |
| `export_hours` | `12` | alle so viele Stunden ein Paket (`0` = nur auf `export`) |
| `export_max_mb` | `25` | Höchstgröße eines Pakets |
| `keep_exports` | `6` | so viele Pakete bleiben liegen |
| `arena_bots` | `6` | Bots je Arena-Server |
| `arena_restart_minutes` | `30` | nach so vielen Minuten bekommt eine Arena eine frische Welt |
| `view_distance`, `simulation_distance` | `12`, `8` | Sicht- und Simulationsweite der Liga-Spiele (Chunks) |
| `difficulty` | `hard` | Schwierigkeit |
| `search_threads` | `4` | Threads für Bobs Wegsuche je Spiel |
| `server_jar` | `auto` | die Fabric-Jar; `auto` erkennt sie selbst |
| `mods_folder` | `mods` | der Mods-Ordner, den die Spiele laden |
| `java` | `auto` | Java für die Spiele; `auto` = dasselbe wie der Autopilot |
| `extra_jvm_flags` | leer | zusätzliche Java-Parameter je Spiel |
| `seeds` | leer | feste Seeds, der Reihe nach (leer = zufällig) |
| `machine` | `bisect` | der Name dieses Servers in den Ergebnissen |

## Konsolenbefehle

| Befehl | Wirkung |
| --- | --- |
| `status` | den Stand zeigen |
| `export` | jetzt ein Paket zum Herunterladen anlegen |
| `finish` | laufende Spiele zu Ende spielen, dann aufhören |
| `stop` | sofort aufhören (wie der Stopp-Knopf) |
| `help` | die Befehle zeigen |

## Was im Ordner `bob-autopilot` liegt

| Pfad | Inhalt |
| --- | --- |
| `STATUS.txt`, `status.json` | der Stand |
| `autopilot.log` | alles, was der Autopilot in die Konsole geschrieben hat |
| `export/` | die Pakete zum Herunterladen |
| `league/results.jsonl` | jedes fertige Spiel mit allen Zahlen |
| `league/state.json`, `league/champion.json` | der Stand der Liga, der aktuelle Champion |
| `league/failures.jsonl` | jedes abgebrochene Spiel mit dem Grund |
| `journal/` | Bobs Entscheidungen mit Lage und Ausgang (daraus lernen die Modelle) |
| `games/` | das Log jedes Spiels (gzip) |
| `models/` | leer; hier könnten neuere Modelle liegen (sonst nimmt Bob die aus der Mod) |
| `work/` | die Arbeitsordner der laufenden Spiele (nicht herunterladen) |

---

## Für Entwickler: wie es gebaut ist

- **Code:** `src/autopilot/java/net/kasax/challengecraft/autopilot/`, eigener Source-Set ohne
  Minecraft und ohne Bibliotheken, für Java 17 kompiliert (eine zu alte Java-Version bekommt so eine
  lesbare Meldung statt eines Absturzes). Bauen: `sh ./gradlew bobAutopilotJar` (ergibt
  `build/libs/bob-autopilot.jar`). Tests: `sh ./gradlew bobAutopilotTest` (JUnit; darunter ein
  Durchlauf der ganzen Schleife mit einem Ersatz-Server als eigenem Prozess).
- **Ablauf:** Der Autopilot ist eine Java-Fassung von `scripts/train/bobtrain.py` (Liga- und
  Arena-Teil) und `bobleague.py` (Liga-Regeln, gleiches Zustandsformat, gleiche Knopf-Tabelle; ein
  Test vergleicht sie). Jedes Spiel ist ein eigener Fabric-Server mit eigener Welt in
  `bob-autopilot/work/<slot>`. Jars, `libraries`, `versions`, `mods` und `.fabric/server` sind
  verlinkt, `config` und `eula.txt` kopiert. Gesteuert wird über die Konsole des Spiels (stdin);
  gelesen werden die Ausgabe (`[BOTVS] result`) und `bob-status.json`. Es gibt kein RCON und kein
  Netz. Umgebungsvariablen wie beim Laptop-Trainer: `CHALLENGECRAFT_BOT_SIM=1`,
  `CHALLENGECRAFT_TEST_CHALLENGES=40`, `CHALLENGECRAFT_FULL_BOARD=1`, `BOB_JOURNAL`, `BOB_MODELS`,
  `BOB_STATUS_DIR` und `BOB_PARENT_PID`. Über `BOB_PARENT_PID` beendet sich jedes Spiel selbst, wenn
  der Autopilot weg ist.
- **Code-Stand:** Die Mod-Jar trägt ihren Commit im Manifest (`Bob-Commit`; ein `+` heißt: mit
  nicht eingecheckten Änderungen gebaut). Jedes Ergebnis enthält Jar-Name, Version, Commit und
  Prüfsumme.
- **Pakete auswerten:** `league/results.jsonl` hat das Format des Laptop-Trainers (dazu `machine`,
  `slot`, `code`, `counted`, `real_secs`). `journal/*.jsonl` kann direkt nach
  `runs/train/journal/bisect/` gelegt werden (`bobtrain.py train`). Von gleichen Dateinamen gilt die
  neueste Kopie. `games/*.log.gz` entpackt sind die Spiel-Logs für `scripts/bot/analysis/`
  (`deaths.py`, `goals.py`, …).
- **Im Container prüfen:** `flock <build.lock> python3 scripts/bot/autopilot_check.py --root <Ordner>
  --games 2 --minutes 10 --arena-minutes 8` richtet einen Server wie im Panel ein, startet ihn einmal
  normal, lässt den Autopiloten spielen, packt, stoppt und prüft alles.
