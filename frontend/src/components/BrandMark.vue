<template>
  <svg class="mark" viewBox="0 0 36 36" aria-hidden="true" focusable="false">
    <rect width="36" height="36" rx="7" fill="var(--accent)" />
    <rect
      x="0.5"
      y="0.5"
      width="35"
      height="35"
      rx="6.5"
      fill="none"
      stroke="var(--on-accent)"
      stroke-opacity="0.16"
    />
    <!-- 心电波：本产品世界里最具辨识度的图形 -->
    <path
      d="M8.5 21.5h3.6l2.1-5.6 3.3 10.2 2.4-4.6H27.5"
      fill="none"
      stroke="var(--on-accent)"
      stroke-width="1.9"
      stroke-linecap="round"
      stroke-linejoin="round"
    />
    <circle cx="18" cy="10.5" r="1.9" fill="var(--on-accent)" fill-opacity="0.55" />
  </svg>
</template>

<script setup lang="ts">
defineProps<{ tone?: 'dark' | 'light' }>()
</script>

<style scoped>
.mark {
  width: 30px;
  height: 30px;
  display: block;
  flex-shrink: 0;
}
</style>
