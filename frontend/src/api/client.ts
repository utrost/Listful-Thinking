export interface HealthResponse {
  status: string;
}

export interface AuthUser {
  id: string;
  username: string;
  email: string | null;
  role: 'ADMIN' | 'USER';
}

export interface RegisterRequest {
  username: string;
  email?: string;
  password: string;
}

export interface LoginRequest {
  username: string;
  password: string;
}

export interface AdminSettings {
  registrationEnabled: boolean;
}

export interface UpdateAdminSettingsRequest {
  registrationEnabled: boolean;
}

export interface AdminUserEntry {
  id: string;
  username: string;
  email: string | null;
  role: 'ADMIN' | 'USER';
  createdAt: string;
}

export type ListType = 'WISH' | 'CHORE' | 'EVENT';

export interface ListEntry {
  id: string;
  title: string;
  description: string | null;
  type: ListType;
  publicList: boolean;
  shareToken: string | null;
  targetDate: string | null;
  createdAt: string;
}

export interface ListRequest {
  title: string;
  description?: string;
  type: ListType;
  targetDate?: string;
}

export type ItemStatus = 'OPEN' | 'CLAIMED' | 'PURCHASED';

export interface ItemEntry {
  id: string;
  listId: string;
  name: string;
  url: string | null;
  imageUrl: string | null;
  price: number | null;
  status: ItemStatus;
  dueDate: string | null;
  recurrenceRule: string | null;
  reservedByGuest: string | null;
}

export interface ItemRequest {
  name?: string;
  url?: string;
  imageUrl?: string;
  price?: number;
  status?: ItemStatus;
  dueDate?: string;
  recurrenceRule?: string;
}

export interface ListShareEntry {
  listId: string;
  userId: string;
  username: string;
  createdAt: string;
}

export interface ShareListRequest {
  username: string;
}

export interface PublicShareToken {
  listId: string;
  publicList: boolean;
  shareToken: string;
  shareUrl: string;
}

export interface PublicItemEntry {
  id: string;
  name: string;
  url: string | null;
  imageUrl: string | null;
  price: number | null;
  status: ItemStatus;
  dueDate: string | null;
  reservedByGuest: string | null;
}

export interface PublicListEntry {
  title: string;
  description: string | null;
  type: ListType;
  targetDate: string | null;
  items: PublicItemEntry[];
}

export interface GuestClaimRequest {
  guestName: string;
}

export interface ScrapeRequest {
  url: string;
}

export interface ScrapeResponse {
  title: string | null;
  description: string | null;
  imageUrl: string | null;
  price: number | null;
}

export interface NotificationEntry {
  id: string;
  messageKey: string;
  message: string;
  readAt: string | null;
  createdAt: string;
}

async function requestJson<T>(path: string, init: RequestInit = {}): Promise<T> {
  const response = await fetch(path, {
    ...init,
    credentials: 'include',
    headers: {
      'Content-Type': 'application/json',
      ...(init.headers ?? {})
    }
  });

  if (!response.ok) {
    throw new Error(`Request failed: ${response.status}`);
  }

  return response.json() as Promise<T>;
}

export async function getHealth(): Promise<HealthResponse> {
  return requestJson<HealthResponse>('/api/v1/health');
}

export async function register(request: RegisterRequest): Promise<AuthUser> {
  return requestJson<AuthUser>('/api/v1/auth/register', {
    method: 'POST',
    body: JSON.stringify(request)
  });
}

export async function login(request: LoginRequest): Promise<AuthUser> {
  return requestJson<AuthUser>('/api/v1/auth/login', {
    method: 'POST',
    body: JSON.stringify(request)
  });
}

export async function getCurrentUser(): Promise<AuthUser | null> {
  const response = await fetch('/api/v1/auth/me', {
    credentials: 'include'
  });

  if (response.status === 401) {
    return null;
  }

  if (!response.ok) {
    throw new Error(`Request failed: ${response.status}`);
  }

  return response.json() as Promise<AuthUser>;
}

export async function logout(): Promise<void> {
  const response = await fetch('/api/v1/auth/logout', {
    method: 'POST',
    credentials: 'include'
  });

  if (!response.ok) {
    throw new Error(`Request failed: ${response.status}`);
  }
}

export async function getAdminSettings(): Promise<AdminSettings> {
  return requestJson<AdminSettings>('/api/v1/admin/settings');
}

export async function getAdminUsers(): Promise<AdminUserEntry[]> {
  return requestJson<AdminUserEntry[]>('/api/v1/admin/users');
}

