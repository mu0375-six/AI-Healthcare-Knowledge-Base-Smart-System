<template>
  <div class="page">
<!-- 两栏：主栏收窄，右侧是可滚动的健康新闻流。
         新闻来自权威机构（启动时由后端爬取），点开是站内详情页。
         英雄区也收进主栏 —— 右栏从页面顶部就位，不留一段空白的右上角。 -->
    <div class="home-grid">
    <div class="main-col">
    <!-- Hero 是一句论断，不是一个问句。
         论断的依据来自这个产品世界里最核心的器物：化验单的参考线。
         有指标越线时，标题直接说出越了几项；没有时，说出"都在线内"。 -->
    <header class="hero" v-reveal>
      <span class="eyebrow">
        <span v-html="ICONS.pulse"></span>
        {{ today }}
      </span>

      <h1 v-if="alerts.length">
        {{ cn(alerts.length) }}项指标<br />
        <em>越过了参考线。</em>
      </h1>
      <h1 v-else-if="hasAnyData">
        所有指标<br />
        <em>都在参考线内。</em>
      </h1>
      <h1 v-else>
        先把一张化验单<br />
        <em>交给它读一遍。</em>
      </h1>

      <p class="thesis">
        {{
          alerts.length
            ? '越线不等于生病。下面每一条都标出了它离参考区间有多远，以及这通常意味着什么。'
            : hasAnyData
              ? '继续记录才看得出走势 —— 单次正常说明不了长期趋势。'
              : '拍一张照，指标、高低、逐项解释会自动整理成一份档案。'
        }}
      </p>

      <div class="hero-acts">
        <button class="btn btn-primary btn-cta" type="button" @click="goAsk">
          {{ alerts.length ? '问问这意味着什么' : '开始一次问诊' }}
          <span class="knob" v-html="ICONS.arrow"></span>
        </button>
        <router-link class="btn btn-ghost" to="/reports/upload">
          <span v-html="ICONS.camera"></span>读一张化验单
        </router-link>
      </div>
    </header>

