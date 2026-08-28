<template>
  <header class="head">
    <p v-if="kicker" class="eyebrow">{{ kicker }}</p>
    <div class="row">
      <div class="lead">
        <h1>{{ title }}</h1>
        <p v-if="desc" class="desc">{{ desc }}</p>
      </div>
      <div v-if="$slots.extra" class="extra">
        <slot name="extra" />
      </div>
    </div>
  </header>
</template>

<script setup lang="ts">
/**
 * 管理类页面的页头。三个顶级页面各自内联写 header（版式不同），
 * 这里只服务「知识库」「向量检索」这类结构一致的次级页面。
 * icon 保留在 props 里但不再渲染 —— 新版式靠 eyebrow + 大标题分层，
 * 页头挂图标砖会与顶部导航的品牌标打架。
 */
import type { IconName } from '@/utils/icons'

defineProps<{ title: string; desc?: string; kicker?: string; icon?: IconName }>()
</script>

<style scoped>
.head {
  margin-bottom: 22px;
}

.row {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
}

.lead {
  min-width: 0;
}

.lead h1 {
  margin-top: 4px;
}

.desc {
  margin-top: 10px;
  color: var(--ink-mute);
  font-size: 14px;
  line-height: 1.7;
  max-width: 32em;
}

.extra {
  flex-shrink: 0;
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

@media (max-width: 720px) {
  .row {
    flex-direction: column;
    align-items: stretch;
  }
}
</style>
