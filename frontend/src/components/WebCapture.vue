<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue';
import { useI18n } from 'vue-i18n';
import PrivateImage from './PrivateImage.vue';
import { ApiClientError, createItem, createList, extractPage, getItems, type ListEntry, type ListType, type PageDraft } from '../api/client';

const props = defineProps<{ lists: ListEntry[] }>();
const emit = defineEmits<{ saved: [listId: string]; progress: [listId: string] }>();
const { t, locale } = useI18n();
type CaptureType = ListType;
type CaptureStart = { url?: string; listId?: string; title?: string; description?: string; imageUrl?: string;
  price?: number | null; priceCurrency?: string; variantNote?: string; seller?: string; availability?: string };
const url = ref('');
const editingUrl = ref(true);
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
let preferredListId = '';
let enteredFields: CaptureStart = {};
const matchingLists = computed(() => props.lists.filter(list => list.type === targetType.value && list.access !== 'READ' && !list.archived && !list.template));

watch(url, () => { draft.value = null; duplicates.value = []; createdEventListId.value = ''; });
watch(targetType, () => { targetListId.value = ''; duplicates.value = []; createdEventListId.value = ''; });
watch(targetListId, () => { void checkDuplicates(); });

function sameSource(left: string, right: string) {
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
  if ((targetType.value !== 'WISH' && targetType.value !== 'TRIP') || !listId || !draft.value) return;
  const sourceUrl = draft.value.sourceUrl;
  try {
    const existing = await getItems(listId);
    if (targetListId.value === listId && draft.value?.sourceUrl === sourceUrl) {
      duplicates.value = existing.filter(item => (item.url && sameSource(item.url, sourceUrl)) ||
        (item.sourceUrl && sameSource(item.sourceUrl, sourceUrl))).map(item => item.name);
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
      if (blocksManualFallback(cause)) throw cause;
      found = { kind: 'PAGE', sourceUrl: requestedUrl, extractedAt: new Date().toISOString(), title: null,
        description: null, imageUrl: null, price: null, priceCurrency: null, seller: null,
        availability: null, ingredients: [], steps: [], eventStart: null, eventLocation: null };
      message.value = t('capture.manualFallback');
    }
    if (url.value.trim() !== requestedUrl) return;
    draft.value = found;
    editingUrl.value = false;
    const preferred = props.lists.find(list => list.id === preferredListId && list.access !== 'READ' && !list.archived && !list.template);
    targetType.value = preferred?.type === 'TRIP' ? 'TRIP' : found.kind === 'RECIPE' ? 'GROCERY' : found.kind === 'EVENT' ? 'EVENT' : found.kind === 'HOWTO' ? 'TODO' : 'WISH';
    await nextTick();
    title.value = enteredFields.title || found.title || '';
    description.value = enteredFields.description || found.description || '';
    imageUrl.value = enteredFields.imageUrl || found.imageUrl || '';
    price.value = enteredFields.price ?? found.price;
    priceCurrency.value = enteredFields.priceCurrency || found.priceCurrency || '';
    variantNote.value = enteredFields.variantNote || '';
    seller.value = enteredFields.seller || found.seller || '';
    availability.value = enteredFields.availability || found.availability || '';
    lines.value = (found.kind === 'RECIPE' ? found.ingredients : found.kind === 'HOWTO' ? found.steps : []).join('\n');
    eventLocation.value = found.eventLocation ?? '';
    eventDate.value = found.eventStart && !Number.isNaN(Date.parse(found.eventStart))
      ? localDateTime(new Date(found.eventStart)) : '';
    targetListId.value = props.lists.some(list => list.id === preferredListId && list.type === targetType.value && list.access !== 'READ' && !list.archived && !list.template)
      ? preferredListId : '';
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : t('capture.previewFailed');
  } finally { pending.value = false; }
}

async function begin(start: CaptureStart = {}) {
  preferredListId = start.listId ?? '';
  enteredFields = start;
  url.value = start.url ?? '';
  editingUrl.value = true;
  draft.value = null;
  targetListId.value = '';
  newListTitle.value = '';
  duplicates.value = [];
  createdEventListId.value = '';
  message.value = '';
  error.value = '';
  if (start.url) await preview();
}

defineExpose({ begin });

function localDateTime(date: Date) {
  const pad = (value: number) => String(value).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

function sourceHost(value: string) {
  try { return new URL(value).hostname; } catch { return value; }
}

function blocksManualFallback(cause: unknown) {
  if (!(cause instanceof ApiClientError)) return false;
  if ([401, 403, 429].includes(cause.status)) return true;
  return cause.status === 400 && [
    'Scraping private or local network addresses is not allowed.',
    'Only standard web ports can be scraped.',
    'URL credentials are not allowed.',
    'Only HTTP and HTTPS URLs can be scraped.',
    'URL must include a host.',
    'URL is invalid.'
  ].includes(cause.backendMessage);
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
    } else if (targetType.value === 'WISH' || targetType.value === 'TRIP') {
      if (!title.value.trim()) throw new Error(t('capture.titleRequired'));
      listId = await targetList();
      await createItem(listId, { name: title.value.trim(), description: description.value.trim() || undefined,
        url: draft.value.sourceUrl, sourceUrl: draft.value.sourceUrl, imageUrl: imageUrl.value.trim() || undefined,
        ...(targetType.value === 'WISH' ? { price: price.value ?? undefined, priceCurrency: price.value == null ? undefined : priceCurrency.value.trim().toUpperCase() || undefined,
          variantNote: variantNote.value.trim() || undefined, seller: seller.value.trim() || undefined,
          availability: availability.value.trim() || undefined } : {}) });
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
    if (saved && listId) emit('progress', listId);
    error.value = saved ? t('capture.partial', { count: saved }) : cause instanceof Error ? cause.message : t('capture.saveFailed');
  } finally { pending.value = false; }
}
</script>

