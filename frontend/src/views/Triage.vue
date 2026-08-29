<template>
  <div class="page">
    <PageHeader
      kicker="科室导诊"
      title="不知道该挂哪个科？"
      desc="写下最困扰你的症状 —— 持续多久、什么时候加重。系统给出可能科室与紧急程度，不能替代分诊台。"
    />

    <div class="triage-workbench">
      <aside class="input-pane">
    <Shell>
      <div class="form">
        <label class="field">
          <span>主要症状</span>
          <el-input v-model="form.symptoms" type="textarea" :rows="5" placeholder="例如：胸痛伴呼吸困难、反复头晕、多饮多尿…" />
        </label>

        <div class="quick">
          <span class="quick-label">常见描述</span>
          <button v-for="s in samples" :key="s" class="chip-btn" type="button" @click="form.symptoms = s">{{ s }}</button>
        </div>

        <div class="two">
          <label class="field">
            <span>年龄</span>
            <el-input-number v-model="form.age" :min="0" :max="120" style="width: 100%" />
          </label>
          <label class="field">
            <span>性别</span>
            <el-select v-model="form.sex" clearable placeholder="可不填" style="width: 100%">
              <el-option label="男" value="男" />
              <el-option label="女" value="女" />
            </el-select>
          </label>
        </div>

        <button class="btn btn-primary btn-cta" type="button" :disabled="loading || !form.symptoms.trim()" @click="submit">
          {{ loading ? '正在导诊…' : '开始导诊' }}
          <span class="knob" v-html="loading ? ICONS.clock : ICONS.arrow"></span>
        </button>
      </div>
    </Shell>

        <div class="input-state" aria-live="polite">
          <span>症状信息</span>
          <strong :class="{ ready: form.symptoms.trim() }">{{ form.symptoms.trim() ? '可以开始导诊' : '等待填写' }}</strong>
        </div>
      </aside>

      <section class="output-pane" aria-label="导诊结果">
        <header class="output-head">
          <div>
            <span>结果区</span>
            <strong>{{ result ? '本次导诊建议' : '等待分析' }}</strong>
          </div>
          <span class="output-status" :class="{ active: loading }">{{ loading ? '分析中' : result ? '已完成' : '未开始' }}</span>
        </header>

    <div v-if="loading" class="result">
      <div class="skeleton" style="height: 52px; border-radius: var(--r-card)"></div>
      <div class="depts">
        <div v-for="n in 3" :key="n" class="skeleton" style="height: 172px; border-radius: var(--r-card)"></div>
      </div>
    </div>

    <div v-else-if="result" class="result">
      <section class="urgency" :class="result.urgency" v-reveal>
        <div class="urgency-copy">
          <span>综合紧急程度</span>
          <strong>{{ urgencyText(result.urgency) }}</strong>
          <p>{{ urgencyHint(result.urgency) }}</p>
        </div>
        <div
          class="urgency-scale"
          role="img"
          :aria-label="`综合紧急程度：${urgencyText(result.urgency)}。${urgencyHint(result.urgency)}`"
        >
          <div class="urgency-rail">
            <span v-for="s in urgencySegments" :key="s.key" class="urgency-segment" :class="s.key"></span>
            <i class="urgency-cursor" :style="{ left: urgencyPosition + '%' }"></i>
          </div>
          <div class="urgency-labels" aria-hidden="true">
            <span v-for="s in urgencySegments" :key="s.key" :class="{ active: s.key === result.urgency }">{{ s.label }}</span>
          </div>
        </div>
      </section>

      <div class="depts">
        <article v-for="(d, i) in result.departments" :key="d.department" class="tile dept" v-reveal="i * 60">
          <div class="dept-top">
            <span class="rank num">{{ i + 1 }}</span>
            <h3 class="dept-title">{{ d.department }}</h3>
          </div>
          <p>{{ d.reason }}</p>
          <div class="score" role="img" :aria-label="`匹配度${matchLevel(d.score)}`">
            <div class="bar"><i :style="{ width: pct(d.score) + '%' }"></i></div>
            <small>匹配度{{ matchLevel(d.score) }}</small>
          </div>
        </article>
      </div>

      <section class="panel core-pad" v-reveal>
        <div class="section-head"><h3>导诊说明</h3></div>
        <div class="prose" v-html="renderMarkdown(result.summary)"></div>
      </section>

      <!-- 附近医疗资源：机构数据来自高德地图的真实 POI，大模型只对列表做解释。
           位置默认用完即走；勾选「保存此地址」才落库，可随时清除。 -->
      <section class="panel core-pad near" v-reveal>
        <div class="section-head">
          <h3>附近医疗资源</h3>
          <span v-if="savedLoc" class="count">
            已保存：{{ savedLoc.addressText }}
            <button class="linklike" type="button" @click="useSaved">使用</button>
            <button class="linklike" type="button" @click="removeSaved">清除</button>
          </span>
        </div>

        <div class="near-acts">
          <button class="btn btn-primary btn-sm" type="button" :disabled="nearLoading" @click="useGeo">
            <span v-html="ICONS.compass"></span>使用我的位置
          </button>
          <input
            v-model="addressText"
            class="near-input"
            placeholder="或填写地址，如：北京市海淀区"
            @keyup.enter="searchByAddress"
          />
          <button class="btn btn-ghost btn-sm" type="button" :disabled="nearLoading || !addressText.trim()" @click="searchByAddress">
            搜附近
          </button>
          <label class="save-chk">
            <input v-model="saveLocation" type="checkbox" />保存此地址
          </label>
        </div>
        <p v-if="geoHint" class="geo-hint">{{ geoHint }}</p>

        <div v-if="nearLoading" class="skeleton" style="height: 120px; border-radius: var(--r-card)"></div>

        <template v-else-if="near">
          <p class="near-advice" :class="{ fallback: near.adviceSource === 'template' }" v-html="renderMarkdown(near.advice || '')"></p>

          <template v-if="near.hospitals.length || near.pharmacies.length">
            <h4 class="near-cat">医院</h4>
            <div class="pois">
              <article v-for="p in near.hospitals" :key="'h' + p.name" class="tile poi">
                <div class="poi-top">
                  <b>{{ p.name }}</b>
                  <span v-if="p.distanceMeters != null" class="chip dist">{{ fmtDist(p.distanceMeters) }}</span>
                </div>
                <p v-if="p.address" class="poi-addr">{{ p.address }}</p>
                <a v-if="p.tel" class="poi-tel" :href="'tel:' + p.tel"><span v-html="ICONS.phone"></span>{{ p.tel }}</a>
              </article>
            </div>

            <h4 v-if="near.pharmacies.length" class="near-cat">药店</h4>
            <div v-if="near.pharmacies.length" class="pois">
              <article v-for="p in near.pharmacies" :key="'p' + p.name" class="tile poi">
                <div class="poi-top">
                  <b>{{ p.name }}</b>
                  <span v-if="p.distanceMeters != null" class="chip dist">{{ fmtDist(p.distanceMeters) }}</span>
                </div>
                <p v-if="p.address" class="poi-addr">{{ p.address }}</p>
                <a v-if="p.tel" class="poi-tel" :href="'tel:' + p.tel"><span v-html="ICONS.phone"></span>{{ p.tel }}</a>
              </article>
            </div>
            <p class="near-note">机构信息来自地图服务，就诊/购药前请电话确认。</p>
          </template>
        </template>
      </section>
    </div>

    <div v-else class="panel empty">
      <span v-html="ICONS.compass"></span>
      <h3>还没有导诊结果</h3>
      <p>症状写得越具体，推荐的科室越准。</p>
    </div>
      </section>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import {
  runTriage,
  nearbyMedical,
  getSavedLocation,
  clearSavedLocation,
  type NearbyResult,
  type SavedLocation,
} from '@/api/triage'
import type { TriageResult } from '@/api/types'
import { renderMarkdown } from '@/utils/markdown'
import { ICONS } from '@/utils/icons'
import PageHeader from '@/components/PageHeader.vue'
import Shell from '@/components/Shell.vue'

