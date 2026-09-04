import { describe, expect, it } from 'vitest';
import { defaultItemReviewState, reviewDisplayedItems, reviewItems, type ItemReviewState } from './itemReview';
import type { ItemEntry, ListEntry } from './api/client';

const baseItem = (overrides: Partial<ItemEntry>): ItemEntry => ({
  id: overrides.id ?? 'item',
  listId: 'list',
  name: overrides.name ?? 'Item',
  description: overrides.description ?? null,
  url: overrides.url ?? null,
  imageUrl: null,
  price: overrides.price ?? null,
  status: overrides.status ?? 'OPEN',
  dueDate: overrides.dueDate ?? null,
  recurrenceRule: null,
  quantity: overrides.quantity ?? null,
  category: overrides.category ?? null,
  reservedByGuest: null,
  lastCompletedAt: null
});

const state = (overrides: Partial<ItemReviewState> = {}): ItemReviewState => ({
  query: '',
  statusFilter: 'ALL',
  sortBy: 'created',
  now: new Date('2027-01-10T00:00:00Z'),
  ...overrides
});

const list = (type: ListEntry['type']): ListEntry => ({
  id: `list-${type}`,
  title: type,
  description: null,
  type,
  publicList: false,
  shareToken: null,
  publicShareMode: type === 'WISH' ? 'WISH_CLAIM' : 'VIEW',
  targetDate: null,
  access: 'OWNER',
  createdAt: '2027-01-01T00:00:00Z'
});

describe('item review helpers', () => {
  it('searches item name, description, category, and url case-insensitively', () => {
    const items = [
      baseItem({ id: '1', name: 'Oat milk', category: 'Dairy' }),
      baseItem({ id: '2', name: 'Tripod', description: 'Carbon travel tripod' }),
      baseItem({ id: '3', name: 'Book', url: 'https://shop.test/darkroom' })
    ];

    expect(reviewItems(items, state({ query: 'dairy' })).map((item) => item.id)).toEqual(['1']);
    expect(reviewItems(items, state({ query: 'TRAVEL' })).map((item) => item.id)).toEqual(['2']);
    expect(reviewItems(items, state({ query: 'darkroom' })).map((item) => item.id)).toEqual(['3']);
  });

  it('filters open, completed, claimed, purchased, overdue, and upcoming views', () => {
    const items = [
      baseItem({ id: 'open', status: 'OPEN' }),
      baseItem({ id: 'done', status: 'DONE' }),
      baseItem({ id: 'claimed', status: 'CLAIMED' }),
      baseItem({ id: 'purchased', status: 'PURCHASED' }),
      baseItem({ id: 'overdue', status: 'OPEN', dueDate: '2027-01-01T00:00:00Z' }),
      baseItem({ id: 'upcoming', status: 'OPEN', dueDate: '2027-01-12T00:00:00Z' })
    ];

    expect(reviewItems(items, state({ statusFilter: 'OPEN' })).map((item) => item.id)).toEqual(['open', 'overdue', 'upcoming']);
    expect(reviewItems(items, state({ statusFilter: 'COMPLETED' })).map((item) => item.id)).toEqual(['done', 'purchased']);
    expect(reviewItems(items, state({ statusFilter: 'CLAIMED' })).map((item) => item.id)).toEqual(['claimed']);
    expect(reviewItems(items, state({ statusFilter: 'PURCHASED' })).map((item) => item.id)).toEqual(['purchased']);
    expect(reviewItems(items, state({ statusFilter: 'OVERDUE' })).map((item) => item.id)).toEqual(['overdue']);
    expect(reviewItems(items, state({ statusFilter: 'UPCOMING' })).map((item) => item.id)).toEqual(['upcoming']);
  });

  it('sorts by due date, category, status, or created order', () => {
    const items = [
      baseItem({ id: 'b', name: 'B', category: 'Pantry', status: 'DONE', dueDate: '2027-02-01T00:00:00Z' }),
      baseItem({ id: 'a', name: 'A', category: 'Bakery', status: 'OPEN', dueDate: '2027-01-01T00:00:00Z' }),
      baseItem({ id: 'c', name: 'C', category: null, status: 'CLAIMED', dueDate: null })
    ];

    expect(reviewItems(items, state({ sortBy: 'dueDate' })).map((item) => item.id)).toEqual(['a', 'b', 'c']);
    expect(reviewItems(items, state({ sortBy: 'category' })).map((item) => item.id)).toEqual(['c', 'a', 'b']);
    expect(reviewItems(items, state({ sortBy: 'status' })).map((item) => item.id)).toEqual(['c', 'b', 'a']);
    expect(reviewItems(items, state({ sortBy: 'created' })).map((item) => item.id)).toEqual(['b', 'a', 'c']);
  });

  it('uses injected time so overdue and upcoming views can react while the page stays open', () => {
    const item = baseItem({ id: 'soon', status: 'OPEN', dueDate: '2027-01-10T12:00:00Z' });

    expect(reviewItems([item], state({ statusFilter: 'UPCOMING', now: new Date('2027-01-10T11:59:00Z') })).map((entry) => entry.id)).toEqual(['soon']);
    expect(reviewItems([item], state({ statusFilter: 'OVERDUE', now: new Date('2027-01-10T12:01:00Z') })).map((entry) => entry.id)).toEqual(['soon']);
  });

  it('combines grocery hide-completed with review filtering before grouping or rendering', () => {
    const items = [
      baseItem({ id: 'open-produce', name: 'Apples', category: 'Produce', status: 'OPEN' }),
      baseItem({ id: 'done-produce', name: 'Bananas', category: 'Produce', status: 'DONE' }),
      baseItem({ id: 'open-bakery', name: 'Bread', category: 'Bakery', status: 'OPEN' })
    ];

    expect(reviewDisplayedItems(items, list('GROCERY'), true, state({ query: 'produce' })).map((item) => item.id)).toEqual(['open-produce']);
  });

  it('keeps non-grocery completed items visible unless the review filter removes them', () => {
    const items = [baseItem({ id: 'done-task', status: 'DONE' })];

    expect(reviewDisplayedItems(items, list('TODO'), true, state()).map((item) => item.id)).toEqual(['done-task']);
    expect(reviewDisplayedItems(items, list('TODO'), true, state({ statusFilter: 'OPEN' }))).toEqual([]);
  });

  it('provides a fresh default review state for list switches', () => {
    expect(defaultItemReviewState()).toEqual({ query: '', statusFilter: 'ALL', sortBy: 'created' });
    expect(defaultItemReviewState()).not.toBe(defaultItemReviewState());
  });
});
