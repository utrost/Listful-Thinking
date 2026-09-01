#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
project="${LISTFUL_SMOKE_PROJECT:-listful-thinking-smoke}"
port="${LISTFUL_SMOKE_PORT:-18080}"
base_url="http://localhost:${port}"
workdir="$(mktemp -d)"
compose_file="$workdir/docker-compose.smoke.yml"
admin_cookie="$workdir/admin.cookies"
user_cookie="$workdir/user.cookies"

cleanup() {
  if [ "${LISTFUL_KEEP_SMOKE:-false}" = "true" ]; then
    echo "Keeping smoke stack '$project' for debugging. Remove with: docker compose -p '$project' -f '$compose_file' down -v"
    echo "Temporary smoke files kept in: $workdir"
    return
  fi
  docker compose -p "$project" -f "$compose_file" down -v --remove-orphans >/dev/null 2>&1 || true
  rm -rf "$workdir"
}
trap cleanup EXIT

require_command() {
  command -v "$1" >/dev/null 2>&1 || { echo "Missing required command: $1" >&2; exit 1; }
}

json_field() {
  python3 -c 'import json, sys
path = sys.argv[1].split(".")
data = json.load(sys.stdin)
for part in path:
    data = data[int(part)] if isinstance(data, list) else data[part]
print(data)' "$1"
}

assert_json() {
  python3 -c 'import json, sys
expr = sys.argv[1]
data = json.load(sys.stdin)
if not eval(expr, {"__builtins__": {"all": all, "any": any, "len": len}}, {"data": data}):
    raise SystemExit(f"JSON assertion failed: {expr}; data={data!r}")' "$1"
}

curl_json() {
  curl -fsS "$@"
}

require_command docker
require_command curl
require_command python3

docker compose version >/dev/null

cat > "$compose_file" <<YAML
services:
  listful-thinking:
    build: $repo_root
    image: listful-thinking:smoke
    ports:
      - "$port:8080"
    environment:
      SYSTEM_LANG: en
      REGISTRATION_ENABLED: "true"
      PUBLIC_BASE_URL: "http://localhost:${port}"
      MAIL_HOST: ""
      MAIL_PORT: ""
      MAIL_USER: ""
      MAIL_PASS: ""
    volumes:
      - listful-smoke-data:/app/data
volumes:
  listful-smoke-data:
YAML

echo "Starting smoke stack '$project' on $base_url"
docker compose -p "$project" -f "$compose_file" down -v --remove-orphans >/dev/null 2>&1 || true
docker compose -p "$project" -f "$compose_file" up --build -d

for attempt in $(seq 1 90); do
  if curl -fsS "$base_url/api/v1/health" >/dev/null 2>&1; then
    break
  fi
  if [ "$attempt" = 90 ]; then
    docker compose -p "$project" -f "$compose_file" logs --no-color listful-thinking >&2 || true
    echo "Timed out waiting for health endpoint" >&2
    exit 1
  fi
  sleep 1
done

uid_gid="$(docker compose -p "$project" -f "$compose_file" exec -T listful-thinking sh -c 'printf "%s:%s" "$(id -u)" "$(id -g)"')"
[ "$uid_gid" = "1000:1000" ] || { echo "Expected runtime UID:GID 1000:1000, got $uid_gid" >&2; exit 1; }

curl_json -c "$admin_cookie" -H 'Content-Type: application/json' \
  -d '{"username":"admin","email":"admin@example.test","password":"correct horse battery staple"}' \
  "$base_url/api/v1/auth/register" | assert_json 'data["role"] == "ADMIN"'

curl_json -c "$user_cookie" -H 'Content-Type: application/json' \
  -d '{"username":"martha","email":"martha@example.test","password":"another good password"}' \
  "$base_url/api/v1/auth/register" | assert_json 'data["role"] == "USER"'

curl_json -b "$admin_cookie" "$base_url/api/v1/admin/users" \
  | assert_json 'len(data) == 2 and data[0]["role"] == "ADMIN" and data[0]["active"] is True and data[1]["role"] == "USER" and all("passwordHash" not in user for user in data)'

created_user_json="$(curl_json -b "$admin_cookie" -H 'Content-Type: application/json' \
  -d '{"username":"bob","email":"bob@example.test","password":"admin set password","role":"USER"}' \
  "$base_url/api/v1/admin/users")"
created_user_id="$(printf '%s' "$created_user_json" | json_field id)"
curl_json -b "$admin_cookie" -X PATCH -H 'Content-Type: application/json' \
  -d '{"active":false}' \
  "$base_url/api/v1/admin/users/$created_user_id" | assert_json 'data["username"] == "bob" and data["active"] is False'

