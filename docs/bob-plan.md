# Bob – Plan zum guten Lockout-Spieler

> Fortschritt: Schritte 1–7 umgesetzt (siehe `docs/konzept-bot.md`, Abschnitt „Stand“). Seitdem
> Testphase mit ganzen Brettern, siehe Abschnitt 6.

Stand: 30.09.2026. Dieser Plan ist die Arbeitsgrundlage, um Bob ohne Rückfragen Schritt für Schritt
zu einem ebenbürtigen Lockout-Gegner zu machen. Jeder Schritt hat messbare Abnahmekriterien; ein
Schritt gilt erst als fertig, wenn seine Tests grün sind.

## 1. Analyse: wo Bob steht

### Was vorhanden ist

| Schicht | Inhalt |
|---|---|
| Körper | echter `ServerPlayer` (`BotPlayer`), Eingaben vorwärts/seitwärts/springen/schleichen, Respawn |
| Motorik | A*-Pfadsuche (laufen, diagonal, springen, fallen ≤ 3, schwimmen, graben, pfeilern, brücken), Navigator mit Neuplanung |
| Aktionen | abbauen mit bestem Werkzeug, platzieren, Items benutzen, Kisten öffnen |
| Wissen | Rezepte und Loot aus dem Spiel, Seltenheiten, Erzhöhen, Biom-Typisches, Mob-Drops |
| Planer | `ObtainPlanner`: Item-Baum (abbauen, töten, craften, schmelzen, Eimer füllen, Obsidian gießen) mit Kosten, Simulation, Budget |
| Aufgaben | abbauen, craften, schmelzen, jagen, essen, ausrüsten, Kiste looten, Biom/Struktur besuchen, melken/scheren, Portal bauen, Portal gießen, Schatz ausgraben, aufsteigen |
| Sinne | Biome im Raster, Strukturen per Sichtlinie, ungeöffnete Kisten, Portal-Gedächtnis |
| Gehirn | `LockoutBrain`: nimmt immer das billigste machbare Ziel (Blick auf das nächste), Essensvorrat, Gelegenheiten (Kisten < 32, Schatzkarte) |

### Was einmal in einer Testwelt funktioniert hat

Steinspitzhacke in 30 s, Eisen schmelzen, Strip-Mining, Kuh melken, Brücke, Wasser-MLG, Portal
gießen in 70 s, von Eisen bis Nether in 83 s.

### Warum es sich nicht nachhaltig anfühlt (Ursachen)

1. **Kein Spielplan.** Das Gehirn entscheidet Ziel für Ziel nach geschätzten Kosten. Es gibt keine
   Phasen (Eröffnung, Eisen, Nether …), keine Einkaufsliste, kein Gedächtnis für „warum“.
2. **Aufgeben statt eskalieren.** Suchen haben ein Zeitbudget und enden mit „fehlgeschlagen“.
   Darüber sitzt nichts, das „dann anders“ sagt. Einzelbefehle (`nether`) bleiben danach stehen.
3. **Strategiewissen als Einzelfälle im Code.** Es gibt kein Strategiebuch, aus dem Bob nach
   Bedingungen wählt (z. B. „Ozean in der Nähe → Schiffswrack für Eisen“).
4. **Bewegung nicht robust.** Ausstieg aus dem Wasser, Ufer, Höhlen, Rückweg an die Oberfläche
   klappen in echter Welt oft nicht. Jede höhere Fähigkeit hängt daran.
5. **Wahrnehmung künstlich eingeschränkt.** Sichtlinien-Simulation für Strukturen ist teuer und
   lückenhaft. Gewünscht ist: Bob darf die Welt lesen (wie „gesehen“) – das ist einfacher und
   zuverlässiger.
6. **Testen zu langsam.** Ganzer Client in Echtzeit mit Screenshots: ein Befund pro 10 Minuten.

## 2. Erkenntnisse aus Baritone (LGPL-3.0 – nur als Vorlage, kein Code übernommen)

- **Bewegungen als eigene Klassen** (Traverse, Ascend, Descend, Diagonal, Downward, Fall, Pillar,
  Parkour). Jede hat `cost()` in Ticks (unendlich = unmöglich) und `updateState()`, das pro Tick
  Eingaben setzt und `RUNNING/SUCCESS/UNREACHABLE/FAILED` meldet.
- **Abbruchregeln im Ausführer:** zu weit vom Pfad (> 200 Ticks) → neu; Bewegung dauert deutlich
  länger als ihre Kostenschätzung → abbrechen und neu planen; Bewegung wird durch Weltänderung
  unmöglich → abbrechen. Nie endlos an einer Stelle hängen.
