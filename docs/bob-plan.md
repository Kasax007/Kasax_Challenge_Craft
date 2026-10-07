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
| 5 | 2,3 | 0 | 7 | Sachen nach dem Tod zurückholen (Eröffnungszeit 2519 → 1240 s) |

Die Streuung zwischen Runden ist groß (gleiche Welten, aber Mobs und Zufall): ±0,5 Felder im
Schnitt sind Rauschen. Für belastbare Vergleiche braucht es mehr Welten (20+).

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

- Tode senken (Creeper, Ertrinken, Tropfstein, Zombies in Gruppen); Rüstung/Schild früh.
- Teure Erz-Ziele (Gold, 9 Roheisen, Redstone) scheitern oft: Strip-Mining-Tempo und Schätzung.
- Wüsten- und Ozeanstarts: Der Weg zum Wald ist lang; Dorf- oder Schiffswrack-Holz wäre schneller.
- Teure Kettenziele (Bogen, Item Frame, Feuerwerk) genauer schätzen.

## 7. Bewertung: wie Bob denkt (Stand nach der Testphase)

### So läuft eine Entscheidung ab

1. **Reflexe** (jede halbe Sekunde, vor allem anderen): Creeper ausweichen, bei wenig Leben
   zurückziehen oder eingraben, zurückschlagen, essen, Wasser-MLG beim Fallen.
2. **Grundbedarf**: Ist er gestorben, holt er zuerst seine Sachen zurück; bei echtem Hunger sucht er
   Essen. Danach kommt die Eröffnung (Holz, Werkbank, Steinspitzhacke und -axt).
3. **Spielplan** (`LockoutStrategist`, bei jeder Brettänderung, nach dem Tod und alle 2 Minuten):
   - Er schätzt für jedes offene Feld die Kosten in Sekunden.
   - Er bewertet Investitionen: Eisen-Kit oder Diamanten, wenn sie mehrere Felder genug verbilligen.
   - Er gibt einen Bonus für den Nether-Trip, wenn dort mehrere Felder liegen.
   - Er blockt Felder, bei denen der Gegner kurz vor dem Ziel steht.
   - Er führt eine Einkaufsliste (Rohstoffe aller Felder zusammen) und eine Mitnahme-Liste
     (Feuerstein, TNT, Zuckerrohr).
4. **Zielwahl** (`LockoutBrain`):
   - Gewählt wird das billigste Feld abzüglich Bonus, mit einem Schritt Vorausschau: welches Feld
     das nächste am meisten verbilligt.
   - Felder, die schon einmal ihr Zeitbudget überzogen haben, gelten danach als teurer.
5. **Planer** (`ObtainPlanner`):
   - Er rechnet einen Rezeptbaum durch (abbauen, töten, craften, schmelzen, Eimer füllen,
     Obsidian gießen).
   - Die Kosten kommen aus dem, was gesehen oder erinnert ist, aus der Seltenheit, der Tiefe und
     dem passenden Werkzeug.
   - Er simuliert das Inventar mit.
6. **Ausführung**:
   - Aufgaben laufen auf einem Stapel; Reflexe und Nebenaufgaben schieben sich davor.
   - Ein Watchdog gibt jedem Ziel das 3-Fache seiner Schätzung. Kommt Bob dabei gut voran,
     bekommt das Ziel mehr Zeit.
7. **Bewegung**: A*-Pfadsuche mit echten Kosten (graben, pfeilern, brücken, springen,
   schwimmen). Weite Strecken legt er in Etappen über die Oberfläche zurück.

### Stärken

- Er versteht fast alle Zieltypen und kann für etwa drei Viertel der Felder einen Weg planen.
- Wissen aus dem Spiel selbst: Rezepte, Loot und Drops kommen aus den Spieldaten; nichts davon ist
  von Hand gepflegt.
- Mit festen Welten und GameTests ist jeder Fehler reproduzierbar.

### Schwächen und was dagegen getan wird

| Schwäche | Folge | Maßnahme |
|---|---|---|
| Kostenschätzungen sind Heuristiken (Seltenheit, Tiefe) | Felder dauern ein Vielfaches (Kessel 77 s geschätzt, 676 s real) | Überziehungen verteuern das Feld (erledigt); nächster Schritt: gemessene Dauern je Wegart über Läufe hinweg speichern und die Schätzung damit kalibrieren |
| Jedes Feld wird einzeln geplant | Er holt dreimal Eisen statt einmal | Einkaufsliste fürs ganze Brett (erledigt) |
| Nur ein Schritt Vorausschau | Er wählt keine Reihenfolge nach Ort (alles in der Höhle, dann alles im Nether) | Phasen planen: Oberwelt-Block, Höhlen-Block, Nether-Block |
| Unerreichbares fiel aus dem Plan | Nether-Felder galten von der Oberwelt aus als unmöglich, Bob ging nie in den Nether | Der Weg dorthin wird jetzt mitgeplant (erledigt) |
| Röntgenblick des Gedächtnisses | Er grub zu eingeschlossenen Erzen | Nur noch freiliegende Blöcke; Höhlen als Abstieg (erledigt) |
| Fehlende Fähigkeiten | Feld „kein Weg bekannt“ | Liste aus den Komplett-Brettern, Fähigkeit für Fähigkeit nachrüsten |

### Komplett-Bretter (jedes Feld muss erfüllt werden)

`scripts/bot/bench-many.sh 3600 hard <seeds>` spielt bis zu 60 Minuten oder bis das Brett voll
ist. Der Bericht listet jedes offene Feld mit „kein Weg bekannt“ oder mit Plan, Starts, Fehlschlägen,
Überziehungen und verbrauchter Zeit. Das ist die Arbeitsliste für die nächsten Runden.

## 8. Komplett-Bretter: Befunde und Fähigkeiten (Runde 2)

Bob spielt ein Brett bis zu 60 Minuten und muss jedes Feld erfüllen. Der Bericht am Ende nennt
für jedes offene Feld, ob ein Weg bekannt ist und was versucht wurde.

### Was die Komplett-Bretter aufgedeckt haben (und behoben ist)

| Befund | Ursache | Behoben durch |
|---|---|---|
| Nie im Nether | Nether-Felder galten von der Oberwelt aus als unmöglich | Der Weg dorthin wird mitgeplant und auf alle Nether-Felder verteilt; eigene **Nether-Phase**, sobald der Eimer da ist |
| Eisen erst nach 30–50 Min. | Das lohnende Eisen-Kit wurde erkannt, aber nie geholt | **Kit-Phase** nach der Eröffnung: Eisen nach 76–255 s statt 1447–2899 s |
| Dreimal Eisen holen | Jedes Feld wurde einzeln geplant | **Einkaufsliste** fürs ganze Brett |
| Suche nach Roheisen- oder Kupferblöcken | Speicherblöcke hatten den Standard-Seltenheitswert | Werden nie gesucht |
| Wassereimer „unmöglich“ | Wasser war nur in Sicht bekannt; ein Fehlschlag sperrte Wasser für 10 Min. | Wasseroberflächen im Gedächtnis, Suche, kurze Sperre |
| Gehen bleibt nach Unterbrechung stehen | Ein Reflex hielt den Navigator an, die Aufgabe „lief“ weiter | Der Navigator nimmt unterbrochene Wege wieder auf |
| Kein Platz zum Portalgießen | Die Form braucht einen ebenen, freien Streifen | Bob gräbt sich den Platz samt Standreihe frei |
| „Air on Air“ an Kanten | Der Pfad startete über der Luft | Start vom Block, auf dem Bob wirklich steht |
| Erz per Röntgenblick | Das Gedächtnis kannte eingeschlossenes Erz | Nur freiliegende Blöcke; Höhlen als Abstieg |
| Zombie-Tode in Höhlen | Rückzug scheitert, Eingraben zu langsam | 2–3 Blöcke hoch bauen, außer Reichweite |

### Neue Fähigkeiten (jeweils mit GameTest)

Eier sammeln (Kuchen, Kürbiskuchen), Fisch im Eimer, Angeln bis zum Kugelfisch, Bogen und Armbrust
(Take Aim, Armbrust schießen, Zielblock), Schild-Block, absichtlich vom Skelett angeschossen
werden, Wasserflasche trinken („Trank trinken“), Honigwaben, Creeper-Explosion überleben, Monster
Hunter, beliebiges Tier zähmen, Dorf-Trips (Handel, Glocke, Lesepult, Dorfbett), Nether-Strukturen
und Nether-Gegenstände über den Trip, Lerngedächtnis über Spiele hinweg
(`config/challengecraft-bob-experience.properties`).

### Hinweis zur Zieldefinition

`have_10_hearts_missing` prüft `maxHealth - health >= 20`. Bei 20 maximalen HP ist das nur im
Moment des Todes erfüllt, deshalb fiel das Feld bisher nur beim Sterben. Vermutlich ist `>= 19`
gemeint (ein halbes Herz übrig).

### Noch offen (aus den Berichten)

Getränkter Pfeil, Armbrust mit Feuerwerk, Packeis/Eis (Behutsamkeit), Waldanwesen, Brauen
(Braustand, Wurftrank), Ende und Bosse; Tode durch Creeper und Zombie-Gruppen bleiben die größte
Zeitbremse.

### Komplett-Bretter in Zahlen (60 Min., schwer, Welten 33/77/11/22)

| Stand | Felder | Erstes Eisen | Eimer | Nether |
|---|---|---|---|---|
| Vor den Änderungen (Welten 33/77) | 10 / 5 | 972 / 1447 s | 1022 / 3337 s | nie |
| Mit Kit-Phase, Einkaufsliste | 8 / 8 / 3 | 255 / 237 / – s | 549 / 334 / – s | nie |
| Aktuell | 7 / 7 / 2 / 7 | 80 / 1199 / 2823 / 205 s | 134 / 1724 / 3494 / 337 s | einmal (Welt 22, Runde davor, 1976 s) |

Eisen und Eimer kommen meist früh. Der Engpass ist jetzt das Portalgießen im echten Gelände: Platz,
Erreichbarkeit, Lava unter Tage. Die Fehlermeldungen nennen inzwischen den genauen Grund, und jede
Runde behebt einen weiteren. Bisher hat Bob den Nether einmal selbst erreicht.

## 9. Lange Komplett-Bretter (bis 3 Stunden, ganzes Brett ausgespielt)

Die Runden laufen jetzt ohne Mehrheits-Sieg (`CHALLENGECRAFT_FULL_BOARD=1` in `scripts/bot/bench.sh`):
vorher endete das Spiel bei 13 von 25 Feldern, und Bob stand danach im Wartebereich.

### Was die langen Runden aufgedeckt haben (behoben, jeweils mit GameTest wo möglich)

| Befund in der Runde | Ursache | Behebung |
|---|---|---|
| Nie wieder aus dem Nether zurück | Fake-Spieler bestätigte den Dimensionswechsel nie | Bestätigung wie ein Client; Portal-Hin-und-zurück-Test |
| 11 558 Mal „go back through the portal“ | Portal nicht gemerkt, Rückweg ohne Kosten | Portal-Gedächtnis beider Enden, echte Rückweg-Kosten, Pause nach Fehlschlag |
| 22 Min. in Grubenwasser | Navigator plante „Säule im Wasser“ ohne Halt | Wandsprung aus dem Wasser, Stützblock, Fortschritts-Wächter; zwei Grubentests |
| Hochklettern aus y −30: 2 Blöcke/Min. | eine Wegsuche über 90 Blöcke | Etappen zu 16 Blöcken; Test 55 Blöcke Stein |
| Verlorene Höhlen-Rückwege | nur ein „Höhleneingang“, nur beim Bergbau | Brotkrumen-Spur alle 6 Blöcke, Rückweg entlang der Spur |
| Spitzhacke bei 3/131 im Diamantenbergbau | Werkzeug-Reserve nur zwischen Zielen; abgenutzte Werkzeuge wurden geschont | Ersatz auch mitten im Ziel; Werkzeuge der Reihe nach aufbrauchen |
| 40 Min. „mine birch_log“ | Rucksack voll | Ballast abwerfen (nie was das Brett/der Plan braucht) |
| Ofen-Suche über 1000 Blöcke | Abbau-Weg für Dinge, die nirgends herumliegen | Seltenes nur abbauen, wenn gesehen; sonst herstellen |
| Pferde im Schnee gesucht | Mob-Suche ohne Lebensraum | Lebensräume aus den Spawn-Listen des Spiels; Weg zum bekannten Biom |
| Nachtjagd auf Hexen/Schleim/Höhlenspinnen | „überall“ falsch bestimmt | nur häufige Monster oder solche, deren Lebensraum man betritt |
| Portal gießen: „no lava left“ | kleine Lava-Pfützen, Suche zu eng | Pool mit ≥ 6 Quellen, Rückfall auf bekannte Lava, gescheiterte Pools gemieden, nur Pools unter freiem Himmel |
| Wasser unter dem Berg: 2 Min. „Etappen“ am Ort | „weit weg“ in 3D statt über Grund | horizontale Entfernung, näher herantreten, Fehlversuche zulassen |
| Schreiter auf Lava: 8× gescheitert | Nahkampf erreicht ihn nicht | Bogen/Armbrust als Rückfall |
| Nether: zwei Spitzhacken in einer Minute | Erkundung zielte auf die Bedrock-Decke | `Explorer.ground()`: unter einer Decke nahe der eigenen Höhe |

### Neue Fähigkeiten in diesem Abschnitt

Zielgerichtetes Handeln mit Dorfbewohnern (Leveln, Reroll, Arbeitsplatz-Konkurrenz, Nitwits, zweite Station),
Weltkarte (Atlas pro Dimension) und Langstrecken-Lauf, nächtliche Jagdrunde, Monster in Sicht mitnehmen,
Werkzeug- und Block-Reserve, Armbrust mit Feuerwerk laden, Nether-Zutaten holen und zu Hause herstellen
(Komparator), getippte Pfeile von Streunern/Sumpfskeletten. Abdeckung: 55 von 312 Zielen ohne bekannten Weg (vorher 72).

### Offener Punkt der Testumgebung

In der Benchmark-Welt kommt Bob im Nether an genau den Oberwelt-Koordinaten an, ohne Ausgangsportal
(„crossed from overworld at 1382, 73, -2535 … to the_nether at 1382, 73, -2535, portal here: none“).
In der GameTest-Welt (auch mit `tick sprint`) teilt das Spiel korrekt durch 8 und baut ein Portal.
Bob ist abgesichert (merkt sich die Ankunftsstelle, besorgt notfalls Obsidian), die Ursache in der
Welt-Erzeugung der Challenge ist noch nicht gefunden.

## 10. Runde 3: Plan (Spieler-Realismus, Nether, restliche Ziele)

### Analyse

- **Portal-Rätsel der Testwelten**: Nach einem Tod zeigte die Netzwerk-Schicht des Bots noch auf
  den toten Körper. Jeder spätere Teleport, auch der durchs Portal, bewegte die Leiche. Bob kam im
  Nether bei seinen Oberwelt-Koordinaten an, ohne Portal. Die GameTests trafen das nie, weil Bob
  dort vor dem Portal nicht starb. → Behoben, Test `portal_after_death`.
- **Allwissen**: Gedächtnis, Planer und Blocksuche kannten jeden freiliegenden Block, auch in
  Höhlen 40 Blöcke unter Bob. Deshalb grub er zu Seen, die er nie gesehen haben konnte.
  → Sichtmodell: Ein Block gilt als gesehen, wenn eine offene Seite Himmelslicht hat (Oberfläche,
  Schlucht, Höhleneingang) oder er in Sichtlinie in bis zu 24 Blöcken liegt. Unterirdische
  Strukturen erkennt Bob erst aus der Nähe.
- **Abdeckung**: Ehrlich gemessen (Nether-Ziele so, als stünde Bob im Nether) sind es rund
  55 Ziele auf dem Brett ohne Weg. Sie fallen in drei Gruppen: Nether (Gold, Tauschhandel, Bastion,
  Festung, Brauen), einfache Oberwelt-Stunts (Apfel, Schwein reiten, Lagerfeuer, Pulverschnee,
  Bauhöhe, Scharfschütze, Phantom) und teure Ausnahmen (Eis/Sculk nur mit Behutsamkeit, Ende,
  Waldanwesen).

