<template>
  <main class="app-shell">
    <section class="hero">
      <p class="eyebrow">{{ t('app.tagline') }}</p>
      <h1>{{ t('app.title') }}</h1>
      <p>{{ t('app.intro') }}</p>
      <button type="button" @click="checkHealth">{{ t('health.check') }}</button>
      <p v-if="healthStatus" class="status">{{ healthStatus }}</p>
    </section>
  </main>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import { useI18n } from 'vue-i18n';
import { getHealth } from './api/client';

const { t } = useI18n();
const healthStatus = ref('');

async function checkHealth() {
  const response = await getHealth();
  healthStatus.value = `${t('health.status')}: ${response.status}`;
}
</script>
