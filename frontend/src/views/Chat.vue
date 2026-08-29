<template>
  <div class="chat">
    <SessionList
      id="chat-session-drawer"
      :sessions="sessions"
      :active-id="sessionId"
      :has-more="hasMoreSessions"
      :drawer-open="sessionsOpen && narrowScreen"
      :class="{ open: sessionsOpen }"
      @new="newSession"
      @open="openSession"
      @remove="removeSession"
      @rename="renameSessionById"
      @more="loadMoreSessions"
      @close="closeSessions(true)"
    />
    <button
      v-if="sessionsOpen && narrowScreen"
      class="session-scrim"
      type="button"
      tabindex="-1"
      aria-hidden="true"
      @click="closeSessions(true)"
    ></button>

    <section class="board">
      <div class="mobile-chatbar">
        <button
          ref="sessionTrigger"
          class="btn btn-ghost btn-sm session-trigger"
          type="button"
          aria-controls="chat-session-drawer"
          :aria-expanded="sessionsOpen && narrowScreen"
          @click="openSessions"
        >
          <span v-html="ICONS.chat"></span>
          <span class="session-trigger-label">{{ activeSessionTitle }}</span>
        </button>
      </div>
      <div v-if="profileHint" class="profile-bar">
        <span v-html="ICONS.file"></span>
        <span>正在结合「<b>{{ profileHint }}</b>」的档案提问</span>
        <button type="button" @click="clearProfile">不用档案</button>
      </div>
      <MessageList
        ref="messageList"
        :messages="messages"
        :streaming="streaming"
        :terms="terms"
        :has-earlier="hasEarlierMessages"
        :suggests="suggests"
        @fav="fav"
        @earlier="loadEarlierMessages"
        @suggest="ask"
      />
      <Composer ref="composer" :streaming="streaming" @send="onSend" @stop="stop" />
    </section>

    <p class="live-status" aria-live="polite" aria-atomic="true">{{ announcement }}</p>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { createSession, deleteSession, listMessages, listSessions, renameSession } from '@/api/chat'
import { listProfiles } from '@/api/health'
import { addFavorite } from '@/api/favorites'
import { useTerms } from '@/composables/useTerms'
import { useChatStream } from '@/composables/useChatStream'
import type { ChatMessage, ChatSession } from '@/api/types'
import SessionList from '@/components/chat/SessionList.vue'
import MessageList from '@/components/chat/MessageList.vue'
import Composer from '@/components/chat/Composer.vue'
import { ICONS } from '@/utils/icons'

const route = useRoute()
const router = useRouter()
const sessions = ref<ChatSession[]>([])
const sessionId = ref<number | null>(null)
const messages = ref<ChatMessage[]>([])
const profileId = ref<number | null>(null)
const profileHint = ref('')
import { SUGGESTIONS } from '@/utils/suggestions'
const suggests = SUGGESTIONS.chat
const composer = ref<InstanceType<typeof Composer>>()
const messageList = ref<InstanceType<typeof MessageList>>()
const sessionTrigger = ref<HTMLButtonElement>()
const sessionsOpen = ref(false)
const drawerMedia = window.matchMedia('(max-width: 900px)')
const narrowScreen = ref(drawerMedia.matches)
const activeSessionTitle = computed(
  () => sessions.value.find((session) => session.id === sessionId.value)?.title || '会话',
)

// 会话分页：侧栏只装最近一页，攒多了按「加载更多」往后翻
let sessionPage = 1
const hasMoreSessions = ref(false)
// 消息分页：第 1 页是最新一页，「加载更早」往前翻
let messagePage = 1
const hasEarlierMessages = ref(false)

const { terms, loadTerms } = useTerms()
const { streaming, announcement, send, stop } = useChatStream(messages, ensureSession, reloadSessions)

