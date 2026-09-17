import { expect, test } from '@playwright/test';

test.describe('Listful Thinking GUI smoke @gui @smoke', () => {
  test('bootstraps first admin and creates a grocery item @auth @lists', async ({ page }) => {
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

    await page.goto('/');

    await expect(page.getByRole('heading', { name: 'Listful Thinking' })).toBeVisible();

    const registerForm = page.locator('form.panel').filter({ has: page.getByRole('heading', { name: 'Register' }) });
    await expect(registerForm).toBeVisible();
    await registerForm.getByLabel('Username').fill('admin');
    await registerForm.getByLabel('Email').fill('admin@example.test');
    await registerForm.getByLabel('Password').fill('listful-admin-password');
    await registerForm.getByRole('button', { name: 'Register' }).click();

    await expect(page.getByRole('heading', { name: 'Your lists' })).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Admin' })).toBeVisible();

    const listTitle = 'GUI groceries';
    await page.getByPlaceholder('New list title').fill(listTitle);
    await page.locator('form.inline-form').filter({ has: page.getByPlaceholder('New list title') }).getByRole('combobox').selectOption('GROCERY');
    await page.getByRole('button', { name: 'Create list' }).click();

    await expect(page.getByRole('heading', { name: listTitle })).toBeVisible();

    await page.getByPlaceholder('New item name').fill('Oat milk');
    await page.getByRole('textbox', { name: 'Quantity' }).fill('2 cartons');
    await page.getByRole('textbox', { name: 'Category' }).fill('Pantry');
    await page.getByRole('button', { name: 'Add item' }).click();

    await expect(page.getByText('Oat milk')).toBeVisible();
    await expect(page.getByText('2 cartons · Pantry')).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Pantry' })).toBeVisible();

    await expect.soft.poll(() => browserErrors, { message: 'browser console/page errors' }).toEqual([]);
    await expect.soft.poll(() => unexpectedResponses, { message: 'unexpected HTTP errors' }).toEqual([]);
  });
});