const loading = ref(false)
const result = ref<TriageResult | null>(null)
const form = reactive({ symptoms: '', age: undefined as number | undefined, sex: '' })

// ---- 附近医疗资源 ----
const near = ref<NearbyResult | null>(null)
const nearLoading = ref(false)
const addressText = ref('')
const saveLocation = ref(false)
const savedLoc = ref<SavedLocation | null>(null)
const geoHint = ref('')

onMounted(async () => {
  try {
    savedLoc.value = (await getSavedLocation()).data
  } catch {
    savedLoc.value = null
  }
})

const topDepartment = computed(() => result.value?.departments?.[0]?.department || '相应科室')

/** 浏览器定位：用完即走，坐标只在勾选「保存此地址」时随请求落库。 */
function useGeo() {
  geoHint.value = ''
  if (!navigator.geolocation) {
    geoHint.value = '当前浏览器不支持定位，请手动填写地址。'
    return
  }
  nearLoading.value = true
  navigator.geolocation.getCurrentPosition(
    (pos) => {
      search({ lng: pos.coords.longitude, lat: pos.coords.latitude }).finally(() => {
        nearLoading.value = false
      })
    },
    () => {
      nearLoading.value = false
      geoHint.value = '定位未授权或失败，可以手动填写地址。'
    },
    { timeout: 8000 },
  )
}

