import { expect, test } from '@playwright/test';
import { bootstrapOrLoginAdmin, collectPageDiagnostics, createList } from './helpers';

test.describe('Focused list experience @ux', () => {
  test('keeps items central and guides sharing, settings and search', async ({ page }, info) => {
    const diagnostics = collectPageDiagnostics(page);
    await bootstrapOrLoginAdmin(page);
    await createList(page, `Keep this list ${info.project.name}`, 'TODO');
    const title = `Everyday wishes ${info.project.name}`;
    await createList(page, title, 'WISH');
    await expect(page.getByRole('heading', { name: 'A fresh start.' })).toBeVisible();
    await expect(page.locator('.share-panel')).toBeHidden();
    await expect(page.locator('.list-settings')).toBeHidden();
    await expect(page.getByLabel('Price', { exact: true })).toBeHidden();
    await page.getByLabel('New item name', { exact: true }).fill('A beautiful notebook');
    await page.locator('.item-details > summary').click();
    await page.getByLabel('Description', { exact: true }).filter({ visible: true }).fill('For thoughts, sketches and plans');
    await page.getByLabel('Price', { exact: true }).filter({ visible: true }).fill('24');
    await page.getByLabel('Currency (EUR, USD…)').filter({ visible: true }).fill('EUR');
    await page.getByRole('button', { name: 'Add item', exact: true }).click();
    await expect(page.getByText('A beautiful notebook', { exact: true })).toBeVisible();
    await expect(page.getByLabel('New item name', { exact: true })).toBeHidden();
    await page.locator('.composer-disclosure > summary').click();
    await expect(page.getByLabel('New item name', { exact: true })).toBeVisible();
    await expect(page.getByLabel('Price', { exact: true })).toBeHidden();
    await page.locator('.composer-disclosure > summary').click();
    await expect(page.getByText('1 open · 1 total', { exact: true })).toBeVisible();
    await expect(page.getByRole('button', { name: 'Delete: A beautiful notebook', exact: true })).toBeHidden();
    await page.getByLabel('Search items').fill('nothing matches this');
    await expect(page.getByRole('heading', { name: 'No matching items.' })).toBeVisible();
    await page.getByRole('button', { name: 'Reset search & filters' }).click();
    await expect(page.getByText('A beautiful notebook', { exact: true })).toBeVisible();
    await page.screenshot({ path: `/tmp/listful-ux-${info.project.name}-items.png`, fullPage: true });

    await page.getByRole('button', { name: 'Sharing', exact: true }).click();
    await expect(page.locator('.item-composer')).toBeHidden();
    await expect(page.getByRole('heading', { name: 'Share a link' })).toBeVisible();
    await expect(page.getByText('Read-only lets people view items. Contributors can also add and update them.')).toBeVisible();
    await page.screenshot({ path: `/tmp/listful-ux-${info.project.name}-sharing.png`, fullPage: true });
    await page.getByRole('button', { name: 'List settings', exact: true }).click();
    await page.getByRole('button', { name: 'Edit list', exact: true }).click();
    await expect(page.locator('.edit-list-form').getByLabel('List type')).toBeDisabled();
    await page.locator('.edit-list-form').getByLabel('New list title').fill(`${title} updated`);
    await page.getByRole('button', { name: 'Save list', exact: true }).click();
    await expect(page.getByRole('heading', { name: `${title} updated`, exact: true })).toBeVisible();
    await page.getByRole('button', { name: 'Delete list', exact: true }).click();
    await expect(page.getByRole('button', { name: 'Confirm delete' })).toBeVisible();
    await page.getByRole('button', { name: 'Keep list' }).click();
    await page.getByRole('button', { name: 'Items', exact: true }).click();
    await page.getByLabel('More: A beautiful notebook', { exact: true }).click();
    await page.getByRole('button', { name: 'Edit: A beautiful notebook', exact: true }).click();
    await page.locator('.edit-item-form').getByLabel('New item name').fill('A pocket notebook');
    await page.locator('.edit-item-form').getByRole('button', { name: 'Save', exact: true }).click();
    await expect(page.getByText('A pocket notebook', { exact: true })).toBeVisible();
    await page.getByRole('button', { name: 'List settings', exact: true }).click();
    await page.getByRole('button', { name: 'Delete list', exact: true }).click();
    await page.getByRole('button', { name: 'Confirm delete', exact: true }).click();
    await expect(page.getByRole('heading', { name: `Keep this list ${info.project.name}`, exact: true })).toBeVisible();
    await expect(page.locator('.items-workspace')).toBeVisible();
    await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
    await diagnostics.expectClean();
  });

  test('supports chores and events with purposeful optional fields', async ({ page }, info) => {
    await bootstrapOrLoginAdmin(page);
    await createList(page, `Home chores ${info.project.name}`, 'CHORE');
    await page.getByLabel('New item name', { exact: true }).fill('Water the plants');
    await page.locator('.item-details > summary').click();
    await page.locator('.item-composer').getByLabel('Due date').fill('2027-05-08T09:00');
    await page.locator('.item-composer').getByLabel('Repeat').selectOption('FREQ=WEEKLY');
    await page.getByRole('button', { name: 'Add item', exact: true }).click();
    await page.getByRole('button', { name: 'Done: Water the plants', exact: true }).click();
    await expect(page.getByText('Water the plants', { exact: true })).toBeVisible();
    await createList(page, `Garden party ${info.project.name}`, 'EVENT');
    await page.getByLabel('New item name', { exact: true }).fill('Bring refreshments');
    await page.locator('.item-details > summary').click();
    await page.locator('.item-composer').getByLabel('Owner / responsible').fill('Alex');
    await page.getByRole('button', { name: 'Add item', exact: true }).click();
    await expect(page.getByText('Owner / responsible: Alex')).toBeVisible();
    await page.screenshot({ path: `/tmp/listful-ux-${info.project.name}-event.png`, fullPage: true });
    await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  });

  test('switches lists and language without mixing administration into list work', async ({ page }, info) => {
    await bootstrapOrLoginAdmin(page);
    await createList(page, `Navigation event ${info.project.name}`, 'EVENT');
    await page.getByLabel('New item name', { exact: true }).fill('Prepare invitations');
    await page.getByRole('button', { name: 'Add item', exact: true }).click();
    await createList(page, `Simple tasks ${info.project.name}`, 'TODO');
    await page.getByLabel('New item name', { exact: true }).fill('Call a friend');
    await page.getByRole('button', { name: 'Add item', exact: true }).click();
    await page.getByRole('button', { name: 'Done: Call a friend', exact: true }).click();
    await expect(page.getByText('0 open · 1 total', { exact: true })).toBeVisible();
    await page.getByRole('button', { name: 'Administration', exact: true }).click();
    await expect(page.locator('.content-grid')).toBeHidden();
    await expect(page.getByRole('button', { name: 'Create user' })).toBeVisible();
    await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
    await page.getByRole('button', { name: 'Back to lists', exact: true }).click();
    await expect(page.getByText('Call a friend', { exact: true })).toBeVisible();
    if (info.project.name.includes('mobile')) {
      await page.getByLabel('Choose a list', { exact: true }).selectOption({ label: `Navigation event ${info.project.name} · Event` });
      await expect(page.getByText('Prepare invitations', { exact: true })).toBeVisible();
    } else {
      await page.getByLabel('Find a list').fill('Navigation event');
      await page.locator('.list-cards button').filter({ hasText: `Navigation event ${info.project.name}` }).click();
      await expect(page.getByText('Prepare invitations', { exact: true })).toBeVisible();
    }
    await page.getByLabel('Language').selectOption('de');
    await expect(page.getByRole('heading', { name: 'Deine Listen' })).toBeVisible();
    await expect(page.getByRole('button', { name: 'Freigaben', exact: true })).toBeVisible();
    await page.reload();
    await expect(page.getByRole('heading', { name: 'Deine Listen' })).toBeVisible();
    await expect(page.getByRole('heading', { name: `Navigation event ${info.project.name}`, exact: true })).toBeVisible();
    await page.screenshot({ path: `/tmp/listful-ux-${info.project.name}-german.png`, fullPage: true });
  });
});

