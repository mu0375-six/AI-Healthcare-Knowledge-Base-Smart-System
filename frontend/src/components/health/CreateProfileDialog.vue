<template>
  <el-dialog
    :model-value="visible"
    title="新建健康档案"
    width="min(460px, calc(100vw - 32px))"
    @update:model-value="$emit('close')"
  >
    <div class="group-label"><span>快捷成员</span><small>选择后可继续修改</small></div>
    <div class="quick">
      <button
        v-for="q in quickRels"
        :key="q.relation"
        class="chip-btn"
        :class="{ selected: form.relation === q.relation && form.displayName === q.displayName }"
        type="button"
        :aria-pressed="form.relation === q.relation && form.displayName === q.displayName"
        @click="pick(q)"
      >
        {{ q.label }}
      </button>
    </div>
    <div class="group-label details-label"><span>基础资料</span></div>
    <el-form label-position="top" class="profile-form">
      <div class="profile-grid">
        <el-form-item label="称呼"><el-input v-model="form.displayName" placeholder="如：爸爸" /></el-form-item>
        <el-form-item label="关系">
          <el-select v-model="form.relation">
            <el-option v-for="r in relations" :key="r" :label="r" :value="r" />
          </el-select>
        </el-form-item>
        <el-form-item label="年龄"><el-input-number v-model="form.age" :min="0" :max="120" /></el-form-item>
        <el-form-item label="性别">
          <el-select v-model="form.sex">
            <el-option label="男" value="男" />
            <el-option label="女" value="女" />
          </el-select>
        </el-form-item>
      </div>
    </el-form>
    <template #footer>
      <el-button @click="$emit('close')">取消</el-button>
      <el-button type="primary" @click="submit">创建</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { reactive, watch } from 'vue'

const props = defineProps<{
  visible: boolean
  relations: string[]
}>()

const emit = defineEmits<{
  close: []
  create: [payload: { displayName: string; relation: string; age?: number; sex: string }]
}>()

const quickRels = [
  { label: '本人', relation: '本人', displayName: '我', age: 30, sex: '男' },
  { label: '爸爸', relation: '父亲', displayName: '爸爸', age: 58, sex: '男' },
  { label: '妈妈', relation: '母亲', displayName: '妈妈', age: 56, sex: '女' },
  { label: '配偶', relation: '配偶', displayName: '爱人', age: 32, sex: '女' },
  { label: '孩子', relation: '子女', displayName: '小宝', age: 8, sex: '男' },
]

const form = reactive({ displayName: '', relation: '父亲', age: 50 as number | undefined, sex: '男' })

// 每次打开都重置到默认快捷项，避免上次残留
watch(
  () => props.visible,
  (v) => {
    if (v) pick(quickRels[1])
  },
)

function pick(q: (typeof quickRels)[number]) {
  form.displayName = q.displayName
  form.relation = q.relation
  form.age = q.age
  form.sex = q.sex
}

function submit() {
  emit('create', { displayName: form.displayName.trim(), relation: form.relation, age: form.age, sex: form.sex })
}
</script>

<style scoped>
.group-label {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: var(--space-3);
  margin-bottom: var(--space-2);
}
.group-label span {
  color: var(--ink);
  font-size: 12px;
  font-weight: 650;
}
.group-label small {
  color: var(--ink-faint);
  font-size: 11px;
}
.details-label {
  margin-top: var(--space-5);
  padding-top: var(--space-4);
  border-top: 1px solid var(--edge);
}
.quick {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: var(--space-2);
}
.quick .chip-btn {
  min-width: 0;
  padding: var(--space-2) var(--space-1);
}
.quick .chip-btn.selected {
  border-color: var(--accent);
  background: var(--accent-wash);
  color: var(--accent);
  font-weight: 650;
}
.profile-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 var(--space-4);
}
.profile-grid :deep(.el-select),
.profile-grid :deep(.el-input-number) {
  width: 100%;
}

@media (max-width: 420px) {
  .quick {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  .profile-grid {
    grid-template-columns: 1fr;
  }
}
</style>