async function searchByAddress() {
  const addr = addressText.value.trim()
  if (!addr || nearLoading.value) return
  geoHint.value = ''
  nearLoading.value = true
  try {
    await search({ address: addr })
  } finally {
    nearLoading.value = false
  }
}

function useSaved() {
  const s = savedLoc.value
  if (!s?.addressText) return
  addressText.value = s.addressText
  searchByAddress()
}

async function removeSaved() {
  try {
    await clearSavedLocation()
  } finally {
    savedLoc.value = null
  }
}

/** 发起周边检索；科室/症状取自当前导诊结果，喂给后端生成贴合的建议。 */
async function search(loc: { lng?: number; lat?: number; address?: string }) {
  try {
    near.value = (
      await nearbyMedical({
        symptoms: form.symptoms,
        department: topDepartment.value,
        urgency: result.value?.urgency,
        ...loc,
        save: saveLocation.value,
      })
    ).data
    if (saveLocation.value) {
      try {
        savedLoc.value = (await getSavedLocation()).data
      } catch {
        /* 保存状态刷新失败不打扰用户 */
      }
    }
  } catch {
    near.value = null
    geoHint.value = '附近检索没有成功，稍后再试或换个地址。'
  }
}

function fmtDist(m?: number | null) {
  if (m == null) return ''
  return m >= 1000 ? (m / 1000).toFixed(1) + ' km' : Math.round(m) + ' m'
}

const samples = ['胸闷、活动后加重两周', '反复头晕伴恶心', '低烧咳嗽一周不退', '多饮多尿、体重下降']
const urgencySegments = [
  { key: 'self_care', label: '自我观察' },
  { key: 'outpatient', label: '门诊' },
  { key: 'emergency', label: '急诊' },
] as const

// 匹配分的量纲后端未固定：按当次结果里的最高分归一化，
// 保证进度条永远有一个铺满的参照，而不是全都挤在左边一小截。
const maxScore = computed(() => Math.max(...(result.value?.departments || []).map((d) => Number(d.score) || 0), 1))

function pct(score: number) {
  return Math.max(8, Math.round((Number(score) || 0) / maxScore.value * 100))
}

function matchLevel(score: number) {
  const value = pct(score)
  if (value >= 72) return '高'
  if (value >= 40) return '中'
  return '低'
}

async function submit() {
  loading.value = true
  try {
    result.value = (await runTriage({ ...form })).data
  } finally {
    loading.value = false
  }
}

function urgencyText(u: string) {
  return ({ emergency: '急诊', outpatient: '门诊', self_care: '可先自我观察' } as Record<string, string>)[u] || u
}

const urgencyPosition = computed(() => {
  const index = urgencySegments.findIndex((s) => s.key === result.value?.urgency)
  return ((index < 0 ? 1 : index) + 0.5) * (100 / urgencySegments.length)
})

function urgencyHint(u: string) {
  return (
    {
      emergency: '建议尽快前往急诊，不要自行观察等待。',
      outpatient: '建议近期挂号就诊，症状加重随时就医。',
      self_care: '可先观察，症状持续或加重再就诊。',
    } as Record<string, string>
  )[u] || ''
}
</script>

<style scoped>
.page {
  max-width: 1280px;
  display: grid;
  gap: var(--space-4);
}

.page :deep(.head) {
  margin-bottom: 0;
}

