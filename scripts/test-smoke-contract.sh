#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
script="$repo_root/scripts/smoke.sh"

[ -x "$script" ] || { echo "scripts/smoke.sh must exist and be executable" >&2; exit 1; }

grep -q 'set -euo pipefail' "$script" || { echo "smoke.sh must use strict shell mode" >&2; exit 1; }
grep -q 'docker compose' "$script" || { echo "smoke.sh must exercise docker compose" >&2; exit 1; }
grep -q -- '--build' "$script" || { echo "smoke.sh must build the image" >&2; exit 1; }
grep -q '/api/v1/health' "$script" || { echo "smoke.sh must check health" >&2; exit 1; }
grep -q '/api/v1/auth/register' "$script" || { echo "smoke.sh must register users" >&2; exit 1; }
grep -q '/api/v1/lists' "$script" || { echo "smoke.sh must exercise list API" >&2; exit 1; }
grep -q 'ownerLabel' "$script" || { echo "smoke.sh must exercise item responsibility owner labels" >&2; exit 1; }
grep -q 'assistantLabels' "$script" || { echo "smoke.sh must exercise item responsibility assistant labels" >&2; exit 1; }
grep -q '/api/v1/admin/users' "$script" || { echo "smoke.sh must exercise admin API" >&2; exit 1; }
grep -q '/api/v1/share' "$script" || { echo "smoke.sh must exercise public sharing" >&2; exit 1; }
grep -q 'LISTFUL_KEEP_SMOKE' "$script" || { echo "smoke.sh must support keeping the container for debugging" >&2; exit 1; }

echo "smoke contract OK"
