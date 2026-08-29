<template>
  <div v-if="report" class="page">
    <PageHeader kicker="报告解读" :title="report.filename || '检查报告'" :desc="reportMeta">
      <template #back>
        <router-link class="back" :to="{ path: '/health', query: { tab: 'reports' } }">
          <span v-html="ICONS.chevron"></span>返回档案
        </router-link>
      </template>
      <template #extra>
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
  </div>
</template>
<script setup lang="ts">
import { computed, nextTick, onMounted, ref } from 'vue'
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

onMounted(async () => {
  const id = Number(route.params.id)
  const res = await reportDetail(id)
  report.value = res.data.report
  items.value = res.data.items || []
  profileId.value = res.data.report.profileId || undefined
  loadTerms()
  try {
    profiles.value = (await listProfiles()).data || []
    if (!profileId.value && profiles.value[0]?.id) profileId.value = profiles.value[0].id
  } catch {
    profiles.value = []
  }
})

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
  max-width: 720px;
  display: grid;
  gap: var(--space-5);
}

.page :deep(.head) {
  margin-bottom: 0;
}

.page :deep(.head h1) {
  overflow-wrap: anywhere;
}

.back {
  display: inline-flex;
  align-items: center;
  gap: var(--space-1);
  font-size: 13px;
  font-weight: 550;
  color: var(--ink-mute);
  transition: color 0.15s ease;
}

.back :deep(svg) {
  width: 16px;
  height: 16px;
  transform: rotate(90deg);
}

@media (hover: hover) and (pointer: fine) {
  .back:hover {
    color: var(--accent);
  }
}

.acts {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: var(--space-2);
}

.doc {
  min-width: 0;
}

.paper-head {
  display: grid;
  gap: var(--space-2);
  padding-bottom: var(--space-5);
  border-bottom: 1px solid var(--edge);
}

.paper-head h2 {
  font-family: var(--font-display);
  font-size: 32px;
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