### Umsetzung (Reihenfolge)

1. Portal-Fehler, Sichtmodell, Wasser wie ein Spieler (nächste gesehene Quelle, Fluss/Meer/Sumpf
   aus dem Atlas, sonst erkunden) — erledigt.
2. Reserve: immer Holz (12 Bretter-Äquivalent) und Bruchstein (16) dabei — erledigt.
3. Fische: Eimer-Fisch, Kugel- und Tropenfisch per Jagd oder Angel — erledigt.
4. **Nether**: Goldrüstung (Piglins bleiben friedlich), Tauschhandel in einer Grube (Piglin wird
   mit Gold hineingelockt, Bob steht daneben im Graben, Beute fällt vor seine Füße), Bastion-
   Plünderung (Truhen, Goldblöcke), Festung, Lohenruten, Brauen, Schreiter reiten,
   Witherskelett, Ghast-Feuerball zurückschlagen.
5. **Schiffswracks** im Vorbeigehen plündern (Eisen, Ausrüstung), Rüstung anziehen.
6. **Schleim**: Sumpf bei Nacht (Biom aus dem Atlas), Schleim unter Tage nur wenn gesehen.
7. **Kampf**: kritische Treffer, Schild, Creeper-Abstand, Rückzug, Bogen gegen Fliegendes.
8. **Bündel** für die Inventarverwaltung: Kleinkram ins Bündel statt wegwerfen.
9. **Restliche Ziele** einzeln mit GameTest, wo es geht.
10. **Komplett-Bretter** als Benchmark (aus einer eigenen Kopie, damit Entwickeln weiterläuft).

### Nachtrag zum Plan (Hinweise vom Spieler)

11. **Sicht wie ein Spieler auf 24 Chunks**: Bob fordert 24 Chunks Sichtweite an; die
    Benchmark-Server laufen mit Sicht- und Simulationsdistanz 24. Landschaft, Atlas, Strukturen und
    Gedächtnis reichen bis zur Renderdistanz. Mobs sieht er bis 128 Blöcke in Sichtlinie, so weit,
    wie der Server sie einem Spieler zeigt.
12. **Festung triangulieren**: zwei Enderaugen von zwei Standorten, die Flugrichtungen schneiden
    sich an der Festung (wie bei Ninjabrain Bot); dorthin, ein Kontrollauge, dann hinab.
13. **Portal gießen zu 100 %**: die Speedrunner-Methode an einer geraden Kante des Lavasees, mit
    Form, Wasserrinne und Auffangmulde; Platz wird notfalls freigegraben; GameTests für viele
    Seeformen.
14. **Abschluss**: Abdeckung aller Ziele prüfen, mindestens zehn Komplett-Bretter, Auswertung.

## 11. Runde 3: Umsetzung

### Portal gießen, das nicht schiefgeht (`LavaPortalTask`)

Grundlage ist das Verhalten des Spiels (aus dem Spielcode geprüft): Eine Lava-**Quelle** wird in dem
Moment zu Obsidian, in dem Wasser über oder neben ihr ist. Wasser beginnt erst nach 5 Ticks zu
fließen. Daraus folgt ein Ablauf, bei dem kein Wasser je den Lavasee erreicht:

1. **Platz wählen**: ein Standplatz am See, von dem aus Lava im Eimer-Bereich liegt. Zwei Blöcke
   davor liegt die Portalebene (4 breit, 5 hoch, um einen Block abgesenkt). Sie wird so gewählt, dass
   neben keinem ausgehobenen Block Flüssigkeit liegt. Bewertet wird nach Grabaufwand, fehlender
   Rückwand und Lava in Reichweite. Erde, Stein oder ein Hang werden dafür ausgehoben, auch in einer
   Höhle.
2. **Ausheben**: die Ebene und der Raum davor, von oben nach unten. Die unteren Ecken bleiben stehen
   und dienen als Zielfläche.
3. **Form**: hinter jede Zelle der Ebene ein Block. Das ist die Gussform, gegen die jeder Eimer
   gegossen wird.
4. **Gießen, Rahmenblock für Rahmenblock** (unten, oben über die Eckzellen, dann die Seiten von oben
   nach unten):
   - Wasser in die Nachbarzelle;
   - im nächsten Tick Lava in die Rahmenzelle, die sofort zu Obsidian wird;
   - im selben Tick das Wasser zurück in den Eimer.

   Vor dem Wasser prüft Bob drei Sichtlinien: die für das Wasser, die für die Lava und die zum
   Zurückschöpfen, auch am gleich entstehenden Obsidian vorbei. Klappt das Schöpfen trotzdem nicht,
   kommt ein Block in das Wasser.
5. Anzünden, hindurch.

GameTests: Wiese, kleiner 2×5-See (genau 10 Lava), Hang, Höhle ohne Platz, zerfranster See mit Fluss
daneben, überlaufender See. **36 von 36 Läufen** sind bestanden, dazu die zwei alten Portal-Tests.

Das ist nicht wörtlich die Speedrunner-Methode an der Seekante. Das Prinzip ist aber dasselbe:
Wasser an der richtigen Stelle, Lava hinein, Wasser zurück. Der Unterschied: Das Wasser fließt hier
nie und berührt den See nie. So ist das Ergebnis unabhängig von der Form des Sees.

### Festung triangulieren (`EyeTrackTask`)

- Erstes Auge: Startpunkt und Flugrichtung werden gemessen. Das Auge fliegt im Spiel exakt geradlinig
  auf den Festungs-Chunk zu.
- 40 Blöcke seitlich: zweites Auge.
- Schnittpunkt der beiden Linien, dorthin, hinabgraben.
- Liegen die Linien zu parallel, macht Bob einen größeren Seitenschritt. Danach wie früher: dem Auge
  nach.
- Überlebende Augen hebt Bob auf. Die Koordinaten merkt er sich für später.

Ergebnisse:
- GameTest: Schätzung 0,00002 Blöcke neben dem Ziel.
- Echte Welt (Seed 4242): Bob berechnet 1008, 912; `/locate` sagt [1008, ~, 912]. Nach 1360
  Blöcken Weg steht Bob in der Festung.

### Weitere Befunde aus den Läufen (behoben)

- **Holz am Spawn**: Bob lief zu einem 970 Blöcke entfernten Fichtenwald, weil die Holzart vorab
  feststand. Jetzt geht es zum nächsten Wald gleich welcher Art, und Bob wechselt den Plan, sobald
  anderes Holz in Sicht ist.
- **Unerreichbares**: Bäume oben auf einem Tafelberg führten zu 40 Minuten Schleife (0 Felder).
  Unerreichbare Ziele samt ihrem Baum oder ihrer Ader gelten jetzt 5 Minuten lang für alle Aufgaben
  und den Planer als "nicht zu haben".
- **Ertrinken** in einem gefluteten Tunnel: Bei Luftnot sucht Bob jetzt zuerst die nächste Stelle
  mit Luft.
- **Creeper**: Kommt einer auf Bob zu und zischt noch nicht, tötet Bob ihn (mit Waffe) oder geht
  weg.
- **Äpfel**: Laub-Drops werden so oft gewürfelt, bis die seltenen sichtbar sind (Apfel aus
  Eichenlaub).
- **Zurück zum Portal**: Bob fiel im Nether einen Abhang hinunter und gab den Rückweg auf. Jetzt
  geht er bei großem Höhenunterschied direkt auf das Portal zu und gräbt oder baut sich hinauf.
- **Benchmark-Skripte**: `SERVER_PORT`/`RCON_PORT` erlauben zwei Läufe parallel. Am Ende jedes
  Laufs steht eine Abdeckungsliste (`[COVERAGE]`).

## 12. Runde 4: Neuausrichtung (Analyse und Plan nach dem Live-Test)

### 12.1 Ziel und Messgrößen

- **Langfristziel**: ein komplettes Lockout-Brett (25 Felder) auf *schwer* in einer zufälligen Welt
  in höchstens **90 Minuten**.
- **Zwischenziel**:
  - alle Punkte aus dem Live-Test umgesetzt;
  - Bob spielt ein komplettes Brett eigenständig zu Ende (Zeit noch offen; Tests dürfen mit
    `tick sprint` beschleunigt laufen).
- **Messgrößen pro Benchmark** (10 feste Seeds):
  - Felder nach 30, 60 und 90 Minuten;
  - Zeit bis zum ganzen Brett;
  - Tode;
  - Leerlauf;
  - Wegsuche-Fehler pro Minute;
  - mittlere Laufgeschwindigkeit (Blöcke/s gegenüber Sprint);
  - abgebrochene Ziele.
- **Hinweis zur Brettzusammensetzung**: Manche Felder sind in 90 Minuten für niemanden machbar
  (Endstadt, End-Gateway, Behüterhaus, Ancient City, Behutsamkeit-Ziele). Ein "komplettes Brett"
  braucht eine Brettauswahl ohne solche Felder. Das Benchmark-Brett wird dafür auf die Felder
  begrenzt, die ein guter Spieler in 90 Minuten schaffen kann. Die übrigen werden getrennt gezählt.

### 12.2 Wo Bob steht (Ist-Analyse)

**Benchmarks der letzten Stände** (60 Minuten, schwer):

| Seed | Felder | Bemerkung |
|---|---|---|
| 11 | 6–7 | |
| 22 | 7 | |
| 77 | 6 | |
| 66 | 4–7 | |

- Nether meist nicht erreicht.
- Tode: 1–2 pro Lauf.
- Durchsatz: **etwa 7 Felder pro Stunde**. Das Ziel braucht rund 17 pro Stunde, also das
  2,5- bis 3,5-Fache.
- Die letzte Zehnerreihe wurde durch einen Container-Neustart abgebrochen. Ihre ersten Läufe lagen
  bei 7 Feldern nach 42 bzw. 51 Minuten.

**Live-Test des Spielers** (Seed -8848941644679110190), am Chatverlauf und am Code geprüft:

| Beobachtung | Bestätigt durch | Ursache im Code |
|---|---|---|
| "navigation failed" fast im Minutentakt (35× in 30 min) | Chat | Suche bricht nach **5000 Knoten** ab (Baritone: 0,5–4 s Suche, Hunderttausende Knoten); nach 12 Neuversuchen Abbruch; Teilpfade ohne Mindestfortschritt |
| Springt gegen zwei Blöcke hohe Stufen, baut erst danach ab | Beobachtung | Ausführung springt, sobald das Ziel höher liegt, ohne Kopffreiheit und Weltzustand zu prüfen; Fehler fällt erst nach 50 Ticks "festgesteckt" auf |
| Hügel schlecht, wirkt ziellos | Beobachtung | kein Sprinten (`setSprinting(false)` beim Laufen), Teilpfade kurz, an jedem Segmentende Stillstand bis zur nächsten Suche |
| Hüpft über Wasser statt zu schwimmen | Beobachtung | im Wasser wird dauerhaft "springen" gehalten; kein Sprint-Schwimmen |
| Brücke über die Schlucht abgebrochen | Beobachtung | jede neue Suche verwirft den alten Plan; keine Zusage an angefangene Bauten (Hysterese) |
| Skelett und Baby-Zombie fast/ganz tödlich, Tod nach 5 Minuten | Chat ("slain by Zombie" 17:27) | Reflexe nur bei wenig Leben bzw. zischendem Creeper; keine Bedrohungsbewertung (Fernkämpfer in Sichtlinie, schnelle Babys), keine Rüstung früh |
| Erz und Kohle liegen gelassen, Eisenerz direkt vor ihm ignoriert | Beobachtung | Abbau-Aufgabe nimmt das *nächste bekannte* Ziel aus dem Gedächtnis, nicht die Ader vor ihm; "unterwegs mitnehmen" nur für eine kurze Liste |
| Schmilzt mit Holz, baut Öfen immer neu, schmilzt in 3er-Portionen | Chat ("craft furnace" 17:23 und 17:41, "smelt iron x3") | SmeltTask nimmt den billigsten Brennstoff im Inventar; kein Kohle-Ziel; jede Teilaufgabe schmilzt nur ihren Bedarf |
| Nur Spitzhacke dabei | Beobachtung | nur Spitzhacke und Axt in der Eröffnung; Schwert und Schaufel nur bei Bedarf |
| Dorf "geplündert", aber nichts getan | Chat ("done: raid the village" direkt nach "navigation failed") | RaidTask öffnet höchstens 3 Truhen und meldet "erledigt", wenn keine da ist; kein Bett, keine Felder, keine Glocke |
| Schneeball zuerst, ohne kaltes Biom in Sicht | Chat ("Snowball (~5 s)", dann "off to … -978, 63, 1111") | Schätzung (5 s) und Suche widersprechen sich; die Schätzung bewertet Fernes zu billig |
| 3 Blumenarten geplant, Blumen ringsum ignoriert | Chat (Plan nennt 3_flower_types, Mitnahmeliste nicht) | keine Gelegenheits-Logik für sichtbare Ziele |
| Rohstoffblock direkt nach dem Eisenabbau nicht mitgemacht | Chat | Ziele werden einzeln geplant, nicht nach Ort gebündelt |
| Redstone kurz vor dem Ziel abgebrochen, zurück zum Dorf | Chat ("still on … ~32 s left", 90 s später "takes too long" → "raid the village") | starres Zeitbudget mit höchstens 2 Verlängerungen statt Neubewertung nach Lage (tief unten, große Höhle) |
| Nachts Spinne geplant, aber nicht gesucht | Chat | Jagdrunde sucht kurz, dann "found no spider"; keine gezielte Nachtroutine |
| Schätzwert ~888 Mio. s für den Netherweg | Chat ("flint and steel 888888913") | unendlicher Wert sickert in Summen durch |

**Kern der Analyse**: Bob *kann* fast alles einzeln (über 300 Ziele, über 85 GameTests). Er verliert die
Zeit an zwei Stellen:
1. **Bewegung**: kurze Suchen, Stillstand an Segmentgrenzen, kein Sprint, schlechte Ausführung von
   Sprüngen und Schwimmen, häufige Abbrüche.
2. **Entscheidung**: Ziele werden einzeln und nach Punktschätzungen gewählt; starre Zeitbudgets
   brechen gute Läufe ab; keine Bündelung nach Ort und Gelegenheit; wenig Wahrnehmung dessen, was
   gerade direkt vor ihm liegt.

Das passt zu dem, was der Spieler gesehen hat. Ein Mensch läuft zielstrebig, nimmt mit, was am Weg
liegt, bündelt nach Ort ("jetzt unter Tage: Redstone, Diamanten, Eisen, Kohle, Höhlenspinne") und
bricht nur ab, wenn die Lage sich wirklich verschlechtert.

### 12.3 Recherche: was wir übernehmen

**Baritone** (nur als Vorbild, keine Abhängigkeit):
- **Zeitbudget statt Knotenbudget**: 0,5 s pro Segment (bis 2 s, wenn noch nichts gefunden ist),
  dabei Zehn- bis Hunderttausende Knoten.
- **Bester Teilpfad über mehrere Gewichte** (1,5 bis 10 auf die bisherigen Kosten) mit
  **Mindestfortschritt von 5 Blöcken**. Ein Teilpfad, der nicht voranbringt, gilt nicht.
- **Kosten in Ticks** aus der echten Spielphysik:
  - Gehen 4,63, Sprinten 3,56, Schwimmen (Sprint) 6,99, Kriechen 15,4;
  - Sprung als Fallzeit-Differenz;
  - Fallkosten nach Fallhöhe;
  - Zuschläge: Platzieren +20, Abbauen +2, Springen +2.
- **Vorausplanen**: Das nächste Segment wird ab dem Ende des laufenden geplant (150 Ticks vorher),
  danach werden die Pfade zusammengefügt. Kein Stillstand an Segmentgrenzen.
- **Ausführung je Bewegungsart** mit eigener Zustandsprüfung:
  - Stufe hoch erst springen, wenn ausgerichtet, nah genug (≤ 1,2) und der Kopf frei ist;
  - Diagonale, Fallen, Parkour, Säule und Brücke jeweils eigen;
  - Abweichen vom geplanten Weltzustand wird sofort erkannt, nicht nach 50 Ticks.
