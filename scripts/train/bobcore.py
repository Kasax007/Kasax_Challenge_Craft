"""The trainer's plumbing: where things are, the Java and the launch spec, the server instances, the
machine's load.

Everything the trainer keeps lives under runs/train/ in the project (not in git):
  instances/<name>/   one dedicated server each (its world, its log, bob-status.json)
  journal/<name>/     the decisions Bob wrote down there (JSON lines), what the models learn from
  models/             the models in use (cover.json, goal.json, champion.json) and candidates
  league/             the self-play matches (results.jsonl)
  status.json, report.md, calibration.json/.md, bobtrain.log
"""
import glob
import json
import logging
import os
import platform
import random
import re
import shutil
import socket
import subprocess
import sys
import threading
import time

ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
WINDOWS = platform.system() == "Windows"

log = logging.getLogger("bobtrain")


class Paths:
    def __init__(self, base=None):
        self.base = os.path.abspath(base or os.path.join(ROOT, "runs", "train"))
        self.instances = os.path.join(self.base, "instances")
        self.journal = os.path.join(self.base, "journal")
        self.models = os.path.join(self.base, "models")
        self.history = os.path.join(self.models, "history")
        self.league = os.path.join(self.base, "league")
        self.stop_file = os.path.join(self.base, "STOP")
        self.status = os.path.join(self.base, "status.json")
        self.report = os.path.join(self.base, "report.md")
        self.calibration = os.path.join(self.base, "calibration.json")
        self.calibration_md = os.path.join(self.base, "calibration.md")
        self.state = os.path.join(self.base, "state.json")
        self.logfile = os.path.join(self.base, "bobtrain.log")
        # Where Bob plays from IntelliJ (runClient): the models it uses there, and its journal
        # (the user's own games are data too).
        self.client_models = os.path.join(ROOT, "run", "bob-models")
        self.client_journal = os.path.join(ROOT, "run", "bob-journal")
        for d in (self.base, self.instances, self.journal, self.models, self.history, self.league):
            os.makedirs(d, exist_ok=True)


def setup_logging(paths, verbose=False):
    fmt = logging.Formatter("%(asctime)s %(levelname)s %(message)s", "%H:%M:%S")
    log.setLevel(logging.DEBUG)
    fh = logging.FileHandler(paths.logfile, encoding="utf-8")
    fh.setFormatter(fmt)
    fh.setLevel(logging.DEBUG)
    ch = logging.StreamHandler(sys.stdout)
    ch.setFormatter(fmt)
    ch.setLevel(logging.DEBUG if verbose else logging.INFO)
    log.handlers[:] = [fh, ch]


def write_json(path, obj):
    tmp = path + ".tmp"
    with open(tmp, "w", encoding="utf-8") as f:
        json.dump(obj, f, indent=1, ensure_ascii=False)
    os.replace(tmp, path)


def read_json(path, default=None):
    try:
        with open(path, encoding="utf-8") as f:
            return json.load(f)
    except (OSError, ValueError):
        return default


# ---- Java 25 and the launch spec ---------------------------------------------------------------

def java_version(java):
    try:
        out = subprocess.run([java, "-version"], capture_output=True, text=True, timeout=30)
    except (OSError, subprocess.SubprocessError):
        return None
    m = re.search(r'version "(\d+)', out.stderr + out.stdout)
    return int(m.group(1)) if m else None


def find_java(min_version=25):
    """A Java of at least {min_version}: BOB_JAVA, JAVA_HOME, then the usual places (IntelliJ's .jdks too)."""
    exe = "java.exe" if WINDOWS else "java"
    candidates = []
    if os.environ.get("BOB_JAVA"):
        candidates.append(os.environ["BOB_JAVA"])
    if os.environ.get("JAVA_HOME"):
        candidates.append(os.path.join(os.environ["JAVA_HOME"], "bin", exe))
    home = os.path.expanduser("~")
    patterns = [os.path.join(home, ".jdks", "*")]
    if WINDOWS:
        for pf in {os.environ.get("ProgramFiles", r"C:\Program Files"), os.environ.get("ProgramW6432", r"C:\Program Files")}:
            for vendor in ("Java", "Eclipse Adoptium", "Microsoft", "Zulu", "Amazon Corretto", "BellSoft", "OpenJDK", "Semeru"):
                patterns.append(os.path.join(pf, vendor, "*"))
    elif platform.system() == "Darwin":
        patterns += ["/Library/Java/JavaVirtualMachines/*/Contents/Home", os.path.join(home, "Library/Java/JavaVirtualMachines/*/Contents/Home")]
    else:
        patterns += ["/usr/lib/jvm/*", "/opt/jdk*/*", "/opt/jdk*", "/opt/java/*"]
    for pat in patterns:
        for d in sorted(glob.glob(pat), reverse=True):
            candidates.append(os.path.join(d, "bin", exe))
    which = shutil.which("java")
    if which:
        candidates.append(which)
    seen = set()
    for c in candidates:
        c = os.path.abspath(c)
        if c in seen or not os.path.isfile(c):
            continue
        seen.add(c)
        v = java_version(c)
        if v is not None and v >= min_version:
            return c
    return None


