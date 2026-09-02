import type { ItemEntry } from './api/client';

export type ItemStatusFilter = 'ALL' | 'OPEN' | 'COMPLETED' | 'CLAIMED' | 'PURCHASED' | 'OVERDUE' | 'UPCOMING';
export type ItemSortBy = 'created' | 'dueDate' | 'category' | 'status';

export type ItemReviewState = {
  query: string;
  statusFilter: ItemStatusFilter;
  sortBy: ItemSortBy;
  now?: Date;
};

export function reviewItems(items: ItemEntry[], state: ItemReviewState): ItemEntry[] {
  const query = state.query.trim().toLowerCase();
  const now = state.now ?? new Date();
  return items
    .filter((item) => matchesQuery(item, query))
    .filter((item) => matchesStatusFilter(item, state.statusFilter, now))
    .sort((left, right) => compareItems(left, right, state.sortBy));
}

function matchesQuery(item: ItemEntry, query: string): boolean {
  if (!query) return true;
  return [item.name, item.description, item.category, item.url]
    .some((value) => value?.toLowerCase().includes(query));
}

function matchesStatusFilter(item: ItemEntry, filter: ItemStatusFilter, now: Date): boolean {
  switch (filter) {
    case 'ALL':
      return true;
    case 'OPEN':
      return item.status === 'OPEN';
    case 'COMPLETED':
      return item.status === 'DONE' || item.status === 'PURCHASED';
    case 'CLAIMED':
      return item.status === 'CLAIMED';
    case 'PURCHASED':
      return item.status === 'PURCHASED';
    case 'OVERDUE':
      return item.status === 'OPEN' && item.dueDate !== null && new Date(item.dueDate) < now;
    case 'UPCOMING':
      return item.status === 'OPEN' && item.dueDate !== null && new Date(item.dueDate) >= now;
  }
}

function compareItems(left: ItemEntry, right: ItemEntry, sortBy: ItemSortBy): number {
  switch (sortBy) {
    case 'dueDate':
      return compareNullable(left.dueDate, right.dueDate);
    case 'category':
      return compareText(left.category, right.category) || compareText(left.name, right.name);
    case 'status':
      return compareText(left.status, right.status) || compareText(left.name, right.name);
    case 'created':
      return 0;
  }
}

function compareNullable(left: string | null, right: string | null): number {
  if (left === null && right === null) return 0;
  if (left === null) return 1;
  if (right === null) return -1;
  return left.localeCompare(right);
}

function compareText(left: string | null, right: string | null): number {
  return (left ?? '').localeCompare(right ?? '');
}
