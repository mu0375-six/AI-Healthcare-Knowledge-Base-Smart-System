<template>
  <el-dialog :model-value="visible" title="给谁建档" width="440px" @update:model-value="$emit('close')">
    <div class="quick">
      <button v-for="q in quickRels" :key="q.relation" type="button" @click="pick(q)">{{ q.label }}</button>
    </div>
    <el-form label-width="80px">
      <el-form-item label="称呼"><el-input v-model="form.displayName" placeholder="如：爸爸" /></el-form-item>
      <el-form-item label="关系">
        <el-select v-model="form.relation" style="width: 200px">
          <el-option v-for="r in relations" :key="r" :label="r" :value="r" />
        </el-select>
      </el-form-item>
      <el-form-item label="年龄"><el-input-number v-model="form.age" :min="0" :max="120" /></el-form-item>
      <el-form-item label="性别">
        <el-select v-model="form.sex" style="width: 160px">
          <el-option label="男" value="男" />
          <el-option label="女" value="女" />
        </el-select>
      </el-form-item>
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
.quick {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 16px;
}
.quick button {
  border: 1px solid var(--edge-strong);
  background: var(--sunk);
  border-radius: 999px;
  padding: 6px 12px;
  cursor: pointer;
}
</style>
