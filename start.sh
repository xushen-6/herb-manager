#!/bin/sh
set -eu
case "$0" in
    */*) SCRIPT_DIR=${0%/*} ;;
    *) SCRIPT_DIR=. ;;
esac
BASE=$(CDPATH= cd -- "$SCRIPT_DIR" && pwd)
cd "$BASE"
if [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/java" ]; then
    JAVA_CMD="$JAVA_HOME/bin/java"
elif command -v java >/dev/null 2>&1; then
    JAVA_CMD=java
else
    printf '%s\n' 'Java was not found. Install Java 17 or newer, or set JAVA_HOME.' >&2
    exit 1
fi
APP_JAR="$BASE/app/herb-manager.jar"
if [ ! -f "$APP_JAR" ]; then APP_JAR="$BASE/target/herb-manager.pending.jar"; fi
if [ ! -f "$APP_JAR" ]; then APP_JAR="$BASE/target/herb-manager.jar"; fi
if [ ! -f "$APP_JAR" ]; then
    printf '%s\n' 'Application JAR is missing. Extract the complete source/demo ZIP.' >&2
    exit 1
fi
exec "$JAVA_CMD" -Dfile.encoding=UTF-8 -jar "$APP_JAR" "$@"
