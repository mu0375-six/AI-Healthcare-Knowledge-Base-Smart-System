<template>
  <section class="panel dossier" aria-labelledby="dossier-title">
    <div class="who">
      <span class="av lg">{{ initial(profile.displayName) }}</span>
      <div class="identity">
        <span class="eyebrow">当前档案</span>
        <h3 id="dossier-title">{{ profile.displayName }}</h3>
        <div class="facts" aria-label="档案概要">
          <span><i>关系</i><b>{{ profile.relation || '档案' }}</b></span>
          <span><i>年龄</i><b class="num">{{ profile.age ? profile.age + '岁' : '未填写' }}</b></span>
          <span><i>性别</i><b>{{ profile.sex || '未填写' }}</b></span>
          <span><i>BMI</i><b class="num">{{ bmi != null ? bmi.toFixed(1) + ' · ' + bmiLabel(bmi) : '未记录' }}</b></span>
        </div>
        <p v-if="profile.allergies" class="allergy"><span>过敏提醒</span>{{ profile.allergies }}</p>
      </div>
      <div class="who-actions">
        <button class="btn btn-primary btn-sm" type="button" @click="$emit('ask')">结合档案去问</button>
        <button class="btn btn-ghost btn-sm" type="button" @click="$emit('toggle-edit')">
          {{ editing ? '收起资料' : '编辑资料' }}
        </button>
      </div>
    </div>

    <el-form v-if="editing" label-position="top" class="form">
      <div class="form-title">
        <strong>基础资料</strong>
        <span>编辑后将同步用于问诊与报告解读</span>
      </div>
      <div class="form-grid">
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
        <el-form-item label="过敏史" class="wide"><el-input v-model="form.allergies" type="textarea" :rows="2" /></el-form-item>
        <el-form-item label="档案共享" class="wide sharing">
          <el-switch v-model="form.sharedToAdmin" />
          <span class="tip">仅管理员可读，默认关闭</span>
        </el-form-item>
      </div>
      <div class="form-bar">
        <button class="btn btn-primary btn-sm" type="button" @click="$emit('save')">保存资料</button>
        <button v-if="removable" class="btn btn-quiet btn-sm danger" type="button" @click="$emit('delete')">删除此档案</button>
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
  position: relative;
  display: flex;
  gap: var(--space-5);
  align-items: center;
  padding: var(--space-5);
  border-left: 4px solid var(--accent);
}
.dossier h3 {
  margin: var(--space-1) 0 var(--space-3);
  font-size: 25px;
  line-height: 1.15;
}
.dossier p {
  margin: 0;
  color: var(--ink-faint);
}
.identity {
  flex: 1;
  min-width: 0;
}
.eyebrow {
  color: var(--accent);
  font-size: 11px;
  font-weight: 700;
}
.facts {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2) var(--space-5);
}
.facts span {
  display: inline-flex;
  align-items: baseline;
  gap: var(--space-2);
}
.facts i {
  color: var(--ink-faint);
  font-size: 11px;
  font-style: normal;
}
.facts b {
  color: var(--ink-soft);
  font-size: 13px;
  font-weight: 600;
}
.allergy {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  margin-top: var(--space-3) !important;
  color: var(--flag-high) !important;
  font-size: 13px;
}
.allergy span {
  padding: 2px var(--space-2);
  border: 1px solid var(--flag-high-line);
  border-radius: var(--r-chip);
  background: var(--flag-high-wash);
  font-size: 11px;
  font-weight: 650;
}
.who-actions {
  margin-left: auto;
  display: flex;
  gap: var(--space-2);
  flex-shrink: 0;
}
.av {
  width: 58px;
  height: 58px;
  border-radius: var(--r-avatar);
  background: var(--accent);
  color: var(--on-accent);
  display: grid;
  place-items: center;
  font-family: var(--font);
  font-size: 23px;
  flex-shrink: 0;
}
.form {
  padding: var(--space-5);
  border-top: 1px solid var(--edge);
  background: var(--sunk);
}
.form-title {
  display: flex;
  align-items: baseline;
  gap: var(--space-3);
  margin-bottom: var(--space-4);
}
.form-title strong {
  font-size: 14px;
}
.form-title span {
  color: var(--ink-faint);
  font-size: 12px;
}
.form-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 0 var(--space-4);
}
.form-grid .wide {
  grid-column: span 2;
}
.sharing :deep(.el-form-item__content) {
  min-height: 32px;
}
.form-bar {
  display: flex;
  gap: var(--space-3);
  justify-content: flex-end;
}
.tip {
  margin-left: var(--space-2);
  color: var(--ink-faint);
  font-size: 13px;
}
.danger {
  color: var(--flag-high);
}
@media (max-width: 900px) {
  .dossier .who {
    flex-wrap: wrap;
  }
  .who-actions {
    margin-left: 0;
    width: 100%;
  }
  .form-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
@media (max-width: 560px) {
  .dossier .who {
    align-items: flex-start;
    gap: var(--space-3);
    padding: var(--space-4);
  }
  .av {
    width: 44px;
    height: 44px;
    font-size: 18px;
  }
  .dossier h3 {
    font-size: 21px;
  }
  .facts {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    width: 100%;
    gap: var(--space-2);
  }
  .facts span {
    display: block;
  }
  .facts i,
  .facts b {
    display: block;
  }
  .facts b {
    margin-top: 2px;
  }
  .who-actions {
    display: grid;
    grid-template-columns: 1fr 1fr;
  }
  .form {
    padding: var(--space-4);
  }
  .form-title {
    display: block;
  }
  .form-title span {
    display: block;
    margin-top: var(--space-1);
  }
  .form-grid {
    grid-template-columns: 1fr;
  }
  .form-grid .wide {
    grid-column: auto;
  }
  .form-bar {
    justify-content: stretch;
  }
  .form-bar .btn {
    flex: 1;
  }
}
</style>
