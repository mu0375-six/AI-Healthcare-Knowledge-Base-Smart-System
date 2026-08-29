<template>
  <span
    ref="host"
    class="photo"
    :class="{ ready: !!src, failed }"
    :aria-busy="nearby && !src && !failed"
  >
    <span v-if="!src" class="placeholder" aria-hidden="true"><i></i><i></i></span>
    <img v-if="src" :src="src" :alt="alt || '新闻配图'" loading="lazy" />
  </span>
</template>

<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { fetchNewsImage } from '@/api/news'

const props = defineProps<{ id: number; alt?: string }>()

const host = ref<HTMLElement | null>(null)
const src = ref('')
const nearby = ref(false)
const failed = ref(false)
let objectUrl = ''
let observer: IntersectionObserver | null = null
let loadVersion = 0

async function load() {
  const version = ++loadVersion
  revoke()
  src.value = ''
  failed.value = false
  try {
    const resp = await fetchNewsImage(props.id)
    if (!resp.ok) {
      if (version === loadVersion) failed.value = true
      return
    }
    const blob = await resp.blob()
    if (version !== loadVersion) return
    objectUrl = URL.createObjectURL(blob)
    src.value = objectUrl
  } catch {
    if (version === loadVersion) failed.value = true
  }
}

function revoke() {
  if (objectUrl) {
    URL.revokeObjectURL(objectUrl)
    objectUrl = ''
  }
}

watch(
  () => props.id,
  () => {
    if (nearby.value) void load()
  },
)

watch(nearby, (value) => {
  if (value) void load()
})

onMounted(() => {
  if (!host.value || !('IntersectionObserver' in window)) {
    nearby.value = true
    return
  }
  observer = new IntersectionObserver(
    (entries) => {
      if (!entries.some((entry) => entry.isIntersecting)) return
      nearby.value = true
      observer?.disconnect()
      observer = null
    },
    { rootMargin: '320px 0px', threshold: 0.01 },
  )
  observer.observe(host.value)
})

onBeforeUnmount(() => {
  loadVersion++
  observer?.disconnect()
  observer = null
  revoke()
})
</script>

<style scoped>
.photo {
  position: relative;
  display: block;
  aspect-ratio: 16 / 9;
  overflow: hidden;
  background: var(--tray);
}

.placeholder {
  position: absolute;
  inset: 0;
  display: grid;
  align-content: end;
  gap: var(--space-2);
  padding: var(--space-4);
  transition: opacity 0.16s var(--ease-soft);
}

.placeholder i {
  display: block;
  width: 52%;
  height: 5px;
  border-radius: var(--r-pill);
  background: var(--edge-strong);
}

.placeholder i + i {
  width: 34%;
  opacity: 0.65;
}

.photo.ready .placeholder {
  opacity: 0;
}

.photo.failed .placeholder i {
  opacity: 0.42;
}

.photo img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  object-position: center;
  display: block;
  opacity: 0;
  transition: opacity 0.16s var(--ease-soft);
}

.photo.ready img {
  opacity: 1;
}
</style>
