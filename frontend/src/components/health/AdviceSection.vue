<template>
  <section class="panel core-pad block">
    <div class="sec">
      <h3>健康建议</h3>
      <button class="btn btn-ghost slim" type="button" :disabled="loading || !metrics.length" @click="$emit('generate')">
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
h3 {
  margin: 0;
  font-size: 20px;
}
.slim {
  padding: 6px 12px;
  font-size: 13px;
}
.basis {
  color: var(--ink-faint);
  font-size: 13px;
  margin: 0 0 10px;
}
.quiet {
  color: var(--ink-faint);
  font-size: 13px;
  line-height: 1.7;
  padding: 18px 0;
}
</style>
