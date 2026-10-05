#!/bin/sh
# film_seq.sh OUTDIR SCENE... : each scene in its own client (memory starts fresh), frames packed after each.
SP=/tmp/claude-0/-home-user-Kasax-Challenge-Craft/2061deb3-7247-5b5c-aa62-6c43681c8da7/scratchpad
OUT=$1; shift
for s in "$@"; do
  sh $SP/film.sh $s $OUT
  echo "$(date +%H:%M:%S) $s: $(grep -E '^exit ' $SP/film_$s.log)" >> $SP/film_seq.log
  sh $SP/pack.sh $OUT >> $SP/film_seq.log 2>&1
done
echo "$(date +%H:%M:%S) all done" >> $SP/film_seq.log
