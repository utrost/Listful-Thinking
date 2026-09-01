<template>
  <main class="app-shell">
    <section class="hero">
      <p class="eyebrow">{{ t('app.tagline') }}</p>
      <div class="header-row">
        <div>
          <h1>{{ t('app.title') }}</h1>
          <p>{{ t('app.intro') }}</p>
        </div>
        <button v-if="currentUser" type="button" class="secondary" @click="handleLogout">{{ t('auth.logout') }}</button>
      </div>

      <p v-if="message" class="status">{{ message }}</p>

      <section v-if="publicToken" class="panel public-panel">
        <h2>{{ publicList?.title ?? t('sharing.publicList') }}</h2>
        <p v-if="publicList?.description">{{ publicList.description }}</p>
        <ul class="item-list">
          <li v-for="item in publicList?.items ?? []" :key="item.id">
            <img v-if="item.imageUrl" class="item-image" :src="item.imageUrl" :alt="item.name" />
            <span>
              <strong>{{ item.name }}</strong>
              <small v-if="item.description">{{ item.description }}</small>
              <small>{{ item.status }}<template v-if="item.price"> · {{ item.price }} €</template></small>
            </span>
            <form v-if="item.status === 'OPEN'" class="claim-form" @submit.prevent="handleClaimPublicItem(item.id)">
              <input v-model="guestName" :placeholder="t('sharing.guestName')" required />
              <button type="submit">{{ t('sharing.claim') }}</button>
            </form>
          </li>
        </ul>
      </section>

      <section v-else-if="!currentUser" class="panel-grid">
        <form class="panel" @submit.prevent="handleRegister">
          <h2>{{ t('auth.register') }}</h2>
          <label>{{ t('auth.username') }}<input v-model="registerForm.username" required minlength="3" /></label>
          <label>{{ t('auth.email') }}<input v-model="registerForm.email" type="email" /></label>
          <label>{{ t('auth.password') }}<input v-model="registerForm.password" type="password" required minlength="8" /></label>
          <button type="submit">{{ t('auth.register') }}</button>
        </form>

        <form class="panel" @submit.prevent="handleLogin">
          <h2>{{ t('auth.login') }}</h2>
          <label>{{ t('auth.username') }}<input v-model="loginForm.username" required /></label>
          <label>{{ t('auth.password') }}<input v-model="loginForm.password" type="password" required /></label>
          <button type="submit">{{ t('auth.login') }}</button>
          <label>{{ t('auth.email') }}<input v-model="emailAuthForm.email" type="email" /></label>
          <div class="button-row">
            <button type="button" class="secondary" @click="handleRequestMagicLink">{{ t('auth.magicLink') }}</button>
            <button type="button" class="secondary" @click="handleRequestPasswordReset">{{ t('auth.passwordReset') }}</button>
          </div>
          <template v-if="resetToken">
            <label>{{ t('auth.newPassword') }}<input v-model="resetPasswordForm.password" type="password" minlength="8" /></label>
            <button type="button" class="secondary" @click="handleConsumePasswordReset">{{ t('auth.setNewPassword') }}</button>
          </template>
        </form>
      </section>

      <section v-else class="workspace">
        <div class="section-header">
          <h2>{{ t('lists.title') }}</h2>
          <span>{{ currentUser.username }} · {{ currentUser.role }}</span>
        </div>

        <section v-if="notifications.length" class="panel notifications-panel">
          <h3>{{ t('notifications.title') }}</h3>
          <ul class="notification-list">
            <li v-for="notification in notifications" :key="notification.id">
              <span>{{ notification.message }}</span>
              <button type="button" class="secondary subtle" @click="handleMarkNotificationRead(notification.id)">{{ t('notifications.markRead') }}</button>
            </li>
          </ul>
        </section>

        <section v-if="currentUser.role === 'ADMIN'" class="panel admin-panel">
          <div class="section-header">
            <h3>{{ t('admin.title') }}</h3>
            <button type="button" class="secondary subtle" @click="handleLoadAdminPanel">{{ t('admin.refresh') }}</button>
          </div>
          <label class="toggle-row">
            <input
              type="checkbox"
              :checked="adminSettings?.registrationEnabled ?? false"
              @change="handleToggleRegistration(($event.target as HTMLInputElement).checked)"
            />
            <span>{{ t('admin.registrationEnabled') }}</span>
          </label>
          <form class="inline-form" @submit.prevent="handleAdminCreateUser">
            <input v-model="adminUserForm.username" :placeholder="t('auth.username')" required minlength="3" />
            <input v-model="adminUserForm.email" :placeholder="t('auth.email')" type="email" />
            <input v-model="adminUserForm.password" :placeholder="t('auth.password')" type="password" required minlength="8" />
            <select v-model="adminUserForm.role">
              <option value="USER">USER</option>
              <option value="ADMIN">ADMIN</option>
            </select>
            <button type="submit">{{ t('admin.createUser') }}</button>
          </form>
          <ul class="admin-user-list">
            <li v-for="user in adminUsers" :key="user.id">
              <span><strong>{{ user.username }}</strong> · {{ user.role }} · {{ user.active ? t('admin.active') : t('admin.inactive') }}</span>
              <small>{{ user.email ?? t('admin.noEmail') }}</small>
              <button type="button" class="secondary subtle" @click="handleToggleUserActive(user.id, !user.active)">{{ user.active ? t('admin.deactivate') : t('admin.activate') }}</button>
            </li>
          </ul>
          <h4>{{ t('admin.lists') }}</h4>
          <ul class="admin-user-list">
            <li v-for="list in adminLists" :key="list.id">
              <span><strong>{{ list.title }}</strong> · {{ list.type }}</span>
              <small>{{ list.ownerUsername }} · {{ list.ownerEmail ?? t('admin.noEmail') }}</small>
            </li>
          </ul>
        </section>

        <form class="inline-form" @submit.prevent="handleCreateList">
          <input v-model="listForm.title" :placeholder="t('lists.newTitle')" required />
          <select v-model="listForm.type">
            <option value="WISH">WISH</option>
            <option value="TODO">TODO</option>
            <option value="CHORE">CHORE</option>
            <option value="EVENT">EVENT</option>
          </select>
          <input
            v-if="newListRules.showTargetDate"
            v-model="listForm.targetDate"
            type="datetime-local"
            :required="newListRules.requireTargetDate"
            :placeholder="t('lists.targetDate')"
          />
          <button type="submit">{{ t('lists.create') }}</button>
        </form>

        <div class="content-grid">
          <ul class="list-cards">
            <li v-for="list in lists" :key="list.id" :class="{ selected: selectedList?.id === list.id }">
              <button type="button" class="text-button" @click="selectList(list)">
                <strong>{{ list.title }}</strong>
                <small>{{ list.type }}</small>
              </button>
            </li>
          </ul>

          <section v-if="selectedList" class="panel detail-panel">
            <div class="section-header">
              <h3>{{ selectedList.title }}</h3>
              <button type="button" class="danger" @click="handleDeleteList(selectedList.id)">{{ t('lists.delete') }}</button>
            </div>
            <p>{{ selectedList.description }}</p>

            <section class="share-panel">
              <h4>{{ t('sharing.title') }}</h4>
              <div class="button-row">
                <button type="button" class="secondary" @click="handleCreatePublicShare">{{ t('sharing.createPublic') }}</button>
                <button v-if="selectedList.publicList" type="button" class="danger subtle" @click="handleRevokePublicShare">{{ t('sharing.revokePublic') }}</button>
              </div>
              <p v-if="selectedList.publicList && selectedList.shareToken" class="copyable-link">{{ publicShareUrl(selectedList.shareToken) }}</p>
              <form class="inline-form" @submit.prevent="handleShareList">
                <input v-model="shareForm.username" :placeholder="t('sharing.username')" required />
                <button type="submit">{{ t('sharing.share') }}</button>
              </form>
              <ul class="chip-list">
                <li v-for="share in shares" :key="share.userId">
                  <span>{{ share.username }}</span>
                  <button type="button" class="danger subtle" @click="handleRevokeShare(share.username)">{{ t('sharing.revoke') }}</button>
                </li>
              </ul>
            </section>

            <form class="inline-form" @submit.prevent="handleCreateItem">
              <input v-model="itemForm.name" :placeholder="t('items.newName')" :required="!currentItemFields.showUrl || !itemForm.url" />
              <input v-if="currentItemFields.showUrl" v-model="itemForm.url" placeholder="URL" @change="handleScrapeItemUrl" />
              <button v-if="currentItemFields.showUrl" type="button" class="secondary" @click="handleScrapeItemUrl">{{ t('items.previewUrl') }}</button>
              <textarea v-if="currentItemFields.showUrl" v-model="itemForm.description" :placeholder="t('items.description')"></textarea>
              <input v-if="currentItemFields.showImageUrl" v-model="itemForm.imageUrl" :placeholder="t('items.imageUrl')" />
              <img v-if="itemForm.imageUrl" class="item-image preview" :src="itemForm.imageUrl" :alt="itemForm.name || t('items.newName')" />
              <input v-if="currentItemFields.showPrice" v-model.number="itemForm.price" type="number" min="0" step="0.01" :placeholder="t('items.price')" />
              <input v-if="currentItemFields.showDueDate" v-model="itemForm.dueDate" type="datetime-local" :placeholder="t('items.dueDate')" />
              <input v-if="currentItemFields.showRecurrenceRule" v-model="itemForm.recurrenceRule" placeholder="FREQ=WEEKLY" />
              <button type="submit">{{ t('items.create') }}</button>
            </form>

            <ul class="item-list">
              <li v-for="item in items" :key="item.id">
                <img v-if="item.imageUrl" class="item-image" :src="item.imageUrl" :alt="item.name" />
                <span>
                  <strong>{{ item.name }}</strong>
                  <small v-if="item.description">{{ item.description }}</small>
                  <small>{{ item.status }}<template v-if="item.price"> · {{ item.price }} €</template></small>
                </span>
                <button type="button" class="danger" @click="handleDeleteItem(item.id)">{{ t('items.delete') }}</button>
              </li>
            </ul>
          </section>
        </div>
      </section>
    </section>
  </main>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { useI18n } from 'vue-i18n';
