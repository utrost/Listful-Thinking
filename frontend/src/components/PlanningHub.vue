<script setup lang="ts">
import { computed, ref } from 'vue';
import { useI18n } from 'vue-i18n';
import type { AgendaEntry, HubView, ListEntry, Overview, TrashedItem } from '../api/client';
const props = defineProps<{ view: HubView; overview: Overview | null; library: ListEntry[]; trashItems: TrashedItem[]; pending: boolean; ready: boolean }>();
const emit = defineEmits<{
  refresh: []; open: [id: string]; complete: [entry: AgendaEntry]; restore: [list: ListEntry]; restoreItem: [id: string];
  instantiate: [id: string, title: string, targetDate?: string]; delete: [id: string];
}>();
const { t, locale } = useI18n();
const usingTemplate = ref<string | null>(null);
const newTitle = ref('');
const targetDate = ref('');
const groups = computed(() => [
  { name: 'overdue', entries: props.overview?.overdue ?? [] },
  { name: 'today', entries: props.overview?.today ?? [] },
  { name: 'upcoming', entries: props.overview?.upcoming ?? [] }
]);
const agendaCount = computed(() => groups.value.reduce((sum, group) => sum + group.entries.length, 0));
function date(value: string) { return new Intl.DateTimeFormat(locale.value, { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value)); }
function startTemplate(list: ListEntry) { usingTemplate.value = list.id; newTitle.value = list.title; targetDate.value = ''; }
function instantiate(list: ListEntry) {
  emit('instantiate', list.id, newTitle.value, targetDate.value ? new Date(targetDate.value).toISOString() : undefined);
}
</script>
<template>
  <section class="planning-hub" :aria-label="t(`planning.views.${view}`)">
    <div class="section-header">
      <div><h2>{{ t(`planning.views.${view}`) }}</h2><p class="muted">{{ t(`planning.help.${view}`) }}</p></div>
      <button type="button" class="secondary" :disabled="pending" @click="emit('refresh')">{{ t('planning.refresh') }}</button>
    </div>
    <p v-if="pending" role="status">{{ t('planning.loading') }}</p>
    <p v-else-if="!ready" role="alert">{{ t('planning.loadFailed') }}</p>
    <template v-else-if="view === 'today'">
      <p v-if="overview" class="muted agenda-period">{{ t('planning.timezone', { zone: overview.zone }) }}</p>
      <section v-if="!agendaCount" class="panel empty-state"><h3>{{ t('planning.clearDay') }}</h3><p>{{ t('planning.clearDayHelp') }}</p></section>
      <div class="agenda-grid">
        <section v-for="group in groups" :key="group.name" class="panel agenda-group" :class="group.name" :aria-label="t(`planning.groups.${group.name}`)">
          <h3>{{ t(`planning.groups.${group.name}`) }} <span class="count-badge">{{ group.entries.length }}</span></h3>
          <p v-if="!group.entries.length" class="muted">{{ t('planning.nothingDue') }}</p>
          <ul v-else class="agenda-list">
            <li v-for="entry in group.entries" :key="entry.kind + entry.id">
              <span class="agenda-kind">{{ entry.kind === 'EVENT' ? t('lists.types.EVENT') : t(`lists.types.${entry.listType}`) }}</span>
              <button type="button" class="agenda-open" @click="emit('open', entry.listId)">{{ entry.name }}</button>
              <small>{{ entry.listTitle }} · {{ date(entry.dueDate) }}</small>
              <small v-if="entry.ownerLabel">{{ t('items.ownerLabel') }}: {{ entry.ownerLabel }}</small>
              <div class="button-row">
                <button v-if="entry.kind === 'ITEM' && entry.access !== 'READ' && (entry.listType !== 'WISH' || entry.access === 'OWNER')" type="button" class="secondary subtle" :aria-label="`${t('items.done')}: ${entry.name}`" @click="emit('complete', entry)">{{ entry.listType === 'WISH' ? t('items.markPurchased') : t('items.done') }}</button>
                <span v-if="entry.recurrenceRule" class="muted">{{ t('planning.repeats') }}</span>
                <span v-if="entry.access === 'READ'" class="muted">{{ t('sharing.readOnly') }}</span>
              </div>
            </li>
          </ul>
        </section>
      </div>
    </template>
    <template v-else>
      <section v-if="!library.length && !trashItems.length" class="panel empty-state"><h3>{{ t(`planning.empty.${view}`) }}</h3><p>{{ t(`planning.emptyHelp.${view}`) }}</p></section>
      <ul class="library-grid">
        <li v-for="list in library" :key="list.id" class="panel library-card">
          <small>{{ t(`lists.types.${list.type}`) }}</small><h3>{{ list.title }}</h3><p v-if="list.description" class="muted">{{ list.description }}</p>
          <div class="button-row">
            <button v-if="view === 'templates'" type="button" @click="startTemplate(list)">{{ t('planning.useTemplate') }}</button>
            <button v-if="view !== 'trash'" type="button" class="secondary" @click="emit('open', list.id)">{{ t('planning.preview') }}</button>
            <button v-if="view === 'archive' || view === 'trash'" type="button" @click="emit('restore', list)">{{ t('planning.restore') }}</button>
            <button v-if="view === 'templates'" type="button" class="danger subtle" @click="emit('delete', list.id)">{{ t('planning.deleteTemplate') }}</button>
          </div>
          <form v-if="view === 'templates' && usingTemplate === list.id" class="template-use-form" @submit.prevent="instantiate(list)">
            <label>{{ t('planning.instanceTitle') }}<input v-model="newTitle" required maxlength="255" /></label>
            <label v-if="list.type === 'EVENT'">{{ t('lists.targetDate') }}<input v-model="targetDate" type="datetime-local" required /></label>
            <p class="muted">{{ t('planning.freshCopy') }}</p>
            <div class="button-row"><button type="submit">{{ t('planning.createFromTemplate') }}</button><button type="button" class="secondary" @click="usingTemplate = null">{{ t('lists.cancel') }}</button></div>
          </form>
        </li>
      </ul>
      <section v-if="view === 'trash' && trashItems.length" class="panel trash-items"><h3>{{ t('planning.deletedItems') }}</h3>
        <ul class="notification-list"><li v-for="item in trashItems" :key="item.id"><span><strong>{{ item.name }}</strong><small>{{ item.listTitle }}</small><small v-if="item.listArchived">{{ t('planning.restoreArchivedFirst') }}</small></span><button type="button" :disabled="item.listArchived" :aria-label="`${t('planning.restore')}: ${item.name}`" @click="emit('restoreItem', item.id)">{{ t('planning.restore') }}</button></li></ul>
      </section>
    </template>
  </section>
</template>
