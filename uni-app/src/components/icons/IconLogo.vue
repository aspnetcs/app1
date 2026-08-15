<template>
  <svg xmlns="http://www.w3.org/2000/svg" :width="size" :height="size" viewBox="0 0 48 48">
    <defs>
      <linearGradient id="logoGrad" x1="0%" y1="0%" x2="100%" y2="100%">
        <stop offset="0%" stop-color="#6366f1" />
        <stop offset="100%" stop-color="#3b82f6" />
      </linearGradient>
    </defs>
    <circle cx="24" cy="24" r="22" fill="url(#logoGrad)" />
    <path d="M16 32 L24 14 L32 32 L28 26 L20 26 Z" fill="white" opacity="0.9" />
  </svg>
</template>
<script setup lang="ts">
withDefaults(defineProps<{ size?: number | string }>(), { size: 48 })
</script>
