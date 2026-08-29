<template>
  <div class="page">
    <PageHeader
      :kicker="loadError ? '读取失败' : '报告解读'"
      :title="loading ? '正在读取报告' : loadError ? '报告暂时打不开' : report?.filename || '检查报告'"
      :desc="loading ? '正在核对报告内容与指标。' : loadError || reportMeta"
    >
      <template v-if="report && !loading" #extra>
        <div class="acts">
          <el-select v-model="profileId" placeholder="写入档案" style="width: 168px" size="default">
            <el-option v-for="p in profiles" :key="p.id" :label="p.displayName || '档案'" :value="p.id" />
          </el-select>
          <button class="btn btn-primary" type="button" :disabled="!profileId || importing" @click="doImport">
            {{ importing ? '写入中…' : '写入档案' }}
          </button>
          <button class="btn btn-ghost" type="button" :disabled="exporting" @click="exportPdf">
            {{ exporting ? '生成中…' : '导出 PDF' }}
          </button>
        </div>
      </template>
    </PageHeader>

    <section v-if="loading" class="panel report-state is-loading" aria-live="polite">
      <span class="state-icon" aria-hidden="true" v-html="ICONS.report"></span>
      <span class="chip accent">读取中</span>
      <h2>正在打开这份报告</h2>
      <p>系统正在载入原文、提取指标和参考区间。</p>
      <div class="state-lines" aria-hidden="true">
        <i v-for="n in 3" :key="n" class="skeleton"></i>
      </div>
    </section>

    <section v-else-if="loadError" class="panel report-state" role="alert">
      <span class="state-icon error" aria-hidden="true" v-html="ICONS.alert"></span>
      <span class="chip high">读取失败</span>
      <h2>没有拿到报告内容</h2>
      <p>{{ loadError }}</p>
      <div class="state-actions">
        <button class="btn btn-primary" type="button" @click="loadReport">重新读取</button>
        <router-link class="btn btn-ghost" :to="{ path: '/health', query: { tab: 'reports' } }">返回报告列表</router-link>
      </div>
    </section>

    <template v-else-if="report">

    <section class="report-overview" aria-label="报告概览">
      <div>
        <span>提取指标</span>
        <strong class="num">{{ items.length }}</strong>
      </div>
      <div>
        <span>超出参考</span>
        <strong class="num" :class="{ alert: abnormal }">{{ abnormal }}</strong>
      </div>
      <div>
        <span>档案状态</span>
        <strong>{{ profileId ? '已选择档案' : '尚未归档' }}</strong>
      </div>
    </section>

    <div ref="docRef" class="doc">
      <Shell class="paper">
        <header class="paper-head">
          <p class="eyebrow">康识 · 报告解读</p>
          <h2>{{ report.filename || '检查报告' }}</h2>
          <p>{{ reportMeta }}</p>
        </header>

        <!-- 逐项解读用标尺而不是表格：表格要读者自己拿结果去比参考范围，
             标尺把"差多远"直接画出来。 -->
        <section v-if="items.length" class="paper-section">
          <div class="section-head"><h3>逐项解读</h3></div>
          <div class="items">
            <article v-for="item in items" :key="item.id" class="item">
              <LabStrip
                :type="item.name"
                :value="numOf(item.value)"
                :unit="item.unit"
                :range="parseRange(item.refRange)"
                :flag-override="item.flag"
              />
              <p v-if="!parseRange(item.refRange)" class="raw-ref">
                结果 <b class="num">{{ item.value }}</b> {{ item.unit }}
                <span v-if="item.refRange">· 参考 {{ item.refRange }}</span>
              </p>
              <p v-if="item.interpretation" class="note">{{ item.interpretation }}</p>
            </article>
          </div>
        </section>

        <p v-else class="notice">
          <span v-html="ICONS.alert"></span>
          未能自动拆分指标，请看下方总体解读与原文。
        </p>

        <section class="paper-section summary">
          <div class="section-head"><h3>总体解读</h3></div>
          <div class="prose" v-html="renderMarkdown(report.summary || '', terms)"></div>
        </section>
      </Shell>
    </div>

    <Shell class="raw-shell">
      <template #head>
        <h3>原文摘录</h3>
        <button class="spacer btn btn-quiet btn-sm" type="button" @click="rawOpen = !rawOpen">
          {{ rawOpen ? '收起' : '展开' }}
        </button>
      </template>
      <pre v-show="rawOpen" class="raw">{{ report.rawText }}</pre>
    </Shell>

    <MedicalDisclaimer />
    </template>
  </div>
</template>
<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { importReportToProfile, reportDetail } from '@/api/reports'
import { listProfiles } from '@/api/health'
import type { ExamReport, ExamReportItem, HealthProfile } from '@/api/types'
import { renderMarkdown } from '@/utils/markdown'
import { formatWhen } from '@/utils/format'
import { useTerms } from '@/composables/useTerms'
import { ICONS } from '@/utils/icons'
import LabStrip from '@/components/LabStrip.vue'
import MedicalDisclaimer from '@/components/MedicalDisclaimer.vue'
import PageHeader from '@/components/PageHeader.vue'
import Shell from '@/components/Shell.vue'

