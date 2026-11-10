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
- **Ältere Ligen:** aus `docs/bob-leagues/league1.jsonl` bis `league8.jsonl` (190 Bot-Spiele). Die
  Spalte „older leagues: s on it“ ist die mittlere Zeit auf der Wurzelaufgabe des Ziels in den
  Spielen, in denen sie unter den zehn größten Zeitposten war (für Schild und Kupfer ist das fast
  immer das Kit, nicht das Feld). „claims“ zählt, wie oft das Feld dort an einen Bob ging.

## Die Wissensbasis

`speedrun.json` hat drei Teile:

| Teil | Inhalt | Wofür Bob es nutzt |
| --- | --- | --- |
| `goals` | 304 Ziele (jedes Ziel, das auf ein normales Brett kommt; die acht End-Ziele kommen auf keins): Expertenzeit, Wege (schnellster zuerst, als „Art: was zu tun ist“), was sie brauchen, wann sie gehen | Expertenzeit im Log und im Journal; die Wege als Wissen zum Nachlesen |
| `mobs` | 46 Mobs: Rudelgröße, Bobs Kampfzeit, Strukturen, an denen sie leben, „nur nachts dort“, Fernsicht | Suchzeit und Kampf in der Schätzung (`MobSearch`), die Jagd am Wohnort (`LockoutGoals.huntAt`) |
| `structures` | 16 Strukturarten: welche Kisten-Tabellen wie oft, Sekunden zum Plündern, gefährlich oder nicht | Kisten als Quelle eines Items, wenn die Struktur bekannt ist (`LockoutGoals.loot`) |

Die Loot-Chancen stehen nicht in der Datei. `SpeedrunKnowledge` würfelt beim ersten Gebrauch jede
Kisten-Tabelle 300-mal aus den Loot-Tabellen des laufenden Spiels (wie `BotKnowledge` die
Block-Drops). So stimmen sie für jede Spielversion. Beispiele für 26.3: Eisen aus einem Schiffswrack
94 % (Schatzkiste), aus einem vergrabenen Schatz 100 %, ein Namensschild aus einer Mine 72 %
(drei Lorenkisten).

Neue Ziele bekommen einen Eintrag in `goals`. Der Test `bot_knowledge_tests_knowledge_base` meldet
jedes Ziel des Pools ohne Eintrag.

## Wofür Bob die Wissensbasis nutzt

1. **Schätzungen als Stapel** (`ObtainPlanner.batch`, Schätzweise `ObtainPlanner.ESTIMATES = 2`).
   Bisher war die Schätzung für n Stück der Anteil eines Stücks mal n. Ein Pfeil kostete so ein
   Viertel eines Rezepts, und bei 16 Holz zählte der Weg zum Baum sechzehnmal. Jetzt folgt die
   Schätzung den Wegen des Plans und zählt wie ein Spieler:
   - ein Rezept so oft, wie seine Ausbeute es verlangt;
   - bei einem Rohstoff das Finden einmal (der Weg zur Ader, zum Baum, zur Herde), dazu jedes Stück;
     ist das Vorkommen aufgebraucht (eine Ader hat etwa sechs Eisenerze, ein Baum fünf Stämme), die
     Suche nach dem nächsten anteilig (`BotKnowledge.patchSize`, `hop`);
   - ein Werkzeug oder eine Station einmal;
   - eine schon gefundene Ader dient allen Teilen eines Kits (`estimateAll`: das Eisen für
     Spitzhacke und Eimer aus einer Reise; so rechnet jetzt der `LockoutStrategist` die Kits);
   - bei Zutaten mit mehreren möglichen Items das, dessen Stapel von hier aus am billigsten ist
     (ein Eisennugget aus einem gehaltenen Barren, nicht ein Kupfernugget).
2. **Mob-Suche nach den Spawnregeln** (`MobSearch`). Die Suchzeit ist 60 / (Rate × Anteil ×
   Fernsicht) Sekunden:
   - **Anteil:** der Anteil der Art an der Spawnliste ihrer Kategorie im Biom (Gewicht mal mittlere
     Rudelgröße, Schleim ausgenommen; `MobHabitats.share`). Ein Skelett ist in der Ebene ein Viertel
     der Nachtmonster, in der Wüste ein Achtel, eine Hexe eins von dreihundert.
   - **Rate:** Monster, die beim Umsehen je Minute ins Blickfeld kommen: nachts an der Oberfläche 5,
     in einer Höhle oder im Nether 6, tagsüber 0,8 (nur die, die die Sonne nicht verbrennt), Tiere 6,
     Fische 4. Geeicht an den Nachtjagden der Ligen 3 bis 8 (Skelett 72 s, Zombie und Creeper 80 bis
     90 s, mit Kampf).
   - **Fernsicht:** ein Enderman fällt dreimal so weit auf.
   - Arten, die die Sonne verbrennt, sind am Tag ein Warten auf die Nacht und dann die Nachtsuche.
   - Dazu Bobs Kampfzeit und die Rudelgröße aus der Wissensbasis (der zweite Mob eines Rudels ist
     nah). Höchstens 1800 s; länger ist keine Suche mehr.
   - Arten, die nur an ihrer Struktur leben (Lohe, Plünderer), und Sonderfälle (Schleim, Phantom)
     werden nicht so gesucht: dort gilt die alte Zahl oder die Jagd am Wohnort (Punkt 4).
3. **Erfahrung** (`GoalExperience`). Sie lernt den Logarithmus des Verhältnisses „echt durch
   geschätzt“, im Band 0,25 bis 8 (vorher 0,6 bis 2; die Ligen hatten Faktoren 4 bis 9 gemessen).
   Wenige Spiele zählen mit Zweifel: Der Faktor ist `exp(Mittel · n / (n + 1,5))`, ein Spiel bewegt
   ihn 40 % des Wegs, drei Spiele 67 %. Vergessen senkt die Zahl der Spiele (10 % je Spiel), nicht das
   Gelernte. Ändert sich die Schätzweise, beginnt sie neu (`_estimates` in der Datei). Das gelernte
   Zielmodell `goal.json` ist gegen die alte Schätzweise trainiert. Bob setzt es deshalb nicht ein
   (Log: „trained on estimates v1“), bis der Trainer eins für Fassung 2 liefert
   (`docs/bob-training.md`).
