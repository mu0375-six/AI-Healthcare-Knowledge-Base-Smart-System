<template>
  <section class="panel metric-workbench" aria-labelledby="metric-workbench-title">
    <header class="workbench-head">
      <div class="workbench-title">
        <span>纵向健康记录</span>
        <div>
          <h3 id="metric-workbench-title">指标趋势与明细</h3>
          <small class="num">{{ metrics.length }} 条记录 · {{ chartOptions.length }} 项可绘制趋势</small>
        </div>
      </div>
      <div class="sec-btns">
        <div class="data-actions">
          <button class="btn btn-ghost btn-sm" type="button" @click="importVisible = true">CSV 导入</button>
          <button class="btn btn-ghost btn-sm" type="button" :disabled="!metrics.length" @click="exportCsv">导出 CSV</button>
          <button class="btn btn-ghost btn-sm" type="button" @click="$emit('from-report')">从报告写入</button>
        </div>
        <button class="btn btn-primary btn-sm" type="button" @click="openDialog">新增指标</button>
      </div>
    </header>

    <div v-if="trends.length" class="trend-summary">
      <span class="summary-label">最新状态</span>
      <ul class="trend-list">
        <li v-for="t in trends" :key="t.metricType" :class="['flag-' + t.latestFlag, { alert: t.alert }]">
          <span class="trend-copy">
            <span class="dot" :class="t.latestFlag"></span>
            <span class="note">{{ t.note }}</span>
          </span>
          <LabStrip
            variant="inline"
            :type="t.metricType"
            :value="t.latest"
            :unit="t.unit"
            :flag-override="t.latestFlag"
          />
        </li>
      </ul>
    </div>

    <section class="trend-block" aria-labelledby="trend-chart-title">
      <div class="subsection-head">
        <div>
          <h4 id="trend-chart-title">时间趋势</h4>
          <span>参考范围以绿色带标记</span>
        </div>
        <small>{{ plottable ? chartOptions.length + ' 项趋势' : '暂无可绘制数据' }}</small>
      </div>
      <!-- 不同单位分成小图：血糖不会再被血压的 0–100 量纲压平。 -->
      <div v-if="plottable" class="trend-charts">
        <article v-for="chart in chartOptions" :key="chart.name" class="trend-chart">
          <div class="chart-head">
            <b>{{ chart.name }}</b>
            <span>{{ chart.unit }}</span>
          </div>
          <v-chart :option="chart.option" autoresize class="chart-canvas" />
        </article>
      </div>
      <div v-else class="quiet">还没有可绘图的指标。点「新增指标」，或用「CSV 导入」一次性迁移历史记录。</div>
    </section>

    <section v-if="metrics.length" class="records-block" aria-labelledby="metric-records-title">
      <div class="subsection-head">
        <div>
          <h4 id="metric-records-title">测量明细</h4>
          <span>按记录时间查看数值与参考位置</span>
        </div>
        <small class="num">共 {{ metrics.length }} 条</small>
      </div>
      <el-table :data="metrics" empty-text="还没有指标">
        <el-table-column label="指标" min-width="180">
          <template #default="{ row }">
            <span class="metric-label">
              <b>{{ row.metricType }}</b>
              <small v-if="row.unit">{{ row.unit }}</small>
              <i v-if="row.note">{{ row.note }}</i>
            </span>
          </template>
        </el-table-column>
        <el-table-column label="数值与参考区间" min-width="230">
          <template #default="{ row }">
            <LabStrip variant="inline" :type="row.metricType" :value="row.value" :unit="row.unit" />
          </template>
        </el-table-column>
        <el-table-column label="时间" min-width="190">
          <template #default="{ row }">
            <span class="metric-when">
              <time>{{ formatWhen(row.recordedAt) }}</time>
              <button
                class="btn btn-quiet btn-sm metric-delete"
                type="button"
                :aria-label="`删除${row.metricType}在${formatWhen(row.recordedAt)}的记录`"
                @click="$emit('delete-metric', row.id)"
              >
                <span aria-hidden="true" v-html="ICONS.trash"></span>
                <span>删除</span>
              </button>
            </span>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <el-dialog v-model="visible" title="新增指标" width="440px">
      <div class="quick-types">
        <button
          v-for="t in metricTypes"
          :key="t"
          type="button"
          class="chip-btn qt"
          :class="{ on: form.metricType === t }"
          @click="pickType(t)"
        >
          {{ t }}
        </button>
      </div>
      <el-form label-width="80px">
        <el-form-item label="类型">
          <el-select v-model="form.metricType" filterable allow-create @change="onMetricType">
            <el-option v-for="t in metricTypes" :key="t" :label="t" :value="t" />
          </el-select>
        </el-form-item>
        <el-form-item label="数值"><el-input-number v-model="form.value" :precision="1" /></el-form-item>
        <el-form-item label="单位"><el-input v-model="form.unit" /></el-form-item>
        <el-form-item label="时间">
          <el-date-picker v-model="form.recordedAt" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" />
        </el-form-item>
        <el-form-item label="备注"><el-input v-model="form.note" placeholder="可选，例如：晨起测量" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" @click="submit">确定</el-button>
      </template>
    </el-dialog>

    <!-- CSV 导入：浏览器端解析预览，标注非法行，确认后走批量接口 -->
    <el-dialog v-model="importVisible" title="CSV 批量导入指标" width="640px">
      <p class="csv-hint">
        第一行为表头：<code>类型,数值,单位,时间,备注</code>（时间格式
        <code>2026-01-31 08:30</code>，缺省为现在；单位可省略）。导出的 CSV 可直接回灌。
      </p>
      <input ref="csvInput" type="file" accept=".csv,.txt" hidden @change="onCsvFile" />
      <div class="csv-pick">
        <button class="btn btn-ghost btn-sm" type="button" @click="csvInput?.click()">选择 CSV 文件</button>
        <span v-if="csvName">已读取 {{ csvName }}</span>
      </div>

      <el-alert
        v-if="csvError"
        :title="csvError"
        type="error"
        :closable="false"
        class="csv-alert"
      />

      <el-table v-if="previewRows.length" :data="pagedPreview" size="small" max-height="320" class="mt">
        <el-table-column label="状态" width="70">
          <template #default="{ row }">
            <el-tag v-if="row.ok" type="success" size="small">可导入</el-tag>
            <el-tag v-else type="danger" size="small">错误</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="type" label="类型" width="110" />
        <el-table-column prop="value" label="数值" width="80" />
        <el-table-column prop="unit" label="单位" width="90" />
        <el-table-column prop="when" label="时间" width="150" />
        <el-table-column prop="reason" label="问题说明" />
      </el-table>
      <p v-if="previewRows.length > PREVIEW_PAGE" class="csv-more">
        仅预览前 {{ Math.min(previewRows.length, PREVIEW_PAGE) }} 条，共 {{ previewRows.length }} 条。
      </p>

      <template #footer>
        <el-button @click="importVisible = false">取消</el-button>
        <el-button type="primary" :disabled="!okCount" :loading="importing" @click="doImport">
          导入 {{ okCount }} 条有效记录
        </el-button>
      </template>
    </el-dialog>
  </section>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import VChart from 'vue-echarts'
