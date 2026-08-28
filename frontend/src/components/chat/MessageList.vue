<template>
  <div ref="scroller" class="messages">
    <button v-if="hasEarlier && messages.length" class="btn btn-quiet btn-sm earlier" type="button" @click="$emit('earlier')">
      加载更早消息
    </button>

    <!-- 空态是行动的邀请，不是一句"暂无数据" -->
    <div v-if="!messages.length" class="blank">
      <h2>把症状、药品或检查，说具体一点。</h2>
      <p>回答会尽量口语，用药与疾病写得更专业，并标出知识库出处。也可以直接发化验单、药盒或患处照片。</p>
      <div class="chips">
        <button v-for="s in suggests" :key="s" type="button" @click="$emit('suggest', s)">{{ s }}</button>
      </div>
      <router-link class="to-triage" to="/triage">
        <span v-html="ICONS.compass"></span>
        <span>
          <b>不知道该挂哪个科？</b>
          <i>填症状，给出可能科室与紧急程度</i>
        </span>
      </router-link>
    </div>

    <article v-for="m in messages" :key="m.id" class="bubble" :class="m.role">
      <div class="who">
        <span v-if="m.role === 'user'" class="who-av">我</span>
        <span v-else class="who-av bot" v-html="ICONS.spark"></span>
        <span>{{ m.role === 'user' ? '我' : '康识助手' }}</span>
      </div>

      <div v-if="m.role === 'user'" class="pics">
        <ChatPhoto v-for="(p, i) in m.localPreviews || []" :key="'p' + i" :src="p" alt="发送的图片" />
        <ChatPhoto v-for="a in attachmentsOf(m)" :key="a.id" :id="a.id" :alt="a.filename" />
      </div>

      <div v-if="m.role === 'user'" class="said">{{ m.content }}</div>
      <div v-else class="prose answer">
        <span v-html="renderMarkdown(m.content, terms)"></span><span v-if="streaming && m === lastMsg" class="caret">▍</span>
      </div>

      <button
        v-if="m.role === 'assistant' && m.id > 0 && !streaming"
        class="btn btn-quiet btn-sm fav"
        type="button"
        @click="$emit('fav', m.id)"
      >
        <span v-html="ICONS.star"></span>收藏该回答
      </button>
    </article>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import type { ChatMessage } from '@/api/types'
import { parseAttachments, renderMarkdown } from '@/utils/markdown'
import ChatPhoto from '@/components/ChatPhoto.vue'
import { ICONS } from '@/utils/icons'

const props = defineProps<{
  messages: ChatMessage[]
  streaming: boolean
  terms: string[]
  hasEarlier: boolean
  suggests?: readonly string[]
}>()

defineEmits<{
  fav: [messageId: number]
  earlier: []
  suggest: [q: string]
}>()

const scroller = ref<HTMLElement>()
const lastMsg = computed(() => props.messages[props.messages.length - 1])

function attachmentsOf(m: ChatMessage) {
  if (m.localPreviews && m.localPreviews.length) return []
  return parseAttachments(m.attachmentsJson)
}

// 流式增量与新消息到达时自动贴底（沿用拆分前行为）
watch(
  () => [props.messages.length, props.messages[props.messages.length - 1]?.content],
  async () => {
    await nextTick()
    if (scroller.value) scroller.value.scrollTop = scroller.value.scrollHeight
  },
)

defineExpose({
  async scrollToBottom() {
    await nextTick()
    if (scroller.value) scroller.value.scrollTop = scroller.value.scrollHeight
  },
})
</script>

<style scoped>
.messages {
  flex: 1;
  overflow: auto;
  padding: 16px 4px 8px;
}

.earlier {
  display: block;
  margin: 0 auto 10px;
}

/* ---- 空态 ---- */
.blank {
  max-width: 560px;
  padding: 24px 4px;
}

.blank h2 {
  margin-bottom: 10px;
  /* 不设 max-width：18ch 会把这句话折成"把症状、药品或检 / 查，说具体一点"，
     断在词中间。让 text-wrap: balance 自己找断点。 */
  text-wrap: balance;
}

.blank p {
  color: var(--ink-mute);
  line-height: 1.75;
  max-width: 30em;
}

