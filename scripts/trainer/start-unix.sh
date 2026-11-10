#!/bin/sh
# Bob-Trainer: startet den Trainer im Hintergrund (macOS, Linux). Aufgerufen von den Startdateien.
# Sucht Java 25; gibt es keins, bietet es an, Eclipse Temurin JRE 25 (api.adoptium.net) in den Ordner
# java/ zu laden. Danach laeuft der Trainer im Hintergrund; das Dashboard oeffnet sich im Browser.
APP="$(cd "$(dirname "$0")/.." && pwd)"
JAR="$APP/app/bob-trainer.jar"

major() {
    "$1" -version 2>&1 | sed -n 's/.*version "\([0-9][0-9]*\).*/\1/p' | head -n 1
}

ok() {
    [ -x "$1" ] && v="$(major "$1")" && [ -n "$v" ] && [ "$v" -ge 25 ] 2>/dev/null
}

find_java() {
    for j in "$APP/java/bin/java" "$APP/java/Contents/Home/bin/java" "${JAVA_HOME:+$JAVA_HOME/bin/java}" "$(command -v java 2>/dev/null)"; do
        [ -n "$j" ] && ok "$j" && { echo "$j"; return 0; }
    done
    if [ -x /usr/libexec/java_home ]; then
        h="$(/usr/libexec/java_home -v 25+ 2>/dev/null)" && ok "$h/bin/java" && { echo "$h/bin/java"; return 0; }
    fi
    for j in /usr/lib/jvm/*/bin/java /Library/Java/JavaVirtualMachines/*/Contents/Home/bin/java \
             "$HOME/Library/Application Support/minecraft/runtime"/*/*/*/jre.bundle/Contents/Home/bin/java \
             "$HOME/.minecraft/runtime"/*/*/*/bin/java; do
        ok "$j" && { echo "$j"; return 0; }
    done
    return 1
}

JAVA="$(find_java)"
if [ -z "$JAVA" ]; then
    echo "Bob-Trainer braucht Java 25, auf diesem Rechner ist keins zu finden."
    printf "Soll Java 25 (Eclipse Temurin, etwa 50 MB) in den Trainer-Ordner geladen werden? (j/n) "
    read -r a
    case "$a" in j*|J*|y*|Y*) ;; *) echo "Abgebrochen. Java 25 gibt es auf https://adoptium.net"; exit 1 ;; esac
    case "$(uname -s)" in Darwin) os=mac ;; *) os=linux ;; esac
    case "$(uname -m)" in arm64|aarch64) arch=aarch64 ;; *) arch=x64 ;; esac
    url="https://api.adoptium.net/v3/binary/latest/25/ga/$os/$arch/jre/hotspot/normal/eclipse?project=jdk"
    echo "Lade $url ..."
    tmp="$APP/java-download"
    rm -rf "$tmp" && mkdir -p "$tmp" || exit 1
    curl -fL "$url" -o "$tmp/java.tar.gz" || { echo "Download fehlgeschlagen"; exit 1; }
    tar -xzf "$tmp/java.tar.gz" -C "$tmp" || exit 1
    rm -rf "$APP/java"
    mv "$tmp"/jdk* "$APP/java" && rm -rf "$tmp"
    JAVA="$(find_java)"
    [ -z "$JAVA" ] && { echo "Java konnte nicht eingerichtet werden."; exit 1; }
    echo "Java 25 ist eingerichtet."
fi
cd "$APP" || exit 1
nohup "$JAVA" -Xmx512m -jar "$JAR" --app "$APP" >/dev/null 2>&1 &
echo $! > "$APP/app/trainer.pid"
echo "Bob-Trainer laeuft im Hintergrund (PID $!). Das Dashboard oeffnet sich im Browser."
echo "Adresse, falls nicht: siehe $APP/server/bob-autopilot/dashboard.url"
