<template>
  <span class="wrap">
    <button ref="shotButton" class="shot" type="button" :title="alt || '查看大图'" @click="open = true">
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
        <header class="preview-head">
          <span>
            <b>图片预览</b>
            <small>{{ alt || '问诊图片' }}</small>
          </span>
          <button
            ref="closeButton"
            class="preview-close"
            type="button"
            aria-label="关闭图片预览"
            @click="open = false"
            v-html="ICONS.close"
          ></button>
        </header>
        <div class="preview-stage">
          <img v-if="display" :src="display" :alt="alt || '图片'" />
        </div>
        <div class="actions">
          <button class="btn btn-primary" type="button" @click="save">保存到本地</button>
        </div>
      </div>
    </Teleport>
  </span>
</template>

<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { authedFetch } from '@/utils/authedFetch'
import { ICONS } from '@/utils/icons'

const props = defineProps<{ id?: number; src?: string; alt?: string }>()
const display = ref(props.src || '')
const open = ref(false)
const shotButton = ref<HTMLButtonElement>()
const closeButton = ref<HTMLButtonElement>()
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
watch(open, async (isOpen) => {
  await nextTick()
  if (isOpen) closeButton.value?.focus()
  else shotButton.value?.focus()
})
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
  border: 1px solid var(--edge);
  background: var(--paper-2);
  cursor: zoom-in;
  border-radius: var(--r-control);
  overflow: hidden;
  transition: border-color 0.15s var(--ease-soft), opacity 0.15s var(--ease-soft);
}
.shot:hover,
.shot:focus-visible {
  border-color: var(--accent-line);
  opacity: 0.9;
}
.shot img {
  display: block;
  width: 128px;
  height: 96px;
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
  justify-content: space-between;
  gap: var(--space-3);
  padding: var(--space-4) var(--space-5) var(--space-5);
}

.preview-head {
  width: min(100%, 1100px);
  min-height: 52px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-4);
  color: var(--paper);
}

.preview-head > span {
  min-width: 0;
}

.preview-head b,
.preview-head small {
  display: block;
}

.preview-head b {
  font-size: 14px;
  font-weight: 650;
}

.preview-head small {
  margin-top: 2px;
  overflow: hidden;
  color: color-mix(in srgb, var(--paper) 68%, transparent);
  font-size: 11.5px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.preview-close {
  display: grid;
  place-items: center;
  width: 36px;
  height: 36px;
  padding: 0;
  border: 1px solid color-mix(in srgb, var(--paper) 26%, transparent);
  border-radius: var(--r-control);
  background: color-mix(in srgb, var(--ink) 42%, transparent);
  color: var(--paper);
  cursor: pointer;
}

.preview-close :deep(svg) {
  width: 18px;
  height: 18px;
}

.preview-stage {
  min-height: 0;
  flex: 1;
  display: grid;
  place-items: center;
  width: 100%;
}

.preview-stage img {
  max-width: min(92vw, 1100px);
  max-height: calc(100dvh - 152px);
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

:global(html.dark) .preview-head,
:global(html.dark) .preview-close {
  color: var(--ink);
}

:global(html.dark) .preview-head small {
  color: var(--ink-faint);
}

@media (max-width: 520px) {
  .layer {
    padding: var(--space-3) var(--space-4) max(var(--space-4), env(safe-area-inset-bottom));
  }

  .preview-stage img {
    max-width: 100%;
  }
}
</style>
