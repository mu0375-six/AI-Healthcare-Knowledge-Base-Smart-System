<template>
  <div class="composer" :class="{ dragging }" @paste="onPaste" @dragover.prevent="dragging = true" @dragleave="dragging = false" @drop.prevent="onDrop">
    <div class="composer-inner">
      <div class="compose-shell">
        <div v-if="pending.length" class="pending" aria-label="待发送图片">
          <div v-for="(p, i) in pending" :key="p.key" class="thumb">
            <img :src="p.preview" alt="" />
            <button type="button" aria-label="移除图片" @click="removePending(i)" v-html="ICONS.close"></button>
          </div>
        </div>

        <el-input
          v-model="question"
          type="textarea"
          :rows="2"
          resize="none"
          aria-label="输入健康问题"
          placeholder="描述症状、药品或检查结果..."
          @keydown="onKey"
        />

        <div class="bar">
          <div class="tools">
            <label class="tool" title="从相册或电脑选图">
              <span v-html="ICONS.image"></span>
              <span class="tool-txt">图片</span>
              <input type="file" accept="image/png,image/jpeg,image/jpg,image/webp,image/gif" multiple hidden @change="onPick" />
            </label>
            <label class="tool" title="调用摄像头拍照">
              <span v-html="ICONS.camera"></span>
              <span class="tool-txt">拍照</span>
              <input type="file" accept="image/*" capture="environment" hidden @change="onPick" />
            </label>
            <span class="hint">
              <span class="hint-wide">图片单张不超过 4 MB</span>
              <span class="hint-short">最多 4 张</span>
            </span>
          </div>

          <button
            v-if="streaming"
            class="btn btn-ghost composer-action"
            type="button"
            title="停止生成"
            aria-label="停止生成"
            @click="$emit('stop')"
          >
            <span v-html="ICONS.stop"></span>
          </button>
          <button
            v-else
            class="btn btn-primary composer-action"
            type="button"
            title="发送"
            aria-label="发送消息"
            :disabled="!question.trim() && !pending.length"
            @click="submit"
          >
            <span v-html="ICONS.send"></span>
          </button>
        </div>
      </div>
      <p class="composer-note">回答仅供健康科普参考，不替代医生诊断与治疗。</p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onBeforeUnmount, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { ICONS } from '@/utils/icons'

defineProps<{ streaming: boolean }>()

const dragging = ref(false)

const emit = defineEmits<{
  send: [text: string, files: File[]]
  stop: []
}>()

const question = ref('')
const pending = ref<{ key: number; file: File; preview: string }[]>([])

function addFiles(files: File[]) {
  for (const file of files) {
    if (!file.type.startsWith('image/')) continue
    if (file.size > 4 * 1024 * 1024) {
      ElMessage.warning(`「${file.name}」超过 4MB，已跳过`)
      continue
    }
    if (pending.value.length >= 4) {
      ElMessage.warning('一次最多 4 张图片')
      break
    }
    pending.value.push({ key: Date.now() + Math.random(), file, preview: URL.createObjectURL(file) })
  }
}

function onPick(e: Event) {
  const input = e.target as HTMLInputElement
  addFiles(Array.from(input.files || []))
  input.value = ''
}

function onPaste(e: ClipboardEvent) {
  const files = Array.from(e.clipboardData?.files || []).filter((f) => f.type.startsWith('image/'))
  if (files.length) {
    e.preventDefault()
    addFiles(files)
  }
}

function onDrop(e: DragEvent) {
  dragging.value = false
  addFiles(Array.from(e.dataTransfer?.files || []))
}

function removePending(i: number) {
  URL.revokeObjectURL(pending.value[i].preview)
  pending.value.splice(i, 1)
}

function releasePending() {
  for (const item of pending.value) URL.revokeObjectURL(item.preview)
  pending.value = []
}

function onKey(e: KeyboardEvent) {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    submit()
  }
}