import type { HealthMetric, HealthProfile } from '@/api/types'
import { addMetricsBatch, type MetricTrend } from '@/api/health'
import { formatWhen } from '@/utils/format'
import { bandOf, unitOf } from '@/utils/metrics'
import { chartTheme } from '@/utils/charts'
import { ICONS } from '@/utils/icons'
import LabStrip from '@/components/LabStrip.vue'

const props = defineProps<{
  profile: HealthProfile
  metrics: HealthMetric[]
  trends: MetricTrend[]
}>()

const emit = defineEmits<{
  add: [payload: Partial<HealthMetric> & { profileId: number }]
  'delete-metric': [id: number]
  reload: []
  'from-report': []
}>()

/** 弹窗里的快捷指标 chips；子集关系便于窄屏换行。 */
const metricTypes = ['收缩压', '舒张压', '空腹血糖', '餐后血糖', '糖化血红蛋白', '体重', '总胆固醇', '甘油三酯']
const visible = ref(false)
const form = reactive({ metricType: '空腹血糖', value: 5.5, unit: 'mmol/L', recordedAt: '', note: '' })

function openDialog() {
  // 今天此刻做默认值：绝大多数手动录入都是「刚测完」
  const now = new Date()
  const pad = (n: number) => String(n).padStart(2, '0')
  form.recordedAt = `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())} ${pad(now.getHours())}:${pad(now.getMinutes())}:00`
  onMetricType(form.metricType)
  visible.value = true
}

