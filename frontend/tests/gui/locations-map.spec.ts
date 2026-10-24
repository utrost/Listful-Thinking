import { expect, test } from '@playwright/test';
import { bootstrapOrLoginAdmin, createList } from './helpers';

test('reviewed place links become map pins only when the map opens @locations', async ({ page }) => {
  test.setTimeout(90_000);
  await bootstrapOrLoginAdmin(page);
  await createList(page, `Berlin places ${Date.now()}`, 'LOCATION');
  let tileRequests = 0;
  let tileReferer = '';
  await page.route('https://tile.openstreetmap.org/**', async route => {
    tileRequests++;
    tileReferer = route.request().headers()['referer'] ?? '';
    await route.fulfill({ contentType: 'image/png', body: Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVQIHWP4z8DwHwAFgAI/ScL8GQAAAABJRU5ErkJggg==', 'base64') });
  });
  await page.route('**/api/v1/utils/extract', async route => route.fulfill({ json: {
    kind: 'PLACE', sourceUrl: 'https://www.visitberlin.de/en/museum-island', extractedAt: new Date().toISOString(),
    title: 'Museum Island', description: 'Five museums', imageUrl: null, price: null, priceCurrency: null,
    seller: null, availability: null, ingredients: [], steps: [], eventStart: null, eventLocation: null,
    address: 'Bodestraße 1, 10178 Berlin', latitude: 52.5201, longitude: 13.3976
  } }));

  await page.locator('.item-composer input[aria-label="URL"]').fill('https://www.visitberlin.de/en/museum-island');
  await page.getByRole('button', { name: 'Add item' }).click();
  const review = page.locator('.capture-review');
  await expect(review.getByLabel('Save as')).toHaveValue('LOCATION');
  await expect(review.getByLabel('Address')).toHaveValue('Bodestraße 1, 10178 Berlin');
  await expect(review.getByLabel('Latitude')).toHaveValue('52.5201');
  await review.getByRole('button', { name: 'Save reviewed draft' }).click();
  await expect(page.locator('.item-list')).toContainText('Museum Island');
  expect(tileRequests).toBe(0);

  await page.getByRole('button', { name: 'Map', exact: true }).click();
  await expect(page.locator('.leaflet-marker-icon')).toHaveCount(1);
  await expect(page.locator('.location-map-index')).toContainText('Museum Island');
  await expect(page.locator('.location-map-index a')).toHaveAttribute('href', /openstreetmap\.org.*mlat=52\.520100/);
  await expect.poll(() => tileRequests).toBeGreaterThan(0);
  expect(tileReferer).toBe(new URL(page.url()).origin + '/');

  await page.getByRole('button', { name: 'List', exact: true }).click();
  await expect(page.locator('.item-list')).toContainText('Bodestraße 1, 10178 Berlin');
});

test('map links retain coordinates in a manual draft @locations', async ({ page }) => {
  await bootstrapOrLoginAdmin(page);
  await page.route('**/api/v1/utils/extract', async route => route.fulfill({
    status: 502, contentType: 'application/json', body: '{"code":"extract_failed","message":"Page unavailable"}'
  }));
  await page.getByRole('button', { name: 'Save from a website' }).click();
  await page.locator('.capture-form').getByLabel('Page URL').fill('https://www.openstreetmap.org/#map=16/52.5201/13.3976');
  await page.locator('.capture-form').getByRole('button', { name: 'Read page' }).click();
  const review = page.locator('.capture-review');
  await expect(review.getByLabel('Save as')).toHaveValue('LOCATION');
  await expect(review.getByLabel('Latitude')).toHaveValue('52.5201');
  await expect(review.getByLabel('Longitude')).toHaveValue('13.3976');
  await review.getByRole('textbox', { name: 'Title', exact: true }).fill('Museum Island');
  await review.getByLabel('New list title').fill('Berlin map links');
  await review.getByRole('button', { name: 'Save reviewed draft' }).click();
  await expect(page.getByRole('heading', { name: 'Berlin map links' })).toBeVisible();
  await expect(page.locator('.item-list')).toContainText('Museum Island');
});
