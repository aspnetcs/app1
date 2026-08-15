<script setup lang="ts">
import SettingsDialog from '@/components/SettingsDialog.vue'

function closeSettingsPage() {
  uni.reLaunch({ url: '/chat/pages/index/index' })
}
</script>

<template>
  <view class="account-settings-page">
    <SettingsDialog :visible="true" page-mode @close="closeSettingsPage" />
  </view>
</template>

<style scoped>
.account-settings-page {
  min-height: 100vh;
  width: 100%;
  background: var(--app-page-bg);
  overflow: hidden;
}
</style>
