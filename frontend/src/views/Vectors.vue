<template>
  <div class="page">
    <PageHeader kicker="Vectors" title="医学知识向量检索" desc="切片向量写入 Milvus（连不上则回退内存）。先看库状态，再输入症状或药名试检索。">
      <template #extra>
        <button v-if="user.isAdmin" class="btn btn-ghost" type="button" :disabled="reindexing" @click="reindex">
          {{ reindexing ? '重建中…' : '重建索引' }}
        </button>
      </template>
    </PageHeader>

    <div class="stats">
      <div class="stat tile">
        <span>向量库</span>
        <em>{{ milvusServing ? 'Milvus' : '内存' }}</em>
      </div>
      <div class="stat tile">
        <span>Milvus</span>
        <em :class="milvusServing ? 'ok' : ''">{{ milvusServing ? '已连接' : '未接入' }}</em>
      </div>
      <div class="stat tile">
        <span>向量条数</span>
        <em>{{ store.count }}</em>
      </div>
      <div class="stat tile">
        <span>维度</span>
        <em>{{ store.dim || 256 }}</em>
      </div>
    </div>
    <p class="detail">{{ store.detail }}{{ store.collection ? ' · 集合 ' + store.collection : '' }}</p>

    <section class="shell">
      <form class="ask" @submit.prevent="run">
        <input v-model="q" placeholder="例如：二甲双胍注意事项 / 高血压饮食" />
        <button class="btn btn-primary" type="submit" :disabled="loading || !q.trim()">{{ loading ? '检索中…' : '检索' }}</button>
      </form>
      <div class="chips">
        <button v-for="s in suggests" :key="s" type="button" @click="q = s; run()">{{ s }}</button>
      </div>
      <p v-if="inspect" class="meta">耗时 {{ inspect.elapsedMs }} ms · ANN 召回 {{ inspect.rawHits.length }} · 词项过滤后 {{ inspect.keptHits.length }}</p>
    </section>

    <div v-if="inspect" class="grid">
      <section class="shell">
        <h3>相似度 Top-K</h3>
        <v-chart v-if="barOption" :option="barOption" autoresize style="height: 280px" />
      </section>
      <section class="shell">
        <h3>分类分布</h3>
        <v-chart v-if="pieOption" :option="pieOption" autoresize style="height: 280px" />
      </section>
    </div>

    <section v-if="inspect" class="panel core-pad block">
      <h3>召回切片</h3>
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
import { CHART_COLORS, chartTheme } from '@/utils/charts'
import { SUGGESTIONS } from '@/utils/suggestions'
import PageHeader from '@/components/PageHeader.vue'

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
        itemStyle: { color: CHART_COLORS[0] },
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
        color: CHART_COLORS,
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
.stats {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 10px;
  margin-bottom: 8px;
}
.stat {
  padding: 14px;
}
.stat span {
  color: var(--ink-faint);
  font-size: 12px;
}
.stat em {
  display: block;
  font-style: normal;
  font-family: var(--font);
  font-size: 22px;
  margin-top: 4px;
}
.stat em.ok {
  color: var(--flag-normal);
}
.detail {
  color: var(--ink-faint);
  font-size: 13px;
  margin: 0 0 16px;
}
.ask {
  display: flex;
  gap: 8px;
}
.ask input {
  flex: 1;
  border: 1px solid var(--edge-strong);
  border-radius: 999px;
  padding: 10px 16px;
  background: var(--field);
  font-size: 14px;
}
.chips {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 12px;
}
.chips button {
  border: 1px solid var(--edge-strong);
  background: var(--field);
  border-radius: 999px;
  padding: 5px 12px;
  cursor: pointer;
}
.meta,
.hint {
  color: var(--ink-faint);
  font-size: 13px;
}
.grid {
  display: grid;
  grid-template-columns: 1.2fr 0.8fr;
  gap: 14px;
  margin-top: 14px;
}
.block {
  margin-top: 14px;
}
h3 {
  margin: 0 0 10px;
  font-size: 18px;
}
.hit {
  border-top: 1px solid var(--edge);
  padding: 12px 0;
}
.hit.keep {
  background: linear-gradient(90deg, var(--accent-wash), transparent);
}
.hit-top {
  display: flex;
  justify-content: space-between;
  gap: 12px;
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
  .stats,
  .grid {
    grid-template-columns: 1fr 1fr;
  }
}
</style>
