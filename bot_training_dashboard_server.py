#!/usr/bin/env python3
"""Local dashboard + safe controller for Challenge Craft Bob training.

Only accepts a small allow-list of trainer operations. Binds to loopback by default.
No arbitrary shell commands, process killing, path-based file reads, or configuration writes.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
import platform
import subprocess
import sys
import threading
import time
import webbrowser
from datetime import datetime
from http.server import ThreadingHTTPServer, BaseHTTPRequestHandler
from pathlib import Path
from urllib.parse import urlparse

DASHBOARD = Path(__file__).with_name("bob-training-dashboard.html")
TEXT_EXTS = {
    ".json", ".jsonl", ".ndjson", ".log", ".out", ".txt", ".csv", ".tsv",
    ".yaml", ".yml", ".toml", ".properties", ".md", ".cfg", ".ini", ".xml",
}
MODEL_EXTS = {".pt", ".pth", ".bin", ".model", ".safetensors", ".onnx", ".pkl", ".pickle", ".npz", ".npy"}
SKIP_DIRS = {
    ".git", ".gradle", ".idea", ".vscode", "libraries", "node_modules", "cache", ".cache",
    "world", "worlds", "screenshots", "resourcepacks", "shaderpacks", "assets", "build",
    "__pycache__", "venv", ".venv",
}
MAX_FILE_BYTES = 1_100_000
MAX_TOTAL_TEXT_BYTES = 18_000_000
MAX_FILES = 6000
RUNTIME_CACHE_SECONDS = 8
_runtime_cache: dict = {"at": 0.0, "external": []}
_runtime_lock = threading.Lock()


def now_iso() -> str:
    return datetime.now().astimezone().isoformat(timespec="seconds")


def safe_json(path: Path, default=None):
    try:
        with path.open("r", encoding="utf-8") as f:
            return json.load(f)
    except (OSError, ValueError, TypeError):
        return default


def write_json_atomic(path: Path, data) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    tmp = path.with_suffix(path.suffix + ".tmp")
    with tmp.open("w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=2)
    os.replace(tmp, path)


def append_jsonl(path: Path, data) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("a", encoding="utf-8") as f:
        f.write(json.dumps(data, ensure_ascii=False) + "\n")


def tail_text(path: Path, max_bytes: int = 50000, max_lines: int = 100) -> str:
    try:
        with path.open("rb") as f:
            f.seek(0, os.SEEK_END)
            size = f.tell()
            f.seek(max(0, size - max_bytes))
            data = f.read().decode("utf-8", errors="replace")
        return "\n".join(data.splitlines()[-max_lines:])
    except OSError:
        return ""


def is_under(path: Path, parent: Path) -> bool:
    try:
        path.relative_to(parent)
        return True
    except ValueError:
        return False


def relevant_run_file(path: Path, root: Path) -> bool:
    if path.suffix.lower() in TEXT_EXTS or path.suffix.lower() in MODEL_EXTS:
        return True
    name = path.name.lower()
    return any(token in name for token in ("champion", "cover", "goal", "model", "calibration", "result", "match"))


def priority(path: Path, root: Path) -> tuple[int, str]:
    rel = path.relative_to(root).as_posix().lower()
    name = path.name.lower()
    if rel == "config.json": return (0, rel)
    if "runs/train" in rel and ("config" in name or "calibration" in rel): return (1, rel)
    if any(x in rel for x in ("result", "match", "journal", "eval", "metrics")): return (2, rel)
    if rel.endswith((".log", ".out", ".jsonl", ".ndjson")): return (3, rel)
    if "/models/" in rel or "/bob-models/" in rel: return (4, rel)
    return (5, rel)


def read_text_safely(path: Path) -> tuple[str | None, bool]:
    if path.suffix.lower() not in TEXT_EXTS:
        return None, False
    try:
        size = path.stat().st_size
        if size > MAX_FILE_BYTES:
            head_bytes = 42_000
            tail_bytes = MAX_FILE_BYTES - head_bytes
            with path.open("rb") as f:
                head = f.read(head_bytes).decode("utf-8", errors="replace")
                f.seek(max(0, size - tail_bytes))
                tail = f.read(tail_bytes).decode("utf-8", errors="replace")
            return head + "\n\n--- DASHBOARD: GROSSE DATEI GEKÜRZT; MITTELTEIL NICHT GELADEN ---\n\n" + tail, True
        return path.read_text(encoding="utf-8", errors="replace"), False
    except (OSError, UnicodeError):
        return None, False


def collect(root: Path) -> dict:
    """Inventory all of runs/train; include useful runtime artefacts from run/."""
    root = root.resolve()
    candidates: set[Path] = set()
    root_config = root / "config.json"
    if root_config.is_file(): candidates.add(root_config.resolve())
    train_root = root / "runs" / "train"
    if train_root.exists():
        for cur, dirs, files in os.walk(train_root):
            dirs[:] = sorted(d for d in dirs if d.lower() not in SKIP_DIRS and not d.startswith("."))
            for fn in files:
                p = Path(cur) / fn
                try:
                    if p.is_file(): candidates.add(p.resolve())
                except OSError:
                    continue
    run_root = root / "run"
    if run_root.exists():
        for cur, dirs, files in os.walk(run_root):
            dirs[:] = sorted(d for d in dirs if d.lower() not in SKIP_DIRS and not d.startswith("."))
            for fn in files:
                p = Path(cur) / fn
                try:
                    if p.is_file() and relevant_run_file(p, root): candidates.add(p.resolve())
                except OSError:
                    continue
    ordered = sorted((p for p in candidates if is_under(p, root)), key=lambda p: priority(p, root))
    omitted_files = max(0, len(ordered) - MAX_FILES)
    ordered = ordered[:MAX_FILES]
    output: list[dict] = []
    total_text_bytes = 0
    text_limit_reached = False
    for p in ordered:
        try:
            stat = p.stat()
            rel = p.relative_to(root).as_posix()
            content, truncated = (None, False)
            if p.suffix.lower() in TEXT_EXTS and not text_limit_reached:
                content, truncated = read_text_safely(p)
                if content is not None:
                    size_text = len(content.encode("utf-8", errors="replace"))
                    if total_text_bytes + size_text > MAX_TOTAL_TEXT_BYTES:
                        content = None
                        text_limit_reached = True
                    else:
                        total_text_bytes += size_text
            output.append({
                "name": p.name, "path": rel, "size": stat.st_size, "modified": int(stat.st_mtime),
                "text": content, "previewAvailable": content is not None, "truncated": truncated,
                "binary": content is None and p.suffix.lower() not in TEXT_EXTS,
                "extension": p.suffix.lower() or "(ohne Endung)",
            })
        except OSError:
            continue
    digest_src = "|".join(f"{f['path']}:{f['modified']}:{f['size']}:{int(f['previewAvailable'])}" for f in output)
    return {"root": str(root), "files": output, "fileCount": len(output), "omittedFiles": omitted_files,
            "textLimitReached": text_limit_reached, "digest": hashlib.sha256(digest_src.encode()).hexdigest(), "scannedAt": time.time()}


def process_alive(pid) -> bool:
    try:
        pid = int(pid)
        if pid <= 0: return False
        os.kill(pid, 0)
        return True
    except (ValueError, OSError, ProcessLookupError, PermissionError):
        return False


def external_trainer_processes(root: Path) -> list[dict]:
    """Discover only processes whose command line names this repo's bobtrain.py."""
    script = (root / "scripts" / "train" / "bobtrain.py").resolve()
    found: list[dict] = []
    try:
        if os.name == "nt":
            ps = r"$ErrorActionPreference='SilentlyContinue'; Get-CimInstance Win32_Process | Where-Object { $_.CommandLine -and $_.CommandLine.Contains('bobtrain.py') } | Select-Object ProcessId,Name,CommandLine | ConvertTo-Json -Compress"
            enc = __import__("base64").b64encode(ps.encode("utf-16le")).decode("ascii")
            p = subprocess.run(["powershell.exe", "-NoProfile", "-ExecutionPolicy", "Bypass", "-EncodedCommand", enc],
                               capture_output=True, text=True, timeout=4, creationflags=getattr(subprocess, "CREATE_NO_WINDOW", 0))
            raw = json.loads(p.stdout) if p.stdout.strip() else []
            if isinstance(raw, dict): raw = [raw]
        else:
            raw = []
            proc_root = Path("/proc")
            if proc_root.exists():
                for d in proc_root.iterdir():
                    if not d.name.isdigit(): continue
                    try:
                        cmd = (d / "cmdline").read_bytes().replace(b"\0", b" ").decode(errors="replace")
                        if "bobtrain.py" in cmd: raw.append({"ProcessId": int(d.name), "Name": "python", "CommandLine": cmd})
                    except OSError:
                        pass
        root_norm = os.path.normcase(str(root.resolve())).replace("/", "\\").lower()
        script_norm = os.path.normcase(str(script)).replace("/", "\\").lower()
        rel_norm = "scripts\\train\\bobtrain.py"
        for item in raw:
            cmd = str(item.get("CommandLine") or "")
            cn = os.path.normcase(cmd).replace("/", "\\").lower()
            # Strict repo match: full path or the repo-relative command in a matching working tree.
            full_path_match = script_norm in cn
            relative_path_match = rel_norm in cn
            if not full_path_match and not relative_path_match:
                continue
            if not full_path_match:
                # train.bat invokes bobtrain.py relative to the repo root. Confirm the matching
                # working copy has a freshly written trainer log before accepting this weaker match.
                try:
                    log_fresh = (root / "runs" / "train" / "bobtrain.log").stat().st_mtime >= time.time() - 120
                except OSError:
                    log_fresh = False
                if not log_fresh:
                    continue
            command = "unknown"
            for cmd_name in ("run", "calibrate", "train", "status", "stop"):
                if __import__("re").search(r"bobtrain\.py[\"']?\s+" + cmd_name + r"(?:\s|$)", cmd, __import__("re").I):
                    command = "run" if cmd_name == "run" else ("calibrate" if cmd_name == "calibrate" else ("train-once" if cmd_name == "train" else cmd_name))
                    break
            if command in ("status", "stop", "unknown"): continue
            found.append({"pid": int(item.get("ProcessId") or 0), "name": str(item.get("Name") or "python"), "command": command,
                          "source": "external-process", "commandLine": cmd[:500]})
    except (OSError, subprocess.SubprocessError, ValueError, TypeError, json.JSONDecodeError):
        pass
    return found


