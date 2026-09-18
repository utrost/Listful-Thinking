// @vitest-environment happy-dom
import { flushPromises, mount } from '@vue/test-utils';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import App from './App.vue';
import { i18n } from './i18n';
import {
  cloneList,
  createAdminUser,
  createItem,
  getAdminLists,
  getAdminSettings,
  getAdminUsers,
  getCurrentUser,
  getItems,
  getListShares,
  getLists,
  getNotifications,
  shareListWithUser,
  updateAdminSettings,
  updateAdminUser,
  updateItem,
  type AdminUserEntry,
  type ItemEntry,
  type ListEntry
} from './api/client';

vi.mock('./api/client', async (importOriginal) => {
  const actual = await importOriginal<typeof import('./api/client')>();
  return {
    ...actual,
    cloneList: vi.fn(),
    createAdminUser: vi.fn(),
    createItem: vi.fn(),
    getAdminLists: vi.fn(),
    getAdminSettings: vi.fn(),
    getAdminUsers: vi.fn(),
    getCurrentUser: vi.fn(),
    getItems: vi.fn(),
    getListShares: vi.fn(),
    getLists: vi.fn(),
    getNotifications: vi.fn(),
    shareListWithUser: vi.fn(),
    updateAdminSettings: vi.fn(),
    updateAdminUser: vi.fn(),
    updateItem: vi.fn()
  };
});

const baseList: ListEntry = {
  id: 'list-1', title: 'Saturday tasks', description: null, type: 'TODO',
  publicList: false, shareToken: null, publicShareMode: 'VIEW',
  targetDate: null, access: 'OWNER', createdAt: '2026-01-01T00:00:00Z'
};
const baseItem: ItemEntry = {
  id: 'item-1', listId: 'list-1', name: 'Water plants', description: null,
  url: null, imageUrl: null, price: null, status: 'OPEN', dueDate: null,
  recurrenceRule: null, quantity: null, category: null, reservedByGuest: null,
  lastCompletedAt: null, ownerLabel: null, assistantLabels: null
};
const adminUser: AdminUserEntry = {
  id: 'admin-1', username: 'admin', email: 'admin@example.test', role: 'ADMIN', active: true, createdAt: '2026-01-01T00:00:00Z'
};

function button(wrapper: ReturnType<typeof mount>, label: string) {
  const match = wrapper.findAll('button').find((candidate) => candidate.text() === label);
  expect(match, `button ${label}`).toBeTruthy();
  return match!;
}

function formWithButton(wrapper: ReturnType<typeof mount>, label: string) {
  const match = wrapper.findAll('form').find((form) => form.findAll('button').some((candidate) => candidate.text() === label));
  expect(match, `form with button ${label}`).toBeTruthy();
  return match!;
}

async function mountSignedIn(list: ListEntry = baseList, item: ItemEntry = baseItem, role: 'USER' | 'ADMIN' = 'USER') {
  vi.mocked(getCurrentUser).mockResolvedValue({ id: 'user-1', username: role.toLowerCase(), email: null, role });
  vi.mocked(getLists).mockResolvedValue([list]);
  vi.mocked(getItems).mockResolvedValue([item]);
  vi.mocked(getListShares).mockResolvedValue([]);
  vi.mocked(getNotifications).mockResolvedValue([]);
  vi.mocked(getAdminSettings).mockResolvedValue({ registrationEnabled: true });
  vi.mocked(getAdminUsers).mockResolvedValue([adminUser]);
  vi.mocked(getAdminLists).mockResolvedValue([{ ...list, ownerId: 'user-1', ownerUsername: 'owner', ownerEmail: null }]);
  const wrapper = mount(App, { global: { plugins: [i18n] } });
  await flushPromises();
  return wrapper;
}

