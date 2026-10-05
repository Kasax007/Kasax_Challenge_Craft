#!/bin/sh
# pack.sh DIR: every finished shot folder under DIR (PNG frames) -> JPEG q2, PNGs deleted.
# A shot counts as finished once the film log says so.
SP=/tmp/claude-0/-home-user-Kasax-Challenge-Craft/2061deb3-7247-5b5c-aa62-6c43681c8da7/scratchpad
for shot in $(grep -ohE "\[Film\] shot [a-z0-9_]+:" $SP/film_*.log | sed 's/.*shot //; s/://' | sort -u); do
  for dir in $1/$shot; do
    [ -d "$dir" ] || continue
    ls $dir/*.png >/dev/null 2>&1 || continue
    ffmpeg -v error -y -i $dir/f%05d.png -q:v 2 -start_number 0 $dir/f%05d.jpg && rm -f $dir/f*.png && echo "packed $dir"
  done
done
