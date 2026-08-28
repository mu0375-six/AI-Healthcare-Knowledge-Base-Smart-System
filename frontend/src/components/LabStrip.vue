<template>
  <div class="lab" :class="[flag, { compact }]">
    <div class="lab-head">
      <span class="lab-name">{{ type }}</span>
      <span v-if="hasValue" class="lab-value num">
        {{ value }}<small>{{ shownUnit }}</small>
      </span>
      <span v-else class="lab-value num empty">—</span>
    </div>

    <!-- 有参考区间才画标尺；未知指标只显示数值，不编一条假的刻度 -->
    <template v-if="band && hasValue">
      <div class="lab-track" role="img" :aria-label="ariaLabel">
        <span class="lab-band" :style="{ left: bandLeft + '%', width: bandWidth + '%' }"></span>
        <span class="lab-tick" :style="{ left: bandLeft + '%' }"></span>
        <span class="lab-tick" :style="{ left: bandLeft + bandWidth + '%' }"></span>
        <span class="lab-mark" :style="{ left: markLeft + '%' }"></span>
      </div>
      <div class="lab-scale num">
        <span :style="{ left: bandLeft + '%' }">{{ band.low }}</span>
        <span :style="{ left: bandLeft + bandWidth + '%' }">{{ band.high }}</span>
      </div>
      <div v-if="!compact" class="lab-foot">
        <span class="chip" :class="flag">{{ flagText(flag) }}</span>
        <span class="lab-ref">参考 <b class="num">{{ band.low }}–{{ band.high }}</b> {{ shownUnit }}</span>
      </div>
    </template>

    <p v-else-if="!compact" class="lab-noref">
      {{ hasValue ? '暂无该指标的参考区间' : '还没有记录' }}
    </p>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { bandOf, flagOf, flagText, unitOf } from '@/utils/metrics'

/**
 * 参考区间标尺 —— 本产品的签名控件。
 *
 * 化验单的核心信息从来不是那个数字本身，而是「它落在参考区间的哪里」。
 * 纯文本的「6.8（参考 3.9–6.1）」需要读者自己心算距离；这条标尺把它
 * 变成一眼可见的空间关系：实心带是正常区间，游标是当前值。
 *
 * 判高低复用 utils/metrics 的 flagOf —— 口径唯一权威源是后端
 * MetricGuide，这里不另写一套判断。
 */
const props = defineProps<{
  type: string
  value?: number | null
  unit?: string
  /** 紧凑模式：只画标尺，不显示状态徽标与参考区间文字（用于密集列表） */
  compact?: boolean
  /**
   * 区间覆盖。报告详情里的项目名是化验单原文（"谷丙转氨酶"这类），
   * 不在 MetricGuide 的表内，但报告自带 refRange 字符串 ——
   * 调用方解析后从这里传进来，标尺照样画得出。
   */
  range?: { low: number; high: number } | null
  /** 高低判定覆盖：报告项目的 flag 由后端随报告给出，不重复判一次。 */
  flagOverride?: string
}>()

const band = computed(() => props.range ?? bandOf(props.type))
const hasValue = computed(() => props.value != null && !Number.isNaN(Number(props.value)))
const flag = computed(() => props.flagOverride || flagOf(props.type, props.value ?? null))
const shownUnit = computed(() => props.unit || unitOf(props.type))

/**
 * 刻度域：以参考区间为核心向两侧留白，并保证当前值一定落在域内。
 * 不用固定倍率——极端值（比如血糖 20）会把正常带压成一条线，
 * 所以域的上下界取「区间外扩」与「值再外扩一点」的较大者。
 */
const domain = computed(() => {
  const b = band.value
  if (!b) return null
  const span = b.high - b.low || 1
  const pad = span * 0.55
  const v = Number(props.value)
  const lo = Math.min(b.low - pad, hasValue.value ? v - span * 0.18 : Infinity)
  const hi = Math.max(b.high + pad, hasValue.value ? v + span * 0.18 : -Infinity)
  return { lo, hi, width: hi - lo || 1 }
})

