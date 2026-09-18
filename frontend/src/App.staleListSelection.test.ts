// @vitest-environment happy-dom
import { flushPromises, mount } from '@vue/test-utils';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { nextTick } from 'vue';
import App from './App.vue';
import { i18n } from './i18n';
import { getCurrentUser, getItems, getListShares, getLists, getNotifications, type ItemEntry, type ListShareEntry } from './api/client';

vi.mock('./api/client', async (importOriginal) => {
  const actual = await importOriginal<typeof import('./api/client')>();
  return {
    ...actual,
    getCurrentUser: vi.fn(),
    getItems: vi.fn(),
    getListShares: vi.fn(),
    getLists: vi.fn(),
    getNotifications: vi.fn()
  };
});

const alphaList = {
  id: 'list-alpha', title: 'Alpha list', description: null, type: 'TODO' as const,
  publicList: false, shareToken: null, publicShareMode: 'VIEW' as const,
  targetDate: null, access: 'OWNER' as const, createdAt: '2026-01-01T00:00:00Z'
};
const betaList = {
  id: 'list-beta', title: 'Beta list', description: null, type: 'TODO' as const,
  publicList: false, shareToken: null, publicShareMode: 'VIEW' as const,
  targetDate: null, access: 'OWNER' as const, createdAt: '2026-01-02T00:00:00Z'
};

function item(id: string, listId: string, name: string): ItemEntry {
  return {
    id, listId, name, description: null, url: null, imageUrl: null, price: null, status: 'OPEN' as const,
    dueDate: null, recurrenceRule: null, quantity: null, category: null, reservedByGuest: null,
    lastCompletedAt: null, ownerLabel: null, assistantLabels: null
  };
}

function deferred<T>() {
  let resolve!: (value: T) => void;
  const promise = new Promise<T>((done) => {
    resolve = done;
  });
  return { promise, resolve };
}

function buttonContaining(wrapper: ReturnType<typeof mount>, label: string) {
  return wrapper.findAll('button').find((candidate) => candidate.text().includes(label));
}

describe('list detail request ordering', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    window.history.replaceState({}, '', '/');
    vi.mocked(getCurrentUser).mockResolvedValue({ id: 'user-1', username: 'owner', email: null, role: 'USER' });
    vi.mocked(getLists).mockResolvedValue([alphaList, betaList]);
    vi.mocked(getNotifications).mockResolvedValue([]);
  });

  afterEach(() => {
    document.body.innerHTML = '';
  });

  it('ignores stale item and share responses after the user switches to another list', async () => {
    const alphaItems = deferred<ItemEntry[]>();
    const alphaShares = deferred<ListShareEntry[]>();
    vi.mocked(getItems).mockImplementation((listId: string) => {
      if (listId === 'list-alpha') return alphaItems.promise;
      if (listId === 'list-beta') return Promise.resolve([item('item-beta', 'list-beta', 'Beta current item')]);
      return Promise.resolve([]);
    });
    vi.mocked(getListShares).mockImplementation((listId: string) => {
      if (listId === 'list-alpha') return alphaShares.promise;
      if (listId === 'list-beta') return Promise.resolve([]);
      return Promise.resolve([]);
    });

    const wrapper = mount(App, { global: { plugins: [i18n] } });
    await flushPromises();
    await nextTick();

    expect(getItems).toHaveBeenCalledWith('list-alpha');
    expect(wrapper.text()).toContain('Alpha list');

    await buttonContaining(wrapper, 'Beta list')!.trigger('click');
    await flushPromises();

    expect(getItems).toHaveBeenCalledWith('list-beta');
    expect(wrapper.text()).toContain('Beta list');
    expect(wrapper.text()).toContain('Beta current item');

    alphaItems.resolve([item('item-alpha', 'list-alpha', 'Alpha stale item')]);
    alphaShares.resolve([]);
    await flushPromises();

    expect(wrapper.text()).toContain('Beta list');
    expect(wrapper.text()).toContain('Beta current item');
    expect(wrapper.text()).not.toContain('Alpha stale item');
  });
});
