<template>
  <section class="panel core-pad">
    <div class="section-head sec">
      <h3>健康建议</h3>
      <button class="btn btn-ghost btn-sm" type="button" :disabled="loading || !metrics.length" @click="$emit('generate')">
        {{ loading ? '生成中…' : '为「' + profile.displayName + '」生成建议' }}
      </button>
    </div>
    <p v-if="basis" class="basis">{{ basis }}</p>
    <div v-if="advice" class="prose" v-html="renderMarkdown(advice, terms)"></div>
    <div v-else class="quiet">
      {{ metrics.length ? '可以生成针对当前档案的建议，生成后会保存在这里。' : '先记一条血压或血糖，再生成建议。' }}
    </div>
    <MedicalDisclaimer />
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
.sec {
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
}
.basis {
  color: var(--ink-faint);
  font-size: 13px;
  margin: 0 0 var(--space-3);
}
.quiet {
  color: var(--ink-faint);
  font-size: 13px;
  line-height: 1.7;
  padding: var(--space-5) 0;
}
</style>
