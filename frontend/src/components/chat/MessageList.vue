<template>
  <div ref="scroller" class="messages" aria-label="问诊消息">
    <div class="timeline">
      <button
        v-if="hasEarlier && messages.length"
        class="btn btn-quiet btn-sm earlier"
        type="button"
        @click="$emit('earlier')"
      >
        加载更早消息
      </button>

      <div v-if="!messages.length" class="blank">
        <div class="blank-heading">
          <span class="blank-icon" v-html="ICONS.spark"></span>
          <span>
            <small>开始问诊</small>
            <h2>今天想了解哪方面的健康问题？</h2>
          </span>
        </div>
        <p class="blank-copy">请说明症状出现的时间、位置和变化，或直接发送化验单、药盒及患处照片。</p>
        <div class="quick-start">
          <span class="quick-label">常用提问</span>
          <div class="suggestion-list">
            <button v-for="s in suggests" :key="s" type="button" @click="$emit('suggest', s)">
              <span>{{ s }}</span>
              <span class="suggest-arrow" v-html="ICONS.arrow"></span>
            </button>
          </div>
        </div>
        <router-link class="to-triage" to="/triage">
          <span class="triage-icon" v-html="ICONS.compass"></span>
          <span>
            <b>不确定该挂哪个科室？</b>
            <i>前往科室导诊，查看紧急程度与建议科室</i>
          </span>
          <span class="triage-arrow" v-html="ICONS.arrow"></span>
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
          <span v-html="renderMarkdown(m.content, terms)"></span><span
            v-if="streaming && m === lastMsg"
            class="caret"
            aria-hidden="true"
          ></span>
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
  min-height: 0;
  background: color-mix(in srgb, var(--paper) 28%, var(--card));
  scrollbar-gutter: stable;
}

.timeline {
  width: min(100%, 820px);
  min-height: 100%;
  margin: 0 auto;
  padding: var(--space-6) var(--space-6) var(--space-5);
}

.earlier {
  display: block;
  margin: 0 auto var(--space-4);
}

.blank {
  max-width: 680px;
  padding-top: clamp(var(--space-4), 7vh, var(--space-7));
}

.blank-heading {
  display: flex;
  align-items: center;
  gap: var(--space-3);
}

.blank-icon {
  display: grid;
  place-items: center;
  width: 38px;
  height: 38px;
  border-radius: var(--r-control);
  background: var(--accent);
  color: var(--on-accent);
  flex-shrink: 0;
}

.blank-icon :deep(svg) {
  width: 18px;
  height: 18px;
}

.blank-heading small {
  display: block;
  margin-bottom: 2px;
  color: var(--accent);
  font-size: 11.5px;
  font-weight: 600;
}

.blank h2 {
  margin: 0;
  color: var(--ink);
  font-size: 22px;
  line-height: 1.35;
  text-wrap: balance;
}

.blank-copy {
  max-width: 42em;
  margin: var(--space-4) 0 0 50px;
  color: var(--ink-mute);
  font-size: 14px;
  line-height: 1.7;
}

.quick-start {
  margin-top: var(--space-6);
}

.quick-label {
  display: block;
  margin-bottom: var(--space-2);
  color: var(--ink-faint);
  font-size: 11.5px;
  font-weight: 600;
}

.suggestion-list {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  border-top: 1px solid var(--edge);
}

.suggestion-list button {
  min-width: 0;
  min-height: 48px;
  display: flex;
  align-items: center;
  gap: var(--space-3);
  padding: var(--space-2) var(--space-3);
  border: 0;
  border-bottom: 1px solid var(--edge);
  background: transparent;
  color: var(--ink-soft);
  cursor: pointer;
  font-size: 13px;
  line-height: 1.45;
  text-align: left;
  transition: background 0.15s var(--ease-soft), color 0.15s var(--ease-soft);
}

.suggestion-list button:nth-child(odd) {
  border-right: 1px solid var(--edge);
}

.suggestion-list button:hover,
.suggestion-list button:focus-visible {
  background: var(--accent-wash);
  color: var(--accent);
}

