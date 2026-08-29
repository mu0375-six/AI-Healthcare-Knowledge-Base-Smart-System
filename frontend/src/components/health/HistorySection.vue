<template>
  <section class="panel core-pad">
    <div class="section-head sec">
      <h3>病史</h3>
      <button class="btn btn-primary btn-sm" type="button" @click="visible = true">新增病史</button>
    </div>
    <el-table :data="histories" empty-text="还没有病史，可点「新增病史」">
      <el-table-column prop="disease" label="疾病" />
      <el-table-column prop="diagnosedAt" label="诊断日期" />
      <el-table-column prop="status" label="状态" />
      <el-table-column prop="note" label="备注" />
      <el-table-column label="" width="80">
        <template #default="{ row }">
          <button class="btn btn-quiet btn-sm delete-history" type="button" @click="$emit('delete-history', row.id)">删除</button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="visible" title="新增病史" width="420px">
      <el-form label-width="80px">
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
.sec {
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
}
.delete-history {
  color: var(--flag-high);
}
</style>
