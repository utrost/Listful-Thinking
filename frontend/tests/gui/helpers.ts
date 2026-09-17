import { expect, type Page } from '@playwright/test';

export function collectPageDiagnostics(page: Page) {
  const browserErrors: string[] = [];
  const unexpectedResponses: string[] = [];

  page.on('pageerror', (error) => browserErrors.push(error.message));
  page.on('console', (message) => {
    if (message.type() === 'error' && !message.text().includes('status of 401')) {
      browserErrors.push(message.text());
    }
  });
  page.on('response', (response) => {
    const url = response.url();
    const status = response.status();
    const isExpectedAnonymousProbe = status === 401 && url.endsWith('/api/v1/auth/me');
    if (status >= 400 && !isExpectedAnonymousProbe) {
      unexpectedResponses.push(`${status} ${url}`);
    }
  });

  return {
    async expectClean() {
      await expect.soft.poll(() => browserErrors, { message: 'browser console/page errors' }).toEqual([]);
      await expect.soft.poll(() => unexpectedResponses, { message: 'unexpected HTTP errors' }).toEqual([]);
    }
  };
}

export async function bootstrapOrLoginAdmin(page: Page) {
  await page.goto('/');
  await expect(page.getByRole('heading', { name: 'Listful Thinking' })).toBeVisible();

  const registerForm = page.locator('form.panel').filter({ has: page.getByRole('heading', { name: 'Register' }) });
  const loginForm = page.locator('form.panel').filter({ has: page.getByRole('heading', { name: 'Log in' }) });
  await expect(loginForm).toBeVisible();

  const registerButton = registerForm.getByRole('button', { name: 'Register' });
  const canSelfRegister = await registerButton.waitFor({ state: 'visible', timeout: 3_000 }).then(() => true).catch(() => false);
  if (canSelfRegister) {
    await registerForm.getByLabel('Username').fill('admin');
    await registerForm.getByLabel('Email').fill('admin@example.test');
    await registerForm.getByLabel('Password').fill('listful-admin-password');
    await registerButton.click();
  } else {
    await loginForm.getByLabel('Username').fill('admin');
    await loginForm.getByLabel('Password').fill('listful-admin-password');
    await loginForm.getByRole('button', { name: 'Log in' }).click();
  }

  await expect(page.getByRole('heading', { name: 'Your lists' })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Admin' })).toBeVisible();
}

export async function createList(page: Page, title: string, type: 'WISH' | 'GROCERY' | 'TODO' | 'CHORE' | 'EVENT') {
  await page.getByPlaceholder('New list title').fill(title);
  await page.locator('form.inline-form').filter({ has: page.getByPlaceholder('New list title') }).getByRole('combobox').selectOption(type);
  await page.getByRole('button', { name: 'Create list' }).click();
  await expect(page.getByRole('heading', { name: title })).toBeVisible();
}
