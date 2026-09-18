import { describe, expect, it, vi, afterEach } from 'vitest';
import { ApiClientError, createItem, deleteItem, login, requestMagicLink } from './client';

describe('API error handling', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('parses structured backend error bodies for JSON responses', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue({
      ok: false,
      status: 400,
      headers: new Headers({ 'content-type': 'application/json' }),
      json: async () => ({ code: 'validation_failed', message: 'Item name is required.' })
    } as Response);

    await expect(login({ username: 'uwe', password: 'wrong' })).rejects.toMatchObject({
      name: 'ApiClientError',
      status: 400,
      code: 'validation_failed',
      backendMessage: 'Item name is required.'
    });
  });

  it('uses a safe fallback when an error response is not valid ApiError JSON', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue({
      ok: false,
      status: 503,
      headers: new Headers({ 'content-type': 'text/html' }),
      text: async () => '<h1>maintenance</h1>'
    } as Response);

    await expect(requestMagicLink({ email: 'uwe@example.test' })).rejects.toEqual(
      new ApiClientError(503, 'request_failed', 'Request failed: 503')
    );
  });

  it('parses structured errors from no-content and delete helpers too', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockImplementation(async (path) => {
      if (path === '/api/v1/auth/csrf') {
        return { ok: true, json: async () => ({ token: 'csrf-123' }) } as Response;
      }
      return {
        ok: false,
        status: 409,
        headers: new Headers({ 'content-type': 'application/json' }),
        json: async () => ({ code: 'conflict', message: 'Already claimed.' })
      } as Response;
    });

    await expect(deleteItem('item-1')).rejects.toMatchObject({ status: 409, code: 'conflict', backendMessage: 'Already claimed.' });
    expect(fetchMock).toHaveBeenCalledWith('/api/v1/auth/csrf', expect.anything());
  });

  it('keeps network failures distinguishable from backend validation errors', async () => {
    vi.spyOn(globalThis, 'fetch').mockRejectedValue(new TypeError('Failed to fetch'));

    await expect(createItem('list-1', { name: 'Task' })).rejects.toMatchObject({
      name: 'ApiClientError',
      status: 0,
      code: 'network_error'
    });
  });
});
