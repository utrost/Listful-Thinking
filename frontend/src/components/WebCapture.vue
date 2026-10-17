<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { useI18n } from 'vue-i18n';
import PrivateImage from './PrivateImage.vue';
import { ApiClientError, createItem, createList, extractPage, getItems, type ListEntry, type ListType, type PageDraft } from '../api/client';

const props = defineProps<{ lists: ListEntry[] }>();
const emit = defineEmits<{ saved: [listId: string] }>();
const { t } = useI18n();
type CaptureType = 'WISH' | 'GROCERY' | 'EVENT' | 'TODO' | 'CHORE';
const url = ref('');
const draft = ref<PageDraft | null>(null);
const targetType = ref<CaptureType>('WISH');
const targetListId = ref('');
const newListTitle = ref('');
const title = ref('');
const description = ref('');
const imageUrl = ref('');
const price = ref<number | null>(null);
const priceCurrency = ref('');
const variantNote = ref('');
const seller = ref('');
const availability = ref('');
const eventDate = ref('');
const eventLocation = ref('');
const lines = ref('');
const duplicates = ref<string[]>([]);
const pending = ref(false);
const message = ref('');
const error = ref('');
const createdEventListId = ref('');
const matchingLists = computed(() => props.lists.filter(list => list.type === targetType.value && list.access !== 'READ' && !list.archived && !list.template));

watch(url, () => { draft.value = null; duplicates.value = []; createdEventListId.value = ''; });
watch(targetType, () => { targetListId.value = ''; duplicates.value = []; createdEventListId.value = ''; });
watch(targetListId, () => { void checkDuplicates(); });

function sameProduct(left: string, right: string) {
  try {
    const clean = (value: string) => {
      const parsed = new URL(value);
      parsed.hash = '';
      for (const key of [...parsed.searchParams.keys()]) {
        if (key.startsWith('utm_') || key === 'fbclid' || key === 'gclid') parsed.searchParams.delete(key);
      }
      parsed.searchParams.sort();
      return `${parsed.origin}${parsed.pathname.replace(/\/$/, '')}?${parsed.searchParams}`;
    };
    return clean(left) === clean(right);
  } catch { return false; }
}

async function checkDuplicates() {
  duplicates.value = [];
  const listId = targetListId.value;
  if (targetType.value !== 'WISH' || !listId || !draft.value) return;
  const sourceUrl = draft.value.sourceUrl;
  try {
    const existing = await getItems(listId);
    if (targetListId.value === listId && draft.value?.sourceUrl === sourceUrl) {
      duplicates.value = existing.filter(item => (item.url && sameProduct(item.url, sourceUrl)) ||
        (item.sourceUrl && sameProduct(item.sourceUrl, sourceUrl))).map(item => item.name);
    }
  } catch { /* Duplicate detection is advisory; saving still works. */ }
}

async function preview() {
  error.value = ''; message.value = ''; duplicates.value = [];
  const requestedUrl = url.value.trim();
  if (!requestedUrl) return;
  try {
    const parsed = new URL(requestedUrl);
    if (!['http:', 'https:'].includes(parsed.protocol)) throw new Error();
  } catch { error.value = t('capture.invalidUrl'); return; }
  pending.value = true;
  try {
    let found: PageDraft;
    try {
      found = await extractPage({ url: requestedUrl });
    } catch (cause) {
      if (cause instanceof ApiClientError && [401, 403, 429].includes(cause.status)) throw cause;
      found = { kind: 'PAGE', sourceUrl: requestedUrl, extractedAt: new Date().toISOString(), title: null,
        description: null, imageUrl: null, price: null, priceCurrency: null, seller: null,
        availability: null, ingredients: [], steps: [], eventStart: null, eventLocation: null };
      message.value = t('capture.manualFallback');
    }
    if (url.value.trim() !== requestedUrl) return;
    draft.value = found;
    targetType.value = found.kind === 'RECIPE' ? 'GROCERY' : found.kind === 'EVENT' ? 'EVENT' : found.kind === 'HOWTO' ? 'TODO' : 'WISH';
    title.value = found.title ?? '';
    description.value = found.description ?? '';
    imageUrl.value = found.imageUrl ?? '';
    price.value = found.price;
    priceCurrency.value = found.priceCurrency ?? '';
    variantNote.value = '';
    seller.value = found.seller ?? '';
    availability.value = found.availability ?? '';
    lines.value = (found.kind === 'RECIPE' ? found.ingredients : found.kind === 'HOWTO' ? found.steps : []).join('\n');
    eventLocation.value = found.eventLocation ?? '';
    eventDate.value = found.eventStart && !Number.isNaN(Date.parse(found.eventStart))
      ? localDateTime(new Date(found.eventStart)) : '';
    targetListId.value = '';
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : t('capture.previewFailed');
  } finally { pending.value = false; }
}

