// @vitest-environment happy-dom
import { flushPromises, mount } from '@vue/test-utils';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import App from './App.vue';
import { i18n } from './i18n';
import {
  createItem,
  getCurrentUser,
  getItems,
  getListShares,
  getLists,
  getNotifications,
  updateItem,
  type ItemEntry,
  type ListEntry
} from './api/client';

vi.mock('./api/client', async (importOriginal) => {
  const actual = await importOriginal<typeof import('./api/client')>();
  return {
    ...actual,
    createItem: vi.fn(),
    getCurrentUser: vi.fn(),
    getItems: vi.fn(),
    getListShares: vi.fn(),
    getLists: vi.fn(),
    getNotifications: vi.fn(),
    updateItem: vi.fn()
  };
});

const wishList: ListEntry = {
  id: 'list-1', title: 'Wishlist', description: null, type: 'WISH',
  publicList: false, shareToken: null, publicShareMode: 'WISH_CLAIM',
  targetDate: null, access: 'OWNER', createdAt: '2026-01-01T00:00:00Z'
};

const todoList: ListEntry = {
  ...wishList, id: 'list-2', title: 'Tasks', type: 'TODO', publicShareMode: 'VIEW'
};

const wishItem: ItemEntry = {
  id: 'item-1', listId: 'list-1', name: 'Camera', description: null,
  url: null, imageUrl: 'https://shop.test/old.jpg', price: null, status: 'OPEN',
  dueDate: null, recurrenceRule: null, quantity: null, category: null,
  reservedByGuest: null, lastCompletedAt: null, ownerLabel: null, assistantLabels: null
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

async function mountWishlist(items: ItemEntry[] = []) {
  vi.mocked(getCurrentUser).mockResolvedValue({ id: 'user-1', username: 'owner', email: null, role: 'USER' });
  vi.mocked(getLists).mockResolvedValue([wishList]);
  vi.mocked(getItems).mockResolvedValue(items);
  vi.mocked(getListShares).mockResolvedValue([]);
  vi.mocked(getNotifications).mockResolvedValue([]);
  const wrapper = mount(App, { global: { plugins: [i18n] } });
  await flushPromises();
  return wrapper;
}

async function mountList(list: ListEntry, items: ItemEntry[] = []) {
  vi.mocked(getCurrentUser).mockResolvedValue({ id: 'user-1', username: 'owner', email: null, role: 'USER' });
  vi.mocked(getLists).mockResolvedValue([list]);
  vi.mocked(getItems).mockResolvedValue(items);
  vi.mocked(getListShares).mockResolvedValue([]);
  vi.mocked(getNotifications).mockResolvedValue([]);
  const wrapper = mount(App, { global: { plugins: [i18n] } });
  await flushPromises();
  return wrapper;
}

async function paste(form: { element: Element }, file: File) {
  const event = new Event('paste', { bubbles: true, cancelable: true });
  Object.defineProperty(event, 'clipboardData', { value: { files: [file] } });
  form.element.dispatchEvent(event);
  await new Promise((resolve) => window.setTimeout(resolve, 0));
  await flushPromises();
}

describe('wishlist image paste', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.stubGlobal('FileReader', class {
      result: string | ArrayBuffer | null = null;
      private loadListener: (() => void) | undefined;
      addEventListener(type: string, listener: () => void) {
        if (type === 'load') this.loadListener = listener;
      }
      readAsDataURL(file: File) {
        this.result = `data:${file.type};base64,dGVzdA==`;
        this.loadListener?.();
      }
    });
    window.history.replaceState({}, '', '/');
    i18n.global.locale.value = 'en';
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    document.body.innerHTML = '';
  });

  it('pastes an image into the create form and previews it immediately', async () => {
    const wrapper = await mountWishlist();
    const form = formWithButton(wrapper, 'Add item');

    await paste(form, new File(['png bytes'], 'wish.png', { type: 'image/png' }));

    const imageUrl = (form.get('input[aria-label="Image URL"]').element as HTMLInputElement).value;
    expect(imageUrl).toMatch(/^data:image\/png;base64,/);
    expect(form.get('img.preview').attributes('src')).toBe(imageUrl);
    expect(form.get('input[aria-label="Upload image"]').attributes('accept')).toContain('image/webp');
  });

  it('ignores pasted non-image files without changing the manual image URL', async () => {
    const wrapper = await mountWishlist();
    const form = formWithButton(wrapper, 'Add item');
    await form.get('input[aria-label="Image URL"]').setValue('https://shop.test/manual.jpg');

    await paste(form, new File(['notes'], 'notes.txt', { type: 'text/plain' }));

    expect((form.get('input[aria-label="Image URL"]').element as HTMLInputElement).value).toBe('https://shop.test/manual.jpg');
    expect(form.get('img.preview').attributes('src')).toBe('https://shop.test/manual.jpg');
  });

  it('rejects oversized image files before reading them', async () => {
    const readAsDataURL = vi.fn();
    vi.stubGlobal('FileReader', class {
      addEventListener() {}
      readAsDataURL = readAsDataURL;
    });
    const wrapper = await mountWishlist();
    const form = formWithButton(wrapper, 'Add item');

    await paste(form, new File([new Uint8Array(3_500_001)], 'huge.png', { type: 'image/png' }));

    expect(readAsDataURL).not.toHaveBeenCalled();
    expect(form.find('img.preview').exists()).toBe(false);
    expect(wrapper.get('[role="alert"]').text()).toContain('3.5 MB');
  });

  it('does not attach image paste or preview UI to non-wishlist forms', async () => {
    const readAsDataURL = vi.fn();
    vi.stubGlobal('FileReader', class {
      addEventListener() {}
      readAsDataURL = readAsDataURL;
    });
    const wrapper = await mountList(todoList);
    const createForm = formWithButton(wrapper, 'Add item');

    await paste(createForm, new File(['png bytes'], 'task.png', { type: 'image/png' }));

    expect(readAsDataURL).not.toHaveBeenCalled();
    expect(createForm.find('input[aria-label="Upload image"]').exists()).toBe(false);
    expect(createForm.find('img.preview').exists()).toBe(false);
    expect(createForm.findAll('button').filter((candidate) => candidate.text() === 'Remove image')).toHaveLength(0);
  });

  it('removes and replaces images while editing a wishlist item', async () => {
    const wrapper = await mountWishlist([wishItem]);
    await wrapper.get('button[aria-label="Edit: Camera"]').trigger('click');
    const form = formWithButton(wrapper, 'Save');

    expect(form.get('img.preview').attributes('src')).toBe('https://shop.test/old.jpg');
    await button(wrapper, 'Remove image').trigger('click');
    expect(form.find('img.preview').exists()).toBe(false);

    await paste(form, new File(['webp bytes'], 'replacement.webp', { type: 'image/webp' }));
    expect(form.get('img.preview').attributes('src')).toMatch(/^data:image\/webp;base64,/);
  });

  it('saves a no-URL wishlist item with pasted image data', async () => {
    const wrapper = await mountWishlist();
    const form = formWithButton(wrapper, 'Add item');
    await form.get('input[aria-label="New item name"]').setValue('Handmade mug');
    await paste(form, new File(['jpeg bytes'], 'mug.jpg', { type: 'image/jpeg' }));
    const imageUrl = (form.get('input[aria-label="Image URL"]').element as HTMLInputElement).value;
    vi.mocked(createItem).mockResolvedValue({ ...wishItem, id: 'item-2', name: 'Handmade mug', imageUrl, url: null });

    await form.trigger('submit');
    await flushPromises();

    expect(createItem).toHaveBeenCalledWith('list-1', expect.objectContaining({
      name: 'Handmade mug',
      url: undefined,
      imageUrl: expect.stringMatching(/^data:image\/jpeg;base64,/)
    }));
  });
});
