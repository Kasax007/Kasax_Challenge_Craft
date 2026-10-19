#!/usr/bin/env python3
"""Bobs Trainings-Dashboard: eine Seite im Browser (http://127.0.0.1:8765), die zeigt, ob das Training
laeuft, wie gut Bob gerade ist und was zuletzt passiert ist - mit Start und Stopp.

    python scripts/train/dashboard.py        (oder scripts\\train\\dashboard.bat)

Liest nur, was der Trainer ohnehin schreibt (runs/train/status.json, state.json, models/,
calibration.json). Nur auf diesem Rechner erreichbar.
"""
import json
import os
import subprocess
import sys
import threading
import time
import webbrowser
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
BASE = os.path.join(ROOT, "runs", "train")
PAGE = os.path.join(HERE, "dashboard.html")


def load(path, default=None):
    try:
        with open(path, encoding="utf-8") as f:
            return json.load(f)
    except (OSError, ValueError):
        return default


def trainer():
    """The running trainer's {pid, phase, since}, or None. It says so in trainer.json from its first
    second on (building and measuring the laptop take minutes before the first status)."""
    info = load(os.path.join(BASE, "trainer.json"))
    if not info:
        return None
    try:
        import psutil
        p = psutil.Process(int(info["pid"]))
        if p.is_running() and any("bobtrain" in a for a in p.cmdline()):
            return info
    except Exception:
        pass
    return None


def running():
    if trainer():
        return True
    # (A trainer from before trainer.json: its status file is fresh, written every 15 s.)
    try:
        return time.time() - os.path.getmtime(os.path.join(BASE, "status.json")) < 60
    except OSError:
        return False


def log_tail(n=12):
    """The trainer's last lines (its log, and what it printed when started from here)."""
    lines = []
    for name in ("bobtrain.log", "dashboard-trainer.log"):
        try:
            with open(os.path.join(BASE, name), encoding="utf-8", errors="replace") as f:
                f.seek(max(0, os.path.getsize(f.name) - 20000))
                lines = f.read().splitlines()[-n:]
        except OSError:
            continue
        if lines:
            break
    return lines


def payload():
    st = load(os.path.join(BASE, "status.json"), {}) or {}
    state = load(os.path.join(BASE, "state.json"), {}) or {}
    models = {n: load(os.path.join(BASE, "models", n)) for n in ("cover.json", "goal.json", "champion.json")}
    league = state.get("league") or {}
    last = (state.get("last_train") or {})
    t = trainer()
    return {
        "running": running(),
        "phase": t.get("phase") if t else None,
        "phase_since": t.get("since") if t else None,
        "log": log_tail(),
        "stopping": os.path.exists(os.path.join(BASE, "STOP")),
        "status": st,
        "evals": state.get("evals", [])[-20:],
        "duels": league.get("history", [])[-20:],
        "duel_games": len(league.get("games", [])),
        "challenger": league.get("challenger"),
        "champion": (models["champion.json"] or {}).get("params"),
        "cover_version": (models["cover.json"] or {}).get("version"),
        "goal_version": (models["goal.json"] or {}).get("version"),
        "goal": last.get("goal"),
        "cover": last.get("cover"),
        "counts": last.get("counts") or state.get("counts") or {},
        "league_games": state.get("league_games", 0),
        "events": (st.get("events") or state.get("events") or [])[-15:],
        "calibration": (load(os.path.join(BASE, "calibration.json"), {}) or {}).get("recommended"),
    }


def trainer_python():
    """The Python to train with: the training's own environment (runs/train/venv) if there is one,
    else this one - with numpy and psutil installed first if they are missing (a new machine)."""
    venv = os.path.join(BASE, "venv", "Scripts" if os.name == "nt" else "bin", "python.exe" if os.name == "nt" else "python")
    py = venv if os.path.exists(venv) else sys.executable
    check = subprocess.run([py, "-c", "import numpy, psutil"], capture_output=True)
    if check.returncode != 0:
        req = os.path.join(HERE, "requirements.txt")
        r = subprocess.run([py, "-m", "pip", "install", "--disable-pip-version-check", "-q", "-r", req], capture_output=True, text=True)
        if r.returncode != 0:
            raise RuntimeError("pip install fehlgeschlagen: " + (r.stderr or r.stdout).strip()[-300:])
    return py