<!-- 快捷入口：一整行圆形图标，横向铺满。
         这是这个产品能做的全部动作，摆出来比藏进菜单强。 -->
    <nav class="quick" aria-label="快捷入口" v-reveal="40">
      <button v-for="a in QUICK" :key="a.label" class="q" type="button" @click="router.push(a.to)">
        <span class="q-ico" :style="{ background: a.bg, color: a.fg }" v-html="ICONS[a.icon]"></span>
        <span>{{ a.label }}</span>
      </button>
    </nav>

    <div class="bento">
      <!-- 提问：主行为，占七列 -->
      <section class="shell b7" v-reveal="60">
        <div class="core core-pad ask">
          <div class="section-head">
            <h3>说说哪里不舒服</h3>
            <span class="count">回答会标出知识库出处</span>
          </div>
          <form class="ask-form" @submit.prevent="goAsk">
            <input v-model="q" aria-label="描述你的问题" placeholder="嗓子痛第三天了，还要不要去医院" />
            <button class="btn btn-primary btn-sm" type="submit" aria-label="发送">
              <span v-html="ICONS.send"></span>
            </button>
          </form>
          <div class="seeds">
            <button v-for="s in suggests" :key="s" type="button" @click="ask(s)">{{ s }}</button>
          </div>
        </div>
      </section>

      <!-- 最该看的那一项：越线最远的指标，大号标尺 -->
      <section class="shell b5" v-reveal="120">
        <div class="core core-pad focus">
          <div class="section-head">
            <h3>{{ headline ? '最该看的一项' : '还没有指标' }}</h3>
            <router-link v-if="headline" class="spacer btn btn-quiet btn-sm" to="/health">全部</router-link>
          </div>

          <template v-if="loading">
            <div class="skeleton" style="height: 96px; border-radius: var(--r-card)"></div>
          </template>

          <template v-else-if="headline">
            <LabStrip
              :type="headline.metricType"
              :value="Number(headline.metricValue)"
              :unit="headline.unit"
            />
            <p class="focus-who">
              {{ headline.profileName }} · <time>{{ headline.recordedAt }}</time>
            </p>
          </template>

          <p v-else class="quiet">
            记一次血压或血糖，或者传一张化验单，这里会显示离参考线最远的那一项。
          </p>
        </div>
      </section>

      <!-- 其余越线项 -->
      <section v-if="rest.length" class="b12" v-reveal="60">
        <div class="section-head">
          <h3>其余越线的指标</h3>
          <span class="count num">{{ rest.length }}</span>
        </div>
        <div class="rest">
          <button
            v-for="a in rest"
            :key="a.metricId"
            class="tile rest-card"
            type="button"
            @click="router.push('/health?id=' + (a.profileId ?? ''))"
          >
            <LabStrip
              :type="a.metricType"
              :value="Number(a.metricValue)"
              :unit="a.unit"
              compact
            />
            <span class="rest-foot">
              <span class="chip" :class="a.flag">{{ a.flag === 'high' ? '偏高' : '偏低' }}</span>
              <span>{{ a.profileName }}</span>
            </span>
          </button>
        </div>
      </section>

      <!-- 趋势：八列，图表最吃宽度 -->
      <section v-if="series.length" class="shell b12" v-reveal>
        <div class="core core-pad">
          <div class="section-head">
            <h3>这些天的走势</h3>
            <router-link class="spacer btn btn-quiet btn-sm" to="/health">全部指标</router-link>
          </div>
          <div class="trends">
            <button
              v-for="sr in series"
              :key="sr.metricType"
              class="trend"
              type="button"
              @click="router.push('/health')"
            >
              <span class="trend-head">
                <b>{{ sr.metricType }}</b>
                <em class="num">{{ sr.points[sr.points.length - 1]?.value ?? '—' }}</em>
              </span>
              <v-chart :option="sparkOption(sr)" autoresize style="height: 58px" />
            </button>
          </div>
        </div>
      </section>

      <!-- 家人：四列 -->
      <section class="shell" :class="'b4'" v-reveal="80">
        <div class="core core-pad">
          <div class="section-head">
            <h3>家人</h3>
            <span v-if="overview.profiles.length" class="count num">{{ overview.profiles.length }}</span>
          </div>
          <div class="folks">
            <button
              v-for="p in overview.profiles"
              :key="p.id"
              class="folk"
              type="button"
              @click="router.push('/health?id=' + p.id)"
            >
              <span class="face">{{ initial(p.displayName) }}</span>
              <span class="folk-txt">
                <b>{{ p.displayName || '未命名' }}</b>
                <i>{{ p.relation || '档案' }}{{ p.age ? ' · ' + p.age + '岁' : '' }}</i>
              </span>
            </button>
            <button class="folk add" type="button" @click="router.push('/health?new=1')">
              <span class="face plus" v-html="ICONS.plus"></span>
              <span class="folk-txt"><b>建一份新的</b><i>爸妈、孩子或自己</i></span>
            </button>
          </div>
        </div>
      </section>

      <!-- 近期：两个六列 -->
      <section class="shell b4" v-reveal="60">
        <div class="core core-pad">
          <div class="section-head">
            <h3>问过的</h3>
            <router-link class="spacer btn btn-quiet btn-sm" to="/chat">全部</router-link>
          </div>
          <p v-if="!overview.recentSessions.length" class="quiet">还没有问过什么。</p>
          <button
            v-for="s in overview.recentSessions"
            :key="s.id"
            class="line"
            type="button"
            @click="router.push('/chat?sid=' + s.id)"
          >
            <b>{{ s.title }}</b>
            <time>{{ formatWhen(s.updatedAt) }}</time>
          </button>
        </div>
      </section>

      <section class="shell b4" v-reveal="120">
        <div class="core core-pad">
          <div class="section-head">
            <h3>读过的报告</h3>
            <router-link class="spacer btn btn-quiet btn-sm" to="/health?tab=reports">全部</router-link>
          </div>
          <p v-if="!overview.recentReports.length" class="quiet">还没有读过报告。</p>
          <button
            v-for="r in overview.recentReports"
            :key="r.id"
            class="line"
            type="button"
            @click="router.push('/reports/' + r.id)"
          >
            <b>{{ r.filename }}</b>
            <time>{{ formatWhen(r.createdAt) }}</time>
          </button>
        </div>
      </section>
    </div>
    </div>

    <aside class="news" aria-label="健康新闻" v-reveal="80">
      <div class="news-head">
        <h3><span class="news-ico" v-html="ICONS.spark"></span>健康新闻</h3>
        <span class="news-src">世界卫生组织 · 中文</span>
      </div>
      <div v-if="news.length" class="news-list">
        <button
          v-for="n in news"
          :key="n.id"
          class="news-card"
          type="button"
          @click="router.push('/news/' + n.id)"
        >
          <!-- grid 不能直接放在 button 上：Chrome 对 button 的 grid 布局
               不完整（高度塌陷、内容叠在一起），外壳只负责观感。 -->
          <span class="news-inner">
            <NewsPhoto v-if="n.image" :id="n.id" :alt="n.title" />
            <span class="news-body">
              <h4>{{ n.title }}</h4>
              <p>{{ n.summary }}</p>
              <span class="news-meta">
                <b>{{ n.sourceName || '权威资讯' }}</b>
                <time>{{ shortDate(n.publishedOn) }}</time>
              </span>
            </span>
          </span>
        </button>
      </div>
      <p v-else class="news-empty">新闻正在路上，稍后再来看看。</p>
      <p class="news-foot">摘自权威机构公开资讯 · 不构成医疗建议</p>
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

