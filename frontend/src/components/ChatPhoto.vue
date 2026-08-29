<template>
  <span class="wrap">
    <button class="shot" type="button" :title="alt || '查看大图'" @click="open = true">
      <img v-if="display" :src="display" :alt="alt || '图片'" />
    </button>
    <Teleport to="body">
      <div
        v-if="open"
        class="layer"
        role="dialog"
        aria-modal="true"
        :aria-label="alt ? `图片预览：${alt}` : '图片预览'"
        @click.self="open = false"
      >
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
let loadVersion = 0

async function load() {
  const version = ++loadVersion
  revoke()
  if (props.src) {
    display.value = props.src
    return
  }
  display.value = ''
  if (!props.id) return
  try {
    const resp = await authedFetch(`/api/chat/images/${props.id}`)
    if (!resp.ok || version !== loadVersion) return
    const blob = await resp.blob()
    if (version !== loadVersion) return
    const nextUrl = URL.createObjectURL(blob)
    if (version !== loadVersion) {
      URL.revokeObjectURL(nextUrl)
      return
    }
    objectUrl = nextUrl
    display.value = nextUrl
  } catch {
    if (version === loadVersion) display.value = ''
  }
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
  loadVersion += 1
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
  z-index: calc(var(--z-modal) + 1);
  background: color-mix(in srgb, var(--ink) 78%, transparent);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: var(--space-4);
  padding: var(--space-5);
}
.layer img {
  max-width: min(92vw, 1100px);
  max-height: 78vh;
  object-fit: contain;
  border-radius: var(--r-control);
  box-shadow: var(--shadow-4);
}
.actions {
  display: flex;
  gap: var(--space-3);
}

:global(html.dark) .layer {
  background: color-mix(in srgb, var(--paper) 84%, transparent);
}
</style>