.triage-workbench {
  display: grid;
  grid-template-columns: minmax(320px, 0.72fr) minmax(0, 1.28fr);
  gap: var(--space-4);
  align-items: start;
}

.input-pane {
  position: sticky;
  top: calc(var(--topbar-h) + var(--space-4));
  display: grid;
  gap: var(--space-3);
}

.input-state {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
  padding: 10px 12px;
  border: 1px solid var(--edge);
  border-radius: var(--r-control);
  background: var(--card);
  color: var(--ink-mute);
  font-size: 12px;
}

.input-state strong {
  color: var(--ink-faint);
  font-weight: 620;
}

.input-state strong.ready {
  color: var(--flag-normal);
}

.output-pane {
  min-width: 0;
  display: grid;
  gap: var(--space-4);
  padding: 18px;
  border: 1px solid var(--edge);
  border-radius: var(--r-shell);
  background: color-mix(in srgb, var(--card) 58%, var(--paper));
}

.output-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
  padding-bottom: var(--space-3);
  border-bottom: 1px solid var(--edge);
}

.output-head > div {
  display: grid;
  gap: 2px;
}

.output-head span {
  color: var(--ink-faint);
  font-size: 11px;
}

.output-head strong {
  color: var(--ink);
  font-size: 14px;
  font-weight: 650;
}

.output-status {
  padding: 4px 8px;
  border-radius: var(--r-chip);
  background: var(--flag-none-wash);
  color: var(--flag-none) !important;
  font-weight: 620;
}

.output-status.active {
  background: var(--info-wash);
  color: var(--info) !important;
}

.form {
  display: grid;
  gap: var(--space-4);
}

.two {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--space-4);
}

/* 常见描述：给不知道怎么描述症状的人一个起点 */
.quick {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: var(--space-2);
  margin-top: calc(-1 * var(--space-2));
}

.quick-label {
  font-size: 12px;
  color: var(--ink-faint);
}

.result {
  display: grid;
  gap: var(--space-4);
}

.output-pane > .empty {
  min-height: 360px;
  margin: 0;
  background: transparent;
  border: 0;
  box-shadow: none;
}

.urgency {
  --urgency-tone: var(--flag-low);
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(260px, 0.9fr);
  gap: var(--space-5);
  align-items: center;
  padding: var(--space-4) var(--space-5);
  border: 1px solid var(--edge);
  border-radius: var(--r-card);
  background: var(--card);
}

.urgency.self_care {
  --urgency-tone: var(--flag-normal);
}

.urgency.emergency {
  --urgency-tone: var(--flag-high);
}

.urgency-copy {
  display: grid;
  gap: var(--space-1);
}

.urgency-copy > span {
  font-size: 12px;
  color: var(--ink-faint);
}

.urgency-copy strong {
  color: var(--urgency-tone);
  font-family: var(--font);
  font-size: 21px;
  font-weight: 650;
}

.urgency-copy p {
  color: var(--ink-mute);
  font-size: 13px;
  line-height: 1.6;
}

.urgency-scale {
  min-width: 0;
}

.urgency-rail {
  position: relative;
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: var(--space-1);
}

.urgency-segment {
  height: 8px;
  border-radius: var(--r-pill);
}

.urgency-segment.self_care {
  background: var(--flag-normal-wash);
  border: 1px solid var(--flag-normal-line);
}

.urgency-segment.outpatient {
  background: var(--flag-low-wash);
  border: 1px solid var(--flag-low-line);
}

.urgency-segment.emergency {
  background: var(--flag-high-wash);
  border: 1px solid var(--flag-high-line);
}

.urgency-cursor {
  position: absolute;
  top: 50%;
  width: 3px;
  height: 18px;
  border-radius: var(--r-pill);
  background: var(--urgency-tone);
  box-shadow: 0 0 0 3px var(--card);
  transform: translate(-50%, -50%);
  transition: left 0.4s var(--ease);
}

.urgency-labels {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: var(--space-1);
  margin-top: var(--space-2);
  font-size: 11.5px;
  color: var(--ink-faint);
  text-align: center;
}

.urgency-labels .active {
  color: var(--urgency-tone);
  font-weight: 650;
}

.depts {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
  gap: var(--space-3);
}

.dept {
  padding: var(--space-5) var(--space-4);
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
}

