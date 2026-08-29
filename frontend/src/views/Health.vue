<template>
  <div class="page">
    <PageHeader
      kicker="家庭健康档案"
      title="健康档案"
      desc="按成员集中查看体征、报告、病史与跟进建议。"
    >
      <template #extra>
        <button class="btn btn-primary" type="button" @click="createVisible = true">
          <span aria-hidden="true" v-html="ICONS.plus"></span>新建档案
        </button>
      </template>
    </PageHeader>

    <div class="page-content">

      <MemberBar
        :profiles="profiles"
        :active-id="currentId"
        @select="select"
        @create="createVisible = true"
      />

      <div v-if="!current" class="panel empty">
        <span aria-hidden="true" v-html="ICONS.file"></span>
        <h3>为家人建立第一份档案</h3>
        <p>比如「爸爸」「妈妈」或「小宝」。建好后记血压、血糖，或把体检报告传进来自动填。</p>
        <button class="btn btn-primary" type="button" @click="createVisible = true">现在新建</button>
      </div>

    <template v-else>
      <DossierCard
        :profile="current"
        :form="form"
        :editing="editing"
        :removable="profiles.length > 1"
        :relations="relations"
        @ask="goAsk"
        @toggle-edit="editing = !editing"
        @save="saveProfile"
        @delete="onDelete"
      />

      <AlertCenter v-if="alerts.length" :alerts="alerts" :profiles="profiles" @locate="locateProfile" />

      <!-- 分段而不是一路堆叠：指标/报告/病史/建议四件事各自完整，
           叠在一页会让人滚到失去方位。 -->
      <div class="workspace-nav">
        <div class="workspace-context">
          <span>档案内容</span>
          <strong>{{ current.displayName }}</strong>
        </div>
        <div class="tabs" role="tablist">
          <button
            v-for="t in TABS"
            :key="t.key"
            class="tab"
            type="button"
            role="tab"
            :id="`health-tab-${t.key}`"
            :aria-selected="tab === t.key"
            :aria-controls="`health-panel-${t.key}`"
            :tabindex="tab === t.key ? 0 : -1"
            :class="{ on: tab === t.key }"
            @click="setTab(t.key)"
            @keydown="onTabKeydown($event, t.key)"
          >
            <span>{{ t.label }}</span>
            <em class="num" :class="{ zero: t.count(counts) === 0 }">{{ t.count(counts) }}</em>
          </button>
        </div>
      </div>

      <!-- 指标 -->
      <section
        id="health-panel-metrics"
        v-show="tab === 'metrics'"
        class="stack tab-panel"
        role="tabpanel"
        aria-labelledby="health-tab-metrics"
        tabindex="0"
      >
        <div class="overview-head">
          <div>
            <span class="section-kicker">关键体征</span>
            <h2>最新指标</h2>
          </div>
          <span class="coverage num">{{ filledCards.length }} / {{ metricCards.length }} 已记录</span>
        </div>
