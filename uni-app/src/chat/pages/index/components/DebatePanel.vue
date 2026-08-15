<script setup lang="ts">
/**
 * DebatePanel.vue
 *
 * Grok-style dual-column team debate panel.
 *
 * Desktop (>=768px):
 *   Left: final answer + history turns
 *   Right: team process rail - overview header + per-Agent feed
 *
 * Mobile (<768px):
 *   Single column. Right rail stacks below main content as an inline section.
 */
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useAppStore } from '@/stores/app'
import { useChatStore } from '@/stores/chat'
import { getDebateStageLabel, useDebateStore } from '@/stores/debate'
import { useModelStore } from '@/stores/models'
import MarkdownRenderer from '@/components/MarkdownRenderer.vue'
import TeamProcessRail from './TeamProcessRail.vue'

const props = withDefaults(defineProps<{
  desktopRailOpen?: boolean
}>(), {
  desktopRailOpen: false,
})

const debateStore = useDebateStore()
const chatStore = useChatStore()
const modelStore = useModelStore()
const appStore = useAppStore()

const emit = defineEmits<{
  (e: 'continue', message: string): void
  (e: 'update:desktop-rail-open', value: boolean): void
}>()

// Mobile: whether the agent rail is expanded inline
const mobileRailOpen = ref(true)

// -- Helpers --

function getModelName(modelId: string | null): string {
  if (!modelId) return '---'
  const model = modelStore.models.find(m => m.id === modelId)
  return model?.name || modelId
}

function captainSourceText(source: string | null): string {
  if (!source) return ''
  const n = source.toLowerCase()
  if (n === 'auto_elected') return '(自动选举)'
  if (n === 'fixed_first_model') return '(固定首选)'
  return `(${source})`
}

// -- Computed --

const showHistory = computed(() => debateStore.turnSummaries.length > 0)
const showCurrentTurn = computed(
  () => debateStore.currentStage !== 'IDLE' || debateStore.finalAnswer !== null
)
const latestUserMessage = computed(() => {
  for (let index = chatStore.messages.length - 1; index >= 0; index -= 1) {
    const message = chatStore.messages[index]
    if (message.role === 'user' && message.content.trim()) {
      return message.content
    }
  }
  return ''
})
const isDesktopLayout = computed(() => appStore.screenWidth >= 768)
const railOpen = computed(() => (isDesktopLayout.value ? props.desktopRailOpen : mobileRailOpen.value))

const stageLabel = computed(() => getDebateStageLabel(debateStore.currentStage))

const elapsedSeconds = ref<number | null>(null)
let elapsedTimer: ReturnType<typeof setInterval> | null = null

function stopElapsedTimer() {
  if (elapsedTimer !== null) {
    clearInterval(elapsedTimer)
    elapsedTimer = null
  }
}

function syncElapsedSeconds() {
  if (!debateStore.currentTurnStartedAt || debateStore.currentStage === 'IDLE') {
    elapsedSeconds.value = null
    return
  }
  elapsedSeconds.value = Math.max(
    0,
    Math.round((Date.now() - debateStore.currentTurnStartedAt) / 1000)
  )
}

function startElapsedTimer() {
  if (elapsedTimer !== null || !debateStore.isInProgress || !debateStore.currentTurnStartedAt) {
    return
  }
  elapsedTimer = setInterval(() => {
    syncElapsedSeconds()
    if (!debateStore.isInProgress) {
      stopElapsedTimer()
    }
  }, 1000)
}

watch(
  () => [debateStore.currentStage, debateStore.currentTurnStartedAt, debateStore.isInProgress],
  () => {
    syncElapsedSeconds()
    if (debateStore.isInProgress) {
      startElapsedTimer()
      return
    }
    stopElapsedTimer()
  },
  { immediate: true }
)

onBeforeUnmount(() => {
  stopElapsedTimer()
})

const expandedAgentIds = computed(() => [...debateStore.expandedAgentIds])

function toggleRail() {
  if (isDesktopLayout.value) {
    emit('update:desktop-rail-open', !props.desktopRailOpen)
    return
  }
  mobileRailOpen.value = !mobileRailOpen.value
}

function openDesktopRail() {
  emit('update:desktop-rail-open', true)
}
</script>

<template>
  <view class="gdp">
    <!-- ==================== LEFT: main content ==================== -->
    <view class="gdp__main">
      <view class="gdp__main-inner">
        <view v-if="latestUserMessage" class="gdp__user-row">
          <view class="gdp__user-bubble">
            <text class="gdp__user-text">{{ latestUserMessage }}</text>
          </view>
        </view>

        <!-- Current Turn: Final Answer -->
        <view
          v-if="showCurrentTurn && debateStore.finalAnswer"
          class="gdp__final-answer"
        >
          <view class="gdp__final-answer-header">
            <text class="gdp__section-label">最终回答</text>
            <text class="gdp__final-captain">
              {{ getModelName(debateStore.currentCaptainModel) }}
              {{ captainSourceText(debateStore.captainSource) }} 综合
            </text>
          </view>
          <view class="gdp__final-answer-body">
            <MarkdownRenderer :content="debateStore.finalAnswer" />
          </view>
        </view>

        <!-- In-progress placeholder (no final answer yet) -->
        <view
          v-if="showCurrentTurn && !debateStore.finalAnswer && debateStore.isInProgress"
          class="gdp__in-progress"
        >
          <text class="gdp__in-progress-label">{{ stageLabel }}...</text>
        </view>

        <!-- Error -->
        <view v-if="debateStore.errorMessage" class="gdp__error">
          <text class="gdp__error-text">{{ debateStore.errorMessage }}</text>
        </view>
      </view>
    </view>

    <!-- ==================== RIGHT: team process rail ==================== -->
    <TeamProcessRail
      class="gdp__rail"
      :class="{ 'gdp__rail--desktop-hidden': isDesktopLayout && !props.desktopRailOpen }"
      :cards="debateStore.agentCards"
      :stage="debateStore.currentStage"
      :process-summary="debateStore.processSummary"
      :expanded-agent-ids="expandedAgentIds"
      :elapsed-seconds="elapsedSeconds"
      :mobile-open="railOpen"
      @toggle-agent="debateStore.toggleAgentExpanded"
      @toggle-rail="toggleRail"
    />

    <view
      v-if="isDesktopLayout && !props.desktopRailOpen"
      class="gdp__rail-reopen"
      @click="openDesktopRail"
    >
      <text class="gdp__rail-reopen-text">展开团队栏</text>
    </view>
  </view>
