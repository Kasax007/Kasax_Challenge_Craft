# Shorts V2 — Plan, Werkzeuge, Stand (Übergabe-Dokument)

> **Für jeden Agenten, der hier weiterarbeitet:**
>
> - Erst dieses Dokument ganz lesen, dann im Abschnitt **Stand** weitermachen.
> - Nach jedem erledigten Schritt den Stand hier aktualisieren: Haken setzen, eine Zeile ins
>   **Protokoll**.
> - So kann ein Nachfolger nach einem Abbruch (Token-Limit, Absturz) nahtlos übernehmen.
> - Dateien: `/home/user/bob-dev/docs/marketing/shorts-v2-plan.md`.

## Auftrag (vom Nutzer, sinngemäß)

Die beiden fertigen Shorts sind gut, aber für die Zielgruppe (junge Leute, sehr kurze
Aufmerksamkeitsspanne) nicht schnell genug. Gewünscht:

1. **V2 des Challenge-Shorts** („I coded viral challenges into Minecraft“): deutlich schneller
   geschnitten, ein durchgehender Flow, kein Moment zum Wegwischen.
2. **V2 des Casino-Shorts** („I built a casino in Minecraft“): ebenso schneller. Am Ende zusätzlich
   die Challenge-Auswahl (`ui/ui_select`) und den Level-Baum (`ui/ui_journey`) anhängen, mit einer
   Zeile in der Art „Play this and 49 other challenges in Challenge Craft – free on CurseForge“.
3. Danach **zwei weitere, neue Shorts** im selben schnellen Stil (Themen siehe unten, eigene Wahl
   mit Begründung erlaubt).

Alle Shorts:

- englisch, 1080×1920, 30 fps;
- eingebrannte Untertitel und Soundeffekte;
- KI-Voice-Over (Chatterbox) mit passender Energie;
- dazu je eine Version ohne Untertitel, die SRT-Datei und die reine Sprachspur.

## Was „schneller“ konkret heißt (Vorgaben)

- **Hook in den ersten 0,5 s:**
  - das spektakulärste Bild zuerst;
  - die Titelzeile sofort voll lesbar, nicht erst einblenden;
  - keine ruhige Einleitung.
- **Schnitte alle 0,4–1,2 s.**
  - Kein Clip länger als ca. 1,5 s; Ausnahme: echte Payoff-Momente bis 2 s (Jackpot, Totem,
    zehn Zombies).
  - Lieber zwei kurze Clips aus derselben Aufnahme als einen langen.
- **Speed-Ramps:** `speed=1.5–3` für Anlauf und Laufen, normal oder Slow-Mo nur im Payoff-Moment.
- **Bewegung in jedem Clip:**
  - `zoom=(a,b)` mit sichtbarem Zoom-in oder Zoom-out;
  - Punch-Zoom auf Schnitten (z. B. `zoom=(1.25,1.05)`);
  - `shake` bei Treffern und Explosionen, `flash` bei Highlights.
- **Untertitel:**
  - 1–4 Wörter pro Einblendung;
  - jede Einblendung so kurz wie der Satzteil;
  - Wörter wechseln im Takt der Sprache (Wort-für-Wort- bzw. Phrasen-Captions statt ganzer Sätze);
  - Akzentwort farbig (`*WORT*`).
- **Voice-Over:**
  - schneller sprechen: kürzere Sätze, `cfg_weight` 0,25–0,3, `exaggeration` 0,6–0,9;
  - Pausen zwischen den Zeilen ≤ 0,2 s;
  - Zeilen dürfen über Schnitte laufen;
  - Sätze kürzen statt vorlesen, z. B. „Red light? Move… you're dead.“
- **Länge:** Ziel 25–40 s (V1 war 52 s). Nicht alles aus V1 muss rein; die stärksten Beats
  bleiben.
- **Ende:** CTA-Karte max. 2–3 s, dabei die Frage („Which one next?“ / „Would you gamble?“) als
  letzte VO-Zeile. Bei TikTok hilft ein Loop-Ende, das in den Anfang übergeht. Optional.
- **Audio:**
  - SFX dichter: Whoosh auf jedem Schnitt (gibt es schon), Pop pro Caption, Impact auf Payoffs;
  - Ducking unter der Stimme (in `mix.py`);
  - keine Musik (die legt der Nutzer in der App darunter).

