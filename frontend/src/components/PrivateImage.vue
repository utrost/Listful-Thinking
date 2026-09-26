<script setup lang="ts">
import { computed, ref } from 'vue';
import { useI18n } from 'vue-i18n';
const props = defineProps<{ src: string; alt: string }>();
const { t } = useI18n();
const allowedSource = ref<string | null>(null);
const uploaded = computed(() => /^data:image\/(png|jpeg|webp|gif);base64,[A-Za-z0-9+/=]+$/.test(props.src));
const external = computed(() => {
  try {
    const url = new URL(props.src);
    return ['https:', 'http:'].includes(url.protocol) && !url.username && !url.password ? url : null;
  } catch { return null; }
});
</script>
<template>
  <img v-if="uploaded || (external && allowedSource === src)" class="item-image" :src="src" :alt="alt" referrerpolicy="no-referrer" loading="lazy" />
  <button v-else-if="external" type="button" class="secondary private-image-placeholder" :aria-label="t('privacy.externalImage', { host: external.hostname })" :title="t('privacy.imageHint', { host: external.hostname })" @click="allowedSource = src">
    {{ t('privacy.loadImage') }}
    <small>{{ external.hostname }}</small>
  </button>
  <span v-else class="muted">{{ t('privacy.invalidImage') }}</span>
</template>
<style scoped>
.private-image-placeholder { width: 100px; min-height: 64px; padding: .4rem; flex: none; font-size: .75rem; }
.private-image-placeholder small { display: block; overflow-wrap: anywhere; font-weight: normal; }
</style>