onMounted(async () => {
  window.addEventListener('keydown', onWindowKeydown)
  drawerMedia.addEventListener('change', onDrawerMediaChange)
  await Promise.all([reloadSessions(), loadTerms()])
  const pid = Number(route.query.profileId)
  if (pid) {
    profileId.value = pid
    try {
      const ps = (await listProfiles()).data || []
      profileHint.value = ps.find((p) => p.id === pid)?.displayName || '家人'
    } catch {
      profileHint.value = '家人'
    }
  }
  const sid = Number(route.query.sid)
  if (sid) await openSession(sid)
  const preset = typeof route.query.q === 'string' ? route.query.q.trim() : ''
  if (preset) {
    const q: Record<string, string> = {}
    if (sid) q.sid = String(sid)
    if (pid) q.profileId = String(pid)
    router.replace({ path: '/chat', query: q })
    ask(preset)
  }
  await handleRouteIntent()
})

onBeforeUnmount(() => {
  window.removeEventListener('keydown', onWindowKeydown)
  drawerMedia.removeEventListener('change', onDrawerMediaChange)
})

watch(
  () => [route.query.new, route.query.photo],
  ([nextNew, nextPhoto], [previousNew, previousPhoto]) => {
    if ((nextNew === '1' && nextNew !== previousNew) || (nextPhoto === '1' && nextPhoto !== previousPhoto)) {
      void handleRouteIntent()
    }
  },
)

async function reloadSessions() {
  const res = await listSessions(1)
  sessions.value = res.data?.records || []
  hasMoreSessions.value = sessions.value.length < (res.data?.total || 0)
  sessionPage = 1
}

async function loadMoreSessions() {
  const res = await listSessions(sessionPage + 1)
  const records = res.data?.records || []
  sessions.value.push(...records)
  sessionPage += 1
  hasMoreSessions.value = sessions.value.length < (res.data?.total || 0)
}

/** 无会话时由流式状态机回调创建；返回会话 id。 */
async function ensureSession(firstTitle: string) {
  if (sessionId.value) return sessionId.value
  const created = await createSession(firstTitle)
  sessions.value.unshift(created.data)
  sessionId.value = created.data.id
  messages.value = []
  return created.data.id
}

async function newSession() {
  if (sessionsOpen.value) await closeSessions(true)
  const created = await createSession()
  sessions.value.unshift(created.data)
  sessionId.value = created.data.id
  messages.value = []
  hasEarlierMessages.value = false
}

async function openSession(id: number) {
  if (sessionsOpen.value) await closeSessions(true)
  sessionId.value = id
  messagePage = 1
  const res = await listMessages(id, 1)
  messages.value = res.data?.records || []
  hasEarlierMessages.value = messages.value.length < (res.data?.total || 0)
  await messageList.value?.scrollToBottom()
}

async function loadEarlierMessages() {
  if (!sessionId.value) return
  const res = await listMessages(sessionId.value, messagePage + 1)
  const records = res.data?.records || []
  // 后端按页返回时间正序块，向前拼接
  messages.value = [...records, ...messages.value]
  messagePage += 1
  hasEarlierMessages.value = messages.value.length < (res.data?.total || 0)
}

async function removeSession(id: number) {
  await deleteSession(id)
  if (sessionId.value === id) {
    sessionId.value = null
    messages.value = []
  }
  await reloadSessions()
}

async function renameSessionById(id: number, title: string) {
  await renameSession(id, title)
  await reloadSessions()
}

function onSend(text: string, files: File[]) {
  send(text, files, profileId.value)
}

function ask(q: string) {
  composer.value?.prefill(q)
}

function clearProfile() {
  profileId.value = null
  profileHint.value = ''
  const q = { ...route.query }
  delete q.profileId
  router.replace({ path: '/chat', query: q })
}

let handlingRouteIntent = false

async function handleRouteIntent() {
  if (handlingRouteIntent) return
  handlingRouteIntent = true
  try {
    const query = { ...route.query }
    if (route.query.new === '1') {
      await newSession()
      delete query.new
    }
    if (route.query.photo === '1') {
      ElMessage.info('点输入框下方的「图片」或「拍照」，可发送化验单、药盒或患处照片')
      delete query.photo
    }
    if (route.query.new === '1' || route.query.photo === '1') {
      await router.replace({ path: '/chat', query })
    }
  } finally {
    handlingRouteIntent = false
  }
}

