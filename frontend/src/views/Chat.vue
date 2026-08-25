<template>
  <div class="chat">
    <aside class="sessions">
      <button class="ink-btn full" type="button" @click="newSession">新对话</button>
      <div class="sess-list">
        <div v-if="!sessions.length" class="quiet">还没有会话</div>
        <button
          v-for="s in sessions"
          :key="s.id"
          class="sess"
          :class="{ active: s.id === sessionId }"
          type="button"
          @click="openSession(s.id)"
        >
          <span class="sess-main">
            <b>{{ s.title || '未命名会话' }}</b>
            <time>{{ formatWhen(s.updatedAt) }}</time>
          </span>
          <em @click.stop="removeSession(s.id)">删除</em>
        </button>
      </div>
    </aside>

    <section class="board">
      <div v-if="profileHint" class="profile-bar">
        正在结合「{{ profileHint }}」的档案提问
        <button type="button" @click="clearProfile">不用档案</button>
      </div>
      <div ref="scroller" class="messages">
        <div v-if="!messages.length" class="blank">
          <h3>把症状、药品或检查，说具体一点。</h3>
          <p>回答会尽量口语，用药与疾病会写得更专业，并标出知识库出处。也可以直接发检查单、药盒或患处照片。</p>
          <div class="chips">
            <button v-for="s in suggests" :key="s" type="button" @click="question = s; send()">{{ s }}</button>
          </div>
        </div>
        <article v-for="m in messages" :key="m.id" class="bubble" :class="m.role">
          <div class="who">{{ m.role === 'user' ? '我' : '康识助手' }}</div>
          <div v-if="m.role === 'user'" class="pics">
            <ChatPhoto v-for="(p, i) in m.localPreviews || []" :key="'p' + i" :src="p" alt="发送的图片" />
            <ChatPhoto v-for="a in attachmentsOf(m)" :key="a.id" :id="a.id" :alt="a.filename" />
          </div>
          <div v-if="m.role === 'user'" class="plain">{{ m.content }}</div>
          <div v-else class="markdown-body" v-html="renderMarkdown(m.content, terms)"></div>
          <button
            v-if="m.role === 'assistant' && m.id > 0 && !streaming"
            class="ghost-btn slim"
            type="button"
            @click="fav(m.id)"
          >
            收藏该回答
          </button>
        </article>
      </div>
      <div class="composer" @paste="onPaste" @dragover.prevent @drop.prevent="onDrop">
        <div v-if="pending.length" class="pending">
          <div v-for="(p, i) in pending" :key="p.key" class="thumb">
            <img :src="p.preview" alt="" />
            <button type="button" @click="removePending(i)">×</button>
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
            <label class="ghost-btn slim attach">
              图片
              <input type="file" accept="image/png,image/jpeg,image/jpg,image/webp,image/gif" multiple hidden @change="onPick" />
            </label>
            <span>仅供科普，不能替代面诊</span>
          </div>
          <button class="copper-btn" type="button" :disabled="streaming || (!question.trim() && !pending.length)" @click="send">
            {{ streaming ? '回答中…' : '发送' }}
          </button>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { nextTick, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { createSession, deleteSession, listMessages, listSessions, uploadChatImage } from '@/api/chat'
import { listProfiles } from '@/api/health'
import { askStream } from '@/api/sse'
import { addFavorite } from '@/api/favorites'
import { listTerms } from '@/api/knowledge'
import type { ChatAttachment, ChatMessage, ChatSession } from '@/api/types'
import { parseAttachments, renderMarkdown } from '@/utils/markdown'
import { formatWhen } from '@/utils/format'
import ChatPhoto from '@/components/ChatPhoto.vue'

const route = useRoute()
const router = useRouter()
const sessions = ref<ChatSession[]>([])
const sessionId = ref<number | null>(null)
const messages = ref<ChatMessage[]>([])
const question = ref('')
const streaming = ref(false)
const terms = ref<string[]>([])
const scroller = ref<HTMLElement>()
const pending = ref<{ key: number; file: File; preview: string }[]>([])
const profileId = ref<number | null>(null)
const profileHint = ref('')
const suggests = ['嗓子痛三天了怎么办', '二甲双胍有哪些注意事项', '空腹血糖 6.8 算高吗']

onMounted(async () => {
  await reloadSessions()
  try {
    terms.value = (await listTerms()).data || []
  } catch {
    terms.value = ['高血压', '糖尿病', '二甲双胍', '阿司匹林', '氨氯地平']
  }
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
    question.value = preset
    const q: Record<string, string> = {}
    if (sid) q.sid = String(sid)
    if (pid) q.profileId = String(pid)
    router.replace({ path: '/chat', query: q })
    await send()
  }
})

