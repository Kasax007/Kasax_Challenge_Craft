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
| Wahrnehmung | `BotWorld`, Scan im `ObtainPlanner` | Sieht nur, was ein Spieler sehen kann: Blöcke, die an Luft oder Wasser grenzen, und Mobs in der Nähe. **Kein X-Ray**: Vergrabene Erze findet er nur durch Graben. |
| Wissen | `BotKnowledge` | Wird zur Laufzeit **aus dem Spiel selbst** gelesen: alle Crafting- und Schmelzrezepte (auch aus Mods und Datapacks), welche Blöcke welche Items droppen (durch Würfeln der echten Loot-Tabellen), welche Werkzeuge welche Blöcke abbauen. Von Hand gepflegt sind nur: Seltenheit von Blöcken in der Oberwelt, beste Abbauhöhe je Erz und Mob-Drops. |
| Planung | `ObtainPlanner`, `ObtainTask` | Zerlegt „habe N × X“ rekursiv (siehe unten). |
| Aufgaben | `task/*` | Abbauen (mit Suche und Strip-Mining), Craften, Schmelzen, Jagen, Essen, Ausrüsten, Arbeitsblock benutzen, Abfolgen |
| Gehirn | `BotBrain`, `lockout/LockoutBrain` | Entscheidet, was als Nächstes zu tun ist. |

## Wie der Bot plant

Beispiel: „Steinspitzhacke“. Der Planer spielt den Weg auf einer **Kopie des Inventars** durch:

1. Steinspitzhacke braucht 3 Bruchstein, 2 Stöcke und eine Werkbank.
2. Bruchstein braucht eine Spitzhacke, also zuerst eine Holzspitzhacke. Das Werkzeug, das gerade
   hergestellt wird, kommt dafür nicht in Frage.
3. Holzspitzhacke, Stöcke und Werkbank brauchen Bretter, also Stämme. Welche Holzart, entscheidet
   er danach, was in der Nähe steht.
4. Fürs Schmelzen: ein Ofen und Brennstoff. Kohle nimmt er, wenn sie nahe ist, sonst Holz. Das
   Eingangsmaterial und das Ergebnis werden nie verheizt, Werkzeuge ebenfalls nicht.

Dann gibt er **nur den ersten Schritt** aus, aber so bemessen, dass er für den ganzen Plan reicht:
drei Stämme in einem Gang statt dreimal einen. Nach jedem Schritt plant er neu, und zwar aus dem
echten Inventar. So nutzt er einen glücklichen Drop sofort und reagiert, wenn etwas fehlt.

**Welcher Weg?** Jede Möglichkeit bekommt geschätzte Kosten in Sekunden:
- Was er im Inventar hat: kostenlos.
- Was er sieht: der Weg dorthin (Entfernung, Höhenunterschied).
- Sonst die Seltenheit des Blocks.
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

2. Er geht auf die billigste Kachel los.
3. Holt jemand anderes die Kachel, bricht er sofort ab und wählt neu.
4. Scheitert ein Ziel zweimal, lässt er es 3 Minuten ruhen.
5. Ziele, die er noch nicht versteht, überlässt er dem Gegner. Dazu gehören Nether, End,
   Strukturen, Advancements, Brauen, Verzaubern und Handel.

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
/challengecraft_bot status|inv <name>          was er gerade tut / was er dabei hat
```

Solange er für OPs `verbose` ist, schreibt er seine Gedanken in den Chat, z. B.
„goal: Iron Ingot (~95 s)“, „mine raw_iron (3) at y 14“, „craft stone_pickaxe x2“.

## Stand und nächste Schritte

Im Test funktioniert Folgendes:
- Von null bis zur Steinspitzhacke in etwa 30 s.
- Holzkohle und Eisen schmelzen.
- Kühe jagen und Leder einsammeln.
- Werkzeug schonen.
- Schacht und Stollen bis auf Höhe −18 mit Nebenfunden wie Redstone und Kupfer.
- Lockout-Kacheln selbstständig holen.

Offen:
- **Zurück an die Oberfläche** nach dem Graben: eigener Aufstieg statt normaler Wegsuche.
- **Aus dem Wasser klettern:** Über einen Rand, der einen Block über dem Wasserspiegel liegt,
  kommt er noch nicht zuverlässig.
- **Zusammen mit „The House Always Wins“:** Stirbt der Bot, sitzt er am Blackjack-Tisch fest,
  weil er Casino-Spiele noch nicht kann. Das gehört zum geplanten Casino-Benchmark.
- **Mehr Zieltypen:** Kuh melken, Schaf scheren, Druckplatte, Bett und Schlafen, Dorfhandel,
  Nether (Portal bauen), Verzaubern, Brauen.
- **Gegner beobachten:** Kacheln bevorzugen, die der Gegner gleich hat, also blocken.
- **Kampf:** zurückweichen bei wenig Leben, Schild, Bogen.
- **The House Always Wins als Benchmark:** dasselbe Gehirnprinzip mit Casino-Zielen (Chips
  verdienen, Gebühr zahlen, spielen).
