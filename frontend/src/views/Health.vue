<template>
  <div class="page health">
    <PageHeader title="健康档案" desc="每位家人一份档案。指标会标出高低，也可以直接带进问答。">
      <template #extra>
        <button class="copper-btn" type="button" @click="openCreate">新建档案</button>
      </template>
    </PageHeader>

    <div class="members">
      <button
        v-for="p in profiles"
        :key="p.id"
        class="member card"
        :class="{ active: p.id === currentId }"
        type="button"
        @click="select(p.id!)"
      >
        <span class="av">{{ initial(p.displayName) }}</span>
        <span>
          <b>{{ p.displayName || '未命名' }}</b>
          <i>{{ p.relation || '档案' }} · {{ p.age ? p.age + '岁' : '年龄未填' }}</i>
        </span>
      </button>
      <button class="member add card" type="button" @click="openCreate">
        <span class="av plus">+</span>
        <span>
          <b>新建档案</b>
          <i>本人 / 家人</i>
        </span>
      </button>
    </div>

    <div v-if="!current" class="empty-sheet">
      <img src="/art/empty-record.svg" alt="" />
      <div>
        <h3>为家人建立第一份档案</h3>
        <p>比如「爸爸」「妈妈」或「小宝」。建好后再记血压、血糖，或把体检报告写进来。</p>
        <button class="copper-btn" type="button" @click="openCreate">现在新建</button>
      </div>
    </div>

    <template v-else>
      <div class="steps">
        <span :class="{ on: hasBasics }">资料 {{ hasBasics ? '已填' : '未全' }}</span>
        <span :class="{ on: metrics.length }">指标 {{ metrics.length ? metrics.length + ' 条' : '还没有' }}</span>
        <span :class="{ on: !!advice }">建议 {{ advice ? '已生成' : '未生成' }}</span>
      </div>

      <section class="sheet sheet-pad dossier">
        <div class="who">
          <span class="av lg">{{ initial(current.displayName) }}</span>
          <div>
            <h3>{{ current.displayName }}</h3>
            <p>
              {{ current.relation || '档案' }}
              <template v-if="current.age"> · {{ current.age }}岁</template>
              <template v-if="current.sex"> · {{ current.sex }}</template>
              <template v-if="bmi != null"> · BMI {{ bmi.toFixed(1) }}（{{ bmiLabel(bmi) }}）</template>
            </p>
            <p v-if="current.allergies" class="allergy">过敏：{{ current.allergies }}</p>
          </div>
          <div class="who-actions">
            <button class="copper-btn slim" type="button" @click="goAsk">结合档案去问</button>
            <button class="ghost-btn slim" type="button" @click="editing = !editing">{{ editing ? '收起资料' : '编辑资料' }}</button>
          </div>
        </div>

        <el-form v-if="editing" label-width="88px" class="form">
          <el-form-item label="称呼"><el-input v-model="form.displayName" placeholder="如：我 / 爸爸 / 小宝" /></el-form-item>
          <el-form-item label="关系">
            <el-select v-model="form.relation" style="width: 100%">
              <el-option v-for="r in relations" :key="r" :label="r" :value="r" />
            </el-select>
          </el-form-item>
          <el-form-item label="年龄"><el-input-number v-model="form.age" :min="0" :max="120" /></el-form-item>
          <el-form-item label="性别">
            <el-select v-model="form.sex" style="width: 100%">
              <el-option label="男" value="男" />
              <el-option label="女" value="女" />
              <el-option label="其他" value="其他" />
            </el-select>
          </el-form-item>
          <el-form-item label="身高 cm"><el-input-number v-model="form.heightCm" :min="50" :max="250" :precision="1" /></el-form-item>
          <el-form-item label="体重 kg"><el-input-number v-model="form.weightKg" :min="10" :max="300" :precision="1" /></el-form-item>
          <el-form-item label="过敏史"><el-input v-model="form.allergies" type="textarea" :rows="2" /></el-form-item>
          <el-form-item label="共享">
            <el-switch v-model="form.sharedToAdmin" />
            <span class="tip">仅管理员可读，默认关闭</span>
          </el-form-item>
          <div class="form-bar">
            <button class="ink-btn slim" type="button" @click="saveProfile">保存资料</button>
            <button v-if="profiles.length > 1" class="ghost-btn slim" type="button" @click="onDelete">删除此档案</button>
          </div>
        </el-form>
      </section>

      <div class="cards">
        <article v-for="c in metricCards" :key="c.type" class="metric-card card" :class="c.flag">
          <div class="mc-top">
            <span>{{ c.type }}</span>
            <em>{{ flagText(c.flag) }}</em>
          </div>
          <strong>{{ c.latest == null ? '—' : c.latest }}<small>{{ c.unit }}</small></strong>
          <p>{{ c.ref }}</p>
          <p v-if="c.delta != null" class="delta">较上次 {{ c.delta > 0 ? '+' : '' }}{{ c.delta }}</p>
          <p v-else class="delta">还没有第二次记录</p>
        </article>
      </div>

      <section class="sheet sheet-pad block">
        <div class="sec">
          <h3>指标趋势</h3>
          <div class="sec-btns">
            <button class="ghost-btn slim" type="button" @click="$router.push({ path: '/reports', query: { profileId: String(currentId) } })">从报告写入</button>
            <button class="copper-btn slim" type="button" @click="openMetric">新增指标</button>
          </div>
        </div>
        <v-chart v-if="hasChart" :option="chartOption" autoresize style="height: 260px" />
        <div v-else class="quiet">还没有可绘图的指标。点「新增指标」，或去报告解读后写入这份档案。</div>
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
              <el-button text type="danger" @click="onDelMetric(row.id)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </section>

      <section class="sheet sheet-pad block">
        <div class="sec">
          <h3>病史</h3>
          <button class="copper-btn slim" type="button" @click="histVisible = true">新增病史</button>
        </div>
        <el-table :data="histories" empty-text="还没有病史，可点「新增病史」">
          <el-table-column prop="disease" label="疾病" />
          <el-table-column prop="diagnosedAt" label="诊断日期" />
          <el-table-column prop="status" label="状态" />
          <el-table-column prop="note" label="备注" />
          <el-table-column label="" width="80">
            <template #default="{ row }">
              <el-button text type="danger" @click="onDelHist(row.id)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </section>

      <section class="sheet sheet-pad block">
        <div class="sec">
          <h3>健康建议</h3>
          <button class="ink-btn slim" type="button" :disabled="adviceLoading || !metrics.length" @click="onAdvice">
            {{ adviceLoading ? '生成中…' : '为「' + current.displayName + '」生成建议' }}
          </button>
        </div>
        <p v-if="adviceBasis" class="basis">{{ adviceBasis }}</p>
        <div v-if="advice" class="markdown-body" v-html="renderMarkdown(advice, terms)"></div>
        <div v-else class="quiet">{{ metrics.length ? '可以生成针对当前档案的建议，生成后会保存在这里。' : '先记一条血压或血糖，再生成建议。' }}</div>
        <div class="disclaimer">以上内容仅供健康科普参考，不能替代执业医师的面诊、检查与处方。如有不适请及时就医。</div>
      </section>
    </template>

    <el-dialog v-model="createVisible" title="给谁建档" width="440px">
      <div class="quick">
        <button v-for="q in quickRels" :key="q.relation" type="button" @click="pickRel(q)">{{ q.label }}</button>
      </div>
      <el-form label-width="80px">
        <el-form-item label="称呼"><el-input v-model="createForm.displayName" placeholder="如：爸爸" /></el-form-item>
        <el-form-item label="关系">
          <el-select v-model="createForm.relation" style="width: 200px">
            <el-option v-for="r in relations" :key="r" :label="r" :value="r" />
          </el-select>
        </el-form-item>
        <el-form-item label="年龄"><el-input-number v-model="createForm.age" :min="0" :max="120" /></el-form-item>
        <el-form-item label="性别">
          <el-select v-model="createForm.sex" style="width: 160px">
            <el-option label="男" value="男" />
            <el-option label="女" value="女" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" @click="onCreate">创建</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="metricVisible" title="新增指标" width="420px">
      <el-form label-width="80px">
        <el-form-item label="类型">
          <el-select v-model="metricForm.metricType" filterable allow-create @change="onMetricType">
            <el-option v-for="t in metricTypes" :key="t" :label="t" :value="t" />
          </el-select>
        </el-form-item>
        <el-form-item label="数值"><el-input-number v-model="metricForm.value" :precision="1" /></el-form-item>
        <el-form-item label="单位"><el-input v-model="metricForm.unit" /></el-form-item>
        <el-form-item label="时间"><el-date-picker v-model="metricForm.recordedAt" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" /></el-form-item>
        <el-form-item label="备注"><el-input v-model="metricForm.note" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="metricVisible = false">取消</el-button>
        <el-button type="primary" @click="onAddMetric">确定</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="histVisible" title="新增病史" width="420px">
      <el-form label-width="80px">
        <el-form-item label="疾病"><el-input v-model="histForm.disease" /></el-form-item>
        <el-form-item label="日期"><el-date-picker v-model="histForm.diagnosedAt" type="date" value-format="YYYY-MM-DD" /></el-form-item>
        <el-form-item label="状态"><el-input v-model="histForm.status" placeholder="随访中 / 已控制" /></el-form-item>
        <el-form-item label="备注"><el-input v-model="histForm.note" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="histVisible = false">取消</el-button>
        <el-button type="primary" @click="onAddHist">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import VChart from 'vue-echarts'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { LineChart, BarChart } from 'echarts/charts'