def get_external_cached(root: Path, force=False) -> list[dict]:
    with _runtime_lock:
        if force or time.time() - _runtime_cache["at"] > RUNTIME_CACHE_SECONDS:
            _runtime_cache["external"] = external_trainer_processes(root)
            _runtime_cache["at"] = time.time()
        return list(_runtime_cache["external"])


def parse_properties(path: Path) -> dict:
    wanted = {"level-seed", "server-port", "view-distance", "simulation-distance", "max-players", "motd", "difficulty", "online-mode", "server-ip"}
    out = {}
    try:
        for line in path.read_text(encoding="utf-8", errors="replace").splitlines():
            line = line.strip()
            if not line or line.startswith("#") or "=" not in line: continue
            k, v = line.split("=", 1)
            if k.strip() in wanted: out[k.strip()] = v.strip()
    except OSError:
        pass
    return out


def dir_journal_stats(path: Path) -> dict:
    files = list(path.glob("**/*.jsonl")) if path.exists() else []
    size = 0
    latest = 0
    for f in files:
        try:
            st = f.stat(); size += st.st_size; latest = max(latest, st.st_mtime)
        except OSError: pass
    return {"files": len(files), "bytes": size, "modified": latest or None}


def controller_info(root: Path) -> dict:
    base = root / "runs" / "train"
    c = safe_json(base / "dashboard-controller.json", {}) or {}
    phase = c.get("phase")
    alive = phase in ("starting", "preparing", "creating venv", "running") and process_alive(c.get("worker_pid"))
    # Close the tiny double-click race between writing a pending operation and the worker's first write.
    if phase == "starting" and not c.get("worker_pid"):
        try: alive = alive or (time.time() - float(c.get("started_at", 0)) < 20)
        except (TypeError, ValueError): pass
    return {**c, "workerAlive": alive}


