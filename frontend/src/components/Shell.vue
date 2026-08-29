<template>
  <section class="shell">
    <div class="core" :class="{ 'core-pad': pad !== false }">
      <div v-if="$slots.head" class="section-head">
        <slot name="head" />
      </div>
      <slot />
    </div>
  </section>
</template>

<script setup lang="ts">
withDefaults(defineProps<{ pad?: boolean }>(), {
  pad: true,
})
</script>
