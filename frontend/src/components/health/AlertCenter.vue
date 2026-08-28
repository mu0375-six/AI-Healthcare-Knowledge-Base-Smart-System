<template>
  <section class="panel alerts-panel">
    <div class="head" @click="expanded = !expanded">
      <div class="title">
        <span class="badge" :class="{ hot: warningCount > 0 }">{{ alertItems.length }}</span>
        <h3>异常提醒</h3>
        <span class="sub">连续 {{ '≥' }} 3 次超出参考范围会升级为「需复查」</span>
      </div>
      <div class="filters" @click.stop>
        <el-select v-model="profileFilter" size="small" style="width: 130px" clearable placeholder="全部成员">
          <el-option v-for="p in profiles" :key="p.id" :label="p.displayName || p.relation" :value="p.id!" />
        </el-select>
      </div>
      <svg viewBox="0 0 24 24" class="chev" :class="{ up: expanded }">
        <path d="m7 10 5 5 5-5" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" />
      </svg>
    </div>

    <div v-if="expanded && filtered.length" class="list">
      <div
        v-for="a in filtered"
        :key="a.profileId + '-' + a.metricType"
        class="item"
        :class="{ done: isDone(a), watch: a.severity === 'watch' }"
      >
        <span class="sev">
          {{ a.severity === 'warning' ? '需复查' : '待观察' }}
        </span>
        <div class="body">
          <div class="line-1">
            <b>{{ a.metricType }}</b>
            <i>{{ a.profileName }}</i>
            <em :class="'flag-' + a.flag">{{ a.latestValue }}{{ a.unit }}</em>
            <span class="ref">参考 {{ a.refRange }}</span>
          </div>
          <p class="line-2">
            最近一次{{ flagTextOf(a.flag) }}，已连续 {{ a.consecutiveAbnormal }} 次超范围（共 {{ a.samples }} 次记录）·
            {{ a.recordedAt }}
          </p>
        </div>
        <el-button text type="primary" size="small" @click="$emit('locate', a.profileId)">看趋势</el-button>
        <el-button text size="small" @click="toggleDone(a)">{{ isDone(a) ? '标为未读' : '标为已处理' }}</el-button>
      </div>
    </div>
    <div v-else-if="expanded" class="all-good">
      <span class="dot"></span>
      当前没有异常提醒。录入新指标后会自动比对参考范围。
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import type { HealthProfile } from '@/api/types'
import type { MetricAlertItem } from '@/api/health'

/**
 * 「已处理」标记只存本地（localStorage）：项目 DDL 只有 CREATE IF NOT EXISTS、
 * 无迁移机制，为一个小勾动数据库表不值得；换设备不共享。
 */
const DONE_KEY = 'alerts-handled'

const props = defineProps<{
  alerts: MetricAlertItem[]
  profiles: HealthProfile[]
}>()

defineEmits<{
  locate: [profileId: number | null]
}>()

const expanded = ref(true)
const profileFilter = ref<number | null>(null)
const handled = ref<Record<string, string>>(readHandled())

function readHandled(): Record<string, string> {
  try {
    return JSON.parse(localStorage.getItem(DONE_KEY) || '{}') as Record<string, string>
  } catch {
    return {}
  }
}

function keyOf(a: MetricAlertItem) {
  return `${a.profileId ?? 0}:${a.metricType}`
}

function isDone(a: MetricAlertItem) {
  // 后端记录有更新（值/时间变化）时自动视为未处理
  return handled.value[keyOf(a)] === a.recordedAt + '@' + a.latestValue
}

function toggleDone(a: MetricAlertItem) {
  const k = keyOf(a)
  if (isDone(a)) {
    delete handled.value[k]
  } else {
    handled.value[k] = a.recordedAt + '@' + a.latestValue
  }
  localStorage.setItem(DONE_KEY, JSON.stringify(handled.value))
}

watch(
  () => props.alerts,
  () => {
    handled.value = readHandled()
  },
)

const alertItems = computed(() => props.alerts)
const warningCount = computed(() => props.alerts.filter((a) => a.severity === 'warning').length)
const filtered = computed(() =>
  props.alerts.filter((a) => !profileFilter.value || a.profileId === profileFilter.value),
)

function flagTextOf(flag: string) {
  return flag === 'high' ? '偏高' : '偏低'
}
</script>

<style scoped>
.alerts-panel {
  margin-bottom: 16px;
  overflow: hidden;
}
.head {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px 20px;
  cursor: pointer;
  user-select: none;
}
.title {
  display: flex;
  align-items: baseline;
  gap: 10px;
  flex: 1;
  min-width: 0;
}
.badge {
  min-width: 22px;
  height: 22px;
  border-radius: 999px;
  background: var(--sunk);
  color: var(--ink-soft);
  font-size: 12px;
  font-weight: 600;
  display: inline-grid;
  place-items: center;
  padding: 0 6px;
  align-self: center;
}
.badge.hot {
  background: var(--flag-high);
  color: #fff;
}
h3 {
  margin: 0;
  font-size: 17px;
}
.sub {
  font-size: 12px;
  color: var(--ink-faint);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.chev {
  width: 18px;
  height: 18px;
  color: var(--ink-faint);
  transition: transform 0.2s ease;
  flex-shrink: 0;
}
.chev.up {
  transform: rotate(180deg);
}
.list {
  border-top: 1px solid var(--edge);
  max-height: 420px;
  overflow-y: auto;
}
.item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 20px;
  border-top: 1px solid var(--edge);
}
.item:first-child {
  border-top: 0;
}
.item.done .body {
  opacity: 0.45;
}
.sev {
  flex-shrink: 0;
  font-size: 11px;
  font-weight: 600;
  color: var(--flag-high);
  background: rgba(220, 38, 38, 0.08);
  padding: 4px 8px;
  border-radius: var(--r-chip);
  width: 52px;
  text-align: center;
}
.item.watch .sev {
  color: var(--flag-high);
  background: rgba(217, 119, 6, 0.1);
}
.body {
  flex: 1;
  min-width: 0;
}
.line-1 {
  display: flex;
  align-items: baseline;
  gap: 8px;
  flex-wrap: wrap;
}
.line-1 b {
  font-size: 14px;
}
.line-1 i {
  font-style: normal;
  font-size: 12px;
  color: var(--ink-faint);
}
.line-1 em {
  font-style: normal;
  font-weight: 600;
  font-size: 15px;
}
.ref {
  font-size: 12px;
  color: var(--ink-faint);
}
.line-2 {
  margin: 3px 0 0;
  font-size: 12px;
  color: var(--ink-faint);
}
.all-good {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 14px 20px;
  border-top: 1px solid var(--edge);
  color: var(--ink-soft);
  font-size: 13px;
}
.all-good .dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--flag-normal);
  flex-shrink: 0;
}
@media (max-width: 720px) {
  .sub {
    display: none;
  }
  .item {
    flex-wrap: wrap;
  }
}
</style>
