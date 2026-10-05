#!/bin/sh
# film.sh SCENE [OUTDIR] [SEED]: films one scene with the client gametest under a virtual display
SP=/tmp/claude-0/-home-user-Kasax-Challenge-Craft/2061deb3-7247-5b5c-aa62-6c43681c8da7/scratchpad
OUT=${2:-$SP/film/$1}
mkdir -p $OUT
pgrep -x Xvfb >/dev/null || { rm -f /tmp/.X11-unix/X98 /tmp/.X98-lock; setsid nohup Xvfb :98 -screen 0 2000x2000x24 >/dev/null 2>&1 < /dev/null & }
for i in $(seq 1 20); do [ -S /tmp/.X11-unix/X98 ] && break; sleep 0.5; done
cd /home/user/bob-dev
export JAVA_HOME=/opt/jdk25/jdk-25.0.4.1+1 PATH=/opt/jdk25/jdk-25.0.4.1+1/bin:$PATH XDG_RUNTIME_DIR=/tmp/claude-0/xdg DISPLAY=:98 SDL_VIDEO_FORCE_EGL=1
SEEDARG=""; [ -n "$3" ] && SEEDARG="-PfilmSeed=$3"
sh ./gradlew runFilm --no-daemon -q -Pfilm=$1 -PfilmOut=$OUT $SEEDARG > $SP/film_$1.log 2>&1
echo "exit $?" >> $SP/film_$1.log