function openSessions() {
  sessionsOpen.value = true
}

async function closeSessions(returnFocus = false) {
  sessionsOpen.value = false
  if (returnFocus) {
    await nextTick()
    sessionTrigger.value?.focus()
  }
}

function onWindowKeydown(event: KeyboardEvent) {
  if (!sessionsOpen.value || !narrowScreen.value) return
  if (event.key === 'Escape') {
    closeSessions(true)
    return
  }
  if (event.key !== 'Tab') return

  const drawer = document.getElementById('chat-session-drawer')
  if (!drawer) return
  const focusable = [...drawer.querySelectorAll<HTMLElement>('button:not(:disabled), input:not(:disabled)')]
  if (!focusable.length) return
  const first = focusable[0]
  const last = focusable[focusable.length - 1]
  const active = document.activeElement
  if (event.shiftKey && (active === first || !drawer.contains(active))) {
    event.preventDefault()
    last.focus()
  } else if (!event.shiftKey && active === last) {
    event.preventDefault()
    first.focus()
  }
}

function onDrawerMediaChange(event: MediaQueryListEvent) {
  narrowScreen.value = event.matches
  if (!event.matches) sessionsOpen.value = false
}

async function fav(messageId: number) {
  await addFavorite(messageId)
  ElMessage.success('已收藏')
}
</script>

<style scoped>
.chat {
  display: grid;
  grid-template-columns: 248px 1fr;
  gap: 16px;
  height: 100%;
  min-height: 0;
}

.mobile-chatbar,
.session-scrim {
  display: none;
}

.live-status {
  position: fixed;
  width: 1px;
  height: 1px;
  padding: 0;
  margin: -1px;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
  white-space: nowrap;
  border: 0;
}

/* 消息区不加背景面：让对话直接落在页面底色上，
   气泡自己是浮起的构件。少一层嵌套，信息密度更高。 */
.board {
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-height: 0;
  overflow: hidden;
}

.profile-bar {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  padding: var(--space-2) var(--space-4);
  border-radius: var(--r-card);
  background: var(--accent-wash);
  border: 1px solid var(--accent-line);
  color: var(--accent);
  font-size: 13px;
  flex-shrink: 0;
}

.profile-bar :deep(svg) {
  width: 16px;
  height: 16px;
  flex-shrink: 0;
}

.profile-bar b {
  font-weight: 600;
}

.profile-bar button {
  margin-left: auto;
  border: 0;
  background: none;
  color: var(--ink-faint);
  cursor: pointer;
  font-size: 13px;
  padding: 2px 6px;
  border-radius: var(--r-chip);
  transition: color 0.15s var(--ease-soft);
}

.profile-bar button:hover {
  color: var(--ink);
}

@media (max-width: 900px) {
  .chat {
    grid-template-columns: 1fr;
    gap: 12px;
  }
  .mobile-chatbar {
    display: flex;
    flex-shrink: 0;
  }

  .session-trigger {
    max-width: min(280px, 76vw);
  }

  .session-trigger-label {
    min-width: 0;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .chat :deep(.sessions) {
    position: fixed;
    inset: var(--topbar-h) auto 0 0;
    z-index: var(--z-modal);
    width: min(320px, 88vw);
    padding: var(--space-4);
    border-right: 1px solid var(--edge);
    background: var(--paper-2);
    box-shadow: var(--shadow-4);
    transform: translateX(-105%);
    visibility: hidden;
    transition: transform 0.4s var(--ease), visibility 0s 0.4s;
  }

  .chat :deep(.sessions.open) {
    transform: none;
    visibility: visible;
    transition: transform 0.4s var(--ease), visibility 0s;
  }

  .session-scrim {
    display: block;
    position: fixed;
    inset: var(--topbar-h) 0 0;
    z-index: var(--z-grain);
    width: auto;
    height: auto;
    padding: 0;
    border: 0;
    background: color-mix(in srgb, var(--ink) 42%, transparent);
    cursor: default;
  }
}

:global(html.dark) .session-scrim {
  background: color-mix(in srgb, var(--paper) 72%, transparent);
}
</style>