def runtime_payload(root: Path, scan_external=False) -> dict:
    root = root.resolve(); base = root / "runs" / "train"
    base.mkdir(parents=True, exist_ok=True)
    status_path = base / "status.json"
    status = safe_json(status_path, {}) or {}
    status_mtime = status_path.stat().st_mtime if status_path.exists() else 0
    status_age = max(0, time.time() - status_mtime) if status_mtime else None
    status_fresh = status_age is not None and status_age <= 45
    ctl = controller_info(root)
    external = get_external_cached(root, force=scan_external)
    active_cmd = None
    active_source = None
    active_pid = None
    active = False
    if ctl.get("workerAlive"):
        active = True; active_cmd = ctl.get("action"); active_source = "dashboard"; active_pid = ctl.get("worker_pid")
    elif external:
        active = True; active_cmd = external[0].get("command"); active_source = "detected process"; active_pid = external[0].get("pid")
    elif status_fresh:
        # bobtrain writes status every ~15 seconds. Used only as fallback for an externally started run.
        active = True; active_cmd = "run"; active_source = "fresh status heartbeat"
    if ctl.get("phase") in ("completed", "failed") and ctl.get("finished_at") and status_mtime <= float(ctl.get("finished_at", 0)) + 1:
        if not ctl.get("workerAlive") and not external:
            active = False
            if active_source == "fresh status heartbeat": active_cmd = None; active_source = None

    active_map = {str(i.get("name")): i for i in (status.get("instances") or []) if isinstance(i, dict)}
    train_instances = base / "instances"
    journal_root = base / "journal"
    instances = []
    if train_instances.exists():
        dirs = []
        for p in train_instances.iterdir():
            try:
                if p.is_dir(): dirs.append(p)
            except OSError: pass
        dirs.sort(key=lambda p: p.stat().st_mtime if p.exists() else 0, reverse=True)
        # Keep all reported active servers plus newest 30 historical folders.
        dirs = dirs[:60]
        for p in dirs:
            name = p.name
            rep = active_map.get(name)
            out_path = p / "server.out"
            bstatus_path = p / "bob-status.json"
            bstatus = safe_json(bstatus_path, {}) or {}
            out_txt = tail_text(out_path, 48000, 80)
            out_lines = out_txt.splitlines()
            mods = []
            low = out_txt.lower()
            for mod in ("lithium", "c2me", "ferritecore"):
                if mod in low: mods.append(mod)
            journal = dir_journal_stats(journal_root / name)
            try:
                out_stat = out_path.stat(); out_bytes = out_stat.st_size; out_modified = out_stat.st_mtime
            except OSError: out_bytes = 0; out_modified = None
            try:
                folder_modified = p.stat().st_mtime
            except OSError: folder_modified = None
            is_active = bool(active and status_fresh and rep is not None)
            instances.append({
                "name": name, "kind": (rep or {}).get("kind") or ("league" if name.startswith("league-") else "arena" if name.startswith("arena-") else "unknown"),
                "job": (rep or {}).get("job"), "active": is_active,
                "state": "active" if is_active else ("stale status" if rep is not None and not status_fresh else "historical / unknown"),
                "up_min": (rep or {}).get("up_min"), "tps": (rep or {}).get("tps", bstatus.get("tps")),
                "mspt": (rep or {}).get("mspt", bstatus.get("mspt")), "bots": (rep or {}).get("bots", bstatus.get("bots")),
                "fights": (rep or {}).get("fights", (bstatus.get("arena") or {}).get("fights")),
                "fights_per_min": (rep or {}).get("fights_per_min", (bstatus.get("arena") or {}).get("fights_per_min")),
                "game_speed": (rep or {}).get("game_speed", (bstatus.get("arena") or {}).get("game_speed")),
                "mem_mb": (rep or {}).get("mem_mb"), "path": p.relative_to(root).as_posix(),
                "serverOutBytes": out_bytes, "serverOutModified": out_modified, "folderModified": folder_modified,
                "lastLogLines": out_lines[-18:], "logTail": out_txt[-16000:], "loadedModsMentioned": mods,
                "bobStatus": {k: v for k, v in bstatus.items() if k in ("wall", "tps", "mspt", "bots", "arena", "state", "mode", "uptime", "fights")},
                "properties": parse_properties(p / "server.properties"), "journal": journal,
                "hasServerOut": out_path.exists(), "hasBobStatus": bstatus_path.exists(),
            })
    instances.sort(key=lambda x: (not x["active"], -(x.get("folderModified") or 0)))

    # Training + runtime model metadata. Never return binary model bytes.
    model_items = []
    for model_root, origin in ((base / "models", "training"), (root / "run" / "bob-models", "runtime")):
        if not model_root.exists(): continue
        for p in model_root.glob("**/*"):
            if not p.is_file() or p.suffix.lower() not in (".json", ".pt", ".pth", ".bin", ".onnx", ".model"): continue
            try:
                st = p.stat()
                obj = safe_json(p, {}) if p.suffix.lower() == ".json" else {}
                model_items.append({"name": p.name, "path": p.relative_to(root).as_posix(), "origin": origin,
                                    "size": st.st_size, "modified": st.st_mtime, "version": obj.get("version"),
                                    "kind": obj.get("kind"), "params": obj.get("params"), "games": obj.get("games"),
                                    "mean_diff": obj.get("mean_diff")})
            except OSError: pass
    model_items.sort(key=lambda x: (x["origin"], -x["modified"]))

    state = safe_json(base / "state.json", {}) or {}
    cal = safe_json(base / "calibration.json", {}) or {}
    cfg = safe_json(base / "config.json", {}) or {}
    current_train = status.get("last_train") or state.get("last_train")
    league = state.get("league") or {}
    op_history = []
    for line in tail_text(base / "dashboard-operations.jsonl", 50000, 60).splitlines():
        try: op_history.append(json.loads(line))
        except ValueError: pass
    league_results = []
    results_path = base / "league" / "results.jsonl"
    for line in tail_text(results_path, 150000, 40).splitlines():
        try:
            r = json.loads(line)
            result = r.get("result") or {}
            bots = result.get("bots") or []
            league_results.append({"time": r.get("time"), "seed": result.get("seed"), "secs": result.get("secs"),
                                   "champion": r.get("champion"), "challenger": r.get("challenger"), "bots": bots,
                                   "decided": result.get("decided"), "winner": result.get("winner")})
        except ValueError: continue

    logs = []
    for f in (base / "bobtrain.log", base / "dashboard-operation.log"):
        for line in tail_text(f, 60000, 90).splitlines():
            if line.strip(): logs.append({"source": f.name, "line": line})
    logs = logs[-140:]
    try:
        report = tail_text(base / "report.md", 35000, 400)
    except Exception: report = ""
    try:
        controller_age = max(0, time.time() - float(ctl.get("started_at", 0))) if ctl.get("started_at") else None
    except (TypeError, ValueError): controller_age = None
    journal_total = dir_journal_stats(journal_root)
    return {
        "root": str(root), "time": now_iso(), "trainer": {"active": active, "action": active_cmd, "source": active_source,
            "pid": active_pid, "controller": ctl, "statusFresh": status_fresh, "statusAgeSec": round(status_age, 1) if status_age is not None else None,
            "status": status, "controllerAgeSec": round(controller_age, 1) if controller_age is not None else None,
            "externalProcesses": [{k: v for k, v in x.items() if k != "commandLine"} for x in external]},
        "machine": status.get("machine") or {}, "instances": instances, "instanceCountActive": sum(1 for i in instances if i["active"]),
        "config": cfg, "calibration": cal, "state": state, "lastTrain": current_train,
        "models": model_items, "league": {"champion": league.get("champion"), "challenger": league.get("challenger"),
            "games": league.get("games", []), "history": league.get("history", [])[-40:], "triedCount": len(league.get("tried", {}))},
        "leagueResults": league_results, "operationHistory": op_history[-30:], "journal": journal_total,
        "logs": logs[-100:], "report": report,
        "paths": {"status": (base / "status.json").relative_to(root).as_posix(),
                  "config": (base / "config.json").relative_to(root).as_posix(),
                  "calibration": (base / "calibration.json").relative_to(root).as_posix(),
                  "report": (base / "report.md").relative_to(root).as_posix(),
                  "models": (base / "models").relative_to(root).as_posix(),
                  "runtimeModels": (root / "run" / "bob-models").relative_to(root).as_posix()},
    }


