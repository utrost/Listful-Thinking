// @vitest-environment happy-dom
import { flushPromises, mount } from '@vue/test-utils';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import WebCapture from './WebCapture.vue';
import { i18n } from '../i18n';
import { ApiClientError, createItem, createList, extractPage, getItems, type ListEntry, type PageDraft } from '../api/client';

vi.mock('../api/client', async (importOriginal) => ({
  ...await importOriginal<typeof import('../api/client')>(),
  createItem: vi.fn(), createList: vi.fn(), extractPage: vi.fn(), getItems: vi.fn()
}));

const wishList: ListEntry = { id: 'wish', title: 'Birthday', description: null, type: 'WISH',
  publicList: false, shareToken: null, publicShareMode: 'WISH_CLAIM', publicShareExpiresAt: null,
  targetDate: null, access: 'OWNER', createdAt: '2026-01-01T00:00:00Z' };
const groceryList: ListEntry = { ...wishList, id: 'grocery', title: 'Groceries', type: 'GROCERY' };
const product: PageDraft = { kind: 'PRODUCT', sourceUrl: 'https://shop.test/mug?utm_source=email', extractedAt: '2026-10-17T08:00:00Z',
  title: 'Blue mug', description: 'Ceramic', imageUrl: null, price: 12, priceCurrency: 'EUR', seller: 'Example Shop',
  availability: 'InStock', ingredients: [], steps: [], eventStart: null, eventLocation: null };

async function openDraft(wrapper: ReturnType<typeof mount>, draft: PageDraft) {
  vi.mocked(extractPage).mockResolvedValue(draft);
  await wrapper.get('input[type="url"]').setValue(draft.sourceUrl);
  await wrapper.get('.capture-form').trigger('submit');
  await flushPromises();
}

describe('reviewed web capture', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(getItems).mockResolvedValue([]);
    vi.mocked(createItem).mockResolvedValue({} as never);
  });

  it('warns about tracked-link duplicates and saves the reviewed exact wish', async () => {
    vi.mocked(getItems).mockResolvedValue([{ url: 'https://shop.test/mug', name: 'Existing mug' } as never]);
    const wrapper = mount(WebCapture, { props: { lists: [wishList] }, global: { plugins: [i18n] } });
    await openDraft(wrapper, product);
    await wrapper.findAll('select')[1].setValue('wish');
    await flushPromises();
    expect(wrapper.text()).toContain('Existing mug');
    await wrapper.get('input[maxlength="255"]').setValue('Blue mug, 350 ml');
    await wrapper.get('.capture-review').trigger('submit');
    await flushPromises();
    expect(createItem).toHaveBeenCalledWith('wish', expect.objectContaining({
      name: 'Blue mug, 350 ml', url: product.sourceUrl, sourceUrl: product.sourceUrl,
      price: 12, priceCurrency: 'EUR', seller: 'Example Shop'
    }));
    expect(wrapper.emitted('saved')?.[0]).toEqual(['wish']);
  });

  it('offers recipe ingredients as editable grocery lines and saves only retained lines', async () => {
    const wrapper = mount(WebCapture, { props: { lists: [groceryList] }, global: { plugins: [i18n] } });
    await openDraft(wrapper, { ...product, kind: 'RECIPE', title: 'Pancakes', ingredients: ['2 eggs', '200 g flour'], price: null });
    await wrapper.findAll('select')[1].setValue('grocery');
    await wrapper.get('.capture-review textarea').setValue('2 eggs\n');
    await wrapper.get('.capture-review').trigger('submit');
    await flushPromises();
    expect(createItem).toHaveBeenCalledTimes(1);
    expect(createItem).toHaveBeenCalledWith('grocery', { name: '2 eggs', sourceUrl: product.sourceUrl });
  });

  it('creates an event only after its date is reviewed', async () => {
    vi.mocked(createList).mockResolvedValue({ ...wishList, id: 'event', type: 'EVENT' });
    const wrapper = mount(WebCapture, { props: { lists: [] }, global: { plugins: [i18n] } });
    await openDraft(wrapper, { ...product, kind: 'EVENT', title: 'Dinner', eventStart: '2027-05-12T18:00:00+02:00', eventLocation: 'Community hall' });
    await wrapper.findAll('.capture-review textarea').at(-1)!.setValue('Book venue\nInvite guests');
    await wrapper.get('.capture-review').trigger('submit');
    await flushPromises();
    expect(createList).toHaveBeenCalledWith(expect.objectContaining({ title: 'Dinner', type: 'EVENT',
      targetDate: '2027-05-12T16:00:00.000Z', eventLocation: 'Community hall', sourceUrl: product.sourceUrl }));
    expect(createItem).toHaveBeenCalledTimes(2);
    expect(wrapper.emitted('saved')?.[0]).toEqual(['event']);
  });

  it('retries remaining event tasks without creating a duplicate event', async () => {
    vi.mocked(createList).mockResolvedValue({ ...wishList, id: 'event', type: 'EVENT' });
    vi.mocked(createItem).mockResolvedValueOnce({} as never).mockRejectedValueOnce(new Error('temporary'));
    const wrapper = mount(WebCapture, { props: { lists: [] }, global: { plugins: [i18n] } });
    await openDraft(wrapper, { ...product, kind: 'EVENT', title: 'Dinner', eventStart: '2027-05-12T18:00:00+02:00' });
    await wrapper.findAll('.capture-review textarea').at(-1)!.setValue('Book venue\nInvite guests');
    await wrapper.get('.capture-review').trigger('submit');
    await flushPromises();
    expect((wrapper.findAll('.capture-review textarea').at(-1)!.element as HTMLTextAreaElement).value).toBe('Invite guests');
    await wrapper.get('.capture-review').trigger('submit');
    await flushPromises();
    expect(createList).toHaveBeenCalledTimes(1);
    expect(createItem).toHaveBeenCalledTimes(3);
  });

  it('offers a manual draft for unreadable pages but surfaces authentication failures', async () => {
    const wrapper = mount(WebCapture, { props: { lists: [] }, global: { plugins: [i18n] } });
    vi.mocked(extractPage).mockRejectedValueOnce(new Error('blocked'));
    await wrapper.get('input[type="url"]').setValue(product.sourceUrl);
    await wrapper.get('.capture-form').trigger('submit');
    await flushPromises();
    expect(wrapper.text()).toContain('enter and save details manually');
    await wrapper.get('.capture-source-actions button').trigger('click');
    vi.mocked(extractPage).mockRejectedValueOnce(new ApiClientError(401, 'unauthorized', 'Sign in again'));
    await wrapper.get('.capture-form').trigger('submit');
    await flushPromises();
    expect(wrapper.get('[role="alert"]').text()).toContain('Sign in again');
  });
});