function pickType(t: string) {
  form.metricType = t
  onMetricType(t)
}

function onMetricType(type: string) {
  const u = unitOf(type)
  if (u) form.unit = u
}

function submit() {
  if (!form.value && form.value !== 0) {
    ElMessage.warning('请填写数值')
    return
  }
  const payload: Partial<HealthMetric> & { profileId: number } = {
    profileId: props.profile.id!,
    metricType: form.metricType,
    value: form.value,
    unit: form.unit || unitOf(form.metricType),
    note: form.note,
  }
  if (form.recordedAt) payload.recordedAt = form.recordedAt
  emit('add', payload)
  visible.value = false
}

/* ---------------- CSV 导入 / 导出 ---------------- */

interface PreviewRow {
  ok: boolean
  type: string
  value: string
  unit: string
  when: string
  reason: string
  payload?: { metricType: string; value: number; unit?: string; recordedAt?: string; note?: string }
}

const importVisible = ref(false)
const importing = ref(false)
const csvInput = ref<HTMLInputElement | null>(null)
const csvName = ref('')
const csvError = ref('')
const previewRows = ref<PreviewRow[]>([])
const PREVIEW_PAGE = 50
const pagedPreview = computed(() => previewRows.value.slice(0, PREVIEW_PAGE))
const okCount = computed(() => previewRows.value.filter((r) => r.ok).length)

/** 跳过引号内的逗号，支持带逗号的备注字段。 */
function splitCsvLine(line: string): string[] {
  const out: string[] = []
  let cur = ''
  let inQuote = false
  for (let i = 0; i < line.length; i++) {
    const ch = line[i]
    if (ch === '"') {
      if (inQuote && line[i + 1] === '"') {
        cur += '"'
        i++
      } else {
        inQuote = !inQuote
      }
    } else if ((ch === ',' || ch === '\t') && !inQuote) {
      out.push(cur)
      cur = ''
    } else {
      cur += ch
    }
  }
  out.push(cur)
  return out.map((s) => s.trim())
}

function normalizeWhen(raw: string): { text: string; iso?: string } {
  const v = raw.replace(/^"|"$/g, '').trim()
  if (!v) return { text: '（默认当前）' }
  // Excel 常见的 2026/1/9 8:30 也接受，统一转成接口要的 YYYY-MM-DD HH:mm:ss
  const m = v.match(/^(\d{4})[-/.](\d{1,2})[-/.](\d{1,2})([ T](\d{1,2}):(\d{2})(?::(\d{2}))?)?$/)
  if (!m) return { text: v }
  const pad = (x: string) => x.padStart(2, '0')
  const iso = `${m[1]}-${pad(m[2])}-${pad(m[3])} ${pad(m[5] || '00')}:${m[6]}:${m[7] || '00'}`
  return { text: iso, iso }
}

function parseCsv(text: string) {
  csvError.value = ''
  const lines = text.replace(/^\uFEFF/, '').split(/\r?\n/).filter((l) => l.trim())
  if (lines.length < 2) {
    csvError.value = '文件里没有数据行（第一行是表头）'
    previewRows.value = []
    return
  }
  const header = splitCsvLine(lines[0]).map((h) => h.toLowerCase())
  const col = (...names: string[]) => header.findIndex((h) => names.some((n) => h.includes(n)))
  const iType = col('类型', 'type')
  const iValue = col('数值', '值', 'value')
  const iUnit = col('单位', 'unit')
  const iWhen = col('时间', '日期', 'time', 'date')
  const iNote = col('备注', 'note', 'remark')
  if (iType < 0 || iValue < 0) {
    csvError.value = '表头缺少「类型」或「数值」列，请参考第一行说明'
    previewRows.value = []
    return
  }
  const rows: PreviewRow[] = []
  for (const line of lines.slice(1)) {
    const cells = splitCsvLine(line)
    const type = (cells[iType] || '').trim()
    const valueRaw = (cells[iValue] || '').replace(/^[<>≈±]\s*/, '')
    const value = Number(valueRaw)
    const when = normalizeWhen(cells[iWhen] || '')
    const note = iNote >= 0 ? (cells[iNote] || '').trim() : ''
    let reason = ''
    if (!type) reason = '类型为空'
    else if (!valueRaw || Number.isNaN(value)) reason = '数值不是数字'
    else if (cells[iWhen] && cells[iWhen].trim() && !when.iso) reason = '时间格式不对'

    if (reason) {
      rows.push({ ok: false, type, value: valueRaw, unit: cells[iUnit] || '', when: when.text, reason })
      continue
    }
    const unit = (cells[iUnit] || '').trim() || unitOf(type) || undefined
    rows.push({
      ok: true,
      type,
      value: String(value),
      unit: unit || '',
      when: when.text,
      reason: '',
      payload: {
        metricType: type,
        value,
        unit,
        recordedAt: when.iso,
        note: note || undefined,
      },
    })
  }
  previewRows.value = rows
}

