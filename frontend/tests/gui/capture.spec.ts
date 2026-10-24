import { expect, test } from '@playwright/test';
import { bootstrapOrLoginAdmin, createList } from './helpers';

test.describe('Reviewed website capture @capture @smoke', () => {
  test('uses the same review from a wishlist product link and keeps its list selected', async ({ page }, info) => {
    await bootstrapOrLoginAdmin(page);
    const listTitle = `Inline capture ${info.project.name}`;
    await createList(page, listTitle, 'WISH');
    await page.route('**/api/v1/utils/extract', async route => route.fulfill({ json: {
      kind: 'PRODUCT', sourceUrl: 'https://shop.example.test/notebook', extractedAt: '2026-10-17T08:00:00Z',
      title: 'Notebook', description: 'Dotted pages', imageUrl: null, price: 18, priceCurrency: 'EUR',
      seller: 'Example Shop', availability: 'InStock', ingredients: [], steps: [], eventStart: null, eventLocation: null
    } }));
    await page.locator('.item-composer').getByLabel('URL', { exact: true }).fill('https://shop.example.test/notebook');
    await page.locator('.item-composer').getByRole('button', { name: 'Add item' }).click();
    const capture = page.locator('.capture-dialog');
    await expect(capture).toBeVisible();
    if (info.project.name.includes('mobile')) await page.screenshot({ path: '/tmp/listful-v18-mobile-capture.png' });
    await expect(capture.getByLabel('Destination list')).toHaveValue((await (await page.request.get('/api/v1/lists')).json()).find((list: { title: string }) => list.title === listTitle).id);
    await expect(capture.getByLabel('Title', { exact: true })).toHaveValue('Notebook');
    await capture.getByLabel('Exact size, colour, model, or buyer note').fill('A5, blue');
    await capture.getByRole('button', { name: 'Save reviewed draft' }).click();
    await expect(capture).toBeHidden();
    await expect(page.getByText('A5, blue')).toBeVisible();
    await expect(page.locator('.wish-price')).toContainText('€18.00');
    if (info.project.name.includes('desktop')) await page.screenshot({ path: '/tmp/listful-v18-desktop-wishlist.png', fullPage: true });
    const list = (await (await page.request.get('/api/v1/lists')).json()).find((entry: { title: string }) => entry.title === listTitle);
    const items = await (await page.request.get(`/api/v1/lists/${list.id}/items`)).json();
    expect(items).toHaveLength(1);
    expect(items[0]).toMatchObject({ name: 'Notebook', variantNote: 'A5, blue', sourceUrl: 'https://shop.example.test/notebook' });
  });

  test('saves a product with reviewed variant and source', async ({ page }, info) => {
    await bootstrapOrLoginAdmin(page);
    await page.route('**/api/v1/utils/extract', async route => route.fulfill({ json: {
      kind: 'PRODUCT', sourceUrl: 'https://shop.example.test/blue-mug', extractedAt: '2026-10-17T08:00:00Z',
      title: 'Blue mug', description: 'Ceramic', imageUrl: null, price: 12.5, priceCurrency: 'EUR',
      seller: 'Example Shop', availability: 'InStock', ingredients: [], steps: [], eventStart: null, eventLocation: null
    } }));
    const capture = page.locator('.web-capture');
    await page.getByRole('button', { name: 'Save from a website' }).click();
    await capture.getByLabel('Page URL').fill('https://shop.example.test/blue-mug');
    await capture.getByRole('button', { name: 'Read page' }).click();
    await expect(capture.getByLabel('Title', { exact: true })).toHaveValue('Blue mug');
    await capture.getByLabel('Exact size, colour, model, or buyer note').fill('350 ml, blue');
    await capture.getByLabel('New list title').fill(`Captured wishes ${info.project.name}`);
    await capture.getByRole('button', { name: 'Save reviewed draft' }).click();
    await expect(page.getByRole('heading', { name: `Captured wishes ${info.project.name}` })).toBeVisible();
    await expect(page.getByText('350 ml, blue')).toBeVisible();
    const items = await (await page.request.get(`/api/v1/lists/${(await (await page.request.get('/api/v1/lists')).json()).find((list: { title: string }) => list.title === `Captured wishes ${info.project.name}`).id}/items`)).json();
    expect(items[0]).toMatchObject({ name: 'Blue mug', variantNote: '350 ml, blue', seller: 'Example Shop', sourceUrl: 'https://shop.example.test/blue-mug' });
  });

  test('creates an event with its source, location, and selected preparation tasks', async ({ page }, info) => {
    await bootstrapOrLoginAdmin(page);
    await page.route('**/api/v1/utils/extract', async route => route.fulfill({ json: {
      kind: 'EVENT', sourceUrl: 'https://events.example.test/dinner', extractedAt: '2026-10-17T08:00:00Z',
      title: `Dinner ${info.project.name}`, description: 'Community dinner', imageUrl: null, price: null,
      priceCurrency: null, seller: null, availability: null, ingredients: [], steps: [],
      eventStart: '2027-05-12T18:00:00+02:00', eventLocation: 'Town hall'
    } }));
    const capture = page.locator('.web-capture');
    await page.getByRole('button', { name: 'Save from a website' }).click();
    await capture.getByLabel('Page URL').fill('https://events.example.test/dinner');
    await capture.getByRole('button', { name: 'Read page' }).click();
    await expect(capture.getByRole('textbox', { name: 'Location', exact: true })).toHaveValue('Town hall');
    await capture.locator('.capture-more > summary').click();
    await capture.getByLabel('Preparation tasks (optional)').fill('Invite guests\nBook tables');
    await capture.getByRole('button', { name: 'Save reviewed draft' }).click();
    await expect(page.getByRole('heading', { name: `Dinner ${info.project.name}` })).toBeVisible();
    await expect(page.getByText('Location: Town hall')).toBeVisible();
    await expect(page.getByText('Invite guests')).toBeVisible();
    const lists = await (await page.request.get('/api/v1/lists')).json();
    const event = lists.find((list: { title: string }) => list.title === `Dinner ${info.project.name}`);
    expect(event).toMatchObject({ sourceUrl: 'https://events.example.test/dinner', eventLocation: 'Town hall' });
    const items = await (await page.request.get(`/api/v1/lists/${event.id}/items`)).json();
    expect(items.map((item: { name: string }) => item.name)).toEqual(['Invite guests', 'Book tables']);
  });
});
