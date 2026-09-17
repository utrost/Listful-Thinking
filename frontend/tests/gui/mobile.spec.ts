import { expect, test } from '@playwright/test';

import { bootstrapOrLoginAdmin, collectPageDiagnostics, createList } from './helpers';

test.describe('Mobile owner workflow @gui @mobile', () => {
  test('keeps core list and item controls usable on a phone viewport @smoke', async ({ page }, testInfo) => {
    test.skip(!testInfo.project.name.includes('mobile'), 'mobile viewport coverage runs only in the mobile project');

    const diagnostics = collectPageDiagnostics(page);

    await bootstrapOrLoginAdmin(page);

    await expect(page.getByRole('button', { name: 'Log out' })).toBeVisible();
    await expect(page.getByPlaceholder('New list title')).toBeVisible();
    await expect(page.getByRole('button', { name: 'Create list' })).toBeVisible();

    const listTitle = `Mobile groceries ${Date.now()}`;
    await createList(page, listTitle, 'GROCERY');

    await expect(page.getByRole('heading', { name: listTitle })).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Sharing' })).toBeVisible();
    await expect(page.getByLabel('Item review controls')).toBeVisible();

    await page.getByPlaceholder('New item name').fill('Bananas');
    await page.getByRole('textbox', { name: 'Quantity' }).fill('6');
    await page.getByRole('textbox', { name: 'Category' }).fill('Fruit');
    await page.getByRole('button', { name: 'Add item' }).click();

    await expect(page.getByText('Bananas')).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Fruit' })).toBeVisible();
    await expect(page.getByText('6 · Fruit')).toBeVisible();

    await diagnostics.expectClean();
  });
});
