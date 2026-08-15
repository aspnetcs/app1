<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useOnboardingStore } from '@/stores/onboarding'
import { createCompatActionRunner, type CompatEventLike } from '@/utils/h5EventCompat'

type StepMeta = {
  key: string
  title: string
  description: string
  hint: string
}

const onboardingStore = useOnboardingStore()
const stepIndex = ref(0)
const loading = ref(true)
const submitting = ref(false)
const runCompatAction = createCompatActionRunner()

const state = computed(() => onboardingStore.state)
const stepKeys = computed(() => onboardingStore.steps)
const stepMetas = computed<StepMeta[]>(() =>
  stepKeys.value.map((key) => getStepMeta(key, state.value?.config.welcomeTitle, state.value?.config.welcomeMessage)),
)
const currentStep = computed(() => stepMetas.value[stepIndex.value] || stepMetas.value[0])
const isLastStep = computed(() => stepIndex.value >= stepMetas.value.length - 1)
const completedStepKeys = computed(() => {
  const keys = stepMetas.value.slice(0, stepIndex.value).map((item) => item.key)
  return Array.from(new Set([...(state.value?.completedSteps || []), ...keys]))
})
const allowSkip = computed(() => state.value?.config.allowSkip !== false)

onMounted(async () => {
  try {
    const next = await onboardingStore.fetchState(true)
    const current = next?.currentStep || onboardingStore.steps[0]
    const index = stepKeys.value.findIndex((item) => item === current)
    stepIndex.value = index >= 0 ? index : 0
  } finally {
    loading.value = false
  }
})

async function submitCurrentStep() {
  if (!currentStep.value || submitting.value) return
  submitting.value = true
  try {
    if (isLastStep.value) {
      await onboardingStore.completeOnboarding()
      uni.reLaunch({ url: '/chat/pages/index/index' })
      return
    }
    const nextIndex = Math.min(stepIndex.value + 1, stepMetas.value.length - 1)
    const nextStep = stepMetas.value[nextIndex]?.key || currentStep.value.key
    const completedSteps = Array.from(new Set([...completedStepKeys.value, currentStep.value.key]))
    await onboardingStore.updateState({
      status: 'in_progress',
      currentStep: nextStep,
      completedSteps,
    })
    stepIndex.value = nextIndex
  } catch (error) {
    uni.showToast({ title: error instanceof Error ? error.message : '更新引导状态失败', icon: 'none' })
  } finally {
    submitting.value = false
  }
}

async function skipOnboarding() {
  if (!allowSkip.value || submitting.value) return
  submitting.value = true
  try {
    await onboardingStore.skipOnboarding()
    uni.reLaunch({ url: '/chat/pages/index/index' })
  } catch (error) {
    uni.showToast({ title: error instanceof Error ? error.message : '跳过引导失败', icon: 'none' })
  } finally {
    submitting.value = false
  }
}

function handlePrimary(event?: CompatEventLike) {
  runCompatAction('onboarding-primary', event, () => {
    void submitCurrentStep()
  })
}

function handleSkip(event?: CompatEventLike) {
  runCompatAction('onboarding-skip', event, () => {
    void skipOnboarding()
  })
}

function getStepMeta(key: string, welcomeTitle?: string, welcomeMessage?: string): StepMeta {
  if (key === 'welcome') {
    return {
      key,
      title: welcomeTitle || '欢迎使用 AI App',
      description: welcomeMessage || '通过简短引导了解历史、助手市场和常用入口。',
      hint: '引导状态会保存在服务端，重新安装小程序也不会丢失。',
    }
  }
  if (key === 'history') {
    return {
      key,
      title: '历史与全局搜索',
      description: '从历史页面快速检索会话、消息和文件，并回跳到精确消息位置。',
      hint: '适合把长对话、附件和旧结论重新拉回当前上下文。',
    }
  }
  if (key === 'market') {
    return {
      key,
      title: '助手市场',
      description: '浏览系统内置的助手模板，直接带着 agentId 进入聊天。',
      hint: '推荐先从已上架的助手卡片开始，而不是手工配置复杂参数。',
    }
  }
  return {
    key,
    title: '常用设置入口',
    description: '账户页和侧边栏已经收口了常用入口，后续可以随时回看和调整。',
    hint: '完成后会直接进入聊天主页。',
  }
}
</script>

