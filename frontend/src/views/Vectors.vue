<template>
  <div class="page">
    <PageHeader kicker="Vectors" title="医学知识向量检索" desc="切片向量写入 Milvus（连不上则回退内存）。先看库状态，再输入症状或药名试检索。">
      <template #extra>
        <button v-if="user.isAdmin" class="btn btn-ghost" type="button" :disabled="reindexing" @click="reindex">
          {{ reindexing ? '重建中…' : '重建索引' }}
        </button>
      </template>
    </PageHeader>

    <section class="system-strip" aria-label="向量服务状态">
      <div class="system-state">
        <i :class="{ ok: milvusServing }"></i>
        <span><small>当前运行模式</small><strong>{{ milvusServing ? 'Milvus 在线检索' : '内存降级检索' }}</strong></span>
      </div>
      <div class="stat"><span>连接</span><em :class="milvusServing ? 'ok' : ''">{{ milvusServing ? '正常' : '未接入' }}</em></div>
      <div class="stat"><span>向量条数</span><em class="num">{{ store.count }}</em></div>
      <div class="stat"><span>向量维度</span><em class="num">{{ store.dim || 256 }}</em></div>
    </section>
    <div v-if="statusCopy.summary" class="status-detail">
      <p>{{ statusCopy.summary }}{{ store.collection ? ' · 集合 ' + store.collection : '' }}</p>
      <details v-if="statusCopy.technical">
        <summary>查看技术详情</summary>
        <pre>{{ statusCopy.technical }}</pre>
      </details>
    </div>

    <section class="search-console panel core-pad">
      <form class="ask" @submit.prevent="run">
        <input v-model="q" aria-label="向量检索内容" placeholder="例如：二甲双胍注意事项 / 高血压饮食" />
        <button class="btn btn-primary" type="submit" :disabled="loading || !q.trim()">{{ loading ? '检索中…' : '检索' }}</button>
      </form>
      <div class="chips">
        <button v-for="s in suggests" :key="s" class="chip-btn" type="button" @click="q = s; run()">{{ s }}</button>
      </div>
      <p v-if="inspect" class="meta">耗时 {{ inspect.elapsedMs }} ms · ANN 召回 {{ inspect.rawHits.length }} · 词项过滤后 {{ inspect.keptHits.length }}</p>
    </section>

    <section v-if="!inspect" class="panel empty vector-empty">
      <span v-html="ICONS.dots"></span>
      <h3>试一次知识召回</h3>
      <p>输入症状或药名，看看知识库会召回什么。</p>
    </section>

    <div v-if="inspect" class="grid">
      <Shell>
        <template #head><h3>相似度 Top-K</h3></template>
        <v-chart v-if="barOption" :option="barOption" autoresize style="height: 280px" />
      </Shell>
      <Shell>
        <template #head><h3>分类分布</h3></template>
        <v-chart v-if="pieOption" :option="pieOption" autoresize style="height: 280px" />
      </Shell>
    </div>

    <section v-if="inspect" class="panel core-pad block">
      <div class="section-head"><h3>召回切片</h3></div>
      <p class="hint">ANN 是向量近邻；词项命中表示标题/正文里出现了问题里的医学词。问答最终只用词项命中的条目。</p>
      <article v-for="h in inspect.rawHits" :key="h.chunkId" class="hit" :class="{ keep: h.lexicalHit }">
        <div class="hit-top">
          <b>{{ h.title }}</b>
          <span>{{ h.category }} · 相似度 {{ h.score }}</span>
        </div>
        <p>{{ h.snippet }}</p>
        <small>{{ h.lexicalHit ? '会进入问答上下文' : '仅向量相近，词项未命中，问答会丢掉' }} · {{ h.source }}</small>
      </article>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import VChart from 'vue-echarts'
import { inspectVectors, reindexVectors, vectorStatus, type VectorInspect, type VectorStoreInfo } from '@/api/knowledge'
import { useUserStore } from '@/stores/user'
import { chartTheme } from '@/utils/charts'
import { SUGGESTIONS } from '@/utils/suggestions'
import { ICONS } from '@/utils/icons'
import PageHeader from '@/components/PageHeader.vue'
import Shell from '@/components/Shell.vue'

