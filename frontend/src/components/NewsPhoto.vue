<template>
  <span class="photo" :class="{ ready: !!src }">
    <img v-if="src" :src="src" :alt="alt || '新闻配图'" loading="lazy" />
  </span>
</template>

<script setup lang="ts">
import { onBeforeUnmount, ref, watch } from 'vue'
import { fetchNewsImage } from '@/api/news'

const props = defineProps<{ id: number; alt?: string }>()

const src = ref('')
let objectUrl = ''

async function load() {
  revoke()
  src.value = ''
  try {
    const resp = await fetchNewsImage(props.id)
    if (!resp.ok) return
    const blob = await resp.blob()
    objectUrl = URL.createObjectURL(blob)
    src.value = objectUrl
  } catch {
    src.value = ''
  }
}

function revoke() {
  if (objectUrl) {
    URL.revokeObjectURL(objectUrl)
    objectUrl = ''
  }
}

watch(() => props.id, load, { immediate: true })
onBeforeUnmount(revoke)
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
