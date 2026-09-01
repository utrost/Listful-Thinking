# Product Vision

Listful Thinking is a small self-hosted household list app for people who want useful sharing without running a full productivity suite.

## Product promise

A self-hoster can start one Docker container, mount one data volume, create the first admin account, and immediately manage lists that can be private, shared with local users, or shared through a public guest link.

## What the app is

- A lightweight list manager for wishlists, to-dos, grocery lists, chores, and events.
- A family/household-friendly tool with simple sharing and clear ownership.
- A zero-config application that works with SQLite and no external services.
- A single deployable web app, not a collection of microservices.

## What the app is not

- Not a generic enterprise task-management platform.
- Not a shopping price tracker.
- Not a public marketplace.
- Not a calendar replacement.
- Not dependent on SMTP, OAuth, PostgreSQL, Redis, or a hosted account.

## Design values

- **Zero-config first:** default startup must work without separate infrastructure.
- **Privacy by default:** lists are private unless deliberately shared.
- **Predictable sharing:** every actor's permissions should be obvious.
- **Graceful degradation:** optional integrations, especially email, must have in-app fallbacks.
- **Small surface area:** prefer boring server-rendered APIs and a simple SPA over clever infrastructure.
- **Extensible domain:** list types should share infrastructure while preserving type-specific rules.

## MVP success

The MVP is successful when a self-hoster can run the container, create the first admin, keep registration private, manage local users, create useful typed lists, add a URL-only wishlist item, generate a public share link, and have a guest claim an item without another account.
