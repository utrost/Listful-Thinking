import { expect, test } from '@playwright/test';
import { bootstrapOrLoginAdmin } from './helpers';

const source = 'https://shop.example.test/notebook?colour=blue#details';

test('opens a bookmarked page in review after login @smoke', async ({ page, context }) => {
  await bootstrapOrLoginAdmin(page);
  await page.getByRole('button', { name: 'Save from a website' }).click();
  await page.locator('.capture-shortcut > summary').click();
  const bookmarklet = await page.getByRole('link', { name: 'Save to Listful Thinking' }).getAttribute('href');
  expect(bookmarklet).toMatch(/^javascript:/);
  await page.getByRole('button', { name: 'Close website review' }).click();
  await page.getByRole('button', { name: 'Log out' }).click();

  await context.route('**/api/v1/utils/extract', async route => route.fulfill({ json: {
    kind: 'PRODUCT', sourceUrl: source, extractedAt: '2026-10-17T08:00:00Z',
    title: 'Blue notebook', description: 'Dotted pages', imageUrl: null, price: 18,
    priceCurrency: 'EUR', seller: 'Example Shop', availability: 'InStock',
    ingredients: [], steps: [], eventStart: null, eventLocation: null
  } }));
  const sourcePage = await context.newPage();
  await sourcePage.route('https://shop.example.test/**', async route => route.fulfill({
    contentType: 'text/html', body: '<!doctype html><title>Notebook</title><a id="save">Save this page</a>'
  }));
  await sourcePage.goto(source);
  await sourcePage.locator('#save').evaluate((link, href) => link.setAttribute('href', href), bookmarklet);
  const popup = context.waitForEvent('page');
  await sourcePage.getByRole('link', { name: 'Save this page' }).click();
  const shortcutPage = await popup;
  await shortcutPage.waitForLoadState('domcontentloaded');
  await expect(shortcutPage).toHaveURL('/');
  const login = shortcutPage.locator('form.panel').filter({ has: shortcutPage.getByRole('heading', { name: 'Log in' }) });
  await login.getByLabel('Username').fill('admin');
  await login.getByLabel('Password').fill('listful-admin-password');
  await login.getByRole('button', { name: 'Log in' }).click();
  const capture = shortcutPage.locator('.capture-dialog');
  await expect(capture).toBeVisible();
  await expect(capture.getByLabel('Title', { exact: true })).toHaveValue('Blue notebook');
  await expect(capture.getByRole('link', { name: 'Open source page' })).toHaveAttribute('href', source);
});

test('ignores non-web shortcut URLs @smoke', async ({ page, context }) => {
  await bootstrapOrLoginAdmin(page);
  const shortcutPage = await context.newPage();
  await shortcutPage.goto('/#save=javascript%3Aalert(1)');
  await expect(shortcutPage).toHaveURL('/');
  await expect(shortcutPage.locator('.capture-dialog')).toBeHidden();
});
