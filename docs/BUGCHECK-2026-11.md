# Bug- und Feature-Check Challenge Craft (November 2026)

Branch `bugcheck-2026-11` (von `26.3-lockout-bot`, Stand 1.1.3). `mod_version` bleibt 1.1.3, nichts
gepusht. Changelog für die nächste Version liegt bereit: `docs/curseforge/changelog-1.1.4.md`.

## Geprüft

- **Build** (`build`, `compileFilmJava`, `compileGametestJava`): ok, keine neuen Warnungen.
- **Game-Tests** (Dedicated-Server-Umgebung, Maschine stark ausgelastet): vorher 194/195 grün
  (`brawl_enderman_slope` rot, bekannt wackelig). Nach den Fixes 197/197 grün; letzter voller Lauf
  196/198 – rot nur zwei Bob-Kampfszenen (`sniper_duel`: alle Pfeile verschossen, Skelett lebt;
  `brawl_enderman`), die mit den Änderungen nichts zu tun haben; einzeln nachgeprüft beide grün.
- **Client** (Film-Harness unter Xvfb): Hub, Challenge-Auswahl mit echten Karten-Klicks; Client-Log
  ohne Fehler der Mod.
- **Dedicated Server** (`runServer`, `CHALLENGECRAFT_VERIFY_IDS=1`): startet ohne Client-Klassen
  (Done in 3,4 s); das Registry-Audit fand eine ungültige ID (Punkt 9), nach dem Fix „OK — every id
  resolves“ (1495 IDs).
- **Ressourcen per Skript:** jede Sprach-Schlüssel-Familie (51 Challenges, Perks, Lockout-Ziele,
  Casino) in `en_us` und `de_de`, Platzhalter gleich; jedes Modell, jede Textur, jede Blockstate- und
  Item-Definition vorhanden; Loot-Tabellen und Rezepte laden ohne Fehler (Server-Log sauber).
- **Gelesen:** alle Server-Empfänger für Client-Pakete (Challenges, XP, Trivia, Infinite Chest,
  Lockout, Force Item Battle, Casino), Infinite Chest komplett, Casino-Eingaben, Void Trail,
  Red Light, Dice, Mixin-Aufteilung Client/Server, Maustasten in allen Screens (26.3 zählt SDL:
  links = 1). Bob nur stichprobenartig.

## Behoben

1. **Infinite Chest: Fern-Zugriff, Dupe und Verlust** – `network/PacketHandler.java:265`,
   `block/InfiniteChestScreenHandler.java:70`.
   - Ein Klick-Paket galt für *jede* geladene Infinite Chest, ohne offenes Menü und aus jeder
     Entfernung (fremde Truhen leerbar; eine ferne Position lud deren Chunk).
   - Das Menü blieb für immer gültig (`stillValid` = true).
   - Entnehmen und Ablegen per Klick riefen nie `setChanged()`: Lag sonst nichts im Chunk an, war
     die Änderung nach dem nächsten Laden weg – entnommene Items kamen zurück (Dupe), abgelegte
     verschwanden.
   - Linksklick nahm immer 64 (auch 64 Schwerter, 64 Enderperlen).
   - Fix: nur die offene Truhe in Reichweite, Menü schließt wie bei einer Kiste, jede Änderung
     markiert die Truhe, Linksklick = ein Stapel des Items.
   - **Im Spiel geprüft:** neuer Game-Test `challenge_fix_tests_infinite_chest_clicks`. Gegen den
     alten Code laufen lassen: 8 der 10 Prüfungen schlugen fehl (genau die oben genannten Fehler;
     Öffnen und Ablegen selbst gingen). Mit Fix grün.
2. **Crafter umging Level- und Kauf-Sperre** – neu `mixin/CrafterGateMixin.java`. Infinite Chest
   (Level 20 + Perk) und Casino-Geräte (einmal beim Croupier kaufen) sind nur am Ergebnis-Slot
   gesperrt; der Crafter klickt nie und baute sie für jeden. Jetzt baut er sie nicht (Hand-Crafting
   unverändert). **Im Spiel geprüft:** `challenge_fix_tests_crafter_makes_no_gated_items`.
3. **Dice: Sync löschte das Lauf-Budget** – `challenges/Chal_46_Dice.java:47`. Im Einzelspieler
   teilt der Client-Sync die Challenge-Flags mit dem Server und schaltet alle kurz aus und an;
   `setActive(false)` löschte dabei alle Budgets (LAN-Spieler tritt bei, Re-Sync nach Beitritt,
   Einstellung geändert). Jetzt wie bei Red Light erst nach 1 s „aus“ bzw. beim Server-Stopp.
   **Im Spiel geprüft:** `challenge_fix_tests_dice_budget_survives_sync`.
4. **Roulette: Chip-Überlauf und Chunk-Laden** – `casino/RouletteGame.java:85` und `:94`. Eine
   absurde Chip-Zahl (nur mit verändertem Client) lief beim Einsatz ins Negative und *zahlte* aus;
   der Block wurde vor der Reichweite geprüft. Nur gelesen.