/** 快捷入口。底色用强调色与数据色的极淡washes，不引第二个品牌色。 */
const QUICK = [
  { to: '/chat', label: '问诊', icon: 'chat', bg: 'var(--accent-wash)', fg: 'var(--accent)' },
  { to: '/reports/upload', label: '读化验单', icon: 'camera', bg: 'var(--flag-low-wash)', fg: 'var(--flag-low)' },
  { to: '/health', label: '家庭档案', icon: 'file', bg: 'var(--flag-normal-wash)', fg: 'var(--flag-normal)' },
  { to: '/health?tab=metrics', label: '记指标', icon: 'pulse', bg: 'var(--flag-high-wash)', fg: 'var(--flag-high)' },
  { to: '/triage', label: '科室导诊', icon: 'compass', bg: 'var(--accent-wash)', fg: 'var(--accent)' },
  { to: '/health?tab=advice', label: '健康建议', icon: 'heart', bg: 'var(--flag-normal-wash)', fg: 'var(--flag-normal)' },
  { to: '/favorites', label: '我的收藏', icon: 'star', bg: 'var(--flag-low-wash)', fg: 'var(--flag-low)' },
  { to: '/health?tab=history', label: '病史', icon: 'book2', bg: 'var(--accent-wash)', fg: 'var(--accent)' },
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

/**
 * 只保留画得出走势的序列：至少要两个点才谈得上"走势"。
 * 后端目前会返回 points 为空的条目（还遇到过 metricType 带脏前缀
 * 如 "33:空腹血糖"），前端不替它画一个空图表框。
 */
const series = computed(() => overview.series.filter((sr) => (sr.points?.length || 0) >= 2))
const alerts = computed(() => overview.alerts)
const hasAnyData = computed(() => overview.metricCount > 0 || overview.reportCount > 0)

const today = computed(() => {
  const d = new Date()
  return `${d.getFullYear()} 年 ${d.getMonth() + 1} 月 ${d.getDate()} 日`
})

/**
 * 头条指标：越参考线最远的那一项。
 * 用「偏离量 ÷ 区间宽度」归一化后比较 —— 血糖超 1 和血压超 1
 * 严重程度完全不同，直接比绝对差值会挑错人。
 */
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

/** 一位数用汉字更像人话：「三项指标」读着比「3 项指标」自然。 */
function cn(n: number) {
  return ['零', '一', '两', '三', '四', '五', '六', '七', '八', '九'][n] ?? String(n)
}

/** 2026-08-20 → 08月20日：窄栏里日期越短越好扫，当年份不变时年份是冗余。 */
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
  // 新闻流失败静默降级：首页主体不依赖它
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

/** 迷你走势：只留线形，坐标轴全部隐藏。 */
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
  display: grid;
  gap: 26px;
  /* 首页是主栏 + 新闻栏的宽两栏，上限放宽到 1760px：
     1920 屏（内容区约 1680px）能整幅铺满，不剩右侧死角白。 */
  max-width: 1760px;
}

/* ---- Hero ---- */
.hero {
  padding: 8px 0 4px;
}

/* 中文标题的宽度必须用 em 而不是 ch ——
   ch 按拉丁 "0" 的字宽算，汉字约是它的两倍宽，
   22ch 实际只放得下 11 个汉字，标题会被劈在词中间。
   7.4em ≈ 每行 7 个汉字，两行成句。 */
.hero h1 {
  margin: 16px 0 0;
  max-width: 7.4em;
  text-wrap: nowrap;
}

/* 论断的后半句用斜体衬线 —— Fraunces 的 wonk 轴在斜体里最有性格，
   同时把"这是一句话的重点"标出来 */
.hero h1 em {
  font-style: italic;
  color: var(--accent);
}

