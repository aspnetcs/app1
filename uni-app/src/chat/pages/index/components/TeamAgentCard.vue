<script setup lang="ts">
/**
 * TeamAgentCard.vue
 *
 * Single Agent card in the Grok-style right panel feed.
 * Shows avatar, name, leader badge, proposal status, and collapsible detail.
 */
import { computed } from 'vue'
import type { DebateStage, TeamAgentCard, TeamAgentDetailEntry } from '@/stores/debate'
import TeamAgentDetail from './TeamAgentDetail.vue'

const props = defineProps<{
  card: TeamAgentCard
  stage: DebateStage
  expanded: boolean
}>()

const emit = defineEmits<{
  (e: 'toggle'): void
}>()

const statusLabel = computed(() => {
  if (props.card.proposalStatus === 'pending') return '思考中...'
  if (props.card.proposalStatus === 'timeout') return '超时'
  if (props.card.proposalStatus === 'failed') return '失败'
  if (props.stage === 'FAILED') return '已中断'
  if (props.stage === 'COLLECTING') return '已提交提案'
  if (props.stage === 'VOTING' || props.stage === 'EXTRACTING') return '等待辩论'
  if (props.stage === 'DEBATING') {
    return props.card.debateStatus === 'completed' ? '已发言' : '辩论中'
  }
  if (props.stage === 'SYNTHESIZING') {
    return props.card.debateStatus === 'completed' ? '已完成辩论' : '等待汇总'
  }
  return '已完成'
})

const avatarLetter = computed(() => {
  const name = props.card.displayName || props.card.modelId
  return name.charAt(0).toUpperCase()
})

const hasSummary = computed(() => Boolean(props.card.summary))
const hasDebateArgs = computed(() => props.card.debateArguments.length > 0)
const hasAnyDetail = computed(() => hasSummary.value || hasDebateArgs.value)
const detailEmptyState = computed<'pending' | 'done' | 'failed'>(() => {
  if (isFailed.value || props.stage === 'FAILED') {
    return 'failed'
  }
  if (props.card.proposalStatus === 'pending') {
    return 'pending'
  }
  if (props.stage === 'COLLECTING' || props.stage === 'VOTING' || props.stage === 'EXTRACTING') {
    return 'pending'
  }
  if (props.stage === 'DEBATING' && props.card.debateStatus === 'pending') {
    return 'pending'
  }
  if (props.stage === 'SYNTHESIZING' && !hasAnyDetail.value) {
    return 'pending'
  }
  return 'done'
})

const summaryPreview = computed(() => {
  if (!props.card.summary) return null
  const text = props.card.summary.trim()
  if (text.length <= 120) return text
  return text.slice(0, 120) + '...'
})

const isPending = computed(() => props.card.proposalStatus === 'pending')
const isFailed = computed(
  () => props.card.proposalStatus === 'failed' || props.card.proposalStatus === 'timeout'
)
const detailEntries = computed<TeamAgentDetailEntry[]>(() => {
  const entries: TeamAgentDetailEntry[] = []
  if (props.card.summary) {
    entries.push({
      id: `${props.card.modelId}-summary`,
      kind: 'summary',
      label: '提案',
      content: props.card.summary,
    })
  }
  for (const argument of props.card.debateArguments) {
    entries.push({
      id: `${props.card.modelId}-${argument.issueId}`,
      kind: 'argument',
      label: '辩论观点',
      content: argument.argument,
      stance: argument.stance,
      issueId: argument.issueId,
    })
  }
  return entries
})
</script>

<template>
  <view
    class="agent-card"
    :class="{
      'agent-card--expanded': expanded,
      'agent-card--pending': isPending,
      'agent-card--failed': isFailed,
    }"
    @click="emit('toggle')"
  >
    <!-- Card header row -->
    <view class="agent-card__header">
      <!-- Avatar -->
      <view
        class="agent-card__avatar"
        :style="{ backgroundColor: card.avatarColor }"
      >
        <text class="agent-card__avatar-letter">{{ avatarLetter }}</text>
      </view>

      <!-- Name + badges -->
      <view class="agent-card__meta">
        <view class="agent-card__name-row">
          <text class="agent-card__name">{{ card.displayName || card.modelId }}</text>
          <view v-if="card.isLeader" class="agent-card__leader-badge">
            <text class="agent-card__leader-text">队长</text>
          </view>
        </view>
        <text class="agent-card__status" :class="{ 'agent-card__status--pending': isPending }">
          {{ statusLabel }}
        </text>
      </view>

      <!-- Chevron -->
      <view class="agent-card__chevron" :class="{ 'agent-card__chevron--open': expanded }">
        <text class="agent-card__chevron-icon">v</text>
      </view>
    </view>

    <!-- Collapsed preview (always visible when not expanded, if has summary) -->
    <view v-if="!expanded && summaryPreview" class="agent-card__preview">
      <text class="agent-card__preview-text">{{ summaryPreview }}</text>
    </view>

    <!-- Expanded detail -->
    <TeamAgentDetail
      v-if="expanded"
      :entries="detailEntries"
      :empty-state="detailEmptyState"
    />
  </view>
</template>

<style scoped>
.agent-card {
  background: var(--app-surface, #ffffff);
  border: 1px solid var(--app-border-color-soft, rgba(0, 0, 0, 0.06));
  border-radius: 14px;
  padding: 12px 14px;
  cursor: pointer;
  transition: background 0.18s ease;
  box-sizing: border-box;
}

.agent-card:active,
.agent-card:hover {
  background: var(--app-surface-muted, rgba(0, 0, 0, 0.03));
}

.agent-card--expanded {
  background: var(--app-surface-muted, rgba(0, 0, 0, 0.02));
}

.agent-card__header {
  display: flex;
  align-items: center;
  gap: 10px;
}

.agent-card__avatar {
  width: 30px;
  height: 30px;
  border-radius: 50%;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
}

.agent-card__avatar-letter {
  font-size: 13px;
  font-weight: 700;
  color: #ffffff;
  line-height: 1;
}

.agent-card__meta {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.agent-card__name-row {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}

.agent-card__name {
  font-size: 13px;
  font-weight: 600;
  color: var(--app-text-primary, #1a1a1a);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.agent-card__leader-badge {
  padding: 1px 6px;
  background: var(--app-surface-muted, rgba(99, 102, 241, 0.08));
  border: 1px solid rgba(99, 102, 241, 0.2);
  border-radius: 6px;
  flex-shrink: 0;
}

.agent-card__leader-text {
  font-size: 10px;
  color: #6366f1;
  font-weight: 600;
}

.agent-card__status {
  font-size: 11px;
  color: var(--app-text-tertiary, #999999);
}

.agent-card__status--pending {
  color: var(--app-text-secondary, #666666);
}

.agent-card__chevron {
  flex-shrink: 0;
  width: 18px;
  height: 18px;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: transform 0.2s ease;
}

.agent-card__chevron--open {
  transform: rotate(180deg);
}

.agent-card__chevron-icon {
  font-size: 10px;
  color: var(--app-text-tertiary, #999999);
  font-weight: 700;
}

/* Collapsed preview */
.agent-card__preview {
  margin-top: 8px;
  padding-top: 8px;
  border-top: 1px solid var(--app-border-color-soft, rgba(0, 0, 0, 0.05));
}

.agent-card__preview-text {
  font-size: 12px;
  color: var(--app-text-secondary, #666666);
  line-height: 1.55;
  display: -webkit-box;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  overflow: hidden;
}
</style>