function clearProfile() {
  profileId.value = null
  profileHint.value = ''
  const q = { ...route.query }
  delete q.profileId
  router.replace({ path: '/chat', query: q })
}

async function reloadSessions() {
  sessions.value = (await listSessions()).data || []
}

async function newSession() {
  const res = await createSession()
  sessions.value.unshift(res.data)
  sessionId.value = res.data.id
  messages.value = []
}

async function openSession(id: number) {
  sessionId.value = id
  messages.value = (await listMessages(id)).data || []
  await scrollBottom()
}

async function removeSession(id: number) {
  await deleteSession(id)
  if (sessionId.value === id) {
    sessionId.value = null
    messages.value = []
  }
  await reloadSessions()
}

function attachmentsOf(m: ChatMessage): ChatAttachment[] {
  if (m.localPreviews && m.localPreviews.length) return []
  return parseAttachments(m.attachmentsJson)
}

function addFiles(files: File[]) {
  for (const file of files) {
    if (!file.type.startsWith('image/')) continue
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
  addFiles(Array.from(e.dataTransfer?.files || []))
}

function removePending(i: number) {
  URL.revokeObjectURL(pending.value[i].preview)
  pending.value.splice(i, 1)
}

function onKey(e: KeyboardEvent) {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    send()
  }
}

async function send() {
  const q = question.value.trim()
  const files = pending.value.map((p) => p.file)
  const previews = pending.value.map((p) => p.preview)
  if ((!q && !files.length) || streaming.value) return
  const display = q || '请看我发的图片，结合健康知识帮我解读。'
  question.value = ''
  pending.value = []
  if (!sessionId.value) {
    const created = await createSession(files.length ? '图片问诊' : display.slice(0, 24))
    sessions.value.unshift(created.data)
    sessionId.value = created.data.id
  }
  messages.value.push({
    id: -Date.now(),
    sessionId: sessionId.value!,
    userId: 0,
    role: 'user',
    content: display,
    localPreviews: previews,
    createdAt: '',
  })
  const bot: ChatMessage = {
    id: -Date.now() - 1,
    sessionId: sessionId.value!,
    userId: 0,
    role: 'assistant',
    content: '',
    createdAt: '',
  }
  messages.value.push(bot)
  const botMsg = messages.value[messages.value.length - 1]
  streaming.value = true
  await scrollBottom()
  try {
    const imageIds: number[] = []
    for (const file of files) {
      const up = await uploadChatImage(file)
      imageIds.push(up.data.id)
    }
    await askStream(
      { sessionId: sessionId.value, question: display, imageIds, profileId: profileId.value },
      {
        onMeta: (meta) => {
          sessionId.value = meta.sessionId
          botMsg.id = meta.messageId
        },
        onDelta: (chunk) => {
          botMsg.content += chunk
          scrollBottom()
        },
        onDone: (done) => {
          botMsg.id = done.messageId
          botMsg.content = done.fullContent
        },
        onError: (msg) => ElMessage.error(msg),
      },
    )
    await reloadSessions()
  } catch {
    botMsg.content = botMsg.content || '回答中断，请重试。'
  } finally {
    streaming.value = false
    await scrollBottom()
  }
}

async function fav(messageId: number) {
  await addFavorite(messageId)
  ElMessage.success('已收藏')
}

