<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue';
import { useI18n } from 'vue-i18n';
import L from 'leaflet';
import 'leaflet/dist/leaflet.css';
import { getMapConfig, type ItemEntry } from '../api/client';

const props = defineProps<{ items: ItemEntry[] }>();
const { t } = useI18n();
const mapElement = ref<HTMLElement | null>(null);
const error = ref('');
const plotted = computed(() => props.items.filter(item =>
  item.latitude != null && item.longitude != null && Number.isFinite(item.latitude) && Number.isFinite(item.longitude)));
const unplotted = computed(() => props.items.filter(item => !plotted.value.includes(item)));
let map: L.Map | null = null;
let markers: L.LayerGroup | null = null;
let mounted = true;
let starting = false;

function osmUrl(item: ItemEntry) {
  if (item.latitude == null || item.longitude == null) return '';
  const lat = item.latitude.toFixed(6);
  const lon = item.longitude.toFixed(6);
  return `https://www.openstreetmap.org/?mlat=${lat}&mlon=${lon}#map=16/${lat}/${lon}`;
}

function renderMarkers() {
  if (!map || !markers) return;
  markers.clearLayers();
  const bounds: L.LatLngExpression[] = [];
  for (const item of plotted.value) {
    const position: L.LatLngExpression = [item.latitude!, item.longitude!];
    const popup = document.createElement('span');
    popup.textContent = item.name;
    L.marker(position, { title: item.name }).bindPopup(popup).addTo(markers);
    bounds.push(position);
  }
  if (bounds.length === 1) map.setView(bounds[0], 15);
  else if (bounds.length > 1) map.fitBounds(L.latLngBounds(bounds), { padding: [28, 28], maxZoom: 15 });
}

async function ensureMap() {
  if (!plotted.value.length || !mapElement.value || map || starting) return;
  starting = true;
  try {
    const config = await getMapConfig();
    if (!mounted || !mapElement.value) return;
    map = L.map(mapElement.value, { scrollWheelZoom: false });
    L.tileLayer(config.tileUrl, { maxZoom: 19, attribution: config.attribution,
      referrerPolicy: 'strict-origin-when-cross-origin' }).addTo(map);
    markers = L.layerGroup().addTo(map);
    renderMarkers();
  } catch {
    error.value = t('locations.mapUnavailable');
  } finally {
    starting = false;
  }
}

onMounted(() => { void ensureMap(); });
watch(plotted, () => { if (map) renderMarkers(); else void ensureMap(); }, { flush: 'post' });
onUnmounted(() => { mounted = false; map?.remove(); });
</script>

<template>
  <section class="location-map-section" :aria-label="t('locations.map')">
    <p v-if="error" role="alert">{{ error }}</p>
    <p v-else-if="!plotted.length" class="muted">{{ t('locations.noPins') }}</p>
    <div v-if="plotted.length" ref="mapElement" class="location-map" role="img" :aria-label="t('locations.mapDescription', { count: plotted.length })"></div>
    <ul v-if="plotted.length" class="location-map-index">
      <li v-for="item in plotted" :key="item.id"><a :href="osmUrl(item)" target="_blank" rel="noopener noreferrer">{{ item.name }} ↗</a><span v-if="item.address"> · {{ item.address }}</span></li>
    </ul>
    <p v-if="unplotted.length" class="muted">{{ t('locations.unplotted', { count: unplotted.length }) }}</p>
  </section>
</template>
