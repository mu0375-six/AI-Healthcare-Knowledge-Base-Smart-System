<template>
  <svg class="mark" viewBox="0 0 36 36" aria-hidden="true">
    <!-- 圆角方章：像盖在纸上的一枚印，与 Editorial Luxury 的纸质语境一致。
         不用渐变 —— 蓝紫渐变正是要避开的 AI 指纹。 -->
    <rect width="36" height="36" rx="10" fill="var(--accent, #40614b)" />
    <rect
      x="0.5"
      y="0.5"
      width="35"
      height="35"
      rx="9.5"
      fill="none"
      stroke="rgba(255,255,255,0.16)"
    />
    <!-- 心电波：本产品世界里最具辨识度的图形 -->
    <path
      d="M8.5 21.5h3.6l2.1-5.6 3.3 10.2 2.4-4.6H27.5"
      fill="none"
      stroke="var(--on-accent, #fffdf9)"
      stroke-width="1.9"
      stroke-linecap="round"
      stroke-linejoin="round"
    />
    <circle cx="18" cy="10.5" r="1.9" fill="rgba(255,255,255,0.55)" />
  </svg>
</template>

<script setup lang="ts">
defineProps<{ tone?: 'dark' | 'light' }>()
</script>

<style scoped>
.mark {
  width: 32px;
  height: 32px;
  display: block;
  flex-shrink: 0;
}
</style>
