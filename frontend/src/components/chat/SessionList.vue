<template>
  <aside class="sessions">
    <button class="btn btn-ghost btn-block new" type="button" @click="$emit('new')">
      <span v-html="ICONS.plus"></span>新对话
    </button>

    <div class="sess-list">
      <p v-if="!sessions.length" class="quiet">还没有会话</p>
      <template v-for="s in sessions" :key="s.id">
        <div v-if="editingId === s.id" class="sess editing">
          <input
            v-model="editTitle"
            v-focus
            class="edit-input"
            maxlength="60"
            aria-label="重命名会话"
            @keyup.enter="save(s.id)"
            @keyup.esc="editingId = null"
            @blur="save(s.id)"
          />
        </div>
        <button
          v-else
          class="sess"
          :class="{ active: s.id === activeId }"
          type="button"
          @click="$emit('open', s.id)"
          @dblclick.prevent="startEdit(s)"
        >
          <span class="sess-main">
            <b>{{ s.title || '未命名会话' }}</b>
            <time>{{ formatWhen(s.updatedAt) }}</time>
          </span>
          <span class="ops">
            <em title="重命名" aria-label="重命名" @click.stop="startEdit(s)" v-html="ICONS.pencil"></em>
            <em class="del" title="删除" aria-label="删除" @click.stop="$emit('remove', s.id)" v-html="ICONS.trash"></em>
          </span>
        </button>
      </template>
      <button v-if="hasMore" class="sess more" type="button" @click="$emit('more')">加载更多</button>
    </div>
  </aside>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import type { ChatSession } from '@/api/types'
import { formatWhen } from '@/utils/format'
import { ICONS } from '@/utils/icons'

defineProps<{
  sessions: ChatSession[]
  activeId: number | null
  hasMore: boolean
}>()

const emit = defineEmits<{
  new: []
  open: [id: number]
  remove: [id: number]
  rename: [id: number, title: string]
  more: []
}>()

/** 行内重命名：双击或点「改名」进入，Esc 取消；失焦视为确认。 */
const editingId = ref<number | null>(null)
const editTitle = ref('')
const savedTitle = ref('')

function startEdit(s: ChatSession) {
  editingId.value = s.id
  editTitle.value = s.title || ''
  savedTitle.value = s.title || ''
}

function save(id: number) {
  if (editingId.value !== id) return
  const title = editTitle.value.trim()
  editingId.value = null
  if (title && title !== savedTitle.value) {
    emit('rename', id, title)
  }
}

const vFocus = { mounted: (el: HTMLInputElement) => el.focus() }
</script>

<style scoped>
/* 侧栏不做成发亮的白色面板：会话很少时，一整根到底的空白高柱
   比没有还难看。改成融进页面底色的列表，只用一条发丝线收边。 */
.sessions {
  display: flex;
  flex-direction: column;
  padding: 0 12px 0 0;
  border-right: 1px solid var(--edge);
  min-height: 0;
  overflow: hidden;
}

.new {
  margin-bottom: 10px;
}

.sess-list {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  margin: 0 -4px;
  padding: 0 4px;
}

.quiet {
  padding: 18px 8px;
}

.sess {
  position: relative;
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  text-align: left;
  background: none;
  border: 0;
  border-radius: var(--r-control);
  padding: 9px 10px;
  cursor: pointer;
  color: var(--ink-soft);
  transition: background 0.15s ease, color 0.15s ease, transform 0.12s var(--ease-out);
}

.sess:active {
  transform: scale(0.985);
}

@media (hover: hover) and (pointer: fine) {
  .sess:hover {
    background: var(--flag-none-wash);
    color: var(--ink);
  }
}

/* 当前会话：主色底 + 左侧竖条，与顶部导航的选中语言保持一致 */
.sess.active {
  background: var(--accent-wash);
  color: var(--accent);
}

.sess.active::before {
  content: '';
  position: absolute;
  left: 0;
  top: 50%;
  width: 3px;
  height: 16px;
  margin-top: -8px;
  border-radius: 0 3px 3px 0;
  background: var(--accent);
}

.sess-main {
  min-width: 0;
  flex: 1;
}

.sess-main b {
  display: block;
  font-weight: 500;
  font-size: 13.5px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.sess-main time {
  display: block;
  margin-top: 2px;
  font-size: 11px;
  color: var(--ink-faint);
}

.ops {
  display: flex;
  gap: 2px;
  flex-shrink: 0;
  opacity: 0;
  transition: opacity 0.15s ease;
}

.sess:hover .ops,
.sess.active .ops {
  opacity: 1;
}

/* 触屏没有 hover：改名/删除会永远藏着 */
@media (hover: none) {
  .ops {
    opacity: 1;
  }
}

.ops em {
  display: grid;
  place-items: center;
  width: 24px;
  height: 24px;
  border-radius: var(--r-chip);
  color: var(--ink-faint);
  cursor: pointer;
  transition: color 0.15s ease, background 0.15s ease;
}

.ops em :deep(svg) {
  width: 14px;
  height: 14px;
  display: block;
}

.ops em:hover {
  color: var(--accent);
  background: var(--flag-none-wash);
}

.ops em.del:hover {
  color: var(--flag-high);
}

.sess.more {
  justify-content: center;
  color: var(--ink-faint);
  font-size: 12px;
}

.editing {
  padding: 4px 6px;
}

.edit-input {
  width: 100%;
  border: 1px solid var(--accent);
  border-radius: var(--r-control);
  padding: 7px 9px;
  font-size: 13.5px;
  background: var(--card);
  color: var(--ink);
  outline: none;
  box-shadow: 0 0 0 3px var(--accent-wash);
}
</style>
