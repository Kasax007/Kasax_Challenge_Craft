#!/bin/sh
# A self-play match on a fresh world, headless: two to four bots, each a Lockout team of its own,
# each with its own knobs (see BotParams: guardian=0|1, quick=<seconds>, ...):
#   scripts/bot/versus.sh <seed> <seconds> <difficulty> <Name[:k=v,...]> <Name[:k=v,...]> [...]
# Prints the [BOTVS] result line (JSON). The game ends when it is decided (a lead the other cannot
# catch up) or after <seconds>. Pace: TICK_RATE (20, the real game's, by default; see bench.sh on
# why faster paces change how the bots play); SIM=1 makes the pace safe to raise ("sprint").
# DIRECT=1 starts the server JVM itself from the launch files of the last "gradlew runServer" (one
# JVM instead of three - the classes must be built: "gradlew classes"), in RUNDIR (default run/):
# two matches at once from one checkout, each with its own RUNDIR, SERVER_PORT and RCON_PORT.
# VIEW: chunks loaded round the bots (24, a player's sight); SIMDIST: chunks simulated (VIEW).
set -e
SEED=${1:?seed}; SECS=${2:?seconds}; DIFF=${3:?difficulty}; shift 3
[ $# -ge 2 ] || { echo "two to four sides" >&2; exit 2; }
ROOT=$(cd "$(dirname "$0")/../.." && pwd)
HERE=$ROOT/scripts/bot
RUN=${RUNDIR:-$ROOT/run}
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
set_prop simulation-distance ${SIMDIST:-${VIEW:-24}}
rm -rf $RUN/versus_$SEED
cd $ROOT
if ! "${JAVA_HOME:-/nonexistent}/bin/java" -version 2>&1 | grep -q "version \"25"; then [ -d /opt/jdk25 ] && JAVA_HOME=$(ls -d /opt/jdk25/*/ | head -1) && export JAVA_HOME; fi
ARGS=$ROOT/build/loom-cache/argFiles/runServer
if [ "${DIRECT:-0}" = 1 ]; then
    [ -f "$ARGS" ] || { echo "no $ARGS: run gradlew runServer once" >&2; exit 1; }
    (cd $RUN && CHALLENGECRAFT_BOT_SIM=${SIM:-0} CHALLENGECRAFT_TEST_CHALLENGES=40 setsid nohup "$JAVA_HOME/bin/java" ${JVM_OPTS:--Xmx5G} \
        -Dfabric.dli.config=$ROOT/.gradle/loom-cache/launch.cfg -Dfabric.dli.env=server \
        -Dfabric.dli.main=net.fabricmc.loader.impl.launch.knot.KnotServer @$ARGS \
        --sun-misc-unsafe-memory-access=allow --enable-native-access=ALL-UNNAMED -Dfile.encoding=UTF-8 \
        net.fabricmc.devlaunchinjector.Main nogui > $LOG 2>&1 < /dev/null & echo $! > $RUN/server.pid)
else
    CHALLENGECRAFT_BOT_SIM=${SIM:-0} CHALLENGECRAFT_TEST_CHALLENGES=40 setsid nohup sh ./gradlew runServer --no-daemon --offline > $LOG 2>&1 < /dev/null &
    echo $! > $RUN/server.pid
fi
# The server and everything it started (gradle: the wrapper, its daemon, the server).
kill_tree() {
    for c in $(pgrep -P $1); do kill_tree $c; done
    kill $1 2>/dev/null || true
}
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
# (Saving thousands of chunks at the stop can overflow the stack in the game's own chunk code and
# hang the server - the world is thrown away anyway: whatever still runs after 20 s is ended.)
for i in $(seq 1 20); do kill -0 $(cat $RUN/server.pid) 2>/dev/null || break; sleep 1; done
kill -0 $(cat $RUN/server.pid) 2>/dev/null && kill_tree $(cat $RUN/server.pid)
# (The world is played out: only the log is kept - a league leaves sixty worlds otherwise.)
[ "${KEEP_WORLD:-0}" = 1 ] || rm -rf $RUN/versus_$SEED
grep "\[BOTVS\] result" $LOG | sed 's/.*\[BOTVS\] result //'
