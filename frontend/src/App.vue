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

      <section v-if="!currentUser" class="panel-grid">
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
        </form>
      </section>

      <section v-else class="workspace">
        <div class="section-header">
          <h2>{{ t('lists.title') }}</h2>
          <span>{{ currentUser.username }} · {{ currentUser.role }}</span>
        </div>

        <form class="inline-form" @submit.prevent="handleCreateList">
          <input v-model="listForm.title" :placeholder="t('lists.newTitle')" required />
          <select v-model="listForm.type">
            <option value="WISH">WISH</option>
            <option value="CHORE">CHORE</option>
            <option value="EVENT">EVENT</option>
          </select>
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

            <form class="inline-form" @submit.prevent="handleCreateItem">
              <input v-model="itemForm.name" :placeholder="t('items.newName')" required />
              <input v-model="itemForm.url" placeholder="URL" />
              <button type="submit">{{ t('items.create') }}</button>
            </form>

            <ul class="item-list">
              <li v-for="item in items" :key="item.id">
                <span>
                  <strong>{{ item.name }}</strong>
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
import { onMounted, reactive, ref } from 'vue';
import { useI18n } from 'vue-i18n';
import {
  createItem,
  createList,
  deleteItem,
  deleteList,
  getCurrentUser,
  getItems,
  getLists,
  login,
  logout,
  register,
  type AuthUser,
  type ItemEntry,
  type ListEntry,
  type ListType
} from './api/client';

const { t } = useI18n();
const currentUser = ref<AuthUser | null>(null);
const lists = ref<ListEntry[]>([]);
const selectedList = ref<ListEntry | null>(null);
const items = ref<ItemEntry[]>([]);
const message = ref('');

const registerForm = reactive({ username: '', email: '', password: '' });
const loginForm = reactive({ username: '', password: '' });
const listForm = reactive<{ title: string; type: ListType }>({ title: '', type: 'WISH' });
const itemForm = reactive({ name: '', url: '' });

onMounted(async () => {
  try {
    currentUser.value = await getCurrentUser();
    await loadLists();
  } catch {
    currentUser.value = null;
  }
});

async function handleRegister() {
  await run(async () => {
    currentUser.value = await register(registerForm);
    registerForm.password = '';
    await loadLists();
  });
}

async function handleLogin() {
  await run(async () => {
    currentUser.value = await login(loginForm);
    loginForm.password = '';
    await loadLists();
  });
}

async function handleLogout() {
  await logout();
  currentUser.value = null;
  lists.value = [];
  selectedList.value = null;
  items.value = [];
}

async function loadLists() {
  lists.value = await getLists();
  selectedList.value = lists.value[0] ?? null;
  await loadItems();
}

async function selectList(list: ListEntry) {
  selectedList.value = list;
  await loadItems();
}

async function handleCreateList() {
  await run(async () => {
    const created = await createList({ title: listForm.title, type: listForm.type });
    listForm.title = '';
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

async function handleCreateItem() {
  if (!selectedList.value) return;
  await run(async () => {
    const created = await createItem(selectedList.value!.id, { name: itemForm.name, url: itemForm.url || undefined });
    itemForm.name = '';
    itemForm.url = '';
    items.value = [created, ...items.value];
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
</script>