const route = useRoute()
const report = ref<ExamReport | null>(null)
const items = ref<ExamReportItem[]>([])
const profiles = ref<HealthProfile[]>([])
const profileId = ref<number | undefined>(undefined)
const importing = ref(false)
const exporting = ref(false)
const docRef = ref<HTMLElement | null>(null)
const rawOpen = ref(false)
const loading = ref(true)
const loadError = ref('')

const abnormal = computed(() => items.value.filter((i) => i.flag === 'high' || i.flag === 'low').length)
const reportMeta = computed(() => {
  if (!report.value) return ''
  const parts = [formatWhen(report.value.createdAt)]
  if (items.value.length) parts.push(`拆出 ${items.value.length} 项指标`)
  if (abnormal.value) parts.push(`${abnormal.value} 项超出参考`)
  return parts.filter(Boolean).join(' · ')
})

/** 化验值可能带 "<"、"≥" 等前缀，取其中的数字部分画标尺。 */
function numOf(v: string) {
  const m = String(v).match(/-?\d+(\.\d+)?/)
  return m ? Number(m[0]) : null
}

/**
 * 解析报告原文里的参考范围字符串。化验单的写法很杂：
 * "3.9-6.1" / "3.9~6.1" / "3.9–6.1" 是双端区间；
 * "<7.8" / "≤7.8" 只有上界，下界按 0 处理（化验值不为负）。
 * 解析不出来就返回 null —— LabStrip 会退回纯数值展示，不编假刻度。
 */
function parseRange(text?: string): { low: number; high: number } | null {
  if (!text) return null
  const both = text.match(/(-?\d+(?:\.\d+)?)\s*[-~–—]\s*(-?\d+(?:\.\d+)?)/)
  if (both) {
    const low = Number(both[1])
    const high = Number(both[2])
    return high > low ? { low, high } : null
  }
  const upper = text.match(/[<≤]\s*(-?\d+(?:\.\d+)?)/)
  if (upper) return { low: 0, high: Number(upper[1]) }
  return null
}
const { terms, loadTerms } = useTerms()

let loadVersion = 0
async function loadReport() {
  const version = ++loadVersion
  loading.value = true
  loadError.value = ''
  report.value = null
  items.value = []
  profiles.value = []
  profileId.value = undefined
  rawOpen.value = false
  const id = Number(route.params.id)
  try {
    if (!Number.isInteger(id) || id <= 0) throw new Error('invalid-report-id')
    const res = await reportDetail(id)
    if (version !== loadVersion) return
    report.value = res.data.report
    items.value = res.data.items || []
    profileId.value = res.data.report.profileId || undefined
    void loadTerms()
    void loadProfilesForReport(version)
  } catch {
    if (version !== loadVersion) return
    loadError.value = '报告可能已被删除、无权访问，或服务暂时不可用。请返回列表确认后重试。'
  } finally {
    if (version === loadVersion) loading.value = false
  }
}

async function loadProfilesForReport(version: number) {
  try {
    const nextProfiles = (await listProfiles()).data || []
    if (version !== loadVersion) return
    profiles.value = nextProfiles
    if (!profileId.value && nextProfiles[0]?.id) profileId.value = nextProfiles[0].id
  } catch {
    if (version === loadVersion) profiles.value = []
  }
}

watch(() => route.params.id, () => void loadReport(), { immediate: true })

async function doImport() {
  if (!report.value || !profileId.value) return
  importing.value = true
  try {
    const d = (await importReportToProfile(report.value.id, profileId.value)).data
    ElMessage.success('已写入 ' + (d.imported || 0) + ' 条指标')
  } finally {
    importing.value = false
  }
}

/** 指标明细 + 总体解读导出为 A4 PDF（后端不做服务端渲染，导出在浏览器本地完成）。 */
async function exportPdf() {
  if (!docRef.value || exporting.value) return
  const root = document.documentElement
  const wasDark = root.classList.contains('dark')
  exporting.value = true
  try {
    if (wasDark) root.classList.remove('dark')
    await nextTick()
    const [{ jsPDF }, { default: html2canvas }] = await Promise.all([import('jspdf'), import('html2canvas')])
    const canvas = await html2canvas(docRef.value, {
      scale: 1.5,
      backgroundColor: getComputedStyle(root).getPropertyValue('--paper').trim(),
      useCORS: true,
    })
    const img = canvas.toDataURL('image/jpeg', 0.92)
    const pdf = new jsPDF({ orientation: 'p', unit: 'mm', format: 'a4' })
    const pageW = pdf.internal.pageSize.getWidth()
    const pageH = pdf.internal.pageSize.getHeight()
    const w = pageW
    const h = (canvas.height * w) / canvas.width
    let heightLeft = h
    let position = 0
    pdf.addImage(img, 'JPEG', 0, position, w, h)
    heightLeft -= pageH
    while (heightLeft > 0) {
      position -= pageH
      pdf.addPage()
      pdf.addImage(img, 'JPEG', 0, position, w, h)
      heightLeft -= pageH
    }
    const name = (report.value?.filename || '报告解读').replace(/\.[^.]+$/, '')
    pdf.save(`康识-${name}-${new Date().toISOString().slice(0, 10)}.pdf`)
    ElMessage.success('已导出 PDF')
  } catch (e) {
    console.warn('PDF 导出失败', e)
    ElMessage.error('导出失败，可改用浏览器「打印 → 另存为 PDF」')
  } finally {
    if (wasDark) {
      root.classList.add('dark')
      await nextTick()
    }
    exporting.value = false
  }
}
</script>