## Werkzeuge (alles vorhanden)

Arbeitsverzeichnis für Schnitt und Material:
`SP=/tmp/claude-0/-home-user-Kasax-Challenge-Craft/2061deb3-7247-5b5c-aa62-6c43681c8da7/scratchpad`

Kopien der Skripte liegen auch im Repo unter `/home/user/bob-dev/scripts/film/`.

### Material (Frames als `f00000.jpg …`, 30 fps)

`$SP/film/<ordner>/<shot>/`:

- `casino/`:
  - Slots: `casino_slot_epic`, `casino_slot_epic_show` (Feuerwerk in Frames 0–30),
    `casino_slot_legendary` (Riesengewinn bei ca. 9,6–11 s), `casino_slot_freespins_intro`,
    `casino_slot_spins`;
  - `casino_counter` (Items auf den Tresen ab 0,3 s, Chips fliegen ca. 3,5–4,7 s);
  - `casino_fee` (Banner ab 0,7 s, „House collects“ ab 3,3 s; oben im Bild, Zoom 1,3 auf y≈0,12);
  - `casino_bankrupt` (Banner „THE HOUSE WINS“ oben, ab ca. 2,3 s ohne den abgeschnittenen Titel);
  - `casino_square` (Platz im Sonnenuntergang);
  - `casino_plinko`;
  - Crash: `casino_crash_launch`, `casino_crash_climb` (10 fps aufgenommen, `speed=4`),
    `casino_crash_cashout`;
  - Roulette: `casino_roulette_bets`, `casino_roulette_spin` (Kugel fällt ca. 10,9 s auf 17);
  - `casino_wave` (Mobs spawnen ab ca. 5,5 s);
  - `casino_blackjack` (Karten ab 0,7 s, Dealer kauft sich ab ca. 3,3 s über → „YOU LIVE“,
    Totem bei ca. 9,3 s);
  - `casino_revived` (schwach, nicht nutzen).
- `chal/`:
  - `red_light` (grün→gelb→ROT ca. 5,2 s, „You Died“ ab 5,8 s);
  - `dice_throw` (Wurf, Würfel zeigt 4);
  - `cushion` (Spieler sitzt, legt Kissen);
  - `chunk_blocks` (Luftflug über die Zufallswelt, ab 0,8 s);
  - `floor_lava` (Spieler brennt auf Steinziegeln);
  - `size_matters` (Riesen- und Mini-Creeper, 0–3 s gut);
  - `upside_down` (Erze fliegen nach oben, 1–3,6 s);
  - `double_trouble` (Zombie erscheint ca. 0,6 s, wird bei ca. 1,8 s zu zehn, Skelette und Creeper
    später);
  - `skyblock` (Insel-Orbit);
  - `force_item` (Bob mit Item über dem Kopf);
  - `lockout_bob_run`, `lockout_board`.
- `ui/`:
  - `ui_select` (Challenge-Liste, statisch; gezoomt 1,7–1,9 lesbar);
  - `ui_journey` (Level-Baum scrollt);
  - `ui_summary` (Run-Zusammenfassung „Ender Dragon defeated 42:17 NEW RECORD“);
  - `ui_title`.
- `cards/`: `end_casino`, `end_challenges` (Abspannkarten mit Logo).

### Schnitt-Skripte (`$SP/edit/`)

- `cut.py`:
  - `Clip(shot, start, length, speed=1, zoom=(1,1), focus=(0.5,0.5), shake=0, flash=False, hold_last=0, reverse=False)`;
  - `Caption(t0, t1, text, style='cap'|'hook'|'tag', y=None, accent=YELLOW, size=None)`:
    - `*Wort*` färbt;
    - mehrere Wörter und Satzzeichen nach dem Stern gehen: `*GAME OVER*?`;
  - `render(clips, captions, out, audio=wav, crf=18)`;
  - `contact_sheet(video, png, every=1.0, cols=10, thumb=150)` zum Prüfen.
- `sfx.py`:
  - `Mix(seconds).add(t, snd, gain)`, `.write(path, seconds)`;
  - Klänge: `mc('mob/zombie/say1')` (Pfad unter `minecraft/sounds/…`), `casino('win_epic')`
    (Dateien in `src/main/resources/assets/challengecraft/sounds/casino`), `whoosh()`, `riser()`,
    `impact()`, `pop()`;
  - Vor dem Rendern Namen prüfen: Skript dazu in Abschnitt **Fallstricke**.
