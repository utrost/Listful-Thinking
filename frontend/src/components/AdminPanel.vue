<script setup lang="ts">
import { computed, ref } from 'vue';
import { useI18n } from 'vue-i18n';
import type { AdminSettings, AdminUserEntry, AdminListEntry, AdminSetupLink } from '../api/client';
const props = defineProps<{
  adminSettings: AdminSettings | null;
  adminUsers: AdminUserEntry[];
  adminLists: AdminListEntry[];
  adminUserForm: { username: string; email: string; password: string; role: 'ADMIN' | 'USER' };
  setupLink: AdminSetupLink | null;
}>();
defineEmits<{ refresh: []; registration: [enabled: boolean]; createUser: []; active: [id: string, active: boolean]; setupLink: [id: string]; transfer: [id: string, username: string] }>();
const { t, locale } = useI18n();
function localDate(value: string) {
  return new Intl.DateTimeFormat(locale.value, { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value));
}
const transferTargets = ref<Record<string, string>>({});
const activeAdminCount = computed(() => props.adminUsers.filter(user => user.role === 'ADMIN' && user.active).length);
function cannotDeactivateAdminUser(user: AdminUserEntry) {
  return user.role === 'ADMIN' && user.active && activeAdminCount.value <= 1;
}
</script>
<template>
        <section class="panel admin-panel">
          <div class="section-header">
            <h3>{{ t('admin.title') }}</h3>
            <button type="button" class="secondary subtle" @click="$emit('refresh')">{{ t('admin.refresh') }}</button>
          </div>
          <label class="toggle-row">
            <input
              type="checkbox"
              :checked="adminSettings?.registrationEnabled ?? false"
              @change="$emit('registration', ($event.target as HTMLInputElement).checked)"
            />
            <span>{{ t('admin.registrationEnabled') }}</span>
          </label>
          <form class="inline-form" @submit.prevent="$emit('createUser')">
            <input v-model="adminUserForm.username" :aria-label="t('auth.username')" :placeholder="t('auth.username')" required minlength="3" />
            <input v-model="adminUserForm.email" :aria-label="t('auth.email')" :placeholder="t('auth.email')" type="email" />
            <input v-model="adminUserForm.password" :aria-label="t('auth.password')" :placeholder="t('admin.optionalPassword')" type="password" minlength="8" />
            <select v-model="adminUserForm.role" :aria-label="t('admin.role')">
              <option value="USER">USER</option>
              <option value="ADMIN">ADMIN</option>
            </select>
            <button type="submit">{{ t('admin.createUser') }}</button>
          </form>
          <p class="muted">{{ t('admin.setupHelp') }}</p>
          <div v-if="setupLink" class="panel setup-link" role="status">
            <strong>{{ t('admin.setupLinkFor', { username: setupLink.user.username }) }}</strong>
            <p>{{ t(setupLink.emailed ? 'admin.setupEmailed' : 'admin.setupNotEmailed') }} {{ t('admin.setupExpires', { date: localDate(setupLink.expiresAt) }) }}</p>
            <input v-if="setupLink.setupUrl" readonly :aria-label="t('admin.setupLink')" :value="setupLink.setupUrl" />
          </div>
          <ul class="admin-user-list">
            <li v-for="user in adminUsers" :key="user.id">
              <span><strong>{{ user.username }}</strong> · {{ user.role }} · {{ user.active ? t('admin.active') : t('admin.inactive') }}</span>
              <small>{{ user.email ?? t('admin.noEmail') }}</small>
              <button type="button" class="secondary subtle" :aria-label="`${user.active ? t('admin.deactivate') : t('admin.activate')}: ${user.username}`" :disabled="cannotDeactivateAdminUser(user)" @click="$emit('active', user.id, !user.active)">{{ user.active ? t('admin.deactivate') : t('admin.activate') }}</button>
              <button v-if="user.active" type="button" class="secondary subtle" :aria-label="`${t('admin.newSetupLink')}: ${user.username}`" @click="$emit('setupLink', user.id)">{{ t('admin.newSetupLink') }}</button>
              <small v-if="cannotDeactivateAdminUser(user)" class="muted">{{ t('admin.lastActiveAdminHint') }}</small>
              <form v-if="!user.active && adminLists.find(list => list.ownerId === user.id)?.listCount" class="inline-form" @submit.prevent="$emit('transfer', user.id, transferTargets[user.id] || '')">
                <input v-model="transferTargets[user.id]" :aria-label="`${t('admin.newOwner')}: ${user.username}`" :placeholder="t('admin.newOwner')" required />
                <button type="submit" class="secondary">{{ t('admin.transferLists') }}</button>
              </form>
            </li>
          </ul>
          <h4>{{ t('admin.lists') }}</h4>
          <p class="muted">{{ t('admin.listPrivacy') }}</p>
          <ul class="admin-user-list">
            <li v-for="list in adminLists" :key="list.ownerId">
              <span><strong>{{ list.ownerUsername }}</strong> · {{ list.listCount === 1 ? t('admin.listCountOne') : t('admin.listCount', { count: list.listCount }) }}</span>
            </li>
          </ul>
        </section>
</template>
