#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$repo_root"
config="$(docker compose --env-file .env.public.example -f compose.public.yml config --format json)"
python3 -c '
import json, sys
c = json.load(sys.stdin)
app = c["services"]["listful-thinking"]
proxy = c["services"]["caddy"]
env = app["environment"]
assert not app.get("ports"), "app must have no host port"
assert proxy["ports"], "proxy must publish HTTPS"
for key, expected in {
    "PUBLIC_HOSTING": "true", "SESSION_COOKIE_SECURE": "true",
    "CSRF_ENABLED": "true", "RATE_LIMIT_ENABLED": "true",
    "TRUST_FORWARDED_FOR": "true", "SCRAPER_ALLOW_PRIVATE_ADDRESSES": "false",
}.items():
    assert str(env.get(key)).lower() == expected, (key, env.get(key))
assert app["read_only"] is True
assert "ALL" in app["cap_drop"]
assert int(app["mem_limit"]) > 0 and int(app["pids_limit"]) > 0
assert app["secrets"] and "BOOTSTRAP_TOKEN_FILE" in env
assert app["healthcheck"]
print("public Compose contract OK")
' <<< "$config"

grep -Fq 'header_up X-Forwarded-For {remote_host}' Caddyfile.public
grep -Fq 'Strict-Transport-Security' Caddyfile.public
