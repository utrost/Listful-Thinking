#!/bin/sh
set -eu
umask 077
# Existing installations also get private SQLite file permissions on restart.
for file in "${LISTFUL_DB_PATH:-/app/data/listful-thinking.sqlite}" "${LISTFUL_DB_PATH:-/app/data/listful-thinking.sqlite}-wal" "${LISTFUL_DB_PATH:-/app/data/listful-thinking.sqlite}-shm"; do
    if [ -f "$file" ]; then chmod 600 "$file"; fi
done
exec java -jar /app/listful-thinking.jar "$@"