import { GridComponent, TooltipComponent, LegendComponent } from 'echarts/components'
import {
  addHistory,
  addMetric,
  createProfile,
  deleteHistory,
  deleteMetric,
  deleteProfile,
  generateAdvice,
  listHistories,
  listMetrics,
  listProfiles,
  updateProfileById,
} from '@/api/health'
import { listTerms } from '@/api/knowledge'
import type { HealthHistory, HealthMetric, HealthProfile } from '@/api/types'
import { renderMarkdown } from '@/utils/markdown'
import { formatWhen, initial } from '@/utils/format'
import { CARD_TYPES, bmiLabel, bmiOf, flagOf, flagText, refText, unitOf } from '@/utils/metrics'
import PageHeader from '@/components/PageHeader.vue'

use([CanvasRenderer, LineChart, BarChart, GridComponent, TooltipComponent, LegendComponent])

const route = useRoute()
const router = useRouter()
const relations = ['本人', '配偶', '父亲', '母亲', '子女', '其他']
const metricTypes = [...CARD_TYPES, '餐后血糖', '糖化血红蛋白']
const quickRels = [
  { label: '本人', relation: '本人', displayName: '我', age: 30, sex: '男' },
  { label: '爸爸', relation: '父亲', displayName: '爸爸', age: 58, sex: '男' },
  { label: '妈妈', relation: '母亲', displayName: '妈妈', age: 56, sex: '女' },
  { label: '配偶', relation: '配偶', displayName: '爱人', age: 32, sex: '女' },
  { label: '孩子', relation: '子女', displayName: '小宝', age: 8, sex: '男' },
]
const profiles = ref<HealthProfile[]>([])
const currentId = ref<number | null>(null)
const editing = ref(false)
const form = reactive<HealthProfile>({ displayName: '我', relation: '本人', sharedToAdmin: false })
const metrics = ref<HealthMetric[]>([])
const histories = ref<HealthHistory[]>([])
const advice = ref('')
const adviceBasis = ref('')
const adviceLoading = ref(false)
const terms = ref<string[]>([])
const createVisible = ref(false)
const metricVisible = ref(false)
const histVisible = ref(false)
const createForm = reactive({ displayName: '', relation: '父亲', age: 50 as number | undefined, sex: '男' })
const metricForm = reactive({ metricType: '空腹血糖', value: 5.5, unit: 'mmol/L', recordedAt: '', note: '' })
const histForm = reactive({ disease: '', diagnosedAt: '', status: '随访中', note: '' })
const current = computed(() => profiles.value.find((p) => p.id === currentId.value) || null)
const bmi = computed(() => bmiOf(current.value?.heightCm, current.value?.weightKg))
const hasBasics = computed(() => !!(current.value?.age && current.value?.sex))

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
      latest: value,
      unit: latest?.unit || unitOf(type),
      flag: flagOf(type, value),
      ref: '参考 ' + refText(type),
      delta: latest && prev ? Number((latest.value - prev.value).toFixed(1)) : null,
    }
  }),
)

