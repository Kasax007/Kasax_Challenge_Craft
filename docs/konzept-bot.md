# Bot – ein eingebauter Minecraft-Mitspieler

Der Bot ist Teil der Mod, ohne externe Abhängigkeit (kein Baritone o. ä.). Er ist ein
echter Spieler auf dem Server: Er hat einen Spielerkörper mit Skin und Namen, erscheint in der
Tab-Liste, stirbt, respawnt, hat Hunger und Inventar und bekommt Statistiken und Advancements.
Alles, was er tut, läuft über dieselben Spielmechaniken wie bei einem Menschen:

- Laufen mit Tasten-Eingaben und Vanilla-Physik
- Abbauen mit Abbauzeit und Werkzeug
- Craften über die echten Rezepte
- Schmelzen im echten Ofen
- Kämpfen mit Angriffs-Cooldown

Einen Sonderweg gibt es nicht. Deshalb zählt er für Challenges wie Lockout Bingo genauso wie ein
Mensch.

## Schichten

| Schicht | Klassen | Aufgabe |
|---|---|---|
| Körper | `BotPlayer`, `BotConnection`, `BotManager` | Spieler ohne Client. Eingaben (vor, seitwärts, springen, schleichen, sprinten) treiben die Vanilla-Bewegung. Respawn läuft über `PlayerList.respawn`. |
| Motorik | `BotPathfinder`, `BotNavigator`, `BotActions`, `BotTools` | A*-Wegsuche (laufen, springen, fallen ≤ 3, schwimmen, durchgraben, hochbauen). Abbauen und Platzieren wie ein Spieler. Werkzeugwahl nach dem Prinzip „das billigste, das reicht“: Stein wird mit der Steinspitzhacke abgebaut, die Eisenspitzhacke bleibt für Gold. |
| Wahrnehmung | `BotSenses`, `BotWorld`, Scan im `ObtainPlanner` | Sieht nur, was ein Spieler sehen kann: Blöcke, die an Luft oder Wasser grenzen, Mobs in der Nähe, Biome in Sichtweite (etwa 96 Blöcke), Strukturen, die aus dem Boden ragen oder direkt vor ihm liegen, und ungeöffnete Loot-Kisten darin. **Kein X-Ray**: Vergrabene Erze und Kisten findet er nur durch Graben. |
| Wissen | `BotKnowledge` | Wird zur Laufzeit **aus dem Spiel selbst** gelesen: alle Crafting- und Schmelzrezepte (auch aus Mods und Datapacks), welche Blöcke welche Items droppen (durch Würfeln der echten Loot-Tabellen), welche Werkzeuge welche Blöcke abbauen. Von Hand gepflegt sind nur: Seltenheit von Blöcken in der Oberwelt, beste Abbauhöhe je Erz und Mob-Drops. |
| Planung | `ObtainPlanner`, `ObtainTask` | Zerlegt „habe N × X“ rekursiv (siehe unten). |
| Aufgaben | `task/*` | Abbauen (mit Suche und Strip-Mining), Craften, Schmelzen, Jagen, Essen, Ausrüsten, Arbeitsblock benutzen, Kiste looten, Biom oder Struktur aufsuchen, Item an Mob benutzen (melken, scheren), Eimer füllen, Obsidian herstellen, Portal bauen und durchgehen, an die Oberfläche steigen, Abfolgen |
| Gehirn | `BotBrain`, `lockout/LockoutBrain` | Entscheidet, was als Nächstes zu tun ist. |

## Wie der Bot plant

Beispiel: „Steinspitzhacke“. Der Planer spielt den Weg auf einer **Kopie des Inventars** durch:

1. Steinspitzhacke braucht 3 Bruchstein, 2 Stöcke und eine Werkbank.
2. Bruchstein braucht eine Spitzhacke, also zuerst eine Holzspitzhacke. Das Werkzeug, das gerade
   hergestellt wird, kommt dafür nicht in Frage.
3. Holzspitzhacke, Stöcke und Werkbank brauchen Bretter, also Stämme. Welche Holzart, entscheidet
   er danach, was in der Nähe steht.
4. Was kein Rezept liefert, kennt er trotzdem: Einen Eimer füllt man an einer sichtbaren Quelle.
   Obsidian entsteht aus einem Lavapool mit Wassereimer und Diamantspitzhacke: Wasser auf das Ufer
   gießen, damit es über die Lava läuft, das Wasser wieder aufnehmen und das Obsidian abbauen.
5. Fürs Schmelzen: ein Ofen und Brennstoff. Kohle nimmt er, wenn sie nahe ist, sonst Holz. Das
   Eingangsmaterial und das Ergebnis werden nie verheizt, Werkzeuge ebenfalls nicht.

