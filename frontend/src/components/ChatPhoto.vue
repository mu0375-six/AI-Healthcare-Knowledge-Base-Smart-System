<template>
  <span class="wrap">
    <button class="shot" type="button" :title="alt || '查看大图'" @click="open = true">
      <img v-if="display" :src="display" :alt="alt || '图片'" />
    </button>
    <Teleport to="body">
      <div v-if="open" class="layer" @click.self="open = false">
        <img v-if="display" :src="display" :alt="alt || '图片'" />
        <div class="actions">
          <button class="btn btn-primary" type="button" @click="save">保存到本地</button>
          <button class="btn btn-ghost" type="button" @click="open = false">关闭</button>
        </div>
      </div>
    </Teleport>
  </span>
</template>

<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { authedFetch } from '@/utils/authedFetch'

const props = defineProps<{ id?: number; src?: string; alt?: string }>()
const display = ref(props.src || '')
const open = ref(false)
let objectUrl = ''

async function load() {
  if (props.src) {
    display.value = props.src
    return
  }
  if (!props.id) return
  revoke()
  const resp = await authedFetch(`/api/chat/images/${props.id}`)
  if (!resp.ok) return
  const blob = await resp.blob()
  objectUrl = URL.createObjectURL(blob)
  display.value = objectUrl
}

function save() {
  if (!display.value) return
  const a = document.createElement('a')
  a.href = display.value
  a.download = props.alt || 'chat-image.jpg'
  document.body.appendChild(a)
  a.click()
  a.remove()
}

function revoke() {
  if (objectUrl) {
    URL.revokeObjectURL(objectUrl)
    objectUrl = ''
  }
}

function onKey(e: KeyboardEvent) {
  if (e.key === 'Escape') open.value = false
}

watch(() => [props.id, props.src], load, { immediate: true })
onMounted(() => window.addEventListener('keydown', onKey))
onBeforeUnmount(() => {
  window.removeEventListener('keydown', onKey)
  revoke()
})
</script>

<style scoped>
.wrap {
  display: inline-block;
}
.shot {
  padding: 0;
  border: 0;
  background: none;
  cursor: zoom-in;
  border-radius: var(--r-control);
  overflow: hidden;
}
.shot img {
  display: block;
  width: 132px;
  height: 100px;
  object-fit: cover;
}
.layer {
  position: fixed;
  inset: 0;
  z-index: 80;
  background: rgba(16, 22, 19, 0.78);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 16px;
  padding: 24px;
}
.layer img {
  max-width: min(92vw, 1100px);
  max-height: 78vh;
  object-fit: contain;
  border-radius: var(--r-control);
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.35);
}
.actions {
  display: flex;
  gap: 10px;
}
</style>
