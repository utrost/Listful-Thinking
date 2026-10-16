import { expect, test, type Page } from '@playwright/test';
import { bootstrapOrLoginAdmin, createList } from './helpers';

async function login(page: Page, username: string, password: string) {
  await page.goto('/');
  const form = page.locator('form.panel').filter({ has: page.getByRole('heading', { name: 'Log in', exact: true }) });
  await form.getByLabel('Username').fill(username);
  await form.getByLabel('Password').fill(password);
  await form.getByRole('button', { name: 'Log in' }).click();
  await expect(page.getByRole('heading', { name: 'Your lists' })).toBeVisible();
}

test.describe('Account and list handoffs @gui @smoke', () => {
  test('admin setup, user chosen password, and recovery without email @admin', async ({ page, browser }) => {
    test.setTimeout(90_000);
    await bootstrapOrLoginAdmin(page);
    await page.getByRole('button', { name: 'Administration', exact: true }).click();
    const panel = page.locator('.admin-panel');
    const form = panel.locator('form.inline-form').first();
    const username = `setup-${Date.now()}`;
    await form.getByLabel('Username').fill(username);
    await form.getByLabel('Role').selectOption('USER');
    await form.getByRole('button', { name: 'Create user' }).click();
    const link = await panel.getByRole('textbox', { name: 'One-time setup link' }).inputValue();
    const token = new URL(link).searchParams.get('token');
    expect(token).toBeTruthy();

    const memberContext = await browser.newContext({ baseURL: new URL(page.url()).origin, viewport: page.viewportSize()! });
    const member = await memberContext.newPage();
    await member.goto(`/reset-password?token=${token}`);
    await member.getByLabel('New password').fill('member-chosen-password');
    await member.getByRole('button', { name: 'Set new password' }).click();
    await expect(member.getByRole('status')).toContainText('Password updated');
    await login(member, username, 'member-chosen-password');

    await member.locator('.account-password > summary').click();
    const passwordForm = member.locator('.account-password form');
    await passwordForm.getByLabel('Current password').fill('member-chosen-password');
    await passwordForm.getByLabel('New password', { exact: true }).fill('member-next-password');
    await passwordForm.getByLabel('Confirm new password').fill('member-next-password');
    await passwordForm.getByRole('button', { name: 'Change password' }).click();
    await expect(member.getByRole('status')).toContainText('Password changed');
    await login(member, username, 'member-next-password');

    const row = panel.locator('ul.admin-user-list').first().locator('li').filter({ hasText: username });
    await row.getByRole('button', { name: `Issue setup or recovery link: ${username}` }).click();
    await expect(panel.getByRole('textbox', { name: 'One-time setup link' })).not.toHaveValue(link);
    const recoveryUrl = await panel.getByRole('textbox', { name: 'One-time setup link' }).inputValue();
    const recoveryToken = new URL(recoveryUrl).searchParams.get('token');
    await member.getByRole('button', { name: 'Log out' }).click();
    await member.goto(`/reset-password?token=${recoveryToken}`);
    await member.getByLabel('New password').fill('member-recovered-password');
    await member.getByRole('button', { name: 'Set new password' }).click();
    await expect(member.getByRole('status')).toContainText('Password updated');
    await login(member, username, 'member-recovered-password');
    await memberContext.close();
  });

  test('sharing notification, owner transfer, and inactive owner handoff @sharing @admin', async ({ page, browser }) => {
    test.setTimeout(90_000);
    await bootstrapOrLoginAdmin(page);
    const suffix = Date.now();
    const ownerName = `owner-${suffix}`;
    const receiverName = `receiver-${suffix}`;
    const departedName = `departed-${suffix}`;
    for (const username of [ownerName, receiverName, departedName]) {
      expect((await page.request.post('/api/v1/admin/users', {
        data: { username, password: 'handoff-test-password', role: 'USER' }
      })).status()).toBe(201);
    }
    const origin = new URL(page.url()).origin;
    const ownerContext = await browser.newContext({ baseURL: origin, viewport: page.viewportSize()! });
    const owner = await ownerContext.newPage();
    await login(owner, ownerName, 'handoff-test-password');
    const title = `Transfer plan ${suffix}`;
    await createList(owner, title, 'TODO');
    await owner.getByRole('button', { name: 'Sharing', exact: true }).click();
    const sharing = owner.locator('.share-panel');
    const shareForm = sharing.locator('form.inline-form').first();
    await shareForm.getByLabel('Username').fill(receiverName);
    await shareForm.getByRole('button', { name: 'Share access' }).click();
    await expect(owner.getByRole('status')).toContainText(`Access granted to ${receiverName}`);

    const receiverContext = await browser.newContext({ baseURL: origin, viewport: page.viewportSize()! });
    const receiver = await receiverContext.newPage();
    await login(receiver, receiverName, 'handoff-test-password');
    await expect(receiver.locator('.notifications-panel')).toContainText(`${title} was shared with you by ${ownerName}`);
    await expect(receiver.getByRole('heading', { name: title })).toBeVisible();

    owner.once('dialog', dialog => dialog.accept());
    await sharing.locator('.transfer-ownership > summary').click();
    await sharing.getByLabel("New owner's username").fill(receiverName);
    await sharing.locator('.transfer-ownership').getByRole('button', { name: 'Transfer ownership' }).click();
    await expect(owner.getByRole('status')).toContainText('Ownership transferred');
    await receiver.reload();
    await receiver.getByRole('button', { name: 'Sharing', exact: true }).click();
    await expect(receiver.locator('.share-panel')).toBeVisible();

    const departedContext = await browser.newContext({ baseURL: origin, viewport: page.viewportSize()! });
    const departed = await departedContext.newPage();
    await login(departed, departedName, 'handoff-test-password');
    const abandonedTitle = `Departed work ${suffix}`;
    await createList(departed, abandonedTitle, 'TODO');
    await page.getByRole('button', { name: 'Administration', exact: true }).click();
    const panel = page.locator('.admin-panel');
    await panel.getByRole('button', { name: 'Refresh admin panel' }).click();
    const row = panel.locator('ul.admin-user-list').first().locator('li').filter({ hasText: departedName });
    await row.getByRole('button', { name: `Deactivate: ${departedName}` }).click();
    page.once('dialog', dialog => dialog.accept());
    await row.getByRole('textbox', { name: `New owner's username: ${departedName}` }).fill(receiverName);
    await row.getByRole('button', { name: 'Transfer owned lists' }).click();
    await expect(page.getByRole('status')).toContainText('1 list transferred');
    await receiver.reload();
    await expect(receiver.getByRole('heading', { name: abandonedTitle })).toBeVisible();
    await departedContext.close();
    await receiverContext.close();
    await ownerContext.close();
  });
});
