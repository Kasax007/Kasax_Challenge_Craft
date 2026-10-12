#!/bin/sh
# A self-play match on a fresh world, headless: two to four bots, each a Lockout team of its own,
# each with its own knobs (see BotParams: guardian=0|1, quick=<seconds>, ...):
#   scripts/bot/versus.sh <seed> <seconds> <difficulty> <Name[:k=v,...]> <Name[:k=v,...]> [...]
# Prints the [BOTVS] result line (JSON). The game ends when it is decided (a lead the other cannot
# catch up) or after <seconds>. Pace: TICK_RATE (20, the real game's, by default; see bench.sh on
# why faster paces change how the bots play). Ports: SERVER_PORT, RCON_PORT (two at once need two
# checkouts, each with its own run directory).
set -e
SEED=${1:?seed}; SECS=${2:?seconds}; DIFF=${3:?difficulty}; shift 3
[ $# -ge 2 ] || { echo "two to four sides" >&2; exit 2; }
ROOT=$(cd "$(dirname "$0")/../.." && pwd)
HERE=$ROOT/scripts/bot
RUN=$ROOT/run
LOG=$RUN/versus_$SEED.log
mkdir -p $RUN
echo "eula=true" > $RUN/eula.txt
PROPS=$RUN/server.properties
touch $PROPS
set_prop() { grep -q "^$1=" $PROPS && sed -i "s|^$1=.*|$1=$2|" $PROPS || echo "$1=$2" >> $PROPS; }
set_prop level-name versus_$SEED
set_prop level-seed $SEED
set_prop online-mode false
set_prop enable-rcon true
set_prop rcon.password test
set_prop rcon.port ${RCON_PORT:-25575}
set_prop server-port ${SERVER_PORT:-25565}
set_prop spawn-protection 0
WORLD=${WORLD_DIFFICULTY:-$DIFF}
set_prop difficulty $WORLD
set_prop view-distance ${VIEW:-24}
set_prop simulation-distance ${VIEW:-24}
rm -rf $RUN/versus_$SEED
cd $ROOT
if ! "${JAVA_HOME:-/nonexistent}/bin/java" -version 2>&1 | grep -q "version \"25"; then [ -d /opt/jdk25 ] && JAVA_HOME=$(ls -d /opt/jdk25/*/ | head -1) && export JAVA_HOME; fi
CHALLENGECRAFT_BOT_SIM=${SIM:-0} CHALLENGECRAFT_TEST_CHALLENGES=40 setsid nohup sh ./gradlew runServer --no-daemon --offline > $LOG 2>&1 < /dev/null &
for i in $(seq 1 120); do sleep 5; grep -q "Done (" $LOG && break; grep -q "BUILD FAILED" $LOG && { tail -20 $LOG; exit 1; }; done
SIDES=""
for side in "$@"; do
    name=${side%%:*}
    python3 $HERE/rcon.py "challengecraft_bot spawn $name" > /dev/null
    SIDES="$SIDES $side"
done
sleep 3
RATE=${TICK_RATE:-20}
# (SIM=1: the searches count in game ticks, so the machine's own pace changes nothing - "sprint" is safe then.)
[ "${SIM:-0}" = 1 ] && RATE=${TICK_RATE:-sprint}
if [ "$RATE" = sprint ]; then PACE="tick sprint $((SECS * 20 + 400))"; else PACE="tick rate $RATE"; fi
python3 $HERE/rcon.py "difficulty $WORLD" "$PACE" "challengecraft_bot versus $SECS $DIFF$SIDES" > /dev/null
for i in $(seq 1 4320); do sleep 5; grep -q "\[BOTVS\] result" $LOG && break; done
python3 $HERE/rcon.py stop > /dev/null 2>&1 || true
sleep 5
grep "\[BOTVS\] result" $LOG | sed 's/.*\[BOTVS\] result //'
