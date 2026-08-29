<template>
  <nav class="members" aria-label="家庭成员档案">
    <div class="members-label">
      <span>家庭成员</span>
      <strong>档案切换</strong>
      <small class="num">{{ profiles.length }} 份档案</small>
    </div>
    <div class="member-list">
      <button
        v-for="p in profiles"
        :key="p.id"
        class="member"
        :class="{ active: p.id === activeId }"
        type="button"
        :aria-pressed="p.id === activeId"
        @click="$emit('select', p.id!)"
      >
        <span class="av">{{ initial(p.displayName) }}</span>
        <span class="member-copy">
          <b>{{ p.displayName || '未命名' }}</b>
          <i>{{ memberMeta(p) }}</i>
        </span>
        <span v-if="p.id === activeId" class="current-mark">当前</span>
      </button>
      <button class="member add" type="button" @click="$emit('create')">
        <span class="av plus" aria-hidden="true" v-html="ICONS.plus"></span>
        <span class="member-copy">
          <b>新建档案</b>
          <i>本人 / 家人</i>
        </span>
      </button>
    </div>
  </nav>
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
  display: grid;
  grid-template-columns: 148px minmax(0, 1fr);
  width: 100%;
  min-width: 0;
  background: var(--card);
  border: 1px solid var(--edge);
  border-radius: var(--r-shell);
  box-shadow: var(--shadow-1);
  overflow: hidden;
}

.members-label {
  display: flex;
  flex-direction: column;
  justify-content: center;
  padding: var(--space-3) var(--space-4);
  border-right: 1px solid var(--edge);
  background: var(--sunk);
}

.members-label span {
  color: var(--accent);
  font-size: 11px;
  font-weight: 700;
}

.members-label strong {
  margin-top: var(--space-1);
  color: var(--ink);
  font-size: 14px;
}

.members-label small {
  margin-top: var(--space-1);
  color: var(--ink-faint);
  font-size: 11px;
}

.member-list {
  display: flex;
  min-width: 0;
  overflow-x: auto;
  scrollbar-width: thin;
}
.member {
  position: relative;
  min-width: 154px;
  display: flex;
  gap: var(--space-2);
  align-items: center;
  text-align: left;
  padding: var(--space-3) var(--space-4);
  border: 0;
  border-right: 1px solid var(--edge);
  cursor: pointer;
  background: transparent;
  color: var(--ink-soft);
  transition: background 0.16s var(--ease-soft), color 0.16s var(--ease-soft);
}
.member:active {
  background: var(--tray);
}
.member.active {
  background: var(--accent-wash);
  color: var(--ink);
  box-shadow: inset 3px 0 0 var(--accent);
}
.member.add {
  min-width: 148px;
  border-right: 0;
}
.member b {
  display: block;
  font-size: 13px;
  font-weight: 650;
}
.member i {
  display: block;
  font-style: normal;
  color: var(--ink-faint);
  font-size: 12px;
  margin-top: var(--space-1);
}
.member-copy {
  min-width: 0;
}
.current-mark {
  align-self: flex-start;
  margin-left: auto;
  color: var(--accent);
  font-size: 10px;
  font-weight: 700;
}
.av {
  width: 34px;
  height: 34px;
  border-radius: var(--r-avatar);
  background: var(--accent-wash);
  color: var(--accent-hover);
  display: grid;
  place-items: center;
  font-family: var(--font);
  flex-shrink: 0;
  font-size: 14px;
}
.member.active .av {
  background: var(--accent);
  color: var(--on-accent);
}
.av.plus {
  background: var(--sunk);
  color: var(--ink-soft);
}

.av.plus :deep(svg) {
  width: 18px;
  height: 18px;
}

@media (hover: hover) and (pointer: fine) {
  .member:hover {
    background: var(--tray);
    color: var(--ink);
  }

  .member.active:hover {
    background: var(--accent-wash);
  }
}

@media (max-width: 720px) {
  .members {
    grid-template-columns: 1fr;
  }

  .members-label {
    display: grid;
    grid-template-columns: auto 1fr auto;
    align-items: baseline;
    gap: var(--space-2);
    padding: var(--space-2) var(--space-3);
    border-right: 0;
    border-bottom: 1px solid var(--edge);
  }

  .members-label strong {
    margin-top: 0;
  }

  .members-label small {
    margin: 0;
  }

  .member {
    min-width: 142px;
    padding: var(--space-2) var(--space-3);
  }

  .member.active {
    box-shadow: inset 0 -3px 0 var(--accent);
  }

  .current-mark {
    display: none;
  }
}
</style>
