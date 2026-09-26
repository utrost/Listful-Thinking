<script setup lang="ts">
import { computed } from 'vue';
import { useI18n } from 'vue-i18n';
import type { AdminSettings, AdminUserEntry, AdminListEntry } from '../api/client';
const props = defineProps<{
  adminSettings: AdminSettings | null;
  adminUsers: AdminUserEntry[];
  adminLists: AdminListEntry[];
  adminUserForm: { username: string; email: string; password: string; role: 'ADMIN' | 'USER' };
}>();
defineEmits<{ refresh: []; registration: [enabled: boolean]; createUser: []; active: [id: string, active: boolean] }>();
const { t } = useI18n();
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
            <input v-model="adminUserForm.password" :aria-label="t('auth.password')" :placeholder="t('auth.password')" type="password" required minlength="8" />
            <select v-model="adminUserForm.role" :aria-label="t('admin.role')">
              <option value="USER">USER</option>
              <option value="ADMIN">ADMIN</option>
            </select>
            <button type="submit">{{ t('admin.createUser') }}</button>
          </form>
          <ul class="admin-user-list">
            <li v-for="user in adminUsers" :key="user.id">
              <span><strong>{{ user.username }}</strong> · {{ user.role }} · {{ user.active ? t('admin.active') : t('admin.inactive') }}</span>
              <small>{{ user.email ?? t('admin.noEmail') }}</small>
              <button type="button" class="secondary subtle" :disabled="cannotDeactivateAdminUser(user)" @click="$emit('active', user.id, !user.active)">{{ user.active ? t('admin.deactivate') : t('admin.activate') }}</button>
              <small v-if="cannotDeactivateAdminUser(user)" class="muted">{{ t('admin.lastActiveAdminHint') }}</small>
            </li>
          </ul>
          <h4>{{ t('admin.lists') }}</h4>
          <ul class="admin-user-list">
            <li v-for="list in adminLists" :key="list.id">
              <span><strong>{{ list.title }}</strong> · {{ t(`lists.types.${list.type}`) }}</span>
              <small>{{ list.ownerUsername }} · {{ list.ownerEmail ?? t('admin.noEmail') }}</small>
            </li>
          </ul>
        </section>
</template>
