<script setup lang="ts">
import { computed, getCurrentInstance, onUnmounted } from 'vue'
import IconSpeaker from '@/components/icons/IconSpeaker.vue'
import IconStop from '@/components/icons/IconStop.vue'
import { createCompatActionRunner, type CompatEventLike } from '@/utils/h5EventCompat'
import { useTts } from '@/composables/useTts'

const props = defineProps<{
  text: string
}>()

const instance = getCurrentInstance()
const playerId = `audio-player:${instance?.uid ?? 'unknown'}`
const runCompatAction = createCompatActionRunner()
const { playingId, loadingId, playMessage, stop } = useTts()

const isPlaying = computed(() => playingId.value === playerId)
const isLoading = computed(() => loadingId.value === playerId)

async function playTts() {
  if (!props.text.trim()) return

  try {
    await playMessage({
      id: playerId,
      text: props.text.slice(0, 1000),
      model: 'tts-1',
    })
  } catch {
    // Toast feedback is handled inside the TTS composable.
  }
}

function handleActivate(event?: CompatEventLike) {
  runCompatAction(`audio-player:${playerId}`, event, () => {
    void playTts()
  })
}

onUnmounted(() => {
  if (playingId.value === playerId) {
    stop()
  }
})
</script>

<template>
  <view
    class="audio-btn"
    :class="{ 'audio-btn-playing': isPlaying, 'audio-btn-loading': isLoading }"
    :title="isPlaying ? '停止朗读' : '朗读消息'"
    @click="handleActivate"
    @tap="handleActivate"
  >
    <IconStop v-if="isPlaying" :size="14" color="#3b82f6" />
    <IconSpeaker v-else :size="14" :color="isLoading ? '#9ca3af' : '#9ca3af'" />
  </view>
</template>

<style scoped>
.audio-btn {
  width: 28px;
  height: 28px;
  border-radius: 6px;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: background 120ms;
}

.audio-btn:hover {
  background: #f3f4f6;
}

.audio-btn-playing {
  background: #eff6ff;
}

.audio-btn-loading {
  opacity: 0.5;
}
</style>
