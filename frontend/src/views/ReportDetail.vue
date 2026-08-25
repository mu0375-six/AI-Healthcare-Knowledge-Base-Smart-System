<template>
  <div v-if="report" class="page">
    <PageHeader :title="report.filename || '检查报告'" :desc="formatWhen(report.createdAt)">
      <template #extra>
        <el-select v-model="profileId" placeholder="写入档案" style="width: 160px; margin-right: 8px" size="small">
          <el-option v-for="p in profiles" :key="p.id" :label="p.displayName || '档案'" :value="p.id" />
        </el-select>
        <button class="copper-btn slim" type="button" :disabled="!profileId || importing" @click="doImport">
          {{ importing ? '写入中…' : '写入档案' }}
        </button>
        <button class="ghost-btn" type="button" @click="$router.push('/reports')">返回列表</button>
      </template>
    </PageHeader>

    <section class="sheet sheet-pad">
      <h3>指标明细</h3>
      <el-table :data="items" empty-text="未能自动拆分指标，请查看原文与总体解读">
        <el-table-column prop="name" label="项目" />
        <el-table-column label="结果">
          <template #default="{ row }">
            <span :class="'flag-' + row.flag">{{ row.value }} {{ row.unit }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="refRange" label="参考范围" />
        <el-table-column label="标志">
          <template #default="{ row }">
            <el-tag :type="tagType(row.flag)" size="small">{{ flagText(row.flag) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="interpretation" label="解读" />
      </el-table>
    </section>

    <section class="sheet sheet-pad block">
      <h3>总体解读</h3>
      <div class="markdown-body" v-html="renderMarkdown(report.summary || '', terms)"></div>
    </section>
    <section class="sheet sheet-pad block">
      <h3>原文摘录</h3>
      <pre class="raw">{{ report.rawText }}</pre>
    </section>
    <div class="disclaimer">以上内容仅供健康科普参考，不能替代执业医师的面诊、检查与处方。如有不适请及时就医。</div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { importReportToProfile, reportDetail } from '@/api/reports'
import { listProfiles } from '@/api/health'
import { listTerms } from '@/api/knowledge'
import type { ExamReport, ExamReportItem, HealthProfile } from '@/api/types'
import { renderMarkdown } from '@/utils/markdown'
import { formatWhen } from '@/utils/format'
import PageHeader from '@/components/PageHeader.vue'

const route = useRoute()
const report = ref<ExamReport | null>(null)
const items = ref<ExamReportItem[]>([])
const terms = ref<string[]>([])
const profiles = ref<HealthProfile[]>([])
const profileId = ref<number | undefined>(undefined)
const importing = ref(false)

onMounted(async () => {
  const id = Number(route.params.id)
  const res = await reportDetail(id)
  report.value = res.data.report
  items.value = res.data.items || []
  profileId.value = res.data.report.profileId || undefined
  try {
    terms.value = (await listTerms()).data || []
  } catch {
    terms.value = []
  }
  try {
    profiles.value = (await listProfiles()).data || []
    if (!profileId.value && profiles.value[0]?.id) profileId.value = profiles.value[0].id
  } catch {
    profiles.value = []
  }
})

async function doImport() {
  if (!report.value || !profileId.value) return
  importing.value = true
  try {
    const d = (await importReportToProfile(report.value.id, profileId.value)).data
    ElMessage.success('已写入 ' + (d.imported || 0) + ' 条指标')
  } finally {
    importing.value = false
  }
}

function flagText(f: string) {
  return ({ high: '偏高', low: '偏低', normal: '正常', unknown: '未知' } as Record<string, string>)[f] || f
}

function tagType(f: string) {
  if (f === 'high') return 'danger'
  if (f === 'low') return 'warning'
  if (f === 'normal') return 'success'
  return 'info'
}
</script>

<style scoped>
h3 {
  margin: 0 0 12px;
  font-size: 20px;
}
.slim {
  padding: 6px 12px;
  font-size: 13px;
}
.block {
  margin-top: 14px;
}
.raw {
  white-space: pre-wrap;
  font-size: 13px;
  color: var(--ink-2);
  margin: 0;
}
</style>
