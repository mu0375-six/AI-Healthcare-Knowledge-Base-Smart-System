<template>
  <section class="panel advice-panel" aria-labelledby="advice-title">
    <header class="advice-head">
      <div>
        <span>档案结论</span>
        <h3 id="advice-title">健康建议</h3>
        <small>{{ profile.displayName }} · 基于 {{ metrics.length }} 条指标</small>
      </div>
      <button class="btn btn-primary btn-sm" type="button" :disabled="loading || !metrics.length" @click="$emit('generate')">
        {{ loading ? '生成中…' : '为「' + profile.displayName + '」生成建议' }}
      </button>
    </header>
    <div class="advice-content">
      <p v-if="basis" class="basis"><span>依据</span>{{ basis }}</p>
      <div v-if="advice" class="advice-document">
        <span class="document-label">健康摘要</span>
        <div class="prose" v-html="renderMarkdown(advice, terms)"></div>
      </div>
      <div v-else class="quiet">
        <strong>尚未生成建议</strong>
        <span>{{ metrics.length ? '可以根据当前指标生成建议。' : '先记一条血压或血糖，再生成建议。' }}</span>
      </div>
      <MedicalDisclaimer />
    </div>
  </section>
</template>

<script setup lang="ts">
import type { HealthMetric, HealthProfile } from '@/api/types'
import { renderMarkdown } from '@/utils/markdown'
import MedicalDisclaimer from '@/components/MedicalDisclaimer.vue'

defineProps<{
  profile: HealthProfile
  metrics: HealthMetric[]
  advice: string
  basis: string
  loading: boolean
  terms: string[]
}>()

defineEmits<{
  generate: []
}>()
</script>

<style scoped>
.advice-panel {
  overflow: hidden;
}
.advice-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: var(--space-4);
  padding: var(--space-4) var(--space-5);
  border-bottom: 1px solid var(--edge);
  background: var(--flag-none-wash);
}
.advice-head span {
  display: block;
  margin-bottom: var(--space-1);
  color: var(--accent);
  font-size: 11px;
  font-weight: 700;
}
.advice-head h3 {
  display: inline;
  margin: 0;
  font-size: 16px;
  font-weight: 680;
}
.advice-head small {
  margin-left: var(--space-2);
  color: var(--ink-faint);
  font-size: 11px;
}
.advice-content {
  padding: var(--space-5);
}
.basis {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  color: var(--ink-faint);
  font-size: 13px;
  margin: 0 0 var(--space-4);
}
.basis span {
  padding: 2px var(--space-2);
  border: 1px solid var(--info-line);
  border-radius: var(--r-chip);
  background: var(--info-wash);
  color: var(--info);
  font-size: 10px;
  font-weight: 700;
}
.advice-document {
  position: relative;
  padding: var(--space-4) var(--space-5);
  border-left: 4px solid var(--accent);
  background: var(--accent-wash);
}
.document-label {
  color: var(--accent);
  font-size: 11px;
  font-weight: 700;
}
.advice-document .prose {
  max-width: 54em;
  margin-top: var(--space-3);
}
.quiet {
  display: grid;
  gap: var(--space-1);
  padding: var(--space-5);
  border-left: 4px solid var(--edge-strong);
  background: var(--flag-none-wash);
  color: var(--ink-faint);
  font-size: 13px;
  line-height: 1.7;
}
.quiet strong {
  color: var(--ink-soft);
  font-size: 14px;
}
.advice-content :deep(.disclaimer) {
  margin-top: var(--space-5);
}

@media (max-width: 720px) {
  .advice-head {
    align-items: stretch;
    flex-direction: column;
    padding: var(--space-4);
  }

  .advice-head .btn {
    width: 100%;
  }

  .advice-content {
    padding: var(--space-4);
  }

  .advice-document,
  .quiet {
    padding: var(--space-4);
  }
}
</style>