- **Wasser:** eigene Laufkosten im Wasser; beim Schwimmen wird **Sprint** gehalten (Schwimmhaltung)
  und die **Wasserlinie** gehalten; am Ufer (`Ascend`) wird erst gesprungen, wenn man nah genug und
  ausgerichtet ist (≤ 1,2 Blöcke, seitlich ≤ 0,2), Kopffreiheit wird geprüft. Blöcke, die an
  Flüssigkeit grenzen, werden nicht abgebaut.
- **Fallen:** bis 3 Blöcke normal, mit Wassereimer bis 23, in stehendes Wasser beliebig.
- **Segmentierte Pfade:** Berechnung bis Zeitlimit/Sichtweite, dann das beste Teilstück gehen und
  unterwegs das nächste vorberechnen; Rückwärtsgehen auf dem alten Segment wird bevorzugt.
- **Prozesse mit Priorität** (Mine, Explore, GetToBlock, Follow, Farm, Builder …): ein
  Steuerungsmanager fragt jeden Tick alle Prozesse, der aktive mit höchster Priorität steuert;
  temporäre Prozesse (z. B. Inventar ordnen) unterbrechen kurz.
- **Mine-Prozess:** Liste bekannter Fundorte aus dem Chunk-Cache (bis zu N), regelmäßig neu
  gescannt; unerreichbare Fundorte kommen auf eine **Blacklist**, statt die Suche abzubrechen;
  Drops werden mit eingesammelt.
- **Explore-Prozess:** geht zu den nächsten noch unbekannten Chunks (Spirale), statt zufällig.
- **Chunk-Cache:** 2-Bit-Karte (Luft/fest/Wasser/meiden) plus Orte wichtiger Blöcke, auch über
  entladene Chunks hinweg.

Übertrag auf Bob: Bewegungsausführung pro Bewegungsart mit eigenem Zustand und Zeitlimit;
Ausführer-Abbruchregeln; Schwimmen mit Sprint/Wasserlinie; Fundort-Listen mit Blacklist statt
„found no“; Erkunden nach unbekannten Chunks; Prioritäts-Steuerung von Verhalten.

## 3. Zielbild: Bob denkt wie ein guter Spieler

```
 Weltwissen ─► Spielplan (Strategie) ─► Aufgaben (Taktik) ─► Fertigkeiten (Motorik)
     ▲              │   ▲                    │
     └── Ereignisse ┘   └── Nebenbei-Liste ──┘
```

- **Weltwissen (`BotMemory`)**: Strukturen aus den Weltdaten in Sichtweite (Dorf, Schiffswrack,
  Ruinenportal, Wüstentempel, Dschungeltempel, Ozeanruine, vergrabener Schatz nur per Karte …),
  Ressourcen (Lavapools, Kies, Sand, Zuckerrohr, Tiere, Heuballen, Bäume nach Art), geplünderte
  Kisten, eigene Portale, Todesort, Gefahren. Aktualisiert beim Laden von Chunks und periodisch.
- **Spielplan (`LockoutStrategist`)**: Beim Start Brett lesen, jedes Ziel einordnen (Voraussetzungen,
  Ort, Phase, Kosten, Punkte), Route in Phasen mit Einkaufsliste bilden. Neu planen bei: Ziel vom
  Gegner genommen, neue Entdeckung, Fehlschlag, Tod, Phasenwechsel. Gegner beobachten und
  Ziele blocken, die er gleich schafft.
- **Nebenbei-Liste**: Was später gebraucht wird (Kies für Feuerstein, Zuckerrohr, Sand, Leder,
  Nahrung, Blöcke) wird unterwegs mitgenommen, wenn der Umweg klein ist.
- **Strategiebuch**: Wege mit Bedingungen: Nether = Portal gießen (Standard, Diamant nur mit
  Diamantspitzhacke); Eisen = Schiffswrack > Dorfschmied > Höhle > Strip-Mining; Nahrung =
  Heuballen → Brot, Tiere; Wüstentempel seitlich anbuddeln, Druckplatte meiden, TNT mitnehmen;
  Schatzkarte → Chunk (9,9); Ruinenportale looten/reparieren; später Bastion, Festung.
- **Beharrlichkeit**: Keine Aufgabe endet ohne Nachfolger. Fehlschlag → Eskalation (weiter suchen
  → andere Methode → zurückstellen). Leerlauf gibt es nicht; im Zweifel Ausrüstung verbessern.