<!-- 只为有数据的指标发卡。没记录过的合并成一行添加入口 ——
             一张写着"还没有记录"的空卡片不承载任何信息，只占版面。 -->
        <div v-if="filledCards.length" class="cards">
          <article v-for="c in filledCards" :key="c.type" class="tile card-pad metric-card" :class="'flag-' + c.flag">
            <LabStrip :type="c.type" :value="c.latest" :unit="c.unit" />
            <p class="delta"><span>较上次</span><b class="num">{{ c.delta == null ? '首条记录' : `${c.delta > 0 ? '+' : ''}${c.delta}` }}</b></p>
          </article>
        </div>

        <button v-if="emptyTypes.length" class="add-row" type="button" @click="openAdd">
          <span class="add-ico" aria-hidden="true" v-html="ICONS.plus"></span>
          <span class="add-txt">
            <b>还可以记录：{{ emptyTypes.join(' · ') }}</b>
            <i>记两次以上才看得出走势</i>
          </span>
        </button>

        <MetricTrendSection
          id="metric-trend"
          ref="trendSection"
          :profile="current"
          :metrics="metrics"
          :trends="trends"
          @add="onAddMetric"
          @delete-metric="onDelMetric"
          @reload="reloadMetrics"
          @from-report="goReports"
        />
      </section>

      <!-- 报告：原「报告解读」页并入这里。上传报告产出的指标本来
           就写进档案，拆成两个目的地会让一条流程跨页。 -->
      <Shell
        id="health-panel-reports"
        v-show="tab === 'reports'"
        role="tabpanel"
        aria-labelledby="health-tab-reports"
        tabindex="0"
      >
        <template #head>
          <div class="report-title">
            <span class="section-kicker">档案附件</span>
            <h3>体检与化验报告</h3>
          </div>
          <router-link
            class="spacer btn btn-primary btn-sm"
            :to="{ path: '/reports/upload', query: { profileId: String(currentId) } }"
          >
            <span aria-hidden="true" v-html="ICONS.upload"></span>上传报告
          </router-link>
        </template>

        <div v-if="reportsLoading" class="stack">
          <div v-for="n in 3" :key="n" class="skeleton" style="height: 58px; border-radius: var(--r-card)"></div>
        </div>

        <div v-else-if="!reports.length" class="empty">
          <span aria-hidden="true" v-html="ICONS.report"></span>
          <h3>还没有报告</h3>
          <p>传一份体检单或化验单，系统会抽出指标、标出高低，并写进这份档案。</p>
          <router-link
            class="btn btn-primary"
            :to="{ path: '/reports/upload', query: { profileId: String(currentId) } }"
          >上传报告</router-link>
        </div>

        <button
          v-for="r in reports"
          :key="r.id"
          class="rep"
          type="button"
          @click="router.push('/reports/' + r.id)"
        >
          <span class="rep-ico" aria-hidden="true" v-html="ICONS.report"></span>
          <span class="rep-main">
            <b>{{ r.filename }}</b>
            <i v-if="r.summary">{{ brief(r.summary) }}</i>
          </span>
          <time>{{ formatWhen(r.createdAt) }}</time>
        </button>
      </Shell>

      <!-- 病史 -->
      <section
        id="health-panel-history"
        v-show="tab === 'history'"
        class="tab-panel"
        role="tabpanel"
        aria-labelledby="health-tab-history"
        tabindex="0"
      >
        <HistorySection :profile="current" :histories="histories" @add="onAddHist" @delete-history="onDelHist" />
      </section>

      <!-- 建议 -->
      <section
        id="health-panel-advice"
        v-show="tab === 'advice'"
        class="tab-panel"
        role="tabpanel"
        aria-labelledby="health-tab-advice"
        tabindex="0"
      >
        <AdviceSection
          :profile="current"
          :metrics="metrics"
          :advice="advice"
          :basis="adviceBasis"
          :loading="adviceLoading"
          :terms="terms"
          @generate="onAdvice"
        />
      </section>
    </template>

    <CreateProfileDialog
      :visible="createVisible"
      :relations="relations"
      @close="createVisible = false"
      @create="onCreate"
    />
    <MedicalDisclaimer />
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  addHistory,
  addMetric,
  createProfile,
  deleteHistory,
  deleteMetric,
  deleteProfile,
  generateAdvice,
  listAlerts,
  listHistories,
  listMetrics,
  listProfiles,
  updateProfileById,
  listTrends,
  type MetricAlertItem,
  type MetricTrend,
} from '@/api/health'
import { listReports } from '@/api/reports'
import type { ExamReport, HealthHistory, HealthMetric, HealthProfile } from '@/api/types'
import { formatWhen } from '@/utils/format'
import { CARD_TYPES, flagOf, loadReference, unitOf } from '@/utils/metrics'
import { useTerms } from '@/composables/useTerms'
import { ICONS } from '@/utils/icons'
import LabStrip from '@/components/LabStrip.vue'
import MedicalDisclaimer from '@/components/MedicalDisclaimer.vue'
import PageHeader from '@/components/PageHeader.vue'
import Shell from '@/components/Shell.vue'
import MemberBar from '@/components/health/MemberBar.vue'
import AlertCenter from '@/components/health/AlertCenter.vue'
import DossierCard from '@/components/health/DossierCard.vue'
import MetricTrendSection from '@/components/health/MetricTrendSection.vue'
import HistorySection from '@/components/health/HistorySection.vue'
import AdviceSection from '@/components/health/AdviceSection.vue'
import CreateProfileDialog from '@/components/health/CreateProfileDialog.vue'