function onCsvFile(e: Event) {
  const file = (e.target as HTMLInputElement).files?.[0]
  ;(e.target as HTMLInputElement).value = ''
  if (!file) return
  csvName.value = file.name
  const reader = new FileReader()
  reader.onload = () => parseCsv(String(reader.result || ''))
  reader.readAsText(file, 'utf-8')
}

async function doImport() {
  const items = previewRows.value.filter((r) => r.ok && r.payload).map((r) => r.payload!)
  if (!items.length) return
  importing.value = true
  try {
    const n = (await addMetricsBatch(props.profile.id!, items)).data || 0
    importVisible.value = false
    previewRows.value = []
    csvName.value = ''
    ElMessage.success(`已导入 ${n} 条指标`)
    emit('reload')
  } catch {
    // 错误提示由 http 拦截器统一弹出
  } finally {
    importing.value = false
  }
}

function exportCsv() {
  const esc = (s: unknown) => {
    const v = s == null ? '' : String(s)
    return /[",\n]/.test(v) ? '"' + v.replace(/"/g, '""') + '"' : v
  }
  const lines = ['类型,数值,单位,时间,备注']
  for (const m of props.metrics) {
    lines.push([esc(m.metricType), m.value, esc(m.unit), esc(formatWhen(m.recordedAt)), esc(m.note)].join(','))
  }
  // BOM 让 Excel 正确识别 UTF-8 中文
  const blob = new Blob(['\uFEFF' + lines.join('\r\n')], { type: 'text/csv;charset=utf-8' })
  const a = document.createElement('a')
  a.href = URL.createObjectURL(blob)
  a.download = `${props.profile.displayName || '健康档案'}-指标.csv`
  a.click()
  URL.revokeObjectURL(a.href)
}

const chartOptions = computed(() => {
  void themeTick.value
  const t = chartTheme()
  const groups = new Map<string, { t: string; v: number }[]>()
  for (const m of [...props.metrics].sort((a, b) => String(a.recordedAt).localeCompare(String(b.recordedAt)))) {
    const arr = groups.get(m.metricType) || []
    arr.push({ t: formatWhen(m.recordedAt), v: m.value })
    groups.set(m.metricType, arr)
  }
  return Array.from(groups.entries())
    .filter(([, arr]) => arr.length >= 2)
    .map(([name, arr], index) => {
      const band = bandOf(name)
      const color = t.colors[index % t.colors.length]
      return {
        name,
        unit: props.metrics.find((m) => m.metricType === name)?.unit || unitOf(name),
        option: {
          animationDuration: 400,
          color: [color],
          tooltip: { trigger: 'axis' },
          grid: { left: 48, right: 16, top: 12, bottom: 30 },
          xAxis: {
            type: 'category',
            boundaryGap: false,
            data: arr.map((x) => x.t),
            axisLabel: { color: t.label, hideOverlap: true },
            axisLine: t.axisLine,
            axisTick: { show: false },
          },
          yAxis: {
            type: 'value',
            scale: true,
            axisLabel: { color: t.label },
            axisLine: { show: false },
            splitLine: t.splitLine,
          },
          series: [
            {
              name,
              type: 'line',
              smooth: true,
              showSymbol: arr.length < 12,
              symbolSize: 6,
              data: arr.map((x) => x.v),
              lineStyle: { width: 2, color },
              itemStyle: { color },
              ...(band
                ? {
                    markArea: {
                      silent: true,
                      itemStyle: { color: t.normalBand },
                      data: [[{ yAxis: band.low }, { yAxis: band.high }]],
                    },
                    markLine: {
                      silent: true,
                      symbol: 'none',
                      label: { show: false },
                      lineStyle: { color: t.normalLine, type: 'dashed', width: 1 },
                      data: [{ yAxis: band.low }, { yAxis: band.high }],
                    },
                  }
                : {}),
            },
          ],
        },
      }
    })
})

/** 单点记录由明细表表达，不用一块空图占版面。 */
const plottable = computed(() => chartOptions.value.length > 0)

// 暗色切换后重建配色（canvas 内文字不吃 CSS 变量，只能重算 option）
const themeTick = ref(0)
onMounted(() => window.addEventListener('theme-change', onThemeChange))
onBeforeUnmount(() => window.removeEventListener('theme-change', onThemeChange))
function onThemeChange() {
  themeTick.value++
}

defineExpose({ openDialog })
</script>

<style scoped>
.metric-workbench {
  overflow: hidden;
}

.workbench-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-4);
  padding: var(--space-4) var(--space-5);
  border-bottom: 1px solid var(--edge);
  background: var(--card);
}

