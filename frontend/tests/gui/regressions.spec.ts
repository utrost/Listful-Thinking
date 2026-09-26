import { expect, test } from '@playwright/test';
import { bootstrapOrLoginAdmin, createList } from './helpers';

test.describe('Review regressions @smoke', () => {
  test('writes after logout/login without reloading the app', async ({ page }, info) => {
    test.skip(info.project.name.includes('mobile'));
    await bootstrapOrLoginAdmin(page);
    await createList(page, `Before logout ${Date.now()}`, 'TODO');
    await page.getByRole('button', { name: 'Log out', exact: true }).click();
    const form = page.locator('form.panel').filter({ has: page.getByRole('heading', { name: 'Log in', exact: true }) });
    await form.getByLabel('Username', { exact: true }).fill('admin');
    await form.getByLabel('Password', { exact: true }).fill('listful-admin-password');
    await form.getByRole('button', { name: 'Log in', exact: true }).click();
    await expect(page.getByRole('heading', { name: 'Your lists' })).toBeVisible();
    await createList(page, `After logout ${Date.now()}`, 'TODO');
    await expect(page.getByRole('alert')).toHaveCount(0);
  });

  test('reader and contributor can refresh their shared lists', async ({ page, browser }, info) => {
    test.skip(info.project.name.includes('mobile'));
    await bootstrapOrLoginAdmin(page);
    const nonce = Date.now();
    const list = await (await page.request.post('/api/v1/lists', { data: { title: `Shared ${nonce}`, type: 'TODO' } })).json();
    for (const permission of ['READ', 'CONTRIBUTE']) {
      const username = `member-${permission.toLowerCase()}-${nonce}`;
      expect((await page.request.post('/api/v1/admin/users', { data: { username, password: 'member-password', role: 'USER' } })).status()).toBe(201);
      expect((await page.request.post(`/api/v1/lists/${list.id}/shares`, { data: { username, permission } })).status()).toBe(201);
      const context = await browser.newContext({ baseURL: new URL(page.url()).origin });
      const member = await context.newPage();
      await member.goto('/');
      const form = member.locator('form.panel').filter({ has: member.getByRole('heading', { name: 'Log in', exact: true }) });
      await form.getByLabel('Username', { exact: true }).fill(username);
      await form.getByLabel('Password', { exact: true }).fill('member-password');
      await form.getByRole('button', { name: 'Log in', exact: true }).click();
      await expect(member.getByRole('heading', { name: list.title, exact: true })).toBeVisible();
      await member.reload();
      await expect(member.getByRole('heading', { name: list.title, exact: true })).toBeVisible();
      await expect(member.getByRole('heading', { name: 'Log in', exact: true })).toHaveCount(0);
      await expect(member.getByRole('alert')).toHaveCount(0);
      if (permission === 'CONTRIBUTE') {
        await member.getByPlaceholder('New item name').fill('Shared task');
        await member.getByRole('button', { name: 'Add item', exact: true }).click();
        await expect(member.getByText('Shared task', { exact: true })).toBeVisible();
      }
      await context.close();
    }
  });

  test('phone layout fits with expanded administration and long content', async ({ page }, info) => {
    test.skip(!info.project.name.includes('mobile'));
    await bootstrapOrLoginAdmin(page);
    await page.getByRole('button', { name: 'Administration', exact: true }).click();
    await expect(page.locator('.admin-user-list').first()).toContainText('admin');
    await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
    await page.getByRole('button', { name: 'Back to lists', exact: true }).click();
    await createList(page, 'A very long household shopping list title that needs to wrap', 'GROCERY');
    await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  });
});
