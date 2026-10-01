#!/bin/sh
# Navigation benchmark on a fresh world: Bob walks to COUNT random surface spots MIN..MAX blocks
# away (peaceful, daytime, fast-forwarded): scripts/bot/navbench.sh <seed> [count=30] [min=40] [max=250]
SEED=${1:?seed}; COUNT=${2:-30}; MIN=${3:-40}; MAX=${4:-250}
HERE=$(cd "$(dirname "$0")" && pwd)
ROOT=$(cd "$HERE/../.." && pwd)
sh $HERE/server.sh $SEED > /dev/null || exit 1
LOG=$ROOT/run/bench_$SEED.log
python3 $HERE/rcon.py "difficulty peaceful" "time set day" "gamerule advance_time false" "challengecraft_bot spawn Bob" > /dev/null
sleep 3
python3 $HERE/rcon.py "give Bob minecraft:stone_pickaxe" "give Bob minecraft:stone_axe" "give Bob minecraft:stone_shovel" \
  "give Bob minecraft:cobblestone 64" "give Bob minecraft:dirt 64" "give Bob minecraft:bread 32" > /dev/null
python3 $HERE/rcon.py "tick sprint 2000000" "challengecraft_bot navbench Bob $COUNT $MIN $MAX" > /dev/null
for i in $(seq 1 2160); do sleep 5; grep -q "\[NAVBENCH\] summary" $LOG && break; done
python3 $HERE/rcon.py stop > /dev/null 2>&1 || true
sleep 5
grep "\[NAVBENCH\]" $LOG | sed 's/.*\[NAVBENCH\] //'