</template>

<style scoped>
/* ========== Layout ========== */
.gdp {
  position: relative;
  display: flex;
  flex-direction: row;
  width: 100%;
  height: 100%;
  overflow: hidden;
  box-sizing: border-box;
}

/* Left main */
.gdp__main {
  flex: 1;
  min-width: 0;
  overflow-y: auto;
  overflow-x: hidden;
  padding: 20px 0 160px;
  box-sizing: border-box;
}

.gdp__main-inner {
  display: flex;
  flex-direction: column;
  gap: 20px;
  width: 100%;
  max-width: var(--chat-content-max-width, 768px);
  margin: 0 auto;
  padding: 0 var(--chat-content-padding-x, 20px);
  box-sizing: border-box;
}

/* Right rail */
.gdp__rail {
  width: var(--team-rail-desktop-width, 320px);
  min-width: var(--team-rail-desktop-width, 320px);
  max-width: var(--team-rail-desktop-width, 320px);
  flex-shrink: 0;
  border-left: 1px solid var(--app-border-color-soft, rgba(0, 0, 0, 0.07));
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background: var(--app-surface-muted, rgba(0, 0, 0, 0.02));
  transition:
    width 0.28s ease,
    min-width 0.28s ease,
    max-width 0.28s ease,
    opacity 0.2s ease,
    transform 0.28s ease,
    border-color 0.2s ease;
}

.gdp__rail--desktop-hidden {
  width: 0;
  min-width: 0;
  max-width: 0;
  opacity: 0;
  transform: translateX(16px);
  pointer-events: none;
  border-left-color: transparent;
}

.gdp__rail-reopen {
  position: absolute;
  top: 18px;
  right: 14px;
  height: 34px;
  padding: 0 14px;
  border-radius: 999px;
  border: 1px solid var(--app-border-color, rgba(0, 0, 0, 0.1));
  background: var(--app-surface-raised, #ffffff);
  box-shadow: var(--app-shadow-elevated);
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  z-index: 3;
}

.gdp__rail-reopen-text {
  font-size: 12px;
  font-weight: 600;
  color: var(--app-text-secondary, #666666);
}

/* ========== Left main: common section label ========== */
.gdp__section-label {
  font-size: 12px;
  font-weight: 600;
  color: var(--app-text-tertiary, #999999);
  text-transform: uppercase;
  letter-spacing: 0.04em;
  margin-bottom: 10px;
  display: block;
}

/* ========== User message ========== */
.gdp__user-row {
  display: flex;
  justify-content: flex-end;
}

.gdp__user-bubble {
  max-width: min(85%, 680px);
  padding: 12px 18px;
  background: var(--app-surface-muted);
  border-radius: 20px 20px 6px 20px;
  box-sizing: border-box;
}

.gdp__user-text {
  font-size: 14px;
  line-height: 1.6;
  color: var(--app-text-primary, #1a1a1a);
  white-space: pre-wrap;
  word-break: break-word;
}

/* ========== Final answer ========== */
.gdp__final-answer {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.gdp__final-answer-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 6px;
}

.gdp__final-captain {
  font-size: 11px;
  color: var(--app-text-tertiary, #999999);
}

.gdp__final-answer-body {
  font-size: 14px;
  line-height: 1.7;
  color: var(--app-text-primary, #1a1a1a);
}

/* ========== In progress ========== */
.gdp__in-progress {
  padding: 20px 0;
}

.gdp__in-progress-label {
  font-size: 14px;
  color: var(--app-text-secondary, #666666);
}

/* ========== Error ========== */
.gdp__error {
  padding: 12px 16px;
  background: var(--app-error-surface, rgba(239, 68, 68, 0.06));
  border-radius: 10px;
  border-left: 3px solid #ef4444;
}

.gdp__error-text {
  font-size: 13px;
  color: #ef4444;
  line-height: 1.5;
}

/* ========== Mobile breakpoint ========== */
@media (max-width: 767px) {
  .gdp {
    flex-direction: column;
  }

  .gdp__main {
    padding: 16px 0 20px;
  }

  .gdp__main-inner {
    max-width: none;
  }

  .gdp__rail {
    width: 100%;
    min-width: 0;
    max-width: none;
    border-left: none;
    border-top: 1px solid var(--app-border-color-soft, rgba(0, 0, 0, 0.07));
    max-height: none;
    padding-bottom: 140px;
  }

  .gdp__rail--desktop-hidden {
    width: 100%;
    min-width: 0;
    max-width: none;
    opacity: 1;
    transform: none;
    pointer-events: auto;
    border-left-color: transparent;
  }

}
</style>
