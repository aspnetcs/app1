<template>
  <view class="message-blocks">
    <view v-for="block in blocks" :key="block.id" class="message-block" :class="`message-block-${block.type}`">
      <view v-if="block.title" class="message-block-title">
        <text>{{ block.title }}</text>
      </view>

      <view v-if="shouldRenderMarkdown(block)" class="message-block-markdown">
        <MarkdownRenderer :content="block.content || ''" />
      </view>

      <view
        v-if="block.type === 'citation' && block.citations?.length"
        class="citation-block-card"
        @click="emitOpen(block.citations)"
      >
        <view class="citation-block-head">
          <text class="citation-block-label">Sources</text>
          <text class="citation-block-meta">{{ block.citations.length }} items</text>
        </view>
        <view class="citation-chip-row">
          <view
            v-for="citation in block.citations.slice(0, 3)"
            :key="citation.id"
            class="citation-chip"
          >
            <text class="citation-chip-index">[{{ citation.index || 0 }}]</text>
            <text class="citation-chip-title">{{ citation.title }}</text>
          </view>
        </view>
      </view>

      <view v-if="block.type !== 'citation' && block.citations?.length" class="citation-inline-row">
        <view class="citation-inline-pill" @click="emitOpen(block.citations)">
          <text class="citation-inline-text">Sources {{ block.citations.length }}</text>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import MarkdownRenderer from '@/components/MarkdownRenderer.vue'
import type { CitationSource, MessageBlock } from '@/utils/messageBlocks'

const props = defineProps<{
  blocks: MessageBlock[]
}>()

const emit = defineEmits<{
  (e: 'open-citations', citations: CitationSource[]): void
}>()

function shouldRenderMarkdown(block: MessageBlock) {
  return block.type !== 'citation' && Boolean(block.content)
}

function emitOpen(citations?: CitationSource[]) {
  if (!citations?.length) return
  emit('open-citations', citations)
}
</script>

<style scoped>
.message-blocks {
  display: flex;
  flex-direction: column;
  gap: var(--app-space-vertical, 12px);
}

.message-block {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.message-block-translation {
  padding: var(--app-space-vertical, 12px) var(--app-space-horizontal, 12px);
  border-radius: 14px;
  border: 1px solid #dbeafe;
  background: #f8fbff;
}

.message-block-title {
  font-size: 12px;
  color: #64748b;
}

.message-block-translation .message-block-title {
  color: #2563eb;
  font-weight: 600;
}

.citation-block-card {
  border: 1px solid #dbeafe;
  background: #f8fbff;
  border-radius: 12px;
  padding: var(--app-space-vertical, 12px) var(--app-space-horizontal, 12px);
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.citation-block-head,
.citation-chip-row,
.citation-inline-row {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.citation-block-label,
.citation-inline-text,
.citation-chip-index {
  font-size: 12px;
  color: #2563eb;
}

.citation-block-meta {
  font-size: 12px;
  color: #64748b;
}

.citation-chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 10px;
  border-radius: 999px;
  background: #eff6ff;
}

.citation-chip-title {
  max-width: 220px;
  font-size: 12px;
  color: #1e3a8a;
}

.citation-inline-pill {
  padding: 6px 10px;
  border-radius: 999px;
  background: #eff6ff;
  cursor: pointer;
}
</style>

