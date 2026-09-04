import { afterEach, describe, expect, it, vi } from 'vitest';
import { createItem, createList, cloneList, createPublicShare, createAdminUser, clearCompletedItems, deleteItem, deleteList, getAdminLists, getAdminSettings, getAdminUsers, getCurrentUser, getItems, getList, getListShares, getLists, getNotifications, getPublicShare, login, logout, markNotificationRead, register, requestMagicLink, requestPasswordReset, consumeMagicLink, consumePasswordReset, revokeListShare, revokePublicShare, scrapeUrl, shareListWithUser, skipChoreItem, postponeChoreItem, updateAdminSettings, updateAdminUser, updateItem, updateList, claimPublicItem } from './client';


function apiCalls(fetchMock: ReturnType<typeof vi.spyOn>) {
  return fetchMock.mock.calls.filter((call: unknown[]) => call[0] !== '/api/v1/auth/csrf');
}

function expectApiCall(fetchMock: ReturnType<typeof vi.spyOn>, index: number, path: string, options: Record<string, unknown>) {
  const call = apiCalls(fetchMock)[index - 1];
  expect(call[0]).toBe(path);
  expect(call[1]).toMatchObject(options);
}

function mockJsonFetch(payload: unknown): ReturnType<typeof vi.spyOn> {
  return vi.spyOn(globalThis, 'fetch').mockImplementation(async (path) => {
    if (path === '/api/v1/auth/csrf') {
      return { ok: true, json: async () => ({ headerName: 'X-CSRF-TOKEN', token: 'csrf-123' }) } as Response;
    }
    return { ok: true, json: async () => payload } as Response;
  });
}

