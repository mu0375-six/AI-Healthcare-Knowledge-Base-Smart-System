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
        <i>{{ memberMeta(p) }}</i>
      </span>
    </button>
    <button class="member add tile" type="button" @click="$emit('create')">
      <span class="av plus" aria-hidden="true" v-html="ICONS.plus"></span>
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
import { ICONS } from '@/utils/icons'

defineProps<{
  profiles: HealthProfile[]
  activeId: number | null
}>()

defineEmits<{
  select: [id: number]
  create: []
}>()

function memberMeta(profile: HealthProfile) {
  const relation = profile.relation || '档案'
  return profile.age ? `${relation} · ${profile.age}岁` : relation
}
</script>

<style scoped>
.members {
  display: inline-flex;
  gap: var(--space-1);
  /* 父级是 grid，默认会被拉满整行 —— 分段控件必须按内容宽度收住 */
  justify-self: start;
  width: fit-content;
  max-width: 100%;
  overflow-x: auto;
  padding: var(--space-1);
  /* macOS 分段控件：灰胶囊容器，选中项浮起为白段 */
  background: var(--sunk);
  border: 1px solid var(--edge);
  border-radius: var(--r-pill);
}
.member {
  min-width: 0;
  display: flex;
  gap: var(--space-3);
  align-items: center;
  text-align: left;
  padding: var(--space-2) var(--space-4);
  border: 0;
  cursor: pointer;
  border-radius: var(--r-pill);
  background: transparent;
  color: var(--ink-soft);
  transition: background 0.18s var(--ease-soft), box-shadow 0.18s var(--ease-soft), color 0.18s var(--ease-soft),
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
  margin-top: var(--space-1);
}
.av {
  width: 30px;
  height: 30px;
  border-radius: var(--r-avatar);
  background: var(--accent-wash);
  color: var(--accent-hover);
  display: grid;
  place-items: center;
  font-family: var(--font);
  flex-shrink: 0;
}
.av.plus {
  background: var(--sunk);
  color: var(--ink-soft);
}

.av.plus :deep(svg) {
  width: 18px;
  height: 18px;
}
</style>
