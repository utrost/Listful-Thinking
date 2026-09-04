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

expect_status_json() {
  expected_status="$1"
  json_expr="$2"
  shift 2
  response_file="$workdir/response.json"
  status="$(curl -sS -o "$response_file" -w '%{http_code}' "$@")"
  [ "$status" = "$expected_status" ] || { echo "Expected HTTP $expected_status, got $status; body=$(cat "$response_file")" >&2; exit 1; }
  assert_json "$json_expr" < "$response_file"
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
      RATE_LIMIT_WINDOW_SECONDS: "3600"
      TRUST_FORWARDED_FOR: "true"
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

oversized_username="$(python3 -c 'print("a" * 70000)')"
expect_status_json 413 'data["code"] == "payload_too_large"' \
  -H 'Content-Type: application/json' \
  -d "{\"username\":\"$oversized_username\",\"password\":\"wrong password\"}" \
  "$base_url/api/v1/auth/login"
for attempt in $(seq 1 60); do
  expect_status_json 401 'data["code"] == "bad_credentials"' \
    -H 'X-Forwarded-For: 203.0.113.60' \
    -H 'Content-Type: application/json' \
    -d '{"username":"missing","password":"wrong password"}' \
    "$base_url/api/v1/auth/login"
done
expect_status_json 429 'data["code"] == "rate_limited"' \
  -H 'X-Forwarded-For: 203.0.113.60' \
  -H 'Content-Type: application/json' \
  -d '{"username":"missing","password":"wrong password"}' \
  "$base_url/api/v1/auth/login"

list_json="$(curl_json -b "$admin_cookie" -H 'Content-Type: application/json' \
  -d '{"title":"Birthday","description":"Gift ideas","type":"WISH"}' \
  "$base_url/api/v1/lists")"
list_id="$(printf '%s' "$list_json" | json_field id)"
curl_json -b "$admin_cookie" -X PUT -H 'Content-Type: application/json' \
  -d '{"title":"Birthday 2027","description":"Updated gift ideas","type":"WISH"}' \
  "$base_url/api/v1/lists/$list_id" | assert_json 'data["title"] == "Birthday 2027" and data["description"] == "Updated gift ideas"'
curl_json -b "$admin_cookie" -H 'Content-Type: application/json' \
  -d '{"username":"martha","permission":"CONTRIBUTE"}' \
  "$base_url/api/v1/lists/$list_id/shares" | assert_json 'data["username"] == "martha" and data["permission"] == "CONTRIBUTE"'
curl_json -b "$user_cookie" "$base_url/api/v1/lists" \
  | assert_json 'any(item["id"] == "'"$list_id"'" and item["access"] == "CONTRIBUTE" for item in data)'

item_json="$(curl_json -b "$admin_cookie" -H 'Content-Type: application/json' \
  -d '{"name":"Book","url":"https://example.test/book","price":19.99}' \
  "$base_url/api/v1/lists/$list_id/items")"
item_id="$(printf '%s' "$item_json" | json_field id)"
contributor_item_json="$(curl_json -b "$user_cookie" -H 'Content-Type: application/json' \
  -d '{"name":"Contributor idea"}' \
  "$base_url/api/v1/lists/$list_id/items")"
contributor_item_id="$(printf '%s' "$contributor_item_json" | json_field id)"
curl_json -b "$user_cookie" -X PUT -H 'Content-Type: application/json' \
  -d '{"name":"Updated contributor idea","status":"OPEN"}' \
  "$base_url/api/v1/items/$contributor_item_id" | assert_json 'data["name"] == "Updated contributor idea"'

clone_json="$(curl_json -b "$admin_cookie" -H 'Content-Type: application/json' \
  -d '{"title":"Birthday 2027 copy"}' \
  "$base_url/api/v1/lists/$list_id/clone")"
