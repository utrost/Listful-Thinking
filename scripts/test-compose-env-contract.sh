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

prod_config="$(docker compose --env-file .env.example -f compose.prod.yml config)"

require_env_in_config() {
  local source_name="$1"
  local haystack="$2"
  local name="$3"
  local value="$4"
  if ! grep -Eq "^[[:space:]]+$name:[[:space:]]+\"?$value\"?$" <<< "$haystack"; then
    echo "$source_name must forward $name override as $value" >&2
    echo "--- docker compose config environment ---" >&2
    sed -n '/environment:/,/image:/p' <<< "$haystack" >&2
    exit 1
  fi
}

require_env() {
  require_env_in_config "docker-compose.yml" "$config" "$1" "$2"
}

require_prod_env() {
  require_env_in_config "compose.prod.yml" "$prod_config" "$1" "$2"
}

for file in .env.example compose.prod.yml; do
  [ -f "$file" ] || { echo "$file must exist" >&2; exit 1; }
done

require_env SESSION_COOKIE_SECURE true
require_env RATE_LIMIT_ENABLED false
require_env RATE_LIMIT_MAX_REQUESTS 17
require_env RATE_LIMIT_WINDOW_SECONDS 23
require_env RATE_LIMIT_MAX_BUCKETS 456
require_env TRUST_FORWARDED_FOR true
require_env MAX_REQUEST_BODY_BYTES 78901
require_env SCRAPER_ALLOW_PRIVATE_ADDRESSES true
require_env CSRF_ENABLED false

for key in \
  SYSTEM_LANG REGISTRATION_ENABLED PUBLIC_BASE_URL SESSION_COOKIE_SECURE \
  MAIL_HOST MAIL_PORT MAIL_USER MAIL_PASS \
  RATE_LIMIT_ENABLED RATE_LIMIT_MAX_REQUESTS RATE_LIMIT_WINDOW_SECONDS RATE_LIMIT_MAX_BUCKETS \
  TRUST_FORWARDED_FOR MAX_REQUEST_BODY_BYTES SCRAPER_ALLOW_PRIVATE_ADDRESSES CSRF_ENABLED; do
  grep -Eq "^$key=" .env.example || { echo ".env.example must document $key" >&2; exit 1; }
done

require_prod_env SESSION_COOKIE_SECURE false
require_prod_env RATE_LIMIT_ENABLED true
require_prod_env RATE_LIMIT_MAX_REQUESTS 60
require_prod_env RATE_LIMIT_WINDOW_SECONDS 60
require_prod_env RATE_LIMIT_MAX_BUCKETS 10000
require_prod_env TRUST_FORWARDED_FOR false
require_prod_env MAX_REQUEST_BODY_BYTES 5100000
require_prod_env SCRAPER_ALLOW_PRIVATE_ADDRESSES false
require_prod_env CSRF_ENABLED true

grep -q 'restart: unless-stopped' compose.prod.yml || { echo "compose.prod.yml must set restart policy" >&2; exit 1; }
grep -q 'healthcheck:' compose.prod.yml || { echo "compose.prod.yml must define a healthcheck" >&2; exit 1; }
grep -q 'LISTFUL_DATA_BIND' compose.prod.yml || { echo "compose.prod.yml must make bind-mount data path explicit" >&2; exit 1; }

echo "compose env contract OK"