Dann gibt er **nur den ersten Schritt** aus, aber so bemessen, dass er für den ganzen Plan reicht:
drei Stämme in einem Gang statt dreimal einen. Nach jedem Schritt plant er neu, und zwar aus dem
echten Inventar. So nutzt er einen glücklichen Drop sofort und reagiert, wenn etwas fehlt.

**Welcher Weg?** Jede Möglichkeit bekommt geschätzte Kosten in Sekunden:
- Was er im Inventar hat: kostenlos.
- Was er sieht: der Weg dorthin (Entfernung, Höhenunterschied).
- Liegt in Sichtweite ein Biom, für das der Block typisch ist: der Weg dorthin. Beispiele: Kakao im
  Dschungel, Ton im Sumpf, Kaktus in der Wüste. Die Suche führt dann auch zuerst dorthin.
- Sonst die Seltenheit des Blocks, und zwar für die Dimension, in der er ist. Im Nether sind
  Netherrack und Quarz alltäglich und Oberweltblöcke unerreichbar.
- Monster am Tag: vierfacher Aufwand.
- Rezepte summieren ihre Zutaten.

Er nimmt den billigsten Weg.

**Fehlschläge merkt er sich:**
- Ein gescheiterter Weg wird 2 Minuten gemieden.
- Blöcke und Mobs, die er bei einer vollen Suche nicht gefunden hat, gelten 10 Minuten als nicht
  vorhanden.

Alle Schätzungen gehen dann um diese Lücke herum. Findet er zum Beispiel keine Maiglöckchen,
wählt er einen anderen Farbstoff.

## Suchen wie ein Spieler

- **Oberfläche** (Bäume, Sand, Blumen, Tiere): Er läuft in Etappen von etwa 32–40 Blöcken in eine
  Richtung und dreht ab, wenn es nicht weitergeht.
- **Erze**: Er gräbt auf die typische Höhe hinunter.

  | Erz | Höhe |
  |---|---|
  | Kohle, Kupfer | 44 |
  | Eisen | 14 |
  | Lapis, Tiefenschiefer | −2 |
  | Gold | −18 |
  | Redstone, Diamant | −53 |

  - **Abstieg:** als senkrechter Schacht, wenn der Boden darunter fest ist und keine Flüssigkeit
    angrenzt. Sonst als Treppe.
  - **Stollen:** 1×2 auf der Zielhöhe. Er baut alles ab, was der Stollen freilegt.
  - **Vor jedem Schritt** prüft er Boden, Wasser und Lava. Bei Gefahr dreht er ab.
  - **Werkzeug:** Vor langem Graben nimmt er 1–2 Steinspitzhacken als Ersatz mit.
- **Reflexe**, unabhängig vom Plan:
  - Bei Angriff durch ein Monster schlägt er zurück.
  - Bei Hunger isst er.
  - Mit dem Kopf unter Wasser hält er Springen gedrückt und taucht auf.
  - Liegt ein Ufer einen vollen Block über dem Wasser, gräbt er nach ein paar vergeblichen
    Sprüngen eine Stufe hinein, wie Spieler es auch tun.
- **Zurück nach oben:** Aus einer Mine steigt er senkrecht auf, indem er springt und einen Block
  unter sich setzt. Ist über ihm Flüssigkeit oder hat er keine Blöcke, gräbt er eine Treppe. Jede
  Suche an der Oberfläche beginnt mit diesem Aufstieg.
  - Wenn das Inventar voll wird, wirft er Schutt weg und behält einen Stapel Baumaterial.

## Lockout Bingo

Die Regeln, wie der Bot sie versteht:
- Jede Kachel gehört dem, der ihr Ziel zuerst schafft.
- Alle Kacheln zählen gleich.
- Wer mehr Kacheln hat, gewinnt.

Daraus folgt die Strategie: **immer die Kachel nehmen, die von hier aus am schnellsten geht.**
Jede Kachel, die er holt, fehlt dem Gegner.

Ablauf:
1. Wenn er frei ist, übersetzt er jede offene Kachel mit `LockoutGoals` in seine Begriffe und
   schätzt den Aufwand:

   | Zieltyp | Was er tut |
   |---|---|
   | Item, Item-Menge | besorgen |
   | Craften | wirklich craften, denn nur Besitz zählt nicht |
   | Kill | jagen |
   | Essen | besorgen und essen, wenn er hungrig ist |
   | Rüstung tragen | die billigsten Teile besorgen und anziehen |
   | N verschiedene Items | die N billigsten besorgen |
   | Arbeitsblöcke | Steinsäge, Schleifstein, Webstuhl, Schmiedetisch oder Kartentisch besorgen, aufstellen und benutzen |
   | Kuh melken, Schaf scheren | Eimer bzw. Schere besorgen, Tier suchen, benutzen |
   | Biom besuchen | hingehen, wenn in Sicht; sonst erkunden, länger je seltener |
   | Struktur besuchen | Dorf, Schiffswrack, Ruinenportal, Tempel, Außenposten, Lager, Ozeanruine: hingehen, wenn gesehen |
   | Nether betreten | Portal gießen (Lavapool in Sicht, 2 Eimer, Feuerzeug, 10 Blöcke) oder 10 Obsidian abbauen und bauen – das Günstigere; im Nether dann Nether-Ziele, danach zurück |
   | Tiefe Y ≤ −50, einfache Advancements | hinuntergraben; Steinzeit, Eisen, Rüstung, Diamanten |

