# Public self-hosting with Docker

The repository ships a Caddy and app Compose profile for one small, public HTTPS instance. Each self-hoster runs their own SQLite database. This is not a multi-tenant service or a high-availability cluster.

The [Self-Hosting Handbook](self-hosting-handbook.md#public-https-example-with-caddy) includes the complete `compose.public.yml` example and checks that the embedded copy matches the shipped file. The [README screenshots](../../README.md#screenshots) show the owner workspace and website review flow using fictional data.

## Before starting

Use a host with Docker Compose v2, a DNS name pointing to it, and inbound TCP ports 80 and 443. Install a versioned image that passed CI. Public images are published from `v*` tags after backend, frontend, GUI, dependency, container smoke, and strict container vulnerability checks. CI builds amd64 and arm64, attaches SBOM and provenance, and signs the image digest. The app is [AGPL-3.0-only](../../LICENSE) and offers a visible link to the matching release source. The RC3 GHCR image is public and was checked with an anonymous manifest request.

```bash
cp .env.public.example .env.public
# Set LISTFUL_DOMAIN, PUBLIC_BASE_URL and LISTFUL_IMAGE to your values.
scripts/create-bootstrap-secret.sh
docker compose --env-file .env.public -f compose.public.yml config --quiet
docker compose --env-file .env.public -f compose.public.yml up -d
```

For RC3, pin `LISTFUL_IMAGE=ghcr.io/utrost/listful-thinking@sha256:c16ef4edd27a6ff367e84ef8451d6d7a00ce10d644259939887b2a786ecbe743`. The [tag workflow](https://github.com/utrost/Listful-Thinking/actions/runs/39059482487) passed all gates and the [GitHub prerelease](https://github.com/utrost/Listful-Thinking/releases/tag/v0.2.0-rc.3) has a checksummed source archive. The app service has no host port. Caddy gets the HTTPS certificate and overwrites inbound `X-Forwarded-For` before the app uses client IPs for rate limiting. Keep this topology intact; a different proxy needs the same header boundary. The app checks that public hosting has an HTTPS origin, secure cookies, CSRF and rate limiting on, and private-address scraping off.

### Verify RC3 before installing

With [Cosign](https://docs.sigstore.dev/cosign/verifying/verify/) and Docker Buildx installed, these commands verify the image signature's GitHub Actions identity and inspect the image's per-platform SBOM:

```bash
image=ghcr.io/utrost/listful-thinking@sha256:c16ef4edd27a6ff367e84ef8451d6d7a00ce10d644259939887b2a786ecbe743
cosign verify \
  --certificate-identity 'https://github.com/utrost/Listful-Thinking/.github/workflows/ci.yml@refs/tags/v0.2.0-rc.3' \
  --certificate-oidc-issuer 'https://token.actions.githubusercontent.com' "$image"
docker buildx imagetools inspect "$image" --format '{{json .SBOM}}' > rc3-sbom.json
```

Both commands succeeded on 2026-10-17; the SBOM output contained `linux/amd64` and `linux/arm64`. The downloaded [release archive and checksum](https://github.com/utrost/Listful-Thinking/releases/tag/v0.2.0-rc.3) passed `sha256sum -c SHA256SUMS.txt`. A matching signature proves which workflow signed the digest; it does not prove the application or every dependency is free of defects. Keep the digest pinned and recheck each new release.

Open `https://YOUR_DOMAIN`, choose Register, and enter the setup code from `secrets/bootstrap-token` to create the first admin. Treat that file as a password and keep it in place for future Compose restarts, even though the code is no longer used after an admin exists. The `secrets/` directory is mode 0700 and excluded from Git. Its files are mode 0444 so the app's UID 1000 can read the Compose mounts even when the host operator has another UID; the private parent directory prevents other host users from opening the files. An instance without a valid setup code cannot create its first admin. After bootstrap, keep registration disabled and create people from Admin.

The database lives in the named Compose volume mounted at `/app/data`. The app runs as UID 1000 with a read-only root filesystem, a bounded temporary filesystem, dropped capabilities, and memory and process limits. Caddy stores certificates in its own volume. Back up the database and the Caddy data volume if certificate continuity matters. Scraping allows two concurrent outbound page fetches; a busy server returns HTTP 429 so the caller can retry. Block outbound access to your host's private services at the host or network boundary as another defense against URL scraping abuse.

In source builds after RC3, `LOCATION` and `TRIP` lists can show map pins. Map tiles are requested only when a signed-in member opens Map. The default is the public OpenStreetMap tile service; keep its attribution visible and follow the [tile usage policy](https://operations.osmfoundation.org/policies/tiles/). For a busy public instance, set `LISTFUL_MAP_TILE_URL` to an HTTPS `{z}/{x}/{y}` template from a provider whose terms and capacity fit your traffic, and set `LISTFUL_MAP_ATTRIBUTION` to its required attribution. These are runtime settings in Compose. The app does not call the public Nominatim geocoder; address-only places need coordinates entered manually.

## Backups and upgrades

`scripts/backup-public.sh` takes a live SQLite online snapshot from the mounted volume, checks database integrity and foreign keys, and writes a SHA-256 checksum. Run it once immediately and copy the result to encrypted storage off this host.

```bash
scripts/backup-public.sh
(cd backups && sha256sum -c listful-thinking-YYYYMMDDTHHMMSSZ.sqlite.sha256)
```

The backup directory must be writable by UID 1000 because the backup helper runs with the same UID as the app. For a host user with another UID, create `backups/`, set its owner to 1000 with `sudo chown 1000:1000 backups`, and set mode 0700. The checksum is generated inside the helper container so the host backup scheduler does not need file read access. Set `LISTFUL_BACKUP_DIR` to another absolute path if desired. Schedule a daily backup with your host's systemd timer or cron, monitor failures, rotate old copies, and test a restore regularly. A backup only on the same host does not protect against host loss.

Before an upgrade, change `LISTFUL_IMAGE` in `.env.public` to the new tag or digest, then run:

```bash
scripts/upgrade-public.sh
```

The script refuses to upgrade without first producing a verified backup, pulls the new image, recreates containers, and waits for database readiness. Then verify login and representative list data through HTTPS. Read release notes for schema changes; Flyway migrations are forward-only. A downgrade may require restoring the matching pre-upgrade database snapshot.

For a restore, stop the app, keep the old volume untouched, create a fresh Compose project/volume, and copy a checked backup into that volume as `listful-thinking.sqlite` owned by UID 1000 with mode 0600. Start the candidate on a private address first, confirm readiness and data, then move traffic. Never copy just the main SQLite file while the app is running, and never mix a restored database with older `-wal` or `-shm` files. The [general restore walkthrough](self-hosting-handbook.md#test-a-restore-without-replacing-production) uses a bind-mounted isolated instance.

## Optional email

The app works without SMTP. For SMTP authentication, run `scripts/create-mail-secret.sh` and add `-f compose.public.smtp.yml` to manual Compose commands. The upgrade script detects that secret file and includes the overlay automatically. Set `MAIL_HOST`, `MAIL_PORT`, `MAIL_USER`, `MAIL_AUTH=true`, `MAIL_STARTTLS=true`, and `MAIL_FROM` in `.env.public`. The overlay mounts the password as a file, so it does not appear in Compose's environment rendering. The entrypoint loads it into the app process at startup. Protect Docker host access and container process environments as part of normal host administration.

If you build a modified image, pass `--build-arg SOURCE_CODE_URL=https://YOUR_SOURCE_REPOSITORY/tree/YOUR_TAG` to `docker build`. This sets the source link shown to users and the image source label. Publish the corresponding modified source and retain the AGPL license notice.

## Public checks

Verify the HTTPS health endpoint, HSTS, secure session cookie, blocked direct port 8080, signup setup-code rejection, and a successful login. The [handbook checklist](self-hosting-handbook.md#public-https-verification-checklist) has commands. Protect the Docker socket, image registry credentials, `.env.public`, `secrets/`, and backups. Patch the host, Docker engine, Caddy image, and application image as releases appear. Use [SECURITY.md](../../SECURITY.md) to report vulnerabilities privately.
