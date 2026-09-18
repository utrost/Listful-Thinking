// @vitest-environment happy-dom
import { flushPromises, mount } from '@vue/test-utils';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import App from './App.vue';
import { i18n } from './i18n';
import {
  clearCompletedItems,
  deleteList,
  getAuthSettings,
  getCurrentUser,
  getItems,
  getListShares,
  getLists,
  getNotifications,
  postponeChoreItem,
  scrapeUrl,
  skipChoreItem,
  updateList,
  type ItemEntry,
  type ListEntry
} from './api/client';

vi.mock('./api/client', async (importOriginal) => {
  const actual = await importOriginal<typeof import('./api/client')>();
  return {
    ...actual,
    clearCompletedItems: vi.fn(),
    deleteList: vi.fn(),
    getAuthSettings: vi.fn(),
    getCurrentUser: vi.fn(),
    getItems: vi.fn(),
    getListShares: vi.fn(),
    getLists: vi.fn(),
    getNotifications: vi.fn(),
    postponeChoreItem: vi.fn(),
    scrapeUrl: vi.fn(),
    skipChoreItem: vi.fn(),
    updateList: vi.fn()
  };
});

const baseList: ListEntry = {
  id: 'list-1', title: 'House list', description: 'Before edit', type: 'TODO',
  publicList: false, shareToken: null, publicShareMode: 'VIEW', targetDate: null,
  access: 'OWNER', createdAt: '2026-01-01T00:00:00Z'
};

function item(overrides: Partial<ItemEntry> = {}): ItemEntry {
  return {
    id: 'item-1', listId: 'list-1', name: 'Milk', description: null, url: null,
    imageUrl: null, price: null, status: 'OPEN', dueDate: null, recurrenceRule: null,
    quantity: null, category: null, reservedByGuest: null, lastCompletedAt: null,
    ownerLabel: null, assistantLabels: null,
    ...overrides
  };
}

function button(wrapper: ReturnType<typeof mount>, label: string) {
  const match = wrapper.findAll('button').find((candidate) => candidate.text() === label);
  expect(match, `button ${label}`).toBeTruthy();
  return match!;
}

function buttonContaining(wrapper: ReturnType<typeof mount>, label: string) {
  const match = wrapper.findAll('button').find((candidate) => candidate.text().includes(label));
  expect(match, `button containing ${label}`).toBeTruthy();
  return match!;
}

function formWithButton(wrapper: ReturnType<typeof mount>, label: string) {
  const match = wrapper.findAll('form').find((form) => form.findAll('button').some((candidate) => candidate.text() === label));
  expect(match, `form with button ${label}`).toBeTruthy();
  return match!;
}

async function mountSignedOut(registrationAvailable: boolean) {
  vi.mocked(getCurrentUser).mockResolvedValue(null);
  vi.mocked(getAuthSettings).mockResolvedValue({ registrationAvailable });
  const wrapper = mount(App, { global: { plugins: [i18n] } });
  await flushPromises();
  return wrapper;
}

async function mountSignedIn(list: ListEntry, items: ItemEntry[]) {
  vi.mocked(getCurrentUser).mockResolvedValue({ id: 'user-1', username: 'owner', email: null, role: 'USER' });
  vi.mocked(getLists).mockResolvedValue([list]);
  vi.mocked(getItems).mockResolvedValue(items);
  vi.mocked(getListShares).mockResolvedValue([]);
  vi.mocked(getNotifications).mockResolvedValue([]);
  const wrapper = mount(App, { global: { plugins: [i18n] } });
  await flushPromises();
  return wrapper;
}

