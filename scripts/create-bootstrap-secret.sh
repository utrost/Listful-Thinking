#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
secret_file="$repo_root/secrets/bootstrap-token"
if [ -e "$secret_file" ]; then
  echo "Bootstrap secret already exists: $secret_file" >&2
  exit 1
fi
umask 077
mkdir -p "$(dirname "$secret_file")"
chmod 0700 "$(dirname "$secret_file")"
python3 -c 'import secrets, sys; open(sys.argv[1], "x", encoding="utf-8").write(secrets.token_urlsafe(48) + "\n")' "$secret_file"
# Local Compose mounts file-backed secrets without remapping their host UID.
# The private parent directory protects the host path; the file remains readable
# to the container's UID 1000 even when the host operator has another UID.
chmod 0444 "$secret_file"
echo "Created private bootstrap secret: $secret_file"
echo "Read it locally when creating the first admin account. Keep the file for future Compose restarts."
