<template>
  <aside
    class="sessions"
    aria-label="会话列表"
    :role="drawerOpen ? 'dialog' : undefined"
    :aria-modal="drawerOpen ? 'true' : undefined"
    @transitionend="onDrawerTransitionEnd"
  >
    <div class="drawer-head">
      <strong>会话</strong>
      <button
        ref="closeButton"
        class="drawer-close"
        type="button"
        aria-label="关闭会话列表"
        @click="$emit('close')"
        v-html="ICONS.close"
      ></button>
    </div>

    <button class="btn btn-ghost btn-block new" type="button" @click="$emit('new')">
      <span v-html="ICONS.plus"></span>新对话
    </button>

    <div class="sess-list">
      <p v-if="!sessions.length" class="quiet">还没有会话</p>
      <template v-for="s in sessions" :key="s.id">
        <div v-if="editingId === s.id" class="sess-row editing">
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
        <div
          v-else
          class="sess-row"
          :class="{ active: s.id === activeId }"
        >
          <button
            class="sess-open"
            type="button"
            :aria-current="s.id === activeId ? 'page' : undefined"
            @click="$emit('open', s.id)"
            @dblclick.prevent="startEdit(s)"
          >
            <span class="sess-main">
              <b>{{ sessionTitle(s) }}</b>
              <time>{{ formatWhen(s.updatedAt) }}</time>
            </span>
          </button>
          <span class="ops">
            <button
              type="button"
              title="重命名"
              :aria-label="`重命名会话：${sessionTitle(s)}`"
              @click="startEdit(s)"
              v-html="ICONS.pencil"
            ></button>
            <button
              class="del"
              type="button"
              title="删除"
              :aria-label="`删除会话：${sessionTitle(s)}`"
              @click="$emit('remove', s.id)"
              v-html="ICONS.trash"
            ></button>
          </span>
        </div>
      </template>
      <button v-if="hasMore" class="more" type="button" @click="$emit('more')">加载更多</button>
    </div>
  </aside>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import type { ChatSession } from '@/api/types'
import { formatWhen } from '@/utils/format'
import { ICONS } from '@/utils/icons'

const props = defineProps<{
  sessions: ChatSession[]
  activeId: number | null
  hasMore: boolean
  drawerOpen?: boolean
}>()

const emit = defineEmits<{
  new: []
  open: [id: number]
  remove: [id: number]
  rename: [id: number, title: string]
  more: []
  close: []
}>()

/** 行内重命名：双击或点「改名」进入，Esc 取消；失焦视为确认。 */
const editingId = ref<number | null>(null)
const editTitle = ref('')
const savedTitle = ref('')
const closeButton = ref<HTMLButtonElement>()

watch(
  () => props.drawerOpen,
  (open) => {
    if (open) window.setTimeout(() => closeButton.value?.focus(), 0)
  },
  { flush: 'post' },
)

function onDrawerTransitionEnd(event: TransitionEvent) {
  if (props.drawerOpen && event.propertyName === 'transform' && event.target === event.currentTarget) {
    closeButton.value?.focus()
  }
}

function sessionTitle(session: ChatSession) {
  return session.title || '未命名会话'
}

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

.drawer-head {
  display: none;
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

.sess-row {
  position: relative;
  width: 100%;
  display: flex;
  align-items: center;
  gap: var(--space-1);
  background: none;
  border: 0;
  border-radius: var(--r-control);
  padding: var(--space-1);
  color: var(--ink-soft);
  transition: background 0.15s ease, color 0.15s ease, transform 0.12s var(--ease-out);
}

.sess-row:active {
  transform: scale(0.985);
}

@media (hover: hover) and (pointer: fine) {
  .sess-row:hover {
    background: var(--flag-none-wash);
    color: var(--ink);
  }
}

/* 当前会话：主色底 + 左侧竖条，与顶部导航的选中语言保持一致 */
.sess-row.active {
  background: var(--accent-wash);
  color: var(--accent);
}

.sess-row.active::before {
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

.sess-open {
  min-width: 0;
  flex: 1;
  border: 0;
  background: none;
  color: inherit;
  cursor: pointer;
  padding: var(--space-1) 5px;
  text-align: left;
  border-radius: var(--r-chip);
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
  gap: var(--space-1);
  flex-shrink: 0;
  opacity: 0;
  transition: opacity 0.15s var(--ease-soft);
}

.sess-row:hover .ops,
.sess-row:focus-within .ops,
.sess-row.active .ops {
  opacity: 1;
}

/* 触屏没有 hover：改名/删除会永远藏着 */
@media (hover: none) {
  .ops {
    opacity: 1;
  }
}

.ops button,
.drawer-close {
  display: grid;
  place-items: center;
  width: 24px;
  height: 24px;
  padding: 0;
  border: 0;
  background: none;
  border-radius: var(--r-chip);
  color: var(--ink-faint);
  cursor: pointer;
  transition: color 0.15s var(--ease-soft), background 0.15s var(--ease-soft);
}

.ops button :deep(svg),
.drawer-close :deep(svg) {
  width: 14px;
  height: 14px;
  display: block;
}

.ops button:hover,
.drawer-close:hover {
  color: var(--accent);
  background: var(--flag-none-wash);
}

.ops button.del:hover {
  color: var(--flag-high);
}

.more {
  width: 100%;
  border: 0;
  background: none;
  padding: var(--space-2);
  color: var(--ink-faint);
  font-size: 12px;
  cursor: pointer;
  border-radius: var(--r-control);
}

.editing {
  padding: var(--space-1) 6px;
}

@media (max-width: 900px) {
  .drawer-head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: var(--space-3);
    margin-bottom: var(--space-3);
  }

  .drawer-head strong {
    font-size: 16px;
    font-weight: 600;
  }

  .drawer-close {
    width: 32px;
    height: 32px;
    border-radius: var(--r-pill);
  }
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
