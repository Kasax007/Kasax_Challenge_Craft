#!/usr/bin/env python3
"""The Bob-Autopilot on a real Fabric server, here in the container - as it runs on a rented one.

    flock <build.lock> python3 scripts/bot/autopilot_check.py --root /tmp/x/bisect --games 2 --minutes 10

Sets up a server folder as a panel does (Fabric's server launcher, Fabric API and the Challenge
Craft jar from build/libs in mods/, eula.txt), starts it once normally (Fabric downloads Minecraft,
the server makes its world), then starts build/libs/bob-autopilot.jar there as the panel would and
drives it through its console: waits for --games league games, has it pack an export, stops it.
With --arena-minutes it then runs an arena alone for that long. At the end: what it wrote, the
games' results, the memory and threads of each game's Java, the disk used - and whether all checks
held (exit 0) or not.

Build first: sh ./gradlew jar bobAutopilotJar. One game is one Minecraft JVM beside the
autopilot's small one: hold the build lock while this runs.
"""
import argparse
import glob
import gzip
import json
import os
import shutil
import subprocess
import sys
import threading
import time
import urllib.request
import zipfile

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.dirname(os.path.dirname(HERE))
LAUNCHER_URL = "https://meta.fabricmc.net/v2/versions/loader/26.3/{loader}/1.1.2/server/jar"


def log(msg):
    print(time.strftime("%H:%M:%S"), msg, flush=True)


class Proc:
    """A process with a console: lines in, lines out (kept, and echoed with a prefix)."""

    def __init__(self, cmd, cwd, prefix, quiet=False):
        self.p = subprocess.Popen(cmd, cwd=cwd, stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                                  text=True, bufsize=1, encoding="utf-8", errors="replace")
        self.lines = []
        self.prefix, self.quiet = prefix, quiet
        threading.Thread(target=self._read, daemon=True).start()

    def _read(self):
        for line in self.p.stdout:
            self.lines.append(line.rstrip("\n"))
            if not self.quiet:
                print(self.prefix + line.rstrip("\n"), flush=True)

    def send(self, text):
        log(">> " + text)
        self.p.stdin.write(text + "\n")
        self.p.stdin.flush()

    def wait_for(self, test, timeout, every=2):
        t0 = time.time()
        while time.time() - t0 < timeout:
            if test():
                return True
            if self.p.poll() is not None:
                return test()
            time.sleep(every)
        return False


def jar_of(pattern):
    found = sorted(glob.glob(pattern))
    if not found:
        sys.exit("not found: " + pattern + " (build first: sh ./gradlew jar bobAutopilotJar)")
    return found[-1]


def setup(root, args):
    os.makedirs(os.path.join(root, "mods"), exist_ok=True)
    launcher = os.path.join(root, "fabric-server-launch.jar")
    if args.fabric_launcher:
        shutil.copy2(args.fabric_launcher, launcher)
    elif not os.path.exists(launcher):
        url = LAUNCHER_URL.format(loader=args.loader)
        log("downloading Fabric's server launcher: " + url)
        urllib.request.urlretrieve(url, launcher)
    for old in glob.glob(os.path.join(root, "mods", "*.jar")):
        os.remove(old)
    shutil.copy2(jar_of(os.path.join(REPO, "build", "libs", "challengecraft-*[0-9].jar")), os.path.join(root, "mods"))
    api = args.fabric_api or jar_of(os.path.expanduser(
        "~/.gradle/caches/modules-2/files-2.1/net.fabricmc.fabric-api/fabric-api/*+26.3/*/fabric-api-*+26.3.jar"))
    shutil.copy2(api, os.path.join(root, "mods"))
    shutil.copy2(os.path.join(REPO, "build", "libs", "bob-autopilot.jar"), root)
    with open(os.path.join(root, "eula.txt"), "w") as f:
        f.write("eula=true\n")


def first_start(root, java):
    """The server started normally once, as the user does before switching to the autopilot."""
    if os.path.isdir(os.path.join(root, ".fabric", "server")) and os.path.isdir(os.path.join(root, "world")):
        return True
    log("first normal start of the Fabric server (downloads Minecraft, makes the world)")
    p = Proc([java, "-Xmx2G", "-jar", "fabric-server-launch.jar", "nogui"], root, "  [server] ", quiet=True)
    ok = p.wait_for(lambda: any("Done (" in l for l in p.lines), 900)
    if ok:
        p.send("stop")
    p.p.wait(timeout=180)
    if not ok:
        print("\n".join(p.lines[-40:]))
    return ok


