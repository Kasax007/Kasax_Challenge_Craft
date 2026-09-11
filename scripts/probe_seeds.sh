#!/usr/bin/env bash
# Measures world seeds against the real 26.2 worldgen, one server boot per seed.
#
# Why this exists: seed lists on the web are overwhelmingly written for older versions and copied
# forward without rechecking. Worldgen changes between versions, so those claims do not transfer.
# A daily seed ships to every player and there is no fixing it afterwards, so the descriptions in
# DailyChallenges are taken from here.
#
# Usage:  scripts/probe_seeds.sh 1 42 1234 ...
#         scripts/probe_seeds.sh --random 30
# Results are appended as one JSON object per line to run/challengecraft_seed_probe.json.
set -u

cd "$(dirname "$0")/.." || exit 1
RUN_DIR="run"
OUT="$RUN_DIR/challengecraft_seed_probe.json"

if [ "${1:-}" = "--random" ]; then
    count="${2:-20}"
    seeds=()
    for ((i = 0; i < count; i++)); do
        seeds+=("$((RANDOM * 32768 + RANDOM))")
    done
else
    seeds=("$@")
fi

if [ ${#seeds[@]} -eq 0 ]; then
    echo "Keine Seeds angegeben." >&2
    exit 1
fi

touch "$RUN_DIR/challengecraft_seed_probe.on"
echo "Vermesse ${#seeds[@]} Seed(s)."

for seed in "${seeds[@]}"; do
    if grep -q "\"seed\":$seed," "$OUT" 2>/dev/null; then
        echo "  $seed — schon vermessen, übersprungen"
        continue
    fi
    # A fresh world per seed; level-seed is only read when there is nothing to load.
    rm -rf "$RUN_DIR/world"
    python - "$seed" <<'PY'
import io, re, sys
p = "run/server.properties"
s = io.open(p, encoding="utf-8").read()
s = re.sub(r"(?m)^level-seed=.*$", "level-seed=" + sys.argv[1], s)
io.open(p, "w", encoding="utf-8", newline="").write(s)
PY
    printf '  %s ... ' "$seed"
    if ./gradlew runServer -q >/dev/null 2>&1; then
        printf 'ok\n'
    else
        printf 'FEHLER\n'
    fi
done

rm -f "$RUN_DIR/challengecraft_seed_probe.on"
echo "Fertig. Ergebnisse: $OUT"