import {
  createItem,
  createAdminUser,
  createList,
  createPublicShare,
  claimPublicItem,
  deleteItem,
  deleteList,
  getAdminSettings,
  getAdminLists,
  getAdminUsers,
  getCurrentUser,
  getItems,
  getListShares,
  getLists,
  getNotifications,
  getPublicShare,
  login,
  logout,
  markNotificationRead,
  register,
  requestMagicLink,
  requestPasswordReset,
  consumeMagicLink,
  consumePasswordReset,
  revokeListShare,
  revokePublicShare,
  scrapeUrl,
  shareListWithUser,
  updateAdminSettings,
  updateAdminUser,
  type AuthUser,
  type AdminSettings,
  type AdminListEntry,
  type AdminUserEntry,
  type ItemEntry,
  type ListEntry,
  type ListShareEntry,
  type ListType,
  type NotificationEntry,
  type PublicListEntry
} from './api/client';
import { itemFormFieldsForListType, listFormRulesForType } from './listTypes';

const { t } = useI18n();
const currentUser = ref<AuthUser | null>(null);
const lists = ref<ListEntry[]>([]);
const selectedList = ref<ListEntry | null>(null);
const items = ref<ItemEntry[]>([]);
const shares = ref<ListShareEntry[]>([]);
const notifications = ref<NotificationEntry[]>([]);
const adminSettings = ref<AdminSettings | null>(null);
const adminUsers = ref<AdminUserEntry[]>([]);
const adminLists = ref<AdminListEntry[]>([]);
const publicToken = window.location.pathname.startsWith('/s/') ? decodeURIComponent(window.location.pathname.slice(3)) : '';
const magicToken = window.location.pathname === '/magic-login' ? new URLSearchParams(window.location.search).get('token') : '';
const resetToken = window.location.pathname === '/reset-password' ? new URLSearchParams(window.location.search).get('token') : '';
const publicList = ref<PublicListEntry | null>(null);
const guestName = ref('');
const message = ref('');