const user = useUserStore()
const store = ref<VectorStoreInfo>({ backend: 'memory', connected: false, count: 0, dim: 256, collection: '', detail: '' })

// connected 表示「当前生效的库是通的」，降级到内存时它同样为 true。
// 判断 Milvus 是否真的在服务必须连 backend 一起看 —— 文字和高亮共用这一个来源。
// 「未接入」不区分关闭与连不上，具体原因由下面的 detail 一行说明。
const milvusServing = computed(() => store.value.backend === 'milvus' && store.value.connected)
const q = ref('二甲双胍注意事项')
const loading = ref(false)
const reindexing = ref(false)
const inspect = ref<VectorInspect | null>(null)
const suggests = SUGGESTIONS.vectors
const statusCopy = computed(() => {
  const detail = store.value.detail?.trim() || ''
  const marker = '原因：'
  const index = detail.indexOf(marker)
  if (index < 0) return { summary: detail, technical: '' }
  const summary = detail.slice(0, index).replace(/[；。\s]+$/, '') + '。'
  return { summary, technical: detail.slice(index + marker.length).trim() }
})

onMounted(async () => {
  store.value = (await vectorStatus()).data
})

async function run() {
  if (!q.value.trim()) return
  loading.value = true
  try {
    inspect.value = (await inspectVectors(q.value.trim())).data
    store.value = inspect.value.store
  } finally {
    loading.value = false
  }
}

async function reindex() {
  reindexing.value = true
  try {
    const d = (await reindexVectors()).data
    store.value = d.store
    ElMessage.success('已重建 ' + d.count + ' 条向量')
    if (q.value.trim()) await run()
  } finally {
    reindexing.value = false
  }
}

const barOption = computed(() => {
  void themeTick.value
  const t = chartTheme()
  const hits = inspect.value?.rawHits || []
  if (!hits.length) return null
  return {
    tooltip: { trigger: 'axis' },
    grid: { left: 120, right: 16, top: 16, bottom: 28 },
    xAxis: { type: 'value', max: 1, axisLabel: { color: t.label }, splitLine: t.splitLine },
    yAxis: {
      type: 'category',
      data: hits.map((h) => (h.title || '切片').slice(0, 12)).reverse(),
      axisLabel: { interval: 0, color: t.label },
      axisLine: t.axisLine,
    },
    series: [
      {
        type: 'bar',
        data: hits.map((h) => h.score).reverse(),
        itemStyle: { color: t.colors[0] },
      },
    ],
  }
})

const pieOption = computed(() => {
  void themeTick.value
  const t = chartTheme()
  const hits = inspect.value?.rawHits || []
  if (!hits.length) return null
  const map = new Map<string, number>()
  for (const h of hits) {
    const k = h.category || '未分类'
    map.set(k, (map.get(k) || 0) + 1)
  }
  return {
    tooltip: { trigger: 'item' },
    legend: { textStyle: { color: t.label } },
    series: [
      {
        type: 'pie',
        radius: ['42%', '68%'],
        data: Array.from(map.entries()).map(([name, value]) => ({ name, value })),
        color: t.colors,
      },
    ],
  }
})

// 暗色切换后重建配色（canvas 内文字不吃 CSS 变量，只能重算 option）
const themeTick = ref(0)
onMounted(() => window.addEventListener('theme-change', onThemeChange))
onBeforeUnmount(() => window.removeEventListener('theme-change', onThemeChange))
function onThemeChange() {
  themeTick.value++
}
</script>