def launch_spec(java, rebuild=True):
    """Builds the classes and writes the launch spec with Gradle (bobLaunchSpec); returns it."""
    spec_file = os.path.join(ROOT, "build", "bob", "launch-spec.json")
    if rebuild or not os.path.exists(spec_file):
        env = dict(os.environ)
        env["JAVA_HOME"] = os.path.dirname(os.path.dirname(java))
        gradlew = [os.path.join(ROOT, "gradlew.bat")] if WINDOWS else ["sh", os.path.join(ROOT, "gradlew")]
        log.info("Baue Bob (gradlew bobLaunchSpec) ...")
        out = subprocess.run(gradlew + ["bobLaunchSpec", "--console=plain", "-q"], cwd=ROOT, env=env, capture_output=True, text=True)
        if out.returncode != 0:
            log.error("Gradle ist fehlgeschlagen:\n%s\n%s", out.stdout[-3000:], out.stderr[-3000:])
            raise SystemExit(2)
    spec = read_json(spec_file)
    if not spec:
        raise SystemExit("keine Launch-Spec: " + spec_file)
    return spec


def snapshot(spec, paths):
    """Bob's own classes copied aside, and the spec pointed at the copy: a rebuild in IntelliJ while
    the training runs must not change the code under servers that are running (or start some on the
    old code and some on the new)."""
    build = os.path.normcase(os.path.join(ROOT, "build"))
    code = os.path.join(paths.base, "code")
    stamp = time.strftime("%Y%m%d-%H%M%S")
    target = os.path.join(code, stamp)
    cp = []
    for i, entry in enumerate(spec["classpath"]):
        if os.path.isdir(entry) and os.path.normcase(os.path.abspath(entry)).startswith(build):
            dst = os.path.join(target, "%d-%s" % (i, os.path.basename(entry)))
            shutil.copytree(entry, dst)
            cp.append(dst)
        else:
            cp.append(entry)
    # (Older copies away - the servers of this run use the new one.)
    for old in glob.glob(os.path.join(code, "*")):
        if os.path.abspath(old) != os.path.abspath(target):
            shutil.rmtree(old, ignore_errors=True)
    out = dict(spec)
    out["classpath"] = cp
    out["snapshot"] = stamp
    return out


_ports_lock = threading.Lock()
_ports_taken = set()


def free_port(start):
    """The first port from {start} on that nothing listens on (and no other server here was given)."""
    with _ports_lock:
        p = start
        while p < start + 2000:
            if p not in _ports_taken:
                with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
                    try:
                        s.bind(("127.0.0.1", p))
                        _ports_taken.add(p)
                        return p
                    except OSError:
                        pass
            p += 1
    raise RuntimeError("no free port from %d" % start)


def release_port(p):
    with _ports_lock:
        _ports_taken.discard(p)


# ---- the machine ---------------------------------------------------------------------------