- **Sprint-Entscheidung** pro Tick: Sprinten, wenn die nächsten Bewegungen es zulassen.
- **Bevorzugung des alten Pfads** (Faktor 0,5) bei neuer Suche: verhindert Hin und Her und
  abgebrochene Brücken.
- **Weitere Strecken**: grobe Wegpunkte, dann feine Suche je Segment.

**Zielwahl**, als Algorithmen aus Spiele-KI und Planung:
- **Nutzenbasierte KI (Utility AI)**: Jede Handlung bekommt einen Wert "erwartete Felder pro
  Zeit", bewertet aus dem, was Bob über die Welt weiß und gerade sieht. Statt fester Regeln wird
  laufend das Beste gewählt.
- **Erwartete Restzeit statt Punktschätzung**:
  - Suchzeit als Ereignisrate (z. B. "Redstone pro Minute Höhle auf Höhe −50"), aus Minecraft-Wissen
    über Erzverteilung, Biome, Tageszeit und Mob-Spawns;
  - Bayes-Aktualisierung mit dem, was Bob tatsächlich sieht und durchsucht hat.
- **Optimales Abbrechen**: Weitermachen, solange die erwartete Restzeit kleiner ist als der Wechsel
  zur besten Alternative plus Wechselkosten. Keine starren Budgets mehr.
- **Routenplanung als Orientierungslauf-Problem** (*orienteering problem*): Felder an Orten,
  Reisezeiten, gemeinsame Zutaten.
  - Gruppierung nach Zonen: hier an der Oberfläche, Bergbau-Ausflug bis Tiefe X, Dorf, Nether.
  - Lösung mit Strahlsuche (*beam search*) über Reihenfolgen der nächsten 4–8 Felder.
  - Bewertet nach Feldern pro Minute, mit geteilten Vorleistungen (gemeinsame Stückliste).
- **Gelegenheiten**: Ein Umweg lohnt, wenn die eingesparte spätere Zeit größer ist als der Umweg
  jetzt (Blumen für "3 Blumenarten", Erz der Stückliste, Kohle vor dem Schmelzen, Spinne nachts).

### 12.4 Plan (Reihenfolge, je mit Abnahme)

**Phase A: Bewegung (zuerst, größter Hebel)**
1. **A1 Suche**:
   - Zeitbudget pro Tick (verteilt über mehrere Ticks, ohne den Server zu blockieren);
   - bester Teilpfad mit Mindestfortschritt und mehreren Gewichten;
   - Kosten nach Spielphysik (Sprint, Sprünge, Fallen, Schwimmen, Abbau mit Werkzeug, Platzieren);
   - Bevorzugung des laufenden Pfads.
2. **A2 Vorausplanen und Zusammenfügen**: das nächste Segment schon während des Laufens; kein
   Stillstand, kein "navigation failed" bei weiten Wegen.
3. **A3 Ausführung neu**, eine Routine je Bewegungsart, mit Prüfung des Weltzustands vor jedem
   Schritt:
   - Sprinten auf geraden Stücken;
   - Stufe hoch nur bei freiem Kopf;
   - Sprint-Schwimmen an der Oberfläche;
   - Brücke mit Zusage bis zum Ende.
4. **A4 Weite Wege**: grobe Wegpunkte über die Höhenkarte (Berge, Wasser, Schluchten als Kosten),
   feine Suche je Abschnitt.
5. **A5 Fehlerbehandlung**:
   - gestaffelte Strategien (andere Gewichte, Abbauen und Bauen erlauben, Umweg-Wegpunkt);
   - "navigation failed" nur noch im Debug-Log;
   - Zähler für Fehler pro Minute im Benchmark.
6. **Abnahme**:
   - neue Bewegungs-GameTests: Hügel-Treppen, zwei Blöcke hohe Wand, Schlucht mit Brücke, See
     queren (Zeit gemessen), Höhle hinab, Wald;
   - ein **Navigations-Benchmark** in echten Welten mit zufälligen Zielen in 50–300 Blöcken.
     Ziele: über 95 % erreicht, mittleres Tempo über 70 % des Sprinttempos, unter 1 Fehler pro
     10 Minuten.

**Phase B: Zielwahl neu (nutzenbasiert, nach Wahrscheinlichkeit)**
1. **B1 Weltmodell mit Wahrscheinlichkeiten**: Zu jedem Rohstoff, Mob und Ort Fundraten nach Biom,
   Höhe und Tageszeit, verbunden mit dem Gesehenen und dem Durchsuchten. Ergebnis: erwartete Zeit
   *und* Unsicherheit. Schätzung und Suche nutzen **dieselbe** Quelle (behebt den Schneeball-Fall).
2. **B2 Stückliste fürs ganze Brett**: Eisen, Kohle, Redstone, Holz, Werkzeuge. Der Abbau richtet
   sich danach (ganze Ader nehmen, wenn die Liste es will).
