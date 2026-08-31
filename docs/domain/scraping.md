# Scraping

Scraping is a best-effort convenience feature, not a correctness dependency.

## User outcome

A user can paste a product URL into a wishlist item form and get a useful title, description, image, and price when the target page exposes metadata.

## API

Implemented:

- `POST /api/v1/utils/scrape`
- Body: `{ "url": "https://example.com/product" }`
- Reject non-HTTP(S) URLs with `400 validation_failed`.
- Requires authenticated session.

## Async item enrichment

When an item is created with a URL and no name:

1. Create the item immediately with the placeholder name `Loading metadata…`.
2. Return the created item.
3. Start asynchronous scraping.
4. Update item metadata when scraping succeeds.
5. Leave original item intact when scraping fails.

## Jsoup request requirements

Use browser-like headers:

- `User-Agent`: modern desktop browser string.
- `Accept`: `text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8`.
- `Accept-Language`: request language or system fallback.
- Timeout: around 8 seconds.

## Extraction priority

Title:

1. `meta[property=og:title]`
2. `meta[name=twitter:title]`
3. `<title>`

Description:

1. `meta[property=og:description]`
2. `meta[name=description]`

Image:

1. `meta[property=og:image]`
2. `meta[name=twitter:image]`

Price:

1. `meta[property=product:price:amount]`
2. JSON-LD `offers.price`
3. Microdata/itemprop price selectors

## Failure policy

- Network failures return a controlled error on the direct scrape endpoint.
- Async item enrichment logs failures and does not roll back item creation.
- Redirects are allowed for HTTP(S), but final URLs should still be treated as untrusted external content.
- Scraped HTML is never stored wholesale in MVP.