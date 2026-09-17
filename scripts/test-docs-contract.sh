#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$repo_root"

handbook="docs/deployment/self-hosting-handbook.md"
[ -f "$handbook" ] || { echo "self-hosting handbook must exist" >&2; exit 1; }

require_in_file() {
  local file="$1"
  local needle="$2"
  if ! grep -Fq "$needle" "$file"; then
    echo "$file must mention: $needle" >&2
    exit 1
  fi
}

require_in_file README.md "docs/deployment/self-hosting-handbook.md"
require_in_file docs/release.md "deployment/self-hosting-handbook.md"
require_in_file "$handbook" "Private LAN or Tailnet"
require_in_file "$handbook" "Public internet behind HTTPS"
require_in_file "$handbook" "SESSION_COOKIE_SECURE=true"
require_in_file "$handbook" "PUBLIC_BASE_URL=https://lists.example.org"
require_in_file "$handbook" "Strict-Transport-Security"
require_in_file "$handbook" "TRUST_FORWARDED_FOR=true"
require_in_file "$handbook" "reverse proxy overwrites or strips inbound"
require_in_file "$handbook" "JSESSIONID"
require_in_file "$handbook" "listful-thinking.sqlite"

python3 - <<'PY'
from pathlib import Path
import re
root = Path('.')
files = [Path('README.md'), *Path('docs').rglob('*.md')]
problems = []
for path in files:
    text = path.read_text(encoding='utf-8')
    for match in re.finditer(r'\[[^\]]+\]\(([^)]+)\)', text):
        target = match.group(1).strip()
        if not target or '://' in target or target.startswith('#') or target.startswith('mailto:'):
            continue
        clean = target.split('#', 1)[0]
        if not clean:
            continue
        candidate = (path.parent / clean).resolve()
        try:
            candidate.relative_to(root.resolve())
        except ValueError:
            problems.append(f'{path}: link escapes repo: {target}')
            continue
        if not candidate.exists():
            problems.append(f'{path}: missing link target: {target}')
if problems:
    print('\n'.join(problems))
    raise SystemExit(1)
print('Markdown links OK')
PY

echo "docs contract OK"