<style scoped>
.page {
  max-width: 1120px;
  display: grid;
  gap: var(--space-4);
}

.page :deep(.head) {
  margin-bottom: 0;
}

.page :deep(.head h1) {
  overflow-wrap: anywhere;
}

.report-state {
  min-height: 380px;
  display: grid;
  place-items: center;
  align-content: center;
  gap: var(--space-3);
  padding: var(--space-7) var(--space-5);
  text-align: center;
}

.report-state h2 {
  font-size: 22px;
}

.report-state > p {
  max-width: 480px;
  color: var(--ink-mute);
  line-height: 1.7;
}

.state-icon {
  display: grid;
  place-items: center;
  width: 48px;
  height: 48px;
  border-radius: var(--r-card);
  background: var(--accent-wash);
  color: var(--accent);
}

.state-icon.error {
  background: var(--flag-high-wash);
  color: var(--flag-high);
}

.state-icon :deep(svg) {
  width: 24px;
  height: 24px;
}

.state-lines {
  width: min(440px, 100%);
  display: grid;
  gap: var(--space-2);
  margin-top: var(--space-3);
}

.state-lines i {
  display: block;
  height: 12px;
}

.state-lines i:nth-child(2) {
  width: 82%;
}

.state-lines i:nth-child(3) {
  width: 64%;
}

.state-actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: var(--space-2);
  margin-top: var(--space-2);
}

.acts {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: var(--space-2);
}

.report-overview {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  border: 1px solid var(--edge);
  border-radius: var(--r-shell);
  background: var(--card);
  overflow: hidden;
}

.report-overview > div {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
  min-height: 62px;
  padding: 12px 16px;
  border-left: 1px solid var(--edge);
}

.report-overview > div:first-child {
  border-left: 0;
}

.report-overview span {
  color: var(--ink-mute);
  font-size: 12px;
}

.report-overview strong {
  color: var(--ink);
  font-size: 14px;
  font-weight: 650;
}

.report-overview strong.num {
  font-size: 20px;
}

.report-overview strong.alert {
  color: var(--flag-high);
}

.doc {
  width: min(780px, 100%);
  margin-inline: auto;
  min-width: 0;
}

.paper {
  border-top: 4px solid var(--accent);
  box-shadow: var(--shadow-2);
}

.raw-shell {
  width: min(780px, 100%);
  margin-inline: auto;
}

.paper-head {
  display: grid;
  gap: var(--space-2);
  padding-bottom: var(--space-5);
  border-bottom: 1px solid var(--edge);
}

.paper-head h2 {
  font-family: var(--font-display);
  font-size: 30px;
  line-height: 1.15;
  word-break: break-word;
}

.paper-head > p:last-child {
  color: var(--ink-faint);
  font-size: 13px;
}

.paper-section {
  padding-top: var(--space-5);
}

.summary {
  border-top: 1px solid var(--edge);
}

.items {
  display: grid;
}

.item {
  padding: var(--space-4) 0;
  border-top: 1px solid var(--edge);
}

.item:first-child {
  padding-top: 0;
  border-top: 0;
}

.raw-ref {
  margin-top: 8px;
  font-size: 13px;
  color: var(--ink-mute);
}

.raw-ref b {
  font-weight: 600;
  color: var(--ink);
}

.note {
  margin-top: var(--space-3);
  padding-top: var(--space-3);
  border-top: 1px solid var(--edge);
  font-size: 13px;
  line-height: 1.7;
  color: var(--ink-mute);
}

.raw {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-word;
  font-family: var(--font);
  font-size: 13px;
  line-height: 1.75;
  color: var(--ink-mute);
  max-height: 420px;
  overflow: auto;
}

@media (max-width: 720px) {
  .report-overview {
    grid-template-columns: 1fr;
  }

  .report-overview > div {
    border-left: 0;
    border-top: 1px solid var(--edge);
  }

  .report-overview > div:first-child {
    border-top: 0;
  }

  .paper-head h2 {
    font-size: 26px;
  }
  .acts {
    justify-content: flex-start;
  }
  .acts :deep(.el-select) {
    width: 100% !important;
  }
  .acts .btn {
    flex: 1;
  }
}

@media print {
  .page {
    max-width: none;
    padding-bottom: 0;
  }
  .page :deep(.head),
  .raw-shell,
  .page :deep(.disclaimer) {
    display: none;
  }
  .paper {
    padding: 0;
    border: 0;
    background: transparent;
  }
  .paper :deep(.core) {
    box-shadow: none;
  }
}
</style>