describe('auth API client', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('posts register and login JSON with credentials included', async () => {
    const fetchMock = mockJsonFetch({ id: 'u1', username: 'uwe', email: 'uwe@example.test', role: 'ADMIN' });

    await register({ username: 'uwe', email: 'uwe@example.test', password: 'correct horse battery staple' });
    await login({ username: 'uwe', password: 'correct horse battery staple' });

    expectApiCall(fetchMock, 1, '/api/v1/auth/register', expect.objectContaining({
      method: 'POST',
      credentials: 'include',
      body: JSON.stringify({ username: 'uwe', email: 'uwe@example.test', password: 'correct horse battery staple' })
    }));
    expectApiCall(fetchMock, 2, '/api/v1/auth/login', expect.objectContaining({
      method: 'POST',
      credentials: 'include'
    }));
  });

  it('returns null for unauthenticated current user', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue({ status: 401, ok: false } as Response);

    await expect(getCurrentUser()).resolves.toBeNull();
  });

  it('posts logout with credentials included', async () => {
    const fetchMock = mockJsonFetch({});

    await logout();

    expectApiCall(fetchMock, 1, '/api/v1/auth/logout', expect.objectContaining({
      method: 'POST',
      credentials: 'include'
    }));
  });

  it('posts magic link and password reset auth flows with credentials included', async () => {
    const fetchMock = mockJsonFetch({ id: 'u1', username: 'uwe', email: 'uwe@example.test', role: 'ADMIN' });

    await requestMagicLink({ email: 'uwe@example.test' });
    await consumeMagicLink({ token: 'magic-token' });
    await requestPasswordReset({ email: 'uwe@example.test' });
    await consumePasswordReset({ token: 'reset-token', password: 'new password' });

    expectApiCall(fetchMock, 1, '/api/v1/auth/magic-link', expect.objectContaining({
      method: 'POST', credentials: 'include', body: JSON.stringify({ email: 'uwe@example.test' })
    }));
    expectApiCall(fetchMock, 2, '/api/v1/auth/magic-link/consume', expect.objectContaining({
      method: 'POST', credentials: 'include', body: JSON.stringify({ token: 'magic-token' })
    }));
    expectApiCall(fetchMock, 3, '/api/v1/auth/password-reset', expect.objectContaining({
      method: 'POST', credentials: 'include', body: JSON.stringify({ email: 'uwe@example.test' })
    }));
    expectApiCall(fetchMock, 4, '/api/v1/auth/password-reset/consume', expect.objectContaining({
      method: 'POST', credentials: 'include', body: JSON.stringify({ token: 'reset-token', password: 'new password' })
    }));
  });

  it('reads and updates admin settings with credentials included', async () => {
    const fetchMock = mockJsonFetch({ registrationEnabled: true });

    await getAdminSettings();
    await updateAdminSettings({ registrationEnabled: true });

    expectApiCall(fetchMock, 1, '/api/v1/admin/settings', expect.objectContaining({
      credentials: 'include'
    }));
    expectApiCall(fetchMock, 2, '/api/v1/admin/settings', expect.objectContaining({
      method: 'PUT',
      credentials: 'include',
      body: JSON.stringify({ registrationEnabled: true })
    }));
  });

  it('fetches and sends CSRF token for authenticated browser mutations', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockImplementation(async (path) => {
      if (path === '/api/v1/auth/csrf') {
        return { ok: true, json: async () => ({ headerName: 'X-CSRF-TOKEN', token: 'csrf-123' }) } as Response;
      }
      return { ok: true, json: async () => ({ registrationEnabled: true }) } as Response;
    });

    await updateAdminSettings({ registrationEnabled: true });

    expect(fetchMock).toHaveBeenNthCalledWith(1, '/api/v1/auth/csrf', expect.objectContaining({ credentials: 'include' }));
    expectApiCall(fetchMock, 1, '/api/v1/admin/settings', {
      method: 'PUT',
      credentials: 'include',
      headers: { 'Content-Type': 'application/json', 'X-CSRF-TOKEN': 'csrf-123' },
      body: JSON.stringify({ registrationEnabled: true })
    });
  });

  it('reads admin users with credentials included', async () => {
    const fetchMock = mockJsonFetch([{ id: 'u1', username: 'admin', email: 'admin@example.test', role: 'ADMIN', createdAt: '2027-01-01T00:00:00Z' }]);

    await getAdminUsers();

    expectApiCall(fetchMock, 1, '/api/v1/admin/users', expect.objectContaining({
      credentials: 'include'
    }));
  });

  it('calls owner list CRUD endpoints with credentials included', async () => {
    const fetchMock = mockJsonFetch({ id: 'l1', title: 'Birthday', type: 'WISH' });

    await getLists();
    await getList('l1');
    await createList({ title: 'Birthday', description: 'Gift ideas', type: 'WISH' });
    await updateList('l1', { title: 'Birthday 2027', type: 'EVENT', targetDate: '2027-01-01T00:00:00Z' });
    await cloneList('l1', { title: 'Birthday copy' });
    await deleteList('l1');

    expectApiCall(fetchMock, 1, '/api/v1/lists', expect.objectContaining({ credentials: 'include' }));
    expectApiCall(fetchMock, 2, '/api/v1/lists/l1', expect.objectContaining({ credentials: 'include' }));
    expectApiCall(fetchMock, 3, '/api/v1/lists', expect.objectContaining({
      method: 'POST',
      credentials: 'include',
      body: JSON.stringify({ title: 'Birthday', description: 'Gift ideas', type: 'WISH' })
    }));
    expectApiCall(fetchMock, 4, '/api/v1/lists/l1', expect.objectContaining({
      method: 'PUT',
      credentials: 'include'
    }));
    expectApiCall(fetchMock, 5, '/api/v1/lists/l1/clone', expect.objectContaining({
      method: 'POST',
      credentials: 'include',
      body: JSON.stringify({ title: 'Birthday copy' })
    }));
    expectApiCall(fetchMock, 6, '/api/v1/lists/l1', expect.objectContaining({
      method: 'DELETE',
      credentials: 'include'
    }));
  });

  it('calls owner item CRUD endpoints with credentials included', async () => {
    const fetchMock = mockJsonFetch({ id: 'i1', listId: 'l1', name: 'Camera strap', status: 'OPEN' });

    await getItems('l1');
    await createItem('l1', { name: 'Camera strap' });
    await updateItem('i1', { name: 'Leather strap', status: 'PURCHASED', price: 29.9 });
    await deleteItem('i1');
    await clearCompletedItems('l1');
    await skipChoreItem('i2');
    await postponeChoreItem('i2', { days: 3 });

    expectApiCall(fetchMock, 1, '/api/v1/lists/l1/items', expect.objectContaining({ credentials: 'include' }));
    expectApiCall(fetchMock, 2, '/api/v1/lists/l1/items', expect.objectContaining({
      method: 'POST',
      credentials: 'include',
      body: JSON.stringify({ name: 'Camera strap' })
    }));
    expectApiCall(fetchMock, 3, '/api/v1/items/i1', expect.objectContaining({
      method: 'PUT',
      credentials: 'include'
    }));
    expectApiCall(fetchMock, 4, '/api/v1/items/i1', expect.objectContaining({
      method: 'DELETE',
      credentials: 'include'
    }));
    expectApiCall(fetchMock, 5, '/api/v1/lists/l1/items/completed', expect.objectContaining({
      method: 'DELETE',
      credentials: 'include'
    }));
    expectApiCall(fetchMock, 6, '/api/v1/items/i2/skip', expect.objectContaining({
      method: 'POST',
      credentials: 'include'
    }));
    expectApiCall(fetchMock, 7, '/api/v1/items/i2/postpone', expect.objectContaining({
      method: 'POST',
      credentials: 'include',
      body: JSON.stringify({ days: 3 })
    }));
  });

  it('allows creating a URL-only wishlist item payload', async () => {
    const fetchMock = mockJsonFetch({ id: 'i2', listId: 'l1', name: 'Loading metadata…', url: 'https://shop.test/camera', status: 'OPEN' });

    await createItem('l1', { url: 'https://shop.test/camera' });

    expectApiCall(fetchMock, 1, '/api/v1/lists/l1/items', expect.objectContaining({
      method: 'POST',
      credentials: 'include',
      body: JSON.stringify({ url: 'https://shop.test/camera' })
    }));
  });

  it('calls list share endpoints with credentials included', async () => {
    const fetchMock = mockJsonFetch({ listId: 'l1', userId: 'u2', username: 'shared' });

    await getListShares('l1');
    await shareListWithUser('l1', { username: 'shared', permission: 'CONTRIBUTE' });
    await revokeListShare('l1', 'shared');

    expectApiCall(fetchMock, 1, '/api/v1/lists/l1/shares', expect.objectContaining({ credentials: 'include' }));
    expectApiCall(fetchMock, 2, '/api/v1/lists/l1/shares', expect.objectContaining({
      method: 'POST',
      credentials: 'include',
      body: JSON.stringify({ username: 'shared', permission: 'CONTRIBUTE' })
    }));
    expectApiCall(fetchMock, 3, '/api/v1/lists/l1/shares/shared', expect.objectContaining({
      method: 'DELETE',
      credentials: 'include'
    }));
  });

  it('calls public share token and guest endpoints', async () => {
    const fetchMock = mockJsonFetch({ shareToken: 'tok123', title: 'Birthday', items: [] });

    await createPublicShare('l1', 'SIGNUP');
    await revokePublicShare('l1');
    await getPublicShare('tok123');
    await claimPublicItem('tok123', 'i1', { guestName: 'Annette' });

    expectApiCall(fetchMock, 1, '/api/v1/lists/l1/public-share', expect.objectContaining({
      method: 'POST',
      credentials: 'include',
      headers: expect.objectContaining({ 'X-CSRF-TOKEN': 'csrf-123' }),
      body: JSON.stringify({ mode: 'SIGNUP' })
    }));
    expectApiCall(fetchMock, 2, '/api/v1/lists/l1/public-share', expect.objectContaining({
      method: 'DELETE',
      credentials: 'include',
      headers: expect.objectContaining({ 'X-CSRF-TOKEN': 'csrf-123' })
    }));
    expectApiCall(fetchMock, 3, '/api/v1/share/tok123', expect.objectContaining({ credentials: 'include' }));
    expectApiCall(fetchMock, 4, '/api/v1/share/tok123/items/i1/claim', expect.objectContaining({
      method: 'POST',
      credentials: 'include',
      body: JSON.stringify({ guestName: 'Annette' })
    }));
  });

  it('calls scraper endpoint with credentials included', async () => {
    const fetchMock = mockJsonFetch({ title: 'Pen', description: null, imageUrl: null, price: 24.95 });

    await scrapeUrl({ url: 'https://example.test/pen' });

    expectApiCall(fetchMock, 1, '/api/v1/utils/scrape', expect.objectContaining({
      method: 'POST',
      credentials: 'include',
      body: JSON.stringify({ url: 'https://example.test/pen' })
    }));
  });

  it('calls notification endpoints with credentials included', async () => {
    const fetchMock = mockJsonFetch({ id: 'n1', message: 'Water plants is due on 2027-01-01.' });

    await getNotifications();
    await markNotificationRead('n1');

    expectApiCall(fetchMock, 1, '/api/v1/notifications', expect.objectContaining({
      credentials: 'include'
    }));
    expectApiCall(fetchMock, 2, '/api/v1/notifications/n1/read', expect.objectContaining({
      method: 'PUT',
      credentials: 'include'
    }));
  });

});
