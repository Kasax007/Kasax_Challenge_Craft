#!/usr/bin/env python3
"""Downloads what the Bob-Trainer bundles besides its own code, checked:

* Lithium mc26.3-0.26.3-fabric and FerriteCore 9.0.0-fabric from Modrinth (api.modrinth.com):
  the file of that version, its sha512 compared with the API's.
* The Fabric server launcher for 26.3 / loader 0.19.5 / installer 1.1.2 (meta.fabricmc.net): a
  jar whose manifest starts net.fabricmc.installer.ServerLauncher.

Usage: python3 -I scripts/bot/fetch_trainer_mods.py <folder>   (files already there and right are kept)
"""
import hashlib
import json
import os
import sys
import urllib.request
import zipfile

MODS = {"lithium": "mc26.3-0.26.3-fabric", "ferrite-core": "9.0.0-fabric"}
LAUNCHER = ("https://meta.fabricmc.net/v2/versions/loader/26.3/0.19.5/1.1.2/server/jar",
            "fabric-server-mc.26.3-loader.0.19.5-launcher.1.1.2.jar")


def get(url):
    req = urllib.request.Request(url, headers={"User-Agent": "bob-trainer-packager"})
    with urllib.request.urlopen(req, timeout=120) as r:
        return r.read()


def main():
    out = sys.argv[1]
    os.makedirs(out, exist_ok=True)
    for project, version in MODS.items():
        versions = json.loads(get("https://api.modrinth.com/v2/project/%s/version" % project))
        v = [x for x in versions if x["version_number"] == version][0]
        f = ([x for x in v["files"] if x.get("primary")] or v["files"])[0]
        path = os.path.join(out, f["filename"])
        want = f["hashes"]["sha512"]
        if not (os.path.exists(path) and hashlib.sha512(open(path, "rb").read()).hexdigest() == want):
            data = get(f["url"])
            if hashlib.sha512(data).hexdigest() != want:
                sys.exit("sha512 mismatch for " + f["filename"])
            open(path, "wb").write(data)
        print("ok", f["filename"], "sha512", want[:16] + "...")
    url, name = LAUNCHER
    path = os.path.join(out, name)
    if not os.path.exists(path):
        open(path, "wb").write(get(url))
    main_class = zipfile.ZipFile(path).read("META-INF/MANIFEST.MF").decode()
    if "net.fabricmc.installer.ServerLauncher" not in main_class:
        sys.exit("not the Fabric server launcher: " + name)
    print("ok", name, os.path.getsize(path), "bytes")


if __name__ == "__main__":
    main()
