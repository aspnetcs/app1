<script setup lang="ts">
import { computed } from 'vue'
import type { TeamAgentDetailEntry } from '@/stores/debate'

const props = withDefaults(defineProps<{
  entries: TeamAgentDetailEntry[]
  emptyState?: 'pending' | 'done' | 'failed'
}>(), {
  emptyState: 'done',
})

const emptyText = computed(() => {
  if (props.emptyState === 'pending') {
    return '详细内容返回中...'
  }
  return '当前轮未返回详细内容'
})

const isPending = computed(() => props.emptyState === 'pending')
</script>

<template>
  <view class="agent-detail">
    <view
      v-for="entry in entries"
      :key="entry.id"
      class="agent-detail__section"
    >
      <text class="agent-detail__section-label">{{ entry.label }}</text>
      <view v-if="entry.kind === 'argument'" class="agent-detail__arg-item">
        <text v-if="entry.stance" class="agent-detail__arg-stance">{{ entry.stance }}</text>
        <text class="agent-detail__arg-body">{{ entry.content }}</text>
      </view>
      <text v-else class="agent-detail__section-body">{{ entry.content }}</text>
    </view>

    <view v-if="entries.length === 0" class="agent-detail__empty">
      <text class="agent-detail__empty-text" :class="{ 'agent-detail__empty-text--pending': isPending }">
        {{ emptyText }}
      </text>
    </view>
  </view>
</template>

<style scoped>
.agent-detail {
  margin-top: 10px;
  padding-top: 10px;
  border-top: 1px solid var(--app-border-color-soft, rgba(0, 0, 0, 0.05));
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.agent-detail__section {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.agent-detail__section-label {
  font-size: 11px;
  font-weight: 600;
  color: var(--app-text-tertiary, #999999);
  text-transform: uppercase;
  letter-spacing: 0.04em;
}

.agent-detail__section-body {
  font-size: 12px;
  color: var(--app-text-primary, #1a1a1a);
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
}

.agent-detail__arg-item {
  display: flex;
  flex-direction: column;
  gap: 3px;
  padding: 8px 10px;
  background: var(--app-surface-muted, rgba(0, 0, 0, 0.03));
  border-radius: 8px;
  border-left: 2px solid var(--app-border-color, rgba(0, 0, 0, 0.12));
}

.agent-detail__arg-stance {
  font-size: 10px;
  font-weight: 600;
  color: var(--app-text-secondary, #666666);
  text-transform: uppercase;
}

.agent-detail__arg-body {
  font-size: 12px;
  color: var(--app-text-primary, #1a1a1a);
  line-height: 1.55;
  word-break: break-word;
}

.agent-detail__empty {
  padding: 10px 0;
}

.agent-detail__empty-text {
  font-size: 12px;
  color: var(--app-text-tertiary, #aaaaaa);
  font-style: italic;
}

.agent-detail__empty-text--pending {
  color: var(--app-text-secondary, #666666);
}
</style>