.workbench-title {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  min-width: 0;
}

.workbench-title > span {
  padding: var(--space-1) var(--space-2);
  border: 1px solid var(--accent-line);
  border-radius: var(--r-chip);
  background: var(--accent-wash);
  color: var(--accent);
  font-size: 10px;
  font-weight: 700;
  white-space: nowrap;
}

.workbench-title h3 {
  margin: 0;
  color: var(--ink);
  font-size: 16px;
  font-weight: 680;
}

.workbench-title small {
  display: block;
  margin-top: var(--space-1);
  color: var(--ink-faint);
  font-size: 11px;
}

.sec-btns,
.data-actions {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  flex-wrap: wrap;
}

.trend-summary {
  display: grid;
  grid-template-columns: 112px minmax(0, 1fr);
  border-bottom: 1px solid var(--edge);
  background: var(--sunk);
}

.summary-label {
  padding: var(--space-3) var(--space-4);
  border-right: 1px solid var(--edge);
  color: var(--ink-mute);
  font-size: 11px;
  font-weight: 650;
}

.trend-list {
  list-style: none;
  margin: 0;
  padding: var(--space-2) var(--space-4);
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(min(360px, 100%), 1fr));
  gap: 0 var(--space-5);
}
.trend-list li {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
  min-width: 0;
  padding: var(--space-2) 0;
  font-size: 13px;
  line-height: 1.5;
  color: var(--ink-soft);
}
.trend-copy {
  display: inline-flex;
  align-items: flex-start;
  gap: var(--space-2);
  min-width: 0;
}
.trend-copy .note {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.trend-list li.alert.flag-high .note {
  color: var(--flag-high);
  font-weight: 600;
}
.trend-list li.alert.flag-low .note {
  color: var(--flag-low);
  font-weight: 600;
}
.trend-list .dot {
  width: 7px;
  height: 7px;
  border-radius: var(--r-pill);
  margin-top: var(--space-2);
  flex: none;
  background: var(--ink-faint);
}
.trend-list .dot.high {
  background: var(--flag-high);
}
.trend-list .dot.low {
  background: var(--flag-low);
}
.trend-list .dot.normal {
  background: var(--flag-normal);
}
.mt {
  margin-top: var(--space-4);
}
.quiet {
  color: var(--ink-faint);
  font-size: 13px;
  line-height: 1.7;
  padding: var(--space-5);
  background: var(--flag-none-wash);
}
.quick-types {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
  margin-bottom: var(--space-4);
}
.qt {
  font-size: 13px;
}
.qt.on {
  border-color: var(--accent);
  color: var(--accent);
  background: var(--accent-wash);
  font-weight: 500;
}
.csv-hint {
  margin: 0 0 var(--space-3);
  font-size: 13px;
  color: var(--ink-soft);
  line-height: 1.7;
}
.csv-pick {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  font-size: 13px;
  color: var(--ink-faint);
}
.csv-more {
  margin: var(--space-2) 0 0;
  font-size: 12px;
  color: var(--ink-faint);
}

.csv-alert {
  margin-top: var(--space-3);
}

.trend-charts {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(min(320px, 100%), 1fr));
  gap: 0;
}

