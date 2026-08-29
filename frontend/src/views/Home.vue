<template>
  <div class="page">
    <header class="workspace-head">
      <div class="workspace-copy">
        <span class="eyebrow"><span v-html="ICONS.pulse"></span>{{ today }}</span>
        <h1>今日健康工作台</h1>
        <p class="status-copy">
          <strong v-if="alerts.length">{{ alerts.length }} 项指标需要留意</strong>
          <strong v-else-if="hasAnyData">当前记录没有越过参考线</strong>
          <strong v-else>还没有可分析的健康记录</strong>
          <span>
            {{
              alerts.length
                ? '先查看偏离最明显的指标，再决定是否继续问诊。'
                : hasAnyData
                  ? '持续记录比单次结果更能说明变化。'
                  : '上传报告或记录指标，建立第一份可追踪档案。'
            }}
          </span>
        </p>
      </div>
      <div class="workspace-actions">
        <button class="btn btn-primary" type="button" @click="goAsk">
          <span v-html="ICONS.send"></span>开始问诊
        </button>
        <router-link class="btn btn-ghost" to="/reports/upload">
          <span v-html="ICONS.camera"></span>上传报告
        </router-link>
      </div>
    </header>

    <section class="command-center" aria-label="快速开始">
      <div class="ask-block">
        <div class="command-label">
          <span>智能问诊</span>
          <small>回答附知识来源</small>
        </div>
        <form class="ask-form" @submit.prevent="goAsk">
          <input v-model="q" aria-label="描述你的健康问题" placeholder="描述症状、持续时间和已有检查结果" />
          <button class="ask-send" type="submit" aria-label="发送问题">
            <span v-html="ICONS.arrow"></span>
          </button>
        </form>
        <div class="seeds" aria-label="常见问题">
          <button v-for="s in suggests" :key="s" type="button" @click="ask(s)">{{ s }}</button>
        </div>
      </div>

      <nav class="quick" aria-label="健康工具">
        <button v-for="a in QUICK" :key="a.label" type="button" @click="router.push(a.to)">
          <span
            class="q-ico"
            :style="{ background: a.bg, color: a.fg }"
            v-html="ICONS[a.icon]"
          ></span>
          <span class="q-copy"><b>{{ a.label }}</b><small>{{ a.hint }}</small></span>
          <span class="q-arrow" v-html="ICONS.arrow"></span>
        </button>
      </nav>
    </section>

    <dl class="overview-strip" aria-label="账户健康数据概览">
      <div><dt>家庭成员</dt><dd class="num">{{ overview.profileCount }}</dd></div>
      <div><dt>健康指标</dt><dd class="num">{{ overview.metricCount }}</dd></div>
      <div><dt>解读报告</dt><dd class="num">{{ overview.reportCount }}</dd></div>
      <div><dt>问诊记录</dt><dd class="num">{{ overview.sessionCount }}</dd></div>
    </dl>

    <div class="workspace-grid">
      <main class="clinical-board">
        <section class="board-section attention">
          <div class="section-title">
            <div>
              <span class="section-index">01</span>
              <h2>重点指标</h2>
            </div>
            <router-link class="text-link" to="/health">
              查看全部<span v-html="ICONS.arrow"></span>
            </router-link>
          </div>

          <div v-if="loading" class="skeleton focus-skeleton"></div>
          <template v-else-if="headline">
            <div class="focus-row">
              <div class="focus-summary">
                <span class="signal" :class="headline.flag">
                  {{ headline.flag === 'high' ? '偏高' : '偏低' }}
                </span>
                <p>当前偏离参考区间最明显</p>
                <small>{{ headline.profileName }} · {{ headline.recordedAt }}</small>
              </div>
              <div class="focus-strip">
                <LabStrip
                  :type="headline.metricType"
                  :value="Number(headline.metricValue)"
                  :unit="headline.unit"
                />
              </div>
            </div>

            <div v-if="rest.length" class="rest-list">
              <button
                v-for="a in rest"
                :key="a.metricId"
                type="button"
                @click="router.push('/health?id=' + (a.profileId ?? ''))"
              >
                <LabStrip
                  :type="a.metricType"
                  :value="Number(a.metricValue)"
                  :unit="a.unit"
                  compact
                />
                <span class="rest-meta">
                  <b :class="a.flag">{{ a.flag === 'high' ? '偏高' : '偏低' }}</b>
                  {{ a.profileName }}
                </span>
              </button>
            </div>
          </template>
          <div v-else class="board-empty">
            <span v-html="ICONS.pulse"></span>
            <div>
              <b>暂无需要优先处理的指标</b>
              <p>记录一次血压、血糖，或上传化验报告后，这里会按偏离程度排序。</p>
            </div>
          </div>
        </section>

        <section v-if="series.length" class="board-section trends-section">
          <div class="section-title">
            <div><span class="section-index">02</span><h2>近期趋势</h2></div>
            <span class="section-note">至少两次记录后生成</span>
          </div>
          <div class="trends">
            <button
              v-for="sr in series"
              :key="sr.metricType"
              type="button"
              @click="router.push('/health')"
            >
              <span class="trend-head">
                <span><b>{{ sr.metricType }}</b><small>{{ sr.points.length }} 次记录</small></span>
                <em class="num">
                  {{ sr.points[sr.points.length - 1]?.value ?? '—' }}
                  <small>{{ sr.unit }}</small>
                </em>
              </span>
              <v-chart :option="sparkOption(sr)" autoresize class="sparkline" />
            </button>
          </div>
        </section>

        <div class="board-split">
          <section class="board-section family-section">
            <div class="section-title compact-title">
              <div>
                <span class="section-index">{{ series.length ? '03' : '02' }}</span>
                <h2>家庭档案</h2>
              </div>
              <span class="section-note num">{{ overview.profiles.length }} 人</span>
            </div>
            <div class="folks">
              <button
                v-for="p in overview.profiles"
                :key="p.id"
                type="button"
                @click="router.push('/health?id=' + p.id)"
              >
                <span class="face">{{ initial(p.displayName) }}</span>
                <span class="folk-txt">
                  <b>{{ p.displayName || '未命名' }}</b>
                  <small>{{ p.relation || '档案' }}{{ p.age ? ' · ' + p.age + '岁' : '' }}</small>
                </span>
                <span class="row-arrow" v-html="ICONS.arrow"></span>
              </button>
              <button class="add-person" type="button" @click="router.push('/health?new=1')">
                <span class="face" v-html="ICONS.plus"></span>
                <span class="folk-txt"><b>新建家庭档案</b><small>为自己或家人持续记录</small></span>
              </button>
            </div>
          </section>

          <section class="board-section recent-section">
            <div class="section-title compact-title">
              <div>
                <span class="section-index">{{ series.length ? '04' : '03' }}</span>
                <h2>最近使用</h2>
              </div>
              <span class="section-note">继续上次进度</span>
            </div>
            <div v-if="hasRecent" class="recent">
              <button
                v-for="s in overview.recentSessions"
                :key="`s-${s.id}`"
                type="button"
                @click="router.push('/chat?sid=' + s.id)"
              >
                <span class="recent-type">问诊</span>
                <b>{{ s.title }}</b>
                <time>{{ formatWhen(s.updatedAt) }}</time>
              </button>
              <button
                v-for="r in overview.recentReports"
                :key="`r-${r.id}`"
                type="button"
                @click="router.push('/reports/' + r.id)"
              >
                <span class="recent-type report">报告</span>
                <b>{{ r.filename }}</b>
                <time>{{ formatWhen(r.createdAt) }}</time>
              </button>
            </div>
            <div v-else class="recent-empty">完成问诊或报告解读后，可从这里快速继续。</div>
          </section>
        </div>
      </main>

      <aside class="news" aria-label="权威健康资讯">
        <div class="news-head">
          <div><span class="eyebrow">PUBLIC HEALTH BRIEF</span><h2>权威健康简报</h2></div>
          <span class="verified"><span></span>公开来源</span>
        </div>
        <div v-if="news.length" class="news-list">
          <button
            v-for="(n, index) in news"
            :key="n.id"
            class="news-item"
            :class="{ featured: index === 0, 'has-photo': !!n.image }"
            type="button"
            @click="router.push('/news/' + n.id)"
          >
            <NewsPhoto v-if="n.image" :id="n.id" :alt="n.title" class="news-photo" />
            <span class="news-copy">
              <span class="news-meta">
                <b>{{ n.sourceName || '权威资讯' }}</b>
                <time>{{ shortDate(n.publishedOn) }}</time>
              </span>
              <strong>{{ n.title }}</strong>
              <span v-if="index === 0" class="news-summary">{{ n.summary }}</span>
            </span>
          </button>
        </div>
        <p v-else class="news-empty">暂无可用资讯，请稍后刷新。</p>
        <p class="news-foot">内容摘自公开权威机构，仅供健康科普参考。</p>
      </aside>
    </div>

    <MedicalDisclaimer />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import VChart from 'vue-echarts'
