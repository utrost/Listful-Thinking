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
require_in_file README.md "GNU AGPL v3.0 only"
require_in_file README.md "docs/images/wishlist-desktop.png"
require_in_file README.md "docs/images/website-review.png"
require_in_file README.md "docs/images/grocery-mobile.png"
require_in_file LICENSE "GNU AFFERO GENERAL PUBLIC LICENSE"
require_in_file README.md "compose.prod.yml"
require_in_file README.md ".env.example"
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
handbook = Path('docs/deployment/self-hosting-handbook.md').read_text()
start = '<!-- compose-public-example-start -->'
end = '<!-- compose-public-example-end -->'
assert handbook.count(start) == handbook.count(end) == 1, 'handbook Compose example markers missing'
sample = handbook.split(start, 1)[1].split(end, 1)[0].strip()
expected = '```yaml\n' + Path('compose.public.yml').read_text().strip() + '\n```'
assert sample == expected, 'handbook Compose example differs from compose.public.yml'
files = [Path('README.md'), *Path('docs').rglob('*.md')]
problems = []
for path in files:
    text = path.read_text(encoding='utf-8')
    targets = [match.group(1) for match in re.finditer(r'\[[^\]]+\]\(([^)]+)\)', text)]
    targets += [match.group(1) for match in re.finditer(r'<img\s+[^>]*src="([^"]+)"', text)]
    for target in targets:
        target = target.strip()
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