2. **Routen statt Einzelziele:** Von den schnellsten fünf Kacheln nimmt er die, nach der die
   nächste am schnellsten geht. Er rechnet mit dem, was er danach in der Hand hätte: Werkzeuge,
   Ofen, Reste. Steht z. B. Eisenbarren neben Eimer und Schere auf dem Brett, holt er zuerst das
   Eisen.
3. **Gelegenheiten unterwegs:** Alle paar Sekunden schaut er, ob etwas nebenher geht:
   - Eine ungeöffnete Loot-Kiste in der Nähe (Schiffswrack mit Eisen, Ruinenportal mit Feuerzeug,
     Obsidian und Gold) plündert er.
   - Eine andere Kachel, die gerade nur Sekunden kostet, erledigt er zwischendurch: die Kuh, die
     vorbeiläuft, das Zuckerrohr am Ufer, das Biom, durch das er gerade läuft.

   Danach macht er mit seinem Ziel weiter.
4. Holt jemand anderes die Kachel, bricht er sofort ab und wählt neu.
5. Scheitert ein Ziel zweimal, lässt er es 3 Minuten ruhen.
6. Ziele, die er noch nicht versteht, überlässt er dem Gegner. Dazu gehören End, Brauen,
   Verzaubern, Handel, Zähmen, Züchten und Reiten.

**Schwierigkeitsgrade:** Sie ändern, wie er spielt, nicht was er weiß.

| Grad | Pause zwischen Zielen | Sprinten | Harte Ziele | Wahl |
|---|---|---|---|---|
| leicht | 15 s | nein | übersprungen | 35 % Fehlgriffe |
| normal | 4 s | nein | ja | optimal |
| schwer | keine | ja | ja | optimal |

**Lobby:** Läuft Lockout mit Lobby, tritt der Bot einem freien Team bei und meldet sich bereit.

## Befehle (nur OP)

```
/challengecraft_bot spawn <name>              Bot betritt den Server an deiner Position
/challengecraft_bot remove <name>
/challengecraft_bot lockout <name> [easy|normal|hard]
      spielt Lockout: tritt dem laufenden Spiel bzw. der Lobby bei, sonst startet es
      „du (Rot) gegen Bot (Blau)“ auf einem neuen Brett
/challengecraft_bot get <name> <item> <anzahl>  besorgt ein beliebiges Item (Planer-Test)
/challengecraft_bot mine|kill|goto|follow|eat|stop <name> …
/challengecraft_bot biome|structure <name> <id>  Biom / Struktur aufsuchen
/challengecraft_bot milk|portal|surface <name>   Kuh melken / Portal bauen / aufsteigen
/challengecraft_bot cast <name>                 Portal am nächsten Lavapool gießen
/challengecraft_bot nether <name>               den Weg in den Nether gehen, den Lockout wählen würde
/challengecraft_bot senses <name>               was er gerade wahrnimmt
/challengecraft_bot status|inv <name>          was er gerade tut / was er dabei hat
```

Solange er für OPs `verbose` ist, schreibt er seine Gedanken in den Chat, z. B.
„goal: Iron Ingot (~95 s)“, „mine raw_iron (3) at y 14“, „craft stone_pickaxe x2“.

## Portal gießen (Speedrunner-Methode)

Ohne Diamantspitzhacke: Ein Lavaquellblock, der Wasser neben oder über sich hat, wird sofort zu
Obsidian. Das gilt auch für fließendes Wasser. Lava, die man in fließendes Wasser gießt, ersetzt
es. Solange die Wasserquelle bleibt, fließt das Wasser weiter. Man braucht also nur so viele
Wasserquellen, dass jede Rahmenstelle einmal nass ist. Die Form aus Hilfsblöcken lenkt das Wasser
dorthin und gibt gleichzeitig die Flächen, gegen die man Lava gießt. Bob macht alles vom Boden aus,
mit einem Lava- und einem Wassereimer:

