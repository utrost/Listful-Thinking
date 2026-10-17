#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$repo_root"
docker compose -f compose.try.yml config --format json | python3 -c '
import json, sys
config = json.load(sys.stdin)
assert config["name"] == "listful-thinking-trial", "trial data must be isolated from other Compose profiles"
app = config["services"]["listful-thinking"]
assert "build" not in app, "trial must use a published image"
assert app["image"] == "ghcr.io/utrost/listful-thinking:v0.2.0-rc.3"
assert app["ports"] == [{"mode": "ingress", "target": 8080, "published": "8080", "host_ip": "127.0.0.1", "protocol": "tcp"}], "trial must stay on loopback"
assert app["read_only"] and app["cap_drop"] == ["ALL"]
assert app["environment"]["BOOTSTRAP_TOKEN_FILE"] == "/run/secrets/bootstrap_token"
assert app["environment"]["SCRAPER_ALLOW_PRIVATE_ADDRESSES"] == "false"
assert any(mount["target"] == "/app/data" for mount in app["volumes"])
print("local trial Compose contract OK")
'