const registerForm = reactive({ username: '', email: '', password: '' });
const loginForm = reactive({ username: '', password: '' });
const emailAuthForm = reactive({ email: '' });
const resetPasswordForm = reactive({ password: '' });
const adminUserForm = reactive<{ username: string; email: string; password: string; role: 'ADMIN' | 'USER' }>({ username: '', email: '', password: '', role: 'USER' });
const listForm = reactive<{ title: string; type: ListType; targetDate: string }>({ title: '', type: 'WISH', targetDate: '' });
const itemForm = reactive({ name: '', description: '', url: '', imageUrl: '', price: undefined as number | undefined, dueDate: '', recurrenceRule: '' });
const shareForm = reactive({ username: '' });
const newListRules = computed(() => listFormRulesForType(listForm.type));
const currentItemFields = computed(() => itemFormFieldsForListType(selectedList.value?.type ?? 'WISH'));

onMounted(async () => {
  if (publicToken) {
    await run(async () => {
      publicList.value = await getPublicShare(publicToken);
    });
    return;
  }

  if (magicToken) {
    await run(async () => {
      currentUser.value = await consumeMagicLink({ token: magicToken });
      await loadLists();
      await loadNotifications();
      await maybeLoadAdminPanel();
    });
    return;
  }

  try {
    currentUser.value = await getCurrentUser();
    await loadLists();
    await loadNotifications();
    await maybeLoadAdminPanel();
  } catch {
    currentUser.value = null;
  }
});

