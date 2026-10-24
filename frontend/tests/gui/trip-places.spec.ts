import { expect, test, type Page } from '@playwright/test';
import { bootstrapOrLoginAdmin, collectPageDiagnostics, createList } from './helpers';

async function login(page: Page, username: string, password: string) {
  await page.goto('/');
  const form = page.locator('form.panel').filter({ has: page.getByRole('heading', { name: 'Log in', exact: true }) });
  await form.getByLabel('Username').fill(username);
  await form.getByLabel('Password').fill(password);
  await form.getByRole('button', { name: 'Log in' }).click();
}

test('friends add reviewed places to a shared city trip @sharing @trip', async ({ page, browser }) => {
  test.setTimeout(90_000);
  const diagnostics = collectPageDiagnostics(page);
  await bootstrapOrLoginAdmin(page);
  const username = `traveler-${Date.now()}`;
  const readerName = `reader-${Date.now()}`;
  const password = 'traveler-test-password';
  expect((await page.request.post('/api/v1/admin/users', { data: { username, password, role: 'USER' } })).status()).toBe(201);
  expect((await page.request.post('/api/v1/admin/users', { data: { username: readerName, password, role: 'USER' } })).status()).toBe(201);

  const title = `Berlin weekend ${Date.now()}`;
  await createList(page, title, 'TRIP');
  await page.getByRole('button', { name: 'Sharing', exact: true }).click();
  const shareForm = page.locator('.share-panel form.inline-form').first();
  await expect(shareForm.getByLabel('Share permission')).toHaveValue('CONTRIBUTE');
  await shareForm.getByLabel('Username').fill(username);
  await shareForm.getByRole('button', { name: 'Share access' }).click();
  await expect(page.locator('.share-panel .chip-list')).toContainText(`${username} · CONTRIBUTE`);
  await shareForm.getByLabel('Username').fill(readerName);
  await shareForm.getByLabel('Share permission').selectOption('READ');
  await shareForm.getByRole('button', { name: 'Share access' }).click();

  const context = await browser.newContext({ baseURL: new URL(page.url()).origin, viewport: page.viewportSize()! });
  const traveler = await context.newPage();
  await login(traveler, username, password);
  await expect(traveler.getByRole('heading', { name: title })).toBeVisible();
  const placeUrl = 'https://www.visitberlin.de/en/museum-island';
  await traveler.locator('.item-composer input[aria-label="URL"]').fill(placeUrl);
  await traveler.getByRole('button', { name: 'Add item' }).click();
  const review = traveler.locator('.capture-review');
  await expect(review).toBeVisible();
  await expect(review.getByRole('combobox', { name: 'Save as' })).toHaveValue('TRIP');
  await expect(review.getByRole('combobox', { name: 'Destination list' })).toContainText(title);
  await review.getByRole('textbox', { name: 'Title' }).fill('Museum Island');
  await review.getByRole('textbox', { name: 'Why visit / note' }).fill('The group wants an afternoon at the museums.');
  await review.getByRole('button', { name: 'Save reviewed draft' }).click();
  await expect(traveler.getByText('Museum Island', { exact: true })).toBeVisible();
  await expect(traveler.getByText(`Suggested by ${username}`)).toBeVisible();
  await expect(traveler.getByRole('link', { name: 'Open place' })).toHaveAttribute('href', /visitberlin\.de\/en\/museum-island/);
  const canonicalUrl = await traveler.getByRole('link', { name: 'Open place' }).getAttribute('href');
  await traveler.route('**/api/v1/utils/extract', async route => route.fulfill({ json: {
    kind: 'PAGE', sourceUrl: `${canonicalUrl}?utm_source=group`, extractedAt: new Date().toISOString(),
    title: 'Museum Island', description: null, imageUrl: null, price: null, priceCurrency: null,
    seller: null, availability: null, ingredients: [], steps: [], eventStart: null, eventLocation: null
  } }));
  await traveler.getByRole('button', { name: 'Save from a website' }).click();
  await traveler.locator('.web-capture').getByLabel('Page URL').fill(`${canonicalUrl}?utm_source=group`);
  await traveler.locator('.web-capture').getByRole('button', { name: 'Read page' }).click();
  await traveler.locator('.capture-review').getByLabel('Save as').selectOption('TRIP');
  await traveler.locator('.capture-review').getByLabel('Destination list').selectOption({ label: title });
  await expect(traveler.locator('.capture-warning')).toContainText('Museum Island');
  await traveler.locator('.capture-dialog-actions').getByRole('button', { name: 'Close' }).click();
  await traveler.reload();
  await expect(traveler.getByRole('link', { name: 'Open place' })).toHaveAttribute('href', /visitberlin\.de\/en\/museum-island/);

  await page.getByRole('button', { name: 'Items', exact: true }).click();
  await expect(page.getByText('Museum Island', { exact: true })).toBeVisible();
  await expect(page.getByText(`Suggested by ${username}`)).toBeVisible();
  const readerContext = await browser.newContext({ baseURL: new URL(page.url()).origin, viewport: page.viewportSize()! });
  const reader = await readerContext.newPage();
  await login(reader, readerName, password);
  await expect(reader.getByRole('heading', { name: title })).toBeVisible();
  await expect(reader.getByText('Museum Island', { exact: true })).toBeVisible();
  await expect(reader.locator('.composer-disclosure')).toHaveCount(0);
  await readerContext.close();
  await context.close();
  await diagnostics.expectClean();
});

test('trip review blocks private URLs and keeps a manual draft for unavailable pages @trip', async ({ page }) => {
  test.setTimeout(90_000);
  await bootstrapOrLoginAdmin(page);
  await createList(page, `Manual trip ${Date.now()}`, 'TRIP');
  await page.locator('.item-composer input[aria-label="URL"]').fill('http://127.0.0.1/place');
  await page.getByRole('button', { name: 'Add item' }).click();
  await expect(page.locator('.web-capture [role="alert"]')).toContainText('private or local network');
  await expect(page.locator('.capture-review')).toHaveCount(0);

  await page.route('**/api/v1/utils/extract', async route => {
    await route.fulfill({ status: 502, contentType: 'application/json', body: '{"code":"extract_failed","message":"Page unavailable"}' });
  });
  await page.locator('.capture-form input[type="url"]').fill('https://example.com/unavailable-place');
  await page.locator('.capture-form').getByRole('button', { name: 'Read page' }).click();
  const review = page.locator('.capture-review');
  await expect(review).toBeVisible();
  await expect(page.locator('.web-capture [role="status"]')).toContainText('enter and save details manually');
  await review.getByRole('textbox', { name: 'Title' }).fill('A place to investigate');
  await review.getByRole('textbox', { name: 'Why visit / note' }).fill('Ask the group if this is worth a stop.');
  await review.getByRole('button', { name: 'Save reviewed draft' }).click();
  await expect(page.getByText('A place to investigate', { exact: true })).toBeVisible();
});