import { getOverview } from '@/api/home'
import type { HomeOverview, MetricSeries } from '@/api/types'
import { formatWhen, initial } from '@/utils/format'
import { bandOf, loadReference } from '@/utils/metrics'
import { chartTheme, ensureCharts } from '@/utils/charts'
import { listNews, type NewsListItem } from '@/api/news'
import { SUGGESTIONS } from '@/utils/suggestions'
import { ICONS } from '@/utils/icons'
import LabStrip from '@/components/LabStrip.vue'
import NewsPhoto from '@/components/NewsPhoto.vue'
import MedicalDisclaimer from '@/components/MedicalDisclaimer.vue'

ensureCharts()

const router = useRouter()
const q = ref('')
const news = ref<NewsListItem[]>([])

const QUICK = [
  {
    to: '/reports/upload',
    label: '解读报告',
    hint: '上传图片或 PDF',
    icon: 'camera',
    bg: 'var(--info-wash)',
    fg: 'var(--info)',
  },
  {
    to: '/health?tab=metrics',
    label: '记录指标',
    hint: '血压、血糖等',
    icon: 'pulse',
    bg: 'var(--flag-high-wash)',
    fg: 'var(--flag-high)',
  },
  {
    to: '/health?tab=advice',
    label: '健康建议',
    hint: '按档案查看',
    icon: 'heart',
    bg: 'var(--flag-normal-wash)',
    fg: 'var(--flag-normal)',
  },
  {
    to: '/health?tab=history',
    label: '病史用药',
    hint: '维护长期资料',
    icon: 'book2',
    bg: 'var(--accent-wash)',
    fg: 'var(--accent)',
  },
] as const

