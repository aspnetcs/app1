<template>
  <view v-if="items.length" class="attachment-list">
    <AttachmentChip
      v-for="item in items"
      :key="item.fileId"
      :item="item"
      :show-remove="showRemove"
      @preview="(payload) => emit('preview', payload)"
      @remove="(fileId) => emit('remove', fileId)"
    />
  </view>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import AttachmentChip, { type AttachmentChipItem } from './AttachmentChip.vue'

const props = defineProps<{
  items: AttachmentChipItem[]
  showRemove?: boolean
}>()

const emit = defineEmits<{
  (e: 'preview', item: AttachmentChipItem): void
  (e: 'remove', fileId: string): void
}>()

const items = computed(() => props.items || [])
const showRemove = computed(() => props.showRemove === true)
</script>

<style scoped>
.attachment-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
</style>