const route = useRoute()
const router = useRouter()

const relations = ['本人', '配偶', '父亲', '母亲', '子女', '其他']
const profiles = ref<HealthProfile[]>([])
const currentId = ref<number | null>(null)
const editing = ref(false)
const form = reactive<HealthProfile>({ displayName: '我', relation: '本人', sharedToAdmin: false })
const metrics = ref<HealthMetric[]>([])
const trends = ref<MetricTrend[]>([])
const alerts = ref<MetricAlertItem[]>([])
const histories = ref<HealthHistory[]>([])
const reports = ref<ExamReport[]>([])
const reportsLoading = ref(false)
const advice = ref('')
const adviceBasis = ref('')
const adviceLoading = ref(false)
const createVisible = ref(false)
const current = computed(() => profiles.value.find((p) => p.id === currentId.value) || null)
const { terms, loadTerms } = useTerms()

type TabKey = 'metrics' | 'reports' | 'history' | 'advice'
const TABS: { key: TabKey; label: string; count: (c: Counts) => number }[] = [
  { key: 'metrics', label: '指标', count: (c) => c.metrics },
  { key: 'reports', label: '报告', count: (c) => c.reports },
  { key: 'history', label: '病史', count: (c) => c.histories },
  { key: 'advice', label: '建议', count: (c) => c.advice },
]
type Counts = { metrics: number; reports: number; histories: number; advice: number }
const counts = computed<Counts>(() => ({
  metrics: metrics.value.length,
  reports: reports.value.length,
  histories: histories.value.length,
  advice: advice.value ? 1 : 0,
}))

// tab 存在 query 里：/reports 的重定向要能直接落到报告分段，
// 刷新与前进后退也保持在同一分段。
function isTabKey(value: unknown): value is TabKey {
  return TABS.some((item) => item.key === value)
}

const tab = ref<TabKey>(isTabKey(route.query.tab) ? route.query.tab : 'metrics')
function setTab(k: TabKey) {
  tab.value = k
  router.replace({ query: { ...route.query, tab: k } })
}

function onTabKeydown(event: KeyboardEvent, key: TabKey) {
  if (!['ArrowLeft', 'ArrowRight', 'Home', 'End'].includes(event.key)) return
  event.preventDefault()
  const index = TABS.findIndex((item) => item.key === key)
  const nextIndex =
    event.key === 'Home'
      ? 0
      : event.key === 'End'
        ? TABS.length - 1
        : (index + (event.key === 'ArrowRight' ? 1 : -1) + TABS.length) % TABS.length
  const nextKey = TABS[nextIndex].key
  setTab(nextKey)
  nextTick(() => document.getElementById(`health-tab-${nextKey}`)?.focus())
}
watch(
  () => route.query.tab,
  (v) => {
    if (isTabKey(v) && v !== tab.value) tab.value = v
  },
)

const filledCards = computed(() => metricCards.value.filter((c) => c.latest != null))
const emptyTypes = computed(() => metricCards.value.filter((c) => c.latest == null).map((c) => c.type))
const trendSection = ref<InstanceType<typeof MetricTrendSection>>()

/** 「还可以记录」直接唤起趋势区里已有的新增指标对话框，不另造一个。 */
function openAdd() {
  trendSection.value?.openDialog?.()
}

const metricCards = computed(() =>
  CARD_TYPES.map((type) => {
    const rows = metrics.value
      .filter((m) => m.metricType === type)
      .sort((a, b) => String(b.recordedAt).localeCompare(String(a.recordedAt)))
    const latest = rows[0]
    const prev = rows[1]
    const value = latest?.value
    return {
      type,
      latest: value ?? null,
      unit: latest?.unit || unitOf(type),
      flag: flagOf(type, value),
      delta: latest && prev ? Number((latest.value - prev.value).toFixed(1)) : null,
    }
  }),
)

