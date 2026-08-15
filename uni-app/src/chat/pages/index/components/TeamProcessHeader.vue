<script setup lang="ts">
import { computed } from 'vue'
import type { TeamAgentCard, TeamProcessSummary } from '@/stores/debate'

const props = defineProps<{
  cards: TeamAgentCard[]
  processSummary: TeamProcessSummary
  elapsedSeconds: number | null
  mobileOpen: boolean
}>()

const emit = defineEmits<{
  (e: 'toggle-rail'): void
}>()

const stackedAvatars = computed(() =>
  props.cards.slice(0, 5).map((card) => ({
    key: card.modelId,
    color: card.avatarColor,
    letter: (card.displayName || card.modelId).charAt(0).toUpperCase(),
  }))
)

const extraAgentCount = computed(() => Math.max(0, props.cards.length - 5))
</script>

<template>
  <view class="team-process-header">
    <view class="team-process-header__avatars">
      <view
        v-for="(avatar, index) in stackedAvatars"
        :key="avatar.key"
        class="team-process-header__avatar"
        :style="{ backgroundColor: avatar.color, zIndex: stackedAvatars.length - index }"
      >
        <text class="team-process-header__avatar-letter">{{ avatar.letter }}</text>
      </view>
      <view v-if="extraAgentCount > 0" class="team-process-header__avatar team-process-header__avatar--extra">
        <text class="team-process-header__avatar-letter">+{{ extraAgentCount }}</text>
      </view>
    </view>

    <view class="team-process-header__main">
      <text class="team-process-header__title">{{ processSummary.title }}</text>
      <view class="team-process-header__meta">
        <text class="team-process-header__count">{{ processSummary.agentCount }} 个成员</text>
        <text class="team-process-header__dot">·</text>
        <text class="team-process-header__stage">{{ processSummary.stageLabel }}</text>
        <text v-if="elapsedSeconds !== null" class="team-process-header__elapsed">· {{ elapsedSeconds }} 秒</text>
      </view>
    </view>

    <view class="team-process-header__toggle" @click="emit('toggle-rail')">
      <text
        class="team-process-header__toggle-icon"
        :class="{ 'team-process-header__toggle-icon--open': mobileOpen }"
      >&lt;</text>
    </view>
  </view>
</template>

<style scoped>
.team-process-header {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 14px 14px 10px;
  border-bottom: 1px solid var(--app-border-color-soft, rgba(0, 0, 0, 0.06));
  flex-shrink: 0;
}

.team-process-header__avatars {
  display: flex;
  flex-direction: row;
}

.team-process-header__avatar {
  width: 26px;
  height: 26px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 2px solid var(--app-surface-muted, #f5f5f5);
  margin-left: -8px;
  flex-shrink: 0;
  position: relative;
}

.team-process-header__avatar:first-child {
  margin-left: 0;
}

.team-process-header__avatar--extra {
  background: var(--app-surface, #ffffff);
  border-color: var(--app-border-color-soft, rgba(0, 0, 0, 0.1));
}

.team-process-header__avatar-letter {
  font-size: 10px;
  font-weight: 700;
  color: #ffffff;
  line-height: 1;
}

.team-process-header__avatar--extra .team-process-header__avatar-letter {
  color: var(--app-text-secondary, #666666);
  font-size: 9px;
}

.team-process-header__main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 1px;
}

.team-process-header__title {
  font-size: 13px;
  font-weight: 600;
  color: var(--app-text-primary, #1a1a1a);
}

.team-process-header__meta {
  display: flex;
  align-items: center;
  gap: 4px;
  flex-wrap: wrap;
}

.team-process-header__count,
.team-process-header__stage,
.team-process-header__elapsed,
.team-process-header__dot {
  font-size: 11px;
  color: var(--app-text-tertiary, #999999);
}

.team-process-header__toggle {
  display: flex;
  width: 24px;
  height: 24px;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  border-radius: 999px;
  background: var(--app-surface, #ffffff);
}

.team-process-header__toggle-icon {
  font-size: 10px;
  font-weight: 700;
  color: var(--app-text-tertiary, #999999);
  transition: transform 0.2s ease;
  display: block;
}

.team-process-header__toggle-icon--open {
  transform: rotate(180deg);
}
</style>
