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


def running():
    """The trainer counts as running while its status file is fresh (it is written every 15 s)."""
    try:
        return time.time() - os.path.getmtime(os.path.join(BASE, "status.json")) < 60
    except OSError:
        return False


def payload():
    st = load(os.path.join(BASE, "status.json"), {}) or {}
    state = load(os.path.join(BASE, "state.json"), {}) or {}
    models = {n: load(os.path.join(BASE, "models", n)) for n in ("cover.json", "goal.json", "champion.json")}
    league = state.get("league") or {}
    last = (state.get("last_train") or {})
    return {
        "running": running(),
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


def start():
    if running():
        return "Das Training laeuft schon."
    try:
        os.remove(os.path.join(BASE, "STOP"))
    except OSError:
        pass
    os.makedirs(BASE, exist_ok=True)
    out = open(os.path.join(BASE, "dashboard-trainer.log"), "a", encoding="utf-8")
    kw = {"cwd": ROOT, "stdout": out, "stderr": subprocess.STDOUT, "stdin": subprocess.DEVNULL}
    if os.name == "nt":
        kw["creationflags"] = 0x08000000  # CREATE_NO_WINDOW
    else:
        kw["start_new_session"] = True
    subprocess.Popen([sys.executable, os.path.join(HERE, "bobtrain.py"), "run"], **kw)
    return "Training startet - nach etwa einer Minute erscheinen die ersten Server."


def stop():
    os.makedirs(BASE, exist_ok=True)
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
