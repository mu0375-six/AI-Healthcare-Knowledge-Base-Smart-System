<template>
  <svg class="mark" :class="tone" viewBox="0 0 36 36" aria-hidden="true">
    <rect width="36" height="36" rx="10" :fill="tone === 'light' ? '#f3eee4' : '#17342b'" />
    <path d="M11 24c5-10 9-10 14 0" fill="none" :stroke="tone === 'light' ? '#17342b' : '#f3eee4'" stroke-width="2.2" stroke-linecap="round" />
    <circle cx="18" cy="12.5" r="2.3" fill="#c45d3a" />
  </svg>
</template>

<script setup lang="ts">
withDefaults(defineProps<{ tone?: 'dark' | 'light' }>(), { tone: 'dark' })
</script>

<style scoped>
.mark {
  width: 36px;
  height: 36px;
  display: block;
  flex-shrink: 0;
}
</style>