clone_id="$(printf '%s' "$clone_json" | json_field id)"
printf '%s' "$clone_json" | assert_json 'data["title"] == "Birthday 2027 copy" and data["type"] == "WISH" and data["publicList"] is False and data["shareToken"] is None'
curl_json -b "$admin_cookie" "$base_url/api/v1/lists/$clone_id/items" \
  | assert_json 'len(data) == 2 and any(item["name"] == "Book" and item["url"] == "https://example.test/book" and item["price"] == 19.99 for item in data) and any(item["name"] == "Updated contributor idea" for item in data)'
curl_json -b "$admin_cookie" "$base_url/api/v1/lists/$clone_id/shares" \
  | assert_json 'len(data) == 0'

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

chore_json="$(curl_json -b "$admin_cookie" -H 'Content-Type: application/json' \
  -d '{"title":"Chores","description":"Home care","type":"CHORE"}' \
  "$base_url/api/v1/lists")"
chore_id="$(printf '%s' "$chore_json" | json_field id)"
chore_item_json="$(curl_json -b "$admin_cookie" -H 'Content-Type: application/json' \
  -d '{"name":"Water plants","dueDate":"2027-01-01T09:00:00Z","recurrenceRule":"FREQ=BIWEEKLY"}' \
  "$base_url/api/v1/lists/$chore_id/items")"
chore_item_id="$(printf '%s' "$chore_item_json" | json_field id)"
curl_json -b "$admin_cookie" -X PUT -H 'Content-Type: application/json' \
  -d '{"name":"Water plants","status":"DONE","dueDate":"2027-01-01T09:00:00Z","recurrenceRule":"FREQ=BIWEEKLY"}' \
  "$base_url/api/v1/items/$chore_item_id" | assert_json 'data["status"] == "OPEN" and data["dueDate"] == "2027-01-15T09:00:00Z" and data["lastCompletedAt"]'
curl_json -b "$admin_cookie" -X POST "$base_url/api/v1/items/$chore_item_id/skip" \
  | assert_json 'data["status"] == "OPEN" and data["dueDate"] == "2027-01-29T09:00:00Z"'
curl_json -b "$admin_cookie" -X POST -H 'Content-Type: application/json' -d '{"days":1}' "$base_url/api/v1/items/$chore_item_id/postpone" \
  | assert_json 'data["status"] == "OPEN" and data["dueDate"] == "2027-01-30T09:00:00Z"'
quarterly_chore_item_json="$(curl_json -b "$admin_cookie" -H 'Content-Type: application/json' \
  -d '{"name":"Service machine","dueDate":"2027-01-31T09:00:00Z","recurrenceRule":"FREQ=QUARTERLY"}' \
  "$base_url/api/v1/lists/$chore_id/items")"
quarterly_chore_item_id="$(printf '%s' "$quarterly_chore_item_json" | json_field id)"
curl_json -b "$admin_cookie" -X PUT -H 'Content-Type: application/json' \
  -d '{"name":"Service machine","status":"DONE","dueDate":"2027-01-31T09:00:00Z","recurrenceRule":"FREQ=QUARTERLY"}' \
  "$base_url/api/v1/items/$quarterly_chore_item_id" | assert_json 'data["status"] == "OPEN" and data["dueDate"] == "2027-04-30T09:00:00Z" and data["lastCompletedAt"]'
annual_chore_item_json="$(curl_json -b "$admin_cookie" -H 'Content-Type: application/json' \
  -d '{"name":"Renew insurance","dueDate":"2027-02-28T09:00:00Z","recurrenceRule":"FREQ=ANNUALLY"}' \
  "$base_url/api/v1/lists/$chore_id/items")"
annual_chore_item_id="$(printf '%s' "$annual_chore_item_json" | json_field id)"
curl_json -b "$admin_cookie" -X POST "$base_url/api/v1/items/$annual_chore_item_id/skip" \
  | assert_json 'data["status"] == "OPEN" and data["dueDate"] == "2028-02-28T09:00:00Z"'

grocery_json="$(curl_json -b "$admin_cookie" -H 'Content-Type: application/json' \
  -d '{"title":"Groceries","description":"Weekly shop","type":"GROCERY"}' \
  "$base_url/api/v1/lists")"
