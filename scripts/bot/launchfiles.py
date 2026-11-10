#!/usr/bin/env python3
"""Writes build/loom-cache/argFiles/runServer from build/bob/launch-spec.json, without starting a server.

versus.sh and bench.sh with DIRECT=1 (one JVM per server, as league.py --parallel uses them) start
the server from that argument file, which Loom otherwise writes only on a first "gradlew runServer".
In a fresh bench worktree, from its root:

    sh ./gradlew classes bobLaunchSpec -q --offline
    python3 scripts/bot/launchfiles.py
"""
import json
import os

spec = json.load(open("build/bob/launch-spec.json"))
os.makedirs("build/loom-cache/argFiles", exist_ok=True)
with open("build/loom-cache/argFiles/runServer", "w") as f:
    f.write("-classpath\n" + ":".join(spec["classpath"]) + "\n")
print("build/loom-cache/argFiles/runServer:", len(spec["classpath"]), "classpath entries")