## 4. Schritte

### Schritt 1 – Testfundament

- Fabric-GameTests (API ist in fabric-api 0.161 enthalten): Szenarien werden im Code gebaut,
  laufen ohne Grafik auf dem Server (`./gradlew runGameTest`), mit bestanden/nicht bestanden und
  benötigten Ticks.
- Headless-Benchmark: dedizierter Server + `/tick sprint`, ein Befehl startet eine Lockout-Runde
  gegen niemanden (Bob allein), nach N Spielminuten Bericht: Kacheln, Zeiten bis Steinwerkzeug /
  Eisen / Nether, Leerlauf- und Steckzeit.
- Entscheidungsprotokoll in `logs/bob-<name>.log`: Plan, Begründungen, Fehlschläge.
- **Abnahme:** `runGameTest` läuft ohne Client, mindestens 6 Szenarien vorhanden; Benchmark liefert
  Bericht für einen Seed in wenigen Minuten Echtzeit.

### Schritt 2 – Bewegung robust

- Wasser: Schwimmen mit Sprint/Wasserlinie, Ausstieg am 1-Block-Ufer ausgerichtet springen, zur
  Not Stufe graben, im tiefen Wasser zum nächsten flachen Ufer planen; fließendes Wasser.
- Ausführer-Abbruchregeln (Zeit > Schätzung, zu weit vom Pfad), Blacklist unerreichbarer Ziele.
- Oberfläche/Höhlen: sicherer Aufstieg (Treppe/Pfeiler), Rückweg aus Minen.
- Sprinten auf Strecken; kleine Lücken springen.
- **Abnahme (GameTests):** Becken 2 tief mit 1-Block-Ufer, See 5 tief mit steilem Ufer, Fluss
  queren, Schacht 20 hoch, Höhle mit Überhang, Lücke 1–2 Blöcke. Alle grün, jeweils unter einer
  Zeitgrenze.

### Schritt 3 – Weltwissen

- `BotMemory` mit Strukturen aus `StructureManager`/Chunk-Strukturstarts im Umkreis der
  Sichtweite, Ressourcen-Index geladener Chunks (Lava, Kies, Sand, Heu, Bäume, Tiere, Wasser),
  geplünderte Kisten, Portale, Tode. Ersetzt Sichtlinien-Simulation.
- Planer und Aufgaben lesen Fundorte aus dem Gedächtnis statt eigene Nahsuchen.
- **Abnahme:** GameTest/Benchmark: Dorf/Schiffswrack/Lavapool im Umkreis werden sofort gekannt;
  `senses` zeigt sie; MineTask findet Kies am Fluss ohne Suchlauf.

### Schritt 4 – Eröffnung und Beharrlichkeit

- Feste Eröffnung: Holz (≥ 5 Stämme) → Werkbank → Holzspitzhacke → Stein → Steinspitzhacke, -axt,
  -schaufel, -schwert; dabei Brett schon analysiert.
- Eskalation statt Abbruch: Suchbereich erweitern → andere Quelle/Methode → Ziel zurückstellen mit
  Wiedervorlage; Einzelbefehle (`nether`, `get`) nutzen dieselbe Eskalation.
- Leerlauf-Verbot: gibt es nichts, dann Ausrüstung/Vorräte verbessern oder erkunden.
- **Abnahme:** Benchmark: Steinwerkzeug < 90 s auf 3 Seeds; kein „idle“ > 10 s; kein Ziel endet
  endgültig ohne Wiedervorlage.

### Schritt 5 – Spielplan

- Brettanalyse beim Start: Ziele nach Voraussetzungen (Eisen, Nether, Ozean, Dorf, Mob, Biom …)
  gruppieren, Phasen bilden, Einkaufsliste (z. B. 3 Eimer, Feuerstein, 10 Blöcke …).
- Route: Reihenfolge nach Nutzen/Zeit inkl. Synergien (ein Weg erledigt mehrere Ziele).
- Neu planen bei Ereignissen (Gegner-Claim, Entdeckung, Fehlschlag, Tod).
- Nebenbei-Liste mit Umweg-Budget.
- Gegner: seine Position/Inventar-Fortschritt grob beobachten, Ziele blocken, die er gleich hat.
- **Abnahme:** Benchmark: Plan erscheint im Protokoll; Kacheln pro 10 min steigen gegenüber
  Schritt 4; Replan-Ereignisse sichtbar.

### Schritt 6 – Strategiebuch

