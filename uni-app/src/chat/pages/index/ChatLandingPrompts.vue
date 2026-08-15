<template>
  <view class="landing">
    <text class="landing-title">有什么可以帮你的吗？</text>

    <view class="landing-prompts">
      <view
        v-for="prompt in prompts"
        :key="prompt.title"
        class="prompt-card"
        @click="handlePromptSelect(prompt, $event)"
        @tap="handlePromptSelect(prompt, $event)"
      >
        <text class="prompt-card-title">{{ prompt.title }}</text>
        <text class="prompt-card-desc">{{ prompt.desc }}</text>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import type { ChatPrompt } from './chatPageShared'
import { createCompatActionRunner, type CompatEventLike } from '@/utils/h5EventCompat'

defineProps<{
  prompts: ChatPrompt[]
}>()

const emit = defineEmits<{
  (e: 'select', prompt: ChatPrompt): void
}>()

const runCompatAction = createCompatActionRunner()

function handlePromptSelect(prompt: ChatPrompt, event?: CompatEventLike) {
  runCompatAction(`landing-prompt:${prompt.title}`, event, () => {
    emit('select', prompt)
  })
}
</script>

<style>
/* Non-scoped: make <chat-landing-prompts> host stretch in flex chain for WeChat MP */
:host {
  display: flex;
  flex-direction: column;
  flex: 1;
  width: 100%;
  min-height: 0;
  overflow: hidden;
}
</style>

<style scoped>
.landing {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 40px 24px;
  box-sizing: border-box;
}

.landing-title {
  font-size: 24px;
  font-weight: 500;
  color: var(--app-text-primary);
  margin-bottom: 40px;
}

.landing-prompts {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 12px;
  max-width: 640px;
  width: 100%;
}

.prompt-card {
  padding: 16px 18px;
  border: 1px solid var(--app-border-color);
  border-radius: 14px;
  cursor: pointer;
  background: var(--app-surface);
  transition: background 150ms, border-color 150ms, transform 120ms;
}

.prompt-card:hover {
  background: var(--app-surface-muted);
  border-color: var(--app-fill-soft);
}

.prompt-card:active {
  transform: scale(0.98);
}

.prompt-card-title {
  display: block;
  font-size: 14px;
  font-weight: 600;
  color: var(--app-text-primary);
  margin-bottom: 4px;
}

.prompt-card-desc {
  display: block;
  font-size: 12px;
  color: var(--app-text-secondary);
  line-height: 1.4;
}

@media (max-width: 1024px) {
  .landing {
    padding: 32px 16px;
  }
}

@media (max-width: 640px) {
  .landing {
    padding: 24px 12px;
  }

  .landing-prompts {
    grid-template-columns: 1fr;
  }

  .landing-title {
    font-size: 20px;
    margin-bottom: 28px;
  }
}
</style>
