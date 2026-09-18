// @vitest-environment happy-dom
import { flushPromises, mount } from '@vue/test-utils';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import App from './App.vue';
import { i18n } from './i18n';
import { ApiClientError, getAuthSettings, getCurrentUser, getItems, getListShares, getLists, getNotifications, login, requestMagicLink, updateItem } from './api/client';

vi.mock('./api/client', async (importOriginal) => {
  const actual = await importOriginal<typeof import('./api/client')>();
  return {
    ...actual,
    getAuthSettings: vi.fn(),
    getCurrentUser: vi.fn(),
    getItems: vi.fn(),
    getListShares: vi.fn(),
    getLists: vi.fn(),
    getNotifications: vi.fn(),
    login: vi.fn(),
    requestMagicLink: vi.fn(),
    updateItem: vi.fn()
  };
});

const todoList = {
  id: 'list-1', title: 'Tasks', description: null, type: 'TODO' as const,
  publicList: false, shareToken: null, publicShareMode: 'VIEW' as const,
  targetDate: null, access: 'OWNER' as const, createdAt: '2026-01-01T00:00:00Z'
};
const todoItem = {
  id: 'item-1', listId: 'list-1', name: 'Call optician', description: null,
  url: null, imageUrl: null, price: null, status: 'OPEN' as const,
  dueDate: null, recurrenceRule: null, quantity: null, category: null, reservedByGuest: null,
  lastCompletedAt: null, ownerLabel: null, assistantLabels: null
};

function button(wrapper: ReturnType<typeof mount>, label: string) {
  return wrapper.findAll('button').find((candidate) => candidate.text() === label);
}

describe('rendered accessibility and localized errors', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    window.history.replaceState({}, '', '/');
    i18n.global.locale.value = 'en';
    vi.mocked(getAuthSettings).mockResolvedValue({ registrationAvailable: false });
    vi.mocked(getNotifications).mockResolvedValue([]);
  });

  afterEach(() => {
    document.body.innerHTML = '';
  });

  it('localizes structured backend errors and announces them as alerts', async () => {
    vi.mocked(getCurrentUser).mockResolvedValue(null);
    vi.mocked(login).mockRejectedValue(new ApiClientError(400, 'validation_failed', 'Name is required'));
    const wrapper = mount(App, { global: { plugins: [i18n] } });
    await flushPromises();

    await wrapper.get('input[aria-label="Username"]').setValue('uwe');
    await wrapper.get('input[aria-label="Password"]').setValue('wrong-password');
    await wrapper.get('form.panel').trigger('submit');
    await flushPromises();

    const alert = wrapper.get('[role="alert"]');
    expect(alert.text()).toBe('Please check the highlighted fields.');
    expect(alert.attributes('aria-live')).toBe('assertive');
  });

  it('uses status live regions for neutral success messages', async () => {
    vi.mocked(getCurrentUser).mockResolvedValue(null);
    vi.mocked(requestMagicLink).mockResolvedValue();
    const wrapper = mount(App, { global: { plugins: [i18n] } });
    await flushPromises();

    await wrapper.get('input[aria-label="Email"]').setValue('uwe@example.test');
    await button(wrapper, 'Email me a magic link')!.trigger('click');
    await flushPromises();

    const status = wrapper.get('[role="status"]');
    expect(status.text()).toContain('If that email exists');
    expect(status.attributes('aria-live')).toBe('polite');
  });

  it('keeps document language synchronized with the active locale', async () => {
    vi.mocked(getCurrentUser).mockResolvedValue(null);
    mount(App, { global: { plugins: [i18n] } });
    await flushPromises();
    expect(document.documentElement.lang).toBe('en');

    i18n.global.locale.value = 'de';
    await flushPromises();
    expect(document.documentElement.lang).toBe('de');
  });

  it('renders persistent labels and contextual repeated action names for item workflows', async () => {
    vi.mocked(getCurrentUser).mockResolvedValue({ id: 'user-1', username: 'owner', email: null, role: 'USER' });
    vi.mocked(getLists).mockResolvedValue([todoList]);
    vi.mocked(getItems).mockResolvedValue([todoItem]);
    vi.mocked(getListShares).mockResolvedValue([]);
    vi.mocked(updateItem).mockResolvedValue({ ...todoItem, status: 'DONE' });
    const wrapper = mount(App, { global: { plugins: [i18n] } });
    await flushPromises();

    expect(wrapper.get('input[aria-label="New item name"]')).toBeTruthy();
    expect(wrapper.get('input[aria-label="Due date"]')).toBeTruthy();
    expect(wrapper.get('input[aria-label="Owner / responsible"]')).toBeTruthy();
    expect(wrapper.get('button[aria-label="Done: Call optician"]')).toBeTruthy();
    expect(wrapper.get('button[aria-label="Edit: Call optician"]')).toBeTruthy();
    expect(wrapper.get('button[aria-label="Delete: Call optician"]')).toBeTruthy();

    await wrapper.get('button[aria-label="Done: Call optician"]').trigger('click');
    await flushPromises();
    expect(updateItem).toHaveBeenCalledWith('item-1', expect.objectContaining({ status: 'DONE' }));
  });
});