onMounted(async () => {
  const prefer = Number(route.query.id) || undefined
  await loadReference()
  await loadProfiles(prefer)
  if (route.query.new === '1') createVisible.value = true
  await loadTerms()
  alerts.value = (await listAlerts()).data || []
  loadReports()
})

/** 报告列表按当前档案过滤：后端返回全部，这里只留归属这份档案的。 */
/** 摘要可能很长，列表里只取第一句/首行，超长截断。 */
function brief(text: string) {
  const line = text.replace(/[#*`>]/g, '').split(/[\n。]/).find((x) => x.trim()) || ''
  return line.length > 42 ? line.slice(0, 42) + '…' : line
}

async function loadReports() {
  reportsLoading.value = true
  try {
    const all = (await listReports()).data || []
    reports.value = all.filter((r) => !r.profileId || r.profileId === currentId.value)
  } catch {
    reports.value = []
  } finally {
    reportsLoading.value = false
  }
}

async function loadProfiles(preferId?: number) {
  profiles.value = (await listProfiles()).data || []
  const next = preferId || currentId.value || profiles.value[0]?.id || null
  if (next) await select(next)
}

async function select(id: number) {
  currentId.value = id
  editing.value = false
  const p = profiles.value.find((x) => x.id === id)
  if (p) assignForm(p)
  metrics.value = (await listMetrics(id)).data || []
  trends.value = (await listTrends(id)).data || []
  histories.value = (await listHistories(id)).data || []
  advice.value = p?.lastAdvice || ''
  adviceBasis.value = p?.adviceAt ? '上次生成于 ' + formatWhen(p.adviceAt) : ''
  loadReports()
}

function assignForm(p: HealthProfile) {
  form.id = p.id
  form.displayName = p.displayName
  form.relation = p.relation
  form.age = p.age
  form.sex = p.sex
  form.heightCm = p.heightCm
  form.weightKg = p.weightKg
  form.allergies = p.allergies
  form.sharedToAdmin = !!p.sharedToAdmin
}

function goAsk() {
  if (!currentId.value) return
  router.push({ path: '/chat', query: { profileId: String(currentId.value) } })
}

function goReports() {
  router.push({ path: '/reports/upload', query: { profileId: String(currentId.value) } })
}

async function onCreate(payload: { displayName: string; relation: string; age?: number; sex: string }) {
  if (!payload.displayName) {
    ElMessage.warning('请填写称呼，例如「爸爸」')
    return
  }
  const created = (await createProfile(payload)).data
  createVisible.value = false
  ElMessage.success('档案已创建，可以开始填写指标')
  await loadProfiles(created.id)
}

async function saveProfile() {
  if (!currentId.value) return
  const saved = (await updateProfileById(currentId.value, { ...form })).data
  ElMessage.success('已保存「' + (saved.displayName || '档案') + '」')
  editing.value = false
  await loadProfiles(currentId.value)
}

async function onDelete() {
  if (!currentId.value) return
  await ElMessageBox.confirm('将删除该档案及其指标、病史，不可恢复。', '删除档案', { type: 'warning' })
  await deleteProfile(currentId.value)
  currentId.value = null
  await loadProfiles()
}

async function onAddMetric(payload: Partial<HealthMetric> & { profileId: number }) {
  await addMetric(payload)
  await reloadMetrics()
}

async function reloadMetrics() {
  if (currentId.value) {
    metrics.value = (await listMetrics(currentId.value)).data || []
    trends.value = (await listTrends(currentId.value)).data || []
    alerts.value = (await listAlerts()).data || []
  }
}

async function locateProfile(profileId: number | null) {
  if (profileId && profileId !== currentId.value) {
    await select(profileId)
  }
  setTab('metrics')
  await nextTick()
  document.getElementById('metric-trend')?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}

async function onDelMetric(id: number) {
  await deleteMetric(id)
  if (currentId.value) {
    metrics.value = (await listMetrics(currentId.value)).data || []
    trends.value = (await listTrends(currentId.value)).data || []
  }
}

async function onAddHist(payload: Partial<HealthHistory> & { profileId: number }) {
  await addHistory(payload)
  histories.value = (await listHistories(payload.profileId)).data || []
}

async function onDelHist(id: number) {
  await deleteHistory(id)
  if (currentId.value) histories.value = (await listHistories(currentId.value)).data || []
}

async function onAdvice() {
  if (!currentId.value) return
  adviceLoading.value = true
  try {
    const d = (await generateAdvice(currentId.value)).data
    advice.value = d.advice
    adviceBasis.value = d.basis || ''
    profiles.value = (await listProfiles()).data || []
  } finally {
    adviceLoading.value = false
  }
}
</script>

<style scoped>
.page-content {
  display: grid;
  gap: var(--space-4);
}

/* ---- 档案工作区导航 ---- */
.workspace-nav {
  display: grid;
  grid-template-columns: 148px minmax(0, 1fr);
  min-width: 0;
  border: 1px solid var(--edge);
  border-radius: var(--r-shell);
  background: var(--card);
  box-shadow: var(--shadow-1);
  overflow: hidden;
}

.workspace-context {
  display: flex;
  flex-direction: column;
  justify-content: center;
  padding: var(--space-3) var(--space-4);
  border-right: 1px solid var(--edge);
  background: var(--sunk);
}

.workspace-context span {
  color: var(--ink-faint);
  font-size: 11px;
}

.workspace-context strong {
  margin-top: var(--space-1);
  color: var(--ink);
  font-size: 14px;
}

.tabs {
  display: grid;
  grid-template-columns: repeat(4, minmax(112px, 1fr));
  min-width: 0;
  overflow-x: auto;
}

.tab {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: var(--space-2);
  border: 0;
  border-right: 1px solid var(--edge);
  background: none;
  cursor: pointer;
  padding: var(--space-3) var(--space-4);
  color: var(--ink-mute);
  font-size: 13px;
  font-weight: 600;
  white-space: nowrap;
  transition: background 0.16s var(--ease-soft), color 0.16s var(--ease-soft), box-shadow 0.16s var(--ease-soft);
}

.tab:last-child {
  border-right: 0;
}

.tab.on {
  background: var(--accent-wash);
  color: var(--accent);
  box-shadow: inset 0 -3px 0 var(--accent);
}

.tab em {
  min-width: 20px;
  padding: 1px var(--space-1);
  border: 1px solid var(--flag-none-line);
  border-radius: var(--r-chip);
  background: var(--flag-none-wash);
  font-style: normal;
  font-size: 11px;
  color: var(--ink-mute);
  text-align: center;
}

.tab.on em {
  border-color: var(--accent-line);
  background: var(--card);
  color: var(--accent);
}

.tab em.zero {
  color: var(--ink-faint);
  opacity: 0.7;
}

@media (hover: hover) and (pointer: fine) {
  .tab:not(.on):hover {
    background: var(--tray);
    color: var(--ink);
  }
}

/* ---- 指标概览 ---- */
.tab-panel {
  min-width: 0;
}

.overview-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: var(--space-4);
  padding: var(--space-1) 0 0;
}

.section-kicker {
  display: block;
  margin-bottom: var(--space-1);
  color: var(--accent);
  font-size: 11px;
  font-weight: 700;
}

.overview-head h2,
.report-title h3 {
  margin: 0;
  font-size: 17px;
  font-weight: 680;
}

.coverage {
  padding: var(--space-1) var(--space-2);
  border: 1px solid var(--edge);
  border-radius: var(--r-chip);
  color: var(--ink-mute);
  font-size: 11px;
}

.cards {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: var(--space-3);
  align-items: start;
}

.card-pad {
  padding: var(--space-4) var(--space-5);
}

.metric-card {
  --metric-tone: var(--flag-none);
  border-top: 3px solid var(--metric-tone);
  box-shadow: inset 0 1px 0 var(--metric-wash, var(--flag-none-wash));
}

.metric-card.flag-high {
  --metric-tone: var(--flag-high);
  --metric-wash: var(--flag-high-wash);
}

.metric-card.flag-low {
  --metric-tone: var(--flag-low);
  --metric-wash: var(--flag-low-wash);
}

.metric-card.flag-normal {
  --metric-tone: var(--flag-normal);
  --metric-wash: var(--flag-normal-wash);
}

.delta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-2);
  margin: var(--space-3) 0 0;
  padding-top: var(--space-2);
  border-top: 1px solid var(--edge);
  color: var(--ink-faint);
  font-size: 11px;
}