const loading = ref(true)
const overview = reactive<HomeOverview>({
  profileCount: 0,
  metricCount: 0,
  reportCount: 0,
  favoriteCount: 0,
  sessionCount: 0,
  profiles: [],
  recentSessions: [],
  recentReports: [],
  alerts: [],
  series: [],
})

const suggests = SUGGESTIONS.home
const series = computed(() => overview.series.filter((sr) => (sr.points?.length || 0) >= 2))
const alerts = computed(() => overview.alerts)
const hasAnyData = computed(() => overview.metricCount > 0 || overview.reportCount > 0)
const hasRecent = computed(
  () => overview.recentSessions.length > 0 || overview.recentReports.length > 0,
)

const today = computed(() => {
  const d = new Date()
  return `${d.getFullYear()} 年 ${d.getMonth() + 1} 月 ${d.getDate()} 日`
})

const ranked = computed(() =>
  [...overview.alerts]
    .map((a) => {
      const b = bandOf(a.metricType)
      const v = Number(a.metricValue)
      if (!b || Number.isNaN(v)) return { a, score: 0 }
      const span = b.high - b.low || 1
      const off = v > b.high ? v - b.high : v < b.low ? b.low - v : 0
      return { a, score: off / span }
    })
    .sort((x, y) => y.score - x.score)
    .map((x) => x.a),
)