function submit() {
  const text = question.value.trim()
  const files = pending.value.map((p) => p.file)
  if (!text && !files.length) return
  question.value = ''
  releasePending()
  emit('send', text, files)
}

onBeforeUnmount(releasePending)

defineExpose({
  /** 路由带预设问题时由视图填入并触发。 */
  prefill(q: string) {
    question.value = q
    submit()
  },
})
</script>

<style scoped>
.composer {
  flex-shrink: 0;
  padding: var(--space-3) var(--space-5) var(--space-2);
  border-top: 1px solid var(--edge);
  background: var(--card);
}

.composer-inner {
  width: min(100%, 820px);
  margin: 0 auto;
}

.compose-shell {
  overflow: hidden;
  border: 1px solid var(--edge-strong);
  border-radius: var(--r-shell);
  background: var(--card);
  transition: border-color 0.15s var(--ease-soft), box-shadow 0.15s var(--ease-soft);
}

.compose-shell:focus-within {
  border-color: var(--accent-line);
  box-shadow: 0 0 0 3px var(--accent-wash);
}

.composer.dragging .compose-shell {
  border-color: var(--accent);
  box-shadow: 0 0 0 3px var(--accent-wash);
}

.composer :deep(.el-textarea__inner) {
  background: transparent !important;
  box-shadow: none !important;
  min-height: 58px !important;
  padding: var(--space-3) var(--space-4) var(--space-2);
  font-size: 14px;
  line-height: 1.55;
}

.pending {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
  padding: var(--space-3) var(--space-4) 0;
}

.thumb {
  position: relative;
  width: 62px;
  height: 62px;
  border-radius: var(--r-control);
  overflow: hidden;
  border: 1px solid var(--edge);
}

.thumb img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.thumb button {
  position: absolute;
  top: 3px;
  right: 3px;
  display: grid;
  place-items: center;
  width: 18px;
  height: 18px;
  border: 0;
  border-radius: var(--r-pill);
  background: color-mix(in srgb, var(--ink) 68%, transparent);
  color: var(--paper);
  cursor: pointer;
  padding: 0;
}

.thumb button :deep(svg) {
  width: 11px;
  height: 11px;
  display: block;
}

.bar {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  min-height: 44px;
  padding: 5px 7px 5px var(--space-2);
  border-top: 1px solid var(--edge);
  background: color-mix(in srgb, var(--paper) 34%, var(--card));
}

.tools {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  min-width: 0;
}

.tool {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-height: 32px;
  padding: 5px var(--space-2);
  border-radius: var(--r-control);
  border: 0;
  color: var(--ink-mute);
  font-size: 12.5px;
  cursor: pointer;
  transition: color 0.15s var(--ease-soft), background 0.15s var(--ease-soft);
}

.tool:active {
  background: var(--accent-wash);
}

.tool :deep(svg) {
  width: 16px;
  height: 16px;
  display: block;
}

@media (hover: hover) and (pointer: fine) {
  .tool:hover {
    color: var(--accent);
    background: var(--accent-wash);
  }
}

.hint {
  font-size: 12px;
  color: var(--ink-faint);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

:global(html.dark) .thumb button {
  background: color-mix(in srgb, var(--paper) 76%, transparent);
  color: var(--ink);
}

.hint-short {
  display: none;
}

.composer-action {
  width: 34px;
  height: 34px;
  padding: 0;
  margin-left: auto;
  flex-shrink: 0;
}

.composer-action :deep(svg) {
  width: 16px;
  height: 16px;
}

.composer-note {
  margin: 5px 0 0;
  color: var(--ink-faint);
  font-size: 10.5px;
  line-height: 1.4;
  text-align: center;
}

@media (max-width: 720px) {
  .composer {
    padding: var(--space-2) var(--space-4) max(var(--space-2), env(safe-area-inset-bottom));
  }
  .tool-txt,
  .hint-wide {
    display: none;
  }
  .hint-short {
    display: inline;
  }
  .hint {
    font-size: 11.5px;
  }
  .tool {
    padding: 5px 7px;
  }
}
</style>
