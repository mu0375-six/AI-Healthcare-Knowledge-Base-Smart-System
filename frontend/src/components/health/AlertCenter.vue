<template>
  <section class="panel alerts-panel" aria-labelledby="alert-title">
    <div class="toolbar">
      <button
        type="button"
        class="head"
        :aria-expanded="expanded"
        aria-controls="alert-list"
        @click="expanded = !expanded"
      >
        <span class="title">
          <span class="badge" :class="{ hot: warningCount > 0 }">{{ alertItems.length }}</span>
          <span id="alert-title" class="heading">异常提醒</span>
          <span class="sub">连续 {{ '≥' }} 3 次超出参考范围会升级为「需复查」</span>
        </span>
        <svg viewBox="0 0 24 24" class="chev" :class="{ up: expanded }" aria-hidden="true" focusable="false">
          <path d="m7 10 5 5 5-5" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" />
        </svg>
      </button>
      <div class="filters">
        <el-select v-model="profileFilter" size="small" style="width: 130px" clearable placeholder="全部成员">
          <el-option v-for="p in profiles" :key="p.id" :label="p.displayName || p.relation" :value="p.id!" />
        </el-select>
      </div>
    </div>

    <div id="alert-list" v-show="expanded" class="alert-content">
      <div v-if="filtered.length" class="list">
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
              <LabStrip
                class="reading"
                variant="inline"
                :type="a.metricType"
                :value="a.latestValue"
                :unit="a.unit"
                :flag-override="a.flag"
              />
            </div>
            <p class="line-2">
              最近一次{{ flagTextOf(a.flag) }}，已连续 {{ a.consecutiveAbnormal }} 次超范围（共 {{ a.samples }} 次记录）·
              {{ a.recordedAt }}
            </p>
          </div>
          <div class="actions">
            <button class="btn btn-quiet btn-sm" type="button" @click="$emit('locate', a.profileId)">看趋势</button>
            <button class="btn btn-quiet btn-sm" type="button" @click="toggleDone(a)">
              {{ isDone(a) ? '标为未读' : '标为已处理' }}
            </button>
          </div>
        </div>
      </div>
      <div v-else class="all-good">
        <span class="dot"></span>
        当前没有异常提醒。录入新指标后会自动比对参考范围。
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import type { HealthProfile } from '@/api/types'
import type { MetricAlertItem } from '@/api/health'
import LabStrip from '@/components/LabStrip.vue'

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
  overflow: hidden;
}
.toolbar {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  padding: var(--space-4) var(--space-5);
}
.head {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  min-width: 0;
  flex: 1;
  border: 0;
  padding: 0;
  background: transparent;
  color: var(--ink);
  font: inherit;
  text-align: left;
  cursor: pointer;
  user-select: none;
}
.title {
  display: flex;
  align-items: baseline;
  gap: var(--space-3);
  flex: 1;
  min-width: 0;
}
.badge {
  min-width: 22px;
  height: 22px;
  border-radius: var(--r-pill);
  background: var(--sunk);
  color: var(--ink-soft);
  font-size: 12px;
  font-weight: 600;
  display: inline-grid;
  place-items: center;
  padding: 0 var(--space-2);
  align-self: center;
}
.badge.hot {
  background: var(--flag-high);
  color: var(--on-accent);
}
.heading {
  font-size: 16.5px;
  font-weight: 600;
  white-space: nowrap;
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
  transition: transform 0.2s var(--ease-soft);
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
  gap: var(--space-3);
  padding: var(--space-3) var(--space-5);
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
  background: var(--flag-high-wash);
  border: 1px solid var(--flag-high-line);
  padding: var(--space-1) var(--space-2);
  border-radius: var(--r-chip);
  width: 52px;
  text-align: center;
}
.item.watch .sev {
  color: var(--flag-none);
  background: var(--flag-none-wash);
  border-color: var(--flag-none-line);
}
.body {
  flex: 1;
  min-width: 0;
}
.line-1 {
  display: flex;
  align-items: center;
  gap: var(--space-2);
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
.reading {
  margin-left: auto;
}
.line-2 {
  margin: var(--space-1) 0 0;
  font-size: 12px;
  color: var(--ink-faint);
}
.actions {
  display: flex;
  align-items: center;
  gap: var(--space-1);
  flex-shrink: 0;
}
.all-good {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  padding: var(--space-4) var(--space-5);
  border-top: 1px solid var(--edge);
  color: var(--ink-soft);
  font-size: 13px;
}
.all-good .dot {
  width: 8px;
  height: 8px;
  border-radius: var(--r-pill);
  background: var(--flag-normal);
  flex-shrink: 0;
}
@media (max-width: 720px) {
  .toolbar {
    align-items: stretch;
    flex-direction: column;
  }
  .sub {
    display: none;
  }
  .item {
    flex-wrap: wrap;
  }
  .body {
    min-width: min(100%, 240px);
  }
  .reading {
    width: 100%;
    margin-left: 0;
  }
  .actions {
    width: 100%;
    justify-content: flex-end;
  }
}
</style>
