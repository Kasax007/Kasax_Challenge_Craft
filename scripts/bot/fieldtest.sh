#!/bin/sh
# Board goals one at a time, each from the same start and kit (Nether goals in the Nether), the
# Lockout brain playing for that tile alone; headless and fast-forwarded:
#   scripts/bot/fieldtest.sh <seed> <goal ids,... | category> [seconds each=600]
# Prints the [FIELDTEST] lines (claimed or not, time, lowest health, deaths) and a summary.
set -e
SEED=${1:?seed}; GOALS=${2:?goals}; SECS=${3:-600}
ROOT=$(cd "$(dirname "$0")/../.." && pwd)
HERE=$ROOT/scripts/bot
RUN=$ROOT/run
LOG=$RUN/fieldtest_$SEED.log
mkdir -p $RUN
echo "eula=true" > $RUN/eula.txt
PROPS=$RUN/server.properties
touch $PROPS
set_prop() { grep -q "^$1=" $PROPS && sed -i "s|^$1=.*|$1=$2|" $PROPS || echo "$1=$2" >> $PROPS; }
set_prop level-name fieldtest_$SEED
set_prop level-seed $SEED
set_prop online-mode false
set_prop enable-rcon true
set_prop rcon.password test
set_prop rcon.port ${RCON_PORT:-25575}
set_prop server-port ${SERVER_PORT:-25565}
set_prop spawn-protection 0
set_prop view-distance 24
set_prop simulation-distance 24
set_prop difficulty hard
rm -rf $RUN/fieldtest_$SEED
cd $ROOT
if ! "${JAVA_HOME:-/nonexistent}/bin/java" -version 2>&1 | grep -q "version \"25"; then [ -d /opt/jdk25 ] && JAVA_HOME=$(ls -d /opt/jdk25/*/ | head -1) && export JAVA_HOME; fi
N=$(echo "$GOALS" | tr ',' '\n' | wc -l)
[ "$N" -lt 2 ] && N=30
CHALLENGECRAFT_FULL_BOARD=1 CHALLENGECRAFT_TEST_CHALLENGES=40 setsid nohup sh ./gradlew runServer --no-daemon > $LOG 2>&1 < /dev/null &
for i in $(seq 1 120); do sleep 5; grep -q "Done (" $LOG && break; grep -q "BUILD FAILED" $LOG && { tail -20 $LOG; exit 1; }; done
python3 $HERE/rcon.py "difficulty hard" "challengecraft_bot spawn Bob" > /dev/null
sleep 3
python3 $HERE/rcon.py "tick sprint $((SECS * 20 * N + 2000))" "challengecraft_bot fieldtest Bob \"$GOALS\" $SECS" > /dev/null
for i in $(seq 1 8640); do sleep 5; grep -q "\[FIELDTEST\] summary" $LOG && break; done
python3 $HERE/rcon.py stop > /dev/null 2>&1 || true
sleep 5
grep "\[FIELDTEST\]" $LOG | grep -v "  .* minute" | sed 's/.*\[FIELDTEST\] //'
