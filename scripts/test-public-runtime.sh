#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$repo_root"
if [ -e secrets/bootstrap-token ]; then
  echo "Refusing disposable public test while secrets/bootstrap-token exists." >&2
  exit 1
fi
docker image inspect listful-thinking:smoke >/dev/null

workdir="$(mktemp -d)"
chmod 0711 "$workdir"
export COMPOSE_PROJECT_NAME="listful-public-test-$$"
export LISTFUL_PUBLIC_ENV_FILE="$workdir/.env.public"
export LISTFUL_BACKUP_DIR="$workdir/backups"
export LISTFUL_IMAGE=listful-thinking:smoke
compose=(docker compose --env-file "$LISTFUL_PUBLIC_ENV_FILE" -f compose.public.yml)
cleanup() {
  local result=$?
  if [ "$result" -ne 0 ]; then "${compose[@]}" logs --tail 50 --no-color listful-thinking >&2 || true; fi
  "${compose[@]}" down -v --remove-orphans >/dev/null 2>&1 || true
  rm -f secrets/bootstrap-token
  rmdir secrets 2>/dev/null || true
  docker run --rm --network none --user root \
    --mount "type=bind,source=$workdir,target=/work" \
    python:3.13-alpine@sha256:2d9aefe2fef018a7eb2c13064c89c71929800fd2e5dccdbf52ea5da5bb8d929a chown -R "$(id -u):$(id -g)" /work >/dev/null 2>&1 || true
  rm -rf "$workdir"
}
trap cleanup EXIT

cp .env.public.example "$LISTFUL_PUBLIC_ENV_FILE"
scripts/create-bootstrap-secret.sh >/dev/null
test "$(stat -c %a secrets)" = 700
test "$(stat -c %a secrets/bootstrap-token)" = 444
# Prove the app can read a secret owned by a different host UID.
docker run --rm --network none --user root \
  --mount "type=bind,source=$repo_root/secrets/bootstrap-token,target=/secret" \
  python:3.13-alpine@sha256:2d9aefe2fef018a7eb2c13064c89c71929800fd2e5dccdbf52ea5da5bb8d929a chown 2000:2000 /secret
"${compose[@]}" run --rm --no-deps caddy caddy validate --config /etc/caddy/Caddyfile
"${compose[@]}" up -d --no-deps listful-thinking

ready=false
for attempt in $(seq 1 60); do
  if "${compose[@]}" exec -T listful-thinking wget -qO- http://localhost:8080/api/v1/health/ready 2>/dev/null \
    | grep -q '"status":"ok"'; then
    ready=true
    break
  fi
  sleep 2
done
if [ "$ready" != true ]; then echo "Public app never became ready" >&2; exit 1; fi
"${compose[@]}" exec -T listful-thinking wget -qO- http://localhost:8080/api/v1/auth/settings \
  | python3 -c 'import json,sys; s=json.load(sys.stdin); assert s["bootstrapConfigured"] and s["bootstrapRequired"] and s["registrationAvailable"]'

mkdir "$LISTFUL_BACKUP_DIR"
docker run --rm --network none --user root \
  --mount "type=bind,source=$LISTFUL_BACKUP_DIR,target=/backups" \
  python:3.13-alpine@sha256:2d9aefe2fef018a7eb2c13064c89c71929800fd2e5dccdbf52ea5da5bb8d929a chown 1000:1000 /backups
scripts/backup-public.sh
if ! docker run --rm --network none --user 1000:1000 \
  --mount "type=bind,source=$LISTFUL_BACKUP_DIR,target=/backups,readonly" \
  python:3.13-alpine@sha256:2d9aefe2fef018a7eb2c13064c89c71929800fd2e5dccdbf52ea5da5bb8d929a \
  sh -eu -c 'cd /backups; set -- *.sha256; test "$#" -eq 1; sha256sum -c "$1"'; then
  echo "Expected one verified public backup" >&2
  exit 1
fi
echo "Public runtime, Caddy config, and live backup OK"