- Vorlagen V1:
  - `casino_short.py` (Liste `add(Clip, *cues)` + Captions);
  - `challenge_short.py` (BEATS-Liste mit `cap`/`sub`/`hook`/`end`).
  - V2 als **neue Dateien** anlegen (`casino_short_v2.py`, …), V1 nicht überschreiben.
- `sheet.py SHOT step` – Kontaktbogen eines Shots (`python3 edit/sheet.py chal/red_light 15`).
- Ausgaben nach `$SP/out/`; Versand-Kopien unter 30 MB nach `$SP/out/send/`:
  `ffmpeg -i X.mp4 -c:v libx264 -preset slow -crf 25 -c:a copy send/X.mp4`.

### Voice-Over (`$SP/vo/`)

`make.py`, `regen.py`, `check.py`, `mix.py` (Kopien in `scripts/film/vo/`).

Die TTS-Umgebung wurde aus Platzgründen gelöscht. Neu aufsetzen:

```sh
cd $SP && python3 -m venv tts && . tts/bin/activate && pip install -q --no-cache-dir --upgrade pip
pip install -q --no-cache-dir torch==2.6.0 torchaudio==2.6.0 --index-url https://download.pytorch.org/whl/cpu
pip install -q --no-cache-dir chatterbox-tts faster-whisper
export HF_HOME=$SP/hf   # Modelle ~3 GB; immer 2>/dev/null (Fortschrittsbalken fluten die Ausgabe)
```

- `make.py`:
  - enthält pro Short eine Liste `(start_s, text, exaggeration, cfg_weight)` und die Gesamtlänge;
  - für V2 neue Schlüssel ergänzen, z. B. `'casino_v2'`;
  - schreibt `vo/<name>/NN.wav` + `lines.json`;
  - ca. 35 s Rechenzeit pro Zeile;
  - immer mit `nice -n 15` starten.
- `check.py vo/<name>`:
  - Whisper-Transkription jeder Zeile gegen den Text;
  - `BAD` heißt neu einsprechen mit `regen.py <name> <i> "text" ex cfg seed`.
  - Es wurde auch der ganze Mix geprüft; Wörter, die unter SFX untergehen, neu einsprechen bzw.
    stärker ducken.
- `mix.py <name>`:
  - legt die Zeilen auf die Zeitachse, rafft zu lange Zeilen bis 1,3× (atempo), duckt die SFX;
  - liest `out/<name>_sfx.wav` und `out/<name>_final.mp4` / `_clean.mp4`;
  - schreibt `out/send/<name>_{final,clean}_vo.mp4`.
  - Für V2 heißen die Ausgaben passend (`casino_v2`), also im Schnitt-Skript dieselben Namen
    verwenden.

### Neue Szenen filmen (nur falls nötig)

Szenen sind Java-Code in `/home/user/bob-dev/src/film/java/net/kasax/challengecraft/film/`:

- `CasinoScenes.java`, `ChallengeScenes.java`, `UiScenes.java`;
- Hilfen in `FilmDirector.java` und `Cam.java`.

Filmen:

```sh
sh $SP/film.sh <scene> $SP/film/<ordner>              # eine Szene (startet Xvfb selbst)
sh $SP/film_seq.sh $SP/film/<ordner> sceneA sceneB    # mehrere, packt danach zu JPEG
```

Dauer ca. 3–10 min pro Szene. Wichtige Eigenheiten:

- **Kamera:** Freie Kamera `d.cam.cutTo/moveTo(eye, at)` (Rüstungsständer); `cam.playerLook` und
  `playerPose` für die Spieler-Sicht. Bei Schnitten setzt `playerPose` die Position auch serverseitig.
- **Spieler:**
  - Bei freier Kamera ist der Spieler sichtbar (Mixin).
  - Laufen auf gedrückten Tasten nur, wenn `Cam.freeWalk = true` (das setzt `keys(true, …)`).
  - Sonst schwebt der Spieler, wo er gesetzt wurde. Mit `freeWalk` gilt die Physik: Er kann
    fallen und sterben.
