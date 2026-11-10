#!/bin/sh
# Bob alone in a Lockout game on a fresh world, headless (at the real pace, or TICK_RATE):
#   scripts/bot/bench.sh <seed> [seconds=600] [difficulty=hard] [knobs, e.g. "quick=60"]
# Prints the [BOTBENCH] report (milestones, tiles, idle time, failures) and the [BOTOPEN] line
# (the opening's stopwatch as JSON, see OpeningClock; scripts/bot/opening.py adds them up).
# SIM=1: the path searches count in game ticks (CHALLENGECRAFT_BOT_SIM), so "sprint" is safe -
# and the pace then defaults to it. DIRECT=1 starts the server JVM itself from the launch files of
# the last "gradlew runServer" (one JVM instead of three; "gradlew classes" first), in RUNDIR
# (default run/), with SERVER_PORT and RCON_PORT: two benches at once from one checkout.
set -e
SEED=${1:?seed}; SECS=${2:-600}; DIFF=${3:-hard}; KNOBS=${4:-}
ROOT=$(cd "$(dirname "$0")/../.." && pwd)
HERE=$ROOT/scripts/bot
RUN=${RUNDIR:-$ROOT/run}
LOG=$RUN/bench_$SEED.log
mkdir -p $RUN
echo "eula=true" > $RUN/eula.txt
PROPS=$RUN/server.properties
touch $PROPS
set_prop() { grep -q "^$1=" $PROPS && sed -i "s|^$1=.*|$1=$2|" $PROPS || echo "$1=$2" >> $PROPS; }
set_prop level-name bench_$SEED
set_prop level-seed $SEED
set_prop online-mode false
set_prop enable-rcon true
set_prop rcon.password test
set_prop rcon.port ${RCON_PORT:-25575}
set_prop server-port ${SERVER_PORT:-25565}
set_prop spawn-protection 0
# The world's difficulty, the same as Bob's (or WORLD_DIFFICULTY): never left to whatever the
# server.properties of this checkout happened to hold - one said easy, the other hard, and a
# "paired" comparison of two builds was easy against hard for a whole day.
WORLD=${WORLD_DIFFICULTY:-$DIFF}
set_prop difficulty $WORLD
# Bob plays with a player's sight: 24 chunks to see, the same to simulate.
set_prop view-distance ${VIEW:-24}
set_prop simulation-distance ${SIMDIST:-${VIEW:-24}}
rm -rf $RUN/bench_$SEED
cd $ROOT
# A JDK 25 (the one on PATH, or JAVA_HOME if set).
if ! "${JAVA_HOME:-/nonexistent}/bin/java" -version 2>&1 | grep -q "version \"25"; then [ -d /opt/jdk25 ] && JAVA_HOME=$(ls -d /opt/jdk25/*/ | head -1) && export JAVA_HOME; fi
ARGS=$ROOT/build/loom-cache/argFiles/runServer
if [ "${DIRECT:-0}" = 1 ]; then
    [ -f "$ARGS" ] || { echo "no $ARGS: run gradlew runServer once" >&2; exit 1; }
    (cd $RUN && CHALLENGECRAFT_BOT_SIM=${SIM:-0} CHALLENGECRAFT_BOARD_SEED=${BOARD_SEED:-$SEED} CHALLENGECRAFT_FULL_BOARD=1 CHALLENGECRAFT_TEST_CHALLENGES=40 \
        setsid nohup "$JAVA_HOME/bin/java" ${JVM_OPTS:--Xmx4G} \
        -Dfabric.dli.config=$ROOT/.gradle/loom-cache/launch.cfg -Dfabric.dli.env=server \
        -Dfabric.dli.main=net.fabricmc.loader.impl.launch.knot.KnotServer @$ARGS \
        --sun-misc-unsafe-memory-access=allow --enable-native-access=ALL-UNNAMED -Dfile.encoding=UTF-8 \
        net.fabricmc.devlaunchinjector.Main nogui > $LOG 2>&1 < /dev/null & echo $! > $RUN/server.pid)
else
    CHALLENGECRAFT_BOT_SIM=${SIM:-0} CHALLENGECRAFT_BOARD_SEED=${BOARD_SEED:-$SEED} CHALLENGECRAFT_FULL_BOARD=1 CHALLENGECRAFT_TEST_CHALLENGES=40 \
        setsid nohup sh ./gradlew runServer --no-daemon --offline > $LOG 2>&1 < /dev/null &
    echo $! > $RUN/server.pid
fi
# The server and everything it started (gradle: the wrapper, its daemon, the server).
# (kill_tree <pid> [signal]: TERM by default.)
kill_tree() {
    for c in $(pgrep -P $1); do kill_tree $c $2; done
    kill ${2:--TERM} $1 2>/dev/null || true
}
# Ends the server after its stop: TERM after 20 s, KILL 10 s later - a JVM stuck saving chunks in its
# own shutdown ignores TERM, and alive it keeps the slot's ports: the next games there fail to bind
# them (no result; two seeds of an integration league were lost so).
end_server() {
    PID=$(cat $RUN/server.pid)
    for i in $(seq 1 20); do kill -0 $PID 2>/dev/null || return 0; sleep 1; done
    kill_tree $PID
    for i in $(seq 1 10); do kill -0 $PID 2>/dev/null || return 0; sleep 1; done
    kill_tree $PID -KILL
}
for i in $(seq 1 120); do sleep 5; grep -q "Done (" $LOG && break; grep -q "BUILD FAILED" $LOG && { tail -20 $LOG; exit 1; }; done
python3 $HERE/rcon.py "challengecraft_bot spawn Bob" > /dev/null
sleep 3
# The game's pace: TICK_RATE ticks a second, the real game's 20 by default. "sprint" runs as
# fast as the machine allows - quicker, but without SIM=1 Bob's path searches take real
# milliseconds, so the faster the machine, the more game ticks each one costs him: the same build
# on the same eight boards died 3 times at 1.5 times the real pace and 18 times at 2.3 times.
RATE=${TICK_RATE:-20}
[ "${SIM:-0}" = 1 ] && RATE=${TICK_RATE:-sprint}
if [ "$RATE" = sprint ]; then PACE="tick sprint $((SECS * 20 + 400))"; else PACE="tick rate $RATE"; fi
BENCH="challengecraft_bot bench Bob $SECS $DIFF"
[ -n "$KNOBS" ] && BENCH="$BENCH $KNOBS"
python3 $HERE/rcon.py "difficulty $WORLD" "$PACE" "$BENCH" > /dev/null
for i in $(seq 1 4320); do sleep 5; grep -q "\[BOTBENCH\] idle:" $LOG && break; kill -0 $(cat $RUN/server.pid) 2>/dev/null || break; done
python3 $HERE/rcon.py "challengecraft_bot coverage Bob" > /dev/null 2>&1 || true
python3 $HERE/rcon.py stop > /dev/null 2>&1 || true
# (Saving thousands of chunks at the stop can hang the server: whatever still runs after 20 s is ended.)
end_server
# (The world is played out: only the log is kept, unless KEEP_WORLD=1.)
[ "${KEEP_WORLD:-0}" = 1 ] || rm -rf $RUN/bench_$SEED
grep "\[BOTBENCH\]\|\[BOTOPEN\]" $LOG | sed 's/.*\[BOTBENCH\] //; s/.*\[BOTOPEN\] /OPENING /'
