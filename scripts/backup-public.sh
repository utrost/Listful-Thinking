#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
env_file="${LISTFUL_PUBLIC_ENV_FILE:-$repo_root/.env.public}"
backup_dir="${LISTFUL_BACKUP_DIR:-$repo_root/backups}"
compose=(docker compose --env-file "$env_file" -f "$repo_root/compose.public.yml")

if [ ! -f "$env_file" ]; then
  echo "Missing public deployment environment file: $env_file" >&2
  exit 1
fi
container_id="$("${compose[@]}" ps -q listful-thinking)"
if [ -z "$container_id" ]; then
  echo "Listful Thinking is not running; no backup was made." >&2
  exit 1
fi
volume_name="$(docker inspect --format '{{range .Mounts}}{{if eq .Destination "/app/data"}}{{.Name}}{{end}}{{end}}' "$container_id")"
if [ -z "$volume_name" ]; then
  echo "The public profile data volume was not found; no backup was made." >&2
  exit 1
fi

umask 077
mkdir -p "$backup_dir"
stamp="$(date -u +%Y%m%dT%H%M%SZ)"
name="listful-thinking-${stamp}.sqlite"
docker run --rm --network none --read-only --user 1000:1000 \
  --cap-drop ALL --security-opt no-new-privileges:true --pids-limit 64 --memory 256m \
  --mount "type=volume,source=$volume_name,target=/app/data" \
  --mount "type=bind,source=$repo_root/scripts/backup-db.py,target=/backup-db.py,readonly" \
  --mount "type=bind,source=$backup_dir,target=/backups" \
  python:3.13-alpine@sha256:2d9aefe2fef018a7eb2c13064c89c71929800fd2e5dccdbf52ea5da5bb8d929a \
  sh -eu -c 'python /backup-db.py /app/data/listful-thinking.sqlite "/backups/$1"; cd /backups; sha256sum "$1" > "$1.sha256"' sh "$name"
echo "Backup and checksum: $backup_dir/$name"
