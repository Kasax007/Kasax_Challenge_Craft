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
