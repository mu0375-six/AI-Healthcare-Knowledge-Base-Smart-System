<template>
  <img v-if="src" :src="src" :alt="alt" class="auth-img" />
</template>

<script setup lang="ts">
import { onBeforeUnmount, ref, watch } from 'vue'

const props = defineProps<{ id: number; alt?: string }>()
const src = ref('')
let objectUrl = ''

async function load() {
  revoke()
  const token = localStorage.getItem('token') || ''
  const resp = await fetch(`/api/chat/images/${props.id}`, {
    headers: { Authorization: `Bearer ${token}` },
  })
  if (!resp.ok) return
  const blob = await resp.blob()
  objectUrl = URL.createObjectURL(blob)
  src.value = objectUrl
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
.auth-img {
  display: block;
  max-width: 240px;
  max-height: 180px;
  object-fit: cover;
  border-radius: var(--r-control);
}
</style>
