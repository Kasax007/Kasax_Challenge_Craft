# Bob Training Dashboard + Controller — Challenge Craft / `26.3-lockout-bot`

Lokales Dashboard für Bobs Self-Play-Training. Die Struktur und Steuerung orientieren sich am Branch `26.3-lockout-bot`, insbesondere an `scripts/train/bobtrain.py`, `scripts/train/bobcore.py`, `scripts/train/bobleague.py` und `scripts/train/bobmodels.py`.

## Enthaltene Bereiche

- **Overview:** erkennbare Matches, Evaluationen, Modellwerte und historische Serverstarts.
- **Servers & runs:** Trainingssteuerung, aktive/zuletzt bekannte Instanzen, je Server TPS/MSPT, Bots, Kämpfe, Speicher, Job, `server.properties`, `bob-status.json`, letzte `server.out`-Zeilen und Journal-Statistiken.
- **Aktionen:** Training starten/sauber stoppen, Voll-/Quick-Kalibrierung und einmaliges Modelltraining.
- **Settings / calibration:** auswählbare Trainingskonfigurationen und Calibration-JSONs.
- **League / models:** Champion/Challenger, Liga-Ergebnisse, `runs/train/models/` und Live-Dateien in `run/bob-models/`.
- **Workspace:** rekursives Dateiinventar in `runs/train/` (inkl. künftiger Unterordner); lesbare Textdateien haben eine Vorschau, Binärartefakte nur Metadaten.
- **Logs & report:** Ausschnitte aus `bobtrain.log`, Dashboard-Operationen und `report.md`.

## Start unter Windows

1. ZIP entpacken.
2. `start-dashboard.bat` doppelklicken.
3. Das Dashboard öffnet sich unter `http://127.0.0.1:8765` und aktualisiert Status und Dateiansicht regelmäßig.
4. Das Konsolenfenster geöffnet lassen, solange du das Dashboard benutzen möchtest. Mit `Ctrl+C` wird nur der Dashboard-Webserver beendet.

Die Startdatei zeigt auf den Repo-Pfad:

`C:\Users\lars6\IdeaProjects\Kasax_Challenge_Craft`

Wenn du das Repo an einem anderen Ort hast, bearbeite `REPO` in `start-dashboard.bat`.

Alternativ aus PowerShell im entpackten Dashboard-Ordner starten:

```powershell
py .\bot_training_dashboard_server.py --root "C:\Users\lars6\IdeaProjects\Kasax_Challenge_Craft"
```

## Steuerungsaktionen

| Dashboard-Aktion | Trainer-Befehl | Verhalten |
|---|---|---|
| Training starten | `scripts/train/bobtrain.py run` | kontinuierliche Arenen, Liga, Modelltraining und Promotion |
| Training sauber stoppen | Trainer-eigene `runs/train/STOP`-Datei | Trainer fährt die verwalteten Server herunter und speichert den Status |
| Voll kalibrieren | `scripts/train/bobtrain.py calibrate` | misst die Parallelität / Performance und schreibt `calibration.json` / `.md` |
| Quick Calibration | `scripts/train/bobtrain.py calibrate --quick` | kürzere Kalibrierung nach vorhandenem Quick-Modus |
| Modelle einmal trainieren | `scripts/train/bobtrain.py train` | verarbeitet die vorhandenen Journaldaten einmal |

Wenn noch keine virtuelle Python-Umgebung unter `runs/train/venv/` existiert, erstellt der Controller sie und installiert `scripts/train/requirements.txt` (NumPy und psutil). PyTorch/CUDA wird **nicht** automatisch installiert; dafür existiert weiterhin der optionale Setup-Weg im Repo.

Die API ist auf `127.0.0.1`/localhost begrenzt, erlaubt nur diese festen Aktionen und nimmt keine beliebigen Shell-Kommandos entgegen. „Stop“ setzt das vorgesehene STOP-Signal; der Controller killt nicht pauschal Java-Prozesse. Kalibrierungen und einmalige Trainingsschritte können nicht erzwungen abgebrochen werden, damit temporäre Minecraft-Prozesse nicht unkontrolliert zurückbleiben.

## Erkenntnisse / Status richtig lesen

- `runs/train/status.json` wird vom laufenden Trainer regelmäßig aktualisiert. Das Dashboard nutzt Alter und externe Prozesssuche, damit historische Statusdateien nicht automatisch als aktuelle Prozesse gelten.
- Jede Instanz unter `runs/train/instances/<Name>/` hat eigene Logs und Statusdaten. Alte Ordner werden als „historisch / unbekannt“ aufgeführt, nicht als laufende Server gezählt.
- Dashboard-Aktionen landen in `runs/train/dashboard-operations.jsonl`; die Bedienoberfläche speichert den Controllerstatus unter `runs/train/dashboard-controller.json` und das Operation-Log unter `runs/train/dashboard-operation.log`.
- Die Cover-Evaluation im Trainer verwendet einen Kostenwert, bei dem niedrigere Werte günstiger sind. Liga-Promotion ist ein separater Vergleich aus Tiles und Todesfällen. Dashboard-Kennzahlen werden nach diesen unterschiedlichen Kriterien getrennt angezeigt.
- Das Vorhandensein eines Modells unter `run/bob-models/` beweist noch nicht, dass eine laufende Minecraft-Instanz dieses Modell tatsächlich geladen hat. Dafür sind die Lade-Meldungen im jeweiligen Server-/Client-Log maßgeblich.

## Sicherheit / Scope

- Der Dashboard-Server bindet absichtlich nur an localhost; `0.0.0.0` wird abgewiesen.
- Konfigurationsdateien werden angezeigt, aber nicht direkt im Dashboard verändert.
- Binäre Modelle/JARs werden nicht an den Browser übertragen.
- Der Scanner überspringt große generierte Verzeichnisse wie `.git`, `build`, `libraries`, Worlds und Caches.
- Es gibt keine externe Telemetrie.