5. **Kasse: unlesbare Item-ID** – `casino/CasinoEconomy.java:322`. Fremder Text ging an
   `Identifier.parse` und warf aus dem Server-Task (Log-Spam). Jetzt nur bekannte IDs. Nur gelesen.
6. **ChallengePacket:** der Decoder reservierte Listen in der Größe, die ein Client schickt (jeder
   Client, die OP-Prüfung kommt später) – `network/ChallengePacket.java:74`. Nur gelesen.
7. **Kleinkram:** Name des Würfel-Entitys fehlte (`entity.challengecraft.dice`, beide Sprachen);
   tote Loot-Tabelle im alten Ordner `loot_tables/` entfernt (die generierte unter `loot_table/`
   gilt, im Jar geprüft).
8. **Challenge-Auswahl: Abschnittszähler blieb stehen** – `client/screen/ChallengeBrowser.java:266`,
   `:316`. Die Zahl „aktiv/gesamt“ neben jedem Abschnitt wurde nur beim Layout berechnet; nach einem
   Klick auf eine Karte stand dort weiter „0/7“, obwohl drei Challenges an waren (im Film-Bild
   gesehen). Jetzt beim Zeichnen gezählt. Gilt für Welt-Erstellung und `/challenges`.
9. **Block-Survey: ID aus 1.21** – `data/challengecraft/block_survey.json`: `minecraft:chain` heißt in
   26.3 `minecraft:iron_chain` (steht schon im Datensatz). Progressive Block Drops hat den Eintrag
   still übersprungen; das eigene ID-Audit meldete ihn. Entfernt.
10. **Film-Harness** – `film/UiScenes.java:116`, `:141`: die Szene `ui_select` klickte mit Taste 0
    (in 26.3 keine Taste) und fand seit dem Umbau auf das Scroll-Panel keine Karte mehr („0 challenge
    cards on screen“, also auch kein Klick). Jetzt `MOUSE_BUTTON_LEFT` und Suche im Panel: 59 Karten,
    die Klicks schalten sichtbar um.

## Offen (nicht geändert)

- **Infinite Chest, Transfer-API** – `block/InfiniteChestBlockEntity.java:99`, `:117`. Trichter können
  nichts herausziehen (die Views geben bei `extract` immer 0 zurück). `extract` zählt mehrere
  Entnahmen in einer Transaktion nicht gegen und bucht über Close-Callbacks statt
  `SnapshotParticipant` – mit Rohr-Mods ein Dupe-Risiko. Erst entscheiden, ob Herausziehen gewollt ist.
- **Infinite Chest beim Abbauen** – `block/InfiniteChestBlock.java:168`: der ganze Inhalt fällt als
  Items heraus (bei Millionen Items Tausende Entities). Idee: Inhalt im Item behalten wie bei der
  Shulker-Kiste.
- **Infinite Chest, Update-Tag** – `block/InfiniteChestBlockEntity.java:78`: jeder Client, der den
  Chunk lädt, bekommt den ganzen Inhalt, obwohl das GUI eigene Pakete nutzt.
- **XP vom Client** – `network/PacketHandler.java:197`: der Server übernimmt höhere Lifetime-XP vom
  Client (gewollt, damit der Fortschritt mitwandert); ein veränderter Client kann sich Level und
  Sterne-Auren geben. Challenges/Perks wählen bleibt OP-pflichtig.
- **Lockout-Lobby** – `challenges/Chal_40_LockoutBingo.java:261`: jeder Spieler kann Bob hinzufügen
  oder entfernen (unter Freunden ok, auf offenen Servern evtl. OP-pflichtig machen).
- **Trivia** – `network/TriviaQuestionPacket.java:12`: die richtige Antwort geht an den Client.
- **Roulette** – `casino/client/RouletteScreen.java:75`: `ROULETTE_LEAVE` wird gesendet, aber der
  Server behandelt es nicht (`casino/CasinoGames.java:283`). Harmlos.
- **Bob (Early Development Stage):** nur stichprobenartig gelesen, keine klaren Fehler gefunden.
  Unter Volllast wackeln Kampfszenen: `brawl_enderman_slope` (Lauf 1), `sniper_duel` und
  `brawl_enderman` (Lauf 3) – jeweils im nächsten bzw. Einzel-Lauf grün. Bei `sniper_duel` gingen
  im roten Lauf alle 48 Pfeile daneben; vielleicht lohnt ein Blick aufs Zielen unter Lag.

## Im Spiel geprüft vs. nur gelesen

- Im Spiel: Punkte 1–3 per Game-Test (Headless-Server), Punkte 8 und 10 im Client (Film-Bilder
  angesehen), Punkt 9 per Server-Start mit ID-Audit; dazu volle Testläufe, Client- und Serverstart.
- Nur gelesen: Punkte 4–7 und alle offenen Punkte. Dass der Dice-Test am alten Code scheitert, folgt
  aus dem Code (dort leerte `setActive(false)` die Budgets); nicht extra laufen lassen.