.suggestion-list button > span:first-child {
  min-width: 0;
  flex: 1;
}

.suggest-arrow,
.triage-arrow {
  display: grid;
  place-items: center;
  color: var(--ink-faint);
  flex-shrink: 0;
}

.suggest-arrow :deep(svg),
.triage-arrow :deep(svg) {
  width: 16px;
  height: 16px;
}

.to-triage {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  margin-top: var(--space-5);
  padding: var(--space-3);
  border-block: 1px solid var(--accent-line);
  background: var(--accent-wash);
  color: var(--ink);
  transition: background 0.15s var(--ease-soft);
}

.to-triage:hover {
  background: color-mix(in srgb, var(--accent-wash) 72%, var(--card));
}

.triage-icon {
  display: grid;
  place-items: center;
  width: 32px;
  height: 32px;
  border: 1px solid var(--accent-line);
  border-radius: var(--r-control);
  color: var(--accent);
  flex-shrink: 0;
}

.triage-icon :deep(svg) {
  width: 17px;
  height: 17px;
}

.to-triage > span:nth-child(2) {
  min-width: 0;
  flex: 1;
}

.to-triage b {
  display: block;
  font-size: 13px;
  font-weight: 600;
}

.to-triage i {
  display: block;
  font-style: normal;
  font-size: 11.5px;
  color: var(--ink-faint);
  margin-top: 2px;
}

.bubble {
  width: 100%;
  padding: var(--space-5) 0;
  border-bottom: 1px solid var(--edge);
  animation: bubble-in 180ms var(--ease-out) backwards;
}

@keyframes bubble-in {
  from {
    opacity: 0;
    transform: translateY(8px);
  }
}

.bubble.user {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
}

.who {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  font-size: 12px;
  font-weight: 550;
  color: var(--ink-faint);
  margin-bottom: var(--space-2);
}

.bubble.user .who {
  flex-direction: row-reverse;
}

.who-av {
  display: grid;
  place-items: center;
  width: 22px;
  height: 22px;
  border-radius: var(--r-chip);
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
  color: var(--ink);
}

.bubble.assistant .answer {
  max-width: 68ch;
  margin-left: 30px;
}

.bubble.user .said {
  max-width: min(72%, 36em);
  padding: var(--space-3) var(--space-4);
  border: 1px solid var(--accent-line);
  border-radius: var(--r-card) 2px var(--r-card) var(--r-card);
  background: var(--accent-wash);
  white-space: pre-wrap;
  word-break: break-word;
}

.pics {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
  margin-bottom: var(--space-2);
  justify-content: flex-end;
}

.caret {
  display: inline;
}

.caret::after {
  content: '';
  display: inline-block;
  width: 2px;
  height: 1em;
  margin-left: 3px;
  vertical-align: -0.12em;
  background: var(--accent);
  animation: blink 1s steps(1) infinite;
}

@keyframes blink {
  50% {
    opacity: 0;
  }
}

.fav {
  margin-top: var(--space-3);
  margin-left: 30px;
  color: var(--ink-faint);
  transition: color 0.15s var(--ease-soft), background 0.15s var(--ease-soft);
}

.bubble:hover .fav,
.fav:focus-visible {
  color: var(--ink-soft);
}

@media (max-width: 720px) {
  .timeline {
    padding: var(--space-5) var(--space-4) var(--space-4);
  }

  .blank {
    padding-top: var(--space-4);
  }

  .blank-heading {
    align-items: flex-start;
  }

  .blank h2 {
    font-size: 19px;
  }

  .blank-copy {
    margin-left: 0;
  }

  .suggestion-list {
    grid-template-columns: 1fr;
  }

  .suggestion-list button:nth-child(odd) {
    border-right: 0;
  }

  .bubble.user .said {
    max-width: 88%;
  }

  .bubble.assistant .answer {
    margin-left: 0;
  }

  .fav {
    margin-left: 0;
  }
}

@media (prefers-reduced-motion: reduce) {
  .bubble,
  .caret::after {
    animation: none;
  }
}
</style>
