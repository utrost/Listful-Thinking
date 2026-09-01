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

1. `meta[property=og:description]`
2. `meta[name=description]`

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

These results were manually checked from the current scraper environment while validating the MVP URL grabbing behavior.

Works:

- **Manufactum** — title, description, image, and price extracted.
  - Example: `https://www.manufactum.de/bolich-aussenleuchte-a13366/`
- **Fotoimpex** — title, description, price, and product-gallery image extracted after adding gallery fallback.
  - Example: `https://www.fotoimpex.de/shop/kameras-zubehoer/filmomat-photoplug-verschlusszeiten-tester.html`
- **IKEA** — title, description, image, and price extracted.
  - Example: `https://www.ikea.com/de/de/p/myggbett-tuer-fenstersensor-smart-00603864/`
- **eBay** — title, description, image, and price extracted.
  - Example: `https://www.ebay.com/itm/188501420812`
- **MUJI** — title, description, image, and price extracted.
  - Example: `https://www.muji.eu/products/set-of-5-planted-tree-paper-notebooks-b5-4594`
- **Tchibo** — title, description, image, and price extracted.
  - Example: `https://www.tchibo.de/products/164454043257/2-paar-outdoor-socken-mit-merinowolle`

Blocked before usable product HTML reaches the scraper:

- **Zalando** — returns `HTTP 403` from Akamai / `x-edge-error: halt`; returned body does not contain product metadata.
  - Example tested: `https://en.zalando.de/pme-legend-nordrop-tapered-fit-cargo-pants-cargo-trousers-olive-night-pg322e02i-n11.html`
- **Etsy** — returns `HTTP 403` from DataDome / `x-datadome: protected`; returned body does not contain product metadata.
  - Example tested: `https://www.etsy.com/de-en/listing/1764649822/5x7-kodak-cut-film-holder`

## Failure policy

- Direct utility scrape returns a controlled validation error when the target URL cannot be fetched.
- Async item enrichment logs failures and does not roll back item creation.
- Redirects are allowed for HTTP(S), but final external content remains untrusted.
- Scraped HTML is never stored wholesale.
- Bot-protection pages are treated as non-actionable fetch failures unless they still expose useful public metadata.

## Known limitations and possible next slices

- Some shops block data-center/server-side requests before product HTML is returned. Current examples: Zalando and Etsy.
- The app does not run a browser automation stack for scraping. It uses server-side Jsoup requests only.
- No per-shop plugin architecture exists yet; selectors are centralized in `ScraperService`.
- No metadata confidence score is shown in the UI.
- No retry/proxy/cookie workflow is implemented for bot-protected shops.

Good next slices if more failures appear:

1. Add regression fixtures for each new shop markup pattern before changing selectors.
2. Split extraction into small strategy classes: social metadata, JSON-LD, microdata, gallery images, shop-specific fallbacks.
3. Add structured scrape diagnostics so the UI/admin can distinguish `blocked`, `network_error`, `no_metadata`, and `parsed`.
4. Optionally add a manual “paste metadata” workflow for shops that intentionally block server-side scraping.
