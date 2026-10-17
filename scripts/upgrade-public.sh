#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
env_file="${LISTFUL_PUBLIC_ENV_FILE:-$repo_root/.env.public}"
compose=(docker compose --env-file "$env_file" -f "$repo_root/compose.public.yml")
if [ -f "$repo_root/secrets/mail-password" ]; then
  compose+=(-f "$repo_root/compose.public.smtp.yml")
fi

"$repo_root/scripts/backup-public.sh"
"${compose[@]}" pull
"${compose[@]}" up -d --no-build
for attempt in $(seq 1 60); do
  if "${compose[@]}" exec -T listful-thinking wget -qO- http://localhost:8080/api/v1/health/ready \
    | grep -q '"status":"ok"'; then
    echo "Upgrade ready. Verify login and a list through the public HTTPS address."
    exit 0
  fi
  sleep 2
done
echo "Upgrade did not become ready. Keep the backup and follow the restore guide before attempting a downgrade." >&2
exit 1