describe('rendered application workflows', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    window.history.replaceState({}, '', '/');
    i18n.global.locale.value = 'en';
  });

  afterEach(() => {
    document.body.innerHTML = '';
  });

  it('edits an item and toggles done through rendered controls', async () => {
    const edited = { ...baseItem, name: 'Water balcony plants' };
    vi.mocked(updateItem)
      .mockResolvedValueOnce(edited)
      .mockResolvedValueOnce({ ...edited, status: 'DONE' });
    const wrapper = await mountSignedIn();

    await wrapper.get('button[aria-label="Edit: Water plants"]').trigger('click');
    await wrapper.get('form.edit-item-form input[aria-label="New item name"]').setValue('Water balcony plants');
    await formWithButton(wrapper, 'Save').trigger('submit');
    await flushPromises();

    expect(updateItem).toHaveBeenCalledWith('item-1', expect.objectContaining({ name: 'Water balcony plants', status: 'OPEN' }));
    expect(wrapper.text()).toContain('Water balcony plants');

    await wrapper.get('button[aria-label="Done: Water balcony plants"]').trigger('click');
    await flushPromises();
    expect(updateItem).toHaveBeenLastCalledWith('item-1', expect.objectContaining({ status: 'DONE' }));
  });

  it('duplicates an owner list and selects the cloned result', async () => {
    const cloned = { ...baseList, id: 'list-copy', title: 'Saturday tasks copy' };
    vi.mocked(cloneList).mockResolvedValue(cloned);
    vi.mocked(getItems).mockResolvedValueOnce([baseItem]).mockResolvedValueOnce([]);
    const wrapper = await mountSignedIn();

    await button(wrapper, 'Duplicate list').trigger('click');
    await flushPromises();

    expect(cloneList).toHaveBeenCalledWith('list-1', { title: 'Saturday tasks copy' });
    expect(wrapper.text()).toContain('Saturday tasks copy');
  });

  it('shares a list with contributor permission from rendered form fields', async () => {
    vi.mocked(shareListWithUser).mockResolvedValue({
      listId: 'list-1', userId: 'user-2', username: 'martha', permission: 'CONTRIBUTE', createdAt: '2026-01-02T00:00:00Z'
    });
    const wrapper = await mountSignedIn();

    await wrapper.get('input[aria-label="Username to share with"]').setValue('martha');
    await wrapper.get('select[aria-label="Share permission"]').setValue('CONTRIBUTE');
    await formWithButton(wrapper, 'Share read-only').trigger('submit');
    await flushPromises();

    expect(shareListWithUser).toHaveBeenCalledWith('list-1', { username: 'martha', permission: 'CONTRIBUTE' });
    expect(wrapper.text()).toContain('martha · CONTRIBUTE');
  });

  it('keeps the only active admin toggle disabled and submits admin actions from the rendered panel', async () => {
    vi.mocked(createAdminUser).mockResolvedValue({ ...adminUser, id: 'user-2', username: 'helper', role: 'USER' });
    vi.mocked(updateAdminSettings).mockResolvedValue({ registrationEnabled: false });
    const wrapper = await mountSignedIn(baseList, baseItem, 'ADMIN');

    expect(button(wrapper, 'Deactivate').attributes('disabled')).toBeDefined();

    const adminForm = formWithButton(wrapper, 'Create user');
    await adminForm.get('input[aria-label="Username"]').setValue('helper');
    await adminForm.get('input[aria-label="Password"]').setValue('correct horse');
    await adminForm.trigger('submit');
    await flushPromises();
    expect(createAdminUser).toHaveBeenCalledWith(expect.objectContaining({ username: 'helper', role: 'USER' }));

    await wrapper.get('label.toggle-row input[type="checkbox"]').setValue(false);
    await flushPromises();
    expect(updateAdminSettings).toHaveBeenCalledWith({ registrationEnabled: false });
    expect(updateAdminUser).not.toHaveBeenCalled();
  });

  it('creates a responsible item only through visible responsibility fields', async () => {
    vi.mocked(createItem).mockResolvedValue({ ...baseItem, id: 'item-2', name: 'Book venue', ownerLabel: 'Uwe', assistantLabels: 'Alice' });
    const wrapper = await mountSignedIn({ ...baseList, type: 'EVENT', title: 'Birthday party' });

    const createForm = formWithButton(wrapper, 'Add item');
    await createForm.get('input[aria-label="New item name"]').setValue('Book venue');
    await createForm.get('input[aria-label="Owner / responsible"]').setValue('Uwe');
    await createForm.get('input[aria-label="Assistants / helpers"]').setValue('Alice');
    await createForm.trigger('submit');
    await flushPromises();

    expect(createItem).toHaveBeenCalledWith('list-1', expect.objectContaining({
      name: 'Book venue', ownerLabel: 'Uwe', assistantLabels: 'Alice'
    }));
    expect(wrapper.text()).toContain('Book venue');
  });
});
