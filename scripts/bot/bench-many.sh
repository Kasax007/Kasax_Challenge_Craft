#!/bin/sh
# Bob on several worlds, one after the other; a table of the results:
#   scripts/bot/bench-many.sh [seconds=600] [difficulty=hard] seed...
SECS=${1:-600}; DIFF=${2:-hard}; shift 2
ROOT=$(cd "$(dirname "$0")/../.." && pwd)
printf "%-8s %5s %6s %6s %6s %6s %5s %5s %5s  %s\n" seed tiles stone iron bucket nether idle fail death claimed
for SEED in "$@"; do
  sh $ROOT/scripts/bot/bench.sh $SEED $SECS $DIFF > /dev/null 2>&1
  L=$ROOT/run/bench_$SEED.log
  v() { grep "\[BOTBENCH\] $1:" $L | tail -1 | sed "s/.*$1: //; s/ s$//; s/ (.*//"; }
  TILES=$(grep "\[BOTBENCH\] tiles claimed:" $L | sed 's/.*claimed: //; s/ (.*//')
  CLAIMED=$(grep "\[BOTBENCH\] tiles claimed:" $L | sed 's/.*(//; s/)$//')
  IDLE=$(grep "\[BOTBENCH\] idle:" $L | sed 's/.*idle: \([0-9]*\) s, failed tasks: \([0-9]*\), deaths: \([0-9]*\)/\1 \2 \3/')
  printf "%-8s %5s %6s %6s %6s %6s %5s %5s %5s  %s\n" $SEED "$TILES" "$(v 'stone pickaxe')" "$(v 'iron ingot')" "$(v bucket)" "$(v nether)" $IDLE "$CLAIMED"
done