def run_worker(root: Path, action: str, quick: bool = False) -> int:
    """Independent worker: creates the training venv if needed, runs an allow-listed trainer command."""
    root = root.resolve(); base = root / "runs" / "train"; base.mkdir(parents=True, exist_ok=True)
    control_path = base / "dashboard-controller.json"
    stop_request = base / "DASHBOARD_STOP_REQUEST"
    stop_file = base / "STOP"
    start_time = time.time()
    record = {"action": action, "phase": "preparing", "started_at": start_time, "started_iso": now_iso(),
              "worker_pid": os.getpid(), "child_pid": None, "quick": bool(quick)}
    write_json_atomic(control_path, record)
    append_jsonl(base / "dashboard-operations.jsonl", {"event": "started", **record})
    log_path = base / "dashboard-operation.log"
    trainer_script = root / "scripts" / "train" / "bobtrain.py"
    requirements = root / "scripts" / "train" / "requirements.txt"
    if not trainer_script.is_file():
        record.update({"phase": "failed", "finished_at": time.time(), "exit_code": 2, "error": f"Trainer fehlt: {trainer_script}"})
        write_json_atomic(control_path, record); append_jsonl(base / "dashboard-operations.jsonl", {"event": "finished", **record}); return 2
    venv_python = root / "runs" / "train" / "venv" / ("Scripts/python.exe" if os.name == "nt" else "bin/python")
    try:
        if not venv_python.is_file():
            record["phase"] = "creating venv"; write_json_atomic(control_path, record)
            venv_python.parent.parent.mkdir(parents=True, exist_ok=True)
            subprocess.run([sys.executable, "-m", "venv", str(venv_python.parent.parent)], cwd=root, check=True, timeout=180)
        # Required dependencies only (numpy + psutil). PyTorch/CUDA remains an optional separate step.
        with log_path.open("a", encoding="utf-8") as out:
            out.write(f"\n[{now_iso()}] Dashboard operation: {action}; preparing Python environment\n")
            out.flush()
            pip = subprocess.run([str(venv_python), "-m", "pip", "install", "--disable-pip-version-check", "-q", "-r", str(requirements)],
                                 cwd=root, stdout=out, stderr=subprocess.STDOUT, timeout=900)
            if pip.returncode != 0: raise RuntimeError(f"Dependency setup failed (pip exit {pip.returncode}); see dashboard-operation.log")
            cmd_action = "run" if action == "start" else "calibrate" if action in ("calibrate", "calibrate-quick") else "train"
            args = [str(venv_python), "-u", str(trainer_script), cmd_action]
            if action == "calibrate-quick" or (action == "calibrate" and quick): args.append("--quick")
            # STOP_REQUEST is consumed only once bobtrain has entered its run loop; this avoids the race
            # where bobtrain.run() removes STOP while the venv is still being prepared.
            record.update({"phase": "running", "command": cmd_action, "child_started_at": time.time()})
            write_json_atomic(control_path, record)
            out.write(f"[{now_iso()}] EXEC: {' '.join(args)}\n"); out.flush()
            kwargs = {"cwd": str(root), "stdout": out, "stderr": subprocess.STDOUT}
            if os.name == "nt": kwargs["creationflags"] = getattr(subprocess, "CREATE_NO_WINDOW", 0)
            else: kwargs["start_new_session"] = True
            child = subprocess.Popen(args, **kwargs)
            record["child_pid"] = child.pid; write_json_atomic(control_path, record)
            last_status_mtime = 0.0
            while child.poll() is None:
                if action == "start" and stop_request.exists():
                    sp = base / "status.json"
                    try: mt = sp.stat().st_mtime
                    except OSError: mt = 0
                    try: fresh_status = safe_json(sp, {}) or {}
                    except Exception: fresh_status = {}
                    # Only create STOP after this run has published fresh status or its own start event.
                    entered_loop = mt >= float(record.get("child_started_at", start_time)) and mt > last_status_mtime
                    if entered_loop:
                        stop_file.touch(exist_ok=True)
                        try: stop_request.unlink()
                        except OSError: pass
                        with log_path.open("a", encoding="utf-8") as logf: logf.write(f"[{now_iso()}] Safe STOP request delivered to active trainer.\n")
                    last_status_mtime = mt
                time.sleep(1)
            code = int(child.returncode or 0)
    except Exception as e:
        code = 1
        with log_path.open("a", encoding="utf-8") as out: out.write(f"[{now_iso()}] ERROR: {type(e).__name__}: {e}\n")
        record["error"] = f"{type(e).__name__}: {e}"
    finally:
        try: stop_request.unlink()
        except OSError: pass
    record.update({"phase": "completed" if code == 0 else "failed", "finished_at": time.time(), "finished_iso": now_iso(), "exit_code": code,
                   "worker_pid": os.getpid()})
    write_json_atomic(control_path, record)
    append_jsonl(base / "dashboard-operations.jsonl", {"event": "finished", **record})
    return code


