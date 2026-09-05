# Alice Tailnet Deployment

This document records the current private deployment contract for the Listful Thinking instance on Alice and the safe redeploy procedure.

The Alice instance is a private Tailnet MVP deployment, not a public-internet production profile.

## Live contract

Observed deployment contract:

- Host: Alice
- Tailnet IP: `100.123.149.120`
- Tailnet URL: `http://alice.taileb20d9.ts.net:8080`
- Container name: `listful-thinking-alice`
- Image tag: `listful-thinking:alice`
- Port binding: `100.123.149.120:8080->8080/tcp`
- Restart policy: `unless-stopped`
- Persistent volume: `listful-thinking-alice-data:/app/data`
- SQLite database: `/app/data/listful-thinking.sqlite`
- Runtime user: non-root application user from the Docker image

The service is intentionally bound to Alice's Tailscale address only. A LAN check against `192.168.10.126:8080` should not respond unless the deployment policy changes.

## Important environment keys

Preserve existing application environment values when recreating the container. The live container may not set every optional key.

Application keys seen or supported:

- `SYSTEM_LANG`
- `SPRING_DATASOURCE_URL`
- `LISTFUL_DB_PATH`
- `REGISTRATION_ENABLED`
- `PUBLIC_BASE_URL`
- `MAIL_HOST`
- `MAIL_PORT`
- `MAIL_USER`
- `MAIL_PASS`

Do not print secrets such as `MAIL_PASS` into logs or chat. If an env file is used during deployment, create it with mode `0600` and remove it after `docker run` succeeds.

## Pre-deploy checks

Run from the repository root on Alice:

```bash
git status --short --branch
git log --oneline -1 --decorate
docker ps --filter name=listful-thinking-alice \
  --format 'container={{.Names}} status={{.Status}} ports={{.Ports}} image={{.Image}}'
python3 - <<'PY'
import json, subprocess
info = json.loads(subprocess.check_output(['docker', 'inspect', 'listful-thinking-alice']))[0]
print('ports=', info['HostConfig']['PortBindings'])
print('restart=', info['HostConfig']['RestartPolicy'])
print('mounts=', [(m['Type'], m.get('Name'), m.get('Destination')) for m in info['Mounts']])
print('env_keys=', sorted(e.split('=', 1)[0] for e in info['Config']['Env']))
PY
```

For schema-changing releases, copy the live database before replacing the container and test the new migration against the copy:

```bash
backup_dir="$HOME/backups/listful-thinking"
mkdir -p "$backup_dir"
stamp="$(date -u +%Y%m%dT%H%M%SZ)"
docker cp listful-thinking-alice:/app/data/listful-thinking.sqlite \
  "$backup_dir/listful-thinking-${stamp}-pre-deploy.sqlite"
```

## Build and package verification

Build the merged source into the live image tag:

```bash
docker build -t listful-thinking:alice .
```

For frontend/backend changes, inspect the packaged JAR before restarting the live container:

```bash
tmp="$(mktemp -d)"
cid="$(docker create listful-thinking:alice)"
docker cp "$cid":/app/listful-thinking.jar "$tmp/listful-thinking.jar"
docker rm "$cid" >/dev/null
python3 - <<'PY' "$tmp/listful-thinking.jar"
import sys, zipfile
jar = sys.argv[1]
with zipfile.ZipFile(jar) as z:
    names = z.namelist()
    body = '\n'.join(
        z.read(n).decode('utf-8', errors='ignore')
        for n in names
        if n.startswith('BOOT-INF/classes/static/assets/') and n.endswith('.js')
    )
    print('has V12 migration:', 'BOOT-INF/classes/db/migration/V12__add_item_responsibility_labels.sql' in names)
    print('has ownerLabel:', 'ownerLabel' in body)
    print('has assistantLabels:', 'assistantLabels' in body)
PY
rm -rf "$tmp"
```

Adjust the markers for the feature being deployed.

## Recreate container

Use the live contract, not a generic compose file, for the Alice Tailnet deployment:

```bash
docker rm -f listful-thinking-alice

docker run -d --name listful-thinking-alice --restart unless-stopped \
  -p 100.123.149.120:8080:8080 \
  -e SPRING_DATASOURCE_URL=jdbc:sqlite:/app/data/listful-thinking.sqlite \
  -e REGISTRATION_ENABLED=false \
  -e PUBLIC_BASE_URL=http://alice.taileb20d9.ts.net:8080 \
  -v listful-thinking-alice-data:/app/data \
  listful-thinking:alice
```

If the existing container has a different supported app env key/value, preserve the observed value instead of blindly using the example.

## Post-deploy verification

Wait for health:

```bash
for i in $(seq 1 60); do
  if curl -fsS http://alice.taileb20d9.ts.net:8080/api/v1/health; then
    echo "health ok after ${i}s"
    break
  fi
  sleep 1
done
```

Verify the deployed frontend asset, using the actual asset path from `/`:

```bash
curl -fsS -o /tmp/listful-live-index.html http://alice.taileb20d9.ts.net:8080/
python3 - <<'PY'
from pathlib import Path
import re, urllib.request
html = Path('/tmp/listful-live-index.html').read_text()
assert 'Listful Thinking' in html
asset = re.findall(r'src="(/assets/[^"]+\.js)"', html)[0]
body = urllib.request.urlopen('http://alice.taileb20d9.ts.net:8080' + asset, timeout=20).read().decode('utf-8', 'ignore')
for marker in ['ownerLabel', 'assistantLabels', 'Owner / responsible', 'Assistants / helpers']:
    assert marker in body, marker
    print(marker, 'ok')
PY
```

Verify live SQLite migration state after schema releases:

```bash
tmp="$(mktemp -d)"
docker cp listful-thinking-alice:/app/data/listful-thinking.sqlite "$tmp/db.sqlite"
python3 - <<'PY' "$tmp/db.sqlite"
import sqlite3, sys
con = sqlite3.connect(sys.argv[1])
print(con.execute('select version, description, success from flyway_schema_history order by installed_rank desc limit 3').fetchall())
cols = [r[1] for r in con.execute('pragma table_info(items)').fetchall()]
assert 'owner_label' in cols
assert 'assistant_labels' in cols
print('item_count=', con.execute('select count(*) from items').fetchone()[0])
PY
rm -rf "$tmp"
```

Verify the running container still matches the current image tag and Tailnet-only exposure:

```bash
python3 - <<'PY'
import json, subprocess
info = json.loads(subprocess.check_output(['docker', 'inspect', 'listful-thinking-alice']))[0]
tag = json.loads(subprocess.check_output(['docker', 'image', 'inspect', 'listful-thinking:alice']))[0]['Id']
assert info['Image'] == tag
print('running image matches tag')
PY
curl -fsS --max-time 10 http://alice.taileb20d9.ts.net:8080/api/v1/health
curl -fsS --max-time 2 http://192.168.10.126:8080/api/v1/health \
  && { echo 'unexpected LAN listener'; exit 1; } \
  || echo 'not listening on LAN'
```

A final browser smoke should load `http://alice.taileb20d9.ts.net:8080/` and show the `Listful Thinking` login screen.

## Current known deployment limitations

- Alice serves Tailnet HTTP. Internet-facing use needs HTTPS/TLS termination, secure cookies, and a public deployment hardening pass.
- Backup retention and encryption are host operations outside the repository.
- The Docker image/JAR is not signed and no SBOM is published.
- Admin support access remains intentionally metadata-focused.