def status(root):
    try:
        with open(os.path.join(root, "bob-autopilot", "status.json"), encoding="utf-8") as f:
            return json.load(f)
    except (OSError, ValueError):
        return None


def results(root):
    try:
        with open(os.path.join(root, "bob-autopilot", "league", "results.jsonl"), encoding="utf-8") as f:
            return [json.loads(l) for l in f if l.strip()]
    except OSError:
        return []


def du(path):
    total = 0
    for d, _, files in os.walk(path):
        for n in files:
            p = os.path.join(d, n)
            if not os.path.islink(p):
                try:
                    total += os.path.getsize(p)
                except OSError:
                    pass
    return total


def run_autopilot(root, java, config, until, timeout, samples, label):
    with open(os.path.join(root, "bob-autopilot.properties"), "w", encoding="utf-8") as f:
        f.write(config)
    log(f"autopilot ({label}): " + config.replace("\n", "  "))
    # (As a panel starts its server jar: its heap, -jar, the jar.)
    ap = Proc([java, "-Xms128M", "-Xmx1024M", "-jar", "bob-autopilot.jar"], root, "  [autopilot] ")
    if not ap.wait_for(lambda: any(")! For help, type " in l for l in ap.lines), 60):
        log("the autopilot did not report itself up")
        return ap, False
    t0 = time.time()
    peak_disk = 0
    while time.time() - t0 < timeout and ap.p.poll() is None:
        st = status(root)
        if st:
            for s in st.get("servers", []):
                if s.get("rss_mb"):
                    samples.append((label, s["name"], s.get("state"), s.get("rss_mb"), s.get("threads"), s.get("speed")))
        peak_disk = max(peak_disk, du(os.path.join(root, "bob-autopilot")))
        if until(st):
            break
        time.sleep(10)
    done = until(status(root))
    ap.send("status")
    time.sleep(2)
    ap.send("export")
    ap.wait_for(lambda: any("Paket zum Herunterladen" in l for l in ap.lines), 120)
    ap.send("stop")
    try:
        ap.p.wait(timeout=120)
    except subprocess.TimeoutExpired:
        log("the autopilot did not end on stop: killed")
        ap.p.kill()
    log(f"{label}: peak disk of bob-autopilot/ {peak_disk / 2**20:.0f} MB, ran {(time.time() - t0) / 60:.1f} min")
    return ap, done


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--root", required=True, help="the server folder to set up (as a panel's /home/container)")
    ap.add_argument("--games", type=int, default=2)
    ap.add_argument("--minutes", type=int, default=10, help="game minutes of each league game")
    ap.add_argument("--arena-minutes", type=int, default=0, help="then an arena alone for this long (0: none)")
    ap.add_argument("--memory", type=int, default=4096)
    ap.add_argument("--loader", default="0.20.1")
    ap.add_argument("--fabric-launcher", help="Fabric's server launcher jar (else downloaded)")
    ap.add_argument("--fabric-api", help="the Fabric API jar (else from the Gradle cache)")
    ap.add_argument("--java", default=os.path.join(os.environ.get("JAVA_HOME", "/opt/jdk25/jdk-25.0.5+8"), "bin", "java"))
    args = ap.parse_args()
    root = os.path.abspath(args.root)
    os.makedirs(root, exist_ok=True)
    setup(root, args)
    checks = []

    def check(name, ok, detail=""):
        checks.append((name, ok))
        log(("OK   " if ok else "FAIL ") + name + (": " + detail if detail else ""))

    check("first normal start", first_start(root, args.java))
    samples = []
    before = len(results(root))
    config = (f"league_games=1\narena_servers=0\ngame_minutes={args.minutes}\ngame_memory_mb={args.memory}\nexport_hours=0\n"
              "machine=container\n")
    proc, done = run_autopilot(root, args.java, config, lambda st: len(results(root)) >= before + args.games,
                               args.games * (args.minutes * 60 * 2 + 600), samples, "league")
    rs = results(root)[before:]
    check(f"{args.games} league games played", len(rs) >= args.games, f"{len(rs)} results")
    for r in rs:
        res = r["result"]
        bots = ", ".join(f"{b['name']} {b['tiles']} tiles {b['deaths']} deaths first {b['first']} s" for b in res["bots"])
        log(f"  game {r['game']} seed {res['seed']}: {res['secs']} game s in {r['real_secs']} real s "
            f"({res['secs'] / max(1, r['real_secs']):.1f}x), counted {r['counted']}: {bots}")
    check("results counted for the duel", all(r["counted"] for r in rs))
    check("seeds differ (a fresh world each game)", len({r['result']['seed'] for r in rs}) == len(rs))
    home = os.path.join(root, "bob-autopilot")
    check("league state written", os.path.exists(os.path.join(home, "league", "state.json")))
    logs = sorted(glob.glob(os.path.join(home, "games", "*.log.gz")))
    check("game logs kept", len(logs) >= len(rs), f"{len(logs)} logs")
    if logs:
        with gzip.open(logs[-1], "rt", encoding="utf-8", errors="replace") as f:
            text = f.read()
        check("a game log has the game", "[BOTVS] result" in text and "[BOTVS] start" in text,
              f"{len(text) / 2**20:.1f} MB unpacked, {os.path.getsize(logs[-1]) / 2**20:.2f} MB gzipped")
    journal = glob.glob(os.path.join(home, "journal", "*.jsonl"))
    kinds = {}
    for j in journal:
        with open(j, encoding="utf-8", errors="replace") as f:
            for line in f:
                try:
                    t = json.loads(line).get("t")
                except ValueError:
                    continue
                kinds[t] = kinds.get(t, 0) + 1
    check("journal written", sum(kinds.values()) > 0, f"{len(journal)} files, {du(os.path.join(home, 'journal')) / 2**20:.1f} MB, {kinds}")
    check("worlds removed after the games", not os.path.exists(os.path.join(home, "work", "liga-1", "world")))
    zips = sorted(glob.glob(os.path.join(home, "export", "*.zip")))
    check("export packed", bool(zips))
    if zips:
        with zipfile.ZipFile(zips[-1]) as z:
            names = z.namelist()
        check("export has results, journal, logs", "league/results.jsonl" in names and any(n.startswith("journal/") for n in names)
              and any(n.startswith("games/") for n in names), f"{os.path.getsize(zips[-1]) / 2**20:.1f} MB, {len(names)} files")
    check("autopilot ended on stop", proc.p.returncode == 0, f"exit {proc.p.returncode}")
    check("the server's own world untouched", os.path.exists(os.path.join(root, "world", "level.dat")))
    if args.arena_minutes:
        n0 = len(samples)
        config = (f"league_games=0\narena_servers=1\narena_restart_minutes=5\nexport_hours=0\nmachine=container\n")
        t_end = time.time() + args.arena_minutes * 60
        proc, _ = run_autopilot(root, args.java, config, lambda st: time.time() > t_end, args.arena_minutes * 60 + 300, samples, "arena")
        fights = [s for s in samples[n0:]]
        st = status(root) or {}
        check("arena ran", any("Arena laeuft" in l for l in proc.lines), f"{len(fights)} samples")
        arena_lines = [l for l in proc.lines if "Arena" in l or "arena" in l]
        for l in arena_lines[-6:]:
            log("  " + l)
        check("arena autopilot ended on stop", proc.p.returncode == 0, f"exit {proc.p.returncode}")
    if samples:
        log("memory and threads of the games' Java (label, server, state, RSS MB, threads, speed):")
        by = {}
        for s in samples:
            by.setdefault((s[0], s[1]), []).append(s)
        for k, xs in by.items():
            rss = [x[3] for x in xs if x[3]]
            thr = [x[4] for x in xs if x[4]]
            sp = [x[5] for x in xs if x[5]]
            log(f"  {k[0]} {k[1]}: RSS max {max(rss)} MB, threads max {max(thr) if thr else '-'}, speed mean "
                f"{sum(sp) / len(sp) if sp else 0:.1f}x over {len(xs)} samples")
    log(f"bob-autopilot/ now {du(home) / 2**20:.0f} MB")
    failed = [n for n, ok in checks if not ok]
    log("ALL CHECKS HELD" if not failed else "FAILED: " + ", ".join(failed))
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
