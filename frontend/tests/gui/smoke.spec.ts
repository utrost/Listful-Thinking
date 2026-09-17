import { expect, test } from '@playwright/test';

import { bootstrapOrLoginAdmin, collectPageDiagnostics, createList } from './helpers';

test.describe('Listful Thinking GUI smoke @gui @smoke', () => {
  test('bootstraps first admin and creates a grocery item @auth @lists', async ({ page }, testInfo) => {
    test.skip(testInfo.project.name.includes('mobile'), 'covered by the focused mobile GUI smoke');

    const diagnostics = collectPageDiagnostics(page);

    await bootstrapOrLoginAdmin(page);

    const listTitle = 'GUI groceries';
    await createList(page, listTitle, 'GROCERY');

    await page.getByPlaceholder('New item name').fill('Oat milk');
    await page.getByRole('textbox', { name: 'Quantity' }).fill('2 cartons');
    await page.getByRole('textbox', { name: 'Category' }).fill('Pantry');
    await page.getByRole('button', { name: 'Add item' }).click();

    await expect(page.getByText('Oat milk')).toBeVisible();
    await expect(page.getByText('2 cartons · Pantry')).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Pantry' })).toBeVisible();

    await diagnostics.expectClean();
  });
});