describe('additional rendered application workflows', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    window.history.replaceState({}, '', '/');
    i18n.global.locale.value = 'en';
  });

  afterEach(() => {
    document.body.innerHTML = '';
  });

  it('uses public auth settings to show or hide self-registration', async () => {
    const hidden = await mountSignedOut(false);
    expect(hidden.text()).toContain('Self-registration is currently disabled');
    expect(hidden.findAll('form').filter((form) => form.text().includes('Register'))).toHaveLength(0);

    hidden.unmount();
    const visible = await mountSignedOut(true);
    expect(visible.findAll('form').some((form) => form.text().includes('Register'))).toBe(true);
    expect(getAuthSettings).toHaveBeenCalledTimes(2);
  });

  it('edits list details and requires confirmation before deleting a list', async () => {
    vi.mocked(updateList).mockResolvedValue({ ...baseList, title: 'House list updated', type: 'EVENT', publicShareMode: 'SIGNUP' });
    vi.mocked(deleteList).mockResolvedValue();
    const wrapper = await mountSignedIn(baseList, []);

    await button(wrapper, 'Edit list').trigger('click');
    const editForm = formWithButton(wrapper, 'Save list');
    await editForm.get('input[aria-label="New list title"]').setValue('House list updated');
    await editForm.get('select[aria-label="List type"]').setValue('EVENT');
    await editForm.get('input[aria-label="Target date"]').setValue('2027-01-01T12:30');
    await editForm.trigger('submit');
    await flushPromises();

    expect(updateList).toHaveBeenCalledWith('list-1', expect.objectContaining({ title: 'House list updated', type: 'EVENT' }));
    expect(wrapper.text()).toContain('House list updated');

    await button(wrapper, 'Delete list').trigger('click');
    expect(button(wrapper, 'Confirm delete')).toBeTruthy();
    await button(wrapper, 'Keep list').trigger('click');
    expect(wrapper.text()).not.toContain('Confirm delete');

    await button(wrapper, 'Delete list').trigger('click');
    await button(wrapper, 'Confirm delete').trigger('click');
    await flushPromises();
    expect(deleteList).toHaveBeenCalledWith('list-1');
  });

  it('previews wishlist URLs and keeps scraped metadata editable before creation', async () => {
    vi.mocked(scrapeUrl).mockResolvedValue({ title: 'Pocket notebook', description: 'A6 dotted', imageUrl: 'https://shop.test/notebook.jpg', price: 12.5 });
    const wrapper = await mountSignedIn({ ...baseList, type: 'WISH', title: 'Wishlist' }, []);

    const form = formWithButton(wrapper, 'Add item');
    await form.get('input[aria-label="URL"]').setValue('https://shop.test/notebook');
    await form.get('input[aria-label="URL"]').trigger('change');
    await flushPromises();

    expect(scrapeUrl).toHaveBeenCalledWith({ url: 'https://shop.test/notebook' });
    expect((form.get('input[aria-label="New item name"]').element as HTMLInputElement).value).toBe('Pocket notebook');
    expect((form.get('textarea[aria-label="Description"]').element as HTMLTextAreaElement).value).toBe('A6 dotted');
    expect((form.get('input[aria-label="Image URL"]').element as HTMLInputElement).value).toBe('https://shop.test/notebook.jpg');
    expect((form.get('input[aria-label="Price"]').element as HTMLInputElement).value).toBe('12.5');
    expect(wrapper.get('img.preview').attributes('src')).toBe('https://shop.test/notebook.jpg');
  });

  it('filters review controls against rendered items and resets them when switching lists', async () => {
    const otherList = { ...baseList, id: 'list-2', title: 'Second list' };
    vi.mocked(getLists).mockResolvedValue([baseList, otherList]);
    vi.mocked(getItems).mockImplementation((listId: string) => Promise.resolve(listId === 'list-1'
      ? [item({ id: 'milk', name: 'Milk' }), item({ id: 'bread', name: 'Bread' })]
      : [item({ id: 'hammer', listId: 'list-2', name: 'Hammer' })]));
    vi.mocked(getCurrentUser).mockResolvedValue({ id: 'user-1', username: 'owner', email: null, role: 'USER' });
    vi.mocked(getListShares).mockResolvedValue([]);
    vi.mocked(getNotifications).mockResolvedValue([]);
    const wrapper = mount(App, { global: { plugins: [i18n] } });
    await flushPromises();

    await wrapper.get('input[type="search"]').setValue('milk');
    expect(wrapper.text()).toContain('Milk');
    expect(wrapper.text()).not.toContain('Bread');

    await buttonContaining(wrapper, 'Second list').trigger('click');
    await flushPromises();
    expect((wrapper.get('input[type="search"]').element as HTMLInputElement).value).toBe('');
    expect(wrapper.text()).toContain('Hammer');
  });

  it('renders grocery shop controls, hides completed groceries, and clears them through the API', async () => {
    vi.mocked(clearCompletedItems).mockResolvedValue();
    const wrapper = await mountSignedIn({ ...baseList, type: 'GROCERY', title: 'Groceries' }, [
      item({ id: 'milk', name: 'Milk', category: 'Dairy', status: 'OPEN' }),
      item({ id: 'done', name: 'Old bread', category: 'Bakery', status: 'DONE' })
    ]);

    expect(wrapper.text()).toContain('Dairy');
    expect(wrapper.text()).toContain('Bakery');
    await wrapper.get('label.toggle-row input[type="checkbox"]').setValue(true);
    await flushPromises();
    expect(wrapper.text()).not.toContain('Old bread');

    await button(wrapper, 'Clear completed').trigger('click');
    await flushPromises();
    expect(clearCompletedItems).toHaveBeenCalledWith('list-1');
  });

  it('offers recurrence choices and calls skip/postpone chore APIs from rendered controls', async () => {
    const chore = item({ name: 'Clean kitchen', recurrenceRule: 'FREQ=WEEKLY', dueDate: '2027-01-01T10:00:00Z' });
    vi.mocked(skipChoreItem).mockResolvedValue({ ...chore, dueDate: '2027-01-08T10:00:00Z' });
    vi.mocked(postponeChoreItem).mockResolvedValue({ ...chore, dueDate: '2027-01-02T10:00:00Z' });
    const wrapper = await mountSignedIn({ ...baseList, type: 'CHORE', title: 'Chores' }, [chore]);

    const recurrence = wrapper.get('select[aria-label="Recurrence"]');
    expect(recurrence.text()).toContain('Daily');
    expect(recurrence.text()).toContain('Annually');

    await wrapper.get('button[aria-label="Skip: Clean kitchen"]').trigger('click');
    await flushPromises();
    expect(skipChoreItem).toHaveBeenCalledWith('item-1');

    await wrapper.get('button[aria-label="Postpone 1 day: Clean kitchen"]').trigger('click');
    await flushPromises();
    expect(postponeChoreItem).toHaveBeenCalledWith('item-1', { days: 1 });
  });
});