const headline = computed(() => ranked.value[0] || null)
const rest = computed(() => ranked.value.slice(1))

function shortDate(iso?: string) {
  if (!iso) return ''
  const parts = iso.split('-')
  return parts.length === 3 ? `${parts[1]}月${parts[2]}日` : iso
}

onMounted(async () => {
  await loadReference().catch(() => undefined)
  try {
    const res = await getOverview()
    Object.assign(overview, res.data)
  } finally {
    loading.value = false
  }
  try {
    news.value = (await listNews(12)).data || []
  } catch {
    news.value = []
  }
})

function ask(text: string) {
  q.value = text
  goAsk()
}

function goAsk() {
  const text = q.value.trim()
  router.push(text ? { path: '/chat', query: { q: text } } : '/chat')
}

function sparkOption(sr: MetricSeries) {
  const t = chartTheme()
  const css = getComputedStyle(document.documentElement)
  const color =
    sr.flag === 'high'
      ? css.getPropertyValue('--flag-high').trim()
      : sr.flag === 'low'
        ? css.getPropertyValue('--flag-low').trim()
        : css.getPropertyValue('--accent').trim()
  return {
    grid: { left: 2, right: 2, top: 5, bottom: 2 },
    xAxis: { type: 'category', show: false, data: sr.points.map((p) => p.when) },
    yAxis: { type: 'value', show: false, scale: true },
    series: [
      {
        type: 'line',
        smooth: true,
        symbolSize: 4,
        data: sr.points.map((p) => p.value),
        lineStyle: { color, width: 1.75 },
        itemStyle: { color },
        areaStyle: { color, opacity: 0.09 },
      },
    ],
    tooltip: {
      trigger: 'axis',
      textStyle: { color: t.label },
      formatter: (ps: { dataIndex: number }[]) => {
        const i = ps[0]?.dataIndex ?? 0
        return `${sr.points[i]?.when ?? ''}<br/>${sr.metricType}: ${sr.points[i]?.value ?? ''} ${sr.unit || ''}`
      },
    },
  }
}
</script>

<style scoped>
.page {
  width: 100%;
  max-width: 1600px;
  display: grid;
  gap: var(--space-5);
}

.workspace-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: var(--space-6);
  padding: var(--space-1) 0 var(--space-5);
  border-bottom: 1px solid var(--edge);
}

.workspace-copy {
  min-width: 0;
}

.workspace-head .eyebrow {
  margin-bottom: var(--space-2);
}

.workspace-head h1 {
  font-size: clamp(26px, 3vw, 34px);
}

.status-copy {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
  margin-top: var(--space-2);
  color: var(--ink-mute);
  font-size: 13.5px;
}

.status-copy strong {
  color: var(--ink);
  font-weight: 650;
}

.status-copy strong::after {
  content: '·';
  margin-left: var(--space-2);
  color: var(--ink-faint);
}

.workspace-actions {
  flex-shrink: 0;
  display: flex;
  gap: var(--space-2);
}

.command-center {
  display: grid;
  grid-template-columns: minmax(0, 1.2fr) minmax(440px, 0.8fr);
  border: 1px solid var(--edge-strong);
  border-radius: var(--r-shell);
  background: var(--card);
  box-shadow: var(--shadow-2);
  overflow: hidden;
}

.ask-block {
  padding: var(--space-5);
  border-right: 1px solid var(--edge);
}

.command-label {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: var(--space-3);
  margin-bottom: var(--space-3);
}

.command-label span {
  font-weight: 650;
}

.command-label small {
  color: var(--ink-faint);
  font-size: 12px;
}

.ask-form {
  display: flex;
  min-height: 48px;
  border: 1px solid var(--edge-strong);
  border-radius: var(--r-control);
  background: var(--paper);
  overflow: hidden;
  transition: border-color 0.16s var(--ease-soft), box-shadow 0.16s var(--ease-soft);
}

