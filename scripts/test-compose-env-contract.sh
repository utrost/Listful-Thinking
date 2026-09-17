#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$repo_root"

config="$({
  SESSION_COOKIE_SECURE=true \
  RATE_LIMIT_ENABLED=false \
  RATE_LIMIT_MAX_REQUESTS=17 \
  RATE_LIMIT_WINDOW_SECONDS=23 \
  RATE_LIMIT_MAX_BUCKETS=456 \
  TRUST_FORWARDED_FOR=true \
  MAX_REQUEST_BODY_BYTES=78901 \
  SCRAPER_ALLOW_PRIVATE_ADDRESSES=true \
  CSRF_ENABLED=false \
  docker compose config
})"

require_env() {
  local name="$1"
  local value="$2"
  if ! grep -Eq "^[[:space:]]+$name:[[:space:]]+\"?$value\"?$" <<< "$config"; then
    echo "docker-compose.yml must forward $name override as $value" >&2
    echo "--- docker compose config environment ---" >&2
    sed -n '/environment:/,/image:/p' <<< "$config" >&2
    exit 1
  fi
}

require_env SESSION_COOKIE_SECURE true
require_env RATE_LIMIT_ENABLED false
require_env RATE_LIMIT_MAX_REQUESTS 17
require_env RATE_LIMIT_WINDOW_SECONDS 23
require_env RATE_LIMIT_MAX_BUCKETS 456
require_env TRUST_FORWARDED_FOR true
require_env MAX_REQUEST_BODY_BYTES 78901
require_env SCRAPER_ALLOW_PRIVATE_ADDRESSES true
require_env CSRF_ENABLED false

echo "compose env contract OK"
