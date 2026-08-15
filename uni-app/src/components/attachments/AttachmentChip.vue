<template>
  <view class="attachment-chip" @click="handlePreview" @tap="handlePreview">
    <view class="attachment-main">
      <text class="attachment-name">{{ item.originalName || 'file' }}</text>
      <view class="attachment-meta">
        <text v-if="kindLabel" class="attachment-kind">{{ kindLabel }}</text>
        <text v-if="statusLabel" class="attachment-status">{{ statusLabel }}</text>
      </view>
    </view>

    <view v-if="showRemove" class="attachment-remove" @click.stop="handleRemove" @tap.stop="handleRemove">
      <text class="attachment-remove-text">x</text>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { FileKind } from '@/api/types/files'
import { previewRemoteFile } from '@/utils/attachments/preview'
import { inferFileExtension } from '@/utils/attachments/fileKind'

export type AttachmentChipItem = {
  fileId: string
  originalName: string
  kind?: FileKind
  mimeType?: string
  url?: string
  recognitionStatus?: string
  recognitionType?: string
  recognitionText?: string
  recognitionMessage?: string
}

const props = defineProps<{
  item: AttachmentChipItem
  showRemove?: boolean
}>()

const emit = defineEmits<{
  (e: 'preview', item: AttachmentChipItem): void
  (e: 'remove', fileId: string): void
}>()

const showRemove = props.showRemove === true
const kindLabel = computed(() => resolveKindLabel(props.item))
const statusLabel = computed(() => resolveStatusLabel(props.item.recognitionStatus))

async function handlePreview() {
  const url = (props.item.url || '').trim()
  if (url) {
    await previewRemoteFile({
      url,
      kind: props.item.kind,
      mimeType: props.item.mimeType,
      filename: props.item.originalName,
    })
    return
  }
  emit('preview', props.item)
}

function handleRemove() {
  emit('remove', props.item.fileId)
}

function resolveKindLabel(item: AttachmentChipItem) {
  const ext = inferFileExtension(item.originalName)
  if (item.kind === 'image') return '图片'
  if (['xls', 'xlsx', 'csv'].includes(ext)) return '表格'
  if (['ppt', 'pptx'].includes(ext)) return '演示'
  if (['doc', 'docx'].includes(ext)) return '文档'
  if (['pdf'].includes(ext)) return 'PDF'
  if (['md', 'markdown'].includes(ext)) return 'Markdown'
  if (['json', 'xml', 'html', 'htm', 'txt', 'rtf'].includes(ext)) return ext.toUpperCase()
  if (item.kind === 'document') return '文档'
  return item.kind || ''
}

function resolveStatusLabel(status?: string) {
  if (status === 'extracted') return '已识别'
  if (status === 'vision_ready') return '视觉可读'
  if (status === 'extract_failed') return '识别失败'
  if (status === 'unsupported' || status === 'unsupported_mime') return '暂不支持'
  return ''
}
</script>

<style scoped>
.attachment-chip {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 10px;
  border: 1px solid var(--app-border-color);
  border-radius: 8px;
  background: var(--app-surface-raised);
  color: var(--app-text-primary);
  min-height: 34px;
  box-sizing: border-box;
}

.attachment-main {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}

.attachment-name {
  font-size: 12px;
  color: var(--app-text-primary);
  max-width: 220px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  line-height: 1;
}

.attachment-meta {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
}

.attachment-kind {
  font-size: 11px;
  color: var(--app-text-secondary);
  line-height: 1;
}

.attachment-status {
  font-size: 11px;
  color: var(--app-accent);
  line-height: 1;
}

.attachment-remove {
  width: 18px;
  height: 18px;
  border-radius: 9px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--app-fill-soft);
  flex-shrink: 0;
}

.attachment-remove-text {
  font-size: 12px;
  color: var(--app-text-secondary);
  line-height: 1;
}
</style>