function localDateTime(date: Date) {
  const pad = (value: number) => String(value).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

function sourceHost(value: string) {
  try { return new URL(value).hostname; } catch { return value; }
}

async function targetList(): Promise<string> {
  if (targetListId.value) return targetListId.value;
  const name = newListTitle.value.trim();
  if (!name) throw new Error(t('capture.listRequired'));
  const created = await createList({ title: name, type: targetType.value as ListType });
  targetListId.value = created.id;
  return created.id;
}

async function save() {
  if (!draft.value) return;
  error.value = ''; message.value = ''; pending.value = true;
  let saved = 0;
  let listId = '';
  try {
    if (targetType.value === 'EVENT') {
      if (!title.value.trim() || !eventDate.value) throw new Error(t('capture.eventRequired'));
      if (!createdEventListId.value) {
        const created = await createList({ title: title.value.trim(), type: 'EVENT', targetDate: new Date(eventDate.value).toISOString(),
          description: description.value.trim() || undefined, sourceUrl: draft.value.sourceUrl, eventLocation: eventLocation.value.trim() || undefined });
        createdEventListId.value = created.id;
        saved = 1;
      }
      listId = createdEventListId.value;
      const remaining = lines.value.split('\n').map(line => line.trim()).filter(Boolean);
      while (remaining.length) {
        await createItem(listId, { name: remaining[0], sourceUrl: draft.value.sourceUrl });
        remaining.shift(); saved++;
        lines.value = remaining.join('\n');
      }
    } else if (targetType.value === 'WISH') {
      if (!title.value.trim()) throw new Error(t('capture.titleRequired'));
      listId = await targetList();
      await createItem(listId, { name: title.value.trim(), description: description.value.trim() || undefined,
        url: draft.value.sourceUrl, sourceUrl: draft.value.sourceUrl, imageUrl: imageUrl.value.trim() || undefined,
        price: price.value ?? undefined, priceCurrency: price.value == null ? undefined : priceCurrency.value.trim().toUpperCase() || undefined,
        variantNote: variantNote.value.trim() || undefined, seller: seller.value.trim() || undefined,
        availability: availability.value.trim() || undefined });
      saved = 1;
    } else {
      const remaining = lines.value.split('\n').map(line => line.trim()).filter(Boolean);
      if (!remaining.length) throw new Error(t('capture.linesRequired'));
      listId = await targetList();
      while (remaining.length) {
        await createItem(listId, { name: remaining[0], sourceUrl: draft.value.sourceUrl });
        remaining.shift(); saved++;
        lines.value = remaining.join('\n');
      }
    }
    emit('saved', listId);
    message.value = t('capture.saved', { count: saved });
    draft.value = null;
    url.value = '';
  } catch (cause) {
    if (saved && listId) emit('saved', listId);
    error.value = saved ? t('capture.partial', { count: saved }) : cause instanceof Error ? cause.message : t('capture.saveFailed');
  } finally { pending.value = false; }
}
</script>

<template>
  <details class="web-capture panel">
    <summary>{{ t('capture.open') }}</summary>
    <p class="muted">{{ t('capture.help') }}</p>
    <form class="capture-form" @submit.prevent="preview">
      <label>{{ t('capture.url') }}<input v-model="url" type="url" required maxlength="2000" placeholder="https://…" /></label>
      <button type="submit" :disabled="pending">{{ t('capture.preview') }}</button>
    </form>
    <p v-if="message" role="status">{{ message }}</p>
    <p v-if="error" role="alert">{{ error }}</p>
    <form v-if="draft" class="capture-review" @submit.prevent="save">
      <p class="muted">{{ t('capture.review') }} · {{ sourceHost(draft.sourceUrl) }} · {{ new Date(draft.extractedAt).toLocaleString() }}</p>
      <label>{{ t('capture.destination') }}<select v-model="targetType">
        <option value="WISH">{{ t('lists.types.WISH') }}</option><option value="GROCERY">{{ t('lists.types.GROCERY') }}</option>
        <option value="EVENT">{{ t('lists.types.EVENT') }}</option><option value="TODO">{{ t('lists.types.TODO') }}</option>
        <option value="CHORE">{{ t('lists.types.CHORE') }}</option>
      </select></label>
      <template v-if="targetType !== 'EVENT'">
        <label>{{ t('capture.list') }}<select v-model="targetListId"><option value="">{{ t('capture.newList') }}</option><option v-for="list in matchingLists" :key="list.id" :value="list.id">{{ list.title }}</option></select></label>
        <label v-if="!targetListId">{{ t('capture.newListTitle') }}<input v-model="newListTitle" required maxlength="255" /></label>
      </template>
      <template v-if="targetType === 'WISH'">
        <p v-if="duplicates.length" role="status" class="capture-warning">{{ t('capture.duplicate', { names: duplicates.join(', ') }) }}</p>
        <label>{{ t('capture.title') }}<input v-model="title" required maxlength="255" /></label>
        <label>{{ t('items.description') }}<textarea v-model="description" maxlength="2000" /></label>
        <label>{{ t('capture.variant') }}<input v-model="variantNote" maxlength="255" /></label>
        <label>{{ t('items.imageUrl') }}<input v-model="imageUrl" maxlength="2000" /></label>
        <PrivateImage v-if="imageUrl" class="preview" :src="imageUrl" :alt="title" />
        <div class="capture-pair"><label>{{ t('items.price') }}<input v-model.number="price" type="number" min="0" step="0.01" /></label>
          <label>{{ t('items.currency') }}<input v-model="priceCurrency" maxlength="3" pattern="[A-Z]{3}" /></label></div>
        <div class="capture-pair"><label>{{ t('capture.seller') }}<input v-model="seller" maxlength="255" /></label>
          <label>{{ t('capture.availability') }}<input v-model="availability" maxlength="40" /></label></div>
      </template>
      <template v-else-if="targetType === 'EVENT'">
        <label>{{ t('capture.title') }}<input v-model="title" required maxlength="255" /></label>
        <label>{{ t('lists.targetDate') }}<input v-model="eventDate" type="datetime-local" required /></label>
        <label>{{ t('capture.location') }}<input v-model="eventLocation" maxlength="255" /></label>
        <label>{{ t('items.description') }}<textarea v-model="description" maxlength="2000" /></label>
        <label>{{ t('capture.preparation') }}<textarea v-model="lines" :placeholder="t('capture.onePerLine')" /></label>
      </template>
      <label v-else>{{ targetType === 'GROCERY' ? t('capture.ingredients') : t('capture.steps') }}
        <textarea v-model="lines" required :placeholder="t('capture.onePerLine')" /></label>
      <p class="muted">{{ t('capture.confirmHelp') }}</p>
      <button type="submit" :disabled="pending">{{ t('capture.save') }}</button>
    </form>
  </details>
</template>
