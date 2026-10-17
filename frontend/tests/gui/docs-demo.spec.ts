import { expect, test } from '@playwright/test';
import { bootstrapOrLoginAdmin, createList } from './helpers';

test.use({ viewport: { width: 1100, height: 900 } });

test('records the website capture story for README', async ({ page }, info) => {
  test.skip(!info.project.name.includes('desktop'), 'Desktop README animation');
  await bootstrapOrLoginAdmin(page);
  await createList(page, 'Weekend ideas', 'WISH');
  await page.screenshot({ path: '/tmp/listful-demo-01.png' });

  await page.route('**/api/v1/utils/extract', async route => route.fulfill({ json: {
    kind: 'PRODUCT', sourceUrl: 'https://shop.example.test/notebook', extractedAt: '2026-10-17T08:00:00Z',
    title: 'Dotted notebook', description: 'A notebook for sketches and plans', imageUrl: null,
    price: 18, priceCurrency: 'EUR', seller: 'Example Shop', availability: 'InStock',
    ingredients: [], steps: [], eventStart: null, eventLocation: null
  } }));
  await page.getByRole('button', { name: 'Save from a website' }).click();
  const capture = page.locator('.capture-dialog');
  await capture.getByLabel('Page URL').fill('https://shop.example.test/notebook');
  await page.screenshot({ path: '/tmp/listful-demo-02.png' });

  await capture.getByRole('button', { name: 'Read page' }).click();
  await expect(capture.getByLabel('Title', { exact: true })).toHaveValue('Dotted notebook');
  await capture.getByLabel('Exact size, colour, model, or buyer note').fill('A5, blue cover');
  const lists = await (await page.request.get('/api/v1/lists')).json() as { id: string; title: string }[];
  await capture.getByLabel('Destination list').selectOption(lists.find(list => list.title === 'Weekend ideas')!.id);
  await page.screenshot({ path: '/tmp/listful-demo-03.png' });

  await capture.getByRole('button', { name: 'Save reviewed draft' }).click();
  await expect(capture).toBeHidden();
  await expect(page.getByText('A5, blue cover')).toBeVisible();
  await page.screenshot({ path: '/tmp/listful-demo-04.png' });
});
