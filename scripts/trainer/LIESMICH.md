# Bob-Trainer

Der Bob-Trainer lässt Bob, den Gegner aus Challenge Craft, auf deinem Rechner gegen sich selbst
Lockout spielen – im Hintergrund, Spiel um Spiel. Dabei findet er heraus, welche seiner
Einstellungen ihn besser machen. Was dabei herauskommt, lädst du ab und zu im Chat hoch; daraus
wird Bob besser.

Du brauchst kein Minecraft-Konto, kein Python und nichts zum Programmieren. Nur beim ersten Start
braucht der Trainer einmal Internet (für Java, falls keins da ist, und für den Minecraft-Server).

## Starten

- **Windows:** Doppelklick auf **`Bob-Trainer starten.bat`**. Ein schwarzes Fenster blitzt kurz auf
  und schließt sich wieder; der Trainer läuft dann unsichtbar im Hintergrund.
- **macOS:** Doppelklick auf **`Bob-Trainer starten.command`**. Beim allerersten Mal: Rechtsklick auf
  die Datei → „Öffnen“ (sonst lässt macOS sie nicht starten).
- **Linux:** im Terminal `./bob-trainer.sh`.

Danach öffnet sich im Browser das **Dashboard** (die Adresse ist `http://127.0.0.1:8765/`; nur
dein eigener Rechner kann sie öffnen). Ein zweiter Doppelklick startet nichts doppelt, sondern
öffnet nur wieder das Dashboard.

## Der erste Start

1. **Java:** Der Trainer braucht Java 25. Er sucht es selbst (auch das Java des
   Minecraft-Launchers). Findet er keins, fragt das Fenster, ob er es laden soll: mit `j` antworten.
   Es landet im Ordner `java` des Trainers, sonst nirgends.
2. **Die Minecraft-EULA:** Im Dashboard steht oben der Kasten „Erster Start“. Lies die Lizenz von
   Minecraft (Link im Kasten), setze das Häkchen und klicke **Akzeptieren und starten**. Vorher
   startet kein Spiel – das musst du selbst tun, das darf niemand für dich.
3. **Minecraft wird geladen:** Beim ersten Spiel lädt der Trainer einmal den Minecraft-Server
   (etwa 100 MB). Das dauert ein paar Minuten; danach nie wieder.

## Das Dashboard

- **Stand:** wie viele Spiele fertig sind, wie viele gleichzeitig laufen, Bobs aktuelle
  Einstellungen (der **Champion**), wie viel Platz alles belegt, der letzte Export.
- **Laufende Spiele:** jede Partie spielt in einer neuen, zufälligen Welt; du siehst die Welt, die
  Spielminute, wie schnell sie läuft (2x = doppelt so schnell wie echt) und die Felder.
- **Das Duell:** Der Champion spielt immer gegen einen **Herausforderer** (dieselben
  Einstellungen, eine anders) – beide in derselben Welt, damit Glück und Pech für beide gleich sind.
  Angezeigt wird, wie wahrscheinlich der Herausforderer besser ist. Ab 95 % (frühestens nach 8
  Welten) wird er der neue Champion; unter 5 % oder nach 24 Welten fliegt er raus.
- **Knöpfe:** jede getestete Einstellung mit ihrer geschätzten Wirkung in Feldern je Spiel und der
  Unsicherheit (+/-). Ungetestete kommen zuerst dran, danach die vielversprechendsten.

## Pause, Weiter, Beenden

| Knopf | Was passiert |
| --- | --- |
| **Pause** | Laufende Spiele werden zu Ende gespielt, danach startet keins mehr. |
| **Sofort pausieren** | Laufende Spiele werden jetzt beendet und nicht gezählt. Sonst geht nichts verloren. |
| **Weiter** | Es geht weiter. |
| **Beenden** | Der Trainer hört auf (laufende Spiele zählen nicht) und beendet sich. |
| **Leistung niedrig / mittel / hoch** | Wie viele Spiele gleichzeitig laufen. *Mittel* lässt dir zwei Prozessorkerne und 4 GB Arbeitsspeicher frei; *niedrig* ist ein Spiel; *hoch* nimmt fast alles. |

Du kannst den Trainer jederzeit beenden, den Rechner ausschalten und später wieder starten: Er
macht da weiter, wo er war. Nach jedem fertigen Spiel ist alles gespeichert; verloren gehen
höchstens die Spiele, die gerade liefen. Eine Pause bleibt auch über einen Neustart bestehen
(dann **Weiter** klicken).

## Export: die Ergebnisse an Claude geben

1. Im Dashboard **Export jetzt** klicken.
2. Kurz warten; unter „Letzter Export“ erscheint der Pfad einer Datei
   `bob-...zip` (im Ordner `server/bob-autopilot/export`).
3. Diese Datei im Chat hochladen. Sie ist höchstens 25 MB groß und enthält die Ergebnisse, Bobs
   Tagebuch und die Spiel-Logs – keine persönlichen Daten.

## Was er tut, genauer

- Jedes Spiel ist ein eigener kleiner Minecraft-Server auf deinem Rechner (nur `127.0.0.1`, niemand
  kann beitreten) mit zwei Bobs. Normal dauert ein Spiel 30 Spielminuten, jedes fünfte 90. Die
  Spiele laufen so schnell, wie der Rechner kann; Bob plant dabei in Spielzeit und spielt nicht
  schlechter, nur weil es schneller läuft.
- Nach jedem Spiel wird die Welt gelöscht. Das Tagebuch und die Logs werden begrenzt (zusammen
  höchstens etwa 4,5 GB; die ältesten gehen zuerst).
- Zur Beschleunigung laufen die Server mit den Mods **Lithium** und **FerriteCore** (siehe
  `THIRD-PARTY.txt`).
- Einstellungen für Fortgeschrittene stehen in `server/bob-autopilot.properties` (mit Erklärung).

## Wenn etwas nicht geht

- **Das Dashboard öffnet sich nicht:** im Browser `http://127.0.0.1:8765/` eingeben (oder die
  Adresse in `server/bob-autopilot/dashboard.url`).
- **„Spiel zu langsam“ oder der Rechner ist zäh:** Leistung auf *niedrig* stellen.
- **Fehler beim ersten Start:** die Datei `server/bob-autopilot/autopilot.log` im Chat hochladen.
- **Ganz entfernen:** den Trainer beenden und den Ordner löschen. Er schreibt nichts woanders hin.