.dept-top {
  display: flex;
  align-items: center;
  gap: var(--space-2);
}

/* 排名徽章：结果本就有序，标出来省得逐个比对匹配分 */
.rank {
  display: grid;
  place-items: center;
  width: 22px;
  height: 22px;
  border-radius: var(--r-chip);
  background: var(--flag-none-wash);
  color: var(--ink-mute);
  font-size: 12px;
  font-weight: 600;
  flex-shrink: 0;
}

.dept:first-child .rank {
  background: var(--accent);
  color: var(--on-accent);
}

.dept-title {
  flex: 1;
  min-width: 0;
}

.dept p {
  color: var(--ink-mute);
  min-height: 46px;
  line-height: 1.65;
  font-size: 13.5px;
}

.score {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  margin-top: auto;
}

.bar {
  flex: 1;
  height: 5px;
  border-radius: var(--r-pill);
  background: var(--sunk);
  overflow: hidden;
}

.bar i {
  display: block;
  height: 100%;
  border-radius: var(--r-pill);
  background: var(--accent);
  transition: width 0.5s var(--ease-out);
}

.score small {
  color: var(--ink-faint);
  font-size: 12px;
  flex-shrink: 0;
}

@media (max-width: 860px) {
  .triage-workbench {
    grid-template-columns: 1fr;
  }

  .input-pane {
    position: static;
  }

  .two {
    grid-template-columns: 1fr;
  }
  .urgency {
    grid-template-columns: 1fr;
  }
}

/* ---- 附近医疗资源 ---- */
.near {
  display: grid;
  gap: var(--space-4);
}

.near-acts {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-3);
}

.near-input {
  flex: 1;
  min-width: 200px;
  border: 1px solid var(--edge);
  border-radius: var(--r-control);
  background: var(--sunk);
  color: var(--ink);
  padding: 8px 14px;
  font-size: 13.5px;
  outline: none;
  transition: border-color 0.2s var(--ease-soft), box-shadow 0.3s var(--ease);
}

.near-input:focus {
  border-color: var(--accent-line);
  box-shadow: 0 0 0 3px var(--accent-wash);
}

.near-input::placeholder {
  color: var(--ink-faint);
}

.save-chk {
  display: inline-flex;
  align-items: center;
  gap: var(--space-1);
  font-size: 12.5px;
  color: var(--ink-mute);
  cursor: pointer;
  user-select: none;
}

.save-chk input {
  accent-color: var(--accent);
}

.linklike {
  border: 0;
  background: none;
  padding: 0 2px;
  color: var(--accent);
  font-size: inherit;
  cursor: pointer;
}

.linklike:hover {
  text-decoration: underline;
}

.geo-hint {
  font-size: 12.5px;
  color: var(--ink-faint);
  margin-top: -6px;
}

.near-advice {
  font-size: 14px;
  line-height: 1.8;
  color: var(--ink);
  padding: 12px 16px;
  border-left: 3px solid var(--accent-line);
  background: var(--accent-wash);
  border-radius: 0 var(--r-control) var(--r-control) 0;
}

.near-advice.fallback {
  border-left-color: var(--edge-strong);
  background: var(--tray);
  color: var(--ink-mute);
}

.near-cat {
  font-size: 13px;
  font-weight: 600;
  color: var(--ink-soft);
}

.pois {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
  gap: var(--space-3);
}

.poi {
  padding: var(--space-3) var(--space-4);
  display: grid;
  gap: var(--space-2);
}

.poi-top {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: var(--space-3);
}

.poi-top b {
  font-size: 14px;
  line-height: 1.45;
}

.poi-top .dist {
  flex-shrink: 0;
  padding: 1px 8px;
  border-radius: var(--r-chip);
  background: var(--flag-none-wash);
  color: var(--ink-mute);
  font-size: 11.5px;
  font-weight: 600;
}

.poi-addr {
  font-size: 12.5px;
  line-height: 1.6;
  color: var(--ink-mute);
}

.poi-tel {
  display: inline-flex;
  align-items: center;
  gap: var(--space-1);
  justify-self: start;
  font-size: 12.5px;
  color: var(--accent);
}

.poi-tel :deep(svg) {
  width: 14px;
  height: 14px;
}

.near-note {
  font-size: 11.5px;
  color: var(--ink-faint);
}

</style>
