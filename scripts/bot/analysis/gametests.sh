#!/bin/sh
# Runs gametest filters one after another in a checkout, one line per result:
#   scripts/bot/analysis/gametests.sh <checkout> <out file> "challengecraft-gametest:bot_stunt_tests_*creeper*" ...
D=$1; OUT=$2; shift 2
export JAVA_HOME=$(ls -d /opt/jdk25/*/ | head -1)
cd $D
: > $OUT
for f in "$@"; do
  rm -rf build/run/gameTest/world
  echo "=== $f" >> $OUT
  timeout 2400 sh ./gradlew runGameTest --offline -q -PbotTest="$f" > /dev/null 2>&1
  echo "exit $?" >> $OUT
  grep -h "passed in\|FAILED after\|failed at\|lost .* health" build/run/gameTest/logs/latest.log | grep -v " t=" | sed 's/^\[[0-9:]*\] \[Server thread\/[A-Z]*\] ([^)]*) //' | sort -u | cut -c1-220 >> $OUT
done
echo ALLDONE >> $OUT