curl_json -b "$admin_cookie" -X PUT -H 'Content-Type: application/json' \
  -d '{"registrationEnabled":false}' \
  "$base_url/api/v1/admin/settings" | assert_json 'data["registrationEnabled"] is False'

curl -fsS "$base_url/magic-login?token=smoke" >/dev/null
curl -fsS "$base_url/reset-password?token=smoke" >/dev/null
curl_json -H 'Content-Type: application/json' \
  -d '{"email":"nobody@example.test"}' \
  "$base_url/api/v1/auth/magic-link" >/dev/null
curl_json -H 'Content-Type: application/json' \
  -d '{"email":"nobody@example.test"}' \
  "$base_url/api/v1/auth/password-reset" >/dev/null

list_json="$(curl_json -b "$admin_cookie" -H 'Content-Type: application/json' \
  -d '{"title":"Birthday","description":"Gift ideas","type":"WISH"}' \
  "$base_url/api/v1/lists")"
list_id="$(printf '%s' "$list_json" | json_field id)"

item_json="$(curl_json -b "$admin_cookie" -H 'Content-Type: application/json' \
  -d '{"name":"Book","url":"https://example.test/book","price":19.99}' \
  "$base_url/api/v1/lists/$list_id/items")"
item_id="$(printf '%s' "$item_json" | json_field id)"

todo_json="$(curl_json -b "$admin_cookie" -H 'Content-Type: application/json' \
  -d '{"title":"Next actions","description":"One-off reminders","type":"TODO"}' \
  "$base_url/api/v1/lists")"
todo_id="$(printf '%s' "$todo_json" | json_field id)"
curl_json -b "$admin_cookie" -H 'Content-Type: application/json' \
  -d '{"name":"Call optician","dueDate":"2030-01-01T09:00:00Z"}' \
  "$base_url/api/v1/lists/$todo_id/items" | assert_json 'data["name"] == "Call optician" and data["dueDate"] == "2030-01-01T09:00:00Z"'
todo_item_id="$(curl_json -b "$admin_cookie" "$base_url/api/v1/lists/$todo_id/items" | json_field 0.id)"
curl_json -b "$admin_cookie" -X PUT -H 'Content-Type: application/json' \
  -d '{"name":"Call optician","status":"DONE","dueDate":"2030-01-01T09:00:00Z"}' \
  "$base_url/api/v1/items/$todo_item_id" | assert_json 'data["status"] == "DONE"'
curl_json -b "$admin_cookie" -X PUT -H 'Content-Type: application/json' \
  -d '{"name":"Call optician","status":"OPEN","dueDate":"2030-01-01T09:00:00Z"}' \
  "$base_url/api/v1/items/$todo_item_id" | assert_json 'data["status"] == "OPEN"'

grocery_json="$(curl_json -b "$admin_cookie" -H 'Content-Type: application/json' \
  -d '{"title":"Groceries","description":"Weekly shop","type":"GROCERY"}' \
  "$base_url/api/v1/lists")"
grocery_id="$(printf '%s' "$grocery_json" | json_field id)"
curl_json -b "$admin_cookie" -H 'Content-Type: application/json' \
  -d '{"name":"Oat milk","quantity":"2 cartons","category":"Dairy alternatives"}' \
  "$base_url/api/v1/lists/$grocery_id/items" | assert_json 'data["name"] == "Oat milk" and data["quantity"] == "2 cartons" and data["category"] == "Dairy alternatives"'

curl_json -b "$admin_cookie" "$base_url/api/v1/admin/lists" \
  | assert_json 'any(item["title"] == "Next actions" and item["ownerUsername"] == "admin" for item in data) and any(item["title"] == "Groceries" and item["type"] == "GROCERY" for item in data)'

share_json="$(curl_json -b "$admin_cookie" -X POST "$base_url/api/v1/lists/$list_id/public-share")"
token="$(printf '%s' "$share_json" | json_field shareToken)"

curl_json "$base_url/api/v1/share/$token" \
  | assert_json 'data["title"] == "Birthday" and len(data["items"]) == 1 and data["items"][0]["status"] == "OPEN"'

curl_json -H 'Content-Type: application/json' \
  -d '{"guestName":"Annette"}' \
  "$base_url/api/v1/share/$token/items/$item_id/claim" | assert_json 'data["status"] == "CLAIMED" and data["reservedByGuest"] == "Annette"'

[ -f "$workdir/../nonexistent" ] || true
sqlite_path="$(docker compose -p "$project" -f "$compose_file" exec -T listful-thinking sh -c 'test -f /app/data/listful-thinking.sqlite && echo present')"
[ "$sqlite_path" = "present" ] || { echo "SQLite database missing in /app/data" >&2; exit 1; }

echo "Smoke OK: health, non-root runtime, admin/users/settings, list/item/public claim, SQLite volume"
