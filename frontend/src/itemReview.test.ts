import { describe, expect, it } from 'vitest';
import { reviewItems, type ItemReviewState } from './itemReview';
import type { ItemEntry } from './api/client';

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
});
