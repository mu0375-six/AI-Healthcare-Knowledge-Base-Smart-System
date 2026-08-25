<template>
  <div class="page">
    <PageHeader title="科室导诊" desc="写下主要症状。系统给出可能科室和紧急程度，不能替代分诊台。" />

    <section class="sheet sheet-pad">
      <el-form label-position="top">
        <el-form-item label="主要症状">
          <el-input v-model="form.symptoms" type="textarea" :rows="5" placeholder="例如：胸痛伴呼吸困难、反复头晕、多饮多尿…" />
        </el-form-item>
        <div class="row">
          <el-form-item label="年龄">
            <el-input-number v-model="form.age" :min="0" :max="120" />
          </el-form-item>
          <el-form-item label="性别">
            <el-select v-model="form.sex" clearable style="width: 160px">
              <el-option label="男" value="男" />
              <el-option label="女" value="女" />
            </el-select>
          </el-form-item>
        </div>
        <button class="copper-btn" type="button" :disabled="loading || !form.symptoms.trim()" @click="submit">
          {{ loading ? '正在导诊…' : '开始导诊' }}
        </button>
      </el-form>
    </section>

    <div v-if="result" class="result">
      <div class="banner" :class="result.urgency">综合紧急程度：{{ urgencyText(result.urgency) }}</div>
      <div class="depts">
        <article v-for="d in result.departments" :key="d.department" class="dept card">
          <h3>{{ d.department }}</h3>
          <span class="tag" :class="d.urgency">{{ urgencyText(d.urgency) }}</span>
          <p>{{ d.reason }}</p>
          <small>匹配分 {{ d.score }}</small>
        </article>
      </div>
      <section class="sheet sheet-pad">
        <div class="markdown-body" v-html="renderMarkdown(result.summary)"></div>
      </section>
    </div>
    <div v-else class="quiet">填写症状后查看推荐科室</div>
    <div class="disclaimer">以上内容仅供健康科普参考，不能替代执业医师的面诊、检查与处方。如有不适请及时就医。</div>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { runTriage } from '@/api/triage'
import type { TriageResult } from '@/api/types'
import { renderMarkdown } from '@/utils/markdown'
import PageHeader from '@/components/PageHeader.vue'

const loading = ref(false)
const result = ref<TriageResult | null>(null)
const form = reactive({ symptoms: '', age: undefined as number | undefined, sex: '' })

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
</script>

<style scoped>
.row {
  display: flex;
  gap: 24px;
}
.result {
  margin-top: 18px;
}
.banner {
  border-radius: var(--r-card);
  padding: 12px 16px;
  margin-bottom: 14px;
  font-weight: 600;
}
.banner.emergency {
  background: #fde8e6;
  color: var(--danger);
}
.banner.outpatient {
  background: #fff4e5;
  color: var(--warn);
}
.banner.self_care {
  background: #e7f0ec;
  color: var(--moss);
}
.depts {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
  margin-bottom: 14px;
}
.dept {
  padding: 18px 16px;
}
.dept h3 {
  margin: 0 0 8px;
  font-size: 22px;
}
.tag {
  display: inline-block;
  font-size: 12px;
  padding: 2px 8px;
  border-radius: 999px;
  background: var(--paper-deep);
}
.tag.emergency {
  background: #fde8e6;
  color: var(--danger);
}
.dept p {
  color: var(--ink-2);
  min-height: 48px;
  line-height: 1.6;
}
.dept small,
.quiet {
  color: var(--ink-3);
  font-size: 12px;
}
.quiet {
  padding: 28px 0;
  text-align: center;
}
@media (max-width: 860px) {
  .depts {
    grid-template-columns: 1fr;
  }
}
</style>
