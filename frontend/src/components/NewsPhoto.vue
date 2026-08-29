<template>
  <span ref="host" class="photo" :class="{ ready: !!src }">
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
let objectUrl = ''
let observer: IntersectionObserver | null = null
let loadVersion = 0

async function load() {
  const version = ++loadVersion
  revoke()
  src.value = ''
  try {
    const resp = await fetchNewsImage(props.id)
    if (!resp.ok) return
    const blob = await resp.blob()
    if (version !== loadVersion) return
    objectUrl = URL.createObjectURL(blob)
    src.value = objectUrl
  } catch {
    if (version === loadVersion) src.value = ''
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
  display: block;
  aspect-ratio: 16 / 9;
  overflow: hidden;
  background: var(--tray);
}

/* 载入前给一块安静的底色，图片就绪后再淡入 */
.photo img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
  opacity: 0;
  transition: opacity 0.5s var(--ease-soft);
}

.photo.ready img {
  opacity: 1;
}
</style>