<style scoped>
.system-strip {
  display: grid;
  grid-template-columns: minmax(260px, 1.5fr) repeat(3, minmax(120px, 0.7fr));
  border: 1px solid var(--edge);
  border-radius: var(--r-shell);
  background: var(--card);
  overflow: hidden;
  margin-bottom: var(--space-3);
}
.stat {
  display: grid;
  align-content: center;
  gap: 2px;
  min-height: 70px;
  padding: 12px 16px;
  border-left: 1px solid var(--edge);
}
.stat span {
  color: var(--ink-mute);
  font-size: 11px;
}
.stat em {
  font-style: normal;
  font-family: var(--font);
  color: var(--ink);
  font-size: 16px;
  font-weight: 650;
}
.stat em.ok {
  color: var(--flag-normal);
}
.system-state {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  min-height: 70px;
  padding: 12px 16px;
}
.system-state > i {
  width: 10px;
  height: 10px;
  border-radius: var(--r-pill);
  background: var(--flag-high);
  box-shadow: 0 0 0 4px var(--flag-high-wash);
}
.system-state > i.ok {
  background: var(--flag-normal);
  box-shadow: 0 0 0 4px var(--flag-normal-wash);
}
.system-state > span {
  display: grid;
  gap: 2px;
}
.system-state small {
  color: var(--ink-mute);
  font-size: 11px;
}
.system-state strong {
  color: var(--ink);
  font-size: 14px;
  font-weight: 650;
}
.status-detail {
  display: grid;
  gap: var(--space-2);
  color: var(--ink-faint);
  font-size: 13px;
  margin: 0 0 var(--space-4);
}
.status-detail summary {
  width: max-content;
  color: var(--ink-mute);
  cursor: pointer;
}
.status-detail pre {
  max-height: 148px;
  overflow: auto;
  padding: var(--space-3);
  border-radius: var(--r-chip);
  background: var(--sunk);
  color: var(--ink-mute);
  font-family: var(--font-mono);
  font-size: 11.5px;
  line-height: 1.6;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}
.ask {
  display: flex;
  gap: var(--space-2);
}
.ask input {
  flex: 1;
  min-width: 0;
  border: 1px solid var(--edge-strong);
  border-radius: var(--r-control);
  padding: 10px var(--space-4);
  background: var(--card);
  color: var(--ink);
  font-size: 14px;
  outline: none;
  transition: border-color 0.18s var(--ease-soft), box-shadow 0.3s var(--ease);
}
.ask input:focus {
  border-color: var(--accent-line);
  box-shadow: 0 0 0 3px var(--accent-wash);
}
.chips {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
  margin-top: var(--space-3);
}
.meta,
.hint {
  color: var(--ink-faint);
  font-size: 13px;
}
.grid {
  display: grid;
  grid-template-columns: 1.2fr 0.8fr;
  gap: var(--space-4);
  margin-top: var(--space-4);
}
.block {
  margin-top: var(--space-4);
}
.vector-empty {
  margin-top: var(--space-4);
}
.hit {
  border-top: 1px solid var(--edge);
  padding: var(--space-3) 0;
}
.hit.keep {
  margin-inline: calc(var(--space-3) * -1);
  padding-inline: var(--space-3);
  border-left: 3px solid var(--accent);
  background: var(--accent-wash);
}
.hit-top {
  display: flex;
  justify-content: space-between;
  gap: var(--space-3);
}
.hit p {
  margin: 6px 0 4px;
  color: var(--ink-soft);
  line-height: 1.6;
}
.hit small {
  color: var(--ink-faint);
}
@media (max-width: 900px) {
  .system-strip {
    grid-template-columns: 1fr 1fr;
  }

  .system-state {
    grid-column: 1 / -1;
  }

  .stat:nth-child(2) {
    border-left: 0;
  }

  .grid {
    grid-template-columns: 1fr 1fr;
  }
}

@media (max-width: 720px) {
  .system-strip {
    grid-template-columns: 1fr;
  }

  .system-state,
  .stat {
    grid-column: auto;
    border-left: 0;
    border-top: 1px solid var(--edge);
  }

  .system-state {
    border-top: 0;
  }

  .grid {
    grid-template-columns: 1fr;
  }
  .ask {
    align-items: stretch;
    flex-direction: column;
  }
}
</style>