.delta b {
  color: var(--metric-tone);
  font-size: 12px;
  font-weight: 650;
}

.add-row {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  width: 100%;
  padding: var(--space-3) var(--space-4);
  border: 1.5px dashed var(--edge-strong);
  border-radius: var(--r-card);
  background: transparent;
  cursor: pointer;
  text-align: left;
  color: var(--ink);
  transition: border-color 0.16s var(--ease-soft), background 0.16s var(--ease-soft), transform 0.12s var(--ease-out);
}

.add-row:active {
  transform: scale(0.99);
}

@media (hover: hover) and (pointer: fine) {
  .add-row:hover {
    border-color: var(--accent-line);
    background: var(--accent-wash);
  }
}

.add-ico {
  display: grid;
  place-items: center;
  width: 32px;
  height: 32px;
  border-radius: var(--r-control);
  background: var(--accent-wash);
  color: var(--accent);
  flex-shrink: 0;
}

.add-ico :deep(svg) {
  width: 17px;
  height: 17px;
  display: block;
}

.add-txt b {
  display: block;
  font-size: 14px;
  font-weight: 620;
}

.add-txt i {
  display: block;
  font-style: normal;
  font-size: 12px;
  color: var(--ink-faint);
  margin-top: var(--space-1);
}

/* ---- 报告行 ---- */
.report-title {
  min-width: 0;
}

