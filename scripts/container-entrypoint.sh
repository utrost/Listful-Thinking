#!/bin/sh
set -eu
umask 077
if [ -n "${MAIL_PASS_FILE:-}" ]; then
    if [ ! -r "$MAIL_PASS_FILE" ]; then
        echo "MAIL_PASS_FILE is not readable" >&2
        exit 1
    fi
    MAIL_PASS="$(cat "$MAIL_PASS_FILE")"
    export MAIL_PASS
fi
# Existing installations also get private SQLite file permissions on restart.
for file in "${LISTFUL_DB_PATH:-/app/data/listful-thinking.sqlite}" "${LISTFUL_DB_PATH:-/app/data/listful-thinking.sqlite}-wal" "${LISTFUL_DB_PATH:-/app/data/listful-thinking.sqlite}-shm"; do
    if [ -f "$file" ]; then chmod 600 "$file"; fi
done
exec java -jar /app/listful-thinking.jar "$@"
