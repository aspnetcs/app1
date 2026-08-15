<template>
  <svg xmlns="http://www.w3.org/2000/svg" :width="size" :height="size" viewBox="0 0 24 24" fill="none" :stroke="color" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
    <rect x="6" y="6" width="12" height="12" rx="2" ry="2" :fill="color"></rect>
  </svg>
</template>
<script setup lang="ts">
withDefaults(defineProps<{ size?: number | string; color?: string }>(), { size: 16, color: 'currentColor' })
</script>
