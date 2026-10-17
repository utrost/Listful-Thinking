# Contributing

Thanks for helping improve Listful Thinking. The focus is a small, understandable app for private self-hosted lists and reviewed website capture. Before a large change, open an issue describing the user problem and proposed behavior.

## Report and reproduce

Use the [support guide](SUPPORT.md) and the matching issue form. Extraction behavior varies by site and date, so include a public URL, the fields that were wrong, and when you tried it. Do not post private URLs, list data, access tokens, or account information. Report security issues through [SECURITY.md](SECURITY.md).

## Develop

- Fork the repository and make a focused branch from `main`.
- Java 17 and Maven run the Spring Boot backend; Node.js and npm run the Vue frontend. The Docker build uses Node 24. The [README](README.md#requirements) lists the supported local setup.
- Run `cd backend && mvn test`, then `cd frontend && npm ci && npm test && npm run build` for relevant code changes.
- For GUI behavior, run `cd frontend && npm run test:gui:smoke`; the Playwright suite starts a disposable Docker instance. For deployment edits, run the relevant `scripts/test-*-contract.sh` and `scripts/test-public-runtime.sh` in a disposable environment.
- Update documentation and English/German UI strings when behavior changes. Keep imports reviewable; do not silently trust scraped prices, dates, or titles.

Open a pull request with the user-facing change, verification performed, and any data migration or deployment impact. The CI dependency and container security scans must pass; exceptions require a separately justified security review. Contributions are licensed under [AGPL-3.0-only](LICENSE).
