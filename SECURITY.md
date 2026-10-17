# Security policy

Report suspected vulnerabilities privately through this repository's GitHub **Report a vulnerability** / private security advisory feature. Include affected version, impact, and a minimal reproduction. Do not publish exploit details or real users' data in a public issue. If private reporting is unavailable, open a public issue asking maintainers for a private reporting channel without including the vulnerability details.

The current supported branch is `main` and its most recent published release. Older release tags receive no guaranteed security updates. Operators should subscribe to repository releases and update the app, Caddy, Docker, and host OS promptly. The CI release path blocks publication when the backend dependency inventory or final container image has known high or critical advisories; exceptions are not silently applied.

For public deployment, follow the [public self-hosting runbook](docs/deployment/public-self-hosting.md). Protect the setup code, SMTP secret, SQLite volume, backups, Docker socket, and registry credentials. Public guest links are bearer credentials and should be shared with care. The app does not provide end-to-end encryption or high availability.
