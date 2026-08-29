<template>
  <div class="chat">
    <SessionList
      id="chat-session-drawer"
      :sessions="sessions"
      :active-id="sessionId"
      :has-more="hasMoreSessions"
      :loading="sessionsLoading"
      :error="sessionsError"
      :drawer-open="sessionsOpen && narrowScreen"
      :class="{ open: sessionsOpen }"
      @new="newSession"
      @open="openSession"
      @remove="removeSession"
      @rename="renameSessionById"
      @more="loadMoreSessions"
      @retry="reloadSessions"
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

    <section class="board" aria-label="健康问诊对话">
      <header class="workspace-head">
        <div class="desktop-chat-title">
          <span class="title-icon" v-html="ICONS.chat"></span>
          <span>
            <b>{{ activeSessionTitle }}</b>
            <small>健康知识库辅助问诊</small>
          </span>
        </div>
        <div class="mobile-chatbar">
          <button
            ref="sessionTrigger"
            class="session-trigger"
            type="button"
            aria-controls="chat-session-drawer"
            :aria-expanded="sessionsOpen && narrowScreen"
            @click="openSessions"
          >
            <span v-html="ICONS.chat"></span>
            <span class="session-trigger-copy">
              <b>{{ activeSessionTitle }}</b>
              <small>查看会话记录</small>
            </span>
            <span class="trigger-caret" v-html="ICONS.chevron"></span>
          </button>
        </div>
        <span class="service-state"><i aria-hidden="true"></i>知识库辅助</span>
      </header>
      <div v-if="profileHint" class="profile-bar">
        <span v-html="ICONS.file"></span>
        <span>正在结合「<b>{{ profileHint }}</b>」的档案提问</span>
        <button type="button" @click="clearProfile">移除关联</button>
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
const sessionsLoading = ref(true)
const sessionsError = ref('')
const sessionSwitching = ref(false)
const drawerMedia = window.matchMedia('(max-width: 900px)')
const narrowScreen = ref(drawerMedia.matches)
const activeSessionTitle = computed(
  () => sessions.value.find((session) => session.id === sessionId.value)?.title || '新问诊',
)

// 会话分页：侧栏只装最近一页，攒多了按「加载更多」往后翻
let sessionPage = 1
const hasMoreSessions = ref(false)
let sessionsRequest = 0
// 消息分页：第 1 页是最新一页，「加载更早」往前翻
let messagePage = 1
const hasEarlierMessages = ref(false)

const { terms, loadTerms } = useTerms()
const { streaming, announcement, send, stop } = useChatStream(messages, ensureSession, reloadSessions)