.thesis {
  margin-top: 18px;
  max-width: 24em;
  color: var(--ink-soft);
  font-size: 16px;
  line-height: 1.72;
}

.hero-acts {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 26px;
}

/* ---- 快捷入口 ---- */
.quick {
  display: flex;
  gap: 6px;
  overflow-x: auto;
  padding: 2px 2px 6px;
  scrollbar-width: none;
}

.quick::-webkit-scrollbar {
  display: none;
}

.q {
  flex: 1 0 auto;
  min-width: 84px;
  display: grid;
  justify-items: center;
  gap: 8px;
  padding: 12px 8px 10px;
  border: 0;
  border-radius: var(--r-card);
  background: none;
  cursor: pointer;
  color: var(--ink-soft);
  font-size: 12.5px;
  font-weight: 500;
  transition: background 0.4s var(--ease), transform 0.4s var(--ease);
}

.q:active {
  transform: scale(0.95);
}

@media (hover: hover) and (pointer: fine) {
  .q:hover {
    background: var(--tray);
  }
}

.q-ico {
  display: grid;
  place-items: center;
  width: 46px;
  height: 46px;
  border-radius: 999px;
}

.q-ico :deep(svg) {
  width: 21px;
  height: 21px;
  display: block;
}

/* ---- 两栏：主栏收窄，右侧新闻流 ---- */
.home-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 336px;
  gap: 26px;
  align-items: start;
}

.news {
  position: sticky;
  /* 与 .main 的顶部内边距对齐，吸顶时和主栏一起挂在视口上 */
  top: 26px;
}

.news-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 10px;
  padding: 2px 4px 10px;
}

.news-head h3 {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  font-family: var(--font);
  font-size: 15px;
  font-weight: 600;
}

.news-ico :deep(svg) {
  width: 15px;
  height: 15px;
  color: var(--accent);
}

.news-src {
  font-size: 12px;
  color: var(--ink-faint);
}

.news-list {
  display: grid;
  gap: 12px;
  /* 一屏内自己滚动：拖动滚动条即可翻完整列，主栏不受牵连 */
  max-height: calc(100dvh - 152px);
  overflow-y: auto;
  padding: 2px 6px 10px 2px;
  scrollbar-width: thin;
  scrollbar-color: var(--edge-strong) transparent;
}

/* 公众号式图文卡：图在上，标题、摘要、来源在下 */
.news-card {
  display: block;
  padding: 0;
  overflow: hidden;
  background: var(--card);
  border: 1px solid var(--edge);
  border-radius: var(--r-card);
  box-shadow: var(--inner-light), var(--shadow-1);
  cursor: pointer;
  text-align: left;
  transition: transform 0.45s var(--ease), box-shadow 0.45s var(--ease),
    border-color 0.3s var(--ease-soft);
}

@media (hover: hover) and (pointer: fine) {
  .news-card:hover {
    transform: translateY(-2px);
    box-shadow: var(--inner-light), var(--shadow-3);
    border-color: var(--edge-strong);
  }
}

.news-card:active {
  transform: scale(0.985);
  transition-duration: 120ms;
}

.news-inner {
  display: grid;
}

.news-body {
  display: grid;
  gap: 6px;
  padding: 12px 14px 11px;
}

