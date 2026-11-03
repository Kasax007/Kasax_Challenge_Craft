# Bug- und Feature-Check Challenge Craft (November 2026)

Branch `bugcheck-2026-11` (von `26.3-lockout-bot`, Stand 1.1.3). `mod_version` bleibt 1.1.3, nichts
gepusht. Changelog für die nächste Version liegt bereit: `docs/curseforge/changelog-1.1.4.md`.

## Geprüft

- **Build** (`build`, `compileFilmJava`, `compileGametestJava`): ok, keine neuen Warnungen.
- **Game-Tests** (Dedicated-Server-Umgebung): vorher 194/195 grün; `brawl_enderman_slope` fiel einmal
  unter Volllast (bekannt wackelig, im nächsten Lauf grün). Nach den Fixes: alle grün, siehe unten.
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
     alten Code laufen lassen: alle acht Prüfungen schlugen fehl. Mit Fix grün.
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
   gilt, im Jar geprüft); Film-Harness `film/UiScenes.java:116` klickte mit Taste 0 (in 26.3 keine
   Taste) – jetzt `MOUSE_BUTTON_LEFT`.

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
- **Bob (Early Development Stage):** keine klaren Fehler gefunden; `brawl_enderman_slope` bleibt
  unter Last wackelig (1 von 2 Läufen rot).

## Im Spiel geprüft vs. nur gelesen

- Im Spiel (Headless-Server bzw. Client): Punkte 1–3 per Game-Test, voller Testlauf, Client-Start
  mit Film-Szenen, Dedicated-Server-Start mit ID-Audit (Ergebnis unten).
- Nur gelesen: Punkte 4–7 und alle offenen Punkte.
