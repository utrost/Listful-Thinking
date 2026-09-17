import { expect, test } from '@playwright/test';

import { bootstrapOrLoginAdmin, collectPageDiagnostics, createList } from './helpers';

test.describe('Public wishlist sharing @gui @sharing', () => {
  test('lets a guest claim an open wish from a public link @smoke @regression', async ({ page, context }, testInfo) => {
    test.skip(testInfo.project.name.includes('mobile'), 'public sharing is covered once on desktop; mobile has a separate owner workflow smoke');

    const diagnostics = collectPageDiagnostics(page);

    await bootstrapOrLoginAdmin(page);

    const listTitle = `GUI wishlist ${Date.now()}`;
    await createList(page, listTitle, 'WISH');

    await page.getByPlaceholder('New item name').fill('Travel watercolor set');
    await page.getByRole('button', { name: 'Add item' }).click();
    await expect(page.getByText('Travel watercolor set')).toBeVisible();

    const sharePanel = page.locator('.share-panel');
    await expect(sharePanel.locator('select').first()).toHaveValue('WISH_CLAIM');
    await sharePanel.getByRole('button', { name: 'Create public link' }).click();

    const publicLink = page.locator('.copyable-link');
    await expect(publicLink).toContainText('/s/');
    const shareUrl = (await publicLink.textContent())?.trim();
    expect(shareUrl).toBeTruthy();

    const guestPage = await context.newPage();
    const guestDiagnostics = collectPageDiagnostics(guestPage);
    await guestPage.goto(shareUrl!);

    await expect(guestPage.getByRole('heading', { name: listTitle })).toBeVisible();
    await expect(guestPage.getByText('Wishlist claiming')).toBeVisible();
    await expect(guestPage.getByText('Travel watercolor set')).toBeVisible();

    const claimForm = guestPage.locator('form.claim-form').filter({ has: guestPage.getByText('Claim') });
    await claimForm.getByPlaceholder('Your name').fill('Guest tester');
    await claimForm.getByRole('button', { name: 'Claim' }).click();

    await expect(guestPage.getByText('CLAIMED')).toBeVisible();
    await expect(guestPage.getByRole('button', { name: 'Claim' })).toHaveCount(0);

    await diagnostics.expectClean();
    await guestDiagnostics.expectClean();
  });
});
