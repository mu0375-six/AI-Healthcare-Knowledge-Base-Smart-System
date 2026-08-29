<template>
  <section class="panel alerts-panel" :class="{ urgent: warningCount > 0 }" aria-labelledby="alert-title">
    <div class="toolbar">
      <button
        type="button"
        class="head"
        :aria-expanded="expanded"
        aria-controls="alert-list"
        @click="expanded = !expanded"
      >
        <span class="alert-symbol" aria-hidden="true">!</span>
        <span class="title">
          <span class="title-line">
            <span id="alert-title" class="heading">异常监测</span>
            <span class="badge" :class="{ hot: warningCount > 0 }">{{ alertItems.length }}</span>
            <span v-if="warningCount" class="warning-count">{{ warningCount }} 项需复查</span>
          </span>
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
          <div class="severity-block">
            <span class="sev">{{ a.severity === 'warning' ? '需复查' : '待观察' }}</span>
            <small class="num">连续 {{ a.consecutiveAbnormal }} 次</small>
          </div>
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
              最近一次{{ flagTextOf(a.flag) }} · 共 {{ a.samples }} 次记录 · {{ a.recordedAt }}
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
  position: relative;
  overflow: hidden;
}
.alerts-panel::before {
  content: '';
  position: absolute;
  inset: 0 auto 0 0;
  width: 4px;
  background: var(--flag-none);
}
.alerts-panel.urgent::before {
  background: var(--flag-high);
}
.toolbar {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  padding: var(--space-3) var(--space-5);
  background: var(--flag-none-wash);
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
.alert-symbol {
  display: grid;
  place-items: center;
  width: 28px;
  height: 28px;
  flex: 0 0 28px;
  border: 1px solid var(--flag-high-line);
  border-radius: var(--r-avatar);
  background: var(--flag-high-wash);
  color: var(--flag-high);
  font-size: 15px;
  font-weight: 750;
}
.title {
  display: flex;
  flex-direction: column;
  gap: 2px;
  flex: 1;
  min-width: 0;
}
.title-line {
  display: flex;
  align-items: center;
  gap: var(--space-2);
}
.badge {
  min-width: 20px;
  height: 20px;
  border-radius: var(--r-chip);
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
  font-size: 15px;
  font-weight: 680;
  white-space: nowrap;
}
.warning-count {
  color: var(--flag-high);
  font-size: 11px;
  font-weight: 650;
}
.sub {
  font-size: 12px;
  color: var(--ink-faint);
  line-height: 1.5;
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
  position: relative;
  display: grid;
  grid-template-columns: 76px minmax(0, 1fr) auto;
  align-items: center;
  gap: var(--space-3);
  padding: var(--space-3) var(--space-5) var(--space-3) calc(var(--space-5) + 4px);
  border-top: 1px solid var(--edge);
  background: var(--flag-high-wash);
}
.item.watch {
  background: transparent;
}
.item:first-child {
  border-top: 0;
}
.item.done .body,
.item.done .severity-block {
  opacity: 0.48;
}
.severity-block {
  display: grid;
  justify-items: start;
  gap: var(--space-1);
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
  min-width: 56px;
  text-align: center;
}
.severity-block small {
  color: var(--ink-faint);
  font-size: 10px;
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
  font-weight: 680;
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
  color: var(--ink-mute);
  line-height: 1.55;
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
  .toolbar {
    padding: var(--space-3) var(--space-4);
  }
  .title-line {
    flex-wrap: wrap;
  }
  .item {
    grid-template-columns: 64px minmax(0, 1fr);
    padding: var(--space-3) var(--space-4);
  }
  .body {
    min-width: min(100%, 240px);
  }
  .reading {
    width: 100%;
    margin-left: 0;
  }
  .actions {
    grid-column: 1 / -1;
    width: 100%;
    justify-content: flex-end;
  }
}

@media (max-width: 460px) {
  .alert-symbol {
    display: none;
  }

  .warning-count {
    width: 100%;
  }

  .item {
    grid-template-columns: 1fr;
  }

  .severity-block {
    display: flex;
    align-items: center;
  }

  .actions {
    grid-column: 1;
    justify-content: stretch;
  }

  .actions .btn {
    flex: 1;
  }
}
</style>