4. **Quellenwahl** (`LockoutGoals.loot`, `LockoutGoals.huntAt`):
   - **Kisten einer bekannten Struktur:** gesehen oder im Atlas des Weltsinns, nicht schon geplündert,
     höchstens 300 s Weg. Die Kosten sind Weg + Plündern + (1 − Chance) × üblicher Weg. Bob nimmt
     den Weg, wenn er billiger ist als der übliche. Beispiele: Eisen aus der Schatzkiste eines
     Schiffswracks, Obsidian und Feuerzeug aus einem Ruinenportal, Schießpulver aus einem
     Wüstentempel.
   - **Gefährliche Orte** (Außenposten, Netherfestung, Bastion, Waldanwesen, Festung) nur mit Schild
     oder Rüstung und mindestens 16 Lebenspunkten.
   - **Nicht geplündert** (`NOT_RAIDED`): Minen (die Beute liegt in Lorenkisten, der Raubzug öffnet
     nur Kistenblöcke), Ozeanruinen (unter Wasser zwischen Ertrunkenen) und Verliese (keine
     Struktur).
   - **Ein Mob, wo er lebt:** Hexe an der Sumpfhütte (nachts), Plünderer am Außenposten, Lohe und
     Witherskelett in der Netherfestung, Piglin in der Bastion, Ertrunkener an der Ozeanruine. Kosten:
     Weg, Warten auf die Nacht, Suche dort und Kampf. Fernkämpfer nur mit Schild oder Rüstung.
5. **Log und Journal:** Die Zielzeile nennt die Planzahl und die Expertenzeit (`plan ~N s, expert
   ~N s`) und endet auf `[goal_id]`. Das Journal speichert `estimates` und `expert` je Ziel.

Noch nicht benutzt: die Texte der Wege (`ways`), `needs` und `when` der Ziele. Sie sind Wissen zum
Nachlesen und Stoff für die nächsten Runden.

## Schätzfehler vorher und nachher

### Die schlimmsten Ziele aus der Übergabe

Gemessen in den Ligen 7 und 8 mit der alten Schätzweise (`docs/bob-plan.md`, Abschnitt 25, Punkt 5),
daneben die neue Schätzung aus der Probe (feste Szene, siehe unten) und die Expertenzeit:

| Ziel | alt geschätzt | wirklich (Mittel) | neu geschätzt (Probe, Nacht / Tag) | Experte |
| --- | ---: | ---: | ---: | ---: |
| Skeleton | 27 s | 247 s | 118 / 717 s | 45 s |
| Name Tag | 18 s | 110 s | 18 s (Eisen und Zuckerrohr zur Hand) | 120 s |
| Arrow | 21 s | 100 s | 39 s (Kies und Hühner in Sicht) | 60 s |
| Firework Rocket | 30 s | 158 s | 82 / 338 s | 200 s |

Beim Skelett zählte die alte Zahl den Kampf nicht (für eines in Sicht nur den Weg hin). Die neue ist
nachts Suche und Kampf. Am Tag ist sie das Warten auf die Nacht: Das Feld ist dann nicht dran, und
Bob nimmt es in der Nacht. Beim Namensschild und beim Pfeil liegt das Material in der Probe schon
da. Dort zählt die neue Schätzung die eine ganze Herstellung statt eines Bruchteils.

### Eine feste Szene: die Probe

`BotEstimateProbe` (`bot_estimate_probe_*`) schätzt die Felder in einer festen Szene, einmal am Tag
(Zeit 1000) und einmal nachts (14000). Die Szene: eine flache Wüste mit Bäumen, Kies, Zuckerrohr an
einem Teich und drei Hühnern; Bob hat Steinwerkzeuge, ein Schwert, einen Schild, zwei Eisenbarren
und eine Werkbank. Der Test nutzt nur, was jede Fassung des Planers hat. Dieselbe Datei misst also
den alten Stand (`d85f36f`) und den neuen. Die Zahlen sind die geplanten Sekunden:

| Ziel | Tag alt | Tag neu | Nacht alt | Nacht neu | Experte |
| --- | ---: | ---: | ---: | ---: | ---: |
| `kill_skeleton` | 220 | 717 | 55 | 118 | 45 |
| `kill_zombie` | 180 | 866 | 45 | 267 | 30 |
| `kill_creeper` | 280 | 320 | 70 | 64 | 40 |
| `kill_spider` | 220 | 325 | 55 | 69 | 30 |
| `kill_enderman` | 799 | 889 | 200 | 290 | 120 |
| `kill_witch` | 899 | 1830 | 300 | 1830 | 300 |
| `obtain_name_tag` | 11 | 18 | 11 | 18 | 120 |
| `obtain_arrow` | 21 | 39 | 21 | 39 | 60 |
| `craft_firework_rocket` | 97 | 338 | 27 | 82 | 200 |
| `obtain_16_logs` | 113 | 50 | 113 | 50 | 60 |
| `obtain_32_cobblestone` | 698 | 66 | 698 | 66 | 45 |
| `craft_bow` | 427 | 436 | 171 | 146 | 120 |
| `obtain_ender_pearl` | 1598 | 1049 | 400 | 450 | 240 |
| `craft_item_frame` | 75 | 110 | 75 | 110 | 60 |
| `craft_painting` | 75 | 532 | 75 | 180 | 60 |
| `obtain_coal_block` | 281 | 74 | 281 | 74 | 90 |
| `craft_bucket` | 335 | 145 | 335 | 145 | 60 |
| `obtain_gold_ingot` | 766 | 633 | 766 | 633 | 150 |

So sind die Zahlen zu lesen:

- **Wüste:** Die Mob-Zahlen gelten für eine Wüste. Dort sind Zombies selten (die Wüste hat Husks):
  Ein Zombie braucht nachts 267 s, in der Ebene wären es etwa 63 s.
- **Hexe:** Ohne bekannte Sumpfhütte ist sie keine Suche (1830 s = die Obergrenze). Mit einer Hütte
  in 40 Blöcken plant Bob die Jagd dort (Test `witch_at_the_swamp_hut`).
- **Gemälde:** Am Tag ohne Schafe in Sicht wird die Wolle aus Spinnenfäden (vier Fäden) teuer. Die
  alte Zahl (75 s) übersah das.
