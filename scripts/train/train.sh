#!/bin/sh
# Bobs Training (Linux/macOS): laeuft, bis ./stop.sh (oder Strg+C).
set -e
cd "$(dirname "$0")/../.."
VENV=runs/train/venv
if [ ! -x "$VENV/bin/python" ]; then
    mkdir -p runs/train
    python3 -m venv "$VENV"
fi
"$VENV/bin/python" -m pip install --disable-pip-version-check -q -r scripts/train/requirements.txt
exec "$VENV/bin/python" scripts/train/bobtrain.py run "$@"