.news-body h4 {
  font-size: 14px;
  line-height: 1.5;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.news-body p {
  font-size: 12.5px;
  line-height: 1.66;
  color: var(--ink-mute);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.news-meta {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 10px;
  margin-top: 2px;
  font-size: 11.5px;
  color: var(--ink-faint);
}

.news-meta b {
  font-weight: 550;
}

.news-empty {
  padding: 18px 4px;
  color: var(--ink-faint);
  font-size: 13px;
}

.news-foot {
  margin-top: 10px;
  padding: 0 4px;
  font-size: 11px;
  line-height: 1.6;
  color: var(--ink-faint);
}

/* ---- 提问 ---- */
.ask-form {
  display: flex;
  gap: 8px;
  align-items: center;
  background: var(--sunk);
  border: 1px solid var(--edge);
  border-radius: 999px;
  padding: 5px 5px 5px 18px;
  transition: border-color 0.3s var(--ease-soft), box-shadow 0.4s var(--ease);
}

.ask-form:focus-within {
  border-color: var(--accent-line);
  box-shadow: 0 0 0 3px var(--accent-wash);
}

.ask-form input {
  flex: 1;
  min-width: 0;
  border: 0;
  background: none;
  color: var(--ink);
  outline: none;
  font-size: 15px;
}

.ask-form input::placeholder {
  color: var(--ink-faint);
}

.ask-form .btn-sm {
  width: 34px;
  height: 34px;
  padding: 0;
  flex-shrink: 0;
}

.seeds {
  display: flex;
  flex-wrap: wrap;
  gap: 7px;
  margin-top: 12px;
}

.seeds button {
  border: 1px solid var(--edge);
  background: transparent;
  color: var(--ink-mute);
  border-radius: 999px;
  padding: 5px 12px;
  font-size: 12.5px;
  cursor: pointer;
  transition: color 0.3s var(--ease-soft), border-color 0.3s var(--ease-soft),
    background 0.4s var(--ease), transform 0.4s var(--ease);
}

.seeds button:active {
  transform: scale(0.96);
}

@media (hover: hover) and (pointer: fine) {
  .seeds button:hover {
    color: var(--accent);
    border-color: var(--accent-line);
    background: var(--accent-wash);
  }
}

/* ---- 头条指标 ---- */
.focus-who {
  margin-top: 12px;
  font-size: 12.5px;
  color: var(--ink-faint);
}

/* ---- 其余越线项 ---- */
.rest {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(232px, 1fr));
  gap: 12px;
  align-items: start;
}

.rest-card {
  padding: 15px 17px 13px;
}

.rest-foot {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 11px;
  font-size: 12px;
  color: var(--ink-faint);
}

/* ---- 走势 ---- */
.trends {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));
  gap: 10px;
}

.trend {
  border: 1px solid var(--edge);
  border-radius: var(--r-card);
  background: transparent;
  padding: 11px 13px 6px;
  cursor: pointer;
  text-align: left;
  transition: border-color 0.3s var(--ease-soft), background 0.4s var(--ease);
}

@media (hover: hover) and (pointer: fine) {
  .trend:hover {
    border-color: var(--edge-strong);
    background: var(--tray);
  }
}

.trend-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 2px;
}

.trend-head b {
  font-size: 13px;
  font-weight: 550;
  color: var(--ink-soft);
}

.trend-head em {
  font-style: normal;
  font-size: 16px;
  font-weight: 600;
}

/* ---- 家人 ---- */
.folks {
  display: grid;
  gap: 6px;
}

.folk {
  display: flex;
  align-items: center;
  gap: 11px;
  padding: 9px 10px;
  border: 0;
  border-radius: var(--r-control);
  background: none;
  cursor: pointer;
  text-align: left;
  color: var(--ink);
  transition: background 0.4s var(--ease), transform 0.4s var(--ease);
}

.folk:active {
  transform: scale(0.985);
}

@media (hover: hover) and (pointer: fine) {
  .folk:hover {
    background: var(--tray);
  }
}

.folk-txt {
  min-width: 0;
}

.folk-txt b {
  display: block;
  font-size: 14px;
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.folk-txt i {
  display: block;
  font-style: normal;
  color: var(--ink-faint);
  font-size: 12px;
  margin-top: 1px;
}

.face {
  width: 34px;
  height: 34px;
  border-radius: 11px;
  background: var(--accent-wash);
  border: 1px solid var(--accent-line);
  color: var(--accent);
  display: grid;
  place-items: center;
  font-weight: 600;
  font-size: 14px;
  flex-shrink: 0;
}

.face.plus {
  background: var(--tray);
  border-color: var(--edge);
  color: var(--ink-mute);
}

.face.plus :deep(svg) {
  width: 17px;
  height: 17px;
}

/* ---- 近期列表 ---- */
.line {
  width: 100%;
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 14px;
  text-align: left;
  background: none;
  border: 0;
  border-top: 1px solid var(--edge);
  padding: 11px 0;
  cursor: pointer;
  color: var(--ink);
  transition: color 0.3s var(--ease-soft);
}

@media (hover: hover) and (pointer: fine) {
  .line:hover {
    color: var(--accent);
  }
}

.line b {
  font-weight: 500;
  font-size: 14px;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.line time {
  color: var(--ink-faint);
  font-size: 12px;
  flex-shrink: 0;
}

@media (max-width: 1140px) {
  .home-grid {
    grid-template-columns: 1fr;
  }
  .news {
    position: static;
  }
  .news-list {
    max-height: 480px;
  }
}

@media (max-width: 720px) {
  .page {
    gap: 20px;
  }
  .hero {
    max-width: none;
  }
  .rest {
    grid-template-columns: 1fr;
  }
}
</style>
