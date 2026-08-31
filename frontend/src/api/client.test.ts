import { afterEach, describe, expect, it, vi } from 'vitest';
import { getAdminSettings, getCurrentUser, login, logout, register, updateAdminSettings } from './client';

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
});
