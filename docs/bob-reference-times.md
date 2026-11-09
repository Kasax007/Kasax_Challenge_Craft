# Referenzzeiten: Experte gegen Bob

Diese Tabelle stellt für jedes Lockout-Ziel die Zeit eines Spitzenspielers neben Bobs gemessene Zeit.
Die Expertenzeiten und die schnellsten Wege stehen in der Wissensbasis
`src/main/resources/bob-knowledge/speedrun.json` (Klasse `bot/plan/SpeedrunKnowledge`). Die Tabelle
erzeugt `scripts/bot/analysis/reference_times.py` aus der Wissensbasis und den Spiel-Logs einer Liga.

## Was die Zahlen bedeuten

- **Experte:** Sekunden, die ein Spitzenspieler für das Feld braucht, ab dem Entschluss, es zu holen.
  Er hat die übliche frühe Ausrüstung (Steinwerkzeuge, eine Werkbank im Gepäck), kennt den Ort noch
  nicht, aber die Welt ist durchschnittlich. Nether-Felder zählen ab dem Moment im Nether; der Weg
  hinein (`enter_nether`, etwa 300 s) kommt einmal dazu. Mob-Felder zählen zur passenden Tageszeit
  (Skelett: nachts). Die Zahlen sind Schätzungen aus der Speedrun-Praxis, keine Messungen. Wo eine
  Zahl auf Spieldaten beruht (Loot-Chancen, Rezepte, Spawnlisten), ist sie gegen die Daten von
  Minecraft 26.3 geprüft.
- **Bob:** Median der Spielsekunden vom Start des Ziels bis zum Feld, über die Starts, die das Feld
  holten. Bob beginnt ein Ziel oft erst, wenn das Material schon im Gepäck ist (die Eröffnung, das
  Kit und die Raubzüge liegen davor). Deshalb ist er bei Handwerksfeldern scheinbar schneller als der
  Experte. Die eigentliche Lücke zeigt sich bei ihm in den Starts ohne Erfolg und in der Zeit vor
  dem ersten Feld.
- **Starts / geholt:** wie oft Bob das Ziel anfing und wie oft er es bekam.
- **Bob / Experte:** das Verhältnis der beiden Zeiten (nur, wo Bob das Feld bekam).
