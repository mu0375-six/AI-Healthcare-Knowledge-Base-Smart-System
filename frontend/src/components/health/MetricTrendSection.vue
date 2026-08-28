<template>
  <section class="panel core-pad block">
    <div class="sec">
      <h3>指标趋势</h3>
      <div class="sec-btns">
        <button class="btn btn-ghost slim" type="button" @click="importVisible = true">CSV 导入</button>
        <button class="btn btn-ghost slim" type="button" :disabled="!metrics.length" @click="exportCsv">导出 CSV</button>
        <button class="btn btn-ghost slim" type="button" @click="$emit('from-report')">从报告写入</button>
        <button class="btn btn-primary slim" type="button" @click="openDialog">新增指标</button>
      </div>
    </div>
    <ul v-if="trends.length" class="trend-list">
      <li v-for="t in trends" :key="t.metricType" :class="{ alert: t.alert }">
        <span class="dot" :class="t.latestFlag"></span>
        <span class="note">{{ t.note }}</span>
      </li>
    </ul>
<!-- 至少两个点才画图：一个点的折线图是一块 260px 高的空白，
         下面的明细表已经把这条记录说清楚了 -->
    <v-chart v-if="plottable" :option="chartOption" autoresize style="height: 260px" />
    <div v-else class="quiet">还没有可绘图的指标。点「新增指标」，或用上面的「CSV 导入」一次性迁移历史记录。</div>
    <el-table v-if="metrics.length" :data="metrics" class="mt" empty-text="还没有指标">
      <el-table-column prop="metricType" label="类型" width="120" />
      <el-table-column prop="value" label="数值" width="90" />
      <el-table-column prop="unit" label="单位" width="90" />
      <el-table-column label="高低" width="80">
        <template #default="{ row }">{{ flagText(flagOf(row.metricType, row.value)) }}</template>
      </el-table-column>
      <el-table-column label="记录时间">
        <template #default="{ row }">{{ formatWhen(row.recordedAt) }}</template>
      </el-table-column>
      <el-table-column prop="note" label="备注" />
      <el-table-column label="" width="80">
        <template #default="{ row }">
          <el-button text type="danger" @click="$emit('delete-metric', row.id)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="visible" title="新增指标" width="440px">
      <div class="quick-types">
        <button
          v-for="t in metricTypes"
          :key="t"
          type="button"
          class="qt"
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
        <button class="btn btn-ghost slim" type="button" @click="csvInput?.click()">选择 CSV 文件</button>
        <span v-if="csvName">已读取 {{ csvName }}</span>
      </div>

      <el-alert
        v-if="csvError"
        :title="csvError"
        type="error"
        :closable="false"
        style="margin-top: 10px"
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
import { flagOf, flagText, unitOf } from '@/utils/metrics'
import { CHART_COLORS, chartTheme } from '@/utils/charts'

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

/** 有任一指标累计到两条以上记录，才谈得上"趋势"。 */
const plottable = computed(() => {
  const byType = new Map<string, number>()
  for (const m of props.metrics) byType.set(m.metricType, (byType.get(m.metricType) || 0) + 1)
  return [...byType.values()].some((n) => n >= 2)
})

const chartOption = computed(() => {
  void themeTick.value
  const t = chartTheme()
  const groups = new Map<string, { t: string; v: number }[]>()
  for (const m of [...props.metrics].sort((a, b) => String(a.recordedAt).localeCompare(String(b.recordedAt)))) {
    const arr = groups.get(m.metricType) || []
    arr.push({ t: formatWhen(m.recordedAt), v: m.value })
    groups.set(m.metricType, arr)
  }
  const times = Array.from(new Set([...groups.values()].flat().map((x) => x.t)))
  return {
    tooltip: { trigger: 'axis' },
    legend: { data: Array.from(groups.keys()), textStyle: { color: t.label } },
    grid: { left: 40, right: 16, top: 32, bottom: 28 },
    xAxis: {
      type: 'category',
      data: times,
      axisLabel: { color: t.label },
      axisLine: t.axisLine,
      axisTick: { show: false },
    },
    yAxis: {
      type: 'value',
      axisLabel: { color: t.label },
      splitLine: t.splitLine,
    },
    series: Array.from(groups.entries()).map(([name, arr]) => ({
      name,
      type: 'line',
      smooth: true,
      data: times.map((t) => arr.find((x) => x.t === t)?.v ?? null),
    })),
    color: CHART_COLORS,
  }
})

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
/* 趋势提示：连续超标的条目单独标红，这是这块最该被一眼看到的信息 */
.trend-list {
  list-style: none;
  margin: 0 0 14px;
  padding: 0;
  display: grid;
  gap: 6px;
}
.trend-list li {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  font-size: 13px;
  line-height: 1.6;
  color: var(--ink-soft);
}
.trend-list li.alert .note {
  color: var(--flag-high);
  font-weight: 600;
}
.trend-list .dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  margin-top: 7px;
  flex: none;
  background: var(--ink-faint);
}
.trend-list .dot.high,
.trend-list .dot.low {
  background: var(--flag-high);
}
.trend-list .dot.normal {
  background: var(--flag-normal);
}
.block {
  margin-top: 14px;
}
.sec {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
  gap: 10px;
  flex-wrap: wrap;
}
.sec-btns {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}
.mt {
  margin-top: 12px;
}
h3 {
  margin: 0;
  font-size: 20px;
}
.quiet {
  color: var(--ink-faint);
  font-size: 13px;
  line-height: 1.7;
  padding: 18px 0;
}
.slim {
  padding: 6px 12px;
  font-size: 13px;
}
.quick-types {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 14px;
}
.qt {
  border: 1px solid var(--edge-strong);
  background: var(--sunk);
  color: var(--ink-soft);
  border-radius: 999px;
  padding: 5px 14px;
  font-size: 13px;
  cursor: pointer;
  transition: all 0.15s ease;
}
.qt.on {
  border-color: var(--accent);
  color: var(--accent);
  background: var(--accent-wash);
  font-weight: 500;
}
.csv-hint {
  margin: 0 0 10px;
  font-size: 13px;
  color: var(--ink-soft);
  line-height: 1.7;
}
.csv-pick {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 13px;
  color: var(--ink-faint);
}
.csv-more {
  margin: 8px 0 0;
  font-size: 12px;
  color: var(--ink-faint);
}
</style>
