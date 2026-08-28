<template>
  <div class="members">
    <button
      v-for="p in profiles"
      :key="p.id"
      class="member"
      :class="{ active: p.id === activeId }"
      type="button"
      @click="$emit('select', p.id!)"
    >
      <span class="av">{{ initial(p.displayName) }}</span>
      <span>
        <b>{{ p.displayName || '未命名' }}</b>
        <i>{{ p.relation || '档案' }} · {{ p.age ? p.age + '岁' : '年龄未填' }}</i>
      </span>
    </button>
    <button class="member add tile" type="button" @click="$emit('create')">
      <span class="av plus">+</span>
      <span>
        <b>新建档案</b>
        <i>本人 / 家人</i>
      </span>
    </button>
  </div>
</template>

<script setup lang="ts">
import type { HealthProfile } from '@/api/types'
import { initial } from '@/utils/format'

defineProps<{
  profiles: HealthProfile[]
  activeId: number | null
}>()

defineEmits<{
  select: [id: number]
  create: []
}>()
</script>

<style scoped>
.members {
  display: inline-flex;
  gap: 4px;
  /* 父级是 grid，默认会被拉满整行 —— 分段控件必须按内容宽度收住 */
  justify-self: start;
  width: fit-content;
  max-width: 100%;
  overflow-x: auto;
  padding: 4px;
  margin-bottom: 16px;
  /* macOS 分段控件：灰胶囊容器，选中项浮起为白段 */
  background: var(--sunk);
  border: 1px solid var(--edge);
  border-radius: 999px;
}
.member {
  min-width: 0;
  display: flex;
  gap: 10px;
  align-items: center;
  text-align: left;
  padding: 8px 14px;
  border: 0;
  cursor: pointer;
  border-radius: 999px;
  background: transparent;
  color: var(--ink-soft);
  transition: background 0.18s ease, box-shadow 0.18s ease, color 0.18s ease,
    transform 0.1s ease-out;
}
.member:hover {
  color: var(--ink);
}
.member:active {
  transform: scale(0.97);
}
.member.active {
  background: var(--card);
  color: var(--ink);
  box-shadow: var(--shadow-1);
}
.member.add {
  min-width: 0;
}
.member b {
  display: block;
}
.member i {
  display: block;
  font-style: normal;
  color: var(--ink-faint);
  font-size: 12px;
  margin-top: 2px;
}
.av {
  width: 30px;
  height: 30px;
  border-radius: 9px;
  background: var(--accent-wash);
  color: var(--accent-hover);
  display: grid;
  place-items: center;
  font-family: var(--font);
  flex-shrink: 0;
}
.av.plus {
  background: var(--sunk);
  font-size: 22px;
  color: var(--ink-soft);
}
</style>