.trend-chart {
  min-width: 0;
  padding: var(--space-3) var(--space-5) var(--space-2);
  border-right: 1px solid var(--edge);
  border-bottom: 1px solid var(--edge);
}

.trend-chart:nth-child(even),
.trend-chart:last-child {
  border-right: 0;
}

.chart-head {
  display: flex;
  align-items: baseline;
  gap: var(--space-2);
}

.chart-head b {
  font-size: 14px;
  font-weight: 680;
}

.chart-head span {
  color: var(--ink-faint);
  font-size: 12px;
}

.chart-canvas {
  height: 210px;
}

.trend-block,
.records-block {
  min-width: 0;
}

.records-block {
  border-top: 1px solid var(--edge);
}

.subsection-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-4);
  padding: var(--space-3) var(--space-5);
  background: var(--flag-none-wash);
}

.subsection-head h4 {
  margin: 0;
  color: var(--ink);
  font-size: 13px;
  font-weight: 680;
}

.subsection-head span,
.subsection-head small {
  color: var(--ink-faint);
  font-size: 11px;
}

.subsection-head span {
  display: block;
  margin-top: 2px;
}

.records-block :deep(.el-table) {
  padding: 0 var(--space-5) var(--space-3);
}

.records-block :deep(.el-table th.el-table__cell) {
  color: var(--ink-mute);
  font-size: 11px;
  font-weight: 650;
}

.metric-label,
.metric-when {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  min-width: 0;
}

.metric-label {
  flex-wrap: wrap;
}

.metric-label b {
  font-size: 14px;
}

.metric-label small,
.metric-label i {
  color: var(--ink-faint);
  font-size: 12px;
}

.metric-label i {
  width: 100%;
  overflow: hidden;
  font-style: normal;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.metric-when {
  justify-content: space-between;
}

.metric-when time {
  color: var(--ink-mute);
  font-size: 12px;
  white-space: nowrap;
}

.metric-delete {
  color: var(--flag-high);
}

@media (hover: hover) and (pointer: fine) {
  .metric-delete {
    opacity: 0;
  }

  :deep(.el-table__row:hover) .metric-delete,
  :deep(.el-table__row:focus-within) .metric-delete,
  .metric-delete:focus-visible {
    opacity: 1;
  }
}

@media (max-width: 720px) {
  .workbench-head {
    align-items: stretch;
    flex-direction: column;
    padding: var(--space-4);
  }

  .workbench-title {
    align-items: flex-start;
  }

  .workbench-title > span {
    display: none;
  }

  .sec-btns {
    align-items: stretch;
    flex-direction: column-reverse;
  }

  .sec-btns > .btn {
    width: 100%;
  }

  .data-actions {
    display: grid;
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  .data-actions .btn {
    min-width: 0;
    padding-inline: var(--space-2);
  }

  .trend-summary {
    grid-template-columns: 1fr;
  }

  .summary-label {
    padding: var(--space-2) var(--space-4);
    border-right: 0;
    border-bottom: 1px solid var(--edge);
  }

  .trend-list {
    grid-template-columns: 1fr;
    padding-inline: var(--space-4);
  }

  .trend-list li {
    align-items: flex-start;
    flex-direction: column;
  }

  .trend-copy .note {
    white-space: normal;
  }

  .subsection-head {
    padding-inline: var(--space-4);
  }

  .trend-chart {
    padding-inline: var(--space-4);
    border-right: 0;
  }

  .chart-canvas {
    height: 185px;
  }

  .records-block :deep(.el-table) {
    padding-inline: var(--space-2);
  }
}

@media (max-width: 420px) {
  .workbench-title h3 {
    font-size: 15px;
  }

  .data-actions {
    grid-template-columns: 1fr 1fr;
  }

  .data-actions .btn:last-child {
    grid-column: 1 / -1;
  }

  .subsection-head small {
    display: none;
  }
}
</style>