- **Uhren:**
  - UI-Animationen (Banner, Blackjack) laufen auf Spielzeit (Mixin `UiClockMixin`).
  - Der Server läuft im Gleichschritt (`FilmClock`).
- **Fertige Szenen-Namen:** `casino_*` siehe oben; `red_light`, `dice`, `cushion`, `chunk_blocks`,
  `floor_lava`, `size_matters`, `upside_down`, `chunk_hunt`, `double_trouble`, `skyblock`,
  `force_item`, `lockout_bob`, `level_border` (kaputt, fällt aus der Welt), `ui_select`,
  `ui_journey`, `ui_summary`, `ui_title`.
- Weitere Challenges stehen im Code unter
  `/home/user/bob-dev/src/main/java/net/kasax/challengecraft/challenges/Chal_*.java`.

## Regeln und Fallstricke

- **Ressourcen:**
  - Höchstens 2 JVMs gleichzeitig (Filmen = 1 JVM).
  - TTS nicht gleichzeitig mit 2 Film-JVMs (15 GB RAM).
  - Platten-Platz knapp (ca. 7–8 GB frei); nichts Großes liegen lassen.
- **Prozesse beenden:**
  - Nie `pkill -f <muster>`, wenn das Muster im eigenen Befehl steht: Das tötet die eigene Shell.
  - Stattdessen `pgrep`/`kill <pid>` oder das `[x]`-Muster.
- **Lange Aufträge:**
  - im Hintergrund starten (`setsid nohup … &`);
  - mit `until <bedingung>; do sleep 20; done` warten (Timeout ≤ 600000 ms);
  - kein nacktes `sleep` > 60.
- **Die Umgebung beendet Prozesse ca. 5 min nach Ende der aktiven Arbeit.** Nichts
  Unbeaufsichtigtes über Turn-Grenzen hinweg planen.
- **Sound-Namen vorher prüfen:**
  ```python
  import json, glob
  idx = json.load(open(glob.glob('/root/.gradle/caches/fabric-loom/assets/indexes/26.3*.json')[0]))['objects']
  'minecraft/sounds/<name>.ogg' in idx
  ```
  Notenblock-Klänge heißen `note/bell`, `note/bass`; Wolle `dig/cloth1`.
- **Ergebnisse prüfen:**
  - Nach jedem Rendern `contact_sheet` ansehen (Read auf das PNG) und gegen das Skript prüfen.
  - Den Ton per Whisper prüfen (`check.py` / Transkription des Mix).
- **Git:**
  - Keine Commits auf andere Branches.
  - Neue Skripte nach `/home/user/bob-dev/scripts/film/` kopieren.
  - Commits nur im Worktree `/home/user/bob-dev` (Branch `bob-dev-tmp`).
  - Am Ende jeder Commit-Nachricht:
    ```
    Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
    Claude-Session: https://claude.ai/code/session_01Y6AzZnzN36nEmDk7YFdyve
    ```
  - Nicht pushen; das macht der Haupt-Agent.
  - Keine Modell-Namen in Dateien.
- **Nicht anfassen:**
  - `src/main` (Bob-Code, daran arbeitet parallel der Haupt-Agent).
  - Bob-Benchmarks in `/home/user/bob-bench*`.

## Vorschläge für die zwei neuen Shorts (frei änderbar, Begründung ins Protokoll)

Format, das gut läuft: **„Minecraft, but …“** mit einer einzigen Challenge, schnell, mit Payoff.

1. **„Minecraft, but every chunk is ONE random block“.**
   - Material: `chunk_blocks`, dazu neue Aufnahmen nötig (Spieler läuft über Chunk-Grenzen von Gold
     zu TNT zu Diamant, gräbt in einen Diamant-Chunk).
   - Hook: Flug über die bunte Welt.
2. **„I made an AI that plays Minecraft Lockout against you (Bob)“.**
   - Material: `lockout_bob_run`, `lockout_board`, `force_item`; neue Aufnahmen von Bob, der
     Bäume fällt, Werkzeuge baut, Feld claimt.
   - Bob als „Early Development Stage“ ehrlich darstellen.
3. Alternative: **„Minecraft, but the House always wins“** als Kurzversion nur Blackjack-ums-Leben
   (Tod → Blackjack → Totem), extrem kurz (15–20 s).

## Stand

