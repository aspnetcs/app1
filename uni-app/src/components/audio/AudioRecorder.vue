<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import IconMic from '@/components/icons/IconMic.vue'
import { voiceChatPipeline } from '@/api/voicechat'
import { logger } from '@/utils/logger'
import {
  getRecordingCapability,
  getRecordingUnsupportedMessage,
} from '@/utils/media-permissions'

const emit = defineEmits<{
  (e: 'transcription', text: string): void
  (e: 'error', error: string): void
}>()

const isRecording = ref(false)
const isProcessing = ref(false)

type RecorderStopPayload = {
  tempFilePath?: string
  duration?: number
  fileSize?: number
}

type RecorderErrorPayload = {
  errMsg?: string
}

type RecorderManagerLike = {
  onStart?: (callback: () => void) => void
  onStop: (callback: (payload: RecorderStopPayload) => void) => void
  onError: (callback: (payload: RecorderErrorPayload) => void) => void
  start: (options: { duration: number; format: 'mp3' }) => void
  stop: () => void
}

const recordingCapability = computed(() => getRecordingCapability())
const unsupportedMessage = computed(() => getRecordingUnsupportedMessage(recordingCapability.value))

let recorderManager: RecorderManagerLike | null = null

onMounted(() => {
  if (!recordingCapability.value.supported) {
    return
  }

  try {
    recorderManager = uni.getRecorderManager
      ? (uni.getRecorderManager() as unknown as RecorderManagerLike)
      : null
  } catch (error) {
    logger.warn('Recorder not supported', error)
  }

  if (!recorderManager) return

  if (recorderManager.onStart) {
    recorderManager.onStart(() => {
      isRecording.value = true
    })
  }

  recorderManager.onStop(async (result: RecorderStopPayload) => {
    isRecording.value = false
    if (!result.tempFilePath) {
      emit('error', '录音文件获取失败')
      return
    }

    isProcessing.value = true
    try {
      const response = await voiceChatPipeline(result.tempFilePath)
      if (response.transcript) {
        emit('transcription', response.transcript)
      } else {
        emit('error', '未识别到语音内容')
      }
    } catch {
      emit('error', '语音识别失败，请重试')
    } finally {
      isProcessing.value = false
    }
  })

  recorderManager.onError((error: RecorderErrorPayload) => {
    isRecording.value = false
    emit('error', error.errMsg || '录音失败，请检查麦克风权限')
  })
})

function startRecord() {
  if (!recordingCapability.value.supported) {
    emit('error', unsupportedMessage.value)
    return
  }

  if (!recorderManager || isRecording.value || isProcessing.value) return

  uni.vibrateShort({})
  recorderManager.start({
    duration: 60000,
    format: 'mp3',
  })
}

function stopRecord() {
  if (!recorderManager || !isRecording.value) return
  recorderManager.stop()
}

onUnmounted(() => {
  if (isRecording.value && recorderManager) {
    recorderManager.stop()
  }
})
</script>

<template>
  <view
    class="toolbar-btn"
    :class="{
      'toolbar-btn-recording': isRecording,
      'toolbar-btn-processing': isProcessing,
      'toolbar-btn-disabled': !recordingCapability.supported,
    }"
    :title="recordingCapability.supported ? (isProcessing ? '识别中...' : '按住说话') : unsupportedMessage"
    @touchstart.prevent="startRecord"
    @touchend.prevent="stopRecord"
    @touchcancel.prevent="stopRecord"
  >
    <IconMic
      :size="18"
      :color="!recordingCapability.supported ? '#cbd5e1' : isRecording ? '#ef4444' : isProcessing ? '#f59e0b' : '#9ca3af'"
    />
  </view>
</template>

<style scoped>
.toolbar-btn {
  width: 32px;
  height: 32px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: background 120ms, transform 150ms;
}

.toolbar-btn:hover {
  background: #e5e7eb;
}

.toolbar-btn-recording {
  background: #fef2f2;
  transform: scale(1.1);
  box-shadow: 0 0 0 2px rgba(239, 68, 68, 0.2);
}

.toolbar-btn-processing {
  background: #fffbeb;
  opacity: 0.8;
  pointer-events: none;
}

.toolbar-btn-disabled {
  opacity: 0.45;
}
</style>