async function handleRegister() {
  await run(async () => {
    currentUser.value = await register(registerForm);
    registerForm.password = '';
    await loadLists();
    await loadNotifications();
    await maybeLoadAdminPanel();
  });
}

async function handleLogin() {
  await run(async () => {
    currentUser.value = await login(loginForm);
    loginForm.password = '';
    await loadLists();
    await loadNotifications();
    await maybeLoadAdminPanel();
  });
}

async function handleRequestMagicLink() {
  await run(async () => {
    await requestMagicLink({ email: emailAuthForm.email });
    message.value = t('auth.emailSent');
  });
}

async function handleRequestPasswordReset() {
  await run(async () => {
    await requestPasswordReset({ email: emailAuthForm.email });
    message.value = t('auth.emailSent');
  });
}

async function handleConsumePasswordReset() {
  if (!resetToken) return;
  await run(async () => {
    await consumePasswordReset({ token: resetToken, password: resetPasswordForm.password });
    resetPasswordForm.password = '';
    message.value = t('auth.passwordUpdated');
  });
}

async function handleLogout() {
  await logout();
  currentUser.value = null;
  lists.value = [];
  selectedList.value = null;
  items.value = [];
  shares.value = [];
  notifications.value = [];
  adminSettings.value = null;
  adminUsers.value = [];
  adminLists.value = [];
}

async function loadLists() {
  lists.value = await getLists();
  selectedList.value = lists.value[0] ?? null;
  await loadListDetails();
}

async function selectList(list: ListEntry) {
  selectedList.value = list;
  await loadListDetails();
}

async function loadListDetails() {
  await Promise.all([loadItems(), loadShares()]);
}

async function handleCreateList() {
  await run(async () => {
    const created = await createList({
      title: listForm.title,
      type: listForm.type,
      targetDate: listForm.type === 'EVENT' ? toIsoInstant(listForm.targetDate) : undefined
    });
    listForm.title = '';
    listForm.targetDate = '';
    lists.value = [created, ...lists.value];
    await selectList(created);
  });
}

async function handleDeleteList(id: string) {
  await run(async () => {
    await deleteList(id);
    await loadLists();
  });
}

async function loadItems() {
  items.value = selectedList.value ? await getItems(selectedList.value.id) : [];
}

async function loadShares() {
  shares.value = selectedList.value ? await getListShares(selectedList.value.id) : [];
}

async function loadNotifications() {
  notifications.value = await getNotifications();
}

async function maybeLoadAdminPanel() {
  if (currentUser.value?.role === 'ADMIN') {
    await handleLoadAdminPanel();
  } else {
    adminSettings.value = null;
    adminUsers.value = [];
    adminLists.value = [];
  }
}

async function handleLoadAdminPanel() {
  await run(async () => {
    const [settings, users, allLists] = await Promise.all([getAdminSettings(), getAdminUsers(), getAdminLists()]);
    adminSettings.value = settings;
    adminUsers.value = users;
    adminLists.value = allLists;
  });
}

async function handleToggleRegistration(registrationEnabled: boolean) {
  await run(async () => {
    adminSettings.value = await updateAdminSettings({ registrationEnabled });
    adminUsers.value = await getAdminUsers();
  });
}

async function handleAdminCreateUser() {
  await run(async () => {
    await createAdminUser({ ...adminUserForm, email: adminUserForm.email || undefined });
    adminUserForm.username = '';
    adminUserForm.email = '';
    adminUserForm.password = '';
    adminUserForm.role = 'USER';
    await handleLoadAdminPanel();
  });
}