<template>
  <view class="onboarding-page">
    <view class="onboarding-shell">
      <view class="onboarding-hero">
        <text class="onboarding-eyebrow">FIRST RUN</text>
        <text class="onboarding-title">{{ currentStep?.title || '欢迎使用 AI App' }}</text>
        <text class="onboarding-description">{{ currentStep?.description || '' }}</text>
      </view>

      <view class="onboarding-progress">
        <view
          v-for="(step, index) in stepMetas"
          :key="step.key"
          class="progress-chip"
          :class="{ 'progress-chip-active': index === stepIndex, 'progress-chip-done': completedStepKeys.includes(step.key) }"
        >
          {{ index + 1 }}. {{ step.title }}
        </view>
      </view>

      <view class="onboarding-card">
        <view v-if="loading" class="onboarding-loading">
          <text>正在读取引导状态...</text>
        </view>
        <view v-else>
          <text class="onboarding-card-title">当前步骤</text>
          <text class="onboarding-card-copy">{{ currentStep?.description }}</text>
          <view class="onboarding-hint-box">
            <text class="onboarding-hint-label">提示</text>
            <text class="onboarding-hint-copy">{{ currentStep?.hint }}</text>
          </view>

          <view class="onboarding-summary-grid">
            <view class="summary-item">
              <text class="summary-label">状态</text>
              <text class="summary-value">{{ state?.status || 'not_started' }}</text>
            </view>
            <view class="summary-item">
              <text class="summary-label">已完成</text>
              <text class="summary-value">{{ completedStepKeys.length }} / {{ stepMetas.length }}</text>
            </view>
          </view>
        </view>
      </view>

      <view class="onboarding-actions">
        <button
          v-if="allowSkip"
          class="onboarding-secondary"
          :disabled="submitting"
          @click="handleSkip"
          @tap="handleSkip"
        >
          稍后再说
        </button>
        <button
          class="onboarding-primary"
          :disabled="loading || submitting"
          @click="handlePrimary"
          @tap="handlePrimary"
        >
          {{ isLastStep ? '开始使用' : '继续下一步' }}
        </button>
      </view>
    </view>
  </view>
</template>

<style scoped>
.onboarding-page {
  min-height: 100vh;
  background:
    radial-gradient(circle at top left, rgba(251, 191, 36, 0.28), transparent 32%),
    radial-gradient(circle at bottom right, rgba(14, 165, 233, 0.2), transparent 34%),
    linear-gradient(160deg, #fff9f2 0%, #f8fbff 55%, #eef6ff 100%);
  padding: 16px;
}

.onboarding-shell {
  max-width: 460px;
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding-top: 28px;
}

.onboarding-hero {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.onboarding-eyebrow {
  font-size: 11px;
  letter-spacing: 3px;
  color: #c2410c;
}

.onboarding-title {
  font-size: 28px;
  line-height: 1.1;
  color: #0f172a;
  font-weight: 700;
}

.onboarding-description {
  font-size: 14px;
  line-height: 1.7;
  color: #475569;
}

.onboarding-progress {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.progress-chip {
  padding: 7px 10px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.78);
  border: 1px solid rgba(148, 163, 184, 0.28);
  color: #64748b;
  font-size: 12px;
}

.progress-chip-active {
  color: #0f172a;
  background: #ffffff;
  border-color: rgba(14, 165, 233, 0.35);
  box-shadow: 0 5px 14px rgba(14, 165, 233, 0.1);
}

.progress-chip-done {
  color: #166534;
  border-color: rgba(34, 197, 94, 0.28);
}

.onboarding-card {
  background: rgba(255, 255, 255, 0.86);
  border-radius: 18px;
  padding: 18px;
  box-shadow: 0 12px 32px rgba(15, 23, 42, 0.08);
  border: 1px solid rgba(226, 232, 240, 0.72);
}

.onboarding-card-title {
  display: block;
  font-size: 12px;
  color: #94a3b8;
  margin-bottom: 6px;
}

.onboarding-card-copy {
  display: block;
  font-size: 15px;
  color: #0f172a;
  line-height: 1.7;
}

.onboarding-hint-box {
  margin-top: 14px;
  padding: 12px;
  border-radius: 12px;
  background: linear-gradient(135deg, rgba(255, 237, 213, 0.8), rgba(224, 242, 254, 0.8));
}

.onboarding-hint-label {
  display: block;
  font-size: 11px;
  color: #9a3412;
  margin-bottom: 5px;
}

.onboarding-hint-copy {
  display: block;
  font-size: 13px;
  line-height: 1.7;
  color: #334155;
}

.onboarding-summary-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
  margin-top: 14px;
}

.summary-item {
  padding: 12px;
  border-radius: 12px;
  background: #f8fafc;
}

.summary-label {
  display: block;
  font-size: 11px;
  color: #94a3b8;
  margin-bottom: 4px;
}

.summary-value {
  display: block;
  font-size: 15px;
  color: #0f172a;
  font-weight: 600;
}

.onboarding-actions {
  display: flex;
  gap: 10px;
}

.onboarding-primary,
.onboarding-secondary {
  flex: 1;
  height: 46px;
  line-height: 46px;
  border-radius: 999px;
  font-size: 14px;
  font-weight: 600;
  border: none;
}

.onboarding-primary {
  background: linear-gradient(135deg, #ea580c, #0284c7);
  color: #ffffff;
  box-shadow: 0 9px 20px rgba(14, 116, 144, 0.22);
}

.onboarding-secondary {
  background: rgba(255, 255, 255, 0.76);
  color: #475569;
}

.onboarding-loading {
  min-height: 140px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #64748b;
}
</style>
