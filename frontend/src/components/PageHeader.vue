<template>
  <header class="head">
    <div v-if="$slots.back" class="back">
      <slot name="back" />
    </div>
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
defineProps<{ title: string; desc?: string; kicker?: string }>()
</script>

<style scoped>
.head {
  margin-bottom: var(--space-5);
}

.back {
  margin-bottom: var(--space-3);
}

.row {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: var(--space-4);
}

.lead {
  min-width: 0;
}

.lead h1 {
  margin-top: var(--space-1);
}

.desc {
  margin-top: var(--space-3);
  color: var(--ink-mute);
  font-size: 14px;
  line-height: 1.7;
  max-width: 32em;
}

.extra {
  flex-shrink: 0;
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
}

@media (max-width: 720px) {
  .row {
    flex-direction: column;
    align-items: stretch;
  }
}
</style>
