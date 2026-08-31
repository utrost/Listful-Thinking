import { afterEach, describe, expect, it, vi } from 'vitest';
import { createItem, createList, deleteItem, deleteList, getAdminSettings, getCurrentUser, getItems, getList, getLists, login, logout, register, updateAdminSettings, updateItem, updateList } from './client';

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
});