3. **B3 Routenplaner**:
   - Zonen-Ausflüge mit Strahlsuche über die nächsten Felder;
   - der Plan nennt die Ausflüge ("Oberfläche hier: Blumen, Schneeball nein; Bergbau bis −50:
     Redstone, Diamant, Eisen, Kohle, Höhlenspinne");
   - neu geplant wird nur bei Ereignissen (neues Gesehenes, Feld vergeben, Tod, Nacht).
4. **B4 Abbruch nach Lage**: erwartete Restzeit wird laufend aktualisiert (tief unten, große Höhle:
   Redstone sehr wahrscheinlich bald); Wechsel nur mit Vorteil über den Wechselkosten.
5. **B5 Gelegenheiten**: Sichtbares, das offene Felder oder die Stückliste bedient, wird mitgenommen,
   wenn sich der Umweg lohnt.
6. **B6 Tag und Nacht**:
   - nachts Monster-Felder und Bergbau;
   - tagsüber Oberfläche;
   - Bett mitnehmen und schlafen, wenn nachts nichts zu tun ist.
7. **Abnahme**:
   - Szenario-Tests für die Fälle aus dem Live-Test: Blumen mitnehmen, kein Schneeball ohne Kälte,
     Redstone nicht abbrechen, Rohstoffblock beim Eisen;
   - Benchmark: Felder pro Stunde mindestens ×1,5 gegenüber dem Stand nach Phase A.

**Phase C: Ressourcen, Werkzeuge, Dorf, Kampf**
1. **C1 Werkzeugsatz**: Spitzhacke, Axt, Schwert, Schaufel stets dabei (Stein, später Eisen).
2. **C2 Schmelzen in einem Rutsch**:
   - Kohle bevorzugt (am Weg mitgenommen);
   - ein Ofen, der mitgenommen wird;
   - während der Ofen läuft (Dauer berechnet), wird in der Nähe gesammelt.
3. **C3 Abbau**: ganze Adern, Erz am Weg, Kohle für den Brennstoffbedarf.
4. **C4 Dorf richtig**:
   - Truhen, Bett mitnehmen;
   - Felder ernten → Brot;
   - Glocke und Handel, wenn auf dem Brett.
5. **C5 Kampf und Sicherheit**:
   - Bedrohungsbewertung je Mob (Skelett in Sichtlinie, Baby-Zombie, Creeper);
   - Schild und frühe Rüstung, Deckung, Nachtregeln.
6. **Abnahme**: weniger als 0,5 Tode pro Stunde im Benchmark, Szenario-Tests je Punkt.

**Phase D: Messen und nachschärfen**
- 10 Seeds, volles Brett, 90 Minuten.
- Kennzahlen wie in 12.1, Auswertung nach Zeitfressern (Bewegung, Suche, Leerlauf, Tod).
- Danach gezielt die größten Zeitfresser angehen, bis das Brett in 90 Minuten fällt.

### 12.5 Arbeitsweise

- Jede Phase in kleinen, getesteten Schritten. Zu jedem Schritt GameTests und eine Messung vorher
  und nachher.
- Benchmarks laufen in eigenen Arbeitskopien parallel zur Entwicklung (zwei Server, eigene Ports).
- Ergebnisse und Abweichungen hier im Dokument.

## 13. Runde 7: Graben wie ein Spieler, Chancen statt Zahlen, Routen nach Ort

Anlass: Rückmeldung vom Spieler nach dem Live-Test:

- Bruchstein aus einem 1×1-Schacht, aus dem Bob sich mit genau diesem Bruchstein wieder
  herausbaut;
- eine Eisenader nur halb abgebaut, obwohl Schild und Eimer noch Eisen brauchen;
- Erz-Hüpfen an der Oberfläche statt gezieltem Graben;
- Kampf gegen Skelette und Creeper;
- der Wunsch nach Wahrscheinlichkeiten und nach Routen gebündelt nach Ort.

### 13.1 Kampf

- **Skelette**:
  - Bob läuft im Sprint direkt hin, mit einem Seitschritt, sobald der Bogen voll gespannt ist
    (der Pfeil fliegt dorthin, wo er stand);
  - in Reichweite folgen die Schläge mit Sprung-Crit;
  - bewaffnet und gesund greift Bob an; den Schild hebt er nur, wenn er nicht kämpft;
  - Messung, 1 gegen 1 auf HARD: vorher 12–16 s Kampf, ~12 HP übrig; jetzt ~5 s, ~16 HP übrig.
- **Creeper**:
  - Bob schlägt knapp außerhalb der drei Blöcke zu, ab denen der Creeper zischt (der Arm
    erreicht den Körper, nicht die Mitte);
  - der Sprint-Schlag wirft ihn zurück;
  - solange der Arm sich erholt, hält Bob Abstand;
  - Messung: 5 von 5 getötet, keine Explosion, 4–15 s.
- Neue Tests: `skeletonDuel`, `skeletonShield`, `creeperKill`. Kampftests mit echten Mobs und
  Tests, die die Uhr verstellen, laufen in eigenen Test-Umgebungen.

### 13.2 Graben

- **Gestein** (Bruchstein, Erde) kommt nur noch aus einer trockenen, hellen Wand auf Körperhöhe,
  nie unter den eigenen Füßen weg. Gibt es keine Wand, gräbt Bob eine **Treppe** nach unten; sie
  ist zugleich der Rückweg zu Fuß. Test: 20 Bruchstein in ~23 s, kein Block verbaut.
- **Abstieg zu Erzen** ist immer eine Treppe, kein Schacht:
  - Trifft die Treppe auf eine Höhle, lässt Bob sich höchstens 3 Blöcke hinunterfallen, sonst
    gräbt er auf gleicher Höhe weiter.
  - Ersatz-Steinspitzhacken plant er nach Blockzahl ein (3 pro Ebene), oben vor dem Abstieg.
  - Zerbricht die Spitzhacke unten, baut er sofort eine neue.
- **Ganze Adern**: Eine Erzader wird immer leer gemacht, auch über die gewünschte Menge hinaus.
  Der Eisenbedarf des Kits (Spitzhacke, Eimer, Schild) steht mit auf der Einkaufsliste.
- **Gezielt statt hüpfen**: Werden noch 3 oder mehr Erze gebraucht, ist ein gemerktes einzelnes Erz
  nur einen kurzen Weg wert (bis ~25 s). Sonst geht Bob dorthin, wo das Erz häufig ist (Höhle oder
  Treppe auf Erz-Höhe).
- Neue Tests: `cobbleStairs`, `cobbleFromWall`, `veinBeyondCount`, `digIntoCave`.

### 13.3 Chancen statt einer Zahl (B1)

Der Planer führt zu jedem Item drei Werte statt einem:

| Wert | Was er ausdrückt |
| --- | --- |
| **Kosten** | erwartete Sekunden |
| **Suchanteil** | der Teil davon, der Glück ist: ein Block noch nicht gesehen, ein Mob noch nicht getroffen |
| **Ankerort** | wo die Arbeit liegt: gesehener Block, Dorf, Lebensraum eines Mobs, Tiefe eines Erzes, oder „hier“ |

Woraus die Werte kommen:

- **Gesehenes**: ein Weg, also sicher.
- **Biom in Sicht, das für den Block bekannt ist**: Weg plus kurze Suche.
- **Nichts davon**: Suche nach Seltenheit; bei Erzen liegt der Ankerort auf Erz-Höhe unter Bob.
- **Monster**:
  - an der Oberfläche bei Tag zählt die Wartezeit bis zur Nacht (sicher) plus die Suche;
  - unter Tage und im Nether ist es jederzeit dunkel.
- **Handel**: das Dorf ist der Ankerort; ein arbeitsloser Dorfbewohner, der einen Arbeitsplatz
  annehmen muss, ist zur Hälfte Glück.
- **Tauschhandel mit Piglins**: zur Hälfte Glück.

Ein Ziel wird so zu einer Verteilung: der sichere Teil plus eine exponentiell verteilte Suche.
Daraus folgt die Chance, innerhalb der Zeit fertig zu sein, die das Ziel bekäme:
P(fertig in t) = 1 − e^(−(t − sicher)/Suche).

Zwei Zusätze:

- **Erfahrung**: Was ein Ziel in früheren Spielen oder Versuchen länger gedauert hat, zählt
  ebenfalls als Glücksanteil.
- **Todesrisiko pro Minute**, abhängig von Nacht, Nether, Monstern in der Nähe, Rüstung und HP:
  Ein Tod kostet etwa 150 s; Kampfziele zählen 1,5-fach, Nether-Ziele von der Oberwelt aus
  bekommen den Nether-Zuschlag.

Chat-Beispiel: `goal: Ring a Bell (~71 s, of that ~71 s luck, 95% within 213 s, ...)`.

### 13.4 Routen nach Ort (B3)

- **Suche**: Das nächste Ziel ist der erste Schritt der besten Route durch die nächsten bis zu
  4 Ziele. Gesucht wird per Beam-Search über Reihenfolgen der zehn schnellsten Felder.
- **Kosten je Abschnitt**: der Weg vom Ende des vorigen Abschnitts zum Ankerort, plus die eigene
  Arbeit.
- **Wertung**: erwartete Felder pro Sekunde. Pro Abschnitt zählen die Erfolgschance (fertig vor
  dem Abbruch, ohne Tod) sowie die erwartete Zeit mit Abbruch und Todeskosten.
- **Bündelung**: Was das erste Ziel in der Hand lässt (z. B. die Eisenspitzhacke für Redstone),
  macht die folgenden billiger. Ziele am selben Ort (Glocke, Brot und Bibliothekar im Dorf;
  Redstone und Diamanten auf einer Abstiegstour) kommen dadurch zusammen.
- Chat-Beispiel: `route: craft_dropper [village] ... (0.7 tiles/min; craft_dropper 100% within 264 s)`.

### 13.5 Weitere Befunde aus Probeläufen (behoben)

- **Lange Wege**: haben einen Fortschritts-Wächter. Nach 45 s ohne Annäherung geht Bob an die
  Oberfläche oder nimmt einen Seitenschlag; nach drei Versuchen gibt er auf. Vorher: 9 Minuten
  Pendeln auf dem Weg ins Dorf.
- **Plan-Kontrolle**: Die Prüfung, ob eine andere Quelle in Sicht ist, wechselt nur noch zu einer
  anderen Quelle **desselben** Items. Vorher: mitten auf der Eisentreppe wieder nach oben, um Holz
  zu holen.
- **Nacht**: keine Plünderzüge zu Strukturen; nachts ist ein Dorf voller Zombies.
- **Eröffnung**: Holz für 28 Bretter auf einmal; der Holz-Nachschub holt 8 Stämme.
  Nebenaufgaben werden höchstens zweimal verlängert.
- **Navigator**: Ein liegengebliebener Weg wird vor dem nächsten Task-Tick beendet. Vorher hielt
  Bob die letzten Tasten weiter gedrückt, und eine selbst steuernde Aufgabe bekam ihre Eingaben
  gelöscht.
- **Pulverschnee**: Bob gräbt sich frei. Fluchtwege führen nicht über Klippen.
- **Biome**: Stony Peaks gelten nicht mehr als Schnee-Biom.

### 13.6 Messungen

Probeläufe über 20 Minuten auf dem Seed aus dem Live-Test:

| Lauf | Felder | Was den Unterschied machte |
| --- | --- | --- |
| Probe 2 | 2 | Eisen-Kit und Dorfweg-Pendeln fraßen 16 Minuten |
| Probe 3 | 6 | Pendeln behoben, Holz für die ganze Eröffnung |
| Probe 4 | 3 | starke Streuung: Spitzhacke auf der Treppe zerbrochen, Höhle unter der Treppe; beides danach behoben |

90-Minuten-Läufe auf mehreren Seeds folgen unten.

## 14. Runde 8: Navigation mit Weitblick, Kampf ohne Schaden, Nacht und Höhlen

Anlass: Rückmeldung vom Spieler:

- Fackeln sind unnötig, ein Profi stellt keine.
- Bob soll besser kämpfen (Abstand, Bewegung) und keinen Schaden nehmen.
- Aus gefundenem Eisen soll er bessere Waffe und Rüstung machen.
- Schlafen nur, wenn keine Aufgabe die Nacht braucht und im Mehrspieler die anderen liegen.
- Große Höhlen statt Strip-Mining.
- Ein Review der Entscheidungslogik und des A*, der zu kurz vorausschaut.

### 14.1 A*-Review: Befunde

- Die Suche las nur 7 Chunks (~112 Blöcke) um den Start. Alles dahinter galt als Wand.
- Weite Wege liefen Bob deshalb in Stücken, die gierig nach Luftlinie gewählt wurden. Es gab
  keine Grobplanung.
- `FarWalk` wich nach Fehlschlägen zufällig um ±60° aus. Folgen waren Sackgassen und Pendeln.
- Die „Bevorzugung des alten Pfads“ war wirkungslos: Beim Neuplanen war der Pfad immer schon
  verworfen.
- Die Suche stoppte nach Zeit statt nach Knoten. Unter Last wurden die Wege dadurch schlechter,
  und Tests wackelten.
- Ziele wählte Bob nach Luftlinie (der Baum jenseits des Flusses). Planer und Route schätzten
  Wege mit Luftlinie/4.
- In der Ausführung zielte Bob auf jede Blockmitte (Zickzack) und sprang nicht im Sprint.

### 14.2 Umsetzung

- **Grobkarte (`BotTerrain`)**:
  - Zellen von 4×4 Blöcken aus den Höhenkarten der geladenen Chunks (bis 24 Chunks Sicht).
  - Je Zelle: Boden, Wassertiefe, Gefahren, Rauheit und Blätterdach.
  - Ein Dijkstra auf einem eigenen Thread liefert das Restkostenfeld zum Ziel.
- **Feiner A***:
  - Heuristik `max(Luftlinie, 0,85·Restkosten − Schlupf)`.
  - Teilpfad und Schnellsuche folgen damit dem echten Weg um Seen und Buchten.
  - Bleibt Bob stecken, wird die Zelle teurer und der Weg neu gerechnet.
- **Pfad-Gedächtnis**: Nach einem Abbruch bevorzugt die nächste Suche den Rest des alten Weges.
- **Budgets**: Knotenzahl statt Zeit, fastutil-Maps.
- **Mehrere Kandidaten** (`goNearAny`): Der A* findet selbst den billigsten erreichbaren Block.
  Genutzt in `MineTask`.
- **Laufen**:
  - Schnur ziehen, also gerade auf den fernsten begehbaren der nächsten Schritte zu.
  - Sprint-Springen auf freien Geraden: 6,8 statt 5,6 Blöcke/s im Test.
- **Neuer Zug**: durch den Boden in eine Höhle darunter graben und hineinfallen.
- **Wegzeiten**: Planer, Route und Strukturziele rechnen mit der Wegzeit über die Karte
  (Reisezeitfeld ab Bob, mit gelerntem Faktor) statt mit Luftlinie/4.
- **Explorer**: Er geht in die Richtung mit dem meisten noch nie gesehenen Land.
- **Kampf**:
  - Abstand halten (Auge bis Hitbox, Reichweite 2,85).
  - Beim Nachladen im Kreis gehen.
  - Den Sprung so starten, dass der Schlag als Crit beim Fallen trifft.
  - Rück- und Seitschritte nur auf Boden.
  - Gezielt der Angreifer, nicht der nächste Mob seiner Art.
  - Zuerst zuschlagen.
  - Schild gegen einen Creeper, der zu nah zischt.
  - Kein Essen und kein Aufräumen unter Angriff.
  - Das Gehirn wartet, solange ein Reflex läuft.
- **Eisen**:
  - Übriges Eisen (über dem Brett-Bedarf, Barren und Roherz) geht in dieser Reihenfolge in
    Schild, Schwert, Brust, Beine, Stiefel und Helm.
  - Was nicht reicht, wird übersprungen.
  - Geprüft wird sofort, wenn Eisen dazukommt.
- **Schlafen**: Nur wenn
  - kein offenes Feld die Nacht braucht (Nachtmonster, ihre Drops, Phantom, Skelett und andere),
  - und die Nacht durch Bobs Schlaf wirklich vergeht (Spielregel `playersSleepingPercentage`, die
    anderen liegen schon).
- **Höhlen**:
  - Im Erzband (±16, Diamant/Redstone/Gold ±10) läuft Bob die Höhle Punkt für Punkt ab, bis 14
    Punkte erreicht sind oder 90 s nichts gefunden wurde.
  - Gräbt sich eine Treppe in eine Höhle, wird diese zuerst erkundet.
  - Höhlenpunkte brauchen einen Boden.
  - Der Planer rechnet weniger Suchzeit, wenn eine bekannte Höhle das Band erreicht.
  - Für Gestein nimmt Bob die billigste Spitzhacke.
- **Routing**:
  - Plünderungen nur bei Bedarf (Eisen, Essen; Portal-Ruine bei Obsidian-, Gold- oder
    Nether-Bedarf).
  - Gelegenheiten am Weg auch bei Nebenaufgaben.
  - Ein bekanntes, brennendes Portal wird wiederverwendet.
- **Phase-0-Korrekturen**:
  - Fackeln entfernt.
  - Kit-Bedarf („hat eines“) korrigiert.
  - Barren-Bedarf über Roherz.
  - Eröffnungsversuche zählen je Werkzeug.
  - Drop-Tabelle mit genug Würfen.
  - Lohe planbar.
  - Gesehenes gilt nicht als „fehlend“.
  - Das Gedächtnis behält Gesehenes beim Neu-Scan.
  - Timer werden nach dem Respawn zurückgesetzt.
  - Gelernt wird nur aus echten Feldzeiten (ohne Stellvertreter, Uhr steht bei Nebenaufgaben).
  - Verlängerung nur bei Fortschritt.
  - Statt Leerlauf versucht Bob die billigsten schweren Felder.

### 14.3 Messungen

**Navigation** (Navbench v2, 30 feste Ziele um den Spawn, 50–400 Blöcke):

| Seed | Stand | erreicht | Blöcke/s | Umweg | Pendler | ohne Weg |
| --- | --- | --- | --- | --- | --- | --- |
| Live | alt | 25/30 | 1,77 | 1,42 | 40 | 4 |
| Live | neu | 29/30 | 2,98 | 1,23 | 7 | 0 |
| 77 | alt | 11/11, dann Sturztod | 3,00 | 1,34 | 2 | 0 |
| 77 | neu | 30/30 | 3,45 (erste 11: 3,65) | 1,20 | 3 | 0 |

**Kampf auf HARD**, Steinschwert, Herzen verloren:

| Gegner | Herzen verloren |
| --- | --- |
| Zombie | 0 |
| Baby-Zombie | 0 |
| Spinne | 0 |
| Zombie-Trio | 0 bis 3 (Streuung über mehrere Läufe) |

**90 Minuten, Live-Seed, alter Stand (vor dieser Runde):** 14 Felder, 1 Tod.

## 15. Runde 9: Zwei Stunden live zugeschaut – Befunde und der Plan für einen klügeren Bob

Anlass: ein zweistündiger Live-Test mit Kommentaren des Zuschauers. Die Befunde stehen in 15.1,
was davon sofort behoben ist in 15.2, der Plan für die nächsten Runden in 15.3.

### 15.1 Befunde aus dem Log (mit Ursache)

| Beobachtung | Ursache im Code |
| --- | --- |
| Läuft und schwimmt durch Lava, verbrennt nicht | Nach dem ersten Tod unverwundbar: der Respawn lief am Client-Befehl vorbei (`waitingForRespawn` blieb gesetzt). |
| Im Nether in Lava gefallen, steht still („Lava on Lava“, hunderte Fehlversuche) | Kein Lava-Reflex; die Suche aus der Lava fand bei großem Lavasee keinen Teilweg. |
| „Air on Magma Block, no moves“ | Magma galt als gefährlicher Block: vom Magma aus gab es gar keinen Zug. |
| Geht dicht an Lavarändern entlang | Kein Aufschlag für Schritte neben Lava; ein Rückstoß reicht. |
| Kann „auf Wasser laufen“, hängt beim Schiffswrack im Wasser | Sprinten mit dem Kopf über Wasser hebt das Absinken auf, ohne zu schwimmen. |
| Wirft Antiken Schutt, Bündel, Goldnuggets, Goldblöcke und Leder weg | Die „nutzlos“-Liste kannte nur Erze, Barren und Holz als wertvoll. |
| Wirft 27 Schwarzstein weg, hat danach zu wenig Blöcke für die Säule zum Portal | Bausteine wurden je Sorte gezählt, nicht insgesamt. |
| „found no netherrack“ in der Bastion | Suchte nur Netherrack; Schwarzstein und Basalt ringsum zählten nicht als Baustoff. |
| „no way back known“, obwohl das Portal bekannt ist | Nach einem gescheiterten Rückweg galt das Portal 2 Minuten lang als „unbekannt“; Bob wollte ein neues Portal bauen. |
| Turmbau zum Portal ohne Blöcke, dann Stillstand | Der Rückweg holte nie Blöcke nach und zählte seine Fehlschläge nicht. |
| Bartern: Bob hebt das Gold wieder auf, Handel stockt | Beim Aufsammeln der Beute stieg Bob in das Loch zum Piglin und kam nicht mehr heraus; abgeschlossene Tauschhandel wurden nicht gezählt. |
| Baut bei fast jedem Schritt eine neue Werkbank | Die Werkbank blieb beim Schmelzen stehen (der Ofen galt als „Werkbank-Schritt“) und war danach zu weit weg. |
| „no Crafting Table to use“ → „no known way to get furnace“ | Der Plan zählte eine Werkbank in 32 Blöcken; beim Schritt war sie außer Reichweite, und der Schritt brach ab statt eine neue zu machen. |
| Blumentopf: Ton im Fluss nie abgebaut | Blöcke neben Wasser galten pauschal als „nicht lohnend“. |
| Fernrohr: „found no amethyst_shard“ | Amethyst kommt fast nur in Geoden vor; Bob kennt weder ihre Höhe noch ihre Erkennungszeichen. |
| Bastion: Brutes hätten ihn mehrfach getötet | Kein Taktikbuch je Gegner; Bob kämpft jeden Mob gleich im Nahkampf. |
| Portalguss: „only 9 lava sources“, „frame cell 5 would not set“, „takes too long“ | Fast fertiger Rahmen wurde nicht wieder aufgenommen; fließendes Wasser schob Bob in die Lava. |
| Rüstung trotz genug Eisen nicht gebaut; Diamanten nicht ganz abgebaut | `upgrade()` brach beim ersten nicht bezahlbaren Teil ab; Diamant-Adern wurden nicht ganz ausgehoben. |

### 15.2 Sofort behoben (diese Runde)

- **Unverwundbarkeit:** Respawn über den Client-Befehl (Test `hurtable_after_death`).
- **Lava:**
  - Lava-Reflex `LavaEscapeTask`: raus auf festen, kühlen Boden, direkt springend, wenn er nah
    ist, sonst über eine Suche durch die Lava (Tests `lava_reflex`, `lava_escape`).
  - Magma: Wer darauf steht, darf darüber (mit Aufschlag) herunter (Test `magma_reflex`).
  - Aufschlag von 8 Ticks für jeden Schritt direkt neben Lava: Bob hält einen Block Abstand
    (Test `lava_edge_kept_off`).
- **Feuer löschen:** `ExtinguishTask` gießt Wasser aus dem Eimer und schöpft es wieder, oder geht
  ins nahe Wasser (Tests `extinguish_bucket`, `extinguish_pond`).
- **Schwimmen:** Sprint nur mit dem Kopf unter Wasser, gezieltes Abtauchen (Tests
  `underwater_chest`, `lake_swim`).
- **Inventar:**
  - Was wertvoll ist (Gold, Netherit, Schutt, Bündel, Leder, Perlen, Lohe, Amethyst, Ton, Glas
    und vieles mehr) wird nie weggeworfen.
  - Zuerst geht Wertloses (Samen, Setzlinge, Kleinkram).
  - Bausteine bleiben zusammen mindestens 64.
- **Nether-Rückweg:**
  - Baustoff im Nether ist Netherrack, Schwarzstein oder Basalt.
  - Der Rückweg holt Blöcke nach, wenn kein Weg gefunden wird und weniger als 24 da sind.
  - Nach 8 Fehlschlägen gibt der Rückweg auf, statt endlos zu suchen.
  - „Kein Weg zurück“ gilt erst, wenn wirklich kein Portal bekannt ist.
- **Bartern:** Bob steigt beim Aufsammeln nie ins Loch; Tauschhandel werden sofort gezählt (Test
  `barter_in_hole` wieder grün).
- **Werkbank:** wird auch vor dem Schmelzen eingepackt (Reichweite 16 Blöcke). Fehlt die geplante
  Werkbank oder der Ofen, macht Bob einmal einen neuen, statt den Schritt abzubrechen.
- **Ton:** Ton unter flachem Wasser (bis 4 tief) wird getaucht und abgebaut, anderes nach 30 s
  vergeblicher Suche an Land (Test `river_clay`).
- **Portal:** Ein angefangener Rahmen wird fortgesetzt; Stellplätze neben fließendem Wasser sind
  tabu (Test `cast_portal_resumed`).
- **Ausrüstung:**
  - Schild zuerst.
  - Danach Eisen- und später Diamant-Ausrüstung aus dem Überschuss über den Brett-Bedarf.
  - Was nicht bezahlbar ist, wird übersprungen statt abzubrechen.
  - Diamant-, Gold- und Smaragd-Adern werden ganz abgebaut, auch über das Ziel hinaus.

### 15.3 Der Plan: Bob klüger machen

Reihenfolge nach Wirkung auf „volles Brett in 90 min, ohne zu sterben“. Jede Stufe mit Tests,
Benchmarks und einem Bericht.

**Stufe A – Überleben zuerst (Gefahrenmodell)**

1. **Gefahrenkarte** je Chunk:
   - Lava, Magma, Feuer, Abgründe über 4 Blöcke, fließendes Wasser neben Lava, Pulverschnee;
   - gespeist aus dem Weltblick (`BotMemory`).
2. **Risiko statt Verbot im A\*:** Jeder Schritt kostet Zeit plus Risiko × Gewicht. Das Gewicht
   steigt mit wenig Herzen, ohne Feuerresistenz und im Nether.
3. **Nether-Brücken:**
   - Über Lava nur schleichend, mit Seitenblöcken.
   - Kein Sprint-Springen in Lavanähe.
   - Bei Kämpfen am Rand zuerst einen Block hinter sich setzen.
4. **Weitere Reflexe:**
   - Ghast-Feuerball abwehren (vorhanden, ausbauen);
   - Fall stoppen (Wassereimer, schon da; dazu Heuballen und Leiter);
   - Ersticken im Kies;
   - Lava, die aus der Decke fließt (beim Graben sofort zumauern).
5. **Globaler Fortschrittswächter:**
   - Keine Bewegung und keine Inventaränderung über 60 s → Notfallprogramm: lokale Flucht,
     Ziel wechseln, Fehler protokollieren.
   - Gleiche Fehlermeldung mehr als 5-mal → exponentielles Warten statt Spam.

**Stufe B – Kampf mit Köpfchen (Taktikbuch je Gegner)**

1. **Je Gegnertyp eine Taktik:**

   | Gegner | Taktik |
   | --- | --- |
   | Piglin-Brute | Nie ohne Schild und Rüstung in den Nahkampf. Lieber meiden, Säule 3 hoch und Bogen, oder Engpass mit Schild. |
   | Piglin | Goldrüstung, keine Truhen und kein Gold abbauen in Sichtweite, bei Wut Rückzug hinter eine Tür oder Mauer. |
   | Hoglin | Warped Fungus hält ihn fern, sonst Säule und von oben schlagen (Rückstoß nach oben ist harmlos). |
   | Lohe | Deckung hinter Blöcken, Schild gegen Feuerbälle, Schneebälle wenn vorhanden. |
   | Wither-Skelett | 2 hohe Lücke, Schwert, nie offen in Gruppen. |
   | Ghast | Feuerball zurückschlagen, Bogen. |
   | Enderman | Nicht ansehen; im Kampf unter einen 2-hohen Überhang. |
   | Gruppen | Engpass suchen, Sweep-Schläge. |

2. **Kampf oder Ausweichen** nach erwarteter Schadensbilanz (Gegner-DPS × Kampfzeit gegen
   Umweg), nicht „immer kämpfen“.
3. **Ausrüstung vor gefährlichen Orten:**
   - Vor Bastion und Festung prüfen: Schild, Rüstung, Essen und Blöcke.
   - Fehlt etwas, kommt es als Vorbereitungsschritt in die Route.

**Stufe C – Nether wie ein Speedrunner**

1. **Portal-Netz im Gedächtnis:**
   - Beide Seiten jedes Portals, umgerechnet 1:8.
   - Der Rückweg ist immer bekannt, die Brotkrumenspur liegt als Rückfallweg bereit.
2. **Reise-Check vor jeder Nether-Reise:** 64 Blöcke, Essen, Feuerzeug, Goldrüstung, Wassereimer
   (für die Oberwelt), Schild.
3. **Bastion-Plan:**
   - Typ erkennen (Brücke, Schatz, Ställe, Wohnungen).
   - Brutes zählen; über Dach oder Wall zum Ziel.
   - Gezielt Truhen, Fluchtweg vorher festlegen.
4. **Bartern im Akkord:** Loch-Falle, ein Barren nach dem anderen, Beute einsammeln ohne den
   Piglin zu stören; mehrere Piglins parallel.
5. **Festung finden:** Blickrichtung der Nether-Biome, Netherziegel in Sicht, Lohe-Spawner merken.

**Stufe D – Wissen und Wahrnehmung**

1. **Amethyst-Geoden:**
   - Erkennungszeichen: Kalzit- und glatte Basalt-Schale in Höhlenwänden und an Hängen.
   - Höhe: −64 bis 30.
   - Gefundene Geoden ins Gedächtnis.
2. **Strukturen per Sicht:** Schiffswrack, Ozeanruine, Geode, Ruinenportal, Iglu und Pyramide aus
   typischen Blöcken erkennen, mit Merkzettel „unterwegs mitnehmen“.
3. **Blockvorkommen je Biom und Höhe** (Ton in Flüssen und Sümpfen, Sand an Stränden, Kürbisse,
   Bienen in Blumenwäldern) als Suchhinweise im Planer.
4. **Unterwasser-Ressourcen:** Ton, Sand, Kies, Seegras und Korallen als gleichwertige Quelle
   (Tauchen mit Luftplanung).

**Stufe E – Planen wie ein Profi**

1. **Stationen mitführen:** Werkbank und Ofen bleiben im Gepäck.
   - Schmelzen läuft parallel: Erz einlegen, weiterarbeiten, später abholen (spart pro Spiel
     mehrere Minuten).
2. **Einkaufsliste über mehrere Ziele** (schon da), dazu Material-Reservierung: Was für ein
   geplantes Feld gebraucht wird, wird nicht für Ausrüstung verbraucht.
3. **Tag/Nacht-Kalender:** Nachtfelder (Phantom, Spinne, Skelett) gebündelt in die Nacht,
   Tagfelder in den Tag; Schlafen nur nach Abschnitt 14.
4. **Gegner im Mehrspieler:** Felder, die der Gegner gleich holt, nicht mehr anfangen; Felder
   blockieren, die er braucht.

**Stufe F – Lernen aus Erfahrung**

1. **Episoden-Gedächtnis über Spiele hinweg:** Erfolgsrate und Zeit je Taktik und Ort, z. B.
   „Brücke über Lava 3× gescheitert“. Daraus entstehen automatisch Strafkosten im Planer.
2. **Fehlerkatalog aus den Logs:** Jede Abbruchmeldung zählt mit Ursache. Die häufigsten fünf
   werden in der nächsten Runde behoben.

**Stufe G – Testen wie ein Weltmeister**

1. **Feld-Matrix:** Für jedes Brettfeld (Oberwelt und Nether) ein Szenario-Test mit Erfolgsrate
   und Zeit. Ein nächtlicher Lauf erzeugt eine Tabelle; rote Felder kommen zuerst dran.
2. **Nether-Testwelt:** Bob startet mit Kit direkt im Nether (Bastion, Festung, Lavasee,
   Seelensandtal), jedes Nether-Feld einzeln.
3. **Replay aus Live-Logs:** Seed, Position und Inventar aus dem Log nachstellen, um eine
   Live-Panne als Test zu wiederholen.
4. **Benchmarks:** 6 Seeds × 90 min auf HARD, je zwei parallel. Neu dazu: Tode, Schaden pro
   Minute und Zeit je Kategorie.

**Abnahme der Runde:** 0 Tode in 6 × 90 min, kein Stillstand länger als 60 s, ≥ 20 Felder im
Schnitt, und jedes Nether-Feld einzeln zu ≥ 80 % im Test.

### 15.4 Umsetzung und Messungen (Runde 9, Teil 2)

**Neue Messwerkzeuge:**

- **Feldtest** (`scripts/bot/fieldtest.sh <seed> <ziele|KATEGORIE> [s]`): jedes Brettfeld einzeln,
  mit gleicher Ausrüstung und gleichem Startpunkt (Nether-Felder im Nether), das echte Gehirn
  spielt nur für dieses Feld. Ergebnis je Feld: CLAIMED, TIMEOUT, NO-WAY oder DIED, dazu Zeit,
  niedrigste HP, Todesort und Todesursache.
- `brawl_*`-GameTests für Brute, Hoglin, Enderman und Magmawürfel; Lava-, Magma- und Kantentests.

**Nether-Feldtest (40 Felder, 300 s je Feld, Live-Seed):**

| Stand | geholt | Tode | häufigste Todesursache |
| --- | --- | --- | --- |
| vor den Fixes | 13 | 19 | Lava (10), Magmawürfel (4), Stürze (3) |
| nach Lava-, Sprung- und Brückenregeln | 17 | 8 | Magmawürfel (4) |

Gefundene und behobene Ursachen:

- Sprünge über Lücken mit Lava oder Leere darunter, Brücken über Lava mit einem einzigen Block.
- Essen mitten in der Lava (andere Reflexe liefen während der Flucht weiter).
- Direkte Sprints auf Gegner über Kanten; kein Blick auf den Boden beim Vorwärtsschritt im Kampf.
- Bogen gegen springende Magmawürfel auf kurze Distanz.
- Magmawürfel und Schleime galten nicht als „Monster“: Bob ist vor ihnen nie geflohen.
- Seelensand, Seelenerde und Basalt wurden „überall“ gegraben statt in ihrem Biom gesucht.

**90 min auf dem Live-Seed (HARD), ehrliche Werte ohne Unverwundbarkeit:**

| Stand | Felder | Tode |
| --- | --- | --- |
| e11e9fc | 9 | 7 |
| mit Rüstung, Essen und Kampfregeln | 11 | 5 |

**Weitere Neuerungen:**

- Paralleles Schmelzen: bis zu drei Öfen, 18 Eisen in gut einer Minute statt drei.
- Säulen-Taktik (3 hoch) gegen Brute, Hoglin, Enderman und Wither-Skelett, mit Pfeilen von oben.
- Bei wenig Herzen mit Nahkämpfern: Säule hoch und dort essen.
- Vorrat an Essen auf HARD; Reise-Check vor dem Nether (64 Blöcke, Essen).
- Nachts ohne Rüstung zählen Felder unter Tage als sicherer: Bob gräbt durch die ersten Nächte.

**Offen (nächste Schritte):**

1. Eisen schneller finden: Höhlen statt Tunnel bei y 14 (Phase 4 ausbauen).
2. Magmawürfel in Basaltdeltas: Biom meiden oder Taktik gegen Gruppen.
3. Zombies in der ersten Nacht mit Steinschwert: Rückzug unter die Erde vor dem Kampf.

## 16. Runde 10: Methodisch klüger – Analyse, Bewertung, Rangfolge

Anlass: Bob überlebt besser, kommt aber nicht über 9–11 Felder in 90 Minuten. Bevor neue
Funktionen entstehen, wird gemessen, warum nicht, und jede Idee wird nach denselben Kriterien
bewertet.

### 16.1 Was die Messungen zeigen (letzter 90-min-Lauf, Live-Seed, HARD)

- **Nur 11 Ziele angefangen** in 90 Minuten. Die übrige Zeit floss in Erledigungen, Ausflüge
  und Tode.
- **7 Tode**, und nach jedem lief die **ganze Eröffnung neu** (Holz, Werkbank, vier
  Steinwerkzeuge): siebenmal.
- **27 Minuten** auf „Eisenspitzhacke“, die erste kam nach 17 Minuten.
- **Schwein reiten:** 2,3 min geschätzt, 9,2 min gebraucht.
- **Ertrunkener:** dreimal angefangen, dreimal gescheitert, 7,5 min.
- **12 Nebenausflüge** („unterwegs noch Kohle/Eisen“) endeten im Zeitlimit.
- **Nether-Feldtest:** Lava bleibt die häufigste Todesursache, danach Magmawürfel und Stürze. Die
  Stürze waren laut Fall-Protokoll zu kurz geratene Sprünge.

### 16.2 Was Bob eigentlich zurückhält (Ursachenanalyse)

Fünfmal „warum?“ für jeden großen Zeitverlust führt immer wieder auf dieselben fünf Wurzeln:

1. **Bob sieht nicht voraus.** Er handelt auf den Zustand *jetzt*: Reflexe prüfen alle 10 Ticks,
   die Bewegung kennt nur den nächsten Schritt. Wohin ihn der eigene Schwung, ein Sprung oder ein
   Rückstoß in einer halben Sekunde trägt, berechnet niemand – Lava und Kanten fallen erst auf,
   wenn er schon drin ist.
2. **Viele kleine Entscheider ohne gemeinsamen Maßstab.** Das Gehirn ist eine Kaskade („wer zuerst
   ‚ich will' ruft, gewinnt“): Essen, Gold, Eröffnung, Ausrüstung, Plünderung, Nether-Phase,
   Investition, Jagd, Schlaf, dann erst die Felder. Dazu kommen Reflexe und Ausflüge mit eigenen
   Zeitgrenzen. Niemand vergleicht „5 min Eisen“ mit „2 min Feld“ in derselben Währung. Daraus
   entstehen Schleifen, Zeitlimits und gegenseitiges Unterbrechen.
3. **Kein Gedächtnis für eigenes Scheitern** im laufenden Spiel: Dieselbe Aufgabe am selben Ort
   scheitert dreimal gleich.
4. **Ein Tod ist zu teuer:** Nach jedem Tod beginnt alles von vorn. Jede Unsicherheit wird so zum
   großen Verlust.
5. **Eisen als Engpass:** Ohne Eisen kein Eimer, kein Schild, keine Rüstung, kein Nether. Der Weg
   dorthin ist ein Tunnel statt der Höhlen.

### 16.3 Bewertung

**Kriterien:**

- **K1 Tempo:** mehr Felder je 90 min.
- **K2 Überleben:** weniger Tode.
- **K3 Breite:** wie viele gemessene Probleme gelöst werden; aus der Matrix.
- **K4 Aufwand:** 10 = gering.
- **K5 Regressionsrisiko:** 10 = gering.
- **K6 Messbarkeit:** 10 = gut testbar.

#### Paarvergleich der Kriterien

| | K1 | K2 | K3 | K4 | K5 | K6 | Summe | Gewicht |
|---|---|---|---|---|---|---|---|---|
| K1 Tempo | – | 1 | 2 | 2 | 2 | 2 | 9 | 30 % |
| K2 Überleben | 1 | – | 2 | 2 | 2 | 2 | 9 | 30 % |
| K3 Breite | 0 | 0 | – | 1 | 2 | 2 | 5 | 17 % |
| K4 Aufwand | 0 | 0 | 1 | – | 1 | 1 | 3 | 10 % |
| K5 Risiko | 0 | 0 | 0 | 1 | – | 1 | 2 | 7 % |
| K6 Messbarkeit | 0 | 0 | 0 | 1 | 1 | – | 2 | 7 % |

(2 = Zeile wichtiger als Spalte, 1 = gleich wichtig, 0 = weniger wichtig.)

#### Probleme mit Gewicht (verlorene Minuten je 90-min-Spiel, aus den Messungen)

| Nr. | Problem | Gewicht |
|---|---|---|
| P1 | Tode durch Mobs (Oberwelt: Zombie, Skelett, Creeper, Ertrunkener, Enderman) | 12 |
| P2 | Tode durch Lava und Feuer (Oberwelt 2/Lauf, Nether 5 von 40 Feldern) | 12 |
| P3 | Tode durch Stürze (zu kurze Sprünge, Rückstoß an Kanten) | 8 |
| P4 | Nach jedem Tod alles neu (Eröffnung 7× in 90 min) | 15 |
| P5 | Eisen zu langsam (Eisenspitzhacke nach 17 min, 27 min auf der Aufgabe) | 10 |
| P6 | Nebenausflüge laufen ins Zeitlimit (12× in 90 min) | 8 |
| P7 | Fehlschlag-Schleifen (Ertrunkener 3× gescheitert, Schwein 9 statt 2 min) | 12 |
| P8 | Falsche Zeitschätzungen → falsche Reihenfolge | 6 |
| P9 | Grundressourcen langsam (8 min für Akazienholz) | 5 |
| P10 | Reflex-Konflikte (Kampf↔Rückzug, Essen in Lava, Stapel) | 5 |
| P11 | Wissenslücken (Festung der Endportale, Beton, Brauen im Nether) | 4 |
| P12 | Kein Essen dabei (oft 0 Essen im Inventar) | 4 |
| P13 | Nether-Gelände (Basaltdeltas, Lavaseen, Magmawürfel-Gruppen) | 8 |

#### Problem-Lösungs-Matrix (Wirkung 0–3, Spalten = Probleme)

| Lösung | P1 | P2 | P3 | P4 | P5 | P6 | P7 | P8 | P9 | P10 | P11 | P12 | P13 | Abdeckung |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| L1 |  | 3 | 3 |  |  |  |  |  |  | 1 |  |  | 2 | 81 |
| L2 | 3 |  | 1 |  |  |  |  |  |  | 1 |  |  | 1 | 57 |
| L3 |  |  |  | 1 | 1 | 3 | 2 | 2 |  | 2 |  | 2 |  | 103 |
| L4 |  |  |  |  |  | 2 | 3 | 1 |  |  |  |  |  | 58 |
| L5 | 1 | 1 | 1 | 3 |  |  |  |  |  |  |  |  |  | 77 |
| L6 | 2 |  |  | 1 |  |  |  |  |  | 2 |  | 3 |  | 61 |
| L7 |  |  |  |  | 3 |  |  |  | 1 |  |  |  |  | 35 |
| L8 |  |  |  |  |  | 1 | 1 | 3 |  |  |  |  |  | 38 |
| L9 |  | 2 | 1 |  |  |  |  |  |  |  |  |  | 3 | 56 |
| L10 | 3 |  |  |  |  |  |  |  |  |  |  |  | 1 | 44 |
| L11 |  |  |  |  |  |  |  |  |  |  | 3 |  |  | 12 |
| L12 |  |  |  |  |  | 1 |  |  | 3 |  |  |  |  | 23 |
| L13 |  |  |  |  |  |  | 1 |  |  | 1 |  |  |  | 17 |

(Abdeckung = Σ Wirkung × Problemgewicht.)

#### Nutzwertanalyse (Noten 1–10, K3 aus der Abdeckung)

| Rang | Lösung | K1 | K2 | K3 | K4 | K5 | K6 | Nutzwert |
|---|---|---|---|---|---|---|---|---|
| 1 | **L1** Gefahrensinn + Bewegungswächter: eigene Bewegung 0,5 s vorausberechnen, Lava/Abgrund/Feuer-Veto in jedem Tick | 6 | 9 | 8 | 6 | 6 | 9 | **7.43** |
| 2 | **L3** Großhirn: ein Entscheider mit gemeinsamer Währung (Felder/Minute × Überleben) für Ziele, Erledigungen, Ausflüge, Bedürfnisse; Bindung statt Hin und Her | 9 | 6 | 10 | 2 | 3 | 6 | **6.97** |
| 3 | **L2** Bedrohungsvorhersage: Laufwege, Creeper-Zündung, Bogenspannung, Projektile im Flug → vorher ausweichen | 5 | 8 | 6 | 4 | 6 | 8 | **6.23** |
| 4 | **L6** Zustands-Modi (Sicher / Erholen / Ausrüsten / Arbeiten) mit Lebens-, Essens- und Ausrüstungsbudget | 5 | 8 | 6 | 5 | 6 | 6 | **6.20** |
| 5 | **L5** Tod-Analyse + schlauer Neustart: Ursache merken, Sachen holen wenn sicher, nur nötige Werkzeuge neu, Bett als Spawn | 8 | 3 | 8 | 7 | 7 | 6 | **6.20** |
| 6 | **L9** Nether-Gefahrenkarte (Biome, Lavaseen, Wege drumherum) | 5 | 7 | 6 | 5 | 7 | 7 | **6.03** |
| 7 | **L10** Taktikbuch je Gegner ausbauen (Ertrunkener, Skelett in Deckung, Creeper, Gruppen) | 4 | 7 | 5 | 6 | 7 | 9 | **5.80** |
| 8 | **L4** Fehlschlag-Gedächtnis + Schleifenwächter: gleiche Sache am gleichen Ort gescheitert → Pause, Alternative | 7 | 3 | 6 | 7 | 8 | 7 | **5.70** |
| 9 | **L7** Höhlen-Bergbau + Erzkarte (gesehene Erze, Höhlenränder, breite Bänder) | 7 | 2 | 4 | 5 | 6 | 7 | **4.73** |
| 10 | **L8** Lernende Schätzungen (Plan gegen Ist je Aufgabenart, schon im laufenden Spiel) | 6 | 1 | 4 | 6 | 7 | 5 | **4.17** |
| 11 | **L13** Szenario-Testbank (Live-Pannen als Tests, Feldmatrix je Nacht) | 3 | 3 | 2 | 5 | 10 | 10 | **3.97** |
| 12 | **L12** Grundressourcen gebündelt (Holz/Stein einmal richtig, Baumgruppen statt Einzelbäume) | 4 | 0 | 3 | 7 | 8 | 7 | **3.40** |
| 13 | **L11** Wissenslücken schließen (Festung der Endportale, Beton, Brauen) | 4 | 0 | 2 | 6 | 9 | 8 | **3.27** |

### 16.4 Entscheidung und Reihenfolge

Die drei Spitzenreiter greifen die Wurzeln 1 und 2 an. Sie werden zuerst gebaut, jeweils mit
Tests, Feldtest und Benchmark davor und danach:

1. **L1 Gefahrensinn und Bewegungswächter** (Wurzel 1).
   - Eine Gefahrenkarte um Bob (Lava, Feuer, Magma, Abgründe) wird jeden Tick nachgeführt.
   - Ein Wächter rechnet Bobs eigene Bewegung aus Schwung und Eingaben eine halbe Sekunde voraus,
     bevor sie ausgeführt wird.
   - Führt sie in Lava, Feuer oder über einen Abgrund, wird sie verboten: Gegensteuern, an
     Kanten ducken, kein Sprung. Das gilt egal, welche Aufgabe gerade steuert.
   - Die Karte verteuert außerdem Wege, Rückzüge und Kampfpositionen in Lavanähe.
2. **L3 Großhirn** (Wurzel 2), zusammen mit **L6 Zustands-Modi** und **L4 Fehlschlag-Gedächtnis**:
   - Alle Kandidaten – Felder, Erledigungen, Ausflüge und Bedürfnisse – werden in einer Währung
     bewertet: erwartete Felder pro Minute, mal Überlebenschance, plus Dringlichkeit.
   - Ein einziger Entscheider wählt.
   - Er bleibt beim Gewählten, solange es Fortschritt macht und nicht deutlich (30 %) Besseres
     da ist.
   - Was scheitert, kommt ins Gedächtnis (Art, Ziel, Ort) und wird erst nach einer Pause wieder
     versucht, mit wachsender Pause.
   - Der Zustand (Herzen, Essen, Rüstung, Nacht, Bedrohung) setzt einen Modus, der die Gewichte
     verschiebt.
3. **L2 Bedrohungsvorhersage** (Wurzel 1): Laufwege der Mobs, Bogenspannung, Creeper-Zündung und
   Projektile werden vorausberechnet; Bob weicht aus, bevor der Treffer kommt.
4. Danach in Rangfolge:
   - **L5** schlauer Neustart (Wurzel 4);
   - **L9** Nether-Gefahrenkarte, aufbauend auf L1;
   - **L10** Taktikbuch;
   - **L7** Höhlen-Bergbau (Wurzel 5);
   - **L8** lernende Schätzungen;
   - **L13** Szenario-Testbank;
   - **L12** Grundressourcen gebündelt;
   - **L11** Wissenslücken.

Alle offenen Punkte aus den Abschnitten 14 und 15 (Höhlen, Nacht, Routing, Taktikbuch,
Nether-Plan, Feld-Matrix) sind in L1–L13 aufgegangen und behalten ihre Einzelschritte.

**Abnahme je Stufe:**

- Feldtest Nether: weniger Tode je Feld.
- 90 min auf 3 Seeds (Durchschnitt, nicht ein einzelner Lauf): mehr Felder, weniger Tode.
- Die GameTest-Suite bleibt grün.

## 17. Runde 11: Gefahrensinn, Großhirn, Ausweichen – umgesetzt

### 17.1 L1 Gefahrensinn und Bewegungswächter (`DangerSense`)

**Gefahrenkarte:** Lava- und Feuerzellen im Umkreis von 8 Blöcken (6 nach unten, 2 nach oben),
alle halbe Sekunde neu gelesen. Daraus kommen:

- „Lava in der Nähe?“
- die Richtung weg von der Lava.

**Wächter (jeden Tick, nachdem die Aufgabe ihre Tasten gedrückt hat):**

- Bobs Bewegung wird mit den Regeln des Spiels vorausgerechnet:
  - Griff des Blocks, Luftwiderstand, Schwerkraft;
  - Sprint, Sprung und Sprint-Sprung;
  - Wände, Seelensand.
- Die Rechnung nimmt die eigenen Tasten für diesen Tick an und danach Vollbremsung (am Boden
  geduckt) bzw. Weiterfliegen bis zur Landung.
- Gezählt wird, was dabei passiert: Lava, Feuer, ein Fall mit Schaden (über den
  Sicherheitsabstand hinaus), die Leere.
- Führt die Bewegung dort hinein, drückt der Wächter stattdessen die kleinste sichere Änderung:
  ohne Sprung, geduckt, Bremsen oder weg von der Gefahr.
- Hält das 20 Ticks an, bekommt der Navigator Bescheid und sucht einen anderen Weg.
- **Magma:** Bob geht geduckt darüber, so verbrennt er nicht. Steht er still, geht er herunter.
- **Fließende Lava:** Bob hält einen Block mehr Abstand und weicht aktiv zurück.

**Kampf an Lava und Kanten:**

- Bob rechnet aus, wohin ihn ein Schlag des Gegners werfen würde (Rückstoß, Hoglin-Wurf).
- Fällt das in Lava oder über eine Kante, sucht er sich zuerst einen Platz, von dem aus der
  Rückstoß auf festen Boden geht.
- **Rückzug:** Die Fluchtrichtung meidet Lava auf und neben dem Weg.

**Tests (alle grün):**

- `guard_lava_ahead`: blindes Losrennen auf Lava auf gleicher Höhe zu;
- `guard_lava_pit_jumping`: Lavagrube, mit Springen;
- `guard_cliff`: Klippe, 10 tief;
- `brawl_by_lava`: Zombie, Lava im Rücken.

Die 45 bestehenden Bewegungs- und Kampftests bleiben grün.

### 17.2 L3 Großhirn mit L4 Fehlschlag-Gedächtnis und L6 Modi (`Cortex`)

Das Großhirn sitzt über der Kaskade in `LockoutBrain`. Jede Absicht hat einen Namen: Entscheider
plus Aufgabe, z. B. `kit:get cobblestone` oder `goal:kill_drowned`.

- **Gedächtnis:** Starts, Erfolge, Fehlschläge, Tode und Zeit je Absicht, über das ganze Spiel.
- **Wachsende Pausen:**
  - Nach Fehlschlägen in Folge wartet Bob 1, 2, 4, 8 Minuten Spielzeit.
  - Nach einem Tod bei der Absicht verdoppelt sich die Pause.
- **Schleifenbrecher:** Dreimal in 10 min begonnen und nie gelungen gilt als Schleife. Die
  Absicht wird 5–40 min zur Seite gelegt, das Log sagt es (`[Cortex]`).
- **Abgelehnter Start:** Die Kaskade läuft weiter zum nächsten Entscheider, statt hängen zu
  bleiben.
- **Modi:**

  | Modus | Wann |
  | --- | --- |
  | Überleben | wenig Herzen, hungrig ohne Essen, oder nackt in der Nacht |
  | Vorbereiten | ohne Steinspitzhacke |
  | Spielen | sonst |

  Beim Überleben startet Bob keine Plünderung, keine Jagd, keine Nether-Reise und keine Bastion.
- **Bilanz:** alle 5 min die teuersten Absichten mit Bilanz (Starts, Erfolge, Fehlschläge, Tode,
  Zeit).

### 17.3 L2 Bedrohungsvorhersage, Teil 1: Geschosse (`ThreatSense`)

- Pfeile, Feuerbälle und Schädel in 24 Blöcken Umkreis werden vorausgeflogen (Pfeile mit Fall und
  Bremsung).
- Trifft eines in 3–20 Ticks, macht Bob einen Seitschritt quer zur Flugbahn, zur offenen Seite.
  Auch diesen Schritt prüft der Wächter.
- Ghast-Feuerbälle werden weiter zurückgeschlagen.
- **Test `dodge_arrows`:** 10 Pfeile aus 15 Blöcken.

  | Durchlauf | Treffer |
  | --- | --- |
  | Ohne Ausweichen (Gegenprobe) | 5 von 10 |
  | Mit Ausweichen | höchstens 2 (die Testgrenze) |

**Noch offen in L2:** Laufwege der Mobs voraus und Creeper-Zündung über die Zeit bis zum
Kontakt.

### 17.4 Aus den Messungen nachgelegt

**L10 Taktikbuch, erster Eintrag: Magmawürfel und Schleime**

- **Ausgangslage:** Im ersten Feldtest kamen 9 von 11 Toden von Magmawürfeln. Bob schlug jeden
  zuerst; ein großer Würfel zerfällt in 2–4 mittlere und die wieder in 2–4 kleine. Der Schwarm
  reibt Bob auf.
- **Regel:** Würfel, die kein Ziel braucht, schlägt Bob nicht zuerst. Ist er schon im Kampf
  (getroffen in den letzten 10 s), schlägt er auch die Abspaltungen zuerst.
- **Flucht** nur vor einem Schwarm: ab 4 Würfeln oder ab 3, wenn er schon verletzt ist. Gegen
  einen oder zwei Würfel ist Flucht schlechter, sie springen schneller hinterher, als Bob läuft
  (gemessen).
- **Test `brawl_magma_pair`** (zwei große Würfel, HARD):

  | Stand | Bestanden | Verlust je bestandenem Lauf |
  | --- | --- | --- |
  | vorher | 2 von 3 | ~11 Herzen |
  | jetzt | 3 von 5 | 3,6 / 4,6 / 10 Herzen |

**L9 Nether-Gefahrenkarte, Teil 1**

- Ist Bob in einem Basaltdelta und will kein offenes Feld etwas von dort, geht er zuerst hinaus:
  zur nächsten anderen Biom-Stelle, die er in Ringen um sich herum findet.
- **Rückstoß vorhergesehen**, auch außerhalb eines Kampfes: Würde der Schlag eines nahen Gegners
  Bob in Lava werfen, stellt er sich vorher um.

**Schleifenbrecher unter dem Großhirn**

- Auf Seed 77 scheiterte „Rote Bete holen“ 1089-mal: Sie war als Bezahlung für einen Bauern
  gewählt, aber nicht beschaffbar.
- **Allgemein:** Scheitert dieselbe Teilaufgabe 10-mal in 30 s, endet die ganze Erledigung als
  Fehlschlag, und das Großhirn verhängt seine Pause.
- **Beim Handel:** Ein Zahlungsmittel, das zweimal nicht zu beschaffen war, wird nicht mehr
  gewählt.

### 17.5 Messungen

**90 min HARD, Live-Seed −8848941644679110190**

| Stand | Felder | Tode | Eisen | Eimer |
| --- | --- | --- | --- | --- |
| vorher | 10 | 7 | 489 s | 1710 s |
| L1–L3 | 10 | 3 | 144 s | 196 s |

- Größter Zeitfresser: `craft_dropper` mit 1103 s; Redstone in der Tiefe, 2 Tode durch Zombie
  und Enderman.

**90 min HARD, Seed 77 (L1–L3 und Würfel-Taktik, ohne die Schleifenbrecher)**

- 15 Felder, 6 Tode.
- Davon über 1000 Fehlschläge in der Rote-Bete-Schleife, inzwischen behoben.

**Nether-Feldtest, 40 Ziele, Start im Basaltdelta**

| Lauf | Felder | Tode |
| --- | --- | --- |
| Runde 9, Lauf 1 | 13 | 19 |
| Runde 9, Lauf 2 | 17 | 8 |
| Runde 9, Lauf 3 | 16 | 16 |
| L1–L3 | 13 | 11 (9 Magmawürfel) |
| mit L9 und Würfel-Taktik | 15 | 4 (3 Magmawürfel, 1 Lava) |

**Als Nächstes:**

- L5 schlauer Neustart;
- L2 Teil 2 (Mob-Laufwege, Creeper-Zeit bis Kontakt);
- L7 Eisen über Höhlen (das Eisen kommt jetzt schon nach 144 s; offen ist das Graben in der
  Tiefe, also Redstone und Diamant);
- L8 lernende Schätzungen aus der Cortex-Bilanz.

### 17.6 Vergleich alt gegen neu, 90 min HARD, je Seed

- **Alt:** Stand vor Runde 11 (`c209711`).
- **Neu:** Stand mit L1–L3, Teilen von L9/L10 und den Schleifenbrechern.
- Je ein Lauf; auf Seed 77 zwei neue Läufe, Mittel in Klammern.

| Seed | Alt: Felder | Alt: Tode | Neu: Felder | Neu: Tode |
| --- | --- | --- | --- | --- |
| −8848941644679110190 | 10 | 7 | 10 | 3 |
| 77 | 10 | 8 | 15 / 9 (12) | 6 / 5 (5,5) |
| 4242 | 1 | 29 | 2 | 14 |
| 1234 | 6 | 1 | 5 | 6 |
| **Summe** | **27** | **45** | **29** | **28,5** |

**Befund:**

- Die Tode sinken deutlich, um gut ein Drittel.
- Die Felder bleiben etwa gleich.
- Die Streuung zwischen zwei Läufen auf demselben Seed ist groß (Seed 77: 15 gegen 9 Felder).
  Einzelläufe sagen wenig; künftig braucht es mehrere Läufe je Stand.
- Auf Seed 1234 starb Bob öfter als vorher (2 Zombies, 1 Creeper) – das bleibt zu beobachten.

**Verworfen:** Deckung schon ab 16 Herzen gegen zwei oder mehr Schützen. Im Test mit zwei
Skeletten bestand der neue Stand 0 von 3 Läufen, der alte 1 von 3; kein Nutzen belegt.

## 18. Runde 12: Woran Bob jetzt stirbt – und dagegen

**Datengrundlage:** 25 Tode aus den neuen Läufen (Seeds 1234, 77, 4242), ausgewertet nach
Ursache, Ort, Tiefe und laufender Aufgabe.

| Befund | Ursache | Lösung | Beleg |
| --- | --- | --- | --- |
| 4 Tode durch Ertrinken, zweimal mitten in „Luft holen“ | Die Luft-Aufgabe galt als erledigt, sobald der Kopf einen Tick lang aus dem Wasser ragte. Der Navigator tauchte sofort wieder ab; mit jedem Auftauchen blieb weniger Luft. | Bob bleibt oben, bis die Luft wieder zu 90 % voll ist. | Test `long_lake_bed`: Auftauchen 29× → 5× |
| 2 Tode durch Ersticken, beide nach Kies-Abbau für Feuerstein | Kein Reflex dafür | Neuer Reflex: Kopf im Block → sofort freigraben, vor allem anderen | Test `buried_in_gravel`: alt erstickt, neu frei |
| 49 Tode auf Seed 77, davon 41 durch Zombies | Spawn-Camping: Nach jedem Tod stand Bob ohne Waffe an seinem Bett, um das nachts Zombies standen. Er wehrte sich mit Fäusten, schon ab 7 Herzen. | Ohne Waffe gegen Nahkämpfer flieht Bob weit (32 Blöcke), statt zu kämpfen. | Seed 77: 49 → 2 Tode, Seed 1234: 10 → 1 |
| Flucht hängt fest, Bob wird weiter getroffen | Kein Abbruch einer festgefahrenen Flucht | Kommt die Flucht nicht voran und Bob wird getroffen, bricht er sie ab; er gilt als in die Enge getrieben und wehrt sich. | Test `unarmed_zombies` |
| 2 Creeper-Tode ohne jede Reaktion | Die Reflexe für Creeper und Rückzug schwiegen, solange irgendeine Lauf-Aufgabe oben lag – nicht nur der eigene Rückzug. | Ausnahme nur noch für den eigenen Rückzug; Bob sagt, wovor er wegläuft. | Test `creeper_on_the_way`: 2 von 3 bestanden, alt ebenso; die Lücke im Benchmark-Log ist geschlossen. |
| Sachen holen: 0 von 10 Versuchen erfolgreich, mehrere Zweit-Tode | Abstecher unterwegs; mit leeren Händen zurück zum Mob, der Bob gerade getötet hat; zu tief unter Tage | Neuer Teil von L5, siehe unten | – |

**L5 schlauer Neustart:** Bob holt seine Sachen nur, wenn es sicher und machbar ist.

- Nicht, wenn ein Mob ihn getötet hat und er unter Tage oder in der Nacht starb.
- Nicht, wenn die Sachen deutlich tiefer liegen als der Wiedereinstiegspunkt.
- Nur, wenn er in der verbleibenden Liegezeit (5 min) hinkommt.
- Unterwegs keine Abstecher.

**L8 lernende Schätzungen:** Das Großhirn glaubt einer Schätzung, was das laufende Spiel gezeigt
hat:

- Jeder Fehlschlag zählt die Schätzung einmal mehr.
- Nie schneller als im Mittel tatsächlich gebraucht.

Gilt für Bündel, Essen und alle Felder. Auf Seed 77 kostete das Bündel 594 s, geschätzt waren
~120 s.

**Testarena:** `BotArena.wide` verlängert den Boden um 20 Blöcke in jede Richtung, damit Fluchttests
nicht an der Arenakante enden.

**Verworfen:** Deckung ab 16 Herzen gegen mehrere Schützen. Im Test bestand der neue Stand 0 von 3
Läufen, der alte 1 von 3; kein Nutzen belegt.

### 18.1 Messungen, 90 min HARD

| Seed | Alt (`c209711`): Felder | Alt: Tode | Neu: Felder | Neu: Tode |
| --- | --- | --- | --- | --- |
| Live | 10 | 7 | 12 | 5 |
| 77 | 10 | 8 | 10 | 2 |
| 4242 | 1 | 29 | 2 | 4 |
| 1234 | 6 | 1 | 5 | 1 |
| **Summe** | **27** | **45** | **29** | **12** |

Live-Seed und 4242 liefen noch ohne Fluchtregel.

**Befund:** Die Tode sind auf ein Viertel gefallen. Der Engpass ist jetzt das Tempo: Fehlschläge
bei Besorgungen und Feldern (Bündel, Bogen, Moos) und lange Wege.

## 19. Baritone gegen Bob – Bestandsaufnahme und Nachbau

**Quelle:** Baritone (cabaletta/baritone, `master`):

- `Settings.java`: 213 Einstellungen;
- `MovementHelper.java`: die Regeln, was betreten und abgebaut wird.

Bob nutzt Baritone nicht; die Regeln sind nachgebaut.

**Befund vorab:**

- Baritone kämpft nicht. Es überlebt, weil es nach strengen Regeln läuft und gräbt.
- Seine Mob-Meidung (`avoidance`) ist ab Werk sogar aus.
- Bob muss zusätzlich kämpfen, Nether-Ziele holen und nachts draußen sein. Die Regeln sind deshalb
  nötig, aber nicht genug.

| Baritone | Was es tut | Bob vorher | Jetzt |
| --- | --- | --- | --- |
| `avoidWalkingInto` | nie in Flüssigkeit, Magma, Kaktus, Feuer, Spinnweben | ja, dazu Beerenbusch, Pulverschnee, Wither-Rose, Lagerfeuer, Tropfsteinspitzen, Druckplatten, Stolperdraht | – |
| `avoidAdjacentBreaking` (Flüssigkeit) | nichts abbauen, wo Lava oder Wasser nachfließt | ja (`liquidAround`) | – |
| `avoidAdjacentBreaking` (fallende Blöcke) | nichts abbauen, neben dem ungestützter Sand oder Kies liegt | nein | **nachgebaut** |
| `avoidBreaking` | Silberfischchen-Blöcke, Eis | nein | **nachgebaut** |
| `pauseMiningForFallingBlocks` | warten, bis fallende Blöcke gelandet sind | nein | **nachgebaut** (Navigator wartet) |
| `maxFallHeightNoWater` = 3 | Fall nur bis 3 Blöcke | ja (`MAX_FALL` = 3) | – |
| `allowWaterBucketFall` | Wassereimer bei tiefem Fall | ja | – |
| `avoidance` / `mobAvoidance*` | Wege nahe Mobs ×1,5 (Radius 8) | nein | **nachgebaut**, gestaffelt ×4 / ×2,5 / ×1,5 nach Abstand, weil der flache Faktor 1,5 den geraden Weg dicht am Mob nicht verhindert (getestet) |
| `mobSpawnerAvoidance*` | Wege nahe Spawnern ×2 (Radius 16) | nein | **nachgebaut** (Spawner und Trial-Spawner aus den geladenen Chunks) |
| `costVerificationLookahead` = 5 | die nächsten 5 Schritte laufend nachprüfen | nur den aktuellen Schritt | **nachgebaut** |
| `strictLiquidCheck`, `assumeWalkOnWater`/`Lava` | Sonderfälle | – | nicht nötig |
| `allowParkour`, Diagonalen | riskante Sprünge | Sprünge mit Prüfung der Absturzgefahr, im Nether vorsichtig | – |
| `autoTool`, `allowInventory` | Werkzeugwahl, Hotbar | ja | – |
| `itemSaver` | Werkzeug vor dem Bruch schonen | Ersatz-Spitzhacke vor dem Bruch | – |
| `blacklistClosestOnFailure` | Unerreichbares merken, nächstes nehmen | ja (`unreachable`) | – |
| `exploreForBlocks` | erkunden, wenn nichts bekannt | ja (`Explorer`) | – |
| `legitMine` | Erz nur sehen, nicht wissen | ja (Bob sieht nur, was sichtbar ist) | – |
| `mineScanDroppedItems` | liegende Drops mitnehmen | ja | – |
| Prozesse Follow, GetToBlock, Mine, Explore, Farm | – | ja (Follow, NavGoal, Mine, Explorer, Ernte) | – |
| Prozesse Build, Backfill, Elytra | Schematics, Löcher füllen, Elytra | nein | für Lockout nicht nötig |

**Eigene Ergänzung, angestoßen durch die Messungen:** Unter Wasser darf ein Weg nur dort verlaufen,
wo Luft gerade darüber liegt, nicht unter einer Felsdecke (geflutete Höhlen). Auf Seed 77 war das
15-mal die Todesursache.

**Tests (alle grün, jeweils mit Gegenprobe gegen den alten Stand):**

| Test | Szenario | Neu | Alt |
| --- | --- | --- | --- |
| `way_round_monster` | Zombie mitten auf dem geraden Weg | geht mit 7,4 Blöcken Abstand vorbei | 1,1 Blöcke |
| `flooded_tunnel_avoided` | gefluteter Tunnel unter der Wand oder trockener Umweg | trockener Umweg | taucht |

## 20. Runden 13–15: Messreihe und weitere Bausteine

### 20.1 Neue Bausteine

| Baustein | Anlass | Beleg |
| --- | --- | --- |
| **Schild-Anlauf:** Mit Schild geht Bob hinter dem erhobenen Schild auf Schützen zu. Ein Bot wird dabei nicht gebremst, nur der Sprint fällt weg. Bob senkt den Schild erst in Schlagweite; der Schild braucht 5 Ticks oben, bevor er blockt. | Skelett-Tode | `shield_charge_skeleton`: neu 3/3 (6/0/0 Herzen verloren), alt 2/3 (6/0/9,7) |
| **Creeper-Flucht im Tick-Takt:** Vorrang vor allem, auch vor dem Pfeil-Ausweichen. Nah und mit Schild: Schild gegen den Creeper; sonst Sprint weg. | 4 Creeper-Tode auf Seed 4242, teils während Bob Pfeilen auswich | `creeper_on_the_way`: 3/3 (vorher 2/3) |
| **Absteigen:** Bob steigt direkt ab; Schleichen allein holt einen Bot ohne Client nicht vom Tier. Ohne Reit-Aufgabe steigt er nach 5 s von jedem Reittier oder Fahrzeug ab. | Bob saß im Spiel des Spielers auf einem Schwein fest | `off_the_pig`: alt bleibt sitzen, neu kommt an |
| **Abstecher nur, wenn fit:** ab 14 Herzen, nicht im Überlebensmodus; Jagd nur bewaffnet | Nächtliche Skelettjagd „für einen Pfeil“ auf Seed 77 | – |

**Verworfen: Deckungswand gegen wiederholte Schützen.** Bob blieb hinter der Wand stehen, das
Skelett lief seitlich herum.

| Stand | Ergebnis im Test |
| --- | --- |
| mit Wand | 3 von 3 erschossen |
| ohne Wand (nur Ausweichen) | 3 von 3 ohne Herzverlust |

Der Test `skeleton_cover` bleibt als Regressionsschutz für das Ausweichen.

### 20.2 Messreihe, 90 min HARD (Felder / Tode)

| Stand | Live | 77 | 4242 | 1234 | Summe |
| --- | --- | --- | --- | --- | --- |
| Ausgangsstand vor Runde 11 | 10/7 | 10/8 | 1/29 | 6/1 | **27/45** |
| Runde 12 (Fluchtregel u. a.)¹ | 12/5 | 10/2 | 2/4 | 5/1 | **29/12** |
| Runde 13 (lernende Schätzungen) | 14/1 | 7/23² | 3/6 | 6/9 | **30/39** |
| Runde 14 (Baritone-Regeln) | 11/5 | 12/6 | 0/13 | 6/2 | **29/26** |
| Runde 15 (Schild-Anlauf, Creeper-Flucht) | 9/4 | 5/12 | 4/4 | 6/3 | **24/23** |

¹ Zusammengesetzt aus verschiedenen Ständen: Live-Seed und 4242 stammen aus Runde 12 noch ohne
Fluchtregel, 77 und 1234 aus den Läufen mit Fluchtregel.

² Davon 15-mal ertrunken in einer gefluteten Höhle; seit Runde 14 sperrt die Pfadsuche Wege unter
Wasser ohne Luft darüber.

**Befund:**

- Die Tode liegen deutlich unter dem Ausgangsstand.
- Die Felder schwanken je Lauf um ±5, weil ein einzelner Tod oder eine Fehlentscheidung am Anfang
  das ganze Spiel verschiebt.
- Häufigste Todesursache sind jetzt Skelette (Seed 77, 4242), oft früh und nachts auf Hügeln,
  noch ohne Schild und Rüstung.
- Gegen einen einzelnen Schützen reicht das Ausweichen im Test (0 Herzen verloren). Im Feld kommen
  Gelände, mehrere Schützen und Vorschäden dazu.

**Offen:**

- Früher an Schild und Rüstung kommen.
- Nachts ohne Rüstung nicht auf offenem Hügelgelände arbeiten.

### 20.3 Runde 16 und die Frage „Rückschritt?“

**Runde 16** (Abstecher nur, wenn fit; Absteigen vom Reittier):

| Seed | Felder / Tode |
| --- | --- |
| Live | 7/5 |
| 77 | 6/6 |
| 4242 | 6/7 |
| 1234 | 5/6 |
| **Summe** | **24/24** |

Auf dem Live-Seed sanken die Felder über die Runden 13 bis 16 (14 → 11 → 9 → 7). Zwei Prüfungen:

**1. Navigations-Benchmark** (30 Ziele auf der Oberfläche, friedlich): vor den Baritone-Regeln
(`4a5e8d6`) gegen den aktuellen Stand.

| Stand | Erreicht | Tempo | Umweg | Suchen ohne Weg |
| --- | --- | --- | --- | --- |
| alt | 30/30 | 3,18 Blöcke/s | 1,20 | 1 |
| neu | 30/30 | 3,17 Blöcke/s | 1,20 | 0 |

Die neuen Wegregeln kosten auf der Oberfläche keine Zeit.

**2. Direkter Vergleich auf dem Live-Seed**, parallel gespielt, 90 min HARD:

| Stand | Felder | Tode | Eisen | Eimer |
| --- | --- | --- | --- | --- |
| alt (`4a5e8d6`) | 9 | 5 | 355 s | 934 s |
| neu | 8 | 6 | 180 s | 362 s |

**Befund:** Kein Rückschritt; die 14 Felder in Runde 13 waren ein Ausreißer nach oben. Ein
einzelner Lauf streut um ±3–5 Felder. Belastbare Vergleiche brauchen mehrere Läufe je Stand.

## 21. Runde 17: Schild, Start-Kit, Unterschlupf – und was die Messungen (nicht) zeigen

### 21.1 Umgesetzt

| Baustein | Anlass | Beleg im Test |
| --- | --- | --- |
| **Schild-Gehen:** Mit Schild hält Bob ihn zum Schützen hoch und geht weiter (ein Bot verliert dabei nur den Sprint); ein anfliegender Pfeil wird mit dem Schild abgefangen statt mit einem Seitschritt. Der alte Reflex blieb stehen und senkte den Schild nach jedem Pfeil: jedes Mal fünf ungeschützte Ticks. Unter vier Blöcken Abstand wird gekämpft. | Seed 4242: mit Schild am Arm von Strays erschossen | `shield_walk_strays` (drei Strays von der Seite): alt erschossen, neu 0 Herzen verloren |
| **Essen, wenn verletzt:** Unter 12 Herzen und nichts zu essen geht Essen vor, auch nachts. | Live-Seed: halbe Gesundheit, kein Essen, Creeper-Jagd in einer Höhle | – |
| **Brustpanzer auf HARD:** Sobald Eisenspitzhacke und Schild da sind, kommen dessen acht Eisen auf die Einkaufsliste. | Kein einziger Lauf baute einen Panzer | – |
| **Unterschlupf für die Nacht:** Wiedergeboren, nachts, ohne Waffe, Monster im Umkreis (keines näher als 8 Blöcke): mit bloßen Händen drei Blöcke tief, die Erde als Deckel, bis zum Morgen. | Seed 77: zwölf Tode in zehn Minuten am Spawn | `night_shelter_bare`: allein bestanden; im vollen Testlauf scheitert er, wenn ein Zombie im Moment des Deckelns auf der Deckelposition steht |
| `get_shot` repariert (wartet, wo nichts mehr zu erkunden ist; kein Schild beim absichtlichen Treffer) | Test schon auf altem Stand rot | grün |

Verworfen: Essen holen bei leerem Rucksack bis drei Minuten, auch satt. Auf Seed 77 holte Bob in
Minute 0 und 2 Äpfel (74 s, 157 s) vor den Werkzeugen und kam damit in die Nacht.

### 21.2 Messungen

90 min HARD, je ein Lauf: alter Stand Live 12/2, 4242 0/6, 77 10/2, 1234 4/6 (Felder/Tode);
Zwischenstände der neuen Version Live 11/6, 77 5/14.

30 min HARD, Seeds 11, 22, 33, 55, je Version zwei Läufe (einmal je Arbeitsverzeichnis, um einen
Einfluss des Verzeichnisses auszuschließen):

| Stand | Lauf A | Lauf B | Summe Tode | Summe Felder |
| --- | --- | --- | --- | --- |
| alt (`87301f3`) | 5 Tode, 12 Felder | 11 Tode, 15 Felder | 16 | 27 |
| neu (`95c653a`) | 13 Tode, 11 Felder | 14 Tode, 11 Felder | 27 | 22 |

**Befund:**

- Im Mittel 2,0 gegen 3,4 Tode pro Lauf. Die Streuung je Lauf ist mit etwa ±2 so groß, dass der
  Unterschied noch nicht gesichert ist: Derselbe alte Stand hatte in Lauf A 5, in Lauf B 11 Tode.
- **In keinem der 30-Minuten-Läufe baute Bob einen Schild**; das Schild-Gehen war nie aktiv, der
  Unterschlupf löste nicht aus, die Essens-Ausflüge waren gleich häufig. Der Code verhält sich im
  frühen Spiel ohne Schild wie der alte. Ein Rückschritt durch diese Änderungen ist damit
  unwahrscheinlich, aber auch ein Nutzen ist in den Benchmarks noch nicht belegt.
- **Der eigentliche Engpass:** Eisen kommt spät (erstes Eisen oft nach 10–25 min, in 7 von 16
  Läufen gar nicht in 30 min). Ohne Eisen kein Schild und keine Rüstung; die meisten Tode fallen
  in genau diese Zeit (Skelette, Zombies, nachts, ohne Essen).
- Ein seltener Absturz von 26.3 selbst trat auf: `NullPointerException` bei der Mondphase während
  der Chunk-Erzeugung (`ServerLevel.getMoonBrightness`). Er liegt nicht im Code des Mods.
- Die Laufumgebung räumt Prozesse ab, sobald die Sitzung ruht: Lange Messreihen laufen nur, solange
  aktiv gearbeitet wird.

### 21.3 Nächste Schritte

1. Erstes Eisen früher: Oberflächen-Höhlen und freiliegendes Eisen gleich nach den Steinwerkzeugen
   gezielt nutzen; der Schild als erstes Eisenteil vor Spitzhacke und Eimer.
2. Mehr Läufe pro Stand (mindestens acht je Version), Tode pro Lauf mit Streuung berichten.
3. Den Deckel-Grenzfall im Unterschlupf lösen (Monster auf der Deckelposition).

## 22. Runde 18: Eisen früh? Messreihe, Fehlerfunde, YouTube-Recherche

### 22.1 Recherche (Lockout-Videos)

- **Werkzeug:** Mit yt-dlp wurden die automatischen Untertitel von 8 Videos geladen: Feinberg
  (Lockout/Draftout), Lockout-Turnier, Ranked und Speedrun-Tricks. Die Transkripte liegen im
  Archiv-Repo unter `research/youtube-transcripts/`.
- **Video-Frames** blockiert YouTube von hier aus mit der Bot-Prüfung. Dafür bräuchte es Cookies
  oder einen Gemini-Schlüssel.
- **Lehren:**
  - Gute Spieler holen Eisen zuerst aus Truhen: zuerst der Schmied im Dorf, dann Schiffswracks und
    vergrabene Schätze, außerdem vom Eisengolem.
  - Essen kommt aus Dorf und Wrack.
  - Lederrüstung von Kühen als schnelle Rüstung.
  - Tode kommen fast immer durch Skelette, Hunger oder Lava beim Graben nach unten.

### 22.2 Was gebaut und gemessen wurde

| Änderung | Ergebnis |
| --- | --- |
| Eröffnung auf HARD: Eisen-Spitzhacke und Schild direkt nach den Steinwerkzeugen (Höhle oder Treppe) | **verworfen**: mehr Tode (Seed 66: 6–7 Tode, Seed 77: 3–4), Eisen nicht früher |
| Auf HARD vor dem Schild Eisen immer „gewünscht“ (Kit-Bedarf) | **verworfen**: 8–15 Eisen-Ausflüge pro Spiel mit Steinwerkzeug, doppelt so viele Tode |
| Eisen nur aus gesichtetem Erz nah an der Oberfläche (≤ 10 unter der Oberfläche, ≤ 48 entfernt) | drin (Wunsch des Spielers); löst selten aus |
| Wrack oder Dorf vor dem Schild plündern (bei Tag) | drin; hat in den Läufen nie ausgelöst |
| Wenige Herzen in Höhle, Dunkel oder bei Monstern: eingraben, essen, heilen | drin; löst kaum aus, meist fehlt Essen |
| Erkunden für Land-Dinge: Land bevorzugen, nach 3 Abschnitten auf offener See zurück | **Fehlerfix**: Seed 11 schwamm 1300 Blöcke aufs Meer |
| Wächter „steckt fest“: setzt bei verworfenen Wegen nicht mehr zurück; beim 2. Mal an derselben Stelle an die Oberfläche graben | **Fehlerfix**: Seed 33 (5 min) und alt Seed 22 (25 min, 0 Felder) |
| In der Baumkrone fest (Respawn auf Blättern): nach 10 s durch das Laub nach unten | **Fehlerfix**: Seed 66, 3 min; Test `tree_top_down` |
| Erinnertes Erz zweimal unerreichbar: dann graben statt zum nächsten laufen | **Fehlerfix**: Seed 33 lief 5 min von Ader zu Ader |

### 22.3 Messung (30 min HARD)

| Stand | Läufe | Felder | Tode | pro Lauf |
| --- | --- | --- | --- | --- |
| alt `73660df` | 16 (Seeds 11–88, teils doppelt) | 65 | 26 | 4,1 Felder, 1,6 Tode |
| neu `139158b` | 8 | 25 | 16 | 3,1 Felder, 2,0 Tode |

- Derselbe alte Stand schwankte auf Seed 66 zwischen 7 Feldern/0 Toden und 3 Feldern/2 Toden.
- Auf Seed 11/22/33/55 liegen alt und neu gleichauf; auf 66/77/88/44 liegt neu etwas schlechter.
- Bei der Streuung (±2 Tode pro Lauf) ist kein Unterschied gesichert. Die neuen Verhaltensweisen
  lösten in den Läufen fast nie aus.
- **Wichtigste Lehre:** Frühes Eisen um jeden Preis kostet Leben. Jede Variante, die Bob mit
  Steinwerkzeug unter Tage schickt, verdoppelt die Tode.

### 22.4 Woran Bob stirbt (alle Läufe)

- Skelette (größter Anteil, auch beim Ausweichen);
- Zombies bei wenig Leben;
- Creeper;
- Ertrinken in Höhlenwasser;
- Wüste (Husk/Parched) und Plünderer;
- danach oft eine Todesspirale: zurück an dieselbe Stelle, ohne Rüstung und Essen.

### 22.5 Nächste Schritte

1. **Skelett-Kampf:** Deckung suchen (Säule oder Block in die Schusslinie), Abstand verkürzen
   hinter Hindernissen.
2. **Essen** als festen Teil des Start-Kits (Tiere, Brot), damit Heilen überhaupt möglich ist.
3. **Nach einem Tod** nicht in dieselbe Höhle zurück. Die Todesstelle für einige Minuten meiden.
4. **Mehr Läufe pro Stand** (16 statt 8) und dieselben Erfahrungsdateien für beide Stände, sonst
   misst man Rauschen.

## 23. Runde 19: Analyse der Entscheidungslogik – und „nicht mehr sterben“

### 23.1 Wie Bob entscheidet (Bestandsaufnahme)

Bob hat drei Ebenen:

1. **Reflexe** (`Bot.reflexes`, `ThreatSense`, alle 1–10 Ticks), immer aktiv:
   - Luft, Lava, Feuer, Pulverschnee;
   - Creeper;
   - wenig Leben → weg, Säule oder Loch;
   - zurückschlagen, Schild, Pfeilen ausweichen, essen.
2. **Gehirn, während einer Aufgabe** (`LockoutBrain.tick`): nur Zeitbudgets, Gelegenheiten am Weg
   und Ersatz-Spitzhacke.
3. **Gehirn, zwischen Aufgaben** (`LockoutBrain.think`): eine Leiter in dieser Reihenfolge:
   1. Sachen holen;
   2. Essen;
   3. Heilen;
   4. Eröffnung;
   5. Kit;
   6. Plündern;
   7. Nether;
   8. Investition;
   9. Jagd;
   10. Schlaf;
   11. Brettziele.

### 23.2 Gefundene Lücken (gegenüber einem normalen Spieler)

- **Überlebens-Checks nur zwischen Aufgaben.** `think()` läuft nur, wenn keine Aufgabe ansteht.
  Folge: Mitten in einer 5-Minuten-Grabung greifen Essen, Heilen und der Überlebensmodus nie, nur
  die Reflexe.
- **Keine Nacht-Planung.** Die Auswertung von 54 Toden zeigt: 29 davon (54 %) in Minute 10–20,
  also in der ersten Nacht, an der Oberfläche, ohne Rüstung. Schlafen ging nur mit Bett im
  Gepäck, das Bob nie baute. Fast jedes Brett hat ein „Nacht-Feld“ (Zombie, Spinne, Pfeil), also
  schlief er nie. Nachtjagden liefen auch ungerüstet.
- **Kein Essensvorrat.** In 30 von 54 Toden hatte Bob nichts zu essen dabei. Auf HARD kommen
  Herzen nur mit vollem Magen zurück. Vorrat holte er nur, wenn er sehr billig war.
- **Säule gegen Spinnen.** Spinnen klettern. Mehrere Tode nach „hurt, with a spider: up a pillar“.
- **Faustkampf mit Skeletten.** Ohne Waffe startete der Treffer-Reflex trotzdem einen Nahkampf.
- **Skelette ungerüstet angreifen** ab 12 Leben, auch gegen zwei Schützen.
- **Creeper zu nah:** Weglaufen aus 2,8 Blöcken scheiterte mehrfach.
- **Einzelfehler:**
  - Unterschlupf in einem Süßbeerenstrauch;
  - Ersticken (falscher Block freigegraben);
  - Graben unter Sand.

### 23.3 Was gebaut wurde

- **Nachtplan:**
  - Ab Spielzeit 10000 prüft Bob, ob er für die Nacht gerüstet ist: Waffe, mindestens 8
    Essenspunkte, Rüstung ≥ 6 oder Schild mit Rüstung ≥ 2, mindestens 12 Leben.
  - Wenn nicht: vor der Dämmerung ein Bett (wenn es günstig zu haben ist) und Essen. Bei
    Nachteinbruch schlafen, sonst eingraben bis zum Morgen. Mit Spitzhacke gräbt er stattdessen
    einen eigenen, geschlossenen Stollen zum Eisen (er bricht keine Höhle an).
  - Laufende Aufgaben werden bei Nachteinbruch unterbrochen.
  - Nachtjagden nur noch gerüstet.
- **Essen:**
  - Vorrat, sobald die Steinwerkzeuge da sind.
  - Mitten in einer Aufgabe holt er Essen, wenn keins mehr da ist und er hungrig oder verletzt
    ist.
- **Kampf:**
  - Spinnen: ins Loch mit Deckel statt Säule.
  - Keine Säule, wenn ein Schütze zielt.
  - Skelette ohne Schild oder Rüstung erst ab 16 Leben und nur einzeln angreifen.
  - Kein Faustkampf außer in die Enge getrieben.
  - Zu naher Creeper: erst ein Schlag (Rückstoß), dann weg.
- **Fixes:**
  - kein Unterschlupf in Beerenstrauch, Kaktus, Feuer oder Spinnweben;
  - beim Ersticken den richtigen Block freigraben;
  - keine Treppenstufe unter Sand oder Kies, aus Sand seitlich heraustreten.
- **Tests:** `skeleton_behind_wall`.

  Dabei zeigte sich: Deckung hinter einer kleinen Mauer verliert gegen ein Skelett, das außen
  herumläuft, während reines Ausweichen auf 12 Blöcke 0 Leben kostet. Die Deckungssuche ist
  deshalb wieder raus.

### 23.4 Messung (30 min HARD, Seeds 11–88)

| Stand | Läufe | Tode | Tode pro Lauf | Felder pro Lauf |
| --- | --- | --- | --- | --- |
| alt (Runde 18) | 16 | 26 | 1,6 | 4,1 |
| A: Nachtplan | 8 | 8 | 1,0 | 4,0 |
| B: + Essen und Kampf | 8 | 8 | 1,0 | 3,0 |

- Die Nacht-Tode sind praktisch weg.
- Die übrigen Tode sind verstreut:
  - Ertrunkener beim Jagen am Wasser;
  - Ersticken im Sand;
  - Beerenstrauch;
  - Enderman;
  - Skelette auf kurze Distanz.

### 23.5 Faire Messung (gleiches Brett pro Seed)

**Problem der bisherigen Messungen:** Das Bingo-Brett war bei jedem Lauf ein anderes, weil die
Startzeit und eine zufällige Spieler-UUID in den Seed eingehen. Das Brett bestimmt, was ein Lauf
riskiert, deshalb schwankte derselbe Seed zwischen 0 und 7 Toden.

**Lösung:** Die Benchmarks setzen jetzt `CHALLENGECRAFT_BOARD_SEED` (in `bench.sh`, `BOARD_SEED`
oder der Welt-Seed). Damit bekommen zwei Stände dasselbe Brett. Im normalen Spiel bleibt das Brett
zufällig.

**Messung (30 min HARD, je 8 Seeds, gleiche Bretter):**

| Stand | Tode | Felder pro Lauf |
| --- | --- | --- |
| alt (`0e39c23`) | 6 in 7 Läufen (Seed 66 fehlt noch) | 3,5 |
| neu (`1ff88ef`) | 13 in 8 Läufen, davon 11 in einer Todesspirale auf Seed 77; ohne diese 2 in 7 Läufen | 4,4 |

**Die Todesspirale:** Bob saß nach einem Respawn 10 Minuten an einem Hang am Spawn fest. In der
Nacht starb er dort 10-mal in 2 Minuten: ohne Waffe, ohne Ausweg, mit Fäusten.

**Behoben durch:**

- Nachts wartet Bob nach dem Tod bis zur Dämmerung, statt sofort zu respawnen (höchstens
  6 Minuten), wie ein Spieler.
- Steckt er zweimal an derselben Stelle fest, gräbt er sich frei (`DigOutTask`).

Mit diesen Fixes hatte Seed 77 im nächsten Lauf 0 Tode.

### 23.6 Weitere Fixes dieser Runde

- Vor Schützen gräbt sich Bob bei wenig Leben ein, statt wegzulaufen.
- Nahe Skelette (≤ 6 Blöcke) greift er schon ab 10 Leben an.
- Endermen schaut er nie in die Augen: Der Blick geht nach unten (Test `enderman_no_stare`).
- Schwere Gegner (Enderman, Hexe, Vindicator …) nur mit Rüstung und vollem Leben.
- Keine Gelegenheiten am Weg, während er sich für die Nacht eingräbt.
- Unterschlupf:
  - nicht in Ranken oder Leitern;
  - auf Stein auch ohne Block im Gepäck;
  - nicht mitten im Sprung prüfen;
  - abbrechen, wenn das Graben nicht vorankommt.
- Steht er oben in einer Baumkrone, steigt er sofort durch das Laub ab.
- Creeper zu nah: Rückstoß-Schlag unabhängig von der Aufladung.
- Nicht unter Sand oder Kies graben; aus Sand tritt er seitlich heraus.
- Der Nachtstollen (Eisen in einem eigenen, geschlossenen Tunnel) brachte kaum Eisen und ist
  abgeschaltet (`NIGHT_MINE`).