test('welcomes a new user and gives recovery links a focused screen @ux', async ({ page, browser }, info) => {
  await bootstrapOrLoginAdmin(page);
  const username = `new-user-${info.project.name}-${Date.now()}`;
  const response = await page.request.post('/api/v1/admin/users', { data: { username, password: 'welcome-password', role: 'USER' } });
  expect(response.status()).toBe(201);
  const context = await browser.newContext({ baseURL: new URL(page.url()).origin, viewport: page.viewportSize()! });
  const newcomer = await context.newPage();
  await newcomer.goto('/');
  const form = newcomer.locator('form.panel').filter({ has: newcomer.getByRole('heading', { name: 'Log in', exact: true }) });
  await form.getByLabel('Username', { exact: true }).fill(username);
  await form.getByLabel('Password', { exact: true }).fill('welcome-password');
  await form.getByRole('button', { name: 'Log in', exact: true }).click();
  await expect(newcomer.getByRole('heading', { name: 'Start with one list.' })).toBeVisible();
  await expect(newcomer.getByRole('button', { name: 'Administration', exact: true })).toHaveCount(0);
  await newcomer.screenshot({ path: `/tmp/listful-ux-${info.project.name}-welcome.png`, fullPage: true });
  await newcomer.getByRole('button', { name: '+ New list', exact: true }).click();
  await expect(newcomer.getByLabel('New list title')).toBeFocused();
  await newcomer.getByRole('button', { name: 'Log out', exact: true }).click();
  await expect(newcomer.getByRole('button', { name: 'Reset password', exact: true })).toBeHidden();
  await newcomer.locator('.recovery-panel > summary').click();
  await expect(newcomer.getByRole('button', { name: 'Reset password', exact: true })).toBeDisabled();
  await expect(newcomer.getByText('Email recovery is unavailable. Ask the administrator to configure outgoing email.')).toBeVisible();
  await newcomer.screenshot({ path: `/tmp/listful-ux-${info.project.name}-login.png`, fullPage: true });
  let payload: unknown;
  // This intercept tests reset-page presentation; real token/SMTP behavior is covered by auth integration tests.
  await newcomer.route('**/api/v1/auth/password-reset/consume', async route => {
    payload = route.request().postDataJSON();
    await route.fulfill({ status: 204 });
  });
  await newcomer.goto('/reset-password?token=ui-test-token');
  await expect(newcomer.getByRole('heading', { name: 'Set new password', exact: true })).toBeVisible();
  await expect(newcomer.getByRole('heading', { name: 'Log in', exact: true })).toHaveCount(0);
  await newcomer.getByLabel('New password').fill('a-new-password');
  await newcomer.getByRole('button', { name: 'Set new password', exact: true }).click();
  await expect(newcomer.getByRole('status').filter({ hasText: 'Password updated.' })).toBeVisible();
  await expect(newcomer.getByRole('heading', { name: 'Log in', exact: true })).toBeVisible();
  expect(payload).toEqual({ token: 'ui-test-token', password: 'a-new-password' });
  expect(newcomer.url()).not.toContain('token=');
  await context.close();
});