.chips {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 18px;
}

.chips button {
  border: 1px solid var(--edge-strong);
  background: var(--card);
  color: var(--ink-soft);
  border-radius: 999px;
  padding: 7px 14px;
  font-size: 13px;
  cursor: pointer;
  transition: color 0.15s ease, border-color 0.15s ease, background 0.15s ease,
    transform 0.12s var(--ease-out);
}

.chips button:active {
  transform: scale(0.96);
}

@media (hover: hover) and (pointer: fine) {
  .chips button:hover {
    color: var(--accent);
    border-color: var(--accent-line);
    background: var(--accent-wash);
  }
}

/* 导诊入口放在空态里：科室导诊已从顶级导航降级，
   而"不知道挂哪科"恰好是问诊页最常见的相邻需求。 */
.to-triage {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 24px;
  padding: 14px 16px;
  border: 1px solid var(--edge);
  border-radius: var(--r-card);
  background: var(--card);
  box-shadow: var(--shadow-1);
  color: var(--ink);
  transition: border-color 0.15s ease, box-shadow 0.24s var(--ease-out),
    transform 0.24s var(--ease-out);
}

.to-triage:active {
  transform: scale(0.985);
}

@media (hover: hover) and (pointer: fine) {
  .to-triage:hover {
    border-color: var(--edge-strong);
    box-shadow: var(--shadow-2);
    transform: translateY(-2px);
  }
}

.to-triage :deep(svg) {
  width: 22px;
  height: 22px;
  color: var(--accent);
  flex-shrink: 0;
}

.to-triage b {
  display: block;
  font-size: 14px;
  font-weight: 600;
}

.to-triage i {
  display: block;
  font-style: normal;
  font-size: 12.5px;
  color: var(--ink-faint);
  margin-top: 2px;
}

/* ---- 气泡 ---- */
.bubble {
  margin: 18px 0;
  max-width: 84%;
  animation: bubble-in 300ms var(--ease-out) backwards;
}

@keyframes bubble-in {
  from {
    opacity: 0;
    transform: translateY(8px);
  }
}

.bubble.user {
  margin-left: auto;
}

.who {
  display: flex;
  align-items: center;
  gap: 7px;
  font-size: 12px;
  font-weight: 550;
  color: var(--ink-faint);
  margin-bottom: 7px;
}

.bubble.user .who {
  flex-direction: row-reverse;
}

.who-av {
  display: grid;
  place-items: center;
  width: 22px;
  height: 22px;
  border-radius: 7px;
  background: var(--flag-none-wash);
  color: var(--ink-mute);
  font-size: 11px;
  font-weight: 600;
  flex-shrink: 0;
}

.who-av.bot {
  background: var(--accent);
  color: var(--on-accent);
}

.who-av :deep(svg) {
  width: 13px;
  height: 13px;
}

.said,
.answer {
  background: var(--card);
  border: 1px solid var(--edge);
  box-shadow: var(--shadow-1);
  padding: 13px 16px;
  border-radius: var(--r-card);
}

/* 靠近发言人的那个角收紧：气泡"指向"说话的一方 */
.bubble.assistant .answer {
  border-top-left-radius: 4px;
  max-width: none;
}

.bubble.user .said {
  border-top-right-radius: 4px;
  background: var(--accent-wash);
  border-color: var(--accent-line);
  color: var(--ink);
  box-shadow: none;
  white-space: pre-wrap;
  word-break: break-word;
}

.pics {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 8px;
  justify-content: flex-end;
}

/* 流式生成的光标：只在最后一条还在输出时出现 */
.caret {
  color: var(--accent);
  animation: blink 1s steps(1) infinite;
}

@keyframes blink {
  50% {
    opacity: 0;
  }
}

/* 收藏按钮平时收起：每条回答都挂个按钮会把版面切碎 */
.fav {
  margin-top: 8px;
  opacity: 0;
  transition: opacity 0.15s ease, color 0.15s ease, background 0.15s ease;
}

.bubble:hover .fav,
.fav:focus-visible {
  opacity: 1;
}

@media (hover: none) {
  .fav {
    opacity: 1;
  }
}

@media (max-width: 720px) {
  .bubble {
    max-width: 94%;
  }
}
</style>
