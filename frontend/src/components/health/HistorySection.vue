<template>
  <section class="panel history-panel" aria-labelledby="history-title">
    <header class="history-head">
      <div>
        <span>临床背景</span>
        <h3 id="history-title">病史与随访</h3>
        <small class="num">{{ histories.length }} 条记录</small>
      </div>
      <button class="btn btn-primary btn-sm" type="button" @click="visible = true">新增病史</button>
    </header>
    <el-table :data="histories" class="history-table" empty-text="还没有病史，可点「新增病史」">
      <el-table-column label="疾病" min-width="160">
        <template #default="{ row }"><strong class="disease">{{ row.disease || '未命名' }}</strong></template>
      </el-table-column>
      <el-table-column label="诊断日期" min-width="140">
        <template #default="{ row }"><time class="history-date num">{{ row.diagnosedAt || '未填写' }}</time></template>
      </el-table-column>
      <el-table-column label="状态" min-width="120">
        <template #default="{ row }"><span class="history-status">{{ row.status || '未填写' }}</span></template>
      </el-table-column>
      <el-table-column label="备注" min-width="180">
        <template #default="{ row }"><span class="history-note">{{ row.note || '—' }}</span></template>
      </el-table-column>
      <el-table-column label="" width="80">
        <template #default="{ row }">
          <button class="btn btn-quiet btn-sm delete-history" type="button" @click="$emit('delete-history', row.id)">删除</button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="visible" title="新增病史" width="min(420px, calc(100vw - 32px))">
      <el-form label-position="top" class="history-form">
        <el-form-item label="疾病"><el-input v-model="form.disease" /></el-form-item>
        <el-form-item label="日期"><el-date-picker v-model="form.diagnosedAt" type="date" value-format="YYYY-MM-DD" /></el-form-item>
        <el-form-item label="状态"><el-input v-model="form.status" placeholder="随访中 / 已控制" /></el-form-item>
        <el-form-item label="备注"><el-input v-model="form.note" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" @click="submit">确定</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import type { HealthHistory, HealthProfile } from '@/api/types'

const props = defineProps<{
  profile: HealthProfile
  histories: HealthHistory[]
}>()

const emit = defineEmits<{
  add: [payload: Partial<HealthHistory> & { profileId: number }]
  'delete-history': [id: number]
}>()

const visible = ref(false)
const form = reactive({ disease: '', diagnosedAt: '', status: '随访中', note: '' })

function submit() {
  const payload: Partial<HealthHistory> & { profileId: number } = {
    profileId: props.profile.id!,
    disease: form.disease,
    status: form.status,
    note: form.note,
  }
  if (form.diagnosedAt) payload.diagnosedAt = form.diagnosedAt
  emit('add', payload)
  visible.value = false
}
</script>

<style scoped>
.history-panel {
  overflow: hidden;
}
.history-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: var(--space-4);
  padding: var(--space-4) var(--space-5);
  border-bottom: 1px solid var(--edge);
  background: var(--flag-none-wash);
}
.history-head span {
  display: block;
  margin-bottom: var(--space-1);
  color: var(--accent);
  font-size: 11px;
  font-weight: 700;
}
.history-head h3 {
  display: inline;
  margin: 0;
  font-size: 16px;
  font-weight: 680;
}
.history-head small {
  margin-left: var(--space-2);
  color: var(--ink-faint);
  font-size: 11px;
}
.history-table {
  padding: 0 var(--space-5) var(--space-3);
}
.history-table :deep(th.el-table__cell) {
  color: var(--ink-mute);
  font-size: 11px;
  font-weight: 650;
}
.disease {
  color: var(--ink);
  font-size: 13px;
  font-weight: 650;
}
.history-date,
.history-note {
  color: var(--ink-mute);
  font-size: 12px;
}
.history-status {
  display: inline-flex;
  padding: 2px var(--space-2);
  border: 1px solid var(--accent-line);
  border-radius: var(--r-chip);
  background: var(--accent-wash);
  color: var(--accent);
  font-size: 11px;
  font-weight: 650;
}
.history-form {
  padding-top: var(--space-2);
}
.delete-history {
  color: var(--flag-high);
}

@media (hover: hover) and (pointer: fine) {
  .delete-history {
    opacity: 0;
  }

  :deep(.el-table__row:hover) .delete-history,
  :deep(.el-table__row:focus-within) .delete-history,
  .delete-history:focus-visible {
    opacity: 1;
  }
}

@media (max-width: 720px) {
  .history-head {
    padding: var(--space-4);
  }

  .history-table {
    padding-inline: var(--space-2);
  }
}
</style>