- **Mengenfelder:** 16 Holz, 32 Bruchstein, ein Kohleblock und der Eimer zählen den Weg nicht mehr je
  Stück. Sie liegen jetzt nahe an der Expertenzeit.
- **Gold:** Der Barren bleibt teuer (633 s), weil Gold in der Wüste tief liegt und keine Struktur
  bekannt ist. Mit einem bekannten Ruinenportal oder Wüstentempel nimmt Bob die Kiste.

### Ligen vorher und nachher

Zwei Ligen auf denselben acht Seeds (die ersten acht der Ligen 3 bis 8), 20 Minuten, hart, Bob
gegen Bob, `SIM=1`, je 16 Bot-Spiele. **Vorher** ist `d85f36f` (alte Schätzweise; das gelernte
Zielmodell korrigiert die Schätzungen). **Nachher** ist `10837e6` (Stapel, Mob-Suche, Erfahrung bis
Faktor 8, Quellenwahl; das alte Zielmodell ist aus). Die Spiele 7 und 8 der Liga „nachher“ gingen
durch einen Neustart des Containers verloren und liefen auf demselben Stand neu.

| | vorher | nachher |
| --- | ---: | ---: |
| Tiles je Bot und Spiel | 2,12 | 2,94 |
| Tiles nach 5 / 10 / 20 min | 0,31 / 1,00 / 2,12 | 0,50 / 1,06 / 2,94 |
| Bots ohne Tile | 4 von 16 | 1 von 16 |
| erstes Tile (wer eins holte) | 355 s | 358 s |
| Tode je Bot und Spiel | 0,06 (1) | 0,31 (5) |
| Zeit auf Zielen je Liga | 3194 s | 2564 s |
| Ziele gestartet / geholt | 29 / 11 (38 %) | 35 / 15 (43 %) |
| Starts über dem 1,5-Fachen der Schätzung | 31 % | 40 % |
| Starts über dem 3-Fachen der Schätzung | 10 % | 11 % |

- **Tiles:** Gepaart je Seed ergibt das +0,81 Tiles je Bot und Spiel (Standardfehler 0,28). Kein Seed
  wurde schlechter, sechs wurden besser (Vorzeichentest p = 0,03).
- **Tode:** Alle fünf Tode der Liga „nachher“ fielen in Eröffnung und Kit, keiner in ein Feld, das
  die neuen Schätzungen oder Wege wählten. Zweimal ertrank Bob bei „up for air“ (beim Schild und
  bei der Eisenspitzhacke), einmal starb er an Zombies in einer unfertigen Box (beim Schild), einmal
  an einem Skelett (beim Herstellen der Eisenrüstung) und einmal an Spinnen nachts ohne Essen. Die
  Liga „vorher“ lag mit einem Tod unter dem langjährigen Mittel (etwa 0,25 je Bot und Spiel).
- **Schätzfehler je Start:** Er wurde nicht kleiner. Die Felder, die jetzt am meisten überziehen,
  schätzt nicht der Planer: Fall 20 Blocks (geschätzt 26 s, echt 184 s), 3 Sapling Types (49 s, 113
  s), Block with Shield (44 s, 78 s), Dripstone Caves (84 s, 379 s), Missing 10 Hearts (13 s, 30 s).
  Seit die Mob- und Handwerksfelder ehrlich geschätzt sind, nimmt Bob diese Felder früher. Bei den
  geholten Feldern überschätzt er jetzt eher (8 von 15 unter zwei Dritteln der Schätzung): Das
  Material liegt oft schon im Gepäck.
- **Neue Wege:** Keine der acht Partien nutzte eine Kiste oder eine Jagd am Wohnort als Weg zu einem
  Feld („loot the …“, „hunt a … at the …“). Die Bretter boten dafür wenig. Die Raubzüge unterwegs
  (Dorf, Schiffswrack) gab es vorher schon.

Dazu die Werkzeuge: `goals.py` (je Ziel; die letzte Zeile zählt auch die Starts ohne Feld),
`compare.py vorher.jsonl nachher.jsonl` (gepaart, je Bot; bei einer Spiegelliga das Mittel beider
Bots) und `deaths.py`.

### Offen

1. **Schätzungen außerhalb des Planers** sind jetzt die zu kleinen: Kunststücke (Fall 20 Blocks,
   Block with Shield, Missing 10 Hearts), Biome unter der Erde (Dripstone Caves) und 3 Sapling
   Types. Fall 20 Blocks scheitert außerdem in der Ausführung und wird wiederholt.
2. **Das Zielmodell** (`goal.json`) ruht, bis der Trainer eins für die Schätzweise 2 hat
   (mindestens 150 erledigte Ziele mit `"estimates": 2` im Journal).
3. **Die neuen Wege sind im Spiel noch unbewiesen.** Kein Brett der 16 Partien bot sie an. Eine Liga
   mit Brettern, die Namensschild, Gold, Obsidian, Hexe, Plünderer und Lohenrute enthalten, würde
   sie zeigen.
4. **Nicht geplündert:** die Lorenkisten der Minen und Kisten unter Wasser. Ein vergrabener Schatz
   braucht die Karte aus einem Schiffswrack; der Atlas kennt ihn nicht.
5. **Der Weg in den Nether:** Ohne einen Lavasee mit genug Quellen zählt die Schätzung Obsidian, mit
   der Diamantspitzhacke abgebaut (in einer Partie 1558 s). Ein Speedrunner gießt das Portal an
   jedem Lavasee oder ergänzt ein Ruinenportal.
6. **Mob-Suche am Tag:** Für Arten, die die Sonne verbrennt, gibt es am Tag nur das Warten auf die
   Nacht. Ein Spieler sucht sie in einer Höhle, wenn er einen Eingang kennt.

## Die größten Lücken

Aus der Tabelle unten, den Ligen 1 bis 8 und den beiden Ligen dieses Abschnitts:

