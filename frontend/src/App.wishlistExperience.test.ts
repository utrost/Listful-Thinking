// @vitest-environment happy-dom
import { flushPromises, mount } from '@vue/test-utils';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import App from './App.vue';
import { i18n } from './i18n';
import { getCurrentUser, getItems, getListShares, getLists, getNotifications, getPublicShare, updateItem } from './api/client';

vi.mock('./api/client', async (importOriginal) => {
  const actual = await importOriginal<typeof import('./api/client')>();
  return {
    ...actual,
    getCurrentUser: vi.fn(),
    getItems: vi.fn(),
    getListShares: vi.fn(),
    getLists: vi.fn(),
    getNotifications: vi.fn(),
    getPublicShare: vi.fn(),
    updateItem: vi.fn()
  };
});

const wishList = {
  id: 'list-1', title: 'Birthday', description: null, type: 'WISH' as const,
  publicList: true, shareToken: 'public-token', publicShareMode: 'WISH_CLAIM' as const,
  targetDate: null, access: 'OWNER' as const, createdAt: '2026-01-01T00:00:00Z'
};
const wishItem = {
  id: 'item-1', listId: 'list-1', name: 'Camera strap', description: 'Leather',
  url: 'https://shop.example.test/strap', imageUrl: null, price: 29.9, status: 'OPEN' as const,
  dueDate: null, recurrenceRule: null, quantity: null, category: null, reservedByGuest: null,
  lastCompletedAt: null, ownerLabel: null, assistantLabels: null
};

function button(wrapper: ReturnType<typeof mount>, label: string) {
  return wrapper.findAll('button').find((candidate) => candidate.text() === label);
}

describe('wishlist links and status controls', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    window.history.replaceState({}, '', '/');
    vi.mocked(getCurrentUser).mockResolvedValue({ id: 'user-1', username: 'owner', email: null, role: 'USER' });
    vi.mocked(getLists).mockResolvedValue([wishList]);
    vi.mocked(getItems).mockResolvedValue([wishItem]);
    vi.mocked(getListShares).mockResolvedValue([]);
    vi.mocked(getNotifications).mockResolvedValue([]);
  });

  afterEach(() => {
    document.body.innerHTML = '';
  });

  it('renders a safe product link and lets the owner mark and reopen a purchased wish', async () => {
    vi.mocked(updateItem)
      .mockResolvedValueOnce({ ...wishItem, status: 'PURCHASED' })
      .mockResolvedValueOnce({ ...wishItem, status: 'OPEN' });
    const wrapper = mount(App, { global: { plugins: [i18n] } });
    await flushPromises();

    const link = wrapper.get('a[href="https://shop.example.test/strap"]');
    expect(link.text()).toBe('View product');
    expect(link.attributes('target')).toBe('_blank');
    expect(link.attributes('rel')).toBe('noopener noreferrer');

    await button(wrapper, 'Mark purchased')!.trigger('click');
    await flushPromises();
    expect(updateItem).toHaveBeenLastCalledWith('item-1', expect.objectContaining({ status: 'PURCHASED' }));
    expect(wrapper.text()).toContain('PURCHASED');
    expect(button(wrapper, 'Reopen wish')).toBeTruthy();

    await button(wrapper, 'Reopen wish')!.trigger('click');
    await flushPromises();
    expect(updateItem).toHaveBeenLastCalledWith('item-1', expect.objectContaining({ status: 'OPEN' }));
    expect(wrapper.text()).toContain('OPEN');
  });

  it.each([
    'javascript:alert(1)',
    'data:text/html,<script>alert(1)</script>',
    'https://[malformed'
  ])('does not render a product link for unsafe wishlist URL %s', async (url) => {
    vi.mocked(getItems).mockResolvedValue([{ ...wishItem, url }]);
    const wrapper = mount(App, { global: { plugins: [i18n] } });
    await flushPromises();

    expect(wrapper.findAll('a').some((link) => link.text() === 'View product')).toBe(false);
  });

  it('does not offer wishlist status controls to contributors', async () => {
    vi.mocked(getLists).mockResolvedValue([{ ...wishList, access: 'CONTRIBUTE' }]);
    const wrapper = mount(App, { global: { plugins: [i18n] } });
    await flushPromises();

    expect(button(wrapper, 'Mark purchased')).toBeUndefined();
    expect(button(wrapper, 'Reopen wish')).toBeUndefined();
  });

  it('renders the same safe product link in the public wishlist view', async () => {
    window.history.replaceState({}, '', '/s/public-token');
    vi.mocked(getPublicShare).mockResolvedValue({
      title: 'Birthday', description: null, type: 'WISH', targetDate: null, mode: 'WISH_CLAIM', items: [wishItem]
    });
    const wrapper = mount(App, { global: { plugins: [i18n] } });
    await flushPromises();

    const link = wrapper.get('a[href="https://shop.example.test/strap"]');
    expect(link.text()).toBe('View product');
    expect(link.attributes('target')).toBe('_blank');
    expect(link.attributes('rel')).toBe('noopener noreferrer');
  });
});
