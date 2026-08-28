<template>
  <div class="page">
    <header class="head">
      <p class="eyebrow">科室导诊</p>
      <h1>不知道该挂哪个科？</h1>
      <p class="lead">写下最困扰你的症状 —— 持续多久、什么时候加重。系统给出可能科室与紧急程度，不能替代分诊台。</p>
    </header>

    <section class="panel core-pad form rise">
      <label class="field">
        <span>主要症状</span>
        <el-input v-model="form.symptoms" type="textarea" :rows="5" placeholder="例如：胸痛伴呼吸困难、反复头晕、多饮多尿…" />
      </label>

      <div class="quick">
        <span class="quick-label">常见描述</span>
        <button v-for="s in samples" :key="s" class="quick-chip" type="button" @click="form.symptoms = s">{{ s }}</button>
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

      <button class="btn btn-primary" type="button" :disabled="loading || !form.symptoms.trim()" @click="submit">
        <span v-html="ICONS.spark"></span>{{ loading ? '正在导诊…' : '开始导诊' }}
      </button>
    </section>

    <div v-if="loading" class="result">
      <div class="skeleton" style="height: 52px; border-radius: var(--r-card)"></div>
      <div class="depts">
        <div v-for="n in 3" :key="n" class="skeleton" style="height: 172px; border-radius: var(--r-card)"></div>
      </div>
    </div>

    <div v-else-if="result" class="result">
      <div class="notice rise" :class="tone(result.urgency)">
        <span v-html="urgencyIcon(result.urgency)"></span>
        <span>综合紧急程度：<b>{{ urgencyText(result.urgency) }}</b></span>
        <span class="hint">{{ urgencyHint(result.urgency) }}</span>
      </div>

      <div class="depts">
        <article v-for="(d, i) in result.departments" :key="d.department" class="tile dept rise">
          <div class="dept-top">
            <span class="rank num">{{ i + 1 }}</span>
            <h3>{{ d.department }}</h3>
            <span class="chip" :class="tone(d.urgency)">{{ urgencyText(d.urgency) }}</span>
          </div>
          <p>{{ d.reason }}</p>
          <div class="score">
            <div class="bar"><i :style="{ width: pct(d.score) + '%' }"></i></div>
            <small class="num">{{ d.score }}</small>
          </div>
        </article>
      </div>

      <section class="panel core-pad rise">
        <div class="section-head"><h3>导诊说明</h3></div>
        <div class="prose" v-html="renderMarkdown(result.summary)"></div>
      </section>

      <!-- 附近医疗资源：机构数据来自高德地图的真实 POI，大模型只对列表做解释。
           位置默认用完即走；勾选「保存此地址」才落库，可随时清除。 -->
      <section class="panel core-pad rise near">
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
                <a v-if="p.tel" class="poi-tel" :href="'tel:' + p.tel">☎ {{ p.tel }}</a>
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
                <a v-if="p.tel" class="poi-tel" :href="'tel:' + p.tel">☎ {{ p.tel }}</a>
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

    <MedicalDisclaimer />
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
import MedicalDisclaimer from '@/components/MedicalDisclaimer.vue'

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

// 匹配分的量纲后端未固定：按当次结果里的最高分归一化，
// 保证进度条永远有一个铺满的参照，而不是全都挤在左边一小截。
const maxScore = computed(() => Math.max(...(result.value?.departments || []).map((d) => Number(d.score) || 0), 1))

function pct(score: number) {
  return Math.max(8, Math.round((Number(score) || 0) / maxScore.value * 100))
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

/** 紧急程度 → 语义色（全站统一的 tone token，深浅主题都成立）。 */
/** 紧急程度 → 数据色类名。急诊=高危色，门诊=偏离色，自我观察=正常色。 */
function tone(u: string) {
  return ({ emergency: 'high', outpatient: 'low', self_care: 'normal' } as Record<string, string>)[u] || ''
}

function urgencyIcon(u: string) {
  return u === 'emergency' ? ICONS.alert : u === 'outpatient' ? ICONS.clock : ICONS.check
}

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
  max-width: 860px;
  display: grid;
  gap: 18px;
}

.head h1 {
  margin: 4px 0 10px;
  max-width: 9em;
}

.lead {
  color: var(--ink-mute);
  line-height: 1.75;
  max-width: 27em;
}

.form {
  display: grid;
  gap: 16px;
}

.two {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}

/* 常见描述：给不知道怎么描述症状的人一个起点 */
.quick {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 7px;
  margin-top: -6px;
}

.quick-label {
  font-size: 12px;
  color: var(--ink-faint);
}

.quick-chip {
  border: 1px solid var(--edge-strong);
  background: var(--card);
  color: var(--ink-mute);
  border-radius: 999px;
  padding: 5px 12px;
  font-size: 12.5px;
  cursor: pointer;
  transition: color 0.15s ease, border-color 0.15s ease, background 0.15s ease,
    transform 0.12s var(--ease-out);
}

.quick-chip:active {
  transform: scale(0.96);
}

@media (hover: hover) and (pointer: fine) {
  .quick-chip:hover {
    color: var(--accent);
    border-color: var(--accent-line);
    background: var(--accent-wash);
  }
}

.result {
  display: grid;
  gap: 14px;
}

.notice .hint {
  margin-left: auto;
  font-size: 13px;
  opacity: 0.85;
}

.depts {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
}

.dept {
  padding: 18px 16px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.dept-top {
  display: flex;
  align-items: center;
  gap: 8px;
}

/* 排名徽章：结果本就有序，标出来省得逐个比对匹配分 */
.rank {
  display: grid;
  place-items: center;
  width: 22px;
  height: 22px;
  border-radius: 7px;
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

.dept h3 {
  font-size: 18px;
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
  gap: 10px;
  margin-top: auto;
}

.bar {
  flex: 1;
  height: 5px;
  border-radius: 999px;
  background: var(--sunk);
  overflow: hidden;
}

.bar i {
  display: block;
  height: 100%;
  border-radius: 999px;
  background: var(--accent);
  transition: width 0.5s var(--ease-out);
}

.score small {
  color: var(--ink-faint);
  font-size: 12px;
  flex-shrink: 0;
}

@media (max-width: 860px) {
  .depts,
  .two {
    grid-template-columns: 1fr;
  }
  .notice {
    flex-wrap: wrap;
  }
  .notice .hint {
    margin-left: 0;
    flex-basis: 100%;
  }
}

/* ---- 附近医疗资源 ---- */
.near {
  display: grid;
  gap: 14px;
}

.near-acts {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
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
  gap: 5px;
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
  grid-template-columns: repeat(2, 1fr);
  gap: 10px;
}

.poi {
  padding: 13px 15px;
  display: grid;
  gap: 6px;
}

.poi-top {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 10px;
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
  justify-self: start;
  font-size: 12.5px;
  color: var(--accent);
}

.near-note {
  font-size: 11.5px;
  color: var(--ink-faint);
}

@media (max-width: 720px) {
  .pois {
    grid-template-columns: 1fr;
  }
}
</style>
