#!/bin/sh
# Bob alone in a Lockout game on a fresh world, headless and fast-forwarded:
#   scripts/bot/bench.sh <seed> [seconds=600] [difficulty=hard]
# Prints the [BOTBENCH] report (milestones, tiles, idle time, failures).
set -e
SEED=${1:?seed}; SECS=${2:-600}; DIFF=${3:-hard}
ROOT=$(cd "$(dirname "$0")/../.." && pwd)
HERE=$ROOT/scripts/bot
RUN=$ROOT/run
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
set_prop rcon.port 25575
set_prop spawn-protection 0
rm -rf $RUN/bench_$SEED
cd $ROOT
# A JDK 25 (the one on PATH, or JAVA_HOME if set).
if ! "${JAVA_HOME:-/nonexistent}/bin/java" -version 2>&1 | grep -q "version \"25"; then [ -d /opt/jdk25 ] && JAVA_HOME=$(ls -d /opt/jdk25/*/ | head -1) && export JAVA_HOME; fi
CHALLENGECRAFT_FULL_BOARD=1 CHALLENGECRAFT_TEST_CHALLENGES=40 setsid nohup sh ./gradlew runServer --no-daemon > $LOG 2>&1 < /dev/null &
for i in $(seq 1 120); do sleep 5; grep -q "Done (" $LOG && break; grep -q "BUILD FAILED" $LOG && { tail -20 $LOG; exit 1; }; done
python3 $HERE/rcon.py "challengecraft_bot spawn Bob" > /dev/null
sleep 3
python3 $HERE/rcon.py "tick sprint $((SECS * 20 + 400))" "challengecraft_bot bench Bob $SECS $DIFF" > /dev/null
for i in $(seq 1 4320); do sleep 5; grep -q "\[BOTBENCH\] idle:" $LOG && break; done
python3 $HERE/rcon.py stop > /dev/null 2>&1 || true
sleep 5
grep "\[BOTBENCH\]" $LOG | sed 's/.*\[BOTBENCH\] //'