.ask-form:focus-within {
  border-color: var(--accent);
  box-shadow: 0 0 0 3px var(--accent-wash);
}

.ask-form input {
  flex: 1;
  min-width: 0;
  border: 0;
  outline: 0;
  padding: 0 var(--space-4);
  color: var(--ink);
  background: transparent;
  font-size: 14px;
}

.ask-form input::placeholder {
  color: var(--ink-faint);
}

.ask-send {
  width: 48px;
  flex: 0 0 48px;
  border: 0;
  border-left: 1px solid var(--accent-line);
  background: var(--accent);
  color: var(--on-accent);
  cursor: pointer;
}

.ask-send :deep(svg) {
  width: 18px;
  height: 18px;
}

.seeds {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
  margin-top: var(--space-3);
}

.seeds button {
  padding: var(--space-1) 0;
  border: 0;
  border-bottom: 1px solid transparent;
  background: transparent;
  color: var(--ink-mute);
  font-size: 12px;
  cursor: pointer;
}

.seeds button + button::before {
  content: '·';
  margin-right: var(--space-2);
  color: var(--ink-faint);
}

.quick {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.quick > button {
  min-width: 0;
  display: flex;
  align-items: center;
  gap: var(--space-3);
  padding: var(--space-3) var(--space-4);
  border: 0;
  border-bottom: 1px solid var(--edge);
  background: transparent;
  color: var(--ink);
  text-align: left;
  cursor: pointer;
  transition: background 0.16s var(--ease-soft);
}

.quick > button:nth-child(odd) {
  border-right: 1px solid var(--edge);
}

.quick > button:nth-child(n + 3) {
  border-bottom: 0;
}

.q-ico,
.face {
  display: grid;
  place-items: center;
  flex-shrink: 0;
}

.q-ico {
  width: 36px;
  height: 36px;
  border-radius: var(--r-control);
}

.q-ico :deep(svg) {
  width: 17px;
  height: 17px;
}

.q-copy {
  min-width: 0;
  display: grid;
}

.q-copy b {
  font-size: 13px;
  font-weight: 650;
}

.q-copy small {
  color: var(--ink-faint);
  font-size: 11.5px;
}

.q-arrow,
.row-arrow {
  margin-left: auto;
  color: var(--ink-faint);
}

.q-arrow :deep(svg),
.row-arrow :deep(svg) {
  width: 15px;
  height: 15px;
}

.overview-strip {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  margin: 0;
  border-block: 1px solid var(--edge);
}

.overview-strip div {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: var(--space-3);
  padding: var(--space-3) var(--space-4);
}

.overview-strip div + div {
  border-left: 1px solid var(--edge);
}

.overview-strip dt {
  color: var(--ink-mute);
  font-size: 12.5px;
}

.overview-strip dd {
  margin: 0;
  color: var(--ink);
  font-size: 21px;
  font-weight: 650;
}

.workspace-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 360px;
  gap: var(--space-5);
  align-items: start;
}

.clinical-board {
  min-width: 0;
  border: 1px solid var(--edge);
  border-radius: var(--r-shell);
  background: var(--card);
  box-shadow: var(--shadow-1);
  overflow: hidden;
}

.board-section {
  padding: var(--space-5);
}

.board-section + .board-section,
.board-split {
  border-top: 1px solid var(--edge);
}

.section-title,
.section-title > div {
  display: flex;
  align-items: center;
}

.section-title {
  justify-content: space-between;
  gap: var(--space-4);
  margin-bottom: var(--space-5);
}

.section-title > div {
  gap: var(--space-3);
  min-width: 0;
}

.section-title h2 {
  font-size: 17px;
  font-weight: 650;
}

.section-index {
  color: var(--accent);
  font: 600 11px/1 var(--font-mono);
}

.section-note {
  color: var(--ink-faint);
  font-size: 12px;
}

