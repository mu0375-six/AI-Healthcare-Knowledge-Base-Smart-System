<template>
  <section class="panel core-pad dossier">
    <div class="who">
      <span class="av lg">{{ initial(profile.displayName) }}</span>
      <div>
        <h3>{{ profile.displayName }}</h3>
        <p>
          {{ profile.relation || '档案' }}
          <template v-if="profile.age"> · {{ profile.age }}岁</template>
          <template v-if="profile.sex"> · {{ profile.sex }}</template>
          <template v-if="bmi != null"> · BMI {{ bmi.toFixed(1) }}（{{ bmiLabel(bmi) }}）</template>
        </p>
        <p v-if="profile.allergies" class="allergy">过敏：{{ profile.allergies }}</p>
      </div>
      <div class="who-actions">
        <button class="btn btn-primary btn-sm" type="button" @click="$emit('ask')">结合档案去问</button>
        <button class="btn btn-ghost btn-sm" type="button" @click="$emit('toggle-edit')">
          {{ editing ? '收起资料' : '编辑资料' }}
        </button>
      </div>
    </div>

    <el-form v-if="editing" label-width="88px" class="form">
      <el-form-item label="称呼"><el-input v-model="form.displayName" placeholder="如：我 / 爸爸 / 小宝" /></el-form-item>
      <el-form-item label="关系">
        <el-select v-model="form.relation" style="width: 100%">
          <el-option v-for="r in relations" :key="r" :label="r" :value="r" />
        </el-select>
      </el-form-item>
      <el-form-item label="年龄"><el-input-number v-model="form.age" :min="0" :max="120" /></el-form-item>
      <el-form-item label="性别">
        <el-select v-model="form.sex" style="width: 100%">
          <el-option label="男" value="男" />
          <el-option label="女" value="女" />
          <el-option label="其他" value="其他" />
        </el-select>
      </el-form-item>
      <el-form-item label="身高 cm"><el-input-number v-model="form.heightCm" :min="50" :max="250" :precision="1" /></el-form-item>
      <el-form-item label="体重 kg"><el-input-number v-model="form.weightKg" :min="10" :max="300" :precision="1" /></el-form-item>
      <el-form-item label="过敏史"><el-input v-model="form.allergies" type="textarea" :rows="2" /></el-form-item>
      <el-form-item label="共享">
        <el-switch v-model="form.sharedToAdmin" />
        <span class="tip">仅管理员可读，默认关闭</span>
      </el-form-item>
      <div class="form-bar">
        <button class="btn btn-ghost btn-sm" type="button" @click="$emit('save')">保存资料</button>
        <button v-if="removable" class="btn btn-ghost btn-sm" type="button" @click="$emit('delete')">删除此档案</button>
      </div>
    </el-form>
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { HealthProfile } from '@/api/types'
import { initial } from '@/utils/format'
import { bmiLabel, bmiOf } from '@/utils/metrics'

const props = defineProps<{
  profile: HealthProfile
  form: HealthProfile
  editing: boolean
  removable: boolean
  relations: string[]
}>()

defineEmits<{
  ask: []
  'toggle-edit': []
  save: []
  delete: []
}>()

const bmi = computed(() => bmiOf(props.profile.heightCm, props.profile.weightKg))
</script>

<style scoped>
.dossier .who {
  display: flex;
  gap: var(--space-4);
  align-items: flex-start;
}
.dossier h3 {
  margin: 0 0 var(--space-1);
  font-size: 24px;
}
.dossier p {
  margin: 0;
  color: var(--ink-faint);
}
.allergy {
  margin-top: var(--space-2) !important;
  color: var(--accent-hover) !important;
}
.who-actions {
  margin-left: auto;
  display: flex;
  gap: var(--space-2);
  flex-shrink: 0;
}
.av {
  width: 52px;
  height: 52px;
  border-radius: 50%;
  background: var(--accent-wash);
  color: var(--accent-hover);
  display: grid;
  place-items: center;
  font-family: var(--font);
  font-size: 22px;
  flex-shrink: 0;
}
.form {
  margin-top: var(--space-5);
  padding-top: var(--space-3);
  border-top: 1px solid var(--edge);
}
.form-bar {
  display: flex;
  gap: var(--space-3);
}
.tip {
  color: var(--ink-faint);
  font-size: 13px;
}
@media (max-width: 900px) {
  .dossier .who {
    flex-wrap: wrap;
  }
  .who-actions {
    margin-left: 0;
    width: 100%;
  }
}
</style>
