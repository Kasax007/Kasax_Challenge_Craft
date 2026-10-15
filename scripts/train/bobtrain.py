#!/usr/bin/env python3
"""Bobs Training auf diesem Rechner - laeuft, bis es gestoppt wird.

    python scripts/train/bobtrain.py run          das Training (Standard): Server starten, so viele
                                                  wie der Rechner gerade stabil traegt, Kaempfe in
                                                  der Arena, Liga-Spiele Bob gegen Bob, Modelle
                                                  trainieren, pruefen, einsetzen - bis STOP
    python scripts/train/bobtrain.py calibrate    den Rechner vermessen: wie viele Server und Arenen
                                                  gleichzeitig, wie schnell (calibration.md)
    python scripts/train/bobtrain.py status       der Stand (aus status.json)
    python scripts/train/bobtrain.py stop         das laufende Training beenden (sauber)
    python scripts/train/bobtrain.py train        einmal trainieren, mit dem, was im Journal steht

Unter Windows: scripts\\train\\train.bat (startet alles), stop.bat (stoppt), status.bat.
Alles, was das Training ablegt, liegt unter runs/train/ (siehe bobcore.py).
"""
import argparse
import collections
import itertools
import json
import os
import random
import shutil
import signal
import sys
import threading
import time

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)

import bobcore  # noqa: E402
import bobleague  # noqa: E402
from bobcore import log, read_json, write_json  # noqa: E402

CONFIG = {
    "arena_bots": 6,            # bots per arena server
    "arena_mb": 2048,           # memory per arena server
    "league_mb": 3072,          # memory per league server (a whole Lockout game)
    "league_view": 12,          # view distance of league games (chunks)
    "league_seconds": 1800,     # length of a league game (game time)
    "league_share": 0.34,       # share of the servers playing league games
    "arena_restart_min": 30,    # a fresh world for an arena server this often
    "train_every_min": 10,      # train the models this often
    "eval_pairs": 120,          # fights per side before a new cover model is judged
    "cpu_low": 72,              # add a server below this CPU load (percent, last minute)
    "cpu_high": 92,             # take one away above it
    "min_free_mb": 1500,        # memory to leave free
    "max_instances": 0,         # 0: from the calibration (or worked out)
    "journal_max_gb": 3.0,      # oldest journal files go above this
    "deploy": True,             # put promoted models into run/bob-models (Bob in IntelliJ uses them)
    "torch": "auto",            # auto | on | off: train on PyTorch (GPU) if installed
    "cover_min": 300,           # fights with a cover choice before the cover model is trained
    "cover_min_explore": 200,   # of them picked at random in the arena
    "goal_min_done": 150,       # tiles done before the goal model may be used
    "search_threads": 4,        # path-search threads per server
}


def load_config(paths):
    cfg = dict(CONFIG)
    user = read_json(os.path.join(paths.base, "config.json")) or {}
    cfg.update({k: v for k, v in user.items() if k in CONFIG})
    # (Written back whole: new settings show up in an older config.json too.)
    write_json(os.path.join(paths.base, "config.json"), cfg)
    return cfg


