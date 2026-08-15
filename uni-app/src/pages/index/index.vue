<template>
  <view class="entry-page" />
</template>

<script setup lang="ts">
import { onMounted } from 'vue'
import { config } from '@/config'
import { useAuthStore } from '@/stores/auth'
import { useOnboardingStore } from '@/stores/onboarding'
import { logger } from '@/utils/logger'
import { handleEntryPageMount } from './entryFlow'

const authStore = useAuthStore()
const onboardingStore = useOnboardingStore()
let startChatWhenUnauthenticated = false

// #ifdef MP-WEIXIN
startChatWhenUnauthenticated = true
// #endif

onMounted(async () => {
  await handleEntryPageMount({
    ensureAuth: () => authStore.ensureAuth(),
    isLoggedIn: () => authStore.isLoggedIn,
    guestAuthEnabled: config.features.guestAuth,
    ensureGuestSession: () => authStore.ensureGuestSession(),
    resolveNextUrl: () => onboardingStore.resolveEntryUrl(),
    reLaunch: (url) => {
      const nextUrl = startChatWhenUnauthenticated && url === '/pages/account/account'
        ? '/chat/pages/index/index'
        : url
      uni.reLaunch({ url: nextUrl })
    },
    showToast: ({ title, icon }) => {
      uni.showToast({ title, icon })
    },
    warn: (message, error) => {
      logger.warn(message, error)
    },
  })
})
</script>

<style scoped>
.entry-page {
  min-height: 100vh;
  background: #ffffff;
}
</style>