export async function updateAdminSettings(request: UpdateAdminSettingsRequest): Promise<AdminSettings> {
  return requestJson<AdminSettings>('/api/v1/admin/settings', {
    method: 'PUT',
    body: JSON.stringify(request)
  });
}

export async function getLists(): Promise<ListEntry[]> {
  return requestJson<ListEntry[]>('/api/v1/lists');
}

export async function getList(id: string): Promise<ListEntry> {
  return requestJson<ListEntry>(`/api/v1/lists/${id}`);
}

export async function createList(request: ListRequest): Promise<ListEntry> {
  return requestJson<ListEntry>('/api/v1/lists', {
    method: 'POST',
    body: JSON.stringify(request)
  });
}

export async function updateList(id: string, request: ListRequest): Promise<ListEntry> {
  return requestJson<ListEntry>(`/api/v1/lists/${id}`, {
    method: 'PUT',
    body: JSON.stringify(request)
  });
}

export async function deleteList(id: string): Promise<void> {
  const response = await fetch(`/api/v1/lists/${id}`, {
    method: 'DELETE',
    credentials: 'include'
  });

  if (!response.ok) {
    throw new Error(`Request failed: ${response.status}`);
  }
}

export async function getItems(listId: string): Promise<ItemEntry[]> {
  return requestJson<ItemEntry[]>(`/api/v1/lists/${listId}/items`);
}

export async function createItem(listId: string, request: ItemRequest): Promise<ItemEntry> {
  return requestJson<ItemEntry>(`/api/v1/lists/${listId}/items`, {
    method: 'POST',
    body: JSON.stringify(request)
  });
}

export async function updateItem(itemId: string, request: ItemRequest): Promise<ItemEntry> {
  return requestJson<ItemEntry>(`/api/v1/items/${itemId}`, {
    method: 'PUT',
    body: JSON.stringify(request)
  });
}

export async function deleteItem(itemId: string): Promise<void> {
  const response = await fetch(`/api/v1/items/${itemId}`, {
    method: 'DELETE',
    credentials: 'include'
  });

  if (!response.ok) {
    throw new Error(`Request failed: ${response.status}`);
  }
}

export async function getListShares(listId: string): Promise<ListShareEntry[]> {
  return requestJson<ListShareEntry[]>(`/api/v1/lists/${listId}/shares`);
}

export async function shareListWithUser(listId: string, request: ShareListRequest): Promise<ListShareEntry> {
  return requestJson<ListShareEntry>(`/api/v1/lists/${listId}/shares`, {
    method: 'POST',
    body: JSON.stringify(request)
  });
}

export async function revokeListShare(listId: string, username: string): Promise<void> {
  const response = await fetch(`/api/v1/lists/${listId}/shares/${encodeURIComponent(username)}`, {
    method: 'DELETE',
    credentials: 'include'
  });

  if (!response.ok) {
    throw new Error(`Request failed: ${response.status}`);
  }
}

export async function createPublicShare(listId: string): Promise<PublicShareToken> {
  return requestJson<PublicShareToken>(`/api/v1/lists/${listId}/public-share`, {
    method: 'POST'
  });
}

export async function revokePublicShare(listId: string): Promise<void> {
  const response = await fetch(`/api/v1/lists/${listId}/public-share`, {
    method: 'DELETE',
    credentials: 'include'
  });

  if (!response.ok) {
    throw new Error(`Request failed: ${response.status}`);
  }
}

export async function getPublicShare(token: string): Promise<PublicListEntry> {
  return requestJson<PublicListEntry>(`/api/v1/share/${encodeURIComponent(token)}`);
}

export async function claimPublicItem(token: string, itemId: string, request: GuestClaimRequest): Promise<PublicItemEntry> {
  return requestJson<PublicItemEntry>(`/api/v1/share/${encodeURIComponent(token)}/items/${itemId}/claim`, {
    method: 'POST',
    body: JSON.stringify(request)
  });
}

export async function scrapeUrl(request: ScrapeRequest): Promise<ScrapeResponse> {
  return requestJson<ScrapeResponse>('/api/v1/utils/scrape', {
    method: 'POST',
    body: JSON.stringify(request)
  });
}

export async function getNotifications(): Promise<NotificationEntry[]> {
  return requestJson<NotificationEntry[]>('/api/v1/notifications');
}

export async function markNotificationRead(notificationId: string): Promise<NotificationEntry> {
  return requestJson<NotificationEntry>(`/api/v1/notifications/${notificationId}/read`, {
    method: 'PUT'
  });
}