<template>
  <section class="web-capture panel">
    <h2>{{ t('capture.open') }}</h2>
    <p v-if="!draft" class="muted">{{ t('capture.help') }}</p>
    <form v-if="!draft || editingUrl" class="capture-form" @submit.prevent="preview">
      <label>{{ t('capture.url') }}<input v-model="url" type="url" required maxlength="2000" placeholder="https://…" /></label>
      <button type="submit" :disabled="pending">{{ t('capture.preview') }}</button>
    </form>
    <p v-if="message" role="status">{{ message }}</p>
    <p v-if="error" role="alert">{{ error }}</p>
    <form v-if="draft" class="capture-review" @submit.prevent="save">
      <div class="capture-source">
        <strong>{{ t('capture.review') }}</strong>
        <p>{{ t('capture.sourceHint', { site: sourceHost(draft.sourceUrl) }) }}</p>
        <small>{{ new Intl.DateTimeFormat(locale, { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(draft.extractedAt)) }}</small>
        <div class="capture-source-actions"><a :href="draft.sourceUrl" target="_blank" rel="noopener noreferrer">{{ t('capture.openSource') }}</a>
          <button type="button" class="secondary subtle" @click="editingUrl = true">{{ t('capture.changeUrl') }}</button></div>
      </div>
      <label v-if="targetType === 'WISH' || targetType === 'EVENT' || targetType === 'TRIP'">{{ t('capture.title') }}<input v-model="title" required maxlength="255" /></label>
      <label>{{ t('capture.destination') }}<select v-model="targetType">
        <option value="WISH">{{ t('lists.types.WISH') }}</option><option value="GROCERY">{{ t('lists.types.GROCERY') }}</option>
        <option value="EVENT">{{ t('lists.types.EVENT') }}</option><option value="TODO">{{ t('lists.types.TODO') }}</option>
        <option value="CHORE">{{ t('lists.types.CHORE') }}</option><option value="TRIP">{{ t('lists.types.TRIP') }}</option>
      </select></label>
      <template v-if="targetType !== 'EVENT'">
        <label>{{ t('capture.list') }}<select v-model="targetListId"><option value="">{{ t('capture.newList') }}</option><option v-for="list in matchingLists" :key="list.id" :value="list.id">{{ list.title }}</option></select></label>
        <label v-if="!targetListId">{{ t('capture.newListTitle') }}<input v-model="newListTitle" required maxlength="255" /></label>
      </template>
      <template v-if="targetType === 'TRIP'">
        <p v-if="duplicates.length" role="status" class="capture-warning">{{ t('trip.duplicate', { names: duplicates.join(', ') }) }}</p>
        <label>{{ t('trip.note') }}<textarea v-model="description" maxlength="2000" /></label>
        <label>{{ t('items.imageUrl') }}<input v-model="imageUrl" maxlength="2000" /></label>
        <PrivateImage v-if="imageUrl" class="preview" :src="imageUrl" :alt="title" />
      </template>
      <template v-else-if="targetType === 'WISH'">
        <p v-if="duplicates.length" role="status" class="capture-warning">{{ t('capture.duplicate', { names: duplicates.join(', ') }) }}</p>
        <label>{{ t('capture.variant') }}<input v-model="variantNote" maxlength="255" /></label>
        <div class="capture-pair"><label>{{ t('items.price') }}<input v-model.number="price" type="number" min="0" step="0.01" /></label>
          <label>{{ t('items.currency') }}<input v-model="priceCurrency" maxlength="3" pattern="[A-Z]{3}" /></label></div>
        <details class="capture-more"><summary>{{ t('capture.optionalProductDetails') }}</summary>
          <div class="capture-more-fields">
            <label>{{ t('items.description') }}<textarea v-model="description" maxlength="2000" /></label>
            <label>{{ t('items.imageUrl') }}<input v-model="imageUrl" maxlength="2000" /></label>
            <PrivateImage v-if="imageUrl" class="preview" :src="imageUrl" :alt="title" />
            <div class="capture-pair"><label>{{ t('capture.seller') }}<input v-model="seller" maxlength="255" /></label>
              <label>{{ t('capture.availability') }}<input v-model="availability" maxlength="40" /></label></div>
          </div>
        </details>
      </template>
      <template v-else-if="targetType === 'EVENT'">
        <label>{{ t('lists.targetDate') }}<input v-model="eventDate" type="datetime-local" required /></label>
        <label>{{ t('capture.location') }}<input v-model="eventLocation" maxlength="255" /></label>
        <details class="capture-more"><summary>{{ t('capture.optionalEventDetails') }}</summary>
          <div class="capture-more-fields">
            <label>{{ t('items.description') }}<textarea v-model="description" maxlength="2000" /></label>
            <label>{{ t('capture.preparation') }}<textarea v-model="lines" :placeholder="t('capture.onePerLine')" /></label>
          </div>
        </details>
      </template>
      <label v-else>{{ targetType === 'GROCERY' ? t('capture.ingredients') : t('capture.steps') }}
        <textarea v-model="lines" required :placeholder="t('capture.onePerLine')" /></label>
      <div class="capture-footer"><button type="submit" :disabled="pending">{{ t('capture.save') }}</button></div>
    </form>
  </section>
</template>