class DashboardHandler(BaseHTTPRequestHandler):
    server_version = "BobTrainingDashboard/2.0"

    @property
    def root(self) -> Path:
        return self.server.repo_root

    def _send(self, code: int, obj, content_type="application/json; charset=utf-8"):
        body = obj if isinstance(obj, bytes) else json.dumps(obj, ensure_ascii=False).encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", content_type)
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Cache-Control", "no-store")
        self.send_header("X-Content-Type-Options", "nosniff")
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self):
        path = urlparse(self.path).path
        if path in ("/", "/dashboard", "/index.html"):
            try: body = DASHBOARD.read_bytes()
            except OSError: self.send_error(500, "Dashboard HTML fehlt neben dem Python-Script"); return
            self._send(200, body, "text/html; charset=utf-8"); return
        if path == "/api/data": self._send(200, collect(self.root)); return
        if path == "/api/runtime": self._send(200, runtime_payload(self.root)); return
        if path == "/health": self._send(200, {"ok": True, "root": str(self.root.resolve())}); return
        self.send_error(404)

    def do_POST(self):
        path = urlparse(self.path).path
        if path != "/api/control": self.send_error(404); return
        origin = self.headers.get("Origin", "")
        if origin and not origin.startswith(("http://127.0.0.1:", "http://localhost:")):
            self._send(403, {"ok": False, "error": "Control API is local-only."}); return
        if not self.headers.get("Content-Type", "").lower().startswith("application/json"):
            self._send(415, {"ok": False, "error": "Expected application/json."}); return
        try:
            length = int(self.headers.get("Content-Length", "0"))
            if length < 1 or length > 4096: raise ValueError("invalid request size")
            body = json.loads(self.rfile.read(length).decode("utf-8"))
        except Exception:
            self._send(400, {"ok": False, "error": "Invalid JSON request."}); return
        action = str(body.get("action", ""))
        allowed = {"start", "stop", "calibrate", "calibrate-quick", "train-once"}
        if action not in allowed:
            self._send(400, {"ok": False, "error": "Unknown action."}); return
        base = self.root / "runs" / "train"; base.mkdir(parents=True, exist_ok=True)
        ctrl = controller_info(self.root)
        externals = get_external_cached(self.root, force=True)
        active_managed = bool(ctrl.get("workerAlive"))
        active_external = bool(externals)
        status_path = base / "status.json"
        status_mtime = status_path.stat().st_mtime if status_path.exists() else 0
        status_fresh = bool(status_mtime and time.time() - status_mtime <= 45)
        status_fresh_active = status_fresh
        if ctrl.get("phase") in ("completed", "failed") and ctrl.get("finished_at") and status_mtime <= float(ctrl.get("finished_at", 0)) + 1:
            status_fresh_active = False
        run_active = active_managed and ctrl.get("action") == "start" or any(x.get("command") == "run" for x in externals) or (status_fresh_active and not active_managed and not active_external)
        any_active = active_managed or active_external or status_fresh_active
        if action == "stop":
            if active_managed and ctrl.get("action") == "start":
                # If the trainer loop is already publishing fresh status, deliver STOP now; otherwise
                # queue the request so startup setup cannot consume it accidentally.
                if status_fresh_active and status_path.stat().st_mtime >= float(ctrl.get("child_started_at", ctrl.get("started_at", 0)) or 0):
                    (base / "STOP").touch(exist_ok=True)
                    response = "STOP-Datei gesetzt; Bob beendet Server und Training sauber."
                else:
                    (base / "DASHBOARD_STOP_REQUEST").touch(exist_ok=True)
                    response = "Stop vorgemerkt; sobald der Trainer gestartet ist, wird das sichere STOP-Signal gesetzt."
                self._send(200, {"ok": True, "message": response}); return
            if any(x.get("command") == "run" for x in externals) or (status_fresh_active and not active_managed and not active_external):
                (base / "STOP").touch(exist_ok=True)
                self._send(200, {"ok": True, "message": "STOP-Datei für den erkannten aktiven Trainer gesetzt."}); return
            self._send(409, {"ok": False, "error": "Kein aktiver kontinuierlicher Trainer erkannt. Es wurde nichts gestoppt."}); return
        if action == "start" and any_active:
            self._send(409, {"ok": False, "error": f"Ein Trainings-/Kalibrierungsprozess läuft bereits ({ctrl.get('action') or (externals[0].get('command') if externals else 'Status-Heartbeat')})."}); return
        if action in ("calibrate", "calibrate-quick", "train-once") and any_active:
            self._send(409, {"ok": False, "error": "Bitte erst den aktiven Trainingsprozess sauber stoppen, bevor eine Kalibrierung oder ein einmaliger Trainingsschritt startet."}); return
        if action == "start":
            for name in ("STOP", "DASHBOARD_STOP_REQUEST"):
                try: (base / name).unlink()
                except OSError: pass
        # Write a provisional marker before spawning the worker so double-clicks cannot launch two trainers.
        pending = {"action": action, "phase": "starting", "started_at": time.time(), "started_iso": now_iso(),
                   "worker_pid": None, "child_pid": None, "quick": action == "calibrate-quick"}
        write_json_atomic(base / "dashboard-controller.json", pending)
        worker_args = [sys.executable, str(Path(__file__).resolve()), "--worker-action", action, "--root", str(self.root.resolve())]
        if action == "calibrate-quick": worker_args.append("--worker-quick")
        kwargs = {"cwd": str(self.root), "stdin": subprocess.DEVNULL, "stdout": subprocess.DEVNULL, "stderr": subprocess.DEVNULL}
        if os.name == "nt": kwargs["creationflags"] = getattr(subprocess, "CREATE_NO_WINDOW", 0)
        else: kwargs["start_new_session"] = True
        try:
            proc = subprocess.Popen(worker_args, **kwargs)
            self._send(202, {"ok": True, "message": f"{action} gestartet. Setup-/Trainingsdetails erscheinen im Dashboard.", "workerPid": proc.pid}); return
        except OSError as e:
            pending.update({"phase": "failed", "finished_at": time.time(), "finished_iso": now_iso(), "error": str(e), "exit_code": 1})
            write_json_atomic(base / "dashboard-controller.json", pending)
            append_jsonl(base / "dashboard-operations.jsonl", {"event": "finished", **pending})
            self._send(500, {"ok": False, "error": f"Worker konnte nicht gestartet werden: {e}"}); return

    def log_message(self, fmt, *args):
        if os.environ.get("BOB_DASH_VERBOSE"): super().log_message(fmt, *args)


