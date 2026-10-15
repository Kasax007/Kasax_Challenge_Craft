#!/bin/sh
# Beendet das laufende Training sauber.
cd "$(dirname "$0")/../.."
mkdir -p runs/train
touch runs/train/STOP
echo "STOP gesetzt - das Training beendet sich in wenigen Sekunden."