```
  y4   W2 L   L  W3     1. Eckblöcke c0, c3; Wasser W1 auf c0 läuft über x1 und x2 → Lava x1, x2
  y3   L  M   M  L      2. Form M: zwei Säulen innen, bis oben (am liebsten Erde, schnell wieder weg)
  y2   L  M   M  L      3. Wasser W2 oben links fällt die linke Säule hinunter → Lava hinein, jeweils
  y1   L  M   M  L         gegen den Formblock daneben; den Formblock oben links abbauen, W2 läuft
  y0   c0 L   L  c3        hinein → Lava dort; W2 zurück in den Eimer
       x0 x1  x2 x3     4. rechts genauso mit W3; 5. Form abbauen, anzünden, hineingehen
```

Drei Mal Wasser für zehn Obsidian. Zwischen den Lava-Güssen holt er jeweils einen Eimer aus dem
Pool, der 9–13 Blöcke entfernt ist, damit das Wasser ihn nicht erreicht. Beim Gießen zielt er wie
ein Spieler auf eine Blockfläche, die er wirklich sieht und erreicht. Seinen Standplatz wählt er
danach und nicht im Rahmen, wo das Wasser fällt. Setzt eine Lava nicht (das Wasser war noch nicht
da), nimmt er sie wieder auf.

## Testen

- **Szenarien ohne Grafik:** `./gradlew runGameTest` baut kleine Arenen (40 × 32 × 40) und schickt
  Bob hindurch, alle parallel, in wenigen Sekunden Echtzeit. Nur einige:
  `./gradlew runGameTest "-PbotTest=challengecraft-gametest:*lake*"`. Jedes Ergebnis steht mit
  Ticks als `[BOTTEST]` im Log. Code: `src/gametest`.
- **Ganze Runden:** `scripts/bot/bench.sh <seed> [sekunden] [schwierigkeit]` startet einen Server
  ohne Grafik, lässt Bob allein Lockout spielen (festes Brett je Seed) und spult mit
  `/tick sprint` vor: 10 Minuten Spiel in etwa 1,5 Minuten. Bericht `[BOTBENCH]`: Meilensteine,
  Kacheln, Zeit je Aufgabe, Leerlauf, Fehlschläge, Tode.
- **Viele Welten:** `scripts/bot/bench-many.sh 600 hard 12345 777 …` gibt eine Tabelle.
- **Einzelfragen:** `scripts/bot/server.sh <seed>` startet nur den Server (rcon mit
  `scripts/bot/rcon.py`); dann z. B. `challengecraft_bot estimate Bob iron_ingot` oder
  `challengecraft_bot coverage Bob` (welche Ziele Bob noch gar nicht kann).

## Stand

Der Plan mit Analyse und den Schritten 1–7 steht in `docs/bob-plan.md`. Umgesetzt:

1. **Testfundament:** Szenario-Tests, Benchmark-Runner, Entscheidungsprotokoll im Log.
2. **Bewegung:** Schwimmen, Ausstieg aus Wasser (auch über hohe Ufer, mit Block oder Stufe),
   Lücken springen, Brücken, Pfeiler (auch mehrere hintereinander), aus Höhlen und Schächten per
   Pfadsuche, Zeitlimit pro Schritt mit Sperrliste, Fallen (Druckplatten, Stolperdrähte) meiden.
3. **Weltwissen:** `BotMemory` liest die Chunks um Bob (Blöcke, Tiere), Strukturen in Sichtweite
   ohne Sichtlinie; Planer und Suche nutzen das.
4. **Eröffnung und Beharrlichkeit:** Steinspitzhacke und -axt zuerst, Werkbank mitnehmen, Essen
   erst bei Hunger, Aufpasser für jede Aufgabe (dreifache Schätzung), nie untätig, Respawn.
5. **Spielplan:** `LockoutStrategist` – Investitionen (Eisen-Kit, Diamanten), Nether-Ausflug,
   Blocken des Gegners, Nebenbei-Liste (Feuerstein, Eisenerz, Zuckerrohr, TNT), Neuplanung.
6. **Strategiebuch:** Portal gießen als Standard, Schiffswrack/Dorf für Eisen, gezieltes Plündern
   (nur lohnende Strukturen, höchstens 3 Kisten), Wüstentempel-Falle, Schatzkarte.
7. **Fortgeschritten:** Handel, Angeln, Verzaubern, Züchten, Zähmen, Reiten, Bett/Spawn, Stürze
   (auch 20 Blöcke), Bedrock, TNT, Glocke, Lesepult, Enderperle, Rückzug bei wenig Leben.

Zahlen: 28 Szenarien grün; Ziele ohne Weg 111 → etwa 75 von 312.

## Offen

- Kosten tiefer Rohstoffe (Redstone, Tropfstein) und die Essenssuche sind noch Zeitfresser.
- Nether-Navigation (Festung, Bastion-Loot-Wege), Brauen, End, Boot-MLG.
- Ruinenportale reparieren statt gießen.
- Portal gießen wackelt in etwa jedem dritten Lauf (Test erlaubt 3 Versuche).
- „The House Always Wins“ als Benchmark (Casino-Ziele).