def main():
    parser = argparse.ArgumentParser(description="Local Bob Training Dashboard and training controller")
    parser.add_argument("--root", default=str(Path(__file__).resolve().parent), help="Repository root")
    parser.add_argument("--host", default="127.0.0.1", help="Bind address; default exposes only this PC")
    parser.add_argument("--port", type=int, default=8765)
    parser.add_argument("--no-browser", action="store_true")
    parser.add_argument("--worker-action", choices=("start", "calibrate", "calibrate-quick", "train-once"), help=argparse.SUPPRESS)
    parser.add_argument("--worker-quick", action="store_true", help=argparse.SUPPRESS)
    args = parser.parse_args()
    root = Path(args.root).expanduser().resolve()
    if not root.exists(): parser.error(f"Repo root does not exist: {root}")
    if args.worker_action:
        sys.exit(run_worker(root, args.worker_action, quick=args.worker_quick))
    if not DASHBOARD.is_file(): parser.error(f"Dashboard HTML not found next to this Python script: {DASHBOARD}")
    if args.host not in ("127.0.0.1", "localhost", "::1"):
        parser.error("For safety, the controller only permits loopback binding (127.0.0.1 / localhost / ::1).")
    httpd = ThreadingHTTPServer((args.host, args.port), DashboardHandler)
    httpd.repo_root = root
    url = f"http://{args.host}:{args.port}/"
    print(f"Bob Training Dashboard + Controller: {url}")
    print(f"Repo root: {root}")
    print("Controls: start/stop trainer, full/quick calibration, train models once.")
    print("Safety: local-only, allow-listed actions; Stop uses the trainer's STOP marker (no process-wide Java kill).")
    if not args.no_browser: threading.Timer(0.7, lambda: webbrowser.open(url)).start()
    try: httpd.serve_forever(poll_interval=0.5)
    except KeyboardInterrupt: print("\nStopping dashboard server…")
    finally: httpd.server_close()

if __name__ == "__main__": main()
