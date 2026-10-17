#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$repo_root/frontend"
npm run docs:demo

frames="$(mktemp)"
trap 'rm -f "$frames"' EXIT
cat > "$frames" <<'EOF'
file '/tmp/listful-demo-01.png'
duration 1.4
file '/tmp/listful-demo-02.png'
duration 1.8
file '/tmp/listful-demo-03.png'
duration 2.8
file '/tmp/listful-demo-04.png'
duration 2.4
file '/tmp/listful-demo-04.png'
EOF
ffmpeg -hide_banner -loglevel error -y -f concat -safe 0 -i "$frames" \
  -vf 'fps=8,scale=880:-1:flags=lanczos,split[a][b];[a]palettegen[p];[b][p]paletteuse' \
  -loop 0 "$repo_root/docs/images/website-capture-demo.gif"
echo "Created docs/images/website-capture-demo.gif from fictional Playwright data"