- [x] 0. TTS-Umgebung wieder aufsetzen (siehe oben), Speicher prüfen
- [x] 1. Challenge-Short V2: Schnittskript `challenge_short_v2.py`, VO-Liste `challenges_v2`
  - [x] Draft gerendert + Kontaktbogen geprüft
  - [x] VO erzeugt, `check.py` ok, gemischt, Mix per Whisper geprüft
  - [x] Final + clean + SRT + Sprachspur in `out/` und `out/send/`
- [x] 2. Casino-Short V2 (mit Auswahl und Level-Baum am Ende + CTA „Play this and 49 other
  challenges …“): `casino_short_v2.py`, VO `casino_v2`
  - [x] Draft + Kontaktbogen
  - [x] VO + Prüfung + Mix
  - [x] Final + clean + SRT + Sprachspur
- [x] 3. Neuer Short A (Thema: Minecraft, but dying is a card game; blackjack_life, 15.5 s)
- [x] 4. Neuer Short B (Thema: every chunk one random block; random_chunks, 16 s)
- [x] 5. `docs/marketing/shorts.md` um V2 und die neuen Shorts ergänzen (Zeiten, Text, Hashtags),
  Skripte nach `scripts/film/` kopieren, committen (ohne Push)
- [ ] 6. Abschlussbericht an den Haupt-Agenten: Dateipfade in `out/send/`, Längen, offene Punkte

## Protokoll

(Jede Zeile: Uhrzeit (UTC), was getan, Ergebnis, nächster Schritt.)
- 08:24 UTC: TTS-Umgebung neu aufgesetzt (tts/ im SP, HF_HOME=SP/hf). Neu: edit/v2lib.py (gemeinsamer V2-Builder: Beats mit Clips, Wort-Captions, VO-Plan -> vo/<name>_plan.json), vo/make.py liest <name>_plan.json, vo/run_v2.sh <name>. edit/challenge_short_v2.py geschrieben (38 Clips, 31 s). Läuft: VO-Erzeugung challenges_v2 + Draft. Nächste: Kontaktbogen prüfen, check.py, mix.
- 08:39 UTC: Challenge V2 fertig: out/send/challenges_v2_{final,clean}_vo.mp4 (32.2 s, 38 Clips, ~15 MB), .srt, _voice.m4a. VO per Whisper ok, Kontaktbogen geprüft. Neu: vo/sync.py NAME (nach Schnitt-Änderung vor mix.py: Startzeiten aus plan.json in lines.json), vo/transcribe.py, fin.sh NAME. casino_short_v2.py (43 Clips, 35.6 s) Draft ok, VO casino_v2 läuft. Nächste: VO check, mix Casino.
- 08:54 UTC: Casino V2 fertig: out/send/casino_v2_{final,clean}_vo.mp4 (36.7 s, 43 Clips), srt, voice.m4a. Whisper-Mix ok (VO: 'dealer' statt 'croupier', weil TTS das Wort verhunzt). Ende: ui_select, ui_journey, 'Play this, and forty-nine other challenges, in Challenge Craft!' + Karte 'Free on CurseForge. Would you gamble?'. Neue Shorts: A = 'Minecraft, but you gamble for your LIFE' (Blackjack, vorhandenes Material, 15-20 s), B = 'Minecraft, but every chunk is ONE random block' (neue Szene chunk_walk filmen, plus vorhandener Luftflug). Nächste: Szene chunk_walk in ChallengeScenes.java ergänzen und filmen.
- 09:21 UTC: Short A fertig: out/send/blackjack_life_{final,clean}_vo.mp4 (15.5 s, 19 Clips), srt, voice. Whisper ok. Neue Szene chunk_walk in ChallengeScenes.java (Kamera-Fahrt über 6 Chunk-Böden gold/redstone/diamond/emerald/lapis/purpur; nutzbar sind Frames 0-125 = 4,2 s, danach läuft der Spieler vom Streifen). Nächste: Short B 'random_chunks' aus chunk_walk 0-4 s + chunk_blocks Luftflug.
- 09:28 UTC: Short B fertig: out/send/random_chunks_{final,clean}_vo.mp4 (16.1 s), srt, voice. shorts.md um V2-Abschnitt ergänzt, Skripte nach scripts/film kopiert. Nächste: committen, Abschlussbericht.