.rep {
  width: 100%;
  display: flex;
  align-items: center;
  gap: var(--space-3);
  text-align: left;
  background: none;
  border: 0;
  border-top: 1px solid var(--edge);
  padding: var(--space-3) var(--space-2);
  cursor: pointer;
  color: var(--ink);
  transition: color 0.15s var(--ease-soft), background 0.15s var(--ease-soft), box-shadow 0.15s var(--ease-soft);
}

@media (hover: hover) and (pointer: fine) {
  .rep:hover {
    background: var(--accent-wash);
    color: var(--accent);
    box-shadow: inset 3px 0 0 var(--accent);
  }
}

.rep-ico {
  color: var(--ink-faint);
  flex-shrink: 0;
}

.rep-ico :deep(svg) {
  width: 20px;
  height: 20px;
  display: block;
}

.rep-main {
  flex: 1;
  min-width: 0;
}

.rep-main b {
  display: block;
  font-weight: 620;
  font-size: 14px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.rep-main i {
  display: block;
  font-style: normal;
  color: var(--ink-faint);
  font-size: 12px;
  margin-top: var(--space-1);
}

.rep time {
  color: var(--ink-faint);
  font-size: 12px;
  flex-shrink: 0;
}

@media (max-width: 720px) {
  .page-content {
    gap: var(--space-3);
  }

  .workspace-nav {
    grid-template-columns: 1fr;
  }

  .workspace-context {
    flex-direction: row;
    align-items: baseline;
    justify-content: space-between;
    gap: var(--space-3);
    padding: var(--space-2) var(--space-3);
    border-right: 0;
    border-bottom: 1px solid var(--edge);
  }

  .workspace-context strong {
    margin-top: 0;
  }

  .tabs {
    grid-template-columns: repeat(4, minmax(78px, 1fr));
  }

  .tab {
    gap: var(--space-1);
    padding: var(--space-3) var(--space-2);
    font-size: 12px;
  }

  .tab em {
    min-width: 18px;
  }

  .overview-head {
    align-items: center;
  }

  .overview-head h2 {
    font-size: 16px;
  }

  .cards {
    grid-template-columns: 1fr;
  }

  .card-pad {
    padding: var(--space-4);
  }

  .rep {
    flex-wrap: wrap;
    gap: var(--space-2);
  }
  .rep time {
    margin-left: var(--space-6);
  }
}

@media (max-width: 420px) {
  .tab {
    flex-direction: column;
    gap: 2px;
  }

  .coverage {
    white-space: nowrap;
  }

  .add-row {
    align-items: flex-start;
  }
}
</style>
