<template>
  <aside
    class="sessions"
    aria-label="会话列表"
    :role="drawerOpen ? 'dialog' : undefined"
    :aria-modal="drawerOpen ? 'true' : undefined"
    @transitionend="onDrawerTransitionEnd"
  >
    <div class="drawer-head">
      <span>
        <small>问诊记录</small>
        <strong>最近会话</strong>
      </span>
      <button
        ref="closeButton"
        class="drawer-close"
        type="button"
        aria-label="关闭会话列表"
        @click="$emit('close')"
        v-html="ICONS.close"
      ></button>
    </div>

    <button class="btn btn-primary btn-block new" type="button" @click="$emit('new')">
      <span v-html="ICONS.plus"></span>开始新问诊
    </button>

    <div class="list-head">
      <span>最近更新</span>
      <small>{{ sessions.length }}</small>
    </div>
    <div class="sess-list">
      <div v-if="!sessions.length" class="quiet">
        <span v-html="ICONS.chat"></span>
        <b>还没有问诊记录</b>
        <small>开始一次新问诊后，会话将保存在这里</small>
      </div>
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
.sessions {
  display: flex;
  flex-direction: column;
  padding: var(--space-4) var(--space-3);
  border-right: 1px solid var(--edge);
  min-height: 0;
  overflow: hidden;
  background: var(--paper-2);
}

.drawer-head {
  min-height: 38px;
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-3);
  margin: 0 var(--space-1) var(--space-3);
}

.drawer-head > span {
  min-width: 0;
}

.drawer-head small {
  display: block;
  margin-bottom: 2px;
  color: var(--ink-faint);
  font-size: 11px;
  line-height: 1.3;
}

.drawer-head strong {
  display: block;
  color: var(--ink);
  font-size: 16px;
  font-weight: 650;
  line-height: 1.35;
}

.new {
  justify-content: flex-start;
  margin-bottom: var(--space-5);
}

.new :deep(svg) {
  width: 16px;
  height: 16px;
}

.list-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-2);
  margin: 0 var(--space-1) var(--space-2);
  color: var(--ink-faint);
  font-size: 11px;
}

.list-head small {
  display: grid;
  place-items: center;
  min-width: 20px;
  height: 20px;
  padding-inline: 5px;
  border: 1px solid var(--edge);
  border-radius: var(--r-pill);
  background: color-mix(in srgb, var(--card) 48%, transparent);
  font-size: 10.5px;
}

.sess-list {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  margin: 0 -4px;
  padding: 0 4px;
}

.quiet {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  padding: var(--space-5) var(--space-2);
  color: var(--ink-faint);
}

.quiet > span {
  display: grid;
  place-items: center;
  width: 30px;
  height: 30px;
  margin-bottom: var(--space-3);
  border: 1px solid var(--edge);
  border-radius: var(--r-control);
  background: color-mix(in srgb, var(--card) 52%, transparent);
}

.quiet :deep(svg) {
  width: 16px;
  height: 16px;
}

.quiet b {
  color: var(--ink-soft);
  font-size: 13px;
  font-weight: 600;
}

.quiet small {
  margin-top: var(--space-1);
  font-size: 11.5px;
  line-height: 1.6;
}

.sess-row {
  position: relative;
  width: 100%;
  display: flex;
  align-items: center;
  gap: var(--space-1);
  background: none;
  border: 0;
  min-height: 48px;
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

.sess-row.active {
  background: var(--card);
  color: var(--accent);
  box-shadow: 0 0 0 1px var(--edge) inset;
}

.sess-row.active::before {
  content: '';
  position: absolute;
  left: 1px;
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
  font-weight: 550;
  font-size: 13px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.sess-main time {
  display: block;
  margin-top: 2px;
  font-size: 10.5px;
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
  margin-top: var(--space-2);
  border: 1px solid var(--edge);
  background: color-mix(in srgb, var(--card) 48%, transparent);
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
  .drawer-close {
    width: 32px;
    height: 32px;
    border-radius: var(--r-pill);
  }
}

@media (min-width: 901px) {
  .drawer-close {
    display: none;
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