- Nether über Portal gießen als Standard; Diamant-Route nur mit vorhandener Diamantspitzhacke.
- Ozean-Route: Schiffswrack-Kisten (Heck: Karte, Mitte: Eisen/Vorräte), Schatzkarte → Schatz.
- Dorf: Kisten looten, Heu → Brot, Schmied.
- Wüstentempel: seitlich runter, Druckplatte meiden, 4 Kisten, TNT abbauen und mitnehmen.
- Ruinenportal: Kiste looten, Portal ergänzen und anzünden, wenn billiger als gießen.
- **Abnahme:** GameTests je Strategie (Tempel ohne Explosion geplündert, Schiff geplündert, Portal
  repariert) grün; Benchmark nutzt die Wege.

### Schritt 7 – Fortgeschritten

- Boot-MLG, Bastion-Loot-Wege, Festung finden, Brauen, Verzaubern, Handel, Züchten/Zähmen, Bett,
  End-Ziele; Kampf (zurückweichen, Schild, Bogen).
- **Abnahme:** je Fähigkeit ein GameTest; Benchmark-Zahlen weiter besser.

## 5. Arbeitsweise

- Nach jedem Schritt: Tests grün, Doku (`docs/konzept-bot.md`) aktualisiert, Commit + Push auf
  `26.3-lockout-bot`.
- Client-Tests nur noch zum visuellen Abschluss.
- Jeder Fehler aus einem Benchmark wird als GameTest-Szenario festgehalten, bevor er behoben wird.

## 6. Testphase: ganze Bretter (10 Welten, je 10 Minuten, schwer)

Gemessen mit `scripts/bot/bench-many.sh 600 hard 11 22 33 44 55 66 77 88 99 1010`, ausgewertet mit
`scripts/bot/analyse.py run/bench_*.log` (Zeit je Wurzelaufgabe, Felder, Tode).

| Runde | Felder Ø | Runden mit 0 | Tode | Wichtigste Änderung davor |
|---|---|---|---|---|
| 1 | 2,0 | 3 | 4 | (Ausgang) |
| 2 | 2,2 | 2 | 7 | Chunk-Tickets folgen dem Bot |
| 3 | 2,5 | 0 | 8 | Fernziele (Biome), Unterwasser-/Tiefziele überspringen |
| 4 | 3,0 | 0 | 7 | Verstecken vor Schützen, Rückzug im Kampf |

### Gefundene und behobene Ursachen

- **Die Welt stand still:** Die Chunk-Tickets des Bots blieben am Einlogpunkt. Weiter weg fielen
  Drops nicht, Tiere bewegten sich nicht, das Gedächtnis sah keine Chunks. Jetzt
  `ServerChunkCache.move` jeden Tick wie beim echten Spieler.
- **Bäume:** Stamm von unten abbauen, nur mit festem Stand (in der Luft 5× langsamer).
- **Keine Bäume in Sicht:** Fernziel aus der Biomverteilung (Wald hinter der Wüste). Es wird gemerkt
  und in Etappen über die Oberfläche angelaufen. Gescheiterte Etappen werden seitlich umgangen, und
  der Weg zählt in die Zeitschätzung.
- **Falsche Ziele:** Holz tief in Minen, Stein unter Flüssen oder unter Wasser wird übersprungen.
  Stein mit der Hand im Weg kostet viermal so viel.
- **Zeitbudget:** Ziele, die gut vorankommen, bekommen mehr Zeit. Unterwegs zu sein zählt als
  Fortschritt. Ziele, die einmal überzogen haben, werden danach teurer geschätzt.
- **Essen:** Gesucht wird erst bei echtem Hunger, Nahrung direkt vor Ort nimmt Bob gleich mit.
  Gescheiterte Mitnahmen (Feuerstein) ruhen eine Weile.
- **Kampf:** Rückzug auch im Kampf, mit Sprint und Ausweichen zur Seite. Gegen Schützen gräbt sich
  Bob ein (3 tief, Deckel, essen).
- **Portal gießen:** Endlosschleife beim Wasserschöpfen behoben; der GameTest besteht stabil.

### Offen (nächste Hebel)

- Die Eröffnung nach einem Tod kostet weiter viel. Tode senken (Creeper, Ertrinken, Tropfstein) und
  nach dem Tod die eigenen Sachen zurückholen.
- Wüsten- und Ozeanstarts: Der Weg zum Wald ist lang; Dorf- oder Schiffswrack-Holz wäre schneller.
- Teure Kettenziele (Bogen, Item Frame, Feuerwerk) genauer schätzen.