def start():
    if running():
        return "Das Training laeuft schon."
    try:
        os.remove(os.path.join(BASE, "STOP"))
    except OSError:
        pass
    os.makedirs(BASE, exist_ok=True)
    out = open(os.path.join(BASE, "dashboard-trainer.log"), "w", encoding="utf-8")
    kw = {"cwd": ROOT, "stdout": out, "stderr": subprocess.STDOUT, "stdin": subprocess.DEVNULL}
    if os.name == "nt":
        # (Own hidden console and process group: it keeps running when the dashboard window closes.)
        kw["creationflags"] = 0x08000000 | 0x00000200  # CREATE_NO_WINDOW | CREATE_NEW_PROCESS_GROUP
    else:
        kw["start_new_session"] = True
    try:
        py = trainer_python()
    except Exception as e:
        out.close()
        return "Konnte die Python-Pakete nicht installieren (%s). Bitte start-dashboard.bat benutzen." % e
    try:
        proc = subprocess.Popen([py, "-u", os.path.join(HERE, "bobtrain.py"), "run"], **kw)
    except OSError as e:
        return "Konnte das Training nicht starten: %s" % e
    finally:
        out.close()
    # (Gone at once: Java missing, a broken config ... - say why instead of nothing.)
    for _ in range(8):
        time.sleep(0.5)
        if proc.poll() is not None:
            tail = " | ".join(log_tail(4))
            return "Das Training hat sich gleich wieder beendet (Code %s). %s" % (proc.returncode, tail)
        if trainer():
            break
    return "Training startet. Erst wird Bob gebaut (und beim ersten Mal der Rechner vermessen), dann kommen die Server."


def stop():
    os.makedirs(BASE, exist_ok=True)
    t = trainer()
    if t and t.get("phase") != "trainiert":
        # (Still building or measuring: nothing to save yet. Its servers notice it is gone.)
        try:
            import psutil
            p = psutil.Process(int(t["pid"]))
            for c in p.children(recursive=True):
                c.terminate()
            p.terminate()
            return "Gestoppt (es lief erst: %s)." % t.get("phase")
        except Exception as e:
            return "Konnte nicht stoppen: %s" % e
    open(os.path.join(BASE, "STOP"), "w").close()
    return "Stopp gesetzt - die Server werden sauber beendet (etwa eine Minute)."


class Handler(BaseHTTPRequestHandler):
    def _send(self, code, body, kind="application/json"):
        data = body if isinstance(body, bytes) else json.dumps(body, ensure_ascii=False).encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", kind + "; charset=utf-8")
        self.send_header("Cache-Control", "no-store")
        self.end_headers()
        self.wfile.write(data)

    def do_GET(self):
        if self.path.startswith("/api/data"):
            return self._send(200, payload())
        with open(PAGE, "rb") as f:
            self._send(200, f.read(), "text/html")

    def do_POST(self):
        # (Only from this page on this machine.)
        origin = self.headers.get("Origin", "")
        if origin and not origin.startswith(("http://127.0.0.1:", "http://localhost:")):
            return self._send(403, {"message": "nur lokal"})
        actions = {"/api/start": start, "/api/stop": stop}
        if self.path not in actions:
            return self._send(404, {"message": "unbekannt"})
        self._send(200, {"message": actions[self.path]()})

    def log_message(self, *args):
        pass


def main():
    port = int(os.environ.get("BOB_DASHBOARD_PORT", "8765"))
    httpd = ThreadingHTTPServer(("127.0.0.1", port), Handler)
    url = "http://127.0.0.1:%d/" % port
    print("Bobs Trainings-Dashboard:", url, "(Fenster offen lassen; Strg+C beendet nur das Dashboard)")
    if "--no-browser" not in sys.argv:
        threading.Timer(0.8, lambda: webbrowser.open(url)).start()
    try:
        httpd.serve_forever()
    except KeyboardInterrupt:
        pass


if __name__ == "__main__":
    main()
