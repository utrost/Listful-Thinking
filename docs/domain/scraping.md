# URL Metadata Grabbing / Scraping

Scraping is a best-effort convenience feature, not a correctness dependency. It is used to prefill editable wishlist item fields from product URLs.

This document describes what exists in code today, not a promise that every shop will work forever.

## User outcome

A user can paste a product URL into a `WISH` item form and get useful metadata when the target page exposes it:

- item name / product title
- description
- image URL
- price

All fetched fields remain editable by the user. Async enrichment only fills missing fields and must not overwrite values the user already entered.

## API and authentication

Implemented direct utility endpoint:

- `POST /api/v1/utils/scrape`
- Body: `{ "url": "https://example.com/product" }`
- Rejects non-HTTP(S) URLs with `400 validation_failed`.
- Requires an authenticated session.

Normal wishlist item creation can also trigger async enrichment without calling the utility endpoint directly.

## Implementation approach

The scraper does **not** parse full HTML with regex.

Current implementation uses:

- **Jsoup** to fetch and parse HTML as a document.
- **CSS selectors** to read OpenGraph, Twitter card, microdata, and product/gallery elements.
- **Jackson** to parse JSON-LD blocks and walk nested `offers.price` structures.
- A small regex only after extraction to normalize candidate price text, e.g. converting `39,90 €` into `39.90`.

Request behavior:

- Browser-like desktop `User-Agent`.
- HTML-oriented `Accept` header.
- German-first `Accept-Language` fallback: `de-DE,de;q=0.9,en-US;q=0.8,en;q=0.7`.
- Additional browser navigation headers.
- Redirects enabled.
- Timeout: 8 seconds.
- Full body allowed via Jsoup `maxBodySize(0)`.

## Async item enrichment

When a `WISH` item is created with a URL and missing metadata:

1. Create the item immediately.
2. Use the placeholder name `Loading metadata…` only when the user did not provide a name.
3. Return the created item to the UI.
4. Start asynchronous scraping.
5. Update only missing item metadata when scraping succeeds.
6. Leave the original item intact when scraping fails.

Fields filled by async enrichment:

- `name` only if it is still the placeholder.
- `description` only if blank.
- `image_url` only if blank.
- `price` only if null.

## Extraction priority

Title:

1. `meta[property=og:title]`
2. `meta[name=twitter:title]`
3. Amazon-style `#productTitle`
4. `<title>`, excluding generic `Amazon.de` / `Amazon.com`

Description:

1. Product-body descriptions when present:
   - `[itemprop=description]`
   - `.productView-description`
   - `.product-description`
   - `.product-detail-description`
   - `#productDescription`
2. `meta[property=og:description]`
3. `meta[name=description]`

Product-body descriptions are preferred over generic shop-wide OpenGraph descriptions, because some storefronts expose precise product copy in the body while their social description remains a generic brand blurb.

Image:

1. `meta[property=og:image]`
2. `meta[name=twitter:image]`
3. Amazon-style `#landingImage[data-old-hires]`
4. Amazon-style `#landingImage[src]`
5. Generic/product gallery fallback:
   - `img[itemprop=image]`
   - `.product img[src]`
   - `.product-detail img[src]`
   - `.os_detail_galmain[src]`
6. For gallery images inside a link, prefer the enclosing `a[href]` when it points to a likely image file (`.jpg`, `.jpeg`, `.png`, `.webp`, `.gif`). This lets Fotoimpex-style thumbnail galleries resolve to the larger product image.

Price:

1. `meta[property=product:price:amount]`
2. JSON-LD `offers.price`
3. `[itemprop=price][content]`
4. `[itemprop=price]` text
5. Amazon-style visible `.a-price` fallback

Price text is normalized after extraction by removing non-number/currency punctuation and converting comma decimals to dot decimals.

## Observed shop status

These results were manually checked from the current scraper environment while validating the URL grabbing behavior. Live shop behavior changes over time; keep deterministic regression tests as local fixtures and treat this matrix as dated field evidence.

### 2026-09-03 smoke matrix

Works:

- **Manufactum** — title, description, image, and price extracted from OpenGraph/product-price metadata.
  - Example: `https://www.manufactum.de/bolich-aussenleuchte-a13366/`
- **MUJI** — title, product-body description, image, and price extracted. This run found a parser gap: OpenGraph description was a generic shop blurb while `.productView-description` contained the useful product copy. The gap is now covered by a regression fixture.
  - Example: `https://www.muji.eu/products/set-of-5-planted-tree-paper-notebooks-b5-4594`

Not parser-actionable in this run:

- **Fotoimpex** — the previously used `filmomat-photoplug-verschlusszeiten-tester-fuer-analoge-kameras.html` URL redirected to `/?err=404`; returned category/listing HTML had no tested product markers.
- **IKEA** — the previously used `myggbett-matratzenschoner-weiss-30461668` URL returned the generic products/category page rather than that product detail page; body had no tested article ID/product markers.
- **Tchibo** — the tested outdoor-socks URL returned `HTTP 404`.
- **eBay** — returned `HTTP 403` from Akamai before usable product HTML.
- **Thalia**, **Bauhaus**, **Decathlon**, and **Conrad** — returned Cloudflare/security-check pages before usable product HTML.
- **Zalando** and **Etsy** — still blocked before usable product HTML reaches the scraper.

### 2026-09-01 historical evidence

Earlier smoke runs had working examples for Fotoimpex, IKEA, eBay, MUJI, and Tchibo, plus blocked Zalando/Etsy cases. Keep the lesson, but re-check live URLs before using old examples as proof of current shop support.

## Failure policy

- Direct utility scrape returns a controlled validation error when the target URL cannot be fetched.
- Async item enrichment logs failures and does not roll back item creation.
- Redirects are allowed for HTTP(S), but final external content remains untrusted.
- Scraped HTML is never stored wholesale.
- Bot-protection pages are treated as non-actionable fetch failures unless they still expose useful public metadata.

## Known limitations and possible next slices

- Some shops block data-center/server-side requests before product HTML is returned. Current examples include Cloudflare/security-check pages from Thalia, Bauhaus, Decathlon, and Conrad plus earlier Zalando/Etsy bot-protection responses.
- Shop URLs age out or redirect to generic/category/404 pages; this is distinct from a parser gap and needs a fresh product URL before selector work.
- The app does not run a browser automation stack for scraping. It uses server-side Jsoup requests only.
- No per-shop plugin architecture exists yet; selectors are centralized in `ScraperService`.
- No metadata confidence score is shown in the UI.
- No retry/proxy/cookie workflow is implemented for bot-protected shops.

Good next slices if more failures appear:

1. Add regression fixtures for each new shop markup pattern before changing selectors.
2. Split extraction into small strategy classes: social metadata, JSON-LD, microdata, gallery images, shop-specific fallbacks.
3. Add structured scrape diagnostics so the UI/admin can distinguish `blocked`, `network_error`, `no_metadata`, and `parsed`.
4. Optionally add a manual “paste metadata” workflow for shops that intentionally block server-side scraping.