async function handleToggleUserActive(id: string, active: boolean) {
  await run(async () => {
    const updated = await updateAdminUser(id, { active });
    adminUsers.value = adminUsers.value.map((user) => user.id === id ? updated : user);
  });
}

async function handleMarkNotificationRead(notificationId: string) {
  await run(async () => {
    await markNotificationRead(notificationId);
    notifications.value = notifications.value.filter((notification) => notification.id !== notificationId);
  });
}

async function handleShareList() {
  if (!selectedList.value) return;
  await run(async () => {
    const share = await shareListWithUser(selectedList.value!.id, { username: shareForm.username });
    shareForm.username = '';
    shares.value = [share, ...shares.value.filter((existing) => existing.userId !== share.userId)];
  });
}

async function handleRevokeShare(username: string) {
  if (!selectedList.value) return;
  await run(async () => {
    await revokeListShare(selectedList.value!.id, username);
    shares.value = shares.value.filter((share) => share.username !== username);
  });
}

async function handleCreatePublicShare() {
  if (!selectedList.value) return;
  await run(async () => {
    const token = await createPublicShare(selectedList.value!.id);
    selectedList.value = { ...selectedList.value!, publicList: token.publicList, shareToken: token.shareToken };
    lists.value = lists.value.map((list) => list.id === selectedList.value!.id ? selectedList.value! : list);
  });
}

async function handleRevokePublicShare() {
  if (!selectedList.value) return;
  await run(async () => {
    await revokePublicShare(selectedList.value!.id);
    selectedList.value = { ...selectedList.value!, publicList: false, shareToken: null };
    lists.value = lists.value.map((list) => list.id === selectedList.value!.id ? selectedList.value! : list);
  });
}

async function handleClaimPublicItem(itemId: string) {
  if (!publicToken) return;
  await run(async () => {
    await claimPublicItem(publicToken, itemId, { guestName: guestName.value });
    guestName.value = '';
    publicList.value = await getPublicShare(publicToken);
  });
}

function publicShareUrl(token: string) {
  return `${window.location.origin}/s/${token}`;
}

async function handleScrapeItemUrl() {
  if (!itemForm.url) return;
  await run(async () => {
    const scraped = await scrapeUrl({ url: itemForm.url });
    if (!itemForm.name && scraped.title) {
      itemForm.name = scraped.title;
    }
    if (!itemForm.description && scraped.description) {
      itemForm.description = scraped.description;
    }
    if (scraped.imageUrl) {
      itemForm.imageUrl = scraped.imageUrl;
    }
    if (scraped.price !== null) {
      itemForm.price = scraped.price;
    }
  });
}

async function handleCreateItem() {
  if (!selectedList.value) return;
  await run(async () => {
    const fields = currentItemFields.value;
    const created = await createItem(selectedList.value!.id, {
      name: itemForm.name || undefined,
      description: itemForm.description || undefined,
      url: fields.showUrl ? itemForm.url || undefined : undefined,
      imageUrl: fields.showImageUrl ? itemForm.imageUrl || undefined : undefined,
      price: fields.showPrice ? itemForm.price : undefined,
      dueDate: fields.showDueDate ? toIsoInstant(itemForm.dueDate) : undefined,
      recurrenceRule: fields.showRecurrenceRule ? itemForm.recurrenceRule || undefined : undefined
    });
    resetItemForm();
    items.value = [created, ...items.value];
    if (created.name === 'Loading metadata…') {
      window.setTimeout(() => void loadItems(), 1500);
    }
  });
}

async function handleDeleteItem(id: string) {
  await run(async () => {
    await deleteItem(id);
    items.value = items.value.filter((item) => item.id !== id);
  });
}

async function run(action: () => Promise<void>) {
  message.value = '';
  try {
    await action();
  } catch (error) {
    message.value = error instanceof Error ? error.message : String(error);
  }
}

function toIsoInstant(localDateTime: string): string | undefined {
  return localDateTime ? new Date(localDateTime).toISOString() : undefined;
}

function resetItemForm() {
  itemForm.name = '';
  itemForm.description = '';
  itemForm.url = '';
  itemForm.imageUrl = '';
  itemForm.price = undefined;
  itemForm.dueDate = '';
  itemForm.recurrenceRule = '';
}
</script>
