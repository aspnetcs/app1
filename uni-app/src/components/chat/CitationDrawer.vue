<template>
  <view v-if="visible" class="citation-drawer-mask" @click="emit('close')">
    <view class="citation-drawer" @click.stop>
      <view class="citation-drawer-header">
        <view>
          <text class="citation-drawer-title">Sources</text>
          <text class="citation-drawer-subtitle">{{ citations.length }} items</text>
        </view>
        <view class="citation-drawer-close" @click="emit('close')">
          <text>x</text>
        </view>
      </view>
      <scroll-view scroll-y class="citation-drawer-body">
        <view v-for="citation in citations" :key="citation.id" class="citation-source-card">
          <view class="citation-source-head">
            <text class="citation-source-index">[{{ citation.index || 0 }}]</text>
            <text class="citation-source-title">{{ citation.title }}</text>
          </view>
          <text v-if="citation.sourceType" class="citation-source-type">{{ citation.sourceType }}</text>
          <text v-if="citation.snippet" class="citation-source-snippet">{{ citation.snippet }}</text>
          <text v-if="citation.url" class="citation-source-url" selectable>{{ citation.url }}</text>
        </view>
      </scroll-view>
    </view>
  </view>
</template>

<script setup lang="ts">
import type { CitationSource } from '@/utils/messageBlocks'

defineProps<{
  visible: boolean
  citations: CitationSource[]
}>()

const emit = defineEmits<{
  (e: 'close'): void
}>()
</script>

<style scoped>
.citation-drawer-mask {
  position: fixed;
  inset: 0;
  z-index: 60;
  background: rgba(15, 23, 42, 0.32);
  display: flex;
  align-items: flex-end;
  justify-content: center;
}

.citation-drawer {
  width: 100%;
  max-width: 768px;
  max-height: 72vh;
  background: #ffffff;
  border-radius: 20px 20px 0 0;
  padding: 16px 16px calc(16px + env(safe-area-inset-bottom));
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.citation-drawer-header,
.citation-source-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;
}

.citation-drawer-title {
  display: block;
  font-size: 16px;
  font-weight: 600;
  color: #0f172a;
}

.citation-drawer-subtitle,
.citation-source-type {
  display: block;
  margin-top: 4px;
  font-size: 12px;
  color: #64748b;
}

.citation-drawer-close {
  width: 28px;
  height: 28px;
  border-radius: 14px;
  background: #f1f5f9;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.citation-drawer-body {
  min-height: 0;
  max-height: 60vh;
}

.citation-source-card {
  padding: 12px;
  border-radius: 14px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  margin-bottom: 10px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.citation-source-index {
  font-size: 12px;
  color: #2563eb;
}

.citation-source-title {
  flex: 1;
  font-size: 14px;
  color: #0f172a;
}

.citation-source-snippet,
.citation-source-url {
  font-size: 12px;
  color: #475569;
  line-height: 1.6;
  white-space: pre-wrap;
}

.citation-source-url {
  color: #2563eb;
}
</style>

