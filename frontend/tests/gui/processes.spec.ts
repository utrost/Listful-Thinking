import { expect, test, type Page } from '@playwright/test';

import { bootstrapOrLoginAdmin, collectPageDiagnostics, createList } from './helpers';

async function login(page: Page, username: string, password: string) {
  await page.goto('/');
  const form = page.locator('form.panel').filter({ has: page.getByRole('heading', { name: 'Log in', exact: true }) });
  await form.getByLabel('Username').fill(username);
  await form.getByLabel('Password').fill(password);
  await form.getByRole('button', { name: 'Log in' }).click();
}

test.describe('User and administrator processes @gui @smoke', () => {
  test('admin controls registration, member access, and private usage counts @admin', async ({ page, browser }) => {
    test.setTimeout(90_000);
    const diagnostics = collectPageDiagnostics(page);
    await bootstrapOrLoginAdmin(page);
    await page.getByRole('button', { name: 'Administration', exact: true }).click();
    const panel = page.locator('.admin-panel');
    const registration = panel.getByRole('checkbox', { name: 'Registration enabled' });
    await expect(registration).not.toBeChecked();
    await expect(panel.getByRole('button', { name: 'Deactivate: admin' })).toBeDisabled();
    await expect(panel).toContainText('At least one active admin must remain.');

    await registration.check();
    await expect(registration).toBeChecked();
    const baseURL = new URL(page.url()).origin;
    const selfContext = await browser.newContext({ baseURL, viewport: page.viewportSize()! });
    const self = await selfContext.newPage();
    await self.goto('/');
    const registerForm = self.locator('form.panel').filter({ has: self.getByRole('heading', { name: 'Register' }) });
    const selfName = `self-${Date.now()}`;
    await registerForm.getByLabel('Username').fill(selfName);
    await registerForm.getByLabel('Email').fill(`${selfName}@example.test`);
    await registerForm.getByLabel('Password').fill('self-registration-password');
    await registerForm.getByRole('button', { name: 'Register' }).click();
    await expect(self.getByRole('heading', { name: 'Your lists' })).toBeVisible();
    await expect(self.getByRole('button', { name: 'Administration' })).toHaveCount(0);
    await selfContext.close();

    await registration.uncheck();
    await expect(registration).not.toBeChecked();
    const closedContext = await browser.newContext({ baseURL, viewport: page.viewportSize()! });
    const closed = await closedContext.newPage();
    await closed.goto('/');
    await expect(closed.getByText('Self-registration is currently disabled. Ask an administrator to create your account.')).toBeVisible();
    await expect(closed.locator('form.panel').filter({ has: closed.getByRole('heading', { name: 'Register' }) })).toHaveCount(0);
    await closedContext.close();

    const username = `managed-${Date.now()}`;
    const password = 'managed-member-password';
    const form = panel.locator('form.inline-form');
    await form.getByLabel('Username').fill(username);
    await form.getByLabel('Email').fill(`${username}@example.test`);
    await form.getByLabel('Password').fill(password);
    await form.getByLabel('Role').selectOption('USER');
    await form.getByRole('button', { name: 'Create user' }).click();
    const userRow = panel.locator('ul.admin-user-list').first().locator('li').filter({ hasText: username });
    await expect(userRow).toContainText('USER · active');
    await expect(form.getByLabel('Username')).toHaveValue('');

    const memberContext = await browser.newContext({ baseURL, viewport: page.viewportSize()! });
    const member = await memberContext.newPage();
    await login(member, username, password);
    await expect(member.getByRole('heading', { name: 'Your lists' })).toBeVisible();
    const listTitle = `Private work ${Date.now()}`;
    await createList(member, listTitle, 'TODO');
    await panel.getByRole('button', { name: 'Refresh admin panel' }).click();
    const inventory = panel.locator('ul.admin-user-list').nth(1).locator('li').filter({ hasText: username });
    await expect(inventory).toContainText('1 list');
    await expect(panel).not.toContainText(listTitle);

    await member.getByRole('button', { name: 'Log out' }).click();
    await userRow.getByRole('button', { name: `Deactivate: ${username}` }).click();
    await expect(userRow).toContainText('USER · inactive');
    await login(member, username, password);
    await expect(member.getByRole('alert')).toContainText('Invalid username or password.');
    await userRow.getByRole('button', { name: `Activate: ${username}` }).click();
    await expect(userRow).toContainText('USER · active');
    await login(member, username, password);
    await expect(member.getByRole('heading', { name: listTitle })).toBeVisible();
    await memberContext.close();

    const backupAdmin = `backup-admin-${Date.now()}`;
    await form.getByLabel('Username').fill(backupAdmin);
    await form.getByLabel('Password').fill('backup-admin-password');
    await form.getByLabel('Role').selectOption('ADMIN');
    await form.getByRole('button', { name: 'Create user' }).click();
    const backupRow = panel.locator('ul.admin-user-list').first().locator('li').filter({ hasText: backupAdmin });
    await expect(backupRow).toContainText('ADMIN · active');
    const backupContext = await browser.newContext({ baseURL, viewport: page.viewportSize()! });
    const backupPage = await backupContext.newPage();
    await login(backupPage, backupAdmin, 'backup-admin-password');
    await expect(backupPage.getByRole('button', { name: 'Administration', exact: true })).toBeVisible();
    await backupContext.close();
    await backupRow.getByRole('button', { name: `Deactivate: ${backupAdmin}` }).click();
    await expect(backupRow).toContainText('ADMIN · inactive');
    await expect(panel.getByRole('button', { name: 'Deactivate: admin' })).toBeDisabled();
    await diagnostics.expectClean();
  });

  test('owner shares through the UI, changes permission, and revokes access @sharing', async ({ page, browser }) => {
    test.setTimeout(90_000);
    const diagnostics = collectPageDiagnostics(page);
    await bootstrapOrLoginAdmin(page);
    const username = `collaborator-${Date.now()}`;
    const password = 'collaborator-password';
    expect((await page.request.post('/api/v1/admin/users', { data: { username, password, role: 'USER' } })).status()).toBe(201);

    const title = `Shared process ${Date.now()}`;
    await createList(page, title, 'TODO');
    await page.getByLabel('New item name').fill('Owner task');
    await page.getByRole('button', { name: 'Add item' }).click();
    await page.getByRole('button', { name: 'Sharing', exact: true }).click();
    const sharePanel = page.locator('.share-panel');
    const shareForm = sharePanel.locator('form.inline-form').first();
    await shareForm.getByLabel('Username').fill(username);
    await shareForm.getByLabel('Share permission').selectOption('READ');
    await shareForm.getByRole('button', { name: 'Share access' }).click();
    const share = sharePanel.locator('.chip-list li').filter({ hasText: username });
    await expect(share).toContainText('READ');

    const context = await browser.newContext({ baseURL: new URL(page.url()).origin, viewport: page.viewportSize()! });
    const member = await context.newPage();
    await login(member, username, password);
    await expect(member.getByRole('heading', { name: title })).toBeVisible();
    await expect(member.getByText('Owner task', { exact: true })).toBeVisible();
    await expect(member.locator('.composer-disclosure')).toHaveCount(0);
    await expect(member.getByRole('button', { name: 'Sharing', exact: true })).toHaveCount(0);
    await expect(member.getByRole('button', { name: 'List settings', exact: true })).toHaveCount(0);

    await shareForm.getByLabel('Username').fill(username);
    await shareForm.getByLabel('Share permission').selectOption('CONTRIBUTE');
    await shareForm.getByRole('button', { name: 'Share access' }).click();
    await expect(share).toContainText('CONTRIBUTE');
    await member.reload();
    await expect(member.locator('.composer-disclosure')).toBeVisible();
    await member.locator('.composer-disclosure > summary').click();
    await member.getByLabel('New item name').fill('Contributor task');
    await member.getByRole('button', { name: 'Add item' }).click();
    await expect(member.getByText('Contributor task', { exact: true })).toBeVisible();
    await member.getByLabel('More: Contributor task').click();
    await expect(member.getByRole('button', { name: 'Delete: Contributor task' })).toHaveCount(0);

    await share.getByRole('button', { name: 'Revoke' }).click();
    await expect(share).toHaveCount(0);
    await member.reload();
    await expect(member.getByRole('heading', { name: title })).toHaveCount(0);
    await expect(member.getByRole('heading', { name: 'Start with one list.' })).toBeVisible();
    await context.close();
    await diagnostics.expectClean();
  });
});
