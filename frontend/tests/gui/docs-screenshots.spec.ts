import { expect, test } from '@playwright/test';
import { resolve } from 'node:path';
import { bootstrapOrLoginAdmin, createList } from './helpers';

const screenshotPath = (name: string) => resolve('..', 'docs', 'images', name);

test.describe('Documentation screenshots', () => {
  test('captures the desktop wishlist and website review', async ({ page }, info) => {
    test.skip(!info.project.name.includes('desktop'), 'desktop screenshot');

    await page.setViewportSize({ width: 1440, height: 900 });
    await bootstrapOrLoginAdmin(page);
    await createList(page, 'Weekend ideas', 'WISH');

    await page.route('**/api/v1/utils/extract', async route => route.fulfill({ json: {
      kind: 'PRODUCT', sourceUrl: 'https://shop.example.test/notebook', extractedAt: '2026-10-17T08:00:00Z',
      title: 'Dotted notebook', description: 'A notebook for sketches and plans', imageUrl: null,
      price: 18, priceCurrency: 'EUR', seller: 'Example Shop', availability: 'InStock',
      ingredients: [], steps: [], eventStart: null, eventLocation: null
    } }));

    await page.locator('.item-composer').getByLabel('URL', { exact: true }).fill('https://shop.example.test/notebook');
    await page.locator('.item-composer').getByRole('button', { name: 'Add item' }).click();
    const capture = page.locator('.web-capture');
    await expect(capture.getByLabel('Title', { exact: true })).toHaveValue('Dotted notebook');
    await capture.getByLabel('Exact size, colour, model, or buyer note').fill('A5, blue cover');
    await expect(capture.getByLabel('Destination list')).toHaveValue((await (await page.request.get('/api/v1/lists')).json()).find((list: { title: string }) => list.title === 'Weekend ideas').id);
    await page.screenshot({ path: screenshotPath('website-review.png') });
    await capture.getByRole('button', { name: 'Save reviewed draft' }).click();
    await expect(page.getByText('A5, blue cover')).toBeVisible();

    await page.locator('.composer-disclosure > summary').click();
    await page.getByPlaceholder('New item name').fill('Ceramic tea mug');
    await page.locator('.item-composer').getByRole('button', { name: 'Add item' }).click();
    await expect(page.getByText('Ceramic tea mug')).toBeVisible();
    await page.locator('.composer-disclosure > summary').click();
    await page.getByPlaceholder('New item name').fill('Board game');
    await page.locator('.item-composer').getByRole('button', { name: 'Add item' }).click();
    await expect(page.getByText('Board game')).toBeVisible();
    await page.reload();
    await expect(page.getByText('Board game')).toBeVisible();
    await expect(page.getByRole('link', { name: 'Source code' })).toBeVisible();
    await page.screenshot({ path: screenshotPath('wishlist-desktop.png'), fullPage: true });
  });

  test('captures the mobile grocery list', async ({ page }, info) => {
    test.skip(!info.project.name.includes('mobile'), 'mobile screenshot');

    await bootstrapOrLoginAdmin(page);
    await createList(page, 'Saturday groceries', 'GROCERY');
    for (const [name, quantity] of [['Bananas', '6'], ['Coffee', '1 bag'], ['Bread', '1 loaf']]) {
      await page.getByPlaceholder('New item name').fill(name);
      await page.getByRole('textbox', { name: 'Quantity' }).fill(quantity);
      await page.locator('.item-composer').getByRole('button', { name: 'Add item' }).click();
      await expect(page.getByText(name, { exact: true })).toBeVisible();
      if (name !== 'Bread') await page.locator('.composer-disclosure > summary').click();
    }
    await page.reload();
    await expect(page.getByText('Bread', { exact: true })).toBeVisible();
    await page.screenshot({ path: screenshotPath('grocery-mobile.png'), fullPage: true });
  });
});