def fmt_min(seconds):
    return "%d:%02d" % (seconds // 3600, (seconds % 3600) // 60) if seconds >= 3600 else "%d min" % (seconds // 60)


# ---- the training run ----------------------------------------------------------------------------

class Trainer:
    def __init__(self, paths, spec, machine, cfg):
        self.paths, self.spec, self.machine, self.cfg = paths, spec, machine, cfg
        self.instances = {}
        self.threads = {}
        self.state = read_json(paths.state, {}) or {}
        self.league = bobleague.League(self.state, paths, cfg["league_seconds"])
        self.counter = itertools.count(1)
        self.stop = threading.Event()
        self.started = time.time()
        self.last_change = 0
        self.last_status = 0
        self.last_report = 0
        self.last_train = 0
        self.train_thread = None
        self.eval = None
        self.eval_checked = 0
        self.events = collections.deque(self.state.get("events", []), maxlen=40)
        self.counts = self.state.get("counts", {})
        self.fights_seen = 0
        self.league_games = self.state.get("league_games", 0)
        self.crashes = collections.deque(maxlen=20)
        self.lock = threading.Lock()

    def event(self, text):
        line = time.strftime("%d.%m. %H:%M") + "  " + text
        self.events.append(line)
        log.info(text)

    # -- capacity -----------------------------------------------------------------------------

    def max_instances(self):
        if self.cfg["max_instances"]:
            return int(self.cfg["max_instances"])
        cal = read_json(self.paths.calibration)
        if cal and cal.get("recommended", {}).get("instances"):
            return int(cal["recommended"]["instances"])
        cores = self.machine.physical
        by_cpu = max(1, cores - 1)
        by_ram = max(1, (self.machine.total_mb - 4096) // (self.cfg["arena_mb"] + 768))
        return int(min(by_cpu, by_ram))

    def kinds(self):
        c = collections.Counter(i.kind for i in self.instances.values())
        return c

    # -- instances ----------------------------------------------------------------------------

    def new_instance(self, kind):
        name = "%s-%d" % (kind, next(self.counter))
        if kind == "league":
            inst = bobcore.Instance(name, kind, self.spec, self.paths, self.cfg["league_mb"], view=self.cfg["league_view"],
                                    simulation=min(8, self.cfg["league_view"]), challenge=40, search_threads=self.cfg["search_threads"])
        else:
            inst = bobcore.Instance(name, kind, self.spec, self.paths, self.cfg["arena_mb"], view=6, simulation=4,
                                    search_threads=self.cfg["search_threads"])
        self.instances[name] = inst
        target = self._run_league if kind == "league" else self._run_arena
        t = threading.Thread(target=target, args=(inst,), daemon=True, name=name)
        self.threads[name] = t
        t.start()
        return inst

    def _run_arena(self, inst):
        try:
            inst.start()
            if not inst.wait_ready(400):
                raise RuntimeError("Server kam nicht hoch")
            mode = "eval" if inst.kind == "eval" else "explore"
            cmd = "challengecraft_bot arena start %d %s" % (self.cfg["arena_bots"], mode)
            if mode == "eval":
                cmd += " candidate-cover.json"
            inst.command(cmd)
            inst.job = "arena " + mode
            log.info("%s: Arena laeuft (%d Bots, %s)", inst.name, self.cfg["arena_bots"], mode)
        except Exception as e:
            self.event("%s: Start fehlgeschlagen (%s)" % (inst.name, e))
            self.crashes.append(time.time())
            inst.kill()

    def _run_league(self, inst):
        game = None
        try:
            with self.lock:
                seed, sides, game = self.league.next_game()
            inst.start(seed=seed)
            if not inst.wait_ready(600):
                raise RuntimeError("Server kam nicht hoch")
            for name, _ in sides:
                inst.command("challengecraft_bot spawn %s" % name)
            time.sleep(3)
            secs = self.cfg["league_seconds"]
            inst.command("difficulty hard")
            inst.command("tick sprint %d" % (secs * 20 + 400))
            inst.command("challengecraft_bot versus %d hard %s" % (secs, " ".join("%s:%s" % s for s in sides)))
            inst.job = "liga " + " vs ".join(n for n, _ in sides)
            t0 = time.time()
            result = None
            while not self.stop.is_set() and inst.alive() and time.time() - t0 < secs * 4 + 900:
                time.sleep(5)
                text = inst.log_text(400000)
                i = text.rfind("[BOTVS] result ")
                if i >= 0:
                    line = text[i + len("[BOTVS] result "):].split("\n", 1)[0]
                    try:
                        result = json.loads(line)
                    except ValueError:
                        result = None
                    break
            if result is not None:
                with self.lock:
                    self.league_games += 1
                    verdict = self.league.record(game, result)
                    if verdict:
                        self.on_duel(verdict)
                    self.save_state()
                real = time.time() - t0
                log.info("%s: Liga-Spiel fertig in %s (Spielzeit %d s)", inst.name, fmt_min(real), result.get("secs", 0))
            elif not self.stop.is_set():
                self.event("%s: Liga-Spiel ohne Ergebnis abgebrochen" % inst.name)
                with self.lock:
                    self.league.record(game, None)
        except Exception as e:
            self.event("%s: Liga-Spiel fehlgeschlagen (%s)" % (inst.name, e))
            self.crashes.append(time.time())
            if game is not None:
                with self.lock:
                    self.league.record(game, None)
        finally:
            inst.stop()
            shutil.rmtree(os.path.join(inst.dir, "world"), ignore_errors=True)
            with self.lock:
                self.instances.pop(inst.name, None)

    def on_duel(self, v):
        if v["verdict"] == "promoted":
            write_json(os.path.join(self.paths.models, "champion.json"),
                       {"params": v["challenger"], "version": time.strftime("%Y%m%d-%H%M%S"), "games": v["games"],
                        "mean_diff": v["mean_diff"], "history": self.state["league"]["history"][-20:]})
            self.deploy("champion.json")
            self.event("Liga: neuer Champion %s (%+.1f Felder je Spiel ueber %d Spiele)" % (v["challenger"], v["mean_diff"], v["games"]))
        else:
            self.event("Liga: Herausforderer %s abgelehnt (%+.1f ueber %d Spiele)" % (v["challenger"], v["mean_diff"], v["games"]))

    def check_instances(self):
        now = time.time()
        for name, inst in list(self.instances.items()):
            t = self.threads.get(name)
            if inst.kind == "league":
                continue  # (its own thread looks after it)
            if t is not None and t.is_alive():
                continue  # still starting
            if not inst.alive():
                self.event("%s: Server beendet/abgestuerzt - wird ersetzt" % name)
                self.crashes.append(now)
                self.instances.pop(name, None)
                if self.eval and self.eval.get("instance") == name:
                    self.eval["instance"] = None
                continue
            st = inst.status()
            if inst.ready_at and now - inst.ready_at > 120 and (st is None or st.get("age", 999) > 120):
                self.event("%s: haengt (kein Status seit 2 min) - wird neu gestartet" % name)
                inst.kill()
                self.instances.pop(name, None)
                continue
            if st:
                self.fights_seen = max(self.fights_seen, 0)
            if inst.kind == "arena" and inst.ready_at and now - inst.ready_at > self.cfg["arena_restart_min"] * 60:
                log.info("%s: frische Welt (nach %d min)", name, self.cfg["arena_restart_min"])
                threading.Thread(target=inst.stop, daemon=True).start()
                self.instances.pop(name, None)

    def scale(self):
        now = time.time()
        starting = [n for n, t in self.threads.items() if t.is_alive() and n in self.instances and self.instances[n].kind != "league"]
        starting += [n for n, i in self.instances.items() if i.kind == "league" and i.ready_at is None]
        n = len(self.instances)
        cpu, free = self.machine.cpu(60), self.machine.free_mb()
        need = max(self.cfg["arena_mb"], self.cfg["league_mb"]) + 512
        recent_crashes = sum(1 for t in self.crashes if now - t < 600)
        if (cpu > self.cfg["cpu_high"] or free < self.cfg["min_free_mb"]) and n > 0 and now - self.last_change > 60:
            # (Too much: the youngest arena server goes - a league game half played is kept.)
            arenas = sorted([i for i in self.instances.values() if i.kind == "arena"], key=lambda i: i.started or 0)
            if arenas:
                victim = arenas[-1]
                self.event("Last zu hoch (CPU %d%%, frei %d MB): %s gestoppt" % (cpu, free, victim.name))
                self.instances.pop(victim.name, None)
                threading.Thread(target=victim.stop, daemon=True).start()
                self.last_change = now
            return
        if starting or n >= self.max_instances() or recent_crashes >= 5:
            return
        if cpu < self.cfg["cpu_low"] and free > need + self.cfg["min_free_mb"] and now - self.last_change > 90:
            kinds = self.kinds()
            if self.eval and self.eval.get("instance") is None:
                kind = "eval"
            elif kinds.get("league", 0) < round(self.cfg["league_share"] * (n + 1)):
                kind = "league"
            else:
                kind = "arena"
            inst = self.new_instance(kind)
            if kind == "eval":
                self.eval["instance"] = inst.name
            self.last_change = now
            log.info("Neuer Server %s (%s) - jetzt %d, CPU %d%%, frei %d MB", inst.name, kind, n + 1, cpu, free)

    # -- models ---------------------------------------------------------------------------------

    def deploy(self, name):
        if not self.cfg["deploy"]:
            return
        try:
            os.makedirs(self.paths.client_models, exist_ok=True)
            shutil.copy2(os.path.join(self.paths.models, name), os.path.join(self.paths.client_models, name))
        except OSError as e:
            log.warning("Konnte %s nicht nach run/bob-models kopieren: %s", name, e)

    def maybe_train(self):
        if self.train_thread is not None and self.train_thread.is_alive():
            return
        if time.time() - self.last_train < self.cfg["train_every_min"] * 60:
            return
        self.last_train = time.time()
        self.train_thread = threading.Thread(target=self.train_once, daemon=True, name="train")
        self.train_thread.start()

    def train_once(self):
        try:
            result = train_models(self.paths, self.cfg, candidate_ok=self.eval is None)
        except Exception as e:
            log.exception("Training fehlgeschlagen")
            self.event("Training fehlgeschlagen: %s" % e)
            return
        with self.lock:
            self.state["last_train"] = result
            self.counts = result.get("counts", self.counts)
            if result.get("candidate"):
                self.eval = {"version": result["candidate"], "instance": None, "started": time.time()}
                self.event("Neues Deckungsmodell %s ist offline besser - Pruefung in der Arena beginnt" % result["candidate"])
            if result.get("goal_promoted"):
                self.deploy("goal.json")
                self.event("Neues Zielzeit-Modell %s eingesetzt (%.0f%% genauer als der Plan)" % (result["goal_promoted"], 100 * result["goal"]["gain"]))
            self.save_state()

    def maybe_eval(self):
        cand = os.path.join(self.paths.models, "candidate-cover.json")
        if self.eval is None:
            return
        if not os.path.exists(cand):
            self.eval = None
            return
        if self.eval.get("instance") is None:
            # (No room for it: an arena server makes way.)
            if len(self.instances) >= self.max_instances():
                arenas = [i for i in self.instances.values() if i.kind == "arena"]
                if arenas:
                    victim = arenas[-1]
                    self.instances.pop(victim.name, None)
                    threading.Thread(target=victim.stop, daemon=True).start()
            return
        if time.time() - self.eval_checked < 60:
            return
        self.eval_checked = time.time()
        inst = self.instances.get(self.eval["instance"])
        if inst is None:
            self.eval["instance"] = None
            return
        res = eval_result(os.path.join(self.paths.journal, inst.name), self.eval["version"])
        self.eval["progress"] = res
        if res["pairs"] < self.cfg["eval_pairs"]:
            return
        good = res["mean_diff"] < 0 and res["t"] > 1.0 and res["deaths_model"] <= res["deaths_rule"]
        version = self.eval["version"]
        if good:
            cur = os.path.join(self.paths.models, "cover.json")
            if os.path.exists(cur):
                old = read_json(cur) or {}
                shutil.move(cur, os.path.join(self.paths.history, "cover-%s.json" % old.get("version", "alt")))
            shutil.move(cand, cur)
            self.deploy("cover.json")
            self.event("Deckungsmodell %s eingesetzt: Tode %d statt %d, Kosten %+.1f je Kampf (%d Paare)"
                       % (version, res["deaths_model"], res["deaths_rule"], res["mean_diff"], res["pairs"]))
        else:
            shutil.move(cand, os.path.join(self.paths.history, "rejected-cover-%s.json" % version))
            self.event("Deckungsmodell %s abgelehnt: Tode %d (Regeln %d), Kosten %+.1f (%d Paare)"
                       % (version, res["deaths_model"], res["deaths_rule"], res["mean_diff"], res["pairs"]))
        self.state.setdefault("evals", []).append(dict(res, version=version, promoted=good, time=time.time()))
        self.instances.pop(inst.name, None)
        threading.Thread(target=inst.stop, daemon=True).start()
        self.eval = None
        self.save_state()

    # -- status ---------------------------------------------------------------------------------

    def save_state(self):
        self.state["events"] = list(self.events)
        self.state["counts"] = self.counts
        self.state["league_games"] = self.league_games
        write_json(self.paths.state, self.state)

    def status(self):
        insts = []
        fights = 0
        for i in list(self.instances.values()):
            st = i.status() or {}
            ar = st.get("arena") or {}
            fights += ar.get("fights", 0)
            insts.append({"name": i.name, "kind": i.kind, "job": i.job, "up_min": round((time.time() - (i.started or time.time())) / 60, 1),
                          "tps": st.get("tps"), "mspt": st.get("mspt"), "bots": st.get("bots"), "fights": ar.get("fights"),
                          "fights_per_min": ar.get("fights_per_min"), "game_speed": ar.get("game_speed"), "mem_mb": self.machine.process_mb(i.proc.pid) if i.proc else 0})
        models = {}
        for name in ("cover.json", "goal.json", "champion.json"):
            m = read_json(os.path.join(self.paths.models, name))
            if m:
                models[name] = m.get("version") or m.get("params")
        champ = read_json(os.path.join(self.paths.models, "champion.json"))
        return {"time": time.strftime("%Y-%m-%d %H:%M:%S"), "uptime_min": round((time.time() - self.started) / 60, 1),
                "machine": {"cpu": round(self.machine.cpu(60)), "free_mb": self.machine.free_mb(), "cores": self.machine.cores,
                            "ram_mb": self.machine.total_mb, "max_instances": self.max_instances()},
                "instances": insts, "fights_now": fights, "league_games": self.league_games, "models": models,
                "champion": champ.get("params") if champ else bobleague.text(self.league.champion),
                "challenger": bobleague.text(self.state["league"]["challenger"]) if self.state["league"].get("challenger") else None,
                "duel_games": len(self.state["league"].get("games", [])), "eval": self.eval, "counts": self.counts,
                "last_train": self.state.get("last_train"), "events": list(self.events)[-10:]}

    def write_status(self, force=False):
        now = time.time()
        if force or now - self.last_status > 15:
            self.last_status = now
            st = self.status()
            write_json(self.paths.status, st)
            if force or now - self.last_report > 300:
                self.last_report = now
                write_report(self.paths, st, self.state)

    # -- the loop -------------------------------------------------------------------------------

    def run(self):
        try:
            os.remove(self.paths.stop_file)
        except OSError:
            pass

        def on_signal(*_):
            self.stop.set()

        signal.signal(signal.SIGINT, on_signal)
        if hasattr(signal, "SIGTERM"):
            signal.signal(signal.SIGTERM, on_signal)
        if hasattr(signal, "SIGBREAK"):
            signal.signal(signal.SIGBREAK, on_signal)
        self.event("Training gestartet: bis zu %d Server (%d Kerne, %d GB RAM)" % (self.max_instances(), self.machine.cores, self.machine.total_mb // 1024))
        while not self.stop.is_set():
            if os.path.exists(self.paths.stop_file):
                self.event("STOP-Datei gefunden: Training wird beendet")
                break
            try:
                with self.lock:
                    self.check_instances()
                    self.maybe_eval()
                    self.scale()
                self.maybe_train()
                self.write_status()
            except Exception:
                log.exception("Fehler in der Hauptschleife")
            self.stop.wait(5)
        self.shutdown()

    def shutdown(self):
        self.stop.set()
        log.info("Stoppe %d Server ...", len(self.instances))
        ts = [threading.Thread(target=i.stop, daemon=True) for i in list(self.instances.values())]
        for t in ts:
            t.start()
        for t in ts:
            t.join(60)
        for name, t in self.threads.items():
            t.join(5)
        self.instances.clear()
        self.save_state()
        self.write_status(force=True)
        try:
            os.remove(self.paths.stop_file)
        except OSError:
            pass
        self.event("Training beendet")
        self.save_state()


# ---- training the models ------------------------------------------------------------------------

def train_models(paths, cfg, candidate_ok=True):
    import bobmodels
    out = {"time": time.strftime("%Y-%m-%d %H:%M:%S")}
    removed = bobmodels.prune_journal(paths.journal, int(cfg["journal_max_gb"] * (1 << 30)))
    counts = collections.Counter()
    cover_recs, goal_recs = [], []
    sources = [paths.journal] + ([paths.client_journal] if os.path.isdir(paths.client_journal) else [])
    for r in itertools.chain.from_iterable(bobmodels.iter_records(src) for src in sources):
        counts[r.get("t", "?")] += 1
        if r.get("t") == "cover":
            cover_recs.append(r)
        elif r.get("t") == "goal":
            goal_recs.append(r)
    out["counts"] = dict(counts)
    out["journal_mb"] = round(bobmodels.journal_size(paths.journal) / (1 << 20), 1)
    if removed:
        out["pruned_files"] = removed
    use_torch = None if cfg["torch"] == "auto" else cfg["torch"] == "on"
    # The cover model.
    data = bobmodels.CoverData(cover_recs)
    if len(data) >= cfg["cover_min"] and data.explore.sum() >= cfg["cover_min_explore"]:
        # (Learnt from the older four fifths, judged on the newest fifth - fights it never saw.)
        cut = len(data) * 4 // 5
        model, metrics = bobmodels.train_cover(data.subset(slice(0, cut)), use_torch=use_torch)
        held = data.subset(slice(cut, len(data)))
        ev = bobmodels.evaluate_cover(model, held)
        cur = read_json(os.path.join(paths.models, "cover.json"))
        base = ev["value_rule"]
        if cur:
            try:
                cur_ev = bobmodels.evaluate_cover(cur, held)
                base = min(base, cur_ev["value_model"]) if cur_ev["value_model"] is not None else base
                ev["value_current"] = cur_ev["value_model"]
            except Exception:
                pass
        out["cover"] = dict(metrics, **ev)
        if candidate_ok and ev["value_model"] is not None and base is not None and ev["value_model"] < 0.97 * base and ev["p_better"] >= 0.75:
            write_json(os.path.join(paths.models, "candidate-cover.json"), model)
            out["candidate"] = model["version"]
        log.info("Deckungsmodell: %d Kaempfe (%d zufaellig), Wert Modell %s gegen Regeln %s (p=%s), %s",
                 metrics["n"], metrics["n_explore"], ev["value_model"], ev["value_rule"], ev["p_better"], metrics["device"])
    else:
        out["cover"] = {"n": len(data), "n_explore": int(data.explore.sum()),
                        "note": "noch zu wenig Kaempfe (%d, davon %d zufaellig)" % (cfg["cover_min"], cfg["cover_min_explore"])}
    # The goal model.
    model, metrics = bobmodels.train_goal(goal_recs)
    out["goal"] = metrics
    if model is not None and metrics.get("gain") is not None and metrics["gain"] >= 0.05 and metrics["n_done"] >= cfg["goal_min_done"]:
        cur = read_json(os.path.join(paths.models, "goal.json"))
        if cur:
            shutil.copy2(os.path.join(paths.models, "goal.json"), os.path.join(paths.history, "goal-%s.json" % cur.get("version", "alt")))
        write_json(os.path.join(paths.models, "goal.json"), model)
        out["goal_promoted"] = model["version"]
    return out


def eval_result(journal_dir, version):
    """The arena's paired fights for a candidate: per start, the rules' fight against the model's."""
    import bobmodels
    pairs = {}
    for r in bobmodels.iter_records(journal_dir, {"arena"}):
        if r.get("mode") != "eval" or r.get("candidate_version") != version:
            continue
        key = (r.get("scenario") or {}).get("pair")
        if key is None:
            continue
        pairs.setdefault(key, {})[r.get("policy")] = r
    diffs, deaths = [], {"rule": 0, "model": 0}
    for p in pairs.values():
        if "rule" not in p or "model" not in p:
            continue
        c = {k: bobmodels.cost_of(v.get("died", 0), v.get("hp_lost", 0), v.get("secs", 0)) for k, v in p.items()}
        diffs.append(c["model"] - c["rule"])
        deaths["rule"] += p["rule"].get("died", 0)
        deaths["model"] += p["model"].get("died", 0)
    n = len(diffs)
    mean = sum(diffs) / n if n else 0.0
    var = sum((d - mean) ** 2 for d in diffs) / max(1, n - 1) if n > 1 else 0.0
    se = (var / n) ** 0.5 if n and var > 0 else 1.0
    return {"pairs": n, "mean_diff": round(mean, 2), "t": round(-mean / se, 2) if se > 0 else 0.0,
            "deaths_rule": deaths["rule"], "deaths_model": deaths["model"]}


# ---- the report ---------------------------------------------------------------------------------

def write_report(paths, st, state):
    L = []
    L.append("# Bobs Training - Stand %s" % st["time"])
    L.append("")
    m = st["machine"]
    L.append("Laufzeit %s, %d Server laufen (hoechstens %d), CPU %d%%, frei %d MB von %d MB." % (
        fmt_min(st["uptime_min"] * 60), len(st["instances"]), m["max_instances"], m["cpu"], m["free_mb"], m["ram_mb"]))
    L.append("")
    L.append("## Server")
    L.append("")
    L.append("| Server | Art | Aufgabe | seit | TPS | ms/Tick | Kaempfe | Kaempfe/min | Spieltempo | RAM |")
    L.append("|---|---|---|---|---|---|---|---|---|---|")
    for i in st["instances"]:
        L.append("| %s | %s | %s | %s min | %s | %s | %s | %s | %s | %s MB |" % (
            i["name"], i["kind"], i.get("job") or "startet", i["up_min"], i.get("tps") or "-", i.get("mspt") or "-",
            i.get("fights") or "-", i.get("fights_per_min") or "-", ("%sx" % i["game_speed"]) if i.get("game_speed") else "-", i.get("mem_mb") or "-"))
    L.append("")
    c = st.get("counts") or {}
    L.append("## Daten im Journal")
    L.append("")
    L.append("Kaempfe mit Deckungswahl: %d, Arena-Kaempfe: %d, Ziele: %d, Tode: %d, Spiele: %d." % (
        c.get("cover", 0), c.get("arena", 0), c.get("goal", 0), c.get("death", 0), c.get("game", 0)))
    L.append("")
    L.append("## Modelle")
    L.append("")
    models = st.get("models") or {}
    L.append("- Deckungsmodell (Punkt 3): %s" % (models.get("cover.json") or "noch keins - Bob spielt nach den Regeln"))
    L.append("- Zielzeit-Modell (Punkt 4): %s" % (models.get("goal.json") or "noch keins - die Schaetzungen des Plans"))
    L.append("- Champion-Knoepfe (Punkt 5): %s" % (st.get("champion") or "-"))
    lt = st.get("last_train") or {}
    if lt.get("cover"):
        cv = lt["cover"]
        if cv.get("value_model") is not None:
            L.append("- Letztes Training (%s): Deckung auf %d Kaempfen, Kosten je Kampf Modell %.1f gegen Regeln %.1f (sicher besser: %d%%), %s" % (
                lt.get("time"), cv["n"], cv["value_model"], cv["value_rule"] or 0, round(100 * cv.get("p_better", 0)), cv.get("device")))
        else:
            L.append("- Letztes Training (%s): %s (%d Kaempfe)" % (lt.get("time"), cv.get("note", "-"), cv.get("n", 0)))
    if lt.get("goal"):
        g = lt["goal"]
        if g.get("gain") is not None:
            L.append("- Zielzeiten: %d Ziele, Fehler (log) Modell %.3f gegen Plan %.3f - %+.0f%%" % (g["n"], g["mae_log_model"], g["mae_log_plan"], 100 * g["gain"]))
        else:
            L.append("- Zielzeiten: %s (%d Ziele)" % (g.get("note", "-"), g.get("n", 0)))
    ev = st.get("eval")
    if ev:
        p = ev.get("progress") or {}
        L.append("- In der Arena-Pruefung: Deckungsmodell %s, %d Paare, Tode Modell %d gegen Regeln %d" % (
            ev.get("version"), p.get("pairs", 0), p.get("deaths_model", 0), p.get("deaths_rule", 0)))
    for e in (state.get("evals") or [])[-5:]:
        L.append("- Pruefung %s: %s (Paare %d, Tode %d gegen %d, Kosten %+.1f)" % (
            e["version"], "eingesetzt" if e["promoted"] else "abgelehnt", e["pairs"], e["deaths_model"], e["deaths_rule"], e["mean_diff"]))
    L.append("")
    L.append("## Liga (Bob gegen Bob)")
    L.append("")
    L.append("Spiele gesamt: %d. Herausforderer: %s (%d Spiele im laufenden Duell)." % (st["league_games"], st.get("challenger") or "-", st.get("duel_games", 0)))
    hist = (state.get("league") or {}).get("history") or []
    if hist:
        L.append("")
        L.append("| Zeit | Herausforderer | Spiele | Felder +/- je Spiel | Ergebnis |")
        L.append("|---|---|---|---|---|")
        for h in hist[-10:]:
            L.append("| %s | %s | %d | %+.1f | %s |" % (time.strftime("%d.%m. %H:%M", time.localtime(h["time"])), h["challenger"], h["games"],
                                                    h["mean_diff"], "neuer Champion" if h["verdict"] == "promoted" else "abgelehnt"))
    L.append("")
    L.append("## Ereignisse")
    L.append("")
    for e in st.get("events") or []:
        L.append("- " + e)
    with open(paths.report, "w", encoding="utf-8") as f:
        f.write("\n".join(L) + "\n")


# ---- calibration (point 1) ---------------------------------------------------------------------

def calibrate(paths, spec, machine, cfg, quick=False):
    """How many servers and arenas this machine runs at once, and how fast: each step one more arena
    server, measured once warm; until more servers no longer bring more fights a minute."""
    import bobmodels
    rows = []
    log.info("Kalibrierung: %s", machine.describe())
    warm, measure = (60, 90) if quick else (90, 150)
    max_try = max(1, min(12, machine.cores))
    if cfg.get("max_instances"):
        max_try = min(max_try, int(cfg["max_instances"]))
    insts = []
    stop_reason = "Obergrenze"

    def snap(inst):
        st = inst.status() or {}
        return {"fights": (st.get("arena") or {}).get("fights", 0), "tick": st.get("tick", 0), "time": time.time()}

    try:
        for n in range(1, max_try + 1):
            inst = bobcore.Instance("calib-%d" % n, "arena", spec, paths, cfg["arena_mb"], view=6, simulation=4,
                                    search_threads=cfg["search_threads"])
            inst.start()
            if not inst.wait_ready(400):
                stop_reason = "Server %d kam nicht hoch" % n
                inst.kill()
                break
            inst.command("challengecraft_bot arena start %d explore" % cfg["arena_bots"])
            insts.append(inst)
            time.sleep(warm)
            before = {i.name: snap(i) for i in insts}
            cpu_samples, t0 = [], time.time()
            while time.time() - t0 < measure:
                time.sleep(5)
                cpu_samples.append(machine.psutil.cpu_percent(interval=None))
            after = {i.name: snap(i) for i in insts}
            minutes = (time.time() - t0) / 60
            # (The game ticks all servers manage a second: steady, where the fights a minute swing
            # with how the fights happen to go - a quick death, a long chase.)
            tps = [(after[k]["tick"] - before[k]["tick"]) / max(1e-6, after[k]["time"] - before[k]["time"]) for k in before]
            total_tps = sum(tps)
            fights = sum(after[k]["fights"] - before[k]["fights"] for k in before) / minutes
            row = {"instances": n, "bots": n * cfg["arena_bots"], "ticks_per_s": round(total_tps, 1),
                   "game_speed": round(total_tps / (20.0 * n), 2), "fights_per_min": round(fights, 1),
                   "cpu": round(sum(cpu_samples) / max(1, len(cpu_samples))), "free_mb": machine.free_mb(),
                   "mem_mb": sum(machine.process_mb(i.proc.pid) for i in insts if i.proc)}
            rows.append(row)
            log.info("Kalibrierung: %d Server -> %.0f Spiel-Ticks/s gesamt (Tempo %.2fx je Server), %.1f Kaempfe/min, CPU %d%%, frei %d MB",
                     n, total_tps, row["game_speed"], fights, row["cpu"], row["free_mb"])
            if row["free_mb"] < cfg["min_free_mb"] + cfg["arena_mb"]:
                stop_reason = "Arbeitsspeicher"
                break
            if row["cpu"] >= 95:
                stop_reason = "CPU voll"
                break
            if len(rows) >= 2 and rows[-1]["ticks_per_s"] < 1.08 * rows[-2]["ticks_per_s"]:
                stop_reason = "kein Zuwachs mehr"
                break
    finally:
        for i in insts:
            i.stop()
    best = None
    for r in rows:
        if r["cpu"] <= cfg["cpu_high"] and (best is None or r["ticks_per_s"] > best["ticks_per_s"] * 1.05):
            best = r
    if best is None and rows:
        best = rows[0]
    # The trainer: numpy (and PyTorch, if there) on made-up fights of the right shape.
    rng = random.Random(1)
    fake = []
    for _ in range(20000):
        x = [rng.random() for _ in range(bobmodels.COVER_FEATURES)]
        o = rng.choice(bobmodels.OPTIONS)
        fake.append({"x": x, "option": o, "possible": bobmodels.OPTIONS, "how": "explore", "rule": "BOX",
                     "died": int(rng.random() < x[0] * 0.5), "hp_lost": 10 * x[1], "secs": 30 * x[2]})
    data = bobmodels.CoverData(fake)
    train = {}
    for label, flag in (("numpy (CPU)", False), ("pytorch", True)):
        if flag:
            try:
                import torch  # noqa: F401
            except ImportError:
                train[label] = "nicht installiert"
                continue
        t0 = time.time()
        _, m = bobmodels.train_cover(data, epochs=20, patience=100, use_torch=flag)
        train[label] = "%.1f s fuer 20 Epochen auf 20000 Kaempfen (%s)" % (time.time() - t0, m["device"])
    result = {"time": time.strftime("%Y-%m-%d %H:%M:%S"), "machine": machine.describe(), "rows": rows, "recommended": best,
              "stop_reason": stop_reason, "arena_bots": cfg["arena_bots"], "training": train}
    write_json(paths.calibration, result)
    write_calibration_md(paths, result)
    return result


def write_calibration_md(paths, r):
    m = r["machine"]
    L = ["# Kalibrierung - %s" % r["time"], "",
         "Rechner: %s, %d logische Kerne (%d physische), %d GB RAM, Python %s." % (
             m["cpu"], m["cores_logical"], m["cores_physical"], m["ram_mb"] // 1024, m["python"]), "",
         "Jeder Arena-Server laeuft mit %d Bots; gemessen wurde nach dem Aufwaermen." % r["arena_bots"], "",
         "Durchsatz: die Spiel-Ticks aller Server zusammen je Sekunde (20 je Server = Echtzeit). Die Kaempfe",
         "pro Minute schwanken mit dem Verlauf der Kaempfe und sind nur zur Orientierung.", "",
         "| Server | Bots | Spiel-Ticks/s gesamt | Tempo je Server | Kaempfe/min | CPU | frei | RAM der Server |",
         "|---|---|---|---|---|---|---|---|"]
    for row in r["rows"]:
        L.append("| %d | %d | %.0f | %.2fx | %.1f | %d%% | %d MB | %d MB |" % (
            row["instances"], row["bots"], row["ticks_per_s"], row["game_speed"], row["fights_per_min"], row["cpu"],
            row["free_mb"], row["mem_mb"]))
    L.append("")
    b = r.get("recommended")
    if b:
        L.append("**Empfehlung: bis zu %d Server gleichzeitig** (%.0f Spiel-Ticks/s, etwa %.1f Kaempfe pro Minute, CPU %d%%). "
                 "Ende der Messung: %s." % (b["instances"], b["ticks_per_s"], b["fights_per_min"], b["cpu"], r["stop_reason"]))
        L.append("")
        L.append("Ein Liga-Spiel (30 Minuten Spielzeit) dauert bei diesem Tempo grob %d Minuten." % max(
            1, round(30 / max(0.1, b["game_speed"]))))
        L.append("")
        L.append("Das Training startet Server bis zu dieser Zahl, aber nur, solange der Rechner Luft hat: "
                 "ueber %d%% CPU oder unter %d MB freiem Speicher nimmt es wieder einen weg." % (CONFIG["cpu_high"], CONFIG["min_free_mb"]))
    L.append("")
    L.append("Trainingsgeschwindigkeit:")
    for k, v in r["training"].items():
        L.append("- %s: %s" % (k, v))
    with open(paths.calibration_md, "w", encoding="utf-8") as f:
        f.write("\n".join(L) + "\n")


# ---- command line --------------------------------------------------------------------------------

def main():
    ap = argparse.ArgumentParser(description="Bobs Training")
    ap.add_argument("command", nargs="?", default="run", choices=["run", "calibrate", "status", "stop", "train"])
    ap.add_argument("--base", help="Ordner fuer alles (Standard: runs/train im Projekt)")
    ap.add_argument("--no-build", action="store_true", help="nicht neu bauen (die vorhandene Launch-Spec)")
    ap.add_argument("--quick", action="store_true", help="kurze Kalibrierung")
    ap.add_argument("--verbose", action="store_true")
    args = ap.parse_args()
    paths = bobcore.Paths(args.base)
    bobcore.setup_logging(paths, args.verbose)
    cfg = load_config(paths)
    if args.command == "stop":
        open(paths.stop_file, "w").close()
        print("STOP gesetzt - das Training beendet sich in wenigen Sekunden (die Server werden sauber gestoppt).")
        return
    if args.command == "status":
        st = read_json(paths.status)
        if not st:
            print("Kein Status - laeuft das Training?")
            return
        print("Stand %s, Laufzeit %s min, %d Server, CPU %s%%, frei %s MB" % (
            st["time"], st["uptime_min"], len(st["instances"]), st["machine"]["cpu"], st["machine"]["free_mb"]))
        for i in st["instances"]:
            print("  %-10s %-6s %-22s TPS %-5s Kaempfe %-5s" % (i["name"], i["kind"], i.get("job") or "startet", i.get("tps"), i.get("fights")))
        print("Modelle:", st.get("models"))
        print("Champion:", st.get("champion"))
        print("Bericht:", paths.report)
        return
    if args.command == "train":
        res = train_models(paths, cfg)
        print(json.dumps(res, indent=1, ensure_ascii=False))
        return
    machine = bobcore.Machine()
    java = bobcore.find_java()
    if not java:
        log.error("Kein Java 25 gefunden. Bitte ein JDK 25 installieren (z.B. in IntelliJ: File > Project Structure > SDKs) "
                  "oder BOB_JAVA auf die java.exe setzen.")
        raise SystemExit(2)
    log.info("Java: %s", java)
    spec = bobcore.snapshot(bobcore.launch_spec(java, rebuild=not args.no_build), paths)
    if args.command == "calibrate":
        r = calibrate(paths, spec, machine, cfg, quick=args.quick)
        print(open(paths.calibration_md, encoding="utf-8").read())
        return
    if not os.path.exists(paths.calibration):
        log.info("Noch keine Kalibrierung: zuerst wird der Rechner vermessen (etwa 15-30 Minuten) ...")
        calibrate(paths, spec, machine, cfg)
        log.info("Kalibrierung fertig: %s", paths.calibration_md)
    Trainer(paths, spec, machine, cfg).run()


if __name__ == "__main__":
    main()