async function scrollBottom() {
  await nextTick()
  if (scroller.value) scroller.value.scrollTop = scroller.value.scrollHeight
}
</script>

<style scoped>
.chat {
  display: grid;
  grid-template-columns: 240px 1fr;
  gap: 16px;
  height: 100%;
  min-height: 0;
}
.sessions,
.board {
  background: var(--card);
  border: 1px solid var(--line);
  border-radius: var(--r-panel);
  min-height: 0;
  overflow: hidden;
}
.sessions {
  display: flex;
  flex-direction: column;
  padding: 14px;
}
.full {
  width: 100%;
  margin-bottom: 12px;
}
.sess-list {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
}
.quiet {
  color: var(--ink-3);
  font-size: 13px;
  padding: 16px 6px;
}
.sess-main {
  min-width: 0;
}
.sess-main b {
  display: block;
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.sess-main time {
  display: block;
  margin-top: 2px;
  font-size: 11px;
  opacity: 0.6;
}
.sess {
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  text-align: left;
  background: none;
  border: 0;
  border-radius: var(--r-control);
  padding: 10px 8px;
  cursor: pointer;
  color: var(--ink-2);
}
.sess span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 13px;
}
.sess em {
  font-style: normal;
  font-size: 12px;
  color: var(--ink-3);
  flex-shrink: 0;
  white-space: nowrap;
  opacity: 0;
  transition: opacity 0.15s ease;
}
.sess:hover em,
.sess.active em {
  opacity: 1;
}
.sess.active,
.sess:hover {
  background: rgba(196, 93, 58, 0.08);
}
.profile-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin: 0 12px 8px;
  padding: 8px 12px;
  border-radius: var(--r-control);
  background: rgba(44, 86, 72, 0.08);
  color: var(--moss);
  font-size: 13px;
}
.profile-bar button {
  border: 0;
  background: none;
  color: var(--ink-3);
  cursor: pointer;
}
.board {
  display: flex;
  flex-direction: column;
  padding: 8px 8px 12px;
}
.messages {
  flex: 1;
  overflow: auto;
  padding: 12px 18px;
}
.blank {
  max-width: 520px;
  padding: 28px 8px;
}
.blank h3 {
  margin: 0 0 8px;
  font-size: 26px;
}
.blank p {
  color: var(--ink-3);
  line-height: 1.7;
}
.chips {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 16px;
}
.chips button {
  border: 1px solid var(--line-strong);
  background: var(--cream);
  border-radius: 999px;
  padding: 7px 12px;
  cursor: pointer;
}
.bubble {
  margin: 14px 0;
  max-width: 82%;
}
.bubble.user {
  margin-left: auto;
}
.who {
  font-size: 12px;
  color: var(--ink-3);
  margin-bottom: 6px;
}
.plain,
.markdown-body {
  background: var(--paper);
  padding: 12px 14px;
  border-radius: var(--r-card);
}
.bubble.user .plain {
  background: #ead8cc;
  color: var(--ink);
}
.pics {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 8px;
}
.pending {
  display: flex;
  gap: 8px;
  margin-bottom: 8px;
}
.thumb {
  position: relative;
}
.thumb img {
  width: 72px;
  height: 72px;
  object-fit: cover;
  border-radius: var(--r-control);
}
.thumb button {
  position: absolute;
  top: -6px;
  right: -6px;
  width: 20px;
  height: 20px;
  border: 0;
  border-radius: 50%;
  background: var(--ink);
  color: #fff;
  cursor: pointer;
}
.tools {
  display: flex;
  align-items: center;
  gap: 10px;
}
.attach {
  cursor: pointer;
  margin-top: 0;
}
.slim {
  margin-top: 8px;
  padding: 4px 10px;
  font-size: 12px;
}
.composer {
  padding: 4px 12px 0;
}
.bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 8px;
  color: var(--ink-3);
  font-size: 12px;
}
@media (max-width: 860px) {
  .chat {
    grid-template-columns: 1fr;
  }
  .sessions {
    max-height: 180px;
  }
}
</style>