function pct(x: number) {
  const d = domain.value
  if (!d) return 0
  return ((x - d.lo) / d.width) * 100
}

const bandLeft = computed(() => (band.value ? pct(band.value.low) : 0))
const bandWidth = computed(() => (band.value ? pct(band.value.high) - pct(band.value.low) : 0))
const markLeft = computed(() => (hasValue.value ? pct(Number(props.value)) : 0))

// 标尺是图形，读屏用户需要等价的文字描述
const ariaLabel = computed(() =>
  band.value
    ? `${props.type} ${props.value}${shownUnit.value}，参考区间 ${band.value.low} 到 ${band.value.high}，${flagText(flag.value)}`
    : '',
)
</script>

<style scoped>
.lab {
  --tone: var(--flag-none);
  --tone-wash: var(--flag-none-wash);
}
.lab.high {
  --tone: var(--flag-high);
  --tone-wash: var(--flag-high-wash);
}
.lab.low {
  --tone: var(--flag-low);
  --tone-wash: var(--flag-low-wash);
}
.lab.normal {
  --tone: var(--flag-normal);
  --tone-wash: var(--flag-normal-wash);
}

.lab-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 12px;
}

/* 没有数值时不画标尺，也不留标尺的空位 —— 空卡片被撑成
   有数据卡片一样高，是"看起来坏了"的主要来源 */
.lab:has(.lab-value.empty) .lab-head {
  margin-bottom: 6px;
}

.lab-name {
  font-size: 14px;
  font-weight: 550;
  color: var(--ink-soft);
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 数值是这张卡的主角：等宽、大字号、随高低着色 */
.lab-value {
  font-size: 26px;
  font-weight: 600;
  letter-spacing: -0.02em;
  line-height: 1;
  color: var(--tone);
  white-space: nowrap;
}

.lab-value.empty {
  color: var(--ink-faint);
}

.lab-value small {
  font-size: 12px;
  font-weight: 500;
  margin-left: 3px;
  color: var(--ink-faint);
}

/* 轨道：整条是"可能的取值范围"，实心带是正常区间 */
.lab-track {
  position: relative;
  height: 6px;
  border-radius: 999px;
  background: var(--sunk);
}

.lab-band {
  position: absolute;
  top: 0;
  bottom: 0;
  border-radius: 999px;
  background: var(--flag-normal);
  opacity: 0.28;
}

/* 区间两端的界桩 */
.lab-tick {
  position: absolute;
  top: -2px;
  bottom: -2px;
  width: 1.5px;
  transform: translateX(-50%);
  background: var(--flag-normal);
  opacity: 0.5;
  border-radius: 1px;
}

/* 游标：当前值。带一圈底色描边，压在实心带上也分得清 */
.lab-mark {
  position: absolute;
  top: 50%;
  width: 12px;
  height: 12px;
  margin-top: -6px;
  margin-left: -6px;
  border-radius: 50%;
  background: var(--tone);
  box-shadow: 0 0 0 3px var(--card), 0 1px 3px rgba(15, 23, 42, 0.28);
  transition: left 0.5s var(--ease-out);
}

/* 刻度数字贴在界桩下方 */
.lab-scale {
  position: relative;
  height: 14px;
  margin-top: 5px;
  font-size: 11px;
  color: var(--ink-faint);
}

.lab-scale span {
  position: absolute;
  transform: translateX(-50%);
  white-space: nowrap;
}

.lab-foot {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 8px;
}

.lab-ref {
  font-size: 12px;
  color: var(--ink-faint);
}

.lab-ref b {
  font-weight: 550;
  color: var(--ink-mute);
}

.lab-noref {
  font-size: 12px;
  color: var(--ink-faint);
}

.lab.compact .lab-head {
  margin-bottom: 9px;
}

.lab.compact .lab-value {
  font-size: 20px;
}

@media (prefers-reduced-motion: reduce) {
  .lab-mark {
    transition: none;
  }
}
</style>
