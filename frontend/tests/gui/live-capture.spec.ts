import { expect, test } from '@playwright/test';
import { bootstrapOrLoginAdmin } from './helpers';

const productUrl = 'https://www.muji.eu/products/set-of-5-planted-tree-paper-notebooks-b5-4594';
const recipeUrl = 'https://www.bbcgoodfood.com/recipes/easy-pancakes';
const eventUrl = 'https://www.berliner-philharmoniker.de/en/concert/calendar/57042/';
const sometimesBlockedUrl = 'https://www.kingarthurbaking.com/recipes/simply-perfect-pancakes-recipe';

test.describe('Live website capture @live', () => {
  test.skip(process.env.LIVE_WEB_CAPTURE !== '1', 'Live external pages are tested only on request.');
  test.skip(({ isMobile }) => isMobile, 'Run live fetches once on desktop.');
  test.setTimeout(60_000);

  async function readPage(page: import('@playwright/test').Page, url: string) {
    const capture = page.locator('.web-capture');
    await capture.locator('summary').click();
    await capture.getByLabel('Page URL').fill(url);
    const responsePromise = page.waitForResponse(response => response.url().endsWith('/api/v1/utils/extract'));
    await capture.getByRole('button', { name: 'Read page' }).click();
    const response = await responsePromise;
    expect(response.status()).toBe(200);
    const draft = await response.json();
    console.log(JSON.stringify({ page: url, draft: {
      kind: draft.kind, title: draft.title, price: draft.price, currency: draft.priceCurrency,
      ingredients: draft.ingredients?.length, steps: draft.steps?.length,
      eventStart: draft.eventStart, eventLocation: draft.eventLocation
    } }));
    await expect(capture.locator('.capture-review')).toBeVisible();
    return { capture, draft };
  }

  test('captures and saves a live product', async ({ page }) => {
    await bootstrapOrLoginAdmin(page);
    const { capture, draft } = await readPage(page, productUrl);
    expect(draft.kind).toBe('PRODUCT');
    expect(draft.title).toMatch(/Notebooks|Notizbücher/);
    await expect(capture.getByLabel('Title', { exact: true })).toHaveValue(draft.title);
    await capture.getByLabel('Exact size, colour, model, or buyer note').fill('B5, set of 5');
    await capture.getByLabel('New list title').fill('Live capture wishlist');
    await capture.getByRole('button', { name: 'Save reviewed draft' }).click();
    await expect(page.getByRole('heading', { name: 'Live capture wishlist' })).toBeVisible();
    await expect(page.getByText('B5, set of 5')).toBeVisible();
    const lists = await (await page.request.get('/api/v1/lists')).json();
    const wish = lists.find((list: { title: string }) => list.title === 'Live capture wishlist');
    const items = await (await page.request.get(`/api/v1/lists/${wish.id}/items`)).json();
    expect(items[0]).toMatchObject({ variantNote: 'B5, set of 5', price: 5.95, priceCurrency: 'EUR', sourceUrl: draft.sourceUrl });
    await capture.getByLabel('Page URL').fill(productUrl);
    await capture.getByRole('button', { name: 'Read page' }).click();
    await capture.getByLabel('Destination list').selectOption(wish.id);
    await expect(capture.locator('.capture-warning')).toContainText(items[0].name);
  });

  test('captures and saves two live recipe ingredients', async ({ page }) => {
    await bootstrapOrLoginAdmin(page);
    const { capture, draft } = await readPage(page, recipeUrl);
    expect(draft.kind).toBe('RECIPE');
    expect(draft.ingredients.length).toBeGreaterThan(1);
    const selected = draft.ingredients.slice(0, 2);
    await capture.getByLabel('New list title').fill('Live capture groceries');
    await capture.getByLabel('Grocery items to add').fill(selected.join('\n'));
    await capture.getByRole('button', { name: 'Save reviewed draft' }).click();
    await expect(page.getByRole('heading', { name: 'Live capture groceries' })).toBeVisible();
    for (const ingredient of selected) await expect(page.getByText(ingredient, { exact: true })).toBeVisible();
    const lists = await (await page.request.get('/api/v1/lists')).json();
    const grocery = lists.find((list: { title: string }) => list.title === 'Live capture groceries');
    const items = await (await page.request.get(`/api/v1/lists/${grocery.id}/items`)).json();
    expect(items.map((item: { name: string }) => item.name)).toEqual(selected);
    expect(items.every((item: { sourceUrl: string }) => item.sourceUrl === recipeUrl)).toBe(true);
  });

  test('captures and saves a live concert with a preparation task', async ({ page }) => {
    await bootstrapOrLoginAdmin(page);
    const { capture, draft } = await readPage(page, eventUrl);
    expect(draft.kind).toBe('EVENT');
    expect(draft.eventStart).toContain('2026-10-21');
    expect(draft.eventLocation).toBeTruthy();
    await capture.getByLabel('Preparation tasks (optional)').fill('Plan journey');
    await capture.getByRole('button', { name: 'Save reviewed draft' }).click();
    await expect(page.getByRole('heading', { name: draft.title, exact: true })).toBeVisible();
    await expect(page.getByText('Plan journey', { exact: true })).toBeVisible();
    const lists = await (await page.request.get('/api/v1/lists')).json();
    const event = lists.find((list: { title: string }) => list.title === draft.title);
    expect(event).toMatchObject({ sourceUrl: eventUrl, eventLocation: 'Foyer Main Auditorium', targetDate: '2026-10-21T11:00:00Z' });
  });

  test('keeps a real source link when the user replaces the extracted details', async ({ page }) => {
    await bootstrapOrLoginAdmin(page);
    const capture = page.locator('.web-capture');
    await capture.locator('summary').click();
    await capture.getByLabel('Page URL').fill(sometimesBlockedUrl);
    const responsePromise = page.waitForResponse(response => response.url().endsWith('/api/v1/utils/extract'));
    await capture.getByRole('button', { name: 'Read page' }).click();
    const response = await responsePromise;
    const pageDraft = response.ok() ? await response.json() : null;
    console.log(JSON.stringify({ page: sometimesBlockedUrl, status: response.status(), kind: pageDraft?.kind, title: pageDraft?.title }));
    await expect(capture.locator('.capture-review')).toBeVisible();
    if (!response.ok()) await expect(capture.getByRole('status')).toContainText('enter and save details manually');
    await capture.getByLabel('Save as').selectOption('WISH');
    await capture.getByLabel('Title', { exact: true }).fill('Pancake recipe to revisit');
    await capture.getByLabel('New list title').fill('Live capture fallback');
    await capture.getByRole('button', { name: 'Save reviewed draft' }).click();
    await expect(page.getByRole('heading', { name: 'Live capture fallback' })).toBeVisible();
    const lists = await (await page.request.get('/api/v1/lists')).json();
    const wish = lists.find((list: { title: string }) => list.title === 'Live capture fallback');
    const items = await (await page.request.get(`/api/v1/lists/${wish.id}/items`)).json();
    expect(items[0].sourceUrl).toBe(pageDraft?.sourceUrl ?? sometimesBlockedUrl);
  });
});