| Was | Experte | Bob | Woran es liegt |
| --- | ---: | ---: | --- |
| Erstes Tile | unter 60 s | 355 s | Die Eröffnung: Essen, Schild, Holz und Eisen kommen vor jedem Feld (Ligen 7/8: 150–220 s Essen, 110–160 s Schild). |
| Schild (Kit) | 45 s | 179 s | Mittel auf „get shield“ in 136 Bot-Spielen. |
| Kupferbarren (Kit) | 45 s | 172 s | Mittel in 79 Bot-Spielen. |
| Skeleton | 45 s | 247 s | Ligen 7/8, alte Schätzung 27 s (ohne den Kampf). Jetzt plant Bob es nachts (siehe Probe). |
| Spider | 30 s | 174 s | Die Jagd nach dem schnellen Kletterer (6 Spiele). |
| Drowned | 120 s | 299 s | Unter Wasser (2 Spiele). |
| Firework Rocket | 200 s | 161 s darauf, 0 Felder in 21 Spielen | Das Schießpulver: ein Creeper nachts, sonst ein Wüstentempel. |
| Note Block | 200 s | 372 s (1 von 5 Starts) | Redstone tief unten. |
| Fall 20 Blocks | 60 s | 184 s (1 von 3) | Der Sturz scheitert und wird wiederholt; die Schätzung (26 s) ist zu klein. |
| Dripstone Caves | 240 s | 379 s, nicht geholt | Das Biom liegt unter der Erde. |
| 3 Sapling Types | 300 s | 113 s, 0 von 3 | Drei Baumarten in der Nähe sind selten; Setzlinge fallen zu 5 %. |

## Tabelle

Erzeugt mit:

```
python3 scripts/bot/analysis/reference_times.py --lang <en_us.json des Clients> \
    --leagues docs/bob-leagues/league*.jsonl -- <Spiel-Logs der Ligen>
```

Stand: die Spiel-Logs der beiden Ligen dieses Abschnitts (16 Spiele, 32 Bot-Spiele) und die
Ergebnisse der Ligen 1 bis 8. Die Spalten:

- **Bob (median, got it):** die Spielsekunden vom Start des Ziels bis zum Feld, über die Starts,
  die das Feld holten.
- **older leagues: s on it / games / claims:** siehe „Ältere Ligen“ oben.

