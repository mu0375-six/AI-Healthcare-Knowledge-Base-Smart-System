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
  position: relative;
  margin-bottom: 20px;
  padding: 2px 0 18px 14px;
  border-bottom: 1px solid var(--edge);
}

.head::before {
  content: '';
  position: absolute;
  left: 0;
  top: 5px;
  bottom: 18px;
  width: 3px;
  border-radius: var(--r-pill);
  background: var(--accent);
}

.back {
  margin-bottom: var(--space-3);
}

.row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-4);
}

.lead {
  min-width: 0;
}

.lead h1 {
  margin-top: 6px;
  font-size: clamp(27px, 3vw, 36px);
}

.desc {
  margin-top: var(--space-2);
  color: var(--ink-mute);
  font-size: 14px;
  line-height: 1.55;
  max-width: 46em;
}

.extra {
  flex-shrink: 0;
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
}

@media (max-width: 720px) {
  .head {
    padding-left: 11px;
  }

  .row {
    flex-direction: column;
    align-items: stretch;
  }
}
</style>
