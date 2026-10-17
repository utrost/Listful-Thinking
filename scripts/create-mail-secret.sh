#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
secret_file="$repo_root/secrets/mail-password"
if [ -e "$secret_file" ]; then
  echo "SMTP secret already exists: $secret_file" >&2
  exit 1
fi
umask 077
mkdir -p "$repo_root/secrets"
chmod 0700 "$repo_root/secrets"
read -r -s -p "SMTP password: " password
printf '\n' >&2
if [ -z "$password" ]; then echo "SMTP password cannot be empty" >&2; exit 1; fi
printf '%s\n' "$password" > "$secret_file"
unset password
chmod 0444 "$secret_file"
echo "Created private SMTP secret: $secret_file"