onMounted(async () => {
  const prefer = Number(route.query.id) || undefined
  await loadProfiles(prefer)
  if (route.query.new === '1') openCreate()
  try {
    terms.value = (await listTerms()).data || []
  } catch {
    terms.value = []
  }
})

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
  histories.value = (await listHistories(id)).data || []
  advice.value = p?.lastAdvice || ''
  adviceBasis.value = p?.adviceAt ? '上次生成于 ' + formatWhen(p.adviceAt) : ''
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

function openCreate() {
  pickRel(quickRels[1])
  createVisible.value = true
}

function pickRel(q: (typeof quickRels)[number]) {
  createForm.displayName = q.displayName
  createForm.relation = q.relation
  createForm.age = q.age
  createForm.sex = q.sex
}

function goAsk() {
  if (!currentId.value) return
  router.push({ path: '/chat', query: { profileId: String(currentId.value) } })
}

function openMetric() {
  onMetricType(metricForm.metricType)
  metricVisible.value = true
}

function onMetricType(type: string) {
  const u = unitOf(type)
  if (u) metricForm.unit = u
}

async function onCreate() {
  if (!createForm.displayName.trim()) {
    ElMessage.warning('请填写称呼，例如「爸爸」')
    return
  }
  const created = (await createProfile({ ...createForm, displayName: createForm.displayName.trim() })).data
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

async function onAddMetric() {
  if (!currentId.value) return
  const payload: Partial<HealthMetric> & { profileId: number } = {
    profileId: currentId.value,
    metricType: metricForm.metricType,
    value: metricForm.value,
    unit: metricForm.unit || unitOf(metricForm.metricType),
    note: metricForm.note,
  }
  if (metricForm.recordedAt) payload.recordedAt = metricForm.recordedAt
  await addMetric(payload)
  metricVisible.value = false
  metrics.value = (await listMetrics(currentId.value)).data || []
}

async function onDelMetric(id: number) {
  await deleteMetric(id)
  if (currentId.value) metrics.value = (await listMetrics(currentId.value)).data || []
}

async function onAddHist() {
  if (!currentId.value) return
  const payload: Partial<HealthHistory> & { profileId: number } = {
    profileId: currentId.value,
    disease: histForm.disease,
    status: histForm.status,
    note: histForm.note,
  }
  if (histForm.diagnosedAt) payload.diagnosedAt = histForm.diagnosedAt
  await addHistory(payload)
  histVisible.value = false
  histories.value = (await listHistories(currentId.value)).data || []
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

const hasChart = computed(() => metrics.value.length > 0)
const chartOption = computed(() => {
  const groups = new Map<string, { t: string; v: number }[]>()
  for (const m of [...metrics.value].sort((a, b) => String(a.recordedAt).localeCompare(String(b.recordedAt)))) {
    const arr = groups.get(m.metricType) || []
    arr.push({ t: formatWhen(m.recordedAt), v: m.value })
    groups.set(m.metricType, arr)
  }
  const times = Array.from(new Set([...groups.values()].flat().map((x) => x.t)))
  return {
    tooltip: { trigger: 'axis' },
    legend: { data: Array.from(groups.keys()) },
    grid: { left: 40, right: 16, top: 32, bottom: 28 },
    xAxis: { type: 'category', data: times },
    yAxis: { type: 'value' },
    series: Array.from(groups.entries()).map(([name, arr]) => ({
      name,
      type: 'line',
      smooth: true,
      data: times.map((t) => arr.find((x) => x.t === t)?.v ?? null),
    })),
    color: ['#c45d3a', '#2c5648', '#b8965a', '#1d4ed8'],
  }
})
</script>

<style scoped>
.members {
  display: flex;
  gap: 10px;
  overflow-x: auto;
  padding-bottom: 8px;
  margin-bottom: 16px;
}
.member {
  min-width: 176px;
  display: flex;
  gap: 10px;
  align-items: center;
  text-align: left;
  padding: 12px 14px;
  cursor: pointer;
}
.member.active {
  border-color: var(--copper);
  box-shadow: 0 0 0 1px rgba(196, 93, 58, 0.25);
}
.member.add {
  border-style: dashed;
}
.member b {
  display: block;
}
.member i {
  display: block;
  font-style: normal;
  color: var(--ink-3);
  font-size: 12px;
  margin-top: 2px;
}
.av {
  width: 38px;
  height: 38px;
  border-radius: 50%;
  background: #ead8cc;
  color: var(--copper-deep);
  display: grid;
  place-items: center;
  font-family: var(--font-serif);
  flex-shrink: 0;
}
.av.lg {
  width: 52px;
  height: 52px;
  font-size: 22px;
}
.av.plus {
  background: var(--paper-deep);
  font-size: 22px;
  color: var(--ink-2);
}
.steps {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 16px;
}
.steps span {
  font-size: 12px;
  color: var(--ink-3);
  background: var(--paper-deep);
  padding: 5px 10px;
  border-radius: 999px;
}
.steps span.on {
  color: var(--moss);
  background: rgba(44, 86, 72, 0.1);
}
.dossier .who {
  display: flex;
  gap: 14px;
  align-items: flex-start;
}
.dossier h3 {
  margin: 0 0 4px;
  font-size: 24px;
}
.dossier p {
  margin: 0;
  color: var(--ink-3);
}
.allergy {
  margin-top: 6px !important;
  color: var(--copper-deep) !important;
}
.who-actions {
  margin-left: auto;
  display: flex;
  gap: 8px;
  flex-shrink: 0;
}
.form {
  margin-top: 18px;
  padding-top: 12px;
  border-top: 1px solid var(--line);
}
.form-bar {
  display: flex;
  gap: 10px;
}
.cards {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 10px;
  margin: 14px 0;
}
.metric-card {
  padding: 14px 14px 12px;
}
.metric-card.high {
  border-color: #e8b4ae;
}
.metric-card.low {
  border-color: #b7c7e8;
}
.mc-top {
  display: flex;
  justify-content: space-between;
  font-size: 13px;
  color: var(--ink-3);
}
.metric-card strong {
  display: block;
  font-family: var(--font-serif);
  font-size: 28px;
  font-weight: 600;
  margin: 6px 0 4px;
}
.metric-card small {
  font-size: 13px;
  margin-left: 4px;
  color: var(--ink-3);
}
.metric-card p {
  margin: 0;
  font-size: 12px;
  color: var(--ink-3);
}
.delta {
  margin-top: 4px !important;
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
}
.sec-btns {
  display: flex;
  gap: 8px;
}
.mt {
  margin-top: 12px;
}
h3 {
  margin: 0;
  font-size: 20px;
}
.slim {
  padding: 6px 12px;
  font-size: 13px;
}
.tip,
.basis {
  color: var(--ink-3);
  font-size: 13px;
}
.basis {
  margin: 0 0 10px;
}
.quiet {
  color: var(--ink-3);
  font-size: 13px;
  line-height: 1.7;
  padding: 18px 0;
}
.quick {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 16px;
}
.quick button {
  border: 1px solid var(--line-strong);
  background: var(--cream);
  border-radius: 999px;
  padding: 6px 12px;
  cursor: pointer;
}
.empty-sheet {
  display: grid;
  grid-template-columns: 280px 1fr;
  gap: 24px;
}
.empty-sheet img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  min-height: 220px;
}
.empty-sheet div {
  padding: 28px 24px 28px 0;
}
.empty-sheet h3 {
  font-size: 28px;
  margin: 0 0 10px;
}
.empty-sheet p {
  color: var(--ink-3);
  line-height: 1.7;
}
@media (max-width: 900px) {
  .cards,
  .empty-sheet,
  .dossier .who {
    grid-template-columns: 1fr 1fr;
    flex-wrap: wrap;
  }
  .who-actions {
    margin-left: 0;
    width: 100%;
  }
}
</style>