| goal | title | expert | Bob (median, got it) | starts | got | Bob / expert | older leagues: s on it | games | claims |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| obtain_iron_ingot | Iron Ingot | 60 | - | - | - | - | 25 | 1 | 3 |
| obtain_gold_ingot | Gold Ingot | 150 | not got | 1 | 0 | - | 146 | 3 | 1 |
| obtain_copper_ingot | Copper Ingot | 45 | - | - | - | - | 172 | 79 | 0 |
| obtain_diamond | Diamond | 300 | - | - | - | - | - | - | - |
| obtain_emerald | Emerald | 120 | 31 | 2 | 1 | 0.3 | 118 | 5 | 4 |
| obtain_lapis_lazuli | Lapis Lazuli | 150 | - | - | - | - | - | - | - |
| obtain_redstone | Redstone Dust | 180 | - | - | - | - | - | - | 1 |
| obtain_coal_block | Block of Coal | 90 | - | - | - | - | - | - | 13 |
| obtain_raw_iron_block | Block of Raw Iron | 300 | - | - | - | - | - | - | - |
| obtain_raw_gold_block | Block of Raw Gold | 600 | - | - | - | - | - | - | - |
| obtain_obsidian | Obsidian | 240 | - | - | - | - | - | - | - |
| obtain_crying_obsidian | Crying Obsidian | 600 | - | - | - | - | - | - | - |
| obtain_amethyst_shard | Amethyst Shard | 180 | - | - | - | - | 118 | 2 | 0 |
| obtain_calcite | Calcite | 180 | not got | 2 | 0 | - | 219 | 5 | 0 |
| obtain_tuff | Tuff | 120 | not got | 1 | 0 | - | 114 | 6 | 2 |
| obtain_deepslate | Cobbled Deepslate | 90 | - | - | - | - | 44 | 3 | 6 |
| obtain_moss_block | Moss Block | 240 | - | - | - | - | - | - | - |
| obtain_dripstone_block | Dripstone Block | 240 | - | - | - | - | 98 | 1 | 2 |
| obtain_pointed_dripstone | Pointed Dripstone | 200 | not got | 1 | 0 | - | 67 | 2 | 3 |
| obtain_sculk | Sculk | 900 | - | - | - | - | - | - | - |
| obtain_snowball | Snowball | 60 | - | - | - | - | - | - | 1 |
| obtain_ice | Ice | 900 | - | - | - | - | - | - | - |
| obtain_packed_ice | Packed Ice | 900 | - | - | - | - | - | - | - |
| obtain_blue_ice | Blue Ice | 1200 | - | - | - | - | - | - | - |
| obtain_slime_ball | Slimeball | 300 | - | - | - | - | - | - | - |
| obtain_honey_bottle | Honey Bottle | 180 | - | - | - | - | - | - | - |
| obtain_honeycomb | Honeycomb | 120 | 5 | 1 | 1 | 0.0 | - | - | - |
| obtain_name_tag | Name Tag | 120 | 14 | 3 | 1 | 0.1 | 138 | 14 | 9 |
| obtain_saddle | Saddle | 150 | - | - | - | - | - | - | - |
| obtain_music_disc | Any Music Disc | 900 | - | - | - | - | - | - | - |
| obtain_ender_pearl | Ender Pearl | 240 | - | - | - | - | - | - | - |
| obtain_ink_sac | Ink Sac | 60 | - | - | - | - | - | - | - |
| obtain_glow_ink_sac | Glow Ink Sac | 240 | - | - | - | - | - | - | - |
| obtain_tropical_fish | Tropical Fish | 120 | - | - | - | - | - | - | - |
| obtain_pufferfish | Pufferfish | 120 | - | - | - | - | - | - | - |
| obtain_turtle_scute | Turtle Scute | 1500 | - | - | - | - | - | - | - |
| craft_lava_bucket | Lava Bucket | 60 | 15 | 3 | 1 | 0.2 | - | - | 1 |
| craft_water_bucket | Water Bucket | 45 | 3 | 2 | 1 | 0.1 | - | - | 2 |
| craft_golden_apple | Golden Apple | 300 | - | - | - | - | - | - | - |
| obtain_trident | Trident | 900 | - | - | - | - | - | - | - |
| obtain_crossbow | Crossbow | 150 | - | - | - | - | 49 | 1 | 0 |
| obtain_shield | Shield | 45 | - | - | - | - | 179 | 136 | 0 |
| obtain_arrow | Arrow | 60 | - | - | - | - | 76 | 12 | 7 |
| obtain_spectral_arrow | Spectral Arrow | 300 | - | - | - | - | - | - | - |
| obtain_tipped_arrow | Tipped Arrow | 400 | - | - | - | - | - | - | - |
| obtain_16_logs | 16 Logs | 60 | - | - | - | - | - | - | 1 |
| obtain_32_cobblestone | 32 Cobblestone | 45 | - | - | - | - | - | - | 11 |
| obtain_16_glass | 16 Glass | 120 | - | - | - | - | - | - | - |
| obtain_16_bricks | 16 Bricks | 500 | - | - | - | - | - | - | - |
| obtain_16_sandstone | 16 Sandstone | 60 | - | - | - | - | - | - | - |
| obtain_16_mud_bricks | 16 Mud Bricks | 360 | - | - | - | - | - | - | - |
| obtain_16_terracotta | 16 Terracotta | 180 | - | - | - | - | - | - | - |
| obtain_16_concrete | 16 White Concrete | 300 | - | - | - | - | - | - | - |
| obtain_16_prismarine | 16 Prismarine | 900 | - | - | - | - | - | - | - |
| obtain_all_log_types_basic | 4 Log Types | 420 | - | - | - | - | - | - | - |
| obtain_3_flower_types | 3 Flower Types | 60 | - | - | - | - | - | - | 13 |
| obtain_5_dye_colors | 5 Dye Colors | 240 | not got | 1 | 0 | - | - | - | 1 |
| obtain_3_sapling_types | 3 Sapling Types | 300 | not got | 3 | 0 | - | - | - | 3 |
| enter_nether | enter_nether | 300 | - | - | - | - | - | - | - |
| obtain_nether_gold_ore | Nether Gold Ore | 600 | - | - | - | - | - | - | - |
| obtain_netherrack | Netherrack | 5 | - | - | - | - | - | - | - |
| obtain_quartz | Nether Quartz | 30 | - | - | - | - | - | - | - |
| obtain_glowstone | Glowstone | 120 | - | - | - | - | - | - | - |
| obtain_blaze_rod | Blaze Rod | 300 | - | - | - | - | - | - | - |
| obtain_nether_wart | Nether Wart | 240 | - | - | - | - | - | - | - |
| obtain_ghast_tear | Ghast Tear | 300 | - | - | - | - | - | - | - |
| obtain_magma_cream | Magma Cream | 180 | - | - | - | - | - | - | - |
| obtain_ancient_debris | Ancient Debris | 900 | - | - | - | - | - | - | - |
| obtain_glowstone_dust | Glowstone Dust | 60 | - | - | - | - | - | - | - |
| obtain_shroomlight | Shroomlight | 60 | - | - | - | - | - | - | - |
| obtain_blackstone | Blackstone | 60 | - | - | - | - | - | - | - |
| obtain_basalt | Basalt | 60 | - | - | - | - | - | - | - |
| obtain_soul_sand | Soul Sand | 30 | - | - | - | - | - | - | - |
| obtain_soul_soil | Soul Soil | 30 | - | - | - | - | - | - | - |
| obtain_warped_fungus | Warped Fungus | 60 | - | - | - | - | - | - | - |
| obtain_crimson_fungus | Crimson Fungus | 45 | - | - | - | - | - | - | - |
| obtain_wither_skeleton_skull | Wither Skeleton Skull | 900 | - | - | - | - | - | - | - |
| obtain_piglin_barter_item | Any Barter Item | 60 | - | - | - | - | - | - | 11 |
| kill_blaze | Blaze | 240 | - | - | - | - | - | - | - |
| kill_wither_skeleton | Wither Skeleton | 300 | - | - | - | - | - | - | - |
| kill_ghast | Ghast | 180 | - | - | - | - | - | - | - |
| kill_magma_cube | Magma Cube | 90 | - | - | - | - | - | - | - |
| kill_piglin | Piglin | 60 | - | - | - | - | - | - | - |
| kill_hoglin | Hoglin | 120 | - | - | - | - | - | - | - |
| kill_zoglin | Zoglin | 900 | - | - | - | - | - | - | - |
| kill_strider | Strider | 90 | - | - | - | - | - | - | - |
| visit_nether_wastes | Nether Wastes | 10 | - | - | - | - | - | - | - |
| visit_crimson_forest | Crimson Forest | 120 | - | - | - | - | - | - | - |
| visit_warped_forest | Warped Forest | 180 | - | - | - | - | - | - | - |
| visit_soul_sand_valley | Soul Sand Valley | 180 | - | - | - | - | - | - | - |
| visit_basalt_deltas | Basalt Deltas | 240 | - | - | - | - | - | - | - |
| barter_with_piglin | Barter with Piglin | 60 | - | - | - | - | - | - | - |
| wear_gold_in_nether | Wear Gold in Nether | 30 | - | - | - | - | - | - | - |
| craft_furnace | Furnace | 20 | - | - | - | - | - | - | 9 |
| craft_blast_furnace | Blast Furnace | 300 | - | - | - | - | - | - | - |
| craft_smoker | Smoker | 30 | - | - | - | - | - | - | 1 |
| craft_campfire | Campfire | 40 | - | - | - | - | 15 | 1 | 6 |
| craft_shield | Shield | 45 | - | - | - | - | - | - | 16 |
| craft_bow | Bow | 120 | - | - | - | - | 166 | 2 | 1 |
| craft_crossbow | Crossbow | 150 | - | - | - | - | - | - | - |
| craft_fishing_rod | Fishing Rod | 90 | - | - | - | - | 154 | 1 | 0 |
| craft_shears | Shears | 45 | - | - | - | - | - | - | 1 |
| craft_bucket | Bucket | 60 | - | - | - | - | - | - | 1 |
| craft_compass | Compass | 240 | - | - | - | - | - | - | 1 |
| craft_clock | Clock | 300 | - | - | - | - | - | - | - |
| craft_map | Empty Map | 300 | - | - | - | - | - | - | - |
| craft_iron_pickaxe | Iron Pickaxe | 60 | - | - | - | - | - | - | 1 |
| craft_diamond_pickaxe | Diamond Pickaxe | 400 | - | - | - | - | - | - | - |
| craft_iron_armor_piece | Any Iron Armor | 90 | - | - | - | - | - | - | 4 |
| craft_full_iron_armor | Full Iron Armor | 400 | - | - | - | - | - | - | - |
| craft_golden_helmet | Golden Helmet | 150 | - | - | - | - | - | - | - |
| craft_anvil | Anvil | 600 | - | - | - | - | - | - | - |
| craft_enchanting_table | Enchanting Table | 500 | - | - | - | - | - | - | - |
| craft_bookshelf | Bookshelf | 240 | not got | 1 | 0 | - | 115 | 1 | 0 |
| craft_brewing_stand | Brewing Stand | 300 | - | - | - | - | - | - | - |
| craft_cauldron | Cauldron | 150 | - | - | - | - | - | - | 1 |
| craft_piston | Piston | 180 | - | - | - | - | 48 | 1 | 0 |
| craft_sticky_piston | Sticky Piston | 360 | - | - | - | - | - | - | - |
| craft_observer | Observer | 300 | - | - | - | - | - | - | - |
| craft_dispenser | Dispenser | 240 | - | - | - | - | - | - | - |
| craft_dropper | Dropper | 200 | - | - | - | - | 31 | 1 | 0 |
| craft_redstone_lamp | Redstone Lamp | 400 | - | - | - | - | - | - | - |
| craft_note_block | Note Block | 200 | 372 | 5 | 1 | 1.9 | 138 | 7 | 2 |
| craft_jukebox | Jukebox | 400 | - | - | - | - | - | - | - |
| craft_tnt | TNT | 300 | - | - | - | - | 78 | 1 | 0 |
| craft_fire_charge | Fire Charge | 400 | - | - | - | - | - | - | - |
| craft_firework_rocket | Firework Rocket | 200 | not got | 1 | 0 | - | 161 | 21 | 0 |
| craft_lead | Lead | 300 | - | - | - | - | 114 | 1 | 0 |
| craft_item_frame | Item Frame | 60 | 15 | 2 | 2 | 0.2 | 101 | 6 | 7 |
| craft_painting | Painting | 60 | 33 | 1 | 1 | 0.5 | 112 | 5 | 9 |
| craft_flower_pot | Flower Pot | 120 | 51 | 5 | 4 | 0.4 | 103 | 15 | 14 |
| craft_bricks | Bricks | 150 | 60 | 6 | 4 | 0.4 | 107 | 6 | 6 |
| craft_cake | Cake | 400 | - | - | - | - | - | - | - |
| craft_cookie | Cookie | 240 | - | - | - | - | 178 | 2 | 0 |
| craft_pumpkin_pie | Pumpkin Pie | 300 | - | - | - | - | - | - | - |
| craft_spyglass | Spyglass | 300 | - | - | - | - | - | - | - |
| craft_lightning_rod | Lightning Rod | 60 | - | - | - | - | - | - | 2 |
| craft_copper_bulb | Copper Bulb | 600 | - | - | - | - | - | - | - |
| craft_redstone_torch | Redstone Torch | 180 | - | - | - | - | 154 | 1 | 0 |
| craft_repeater | Redstone Repeater | 200 | - | - | - | - | 87 | 1 | 0 |
| craft_comparator | Redstone Comparator | 400 | - | - | - | - | - | - | - |
| craft_daylight_detector | Daylight Detector | 400 | - | - | - | - | - | - | - |
| craft_target_block | Target | 300 | - | - | - | - | - | - | - |
| craft_hopper | Hopper | 150 | - | - | - | - | - | - | - |
| kill_zombie | Zombie | 30 | - | - | - | - | 83 | 5 | 17 |
| kill_skeleton | Skeleton | 45 | - | - | - | - | 112 | 19 | 27 |
| kill_creeper | Creeper | 40 | not got | 1 | 0 | - | 82 | 8 | 7 |
| kill_spider | Spider | 30 | - | - | - | - | 174 | 6 | 13 |
| kill_enderman | Enderman | 120 | - | - | - | - | 32 | 2 | 1 |
| kill_witch | Witch | 300 | - | - | - | - | - | - | - |
| kill_slime | Slime | 300 | - | - | - | - | - | - | - |
| kill_phantom | Phantom | 900 | - | - | - | - | - | - | - |
| kill_pillager | Pillager | 150 | - | - | - | - | - | - | - |
| kill_guardian | Guardian | 300 | - | - | - | - | - | - | - |
| kill_elder_guardian | Elder Guardian | 1200 | - | - | - | - | - | - | - |
| kill_drowned | Drowned | 120 | - | - | - | - | 299 | 2 | 3 |
| kill_husk | Husk | 120 | not got | 1 | 0 | - | 54 | 1 | 0 |
| kill_stray | Stray | 180 | - | - | - | - | - | - | - |
| kill_cave_spider | Cave Spider | 300 | - | - | - | - | - | - | - |
| kill_silverfish | Silverfish | 600 | - | - | - | - | - | - | - |
| kill_ravager | Ravager | 1200 | - | - | - | - | - | - | - |
| kill_breeze | Breeze | 600 | - | - | - | - | - | - | - |
| visit_desert | Desert | 120 | - | - | - | - | - | - | 1 |
| visit_savanna | Savanna | 120 | - | - | - | - | - | - | - |
| visit_jungle | Jungle | 300 | - | - | - | - | - | - | - |
| visit_badlands | Badlands | 400 | - | - | - | - | - | - | - |
| visit_swamp | Swamp | 180 | - | - | - | - | - | - | 1 |
| visit_dappled_forest | Dappled Forest | 300 | - | - | - | - | - | - | - |
| visit_mangrove_swamp | Mangrove Swamp | 400 | - | - | - | - | - | - | - |
| visit_snowy_biome | Snowy Plains | 240 | - | - | - | - | - | - | 1 |
| visit_cherry_grove | Cherry Grove | 300 | - | - | - | - | - | - | - |
| visit_mushroom_fields | Mushroom Fields | 1200 | - | - | - | - | - | - | - |
| visit_dark_forest | Dark Forest | 180 | - | - | - | - | - | - | - |
| visit_bamboo_jungle | Bamboo Jungle | 400 | - | - | - | - | - | - | - |
| visit_ocean | Ocean | 60 | - | - | - | - | - | - | - |
| visit_warm_ocean | Warm Ocean | 300 | - | - | - | - | - | - | - |
| visit_deep_dark | Deep Dark | 600 | - | - | - | - | - | - | - |
| visit_dripstone_caves | Dripstone Caves | 240 | not got | 1 | 0 | - | - | - | 4 |
| visit_lush_caves | Lush Caves | 240 | - | - | - | - | - | - | - |
| visit_village | Village | 180 | - | - | - | - | - | - | 7 |
| visit_desert_pyramid | Desert Pyramid | 300 | - | - | - | - | - | - | - |
| visit_jungle_temple | Jungle Temple | 500 | - | - | - | - | - | - | - |
| visit_mineshaft | Mineshaft | 300 | - | - | - | - | - | - | - |
| visit_shipwreck | Shipwreck | 180 | - | - | - | - | - | - | - |
| visit_ocean_ruin | Ocean Ruin | 240 | - | - | - | - | - | - | 1 |
| visit_ruined_portal | Ruined Portal | 180 | - | - | - | - | - | - | - |
| visit_abandoned_camp | Abandoned Camp | 300 | - | - | - | - | - | - | 6 |
| visit_pillager_outpost | Pillager Outpost | 400 | - | - | - | - | - | - | - |
| visit_trial_chambers | Trial Chambers | 600 | - | - | - | - | - | - | - |
| visit_ancient_city | Ancient City | 1200 | - | - | - | - | - | - | - |
| visit_nether_fortress | Nether Fortress | 400 | - | - | - | - | - | - | - |
| visit_bastion_remnant | Bastion Remnant | 300 | - | - | - | - | - | - | - |
| visit_ocean_monument | Ocean Monument | 600 | - | - | - | - | - | - | - |
| visit_woodland_mansion | Woodland Mansion | 2400 | - | - | - | - | - | - | - |
| visit_stronghold | Stronghold | 1800 | - | - | - | - | - | - | - |
| visit_end_city | End City | 3600 | - | - | - | - | - | - | - |
| enter_end_gateway | Enter End Gateway | 3300 | - | - | - | - | - | - | - |
| use_totem | Use Totem | 2400 | - | - | - | - | - | - | - |
| get_levitation | Get Levitation | 3600 | - | - | - | - | - | - | - |
| milk_cow | Milk a Cow | 60 | - | - | - | - | - | - | - |
| shear_sheep | Shear a Sheep | 60 | - | - | - | - | - | - | - |
| breed_animals | Breed Animals | 45 | - | - | - | - | - | - | - |
| breed_cows | Breed Cows | 90 | - | - | - | - | - | - | - |
| breed_sheep | Breed Sheep | 90 | - | - | - | - | - | - | - |
| breed_pigs | Breed Pigs | 120 | - | - | - | - | - | - | - |
| breed_chickens | Breed Chickens | 45 | - | - | - | - | - | - | - |
| tame_wolf | Tame a Wolf | 120 | - | - | - | - | - | - | - |
| tame_cat | Tame a Cat | 180 | - | - | - | - | - | - | - |
| tame_horse | Tame a Horse | 120 | - | - | - | - | - | - | - |
| ride_horse | Ride a Horse | 60 | - | - | - | - | - | - | - |
| ride_pig | Ride a Pig | 200 | - | - | - | - | - | - | - |
| ride_strider | Ride a Strider | 300 | - | - | - | - | - | - | - |
| catch_fish | Catch a Fish | 90 | - | - | - | - | - | - | - |
| eat_apple | Apple | 60 | - | - | - | - | - | - | - |
| eat_bread | Bread | 60 | - | - | - | - | - | - | - |
| eat_cooked_beef | Steak | 60 | - | - | - | - | - | - | - |
| eat_cooked_porkchop | Cooked Porkchop | 60 | - | - | - | - | - | - | - |
| eat_cooked_chicken | Cooked Chicken | 60 | - | - | - | - | - | - | - |
| eat_cooked_mutton | Cooked Mutton | 60 | - | - | - | - | - | - | - |
| eat_cooked_cod | Cooked Cod | 90 | - | - | - | - | - | - | - |
| eat_cooked_salmon | Cooked Salmon | 90 | - | - | - | - | - | - | - |
| eat_cookie | Cookie | 240 | not got | 2 | 0 | - | - | - | - |
| eat_cake_slice | Cake | 420 | - | - | - | - | - | - | - |
| eat_pumpkin_pie | Pumpkin Pie | 300 | - | - | - | - | - | - | - |
| eat_golden_apple | Golden Apple | 300 | - | - | - | - | - | - | - |
| eat_glow_berries | Glow Berries | 240 | - | - | - | - | - | - | - |
| eat_suspicious_stew | Suspicious Stew | 120 | - | - | - | - | - | - | - |
| eat_beetroot_soup | Beetroot Soup | 180 | - | - | - | - | - | - | - |
| eat_rabbit_stew | Rabbit Stew | 400 | - | - | - | - | - | - | - |
| eat_dried_kelp | Dried Kelp | 90 | - | - | - | - | - | - | - |
| eat_honey_bottle | Honey Bottle | 180 | - | - | - | - | - | - | - |
| sleep_in_bed | Sleep in a Bed | 90 | - | - | - | - | - | - | 1 |
| set_spawn | Set Spawn Point | 60 | - | - | - | - | - | - | - |
| use_lectern | Use a Lectern | 120 | - | - | - | - | - | - | - |
| use_grindstone | Use a Grindstone | 60 | - | - | - | - | - | - | 1 |
| use_stonecutter | Use a Stonecutter | 60 | - | - | - | - | - | - | - |
| use_cartography_table | Use a Cartography Table | 120 | 34 | 2 | 2 | 0.3 | - | - | 3 |
| use_smithing_table | Use a Smithing Table | 60 | - | - | - | - | - | - | - |
| use_loom | Use a Loom | 60 | - | - | - | - | - | - | - |
| light_campfire | Light a Campfire | 60 | 4 | 1 | 1 | 0.1 | - | - | - |
| activate_pressure_plate | Trigger Pressure Plate | 20 | - | - | - | - | - | - | 1 |
| wear_full_leather | Full Leather Armor | 300 | - | - | - | - | - | - | - |
| wear_full_iron | Full Iron Armor | 400 | - | - | - | - | - | - | - |
| wear_full_gold | Full Gold Armor | 500 | - | - | - | - | - | - | - |
| wear_diamond_piece | Any Diamond Armor | 400 | - | - | - | - | - | - | - |
| block_damage_with_shield | Block with Shield | 60 | 79 | 3 | 1 | 1.3 | - | - | 19 |
| obtain_firework_crossbow | Firework Crossbow | 300 | - | - | - | - | - | - | - |
| trade_with_villager | Trade with a Villager | 120 | 36 | 2 | 1 | 0.3 | - | - | 3 |
| obtain_emerald_by_trade | Trade for Emerald | 120 | - | - | - | - | - | - | - |
| trade_with_librarian | Trade with Librarian | 180 | - | - | - | - | - | - | - |
| trade_with_armorer | Trade with Armorer | 180 | - | - | - | - | - | - | - |
| trade_with_farmer | Trade with Farmer | 120 | - | - | - | - | - | - | - |
| trade_with_cleric | Trade with Cleric | 180 | - | - | - | - | - | - | - |
| trade_with_toolsmith | Trade with Toolsmith | 180 | - | - | - | - | - | - | - |
| trade_with_fletcher | Trade with Fletcher | 120 | - | - | - | - | - | - | - |
| buy_bread | Buy Bread | 150 | - | - | - | - | - | - | - |
| buy_arrows | Buy Arrows | 150 | - | - | - | - | - | - | - |
| buy_lapis | Buy Lapis | 300 | - | - | - | - | - | - | - |
| buy_bell | Bell | 180 | - | - | - | - | - | - | - |
| ring_bell | Ring a Bell | 150 | - | - | - | - | - | - | - |
| sleep_in_village_bed | Sleep in Village Bed | 180 | 50 | 2 | 2 | 0.3 | - | - | 3 |
| advancement_stone_age | Stone Age | 15 | - | - | - | - | - | - | 13 |
| advancement_acquire_hardware | Acquire Hardware | 60 | - | - | - | - | - | - | 1 |
| advancement_suit_up | Suit Up | 90 | - | - | - | - | - | - | - |
| advancement_diamonds | Diamonds! | 300 | - | - | - | - | - | - | - |
| advancement_enchanter | Enchanter | 600 | - | - | - | - | - | - | - |
| advancement_we_need_to_go_deeper | We Need to Go Deeper | 300 | not got | 1 | 0 | - | - | - | - |
| advancement_return_to_sender | Return to Sender | 600 | - | - | - | - | - | - | - |
| advancement_into_fire | Into Fire | 300 | - | - | - | - | - | - | - |
| advancement_local_brewery | Local Brewery | 400 | - | - | - | - | - | - | - |
| advancement_monster_hunter | Monster Hunter | 20 | - | - | - | - | - | - | 6 |
| advancement_take_aim | Take Aim | 120 | - | - | - | - | - | - | - |
| advancement_sniper_duel | Sniper Duel | 400 | - | - | - | - | - | - | - |
| advancement_best_friends_forever | Best Friends Forever | 120 | - | - | - | - | - | - | - |
| advancement_fishy_business | Fishy Business | 90 | - | - | - | - | - | - | - |
| advancement_tactical_fishing | Tactical Fishing | 120 | - | - | - | - | - | - | 1 |
| brew_any_potion | Brew Any Potion | 400 | - | - | - | - | - | - | - |
| brew_speed_potion | Brew Swiftness | 500 | - | - | - | - | - | - | - |
| brew_strength_potion | Brew Strength | 500 | - | - | - | - | - | - | - |
| brew_fire_resistance | Brew Fire Resistance | 500 | - | - | - | - | - | - | - |
| brew_night_vision | Brew Night Vision | 600 | - | - | - | - | - | - | - |
| enchant_item | Enchant an Item | 450 | - | - | - | - | - | - | - |
| enchant_sword | Enchant a Sword | 450 | - | - | - | - | - | - | - |
| enchant_pickaxe | Enchant a Pickaxe | 450 | - | - | - | - | - | - | - |
| drink_potion | drink_potion | 120 | - | - | - | - | - | - | - |
| splash_potion | Throw Splash Potion | 500 | - | - | - | - | - | - | - |
| place_tnt | Place TNT | 300 | - | - | - | - | - | - | - |
| ignite_tnt | Ignite TNT | 320 | - | - | - | - | - | - | - |
| shoot_crossbow | Shoot Crossbow | 180 | - | - | - | - | - | - | - |
| hit_target_block | Hit Target Block | 400 | - | - | - | - | - | - | - |
| throw_ender_pearl | Throw Ender Pearl | 240 | - | - | - | - | - | - | - |
| throw_trident | Throw Trident | 900 | - | - | - | - | - | - | - |
| survive_explosion | Survive Explosion | 300 | - | - | - | - | - | - | 1 |
| take_fall_damage | Take Fall Damage | 10 | - | - | - | - | - | - | - |
| fall_20_blocks_and_survive | Fall 20 Blocks | 60 | 19 | 5 | 2 | 0.3 | - | - | 1 |
| burn_and_survive | Burn and Survive | 60 | - | - | - | - | - | - | - |
| freeze_in_powder_snow | Freeze in Snow | 240 | - | - | - | - | - | - | 5 |
| get_shot_by_skeleton | Get Shot by Skeleton | 60 | - | - | - | - | - | - | - |
| get_poisoned | Get Poisoned | 120 | - | - | - | - | - | - | 2 |
| get_withered | Get Wither | 600 | - | - | - | - | - | - | - |
| have_10_hearts_missing | Missing 10 Hearts | 60 | not got | 2 | 0 | - | - | - | 5 |
| reach_y_minus_50 | Reach Y -50 | 120 | - | - | - | - | - | - | - |
| reach_build_limit | Reach Build Limit | 300 | - | - | - | - | - | - | - |
| stand_on_bedrock | Stand on Bedrock | 240 | - | - | - | - | - | - | - |
