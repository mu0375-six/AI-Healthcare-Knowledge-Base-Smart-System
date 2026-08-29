<template>
  <div class="composer" :class="{ dragging }" @paste="onPaste" @dragover.prevent="dragging = true" @dragleave="dragging = false" @drop.prevent="onDrop">
    <div v-if="pending.length" class="pending">
      <div v-for="(p, i) in pending" :key="p.key" class="thumb">
        <img :src="p.preview" alt="" />
        <button type="button" aria-label="移除图片" @click="removePending(i)" v-html="ICONS.close"></button>
      </div>
    </div>

    <el-input
      v-model="question"
      type="textarea"
      :rows="3"
      resize="none"
      placeholder="描述症状、药品或想了解的疾病知识，也可粘贴 / 拖入图片。Enter 发送，Shift+Enter 换行"
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
          <span class="hint-wide">化验单、药盒、患处照片都能发</span>
          <span class="hint-short">可直接发图片</span>
        </span>
      </div>

      <button v-if="streaming" class="btn btn-ghost" type="button" @click="$emit('stop')">
        <span v-html="ICONS.stop"></span>停止
      </button>
      <button
        v-else
        class="btn btn-primary"
        type="button"
        :disabled="!question.trim() && !pending.length"
        @click="submit"
      >
        发送<span v-html="ICONS.send"></span>
      </button>
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
/* 输入区是浮起的一块面，压在消息流之上 */
.composer {
  background: var(--card);
  border: 1px solid var(--edge);
  border-radius: var(--r-shell);
  box-shadow: var(--shadow-2), var(--inner-light);
  padding: 12px;
  transition: border-color 0.18s ease, box-shadow 0.24s var(--ease-out);
}

.composer:focus-within {
  border-color: var(--accent-line);
  box-shadow: var(--shadow-2), 0 0 0 3px var(--accent-wash);
}

/* 拖入图片时整块高亮，明确"可以放这儿" */
.composer.dragging {
  border-color: var(--accent);
  box-shadow: var(--shadow-2), 0 0 0 3px var(--accent-wash);
}

.composer :deep(.el-textarea__inner) {
  background: transparent !important;
  box-shadow: none !important;
  padding: 4px 6px;
  font-size: 15px;
  line-height: 1.65;
}

.pending {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 10px;
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
  gap: 12px;
  margin-top: 8px;
}

.tools {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  min-width: 0;
}

/* 图标按钮而不是 emoji：emoji 在不同系统渲染成不同字形，
   而且描边粗细跟界面其余图标对不上 */
.tool {
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
  padding: 6px 11px;
  border-radius: var(--r-pill);
  border: 1px solid var(--edge-strong);
  color: var(--ink-mute);
  font-size: 13px;
  cursor: pointer;
  transition: color 0.15s var(--ease-soft), border-color 0.15s var(--ease-soft), background 0.15s var(--ease-soft),
    transform 0.12s var(--ease-out);
}

.tool:active {
  transform: scale(0.96);
}

.tool :deep(svg) {
  width: 16px;
  height: 16px;
  display: block;
}

@media (hover: hover) and (pointer: fine) {
  .tool:hover {
    color: var(--accent);
    border-color: var(--accent-line);
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

.bar .btn {
  margin-left: auto;
  flex-shrink: 0;
}

@media (max-width: 720px) {
  .composer {
    padding: 10px;
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
    padding: 7px 10px;
  }
}
</style>