onMounted(async () => {
  window.addEventListener('keydown', onWindowKeydown)
  drawerMedia.addEventListener('change', onDrawerMediaChange)
  await Promise.allSettled([reloadSessions(), loadTerms()])
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
  const request = ++sessionsRequest
  sessionsLoading.value = true
  sessionsError.value = ''
  try {
    const res = await listSessions(1)
    if (request !== sessionsRequest) return
    sessions.value = res.data?.records || []
    hasMoreSessions.value = sessions.value.length < (res.data?.total || 0)
    sessionPage = 1
  } catch {
    if (request !== sessionsRequest) return
    sessionsError.value = '会话记录暂时无法读取'
  } finally {
    if (request === sessionsRequest) sessionsLoading.value = false
  }
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
  if (!canChangeSession()) return
  sessionSwitching.value = true
  ++openSessionRequest
  if (sessionsOpen.value) await closeSessions(true)
  try {
    const created = await createSession()
    sessions.value.unshift(created.data)
    sessionId.value = created.data.id
    messages.value = []
    hasEarlierMessages.value = false
  } finally {
    sessionSwitching.value = false
  }
}

let openSessionRequest = 0
async function openSession(id: number) {
  if (!canChangeSession()) return
  sessionSwitching.value = true
  if (sessionsOpen.value) await closeSessions(true)
  const request = ++openSessionRequest
  try {
    const res = await listMessages(id, 1)
    if (request !== openSessionRequest) return
    sessionId.value = id
    messagePage = 1
    messages.value = res.data?.records || []
    hasEarlierMessages.value = messages.value.length < (res.data?.total || 0)
    await messageList.value?.scrollToBottom()
  } catch {
    if (request === openSessionRequest) ElMessage.error('会话内容加载失败，已保留当前对话')
  } finally {
    if (request === openSessionRequest) sessionSwitching.value = false
  }
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
  if (!canChangeSession()) return
  sessionSwitching.value = true
  ++openSessionRequest
  try {
    await deleteSession(id)
    if (sessionId.value === id) {
      sessionId.value = null
      messages.value = []
    }
    await reloadSessions()
  } finally {
    sessionSwitching.value = false
  }
}

async function renameSessionById(id: number, title: string) {
  await renameSession(id, title)
  await reloadSessions()
}

async function onSend(text: string, files: File[]) {
  if (sessionSwitching.value) {
    ElMessage.info('会话切换完成后再发送')
    composer.value?.restore(text, files)
    return
  }
  const accepted = await send(text, files, profileId.value)
  if (!accepted) composer.value?.restore(text, files)
}

function canChangeSession() {
  if (streaming.value) {
    ElMessage.info('请先停止当前回答，再切换会话')
    return false
  }
  if (sessionSwitching.value) {
    ElMessage.info('正在切换会话，请稍候')
    return false
  }
  return true
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
  grid-template-columns: 264px minmax(0, 1fr);
  height: 100%;
  min-height: 0;
  overflow: hidden;
  border: 1px solid var(--edge);
  border-radius: var(--r-shell);
  background: var(--card);
  box-shadow: var(--shadow-1);
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

.board {
  display: flex;
  flex-direction: column;
  min-height: 0;
  overflow: hidden;
  background: var(--card);
}

.workspace-head {
  min-height: 60px;
  display: flex;
  align-items: center;
  gap: var(--space-4);
  padding: var(--space-2) var(--space-5);
  border-bottom: 1px solid var(--edge);
  flex-shrink: 0;
}

.desktop-chat-title {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  min-width: 0;
}

.desktop-chat-title > span:last-child {
  min-width: 0;
}

.desktop-chat-title b,
.session-trigger-copy b {
  display: block;
  color: var(--ink);
  font-size: 14px;
  font-weight: 650;
  line-height: 1.35;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.desktop-chat-title small,
.session-trigger-copy small {
  display: block;
  margin-top: 2px;
  color: var(--ink-faint);
  font-size: 11.5px;
  line-height: 1.3;
}

.title-icon {
  display: grid;
  place-items: center;
  width: 32px;
  height: 32px;
  border-radius: var(--r-control);
  background: var(--accent-wash);
  color: var(--accent);
  flex-shrink: 0;
}

.title-icon :deep(svg) {
  width: 18px;
  height: 18px;
}

.service-state {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  margin-left: auto;
  color: var(--ink-mute);
  font-size: 12px;
  white-space: nowrap;
}

.service-state i {
  width: 7px;
  height: 7px;
  border-radius: var(--r-pill);
  background: var(--info);
  box-shadow: 0 0 0 3px var(--info-wash);
}

.profile-bar {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  min-height: 40px;
  padding: var(--space-2) var(--space-5);
  background: var(--accent-wash);
  border-bottom: 1px solid var(--accent-line);
  color: var(--accent);
  font-size: 12.5px;
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
  border: 1px solid var(--accent-line);
  background: color-mix(in srgb, var(--card) 42%, transparent);
  color: var(--accent);
  cursor: pointer;
  font-size: 12px;
  padding: 4px 8px;
  border-radius: var(--r-chip);
  transition: background 0.15s var(--ease-soft);
}

.profile-bar button:hover {
  background: var(--card);
}

@media (max-width: 900px) {
  .chat {
    grid-template-columns: 1fr;
  }

  .workspace-head {
    min-height: 58px;
    padding: var(--space-2) var(--space-4);
  }

  .desktop-chat-title {
    display: none;
  }

  .mobile-chatbar {
    display: flex;
    min-width: 0;
    flex: 1;
  }

  .session-trigger {
    width: min(100%, 360px);
    min-width: 0;
    display: flex;
    align-items: center;
    gap: var(--space-2);
    padding: 3px 0;
    border: 0;
    background: transparent;
    color: var(--ink);
    cursor: pointer;
    text-align: left;
  }

  .session-trigger > :deep(svg) {
    width: 19px;
    height: 19px;
    color: var(--accent);
    flex-shrink: 0;
  }

  .session-trigger-copy {
    min-width: 0;
    flex: 1;
  }

  .trigger-caret {
    display: grid;
    place-items: center;
    flex-shrink: 0;
    color: var(--ink-faint);
  }

  .trigger-caret :deep(svg) {
    width: 16px;
    height: 16px;
  }

  .service-state {
    margin-left: var(--space-2);
    font-size: 0;
    gap: 0;
  }

  .profile-bar {
    padding-inline: var(--space-4);
  }

  .chat :deep(.sessions) {
    position: fixed;
    inset: var(--topbar-h) auto 0 0;
    z-index: var(--z-modal);
    width: min(320px, 88vw);
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

@media (max-width: 520px) {
  .chat {
    margin-inline: calc(var(--space-4) * -1);
    border-inline: 0;
    border-bottom: 0;
    border-radius: 0;
    box-shadow: none;
  }

  .profile-bar > span:nth-child(2) {
    min-width: 0;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}

:global(html.dark) .session-scrim {
  background: color-mix(in srgb, var(--paper) 72%, transparent);
}
</style>
