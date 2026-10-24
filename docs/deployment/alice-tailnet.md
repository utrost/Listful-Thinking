# Alice Tailnet Deployment

This document records the current private deployment contract for the Listful Thinking instance on Alice and the safe redeploy procedure.

The Alice instance is a private Tailnet MVP deployment, not a public-internet production profile.

## Latest deployment — locations and maps, 2026-10-24

Merged `main` commit `150f4ea` (PR [#70](https://github.com/utrost/Listful-Thinking/pull/70)) is deployed as `listful-thinking:alice`, also tagged `listful-thinking:locations-20261024-150f4ea`. Image ID: `sha256:04290cf68b553a57a7494ce560400b8ffa33e4ac5d0f199421a8cd100e7b0cbe`. This adds `LOCATION` lists, addresses and coordinates for city-trip places, reviewed address extraction from websites, and OpenStreetMap list/map views. An anonymous public link to a city-trip or locations list exposes its exact addresses and map pins; the Sharing screen warns owners before they enable that access.

All seven [PR CI jobs](https://github.com/utrost/Listful-Thinking/actions/runs/40098428157) passed, including the strict dependency and container scans, backend and frontend suites, container smoke, contract checks, and desktop/mobile Playwright GUI smoke. The deployed image passed a second isolated migration preflight after merge. A stopped-container backup was taken at cutover. The live database migrated from V18 to V20; a consistent post-deployment snapshot passed SQLite integrity and foreign-key checks and retained the original 1 user, 1 list, 3 items, and 0 shares by identity. Readiness, image ID, UID 1000, restart policy, persistent volume, Tailnet-only port binding, frontend assets, anonymous map configuration, and read-only desktop/mobile Chromium login pages passed. The browser checks did not create live data.

Private preflight, cutover, and post-deployment records are in `data/deployments/20261024T132550Z-locations-v20/`. The cutover backup is `alice-cutover-pre-v20.sqlite`; the V20 verification snapshot is `alice-post-v20.sqlite`. The previous container is retained stopped as `listful-thinking-alice-pre-v20-20261024T133640Z`, with restart disabled. To roll back to V18, preserve any data created since cutover, stop and remove the V20 container, restore the cutover V18 database snapshot to the volume, and start the previous container. Do not run the V18 image against the V20 database.

## Previous deployment — reviewed website capture, 2026-10-17

Merged `main` commit `ea97030` (PR #53) is deployed from `listful-thinking:web-capture-3737973`; the same image is tagged `listful-thinking:web-capture-20261017-ea97030` and `listful-thinking:alice`. Its ID is `sha256:224ce4e3ec395f78ba8947e67810300f0fa469090fce30d43f09de3fca7d0106`. It adds reviewed product/recipe/event/how-to capture, wishlist product details, and guest claim controls. All six PR and [merged-main CI jobs](https://github.com/utrost/Listful-Thinking/actions/runs/39014873586) passed, including strict dependency scanning and desktop/mobile Playwright smoke.

A stopped-container pre-V18 backup passed SQLite `integrity_check` and `foreign_key_check`. The image migrated an isolated copy from V17 to V18, preserving 1 account, 1 list, and 3 items. A fresh stopped-container backup was taken at cutover. The live V18 database passed integrity, foreign-key, column, and count checks with the same records. Readiness, V18 frontend assets, desktop/mobile Chromium login pages, original application settings, UID 1000, database permissions 0700/0600, and Tailnet-only binding passed. Private backups, previous-container configuration, migration preflight, verification copies, and checksums are in `data/deployments/20261017T074317Z-web-capture-v18/` (`verification.json`).

The previous V17 container is retained stopped as `listful-thinking-alice-pre-v18-20261017T074707Z`, with restart disabled. To roll back to V17, stop and remove the V18 container, restore `listful-thinking-cutover-pre-v18.sqlite` to the volume, then start the previous container. Preserve any data created after cutover before restoring the snapshot. Do not run the V17 image against the upgraded V18 database.

## Previous deployment — account and list handoffs, 2026-10-16

Merged `main` commit `f5b6b64` was deployed as `listful-thinking:handoff-20261016-f5b6b64` (previously tagged `listful-thinking:alice`), image ID `sha256:c387463d10361416fa3a75b334cfc4a902f2827fc6112b05bd07e4ddfd04d0ab`. It includes the Playwright user/admin journeys from PR #51 and account setup, recovery, password change, sharing notifications, owner transfer, and admin transfer of inactive owners' lists from PR #52. All six CI jobs passed on the merged feature PR, including the strict dependency scan and desktop/mobile Playwright smoke.

A stopped-container pre-deploy SQLite backup and a post-deploy copy both passed `integrity_check` and `foreign_key_check`; the schema remains V17 with 1 account, 1 list, and 3 items. Live readiness, current frontend assets, desktop/mobile Chromium login pages, preserved application settings, UID 1000 and database permissions, and Tailnet-only binding passed. Private backups, configuration, and verification are in `data/deployments/20261016T211037Z/`. The previous image/container is retained stopped as `listful-thinking-alice-pre-handoff-20261016` with restart disabled. Because the schema did not change, that container can be used for rollback after preserving any newer data.

The first cutover attempt stopped the service, then a deployment script misread Flyway's text version order and its rollback handler removed the stopped container. The original image was recreated using its saved settings and unchanged volume; readiness and all application settings were verified before the successful retry. The first attempt's private artifacts are in `data/deployments/20261016T210946Z/`.

## Previous deployment — security upgrade and retention policy, 2026-10-16

Merged `main` commit `74de4dc` was deployed as `listful-thinking:retention-20261016-74de4dc` (then tagged `listful-thinking:alice`), image ID `sha256:1c0ef5013bd683e3a2180dfdfb349c8a4fca76552910e8412ad9f4bca2ea96a8`. This includes the runtime and HTML-parser security upgrade from PR #50 and the 14-day Trash and 56-day public-link retention policy from PR #49.

A stopped-container pre-V17 SQLite backup was verified with `integrity_check` and `foreign_key_check`. V17 passed on an isolated restore and then on the live volume while the candidate was bound only to localhost. The migration preserved 1 account, 1 list, and 3 items. All six CI jobs passed on the deployed commit. Live health, Tailnet-only binding, frontend retention copy, and a Chromium login-page check passed. The runtime remains UID 1000; the SQLite directory and file retain modes 0700 and 0600. The existing application settings, including email recovery, were preserved.

Private backups, the original container configuration, migration preflight, and deployment verification are in `data/deployments/20261016T185002Z/`. The final backup is `listful-thinking-final-pre-v17.sqlite`. The previous RC2 container is retained stopped as `listful-thinking-alice-pre-v17-20261016` with restart disabled. Rollback to RC2 requires restoring the matching pre-V17 database backup before starting that container; do not run the RC2 image against V17.

## Previous deployment — 0.2.0-rc.2 privacy/security fixes, 2026-09-26

Image `listful-thinking:v0.2.0-rc.2`, then tagged `listful-thinking:alice`, ID `sha256:e20e3b4c1c97a411c8ac8fae65dc153a84c62c8edc2642ffe65a4a4f1e105c1e`.

See the [security review](../security-review-2026-09-26.md) and [RC2 notes](../releases/0.2.0-rc.2.md). V15→V16 migration was verified on a separate copy before deployment. Live readiness, SQLite integrity/foreign keys, original counts (1 account, 1 list, 3 items), non-root runtime, preserved binding/volume and enabled email recovery passed. SQLite is mode 0600 and its directory is 0700. Desktop/mobile login/recovery UI, cache headers and layout passed. SMTP hostname-verified STARTTLS and authentication passed without sending email.

The private snapshot and deployment record are in `data/deployments/20260926T201735Z/`. The previous image/container is retained stopped with restart disabled. Rollback to RC1 requires that image and its matching V15 snapshot. Existing SMTP configuration and the Tailscale-address binding were preserved.

## Previous deployment — 0.2.0-rc.1, 2026-09-26 19:46 UTC

Image `listful-thinking:v0.2.0-rc.1`, also tagged `listful-thinking:alice`, ID `sha256:d438e808352f0e3a6e8ac258fd97cdb52884fb408b8293de794fc8b46d082c2e`.

The candidate adds Today & upcoming, archive/Undo and persistent Trash recovery, and private reusable templates to the September fixes and redesigned workspace. See [release notes](../releases/0.2.0-rc.1.md) and [verification](../release.md).

V14→V15 migration passed on a separate copy of the live database before cutover. The final image includes bounded import-write contention retries verified by regression tests. The deployed database passes SQLite integrity and foreign-key checks; the original 1 account, 1 list and 3 items remain. Database readiness, non-root runtime, preserved volume/port binding and enabled email recovery passed. SMTP configuration was preserved; no additional recovery emails were sent. Desktop/mobile live login pages passed layout and browser-error checks.

Private original configuration, source archive, migration preflight, consistent stopped backup, deployment and verification records are in `data/deployments/20260926T194643Z/`. The V14→V15 migration preflight and original V14 backup are retained separately in `data/deployments/20260926T194124Z/`. Previous containers are stopped with restart disabled. Rollback across schema versions requires the previous image and its matching database snapshot; never run a V14 image directly against V15.

## Previous deployment — focused list workspace, 2026-09-26 11:57 UTC

The tested image `listful-thinking:ux-20260926` was also tagged `listful-thinking:alice` at that time. Image ID: `sha256:06e90f829ed0bb4745960a3f5a0c694d45c78ae2d42863c8b62d8526199923e6`.

The [user experience concept](../design/user-experience.md) records the design and iteration process. The deployed interface separates Items, Sharing, List settings and Administration; uses desktop list navigation and a mobile selector; adds guidance, empty states and progressive item forms; and remembers language and list selection.

Validation: 63 frontend tests, 14 active desktop/mobile Playwright journeys (6 deliberate duplicate-platform skips), plus both focused deletion-navigation checks passed. The final production image was built by the focused Docker/Playwright run. Live desktop/mobile browser checks, current frontend assets, container health, database integrity, foreign keys, and unchanged counts passed. Existing SMTP configuration and Tailnet-only access were preserved. No extra recovery emails were sent during this UX verification.

Private source archive, consistent pre-upgrade backup, original container configuration and release/verification records: `data/deployments/20260926T115755Z/`. The previous container is retained stopped, with restart disabled. This frontend revision keeps schema V14; the private rollback note explains how to preserve current data when reverting.

## Previous deployment — recovery fix, 2026-09-26 10:24 UTC

The image was `listful-thinking:recovery-20260926`, then tagged `listful-thinking:alice`, ID `sha256:ad7746e61f0f16bc0345f1e60b1ebd04bdf1d070a3e8857258ac36830abf912e`.

Recovery now has its own email input validation and optional username, separate from password login. Reset-token pages have their own validated new-password form. The public auth settings report email availability. Missing SMTP configuration disables recovery with an explanation; failed SMTP submission returns an explicit error and rolls back token creation.

Verification: 63 frontend tests and 13 backend auth tests passed. An isolated Docker instance and loopback SMTP receiver verified browser magic-link login, password-reset email, token consumption, and login using the changed password. Live mobile UI and Docker readiness passed. Existing data remains 1 account, 1 list, and 3 items.

**SMTP enabled on 2026-09-26 at 11:32 UTC.** Outgoing mail uses `mail.your-server.de:587`, authenticated as `selfhosted@simiono.com`, with required STARTTLS and the same sender address. TLS certificate validation and authentication passed. Both live recovery endpoints successfully submitted an email for the existing account (HTTP 204); inbox receipt has not been independently checked. Recovery is enabled in the live auth settings. Credentials are in the container configuration only; do not copy them into this document. The configuration-change backup and verification record are in `data/deployments/20260926T113224Z/`.

The consistent pre-upgrade backup, original configuration, and release record are in `data/deployments/20260926T102433Z/`. The previous container is retained stopped with restart disabled. Preserve any newer data before rollback, and restore the corresponding database snapshot together with the previous container.

## Previous deployment — review fixes, 2026-09-26

The local review remediation build was deployed as `listful-thinking:alice` (also tagged `listful-thinking:review-20260926`). Image ID: `sha256:aabe46367fdde6473e454800e1c5d36890118b75671388dc6aedc7ad8fc2cbbb`.

This build includes the uncommitted fixes based on `7778df4`, including the final reminder rescheduling correction. Its exact source archive, original container configuration, verified pre-upgrade database, and release/verification records are stored privately under `data/deployments/20260926T092453Z/` in this checkout.

Verified after deployment: Docker healthy, database readiness, schema V14, SQLite integrity and foreign keys, unchanged counts (1 account, 1 list, 3 items), current frontend assets, and browser login page. The volume and Tailnet-only port binding are unchanged. The old container is retained stopped with restart disabled; rollback requires its matching database snapshot, not merely starting the old image against V14.

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

For schema-changing releases, stop the application before copying its database, restart it after the copy, and test the new migration against that copy. Alternatively, use the SQLite online backup helper on a readable bind mount. Do not copy a running SQLite file with plain `docker cp`:

```bash
backup_dir="$HOME/backups/listful-thinking"
mkdir -p "$backup_dir"
stamp="$(date -u +%Y%m%dT%H%M%SZ)"
docker stop --time 30 listful-thinking-alice
docker cp listful-thinking-alice:/app/data/listful-thinking.sqlite \
  "$backup_dir/listful-thinking-${stamp}-pre-deploy.sqlite"
docker start listful-thinking-alice
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
    assert 'BOOT-INF/classes/db/migration/V18__web_capture_and_claims.sql' in names
    assert 'BOOT-INF/classes/app/listful/lists/ListOwnershipService.class' in names
    for marker in ['Read page', 'Save reviewed draft', 'Exact size, colour, model, or buyer note']:
        assert marker in body, marker
    print('V18 and website capture backend/frontend markers OK')
PY
rm -rf "$tmp"
```

Adjust the markers for the feature being deployed.

## Recreate container

Use the live contract, not a generic compose file, for the Alice Tailnet deployment:

```bash
umask 077
env_file="$(mktemp /tmp/listful-alice-env.XXXXXX)"
python3 - "$env_file" <<'PY'
import json, os, subprocess, sys
info = json.loads(subprocess.check_output(['docker', 'inspect', 'listful-thinking-alice']))[0]
image_defaults = {'PATH', 'JAVA_HOME', 'JAVA_VERSION', 'LANG', 'LANGUAGE', 'LC_ALL'}
settings = [entry for entry in info['Config']['Env'] if entry.split('=', 1)[0] not in image_defaults]
assert all('\n' not in entry and '\r' not in entry for entry in settings)
with open(sys.argv[1], 'w') as out:
    out.write('\n'.join(settings) + '\n')
os.chmod(sys.argv[1], 0o600)
PY
old_name="listful-thinking-alice-pre-$(date -u +%Y%m%dT%H%M%SZ)"
docker stop --time 30 listful-thinking-alice
docker update --restart no listful-thinking-alice
docker rename listful-thinking-alice "$old_name"
docker run -d --name listful-thinking-alice --restart unless-stopped \
  --user 1000:1000 --env-file "$env_file" \
  -p 100.123.149.120:8080:8080 \
  -v listful-thinking-alice-data:/app/data \
  listful-thinking:alice
rm -f "$env_file"
```

Check readiness before considering the cutover complete. If a schema-changing candidate fails after starting, stop and remove only that new container, restore the matching pre-migration database snapshot to the volume, then rename `$old_name` back to `listful-thinking-alice`, restore its `unless-stopped` restart policy, and start it. Keep the backup and previous image until post-deploy verification is complete. Flyway versions are text: query the latest successful row by `installed_rank`, not `max(version)`.

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
for marker in ['Read page', 'Save reviewed draft', 'Exact size, colour, model, or buyer note']:
    assert marker in body, marker
    print(marker, 'ok')
PY
```

Verify SQLite migration state after schema releases. The following file-copy check requires a brief stop; for uninterrupted verification, use a read-only SQLite client connected to the mounted volume:

```bash
tmp="$(mktemp -d)"
docker stop --time 30 listful-thinking-alice
docker cp listful-thinking-alice:/app/data/listful-thinking.sqlite "$tmp/db.sqlite"
docker start listful-thinking-alice
python3 - <<'PY' "$tmp/db.sqlite"
import sqlite3, sys
con = sqlite3.connect(sys.argv[1])
assert con.execute('pragma integrity_check').fetchone()[0] == 'ok'
assert con.execute('pragma foreign_key_check').fetchall() == []
version = con.execute('select version from flyway_schema_history where success=1 order by installed_rank desc limit 1').fetchone()[0]
assert version == '18', version
print('schema=', version)
print('counts=', {table: con.execute(f'select count(*) from {table}').fetchone()[0] for table in ('users', 'lists', 'items')})
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
- The Docker image/JAR is not signed; RC2 included a resolved runtime SBOM.
- Admin support access remains intentionally metadata-focused.
