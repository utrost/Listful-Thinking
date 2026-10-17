import { expect, test } from '@playwright/test';
import { bootstrapOrLoginAdmin } from './helpers';

test.describe('Reviewed website capture @capture', () => {
  test('saves a product with reviewed variant and source', async ({ page }, info) => {
    await bootstrapOrLoginAdmin(page);
    await page.route('**/api/v1/utils/extract', async route => route.fulfill({ json: {
      kind: 'PRODUCT', sourceUrl: 'https://shop.example.test/blue-mug', extractedAt: '2026-10-17T08:00:00Z',
      title: 'Blue mug', description: 'Ceramic', imageUrl: null, price: 12.5, priceCurrency: 'EUR',
      seller: 'Example Shop', availability: 'InStock', ingredients: [], steps: [], eventStart: null, eventLocation: null
    } }));
    const capture = page.locator('.web-capture');
    await capture.locator('summary').click();
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
    await capture.locator('summary').click();
    await capture.getByLabel('Page URL').fill('https://events.example.test/dinner');
    await capture.getByRole('button', { name: 'Read page' }).click();
    await expect(capture.getByLabel('Location')).toHaveValue('Town hall');
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