grocery_id="$(printf '%s' "$grocery_json" | json_field id)"
curl_json -b "$admin_cookie" -H 'Content-Type: application/json' \
  -d '{"name":"Oat milk","quantity":"2 cartons","category":"Dairy alternatives"}' \
  "$base_url/api/v1/lists/$grocery_id/items" | assert_json 'data["name"] == "Oat milk" and data["quantity"] == "2 cartons" and data["category"] == "Dairy alternatives"'
grocery_done_json="$(curl_json -b "$admin_cookie" -H 'Content-Type: application/json' \
  -d '{"name":"Apples","quantity":"1 kg","category":"Fruit"}' \
  "$base_url/api/v1/lists/$grocery_id/items")"
grocery_done_id="$(printf '%s' "$grocery_done_json" | json_field id)"
curl_json -b "$admin_cookie" -X PUT -H 'Content-Type: application/json' \
  -d '{"name":"Apples","quantity":"1 kg","category":"Fruit","status":"DONE"}' \
  "$base_url/api/v1/items/$grocery_done_id" | assert_json 'data["status"] == "DONE"'
curl -fsS -b "$admin_cookie" -X DELETE "$base_url/api/v1/lists/$grocery_id/items/completed" >/dev/null
curl_json -b "$admin_cookie" "$base_url/api/v1/lists/$grocery_id/items" \
  | assert_json 'len(data) == 1 and data[0]["name"] == "Oat milk"'

curl_json -b "$admin_cookie" "$base_url/api/v1/admin/lists" \
  | assert_json 'any(item["title"] == "Next actions" and item["ownerUsername"] == "admin" for item in data) and any(item["title"] == "Groceries" and item["type"] == "GROCERY" for item in data)'

signup_share_json="$(curl_json -b "$admin_cookie" -H 'Content-Type: application/json' \
  -d '{"mode":"SIGNUP"}' \
  "$base_url/api/v1/lists/$todo_id/public-share")"
signup_token="$(printf '%s' "$signup_share_json" | json_field shareToken)"
printf '%s' "$signup_share_json" | assert_json 'data["publicList"] is True and data["mode"] == "SIGNUP"'
curl_json "$base_url/api/v1/share/$signup_token" \
  | assert_json 'data["title"] == "Next actions" and data["mode"] == "SIGNUP" and any(item["id"] == "'"$todo_item_id"'" and item["status"] == "OPEN" for item in data["items"])'
curl_json -H 'Content-Type: application/json' \
  -d '{"guestName":"Visitor"}' \
  "$base_url/api/v1/share/$signup_token/items/$todo_item_id/claim" | assert_json 'data["status"] == "CLAIMED" and data["reservedByGuest"] == "Visitor"'

share_json="$(curl_json -b "$admin_cookie" -H 'Content-Type: application/json' \
  -d '{"mode":"WISH_CLAIM"}' \
  "$base_url/api/v1/lists/$list_id/public-share")"
printf '%s' "$share_json" | assert_json 'data["publicList"] is True and data["mode"] == "WISH_CLAIM"'
token="$(printf '%s' "$share_json" | json_field shareToken)"

curl_json "$base_url/api/v1/share/$token" \
  | assert_json 'data["title"] == "Birthday 2027" and data["mode"] == "WISH_CLAIM" and len(data["items"]) == 2 and any(item["status"] == "OPEN" for item in data["items"])'

curl_json -H 'Content-Type: application/json' \
  -d '{"guestName":"Annette"}' \
  "$base_url/api/v1/share/$token/items/$item_id/claim" | assert_json 'data["status"] == "CLAIMED" and data["reservedByGuest"] == "Annette"'

[ -f "$workdir/../nonexistent" ] || true
sqlite_path="$(docker compose -p "$project" -f "$compose_file" exec -T listful-thinking sh -c 'test -f /app/data/listful-thinking.sqlite && echo present')"
[ "$sqlite_path" = "present" ] || { echo "SQLite database missing in /app/data" >&2; exit 1; }

echo "Smoke OK: health, non-root runtime, admin/users/settings, list clone, list/item/chore recurrence/grocery clear-completed/public claim/signup, SQLite volume"
