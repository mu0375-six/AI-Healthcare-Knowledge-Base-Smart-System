<template>
  <div class="chat">
    <SessionList
      :sessions="sessions"
      :active-id="sessionId"
      :has-more="hasMoreSessions"
      @new="newSession"
      @open="openSession"
      @remove="removeSession"
      @rename="renameSessionById"
      @more="loadMoreSessions"
    />

    <section class="board">
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
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
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

// 会话分页：侧栏只装最近一页，攒多了按「加载更多」往后翻
let sessionPage = 1
const hasMoreSessions = ref(false)
// 消息分页：第 1 页是最新一页，「加载更早」往前翻
let messagePage = 1
const hasEarlierMessages = ref(false)

const { terms, loadTerms } = useTerms()
const { streaming, send, stop } = useChatStream(messages, ensureSession, reloadSessions)

onMounted(async () => {
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
  // 首页「发照片问医生」入口：给一个明确的引导，浏览器不允许无手势自动拉起文件选择器
  if (route.query.photo === '1') {
    router.replace({ path: '/chat', query: route.query.q ? { q: String(route.query.q) } : {} })
    ElMessage.info('点输入框下方的「图片」或「拍照」上传化验单、药盒或患处照片，再描述想了解什么')
  }
})

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
  const created = await createSession()
  sessions.value.unshift(created.data)
  sessionId.value = created.data.id
  messages.value = []
  hasEarlierMessages.value = false
}

async function openSession(id: number) {
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
  gap: 9px;
  padding: 9px 14px;
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
  transition: color 0.15s ease;
}

.profile-bar button:hover {
  color: var(--ink);
}

@media (max-width: 900px) {
  .chat {
    grid-template-columns: 1fr;
    gap: 12px;
  }
  /* 窄屏把会话列表压成一条可横滚的矮条，不占掉半屏 */
  .chat :deep(.sessions) {
    max-height: 152px;
  }
}
</style>