class Machine:
    """The machine's load, sampled in the background: CPU over the last minute, free memory."""

    def __init__(self):
        import psutil
        self.psutil = psutil
        self.cores = psutil.cpu_count(logical=True) or 4
        self.physical = psutil.cpu_count(logical=False) or self.cores
        self.total_mb = psutil.virtual_memory().total >> 20
        self.samples = []
        self.lock = threading.Lock()
        psutil.cpu_percent(interval=None)
        self.thread = threading.Thread(target=self._run, daemon=True)
        self.thread.start()

    def _run(self):
        while True:
            time.sleep(5)
            cpu = self.psutil.cpu_percent(interval=None)
            avail = self.psutil.virtual_memory().available >> 20
            with self.lock:
                self.samples.append((time.time(), cpu, avail))
                cutoff = time.time() - 600
                self.samples = [s for s in self.samples if s[0] >= cutoff]

    def cpu(self, seconds=60):
        with self.lock:
            xs = [s[1] for s in self.samples if s[0] >= time.time() - seconds]
        return sum(xs) / len(xs) if xs else self.psutil.cpu_percent(interval=1)

    def free_mb(self):
        return self.psutil.virtual_memory().available >> 20

    def describe(self):
        cpu_name = platform.processor() or platform.machine()
        return {"os": platform.platform(), "cpu": cpu_name, "cores_logical": self.cores, "cores_physical": self.physical,
                "ram_mb": self.total_mb, "python": platform.python_version()}

    def process_mb(self, pid):
        try:
            p = self.psutil.Process(pid)
            mb = p.memory_info().rss
            for c in p.children(recursive=True):
                try:
                    mb += c.memory_info().rss
                except self.psutil.Error:
                    pass
            return mb >> 20
        except self.psutil.Error:
            return 0


# ---- a server instance ---------------------------------------------------------------------

