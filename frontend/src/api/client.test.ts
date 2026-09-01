import { afterEach, describe, expect, it, vi } from 'vitest';
import { createItem, createList, createPublicShare, deleteItem, deleteList, getAdminSettings, getAdminUsers, getCurrentUser, getItems, getList, getListShares, getLists, getNotifications, getPublicShare, login, logout, markNotificationRead, register, requestMagicLink, requestPasswordReset, consumeMagicLink, consumePasswordReset, revokeListShare, revokePublicShare, scrapeUrl, shareListWithUser, updateAdminSettings, updateItem, updateList, claimPublicItem } from './client';

describe('auth API client', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('posts register and login JSON with credentials included', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue({
      ok: true,
      json: async () => ({ id: 'u1', username: 'uwe', email: 'uwe@example.test', role: 'ADMIN' })
    } as Response);

    await register({ username: 'uwe', email: 'uwe@example.test', password: 'correct horse battery staple' });
    await login({ username: 'uwe', password: 'correct horse battery staple' });

    expect(fetchMock).toHaveBeenNthCalledWith(1, '/api/v1/auth/register', expect.objectContaining({
      method: 'POST',
      credentials: 'include',
      body: JSON.stringify({ username: 'uwe', email: 'uwe@example.test', password: 'correct horse battery staple' })
    }));
    expect(fetchMock).toHaveBeenNthCalledWith(2, '/api/v1/auth/login', expect.objectContaining({
      method: 'POST',
      credentials: 'include'
    }));
  });

  it('returns null for unauthenticated current user', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue({ status: 401, ok: false } as Response);

    await expect(getCurrentUser()).resolves.toBeNull();
  });

  it('posts logout with credentials included', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue({ ok: true } as Response);

    await logout();

    expect(fetchMock).toHaveBeenCalledWith('/api/v1/auth/logout', expect.objectContaining({
      method: 'POST',
      credentials: 'include'
    }));
  });

  it('posts magic link and password reset auth flows with credentials included', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue({
      ok: true,
      json: async () => ({ id: 'u1', username: 'uwe', email: 'uwe@example.test', role: 'ADMIN' })
    } as Response);

    await requestMagicLink({ email: 'uwe@example.test' });
    await consumeMagicLink({ token: 'magic-token' });
    await requestPasswordReset({ email: 'uwe@example.test' });
    await consumePasswordReset({ token: 'reset-token', password: 'new password' });

    expect(fetchMock).toHaveBeenNthCalledWith(1, '/api/v1/auth/magic-link', expect.objectContaining({
      method: 'POST', credentials: 'include', body: JSON.stringify({ email: 'uwe@example.test' })
    }));
    expect(fetchMock).toHaveBeenNthCalledWith(2, '/api/v1/auth/magic-link/consume', expect.objectContaining({
      method: 'POST', credentials: 'include', body: JSON.stringify({ token: 'magic-token' })
    }));
    expect(fetchMock).toHaveBeenNthCalledWith(3, '/api/v1/auth/password-reset', expect.objectContaining({
      method: 'POST', credentials: 'include', body: JSON.stringify({ email: 'uwe@example.test' })
    }));
    expect(fetchMock).toHaveBeenNthCalledWith(4, '/api/v1/auth/password-reset/consume', expect.objectContaining({
      method: 'POST', credentials: 'include', body: JSON.stringify({ token: 'reset-token', password: 'new password' })
    }));
  });

  it('reads and updates admin settings with credentials included', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue({
      ok: true,
      json: async () => ({ registrationEnabled: true })
    } as Response);

    await getAdminSettings();
    await updateAdminSettings({ registrationEnabled: true });

    expect(fetchMock).toHaveBeenNthCalledWith(1, '/api/v1/admin/settings', expect.objectContaining({
      credentials: 'include'
    }));
    expect(fetchMock).toHaveBeenNthCalledWith(2, '/api/v1/admin/settings', expect.objectContaining({
      method: 'PUT',
      credentials: 'include',
      body: JSON.stringify({ registrationEnabled: true })
    }));
  });

  it('reads admin users with credentials included', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue({
      ok: true,
      json: async () => ([{ id: 'u1', username: 'admin', email: 'admin@example.test', role: 'ADMIN', createdAt: '2027-01-01T00:00:00Z' }])
    } as Response);

    await getAdminUsers();

    expect(fetchMock).toHaveBeenCalledWith('/api/v1/admin/users', expect.objectContaining({
      credentials: 'include'
    }));
  });

  it('calls owner list CRUD endpoints with credentials included', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue({
      ok: true,
      json: async () => ({ id: 'l1', title: 'Birthday', type: 'WISH' })
    } as Response);

    await getLists();
    await getList('l1');
    await createList({ title: 'Birthday', description: 'Gift ideas', type: 'WISH' });
    await updateList('l1', { title: 'Birthday 2027', type: 'EVENT', targetDate: '2027-01-01T00:00:00Z' });
    await deleteList('l1');

    expect(fetchMock).toHaveBeenNthCalledWith(1, '/api/v1/lists', expect.objectContaining({ credentials: 'include' }));
    expect(fetchMock).toHaveBeenNthCalledWith(2, '/api/v1/lists/l1', expect.objectContaining({ credentials: 'include' }));
    expect(fetchMock).toHaveBeenNthCalledWith(3, '/api/v1/lists', expect.objectContaining({
      method: 'POST',
      credentials: 'include',
      body: JSON.stringify({ title: 'Birthday', description: 'Gift ideas', type: 'WISH' })
    }));
    expect(fetchMock).toHaveBeenNthCalledWith(4, '/api/v1/lists/l1', expect.objectContaining({
      method: 'PUT',
      credentials: 'include'
    }));
    expect(fetchMock).toHaveBeenNthCalledWith(5, '/api/v1/lists/l1', expect.objectContaining({
      method: 'DELETE',
      credentials: 'include'
    }));
  });

  it('calls owner item CRUD endpoints with credentials included', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue({
      ok: true,
      json: async () => ({ id: 'i1', listId: 'l1', name: 'Camera strap', status: 'OPEN' })
    } as Response);

    await getItems('l1');
    await createItem('l1', { name: 'Camera strap' });
    await updateItem('i1', { name: 'Leather strap', status: 'PURCHASED', price: 29.9 });
    await deleteItem('i1');

    expect(fetchMock).toHaveBeenNthCalledWith(1, '/api/v1/lists/l1/items', expect.objectContaining({ credentials: 'include' }));
    expect(fetchMock).toHaveBeenNthCalledWith(2, '/api/v1/lists/l1/items', expect.objectContaining({
      method: 'POST',
      credentials: 'include',
      body: JSON.stringify({ name: 'Camera strap' })
    }));
    expect(fetchMock).toHaveBeenNthCalledWith(3, '/api/v1/items/i1', expect.objectContaining({
      method: 'PUT',
      credentials: 'include'
    }));
    expect(fetchMock).toHaveBeenNthCalledWith(4, '/api/v1/items/i1', expect.objectContaining({
      method: 'DELETE',
      credentials: 'include'
    }));
  });

  it('allows creating a URL-only wishlist item payload', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue({
      ok: true,
      json: async () => ({ id: 'i2', listId: 'l1', name: 'Loading metadata…', url: 'https://shop.test/camera', status: 'OPEN' })
    } as Response);

    await createItem('l1', { url: 'https://shop.test/camera' });

    expect(fetchMock).toHaveBeenCalledWith('/api/v1/lists/l1/items', expect.objectContaining({
      method: 'POST',
      credentials: 'include',
      body: JSON.stringify({ url: 'https://shop.test/camera' })
    }));
  });

  it('calls list share endpoints with credentials included', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue({
      ok: true,
      json: async () => ({ listId: 'l1', userId: 'u2', username: 'shared' })
    } as Response);

    await getListShares('l1');
    await shareListWithUser('l1', { username: 'shared' });
    await revokeListShare('l1', 'shared');

    expect(fetchMock).toHaveBeenNthCalledWith(1, '/api/v1/lists/l1/shares', expect.objectContaining({ credentials: 'include' }));
    expect(fetchMock).toHaveBeenNthCalledWith(2, '/api/v1/lists/l1/shares', expect.objectContaining({
      method: 'POST',
      credentials: 'include',
      body: JSON.stringify({ username: 'shared' })
    }));
    expect(fetchMock).toHaveBeenNthCalledWith(3, '/api/v1/lists/l1/shares/shared', expect.objectContaining({
      method: 'DELETE',
      credentials: 'include'
    }));
  });

  it('calls public share token and guest endpoints', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue({
      ok: true,
      json: async () => ({ shareToken: 'tok123', title: 'Birthday', items: [] })
    } as Response);

    await createPublicShare('l1');
    await revokePublicShare('l1');
    await getPublicShare('tok123');
    await claimPublicItem('tok123', 'i1', { guestName: 'Annette' });

    expect(fetchMock).toHaveBeenNthCalledWith(1, '/api/v1/lists/l1/public-share', expect.objectContaining({
      method: 'POST',
      credentials: 'include'
    }));
    expect(fetchMock).toHaveBeenNthCalledWith(2, '/api/v1/lists/l1/public-share', expect.objectContaining({
      method: 'DELETE',
      credentials: 'include'
    }));
    expect(fetchMock).toHaveBeenNthCalledWith(3, '/api/v1/share/tok123', expect.objectContaining({ credentials: 'include' }));
    expect(fetchMock).toHaveBeenNthCalledWith(4, '/api/v1/share/tok123/items/i1/claim', expect.objectContaining({
      method: 'POST',
      credentials: 'include',
      body: JSON.stringify({ guestName: 'Annette' })
    }));
  });

  it('calls scraper endpoint with credentials included', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue({
      ok: true,
      json: async () => ({ title: 'Pen', description: null, imageUrl: null, price: 24.95 })
    } as Response);

    await scrapeUrl({ url: 'https://example.test/pen' });

    expect(fetchMock).toHaveBeenCalledWith('/api/v1/utils/scrape', expect.objectContaining({
      method: 'POST',
      credentials: 'include',
      body: JSON.stringify({ url: 'https://example.test/pen' })
    }));
  });

  it('calls notification endpoints with credentials included', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue({
      ok: true,
      json: async () => ({ id: 'n1', message: 'Water plants is due on 2027-01-01.' })
    } as Response);

    await getNotifications();
    await markNotificationRead('n1');

    expect(fetchMock).toHaveBeenNthCalledWith(1, '/api/v1/notifications', expect.objectContaining({
      credentials: 'include'
    }));
    expect(fetchMock).toHaveBeenNthCalledWith(2, '/api/v1/notifications/n1/read', expect.objectContaining({
      method: 'PUT',
      credentials: 'include'
    }));
  });

});
