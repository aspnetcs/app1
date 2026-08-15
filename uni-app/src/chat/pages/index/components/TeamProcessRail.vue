<script setup lang="ts">
import TeamAgentCard from './TeamAgentCard.vue'
import TeamProcessHeader from './TeamProcessHeader.vue'
import type { DebateStage, TeamAgentCard as TeamAgentCardType, TeamProcessSummary } from '@/stores/debate'

defineProps<{
  cards: TeamAgentCardType[]
  stage: DebateStage
  processSummary: TeamProcessSummary
  expandedAgentIds: string[]
  elapsedSeconds: number | null
  mobileOpen: boolean
}>()

const emit = defineEmits<{
  (e: 'toggle-rail'): void
  (e: 'toggle-agent', modelId: string): void
}>()
</script>

<template>
  <view class="team-process-rail">
    <TeamProcessHeader
      :cards="cards"
      :process-summary="processSummary"
      :elapsed-seconds="elapsedSeconds"
      :mobile-open="mobileOpen"
      @toggle-rail="emit('toggle-rail')"
    />

    <view class="team-process-rail__feed" :class="{ 'team-process-rail__feed--hidden': !mobileOpen }">
      <text v-if="cards.length === 0" class="team-process-rail__empty">等待团队成员响应...</text>
      <TeamAgentCard
        v-for="card in cards"
        :key="card.modelId"
        :card="card"
        :stage="stage"
        :expanded="expandedAgentIds.includes(card.modelId)"
        @toggle="emit('toggle-agent', card.modelId)"
      />
    </view>
  </view>
</template>

<style scoped>
.team-process-rail {
  display: flex;
  flex-direction: column;
  height: 100%;
  overflow: hidden;
}

.team-process-rail__feed {
  flex: 1;
  overflow-y: auto;
  overflow-x: hidden;
  padding: 10px 10px 20px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.team-process-rail__feed--hidden {
  display: none;
}

.team-process-rail__empty {
  font-size: 12px;
  color: var(--app-text-tertiary, #aaaaaa);
  text-align: center;
  padding: 20px 0;
}

@media (max-width: 767px) {
  .team-process-rail__feed {
    max-height: 340px;
  }
}
</style>