class Instance:
    """One dedicated server: started from the launch spec, watched through its log and status file."""

    def __init__(self, name, kind, spec, paths, memory_mb, view=6, simulation=4, sim=True, challenge=None, search_threads=4):
        self.name, self.kind, self.spec, self.paths = name, kind, spec, paths
        self.search_threads = search_threads
        self.memory_mb, self.view, self.simulation, self.sim, self.challenge = memory_mb, view, simulation, sim, challenge
        self.dir = os.path.join(paths.instances, name)
        self.journal = os.path.join(paths.journal, name)
        self.proc = None
        self.port = self.rcon_port = None
        self.rcon = None
        self.started = None
        self.ready_at = None
        self.job = None
        self.out = None
        self.seed = None

    # (Paths into the Java argument file: forward slashes, quoted - Windows paths with spaces too.)
    @staticmethod
    def _arg(a):
        return '"' + str(a).replace("\\", "/").replace('"', '\\"') + '"'

    def start(self, seed=None, extra_props=None):
        from rcon import Rcon
        os.makedirs(self.dir, exist_ok=True)
        os.makedirs(self.journal, exist_ok=True)
        self.seed = seed if seed is not None else random.randrange(-2 ** 63, 2 ** 63)
        # (A fresh world for each start: new ground, and no world growing without end.)
        shutil.rmtree(os.path.join(self.dir, "world"), ignore_errors=True)
        self.port = free_port(26100 + 7 * (abs(hash(self.name)) % 400))
        self.rcon_port = free_port(self.port + 1)
        props = {
            "level-name": "world", "level-seed": str(self.seed), "online-mode": "false", "enable-rcon": "true",
            "rcon.password": "test", "rcon.port": str(self.rcon_port), "server-port": str(self.port), "spawn-protection": "0",
            "difficulty": "hard", "view-distance": str(self.view), "simulation-distance": str(self.simulation),
            "max-players": "32", "motd": "Bob training " + self.name, "sync-chunk-writes": "false", "server-ip": "127.0.0.1",
            "allow-flight": "true", "max-tick-time": "-1",
        }
        props.update(extra_props or {})
        with open(os.path.join(self.dir, "server.properties"), "w", encoding="utf-8") as f:
            f.write("".join("%s=%s\n" % kv for kv in props.items()))
        with open(os.path.join(self.dir, "eula.txt"), "w", encoding="utf-8") as f:
            f.write("eula=true\n")
        # (Extra server mods - Lithium, C2ME, FerriteCore - dropped into runs/train/mods: copied
        # into each server's mods folder, which the Fabric loader reads in the dev launch too.)
        mods = os.path.join(self.paths.base, "mods")
        target = os.path.join(self.dir, "mods")
        shutil.rmtree(target, ignore_errors=True)
        if os.path.isdir(mods):
            os.makedirs(target, exist_ok=True)
            for jar in glob.glob(os.path.join(mods, "*.jar")):
                shutil.copy2(jar, target)
        for stale in ("bob-status.json",):
            try:
                os.remove(os.path.join(self.dir, stale))
            except OSError:
                pass
        sep = ";" if WINDOWS else ":"
        args = ["-Xmx%dM" % self.memory_mb, "-Xms%dM" % min(self.memory_mb, 1024), "-XX:+UseG1GC", "-Dfile.encoding=UTF-8"]
        args += self.spec["jvmArgs"] + ["-cp", sep.join(self.spec["classpath"]), self.spec["mainClass"], "nogui"]
        argfile = os.path.join(self.dir, "java-args.txt")
        with open(argfile, "w", encoding="utf-8") as f:
            f.write("\n".join(self._arg(a) for a in args) + "\n")
        env = dict(os.environ)
        env.update({"BOB_PARENT_PID": str(os.getpid()), "BOB_JOURNAL": self.journal, "BOB_MODELS": self.paths.models,
                    "BOB_STATUS_DIR": self.dir, "CHALLENGECRAFT_BOT_SIM": "1" if self.sim else "0",
                    # (Each bot's path search on a thread of its own: in the arena they otherwise
                    # queue for two threads, and the server waits for each in turn.)
                    "CHALLENGECRAFT_BOT_SEARCH_THREADS": str(self.search_threads)})
        env.pop("CHALLENGECRAFT_TEST_CHALLENGES", None)
        if self.challenge:
            env["CHALLENGECRAFT_TEST_CHALLENGES"] = str(self.challenge)
        self.out = open(os.path.join(self.dir, "server.out"), "w", encoding="utf-8", errors="replace")
        kwargs = {}
        if WINDOWS:
            kwargs["creationflags"] = 0x08000000 | 0x00000200  # CREATE_NO_WINDOW | CREATE_NEW_PROCESS_GROUP
        else:
            kwargs["start_new_session"] = True
        self.proc = subprocess.Popen([self.spec["java"], "@" + argfile], cwd=self.dir, stdout=self.out, stderr=subprocess.STDOUT,
                                     stdin=subprocess.DEVNULL, env=env, **kwargs)
        self.started = time.time()
        self.ready_at = None
        self.rcon = Rcon(self.rcon_port)
        log.info("%s: Server startet (%s, Port %d, Seed %d)", self.name, self.kind, self.port, self.seed)

    def alive(self):
        return self.proc is not None and self.proc.poll() is None

    def log_text(self, tail=200000):
        try:
            with open(os.path.join(self.dir, "server.out"), encoding="utf-8", errors="replace") as f:
                f.seek(0, os.SEEK_END)
                size = f.tell()
                f.seek(max(0, size - tail))
                return f.read()
        except OSError:
            return ""

    def wait_ready(self, timeout=300):
        t0 = time.time()
        while time.time() - t0 < timeout:
            if not self.alive():
                return False
            text = self.log_text(20000)
            if "Done (" in text and "For help, type" in text:
                try:
                    self.rcon.command("list")
                    self.ready_at = time.time()
                    return True
                except Exception:
                    pass
            time.sleep(2)
        return False

    def command(self, cmd):
        return self.rcon.command(cmd)

    def status(self):
        st = read_json(os.path.join(self.dir, "bob-status.json"))
        if st:
            st["age"] = time.time() - st.get("wall", 0) / 1000.0
        return st

    def stop(self, timeout=40):
        if self.proc is None:
            return
        if self.alive():
            try:
                self.rcon.command("stop")
            except Exception:
                pass
            t0 = time.time()
            while self.alive() and time.time() - t0 < timeout:
                time.sleep(1)
            if self.alive():
                log.warning("%s: haengt beim Stoppen, wird beendet", self.name)
                self.kill()
        if self.rcon:
            self.rcon.close()
        if self.out:
            self.out.close()
            self.out = None
        self.proc = None
        for p in (self.port, self.rcon_port):
            if p:
                release_port(p)

    def kill(self):
        if self.proc is None:
            return
        try:
            import psutil
            parent = psutil.Process(self.proc.pid)
            for c in parent.children(recursive=True):
                c.kill()
            parent.kill()
        except Exception:
            try:
                self.proc.kill()
            except OSError:
                pass
        try:
            self.proc.wait(timeout=20)
        except subprocess.TimeoutExpired:
            pass
