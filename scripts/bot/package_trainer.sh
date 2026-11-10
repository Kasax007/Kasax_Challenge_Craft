#!/bin/bash
# Packs the Bob-Trainer app (round 3) into a zip for the user's laptop:
#   scripts/bot/package_trainer.sh <out.zip> [download-folder]
# Builds the mod jar and bob-trainer.jar from this tree, takes Fabric API from the Gradle cache,
# Lithium, FerriteCore and the Fabric server launcher from fetch_trainer_mods.py (checked).
set -euo pipefail
REPO="$(cd "$(dirname "$0")/../.." && pwd)"
OUT="$(realpath -m "$1")"
DL="$(realpath -m "${2:-$REPO/build/trainer-downloads}")"
cd "$REPO"
sh ./gradlew jar bobTrainerJar -q --offline
python3 -I scripts/bot/fetch_trainer_mods.py "$DL"
VERSION="$(sed -n 's/^mod_version=//p' gradle.properties)"
FAPI="$(ls ~/.gradle/caches/modules-2/files-2.1/net.fabricmc.fabric-api/fabric-api/0.161.0+26.3/*/fabric-api-0.161.0+26.3.jar | head -n 1)"
WORK="$(mktemp -d)"
STAGE="$WORK/Bob-Trainer"
mkdir -p "$STAGE/app" "$STAGE/server/mods"
cp build/libs/bob-trainer.jar scripts/trainer/start-unix.sh scripts/trainer/start-windows.ps1 "$STAGE/app/"
cp "scripts/trainer/Bob-Trainer starten.bat" "scripts/trainer/Bob-Trainer starten.command" scripts/trainer/bob-trainer.sh \
   scripts/trainer/LIESMICH.md scripts/trainer/THIRD-PARTY.txt "$STAGE/"
cp "build/libs/challengecraft-$VERSION.jar" "$FAPI" "$DL"/lithium-fabric-*.jar "$DL"/ferritecore-*.jar "$STAGE/server/mods/"
cp "$DL"/fabric-server-mc.26.3-loader.0.19.5-launcher.1.1.2.jar "$STAGE/server/"
# (Windows reads these with CRLF line ends.)
sed -i 's/\r\?$/\r/' "$STAGE/Bob-Trainer starten.bat" "$STAGE/app/start-windows.ps1"
chmod +x "$STAGE/app/start-unix.sh" "$STAGE/Bob-Trainer starten.command" "$STAGE/bob-trainer.sh"
rm -f "$OUT"
mkdir -p "$(dirname "$OUT")"
(cd "$WORK" && zip -qr -X "$OUT" Bob-Trainer)
rm -rf "$WORK"
echo "$OUT $(du -h "$OUT" | cut -f1) sha1 $(sha1sum "$OUT" | cut -d' ' -f1)"
