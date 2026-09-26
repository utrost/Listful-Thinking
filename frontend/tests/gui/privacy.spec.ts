import { test, expect } from '@playwright/test';
import { bootstrapOrLoginAdmin, createList } from './helpers';

test('private and shared lists never contact image hosts before consent @security @smoke', async ({ page }, info) => {
  let requests = 0;
  let referer: string | undefined;
  await page.route('https://images.example.test/**', async route => {
    requests++;
    referer = route.request().headers().referer;
    await route.fulfill({ contentType: 'image/png', body: Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jRZkAAAAASUVORK5CYII=', 'base64') });
  });
  await bootstrapOrLoginAdmin(page);
  const title = `Private images ${info.project.name}`;
  await createList(page, title, 'WISH');
  const lists = await (await page.request.get('/api/v1/lists')).json();
  const list = lists.find((entry: {title: string}) => entry.title === title);
  const item = await page.request.post(`/api/v1/lists/${list.id}/items`, { data: { name: 'Personal photo', imageUrl: 'https://images.example.test/unique-private-id.png' } });
  expect(item.status()).toBe(201);
  const response = await page.request.get(`/api/v1/lists/${list.id}/items`);
  expect(response.headers()['cache-control']).toContain('no-store');
  await page.reload();
  const consent = page.getByRole('button', { name: 'Load external image from images.example.test', exact: true });
  await expect(consent).toBeVisible();
  expect(requests).toBe(0);
  await consent.click();
  await expect.poll(() => requests).toBe(1);
  expect(referer).toBeUndefined();
  const share = await (await page.request.post(`/api/v1/lists/${list.id}/public-share`, { data: { mode: 'VIEW' } })).json();
  await page.goto(`/s/${share.shareToken}`);
  await expect(consent).toBeVisible();
  expect(requests).toBe(1);
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
});

test('logout clears private unsaved list drafts @security @smoke', async ({ page }, info) => {
  await bootstrapOrLoginAdmin(page);
  await page.locator('.new-list-disclosure > summary').click();
  await page.getByPlaceholder('New list title').fill(`Confidential draft ${info.project.name}`);
  await page.getByRole('button', { name: 'Log out', exact: true }).click();
  const form = page.locator('form.panel').filter({ has: page.getByRole('heading', { name: 'Log in', exact: true }) });
  await form.getByLabel('Username', { exact: true }).fill('admin');
  await form.getByLabel('Password', { exact: true }).fill('listful-admin-password');
  await form.getByRole('button', { name: 'Log in', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Your lists', exact: true })).toBeVisible();
  await page.locator('.new-list-disclosure > summary').click();
  await expect(page.getByPlaceholder('New list title')).toHaveValue('');
});