.text-link {
  display: inline-flex;
  align-items: center;
  gap: var(--space-1);
  flex-shrink: 0;
  color: var(--ink-mute);
  font-size: 12.5px;
}

.text-link :deep(svg) {
  width: 14px;
  height: 14px;
}

.focus-skeleton {
  height: 132px;
  border-radius: var(--r-control);
}

.focus-row {
  display: grid;
  grid-template-columns: 210px minmax(0, 1fr);
  gap: var(--space-5);
  align-items: center;
  padding: var(--space-4);
  border: 1px solid var(--edge);
  border-left: 3px solid var(--flag-high);
  border-radius: var(--r-control);
  background: var(--paper);
}

.focus-summary {
  display: grid;
  align-content: center;
  gap: var(--space-2);
}

.signal {
  width: max-content;
  padding: 2px var(--space-2);
  border-radius: var(--r-chip);
  font-size: 11.5px;
  font-weight: 650;
}

.signal.high,
.rest-meta .high {
  color: var(--flag-high);
}

.signal.high {
  background: var(--flag-high-wash);
}

.signal.low,
.rest-meta .low {
  color: var(--flag-low);
}

.signal.low {
  background: var(--flag-low-wash);
}

.focus-summary p {
  margin: 0;
  color: var(--ink);
  font-size: 14px;
  font-weight: 600;
}

.focus-summary small {
  color: var(--ink-faint);
  font-size: 12px;
}

.focus-strip {
  min-width: 0;
}

.rest-list {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  margin-top: var(--space-4);
  border: 1px solid var(--edge);
  border-radius: var(--r-control);
  overflow: hidden;
}

.rest-list > button {
  min-width: 0;
  padding: var(--space-3) var(--space-4);
  border: 0;
  border-bottom: 1px solid var(--edge);
  background: transparent;
  text-align: left;
  cursor: pointer;
}

.rest-list > button:nth-child(odd) {
  border-right: 1px solid var(--edge);
}

.rest-list > button:nth-last-child(-n + 2) {
  border-bottom: 0;
}

.rest-meta {
  display: flex;
  gap: var(--space-2);
  margin-top: var(--space-2);
  color: var(--ink-faint);
  font-size: 11.5px;
}

.rest-meta b {
  font-weight: 650;
}

.board-empty {
  display: flex;
  align-items: flex-start;
  gap: var(--space-3);
  padding: var(--space-5);
  border: 1px dashed var(--edge-strong);
  border-radius: var(--r-control);
  background: var(--paper);
}

.board-empty > span {
  display: grid;
  place-items: center;
  width: 34px;
  height: 34px;
  flex-shrink: 0;
  border-radius: var(--r-control);
  background: var(--flag-normal-wash);
  color: var(--flag-normal);
}

.board-empty > span :deep(svg) {
  width: 17px;
  height: 17px;
}

.board-empty b {
  font-size: 13.5px;
}

.board-empty p {
  margin-top: var(--space-1);
  color: var(--ink-mute);
  font-size: 12.5px;
}

.trends {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  border: 1px solid var(--edge);
  border-radius: var(--r-control);
  overflow: hidden;
}

.trends > button {
  min-width: 0;
  padding: var(--space-3) var(--space-4) var(--space-2);
  border: 0;
  border-right: 1px solid var(--edge);
  background: transparent;
  text-align: left;
  cursor: pointer;
}

.trends > button:last-child {
  border-right: 0;
}

.trend-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-3);
}

.trend-head > span {
  min-width: 0;
  display: grid;
}

.trend-head b {
  overflow: hidden;
  color: var(--ink-soft);
  font-size: 12.5px;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.trend-head small {
  color: var(--ink-faint);
  font-size: 10.5px;
  font-style: normal;
  font-weight: 400;
}

.trend-head em {
  flex-shrink: 0;
  color: var(--ink);
  font-size: 17px;
  font-style: normal;
  font-weight: 650;
}

.sparkline {
  height: 54px;
}

.board-split {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.board-split > section + section {
  border-left: 1px solid var(--edge);
}

.compact-title {
  margin-bottom: var(--space-3);
}

.folks,
.recent {
  display: grid;
}

.folks > button,
.recent > button {
  min-width: 0;
  border: 0;
  border-top: 1px solid var(--edge);
  background: transparent;
  color: var(--ink);
  text-align: left;
  cursor: pointer;
}

.folks > button {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  padding: var(--space-3) 0;
}

.face {
  width: 34px;
  height: 34px;
  border: 1px solid var(--accent-line);
  border-radius: var(--r-avatar);
  background: var(--accent-wash);
  color: var(--accent);
  font-size: 13px;
  font-weight: 650;
}

.face :deep(svg) {
  width: 16px;
  height: 16px;
}

.add-person .face {
  border-color: var(--edge-strong);
  background: var(--tray);
  color: var(--ink-mute);
}

.folk-txt {
  min-width: 0;
  display: grid;
}

.folk-txt b {
  overflow: hidden;
  font-size: 13.5px;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.folk-txt small {
  color: var(--ink-faint);
  font-size: 11.5px;
}

.recent > button {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: center;
  gap: var(--space-3);
  padding: 13px 0;
}

.recent-type {
  padding: 2px var(--space-2);
  border-radius: var(--r-chip);
  background: var(--accent-wash);
  color: var(--accent);
  font-size: 10.5px;
  font-weight: 650;
}

.recent-type.report {
  background: var(--info-wash);
  color: var(--info);
}

.recent b {
  overflow: hidden;
  font-size: 13px;
  font-weight: 550;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.recent time {
  color: var(--ink-faint);
  font-size: 11px;
}

.recent-empty {
  padding: var(--space-5) 0;
  border-top: 1px solid var(--edge);
  color: var(--ink-faint);
  font-size: 12.5px;
}

.news {
  position: sticky;
  top: var(--main-pad);
  min-width: 0;
  border-top: 3px solid var(--ink);
}

.news-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: var(--space-3);
  padding: var(--space-3) 0;
  border-bottom: 1px solid var(--edge-strong);
}

.news-head .eyebrow {
  margin-bottom: var(--space-1);
  font-size: 9.5px;
}

.news-head h2 {
  font-size: 18px;
}

.verified {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: var(--ink-faint);
  font-size: 11px;
  white-space: nowrap;
}

.verified > span {
  width: 6px;
  height: 6px;
  border-radius: var(--r-pill);
  background: var(--flag-normal);
}

.news-list {
  max-height: calc(100dvh - var(--topbar-h) - (2 * var(--main-pad)) - 98px);
  overflow-y: auto;
  scrollbar-width: thin;
}

.news-item {
  width: 100%;
  min-width: 0;
  display: block;
  padding: var(--space-3) 0;
  border: 0;
  border-bottom: 1px solid var(--edge);
  background: transparent;
  color: var(--ink);
  text-align: left;
  cursor: pointer;
}

.news-item.has-photo:not(.featured) {
  display: grid;
  grid-template-columns: 92px minmax(0, 1fr);
  gap: var(--space-3);
  align-items: start;
}

.news-item.featured {
  padding-top: var(--space-4);
}

.news-photo {
  border-radius: var(--r-control);
}

.news-item:not(.featured) .news-photo {
  aspect-ratio: 4 / 3;
}

.news-copy {
  min-width: 0;
  display: grid;
  gap: var(--space-2);
}

.featured .news-copy {
  padding-top: var(--space-3);
}

.news-meta {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: var(--space-2);
  color: var(--ink-faint);
  font-size: 10.5px;
}

.news-meta b {
  overflow: hidden;
  color: var(--accent);
  font-weight: 650;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.news-meta time {
  flex-shrink: 0;
}

.news-copy strong {
  display: -webkit-box;
  overflow: hidden;
  font-size: 13.5px;
  font-weight: 650;
  line-height: 1.55;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.featured .news-copy strong {
  font-size: 16px;
  line-height: 1.5;
}

.news-summary {
  display: -webkit-box;
  overflow: hidden;
  color: var(--ink-mute);
  font-size: 12.5px;
  line-height: 1.7;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.news-empty,
.news-foot {
  color: var(--ink-faint);
  font-size: 11.5px;
}

.news-empty {
  padding: var(--space-5) 0;
  border-bottom: 1px solid var(--edge);
}

.news-foot {
  margin-top: var(--space-3);
}

@media (hover: hover) and (pointer: fine) {
  .quick > button:hover,
  .rest-list > button:hover,
  .trends > button:hover,
  .folks > button:hover,
  .recent > button:hover {
    background: var(--tray);
  }

  .seeds button:hover,
  .text-link:hover {
    color: var(--accent);
  }

  .seeds button:hover {
    border-bottom-color: var(--accent-line);
  }

  .news-item:hover .news-copy strong {
    color: var(--accent);
  }
}

@media (max-width: 1220px) {
  .command-center {
    grid-template-columns: 1fr;
  }

  .ask-block {
    border-right: 0;
    border-bottom: 1px solid var(--edge);
  }

  .workspace-grid {
    grid-template-columns: minmax(0, 1fr) 320px;
  }
}

@media (max-width: 980px) {
  .workspace-grid {
    grid-template-columns: 1fr;
  }

  .news {
    position: static;
  }

  .news-list {
    max-height: none;
  }

  .news-list {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    column-gap: var(--space-5);
  }

  .news-item.featured {
    grid-row: span 3;
  }
}

@media (max-width: 720px) {
  .page {
    gap: var(--space-4);
  }

  .workspace-head {
    align-items: stretch;
    flex-direction: column;
    gap: var(--space-4);
    padding-bottom: var(--space-4);
  }

  .workspace-head h1 {
    font-size: 26px;
  }

  .status-copy {
    display: grid;
    gap: var(--space-1);
  }

  .status-copy strong::after {
    content: none;
  }

  .workspace-actions {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .workspace-actions .btn {
    min-width: 0;
    padding-inline: var(--space-2);
  }

  .ask-block,
  .board-section {
    padding: var(--space-4);
  }

  .command-label {
    align-items: flex-start;
    flex-direction: column;
    gap: 0;
  }

  .ask-form {
    min-height: 46px;
  }

  .quick > button {
    gap: var(--space-2);
    padding: var(--space-3);
  }

  .q-ico {
    width: 32px;
    height: 32px;
  }

  .q-copy small,
  .q-arrow {
    display: none;
  }

  .overview-strip {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .overview-strip div:nth-child(3),
  .overview-strip div:nth-child(4) {
    border-top: 1px solid var(--edge);
  }

  .overview-strip div:nth-child(3) {
    border-left: 0;
  }

  .focus-row {
    grid-template-columns: 1fr;
    gap: var(--space-4);
  }

  .rest-list,
  .board-split {
    grid-template-columns: 1fr;
  }

  .rest-list > button:nth-child(odd) {
    border-right: 0;
  }

  .rest-list > button:nth-last-child(2) {
    border-bottom: 1px solid var(--edge);
  }

  .board-split > section + section {
    border-top: 1px solid var(--edge);
    border-left: 0;
  }

  .trends {
    grid-template-columns: 1fr;
  }

  .trends > button {
    border-right: 0;
    border-bottom: 1px solid var(--edge);
  }

  .trends > button:last-child {
    border-bottom: 0;
  }

  .news-list {
    display: block;
  }

  .news-item.featured {
    grid-row: auto;
  }
}

@media (max-width: 420px) {
  .seeds button:nth-child(n + 3) {
    display: none;
  }

  .section-title {
    align-items: flex-start;
  }

  .section-note {
    max-width: 9em;
    text-align: right;
  }

  .recent > button {
    grid-template-columns: auto minmax(0, 1fr);
  }

  .recent time {
    display: none;
  }
}
</style>
