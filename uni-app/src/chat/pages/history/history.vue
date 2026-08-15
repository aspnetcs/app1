<template>
  <AppLayout>
    <template #topbar-header>
      <view class="history-topbar-header">
        <view class="history-topbar-search">
          <view class="search-box history-search-box">
            <view class="search-icon">
              <!-- #ifdef MP-WEIXIN -->
              <MpShapeIcon name="search" :size="18" color="var(--app-text-secondary)" />
              <!-- #endif -->
              <!-- #ifndef MP-WEIXIN -->
              <IconSearch :size="18" color="var(--app-text-secondary)" />
              <!-- #endif -->
            </view>
            <input
              v-model="keyword"
              class="search-input"
              placeholder="搜索消息"
              confirm-type="search"
              @confirm="submitSearch"
            />
            <view class="search-btn" @click="submitSearch" @tap="submitSearch">搜索</view>
          </view>
        </view>
      </view>
    </template>

    <view class="page">
      <view class="shell">

        <view class="grid">
          <view class="panel list-panel">
            <view class="panel-head">
              <view>
                <view class="panel-title">{{ sectionTitle }}</view>
                <view class="panel-note">{{ sectionNote }}</view>
              </view>
              <view class="panel-tip">点击左侧列表可刷新右侧预览，进入会话请点右下按钮。</view>
            </view>
            <view v-if="searchError" class="state error">{{ searchError }}</view>
            <view v-else-if="searching" class="state">正在搜索...</view>
            <view v-else-if="showSearch && items.length === 0" class="state">没有找到匹配结果。</view>
            <view v-else-if="!showSearch && conversations.length === 0" class="state">暂时还没有可预览的历史会话。</view>
            <scroll-view v-else class="list-scroll" scroll-y :show-scrollbar="false">
              <view class="list">
                <view
                  v-for="item in items"
                  :key="itemKey(item)"
                  class="row"
                  :class="{ active: isSelected(item) }"
                  @click="selectFromUi(item, $event)"
                  @tap="selectFromUi(item, $event)"
                >
                  <view class="row-main">
                    <view class="row-title">{{ itemTitle(item) }}</view>
                    <view class="row-snippet">{{ itemSnippet(item) }}</view>
                  </view>
                  <view class="row-side">
                    <view class="row-meta">{{ itemMeta(item) }}</view>
                    <view class="row-tag">{{ isSelected(item) ? '已选中' : '点击预览' }}</view>
                  </view>
                </view>
              </view>
            </scroll-view>
          </view>

          <view class="side">
            <view class="panel">
              <view class="panel-head compact">
                <view class="panel-title small">对话预览</view>
                <view class="panel-note">{{ previewNote }}</view>
              </view>
              <view v-if="!previewConversation" class="state small">从左侧列表中选中一个会话后，这里会显示预览。</view>
              <view v-else class="preview">
                <view class="summary-title">{{ previewConversation?.title || '' }}</view>
                <view class="chips">
                  <view class="chip">{{ formatMeta(previewConversation?.updatedAt || 0, previewConversation?.messageCount) }}</view>
                  <view v-if="previewConversation?.model" class="chip mute">{{ previewConversation.model }}</view>
                  <view v-if="previewAnchorId" class="chip accent">命中位置</view>
                </view>
                <view v-if="previewError" class="state error small">{{ previewError }}</view>
                <view v-else-if="previewLoading" class="state small">正在加载预览...</view>
                <view v-else-if="previewItems.length === 0" class="state small">这个会话暂时没有可展示的消息。</view>
                <scroll-view v-else class="messages" scroll-y :show-scrollbar="false">
                  <view
                    v-for="message in previewItems"
                    :key="message.serverId || message.id"
                    class="message"
                    :class="{ user: message.role === 'user', anchor: isAnchor(message) }"
                  >
                    <view class="message-head">
                      <view class="message-role">{{ roleText(message.role) }}</view>
                      <view class="message-time">{{ formatPreviewTime(message.createdAt) }}</view>
                    </view>
                    <view class="message-content">{{ message.content || '暂无可展示的消息内容。' }}</view>
                  </view>
                </scroll-view>
                <view class="actions">
                  <view class="pill secondary grow" @click="handleOpenPreview" @tap="handleOpenPreview">进入对话</view>
                  <view class="pill danger grow" @click="handleArchivePreview" @tap="handleArchivePreview">归档对话</view>
                </view>
              </view>
            </view>
          </view>
        </view>
      </view>
    </view>
  </AppLayout>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch, onMounted } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import AppLayout from '@/layouts/AppLayout.vue'
// #ifndef MP-WEIXIN
import IconSearch from '@/components/icons/IconSearch.vue'
// #endif
// #ifdef MP-WEIXIN
import MpShapeIcon from '@/components/icons/MpShapeIcon.vue'
// #endif
import { useChatStore } from '@/stores/chat'
import { writeActiveChatSession } from '@/stores/chatSessionStorage'
import type { Conversation, Message } from '@/stores/chatState'
import { buildCompareRoundsFromMessages } from '../index/compareRoundsState'
import {
  buildHistoryConversationSession,
  loadHistoryPageData,
  openHistorySearchHit,
} from './historyPageState'
import { createCompatActionRunner } from '@/utils/h5EventCompat'
import {
  fetchConversationMessagesData,
  searchConversationMessageHistoryData,
} from '@/stores/chatPersistence'
import type { HistorySearchFileItem, HistorySearchMessageItem, HistorySearchTopicItem } from '@/api/history'

type SearchItem = HistorySearchTopicItem | HistorySearchMessageItem | HistorySearchFileItem
type DisplayItem = SearchItem | Conversation
const chatStore = useChatStore()
const runCompatAction = createCompatActionRunner()
const keyword = ref('')
const searching = ref(false)
const searchError = ref('')
const result = reactive<{ items: SearchItem[]; total: number }>({ items: [], total: 0 })
const selectedKey = ref('')
const selectedItem = ref<DisplayItem | null>(null)
const previewMessages = ref<Message[]>([])
const previewLoading = ref(false)
const previewError = ref('')
const previewAnchorId = ref<string | null>(null)
let previewToken = 0

const conversations = computed(() => chatStore.history)
const showSearch = computed(() => keyword.value.trim().length > 0)
const items = computed<DisplayItem[]>(() => (showSearch.value ? result.items : conversations.value))
const previewConversation = computed(() => (selectedItem.value ? previewConversationFrom(selectedItem.value) : null))
const previewItems = computed(() => previewMessages.value.filter(item => item.role !== 'system' || item.content.trim()).slice(-16))
const sectionTitle = computed(() => (showSearch.value ? '搜索结果' : '最近对话'))
const sectionNote = computed(() => (showSearch.value ? `共 ${result.total} 条结果` : `${conversations.value.length} 个最近会话`))
const previewNote = computed(() => {
  if (!previewConversation.value) return showSearch.value ? '搜索结果预览' : '最近对话预览'
  if (selectedItem.value && !isConversation(selectedItem.value)) return `搜索命中 · ${itemMeta(selectedItem.value)}`
  return formatMeta(previewConversation.value.updatedAt, previewConversation.value.messageCount)
})

onMounted(loadHistory)
onShow(loadHistory)
watch(items, next => {
  void syncSelection(next)
}, { immediate: true })

function loadHistory() {
  loadHistoryPageData(chatStore)
}

function isTopic(item: SearchItem): item is HistorySearchTopicItem {
  return 'title' in item && !('messageId' in item)
}

function isMessageLike(item: SearchItem): item is HistorySearchMessageItem | HistorySearchFileItem {
  return 'messageId' in item
}

function isConversation(item: DisplayItem): item is Conversation {
  return 'updatedAt' in item && 'id' in item && !('conversationId' in item)
}

function conversationId(item: DisplayItem) {
  return isConversation(item) ? item.id : String(item.conversationId || '')
}

function itemKey(item: DisplayItem) {
  return isConversation(item)
    ? item.id
    : String('messageId' in item ? (item.messageId || item.conversationId) : `${item.conversationId || ''}:${item.title || ''}`)
}

function isSelected(item: DisplayItem) {
  return selectedKey.value !== '' && itemKey(item) === selectedKey.value
}

function toTime(raw?: string | number) {
  const value = typeof raw === 'number' ? raw : Date.parse(String(raw || ''))
  return Number.isFinite(value) ? value : Date.now()
}

function previewConversationFrom(item: DisplayItem): Conversation | null {
  const id = conversationId(item)
  if (!id) return null
  const existing = conversations.value.find(entry => entry.id === id)
  if (existing) return existing
  if (isConversation(item)) return item
  if (isTopic(item)) {
    return {
      id,
      title: item.title || '命中结果',
      updatedAt: toTime((item as { updatedAt?: string | number }).updatedAt),
      model: (item as { model?: string }).model,
      messageCount: item.messageCount ?? 0,
      pinned: false,
      starred: false,
    }
  }
  return {
    id,
    title: item.conversationTitle || '命中结果',
    updatedAt: toTime(item.createdAt),
    pinned: false,
    starred: false,
  } as Conversation
}

function itemTitle(item: DisplayItem) {
  return isConversation(item)
    ? item.title
    : isTopic(item)
      ? item.title || '命中结果'
      : ('fileLabel' in item ? item.fileLabel || item.conversationTitle || '命中结果' : item.conversationTitle || '命中结果')
}

function itemSnippet(item: DisplayItem) {
  return isConversation(item)
    ? `${item.messageCount ?? 0} 条消息，进入该会话继续查看完整内容。`
    : String(item.snippet || '暂无摘要')
}

function itemMeta(item: DisplayItem) {
  return isConversation(item)
    ? formatDate(item.updatedAt)
    : isTopic(item)
      ? `${item.messageCount ?? 0} 条消息`
      : formatDate(item.createdAt)
}

function formatMeta(time: number, messageCount?: number) {
  const base = formatDate(time)
  return typeof messageCount === 'number' && messageCount > 0 ? `${base} · ${messageCount} 条消息` : base
}

function formatDate(raw?: string | number) {
  if (raw == null || raw === '') return ''
  const date = typeof raw === 'number' ? new Date(raw) : new Date(String(raw))
  if (Number.isNaN(date.getTime())) return ''
  const now = new Date()
  const today = new Date(now.getFullYear(), now.getMonth(), now.getDate()).getTime()
  const target = new Date(date.getFullYear(), date.getMonth(), date.getDate()).getTime()
  const diff = Math.round((today - target) / 86400000)
  if (diff === 0) return '今天'
  if (diff === 1) return '昨天'
  const month = `${date.getMonth() + 1}月`
  const day = `${date.getDate()}日`
  return date.getFullYear() === now.getFullYear() ? `${month}${day}` : `${date.getFullYear()}年${month}${day}`
}

function formatPreviewTime(raw?: number) {
  return typeof raw === 'number'
    ? new Date(raw).toLocaleString('zh-CN', { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' })
    : ''
}

function roleText(role: Message['role']) {
  return role === 'user' ? '用户' : role === 'assistant' ? 'AI' : '系统'
}

function isAnchor(message: Message) {
  return Boolean(previewAnchorId.value && (message.serverId || message.id) === previewAnchorId.value)
}

function resetPreview() {
  previewToken += 1
  selectedKey.value = ''
  selectedItem.value = null
  previewMessages.value = []
  previewLoading.value = false
  previewError.value = ''
  previewAnchorId.value = null
}

async function loadPreview(id: string) {
  const token = ++previewToken
  previewLoading.value = true
  previewError.value = ''
  try {
    const messages = await fetchConversationMessagesData(id)
    if (token !== previewToken) return
    previewMessages.value = messages
  } catch (error) {
    if (token !== previewToken) return
    previewMessages.value = []
    previewError.value = '对话预览加载失败，请稍后重试'
  } finally {
    if (token === previewToken) previewLoading.value = false
  }
}

async function selectItem(item: DisplayItem | null) {
  if (!item) {
    resetPreview()
    return
  }
  const id = conversationId(item)
  if (!id) {
    resetPreview()
    return
  }
  selectedItem.value = item
  selectedKey.value = itemKey(item)
  previewAnchorId.value = !isConversation(item) && isMessageLike(item) ? (item.anchorMessageId ?? item.messageId) : null
  await loadPreview(id)
}

async function syncSelection(next: DisplayItem[]) {
  if (next.length === 0) {
    resetPreview()
    return
  }
  if (selectedKey.value) {
    const exact = next.find(item => itemKey(item) === selectedKey.value)
    if (exact) {
      selectedItem.value = exact
      return
    }
  }
  await selectItem(next[0] ?? null)
}

async function runSearch() {
  const q = keyword.value.trim()
  if (!q) {
    result.items = []
    result.total = 0
    return
  }
  searching.value = true
  searchError.value = ''
  try {
    const payload = await searchConversationMessageHistoryData(q, undefined, 0, 20)
    result.items = Array.isArray(payload.items) ? payload.items : []
    result.total = Number(payload.total ?? 0)
  } catch (error) {
    result.items = []
    result.total = 0
    searchError.value = '历史搜索失败，请稍后重试'
  } finally {
    searching.value = false
  }
}

function submitSearch() {
  void runSearch()
}


function inferCompareIds() {
  const ids = new Set<string>()
  for (const round of buildCompareRoundsFromMessages(chatStore.messages)) {
    for (const response of round.responses) ids.add(response.modelId)
  }
  return [...ids]
}

function findConversationEntry(id: string) {
  return (
    chatStore.history.find(item => item.id === id) ??
    chatStore.archivedHistory.find(item => item.id === id)
  )
}

async function openConversation(id: string) {
  const conversation = findConversationEntry(id)
  const session = buildHistoryConversationSession(id, conversation)
  if (session.mode === 'team') {
    writeActiveChatSession(session)
    uni.navigateTo({ url: '/chat/pages/index/index' })
    return
  }

  const ok = await chatStore.loadConversation(id)
  if (!ok) {
    uni.showToast({ title: '会话加载失败', icon: 'none' })
    return
  }

  writeActiveChatSession(
    buildHistoryConversationSession(id, conversation, {
      inferredCompareIds: inferCompareIds(),
    }),
  )
  uni.navigateTo({ url: '/chat/pages/index/index' })
}

async function openSearchItem(item: SearchItem) {
  if (isTopic(item)) {
    await openConversation(String(item.conversationId || item.id || ''))
    return
  }
  const id = String(item.conversationId ?? '')
  const conversation = findConversationEntry(id)
  const session = buildHistoryConversationSession(id, conversation, {
    fallbackTitle: item.conversationTitle,
  })
  if (session.mode === 'team') {
    writeActiveChatSession(session)
    uni.navigateTo({ url: '/chat/pages/index/index' })
    return
  }
  const ok = await openHistorySearchHit(chatStore, item)
  if (!ok) {
    uni.showToast({ title: '会话加载失败', icon: 'none' })
    return
  }
  writeActiveChatSession(
    buildHistoryConversationSession(id, conversation, {
      fallbackTitle: item.conversationTitle,
      inferredCompareIds: inferCompareIds(),
    }),
  )
  uni.navigateTo({ url: '/chat/pages/index/index' })
}

function selectFromUi(item: DisplayItem, event?: Event) {
  runCompatAction(`history-select:${itemKey(item)}`, event, () => {
    void selectItem(item)
  })
}

function openArchiveDialog(id: string) {
  const conversation = chatStore.history.find(item => item.id === id) ?? previewConversation.value
  chatStore.openArchiveConfirm(id, conversation?.title || '命中结果')
}

function handleOpenPreview(event?: Event) {
  runCompatAction('history-open-preview', event, () => {
    const item = selectedItem.value
    if (!item) return
    if (isConversation(item)) {
      void openConversation(item.id)
      return
    }
    void openSearchItem(item)
  })
}

function handleArchivePreview(event?: Event) {
  const id = selectedItem.value ? conversationId(selectedItem.value) : ''
  if (!id) return
  runCompatAction(`history-archive:${id}`, event, () => openArchiveDialog(id))
}
</script>

<style scoped>
.page {
  display: flex;
  flex: 1;
  min-height: 0;
  overflow: hidden;
  background: var(--app-page-bg);
}

.shell {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-height: 0;
  max-width: 1280px;
  margin: 0 auto;
  padding: 20px 24px 48px;
  box-sizing: border-box;
}

.history-topbar-header {
  display: flex;
  align-items: center;
  min-width: 0;
  flex: 1;
  width: 100%;
}

.history-topbar-search {
  flex: 1 1 auto;
  min-width: 0;
  max-width: 960px;
}

.search-box {
  display: flex;
  align-items: center;
  gap: 12px;
  min-height: 68px;
  padding: 0 18px;
  border-radius: 22px;
  background: var(--app-surface-muted);
}

.history-search-box {
  min-height: 40px;
  padding: 0 12px;
  border-radius: 16px;
  border: 1px solid var(--app-border-color);
}

.search-icon {
  width: 22px;
  height: 22px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.search-input {
  flex: 1;
  min-width: 0;
  height: 48px;
  font-size: 16px;
  color: var(--app-text-primary);
}

.search-btn,
.row-tag,
.chip {
  padding: 6px 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;
}

.search-btn {
  background: var(--app-surface-muted);
  color: var(--app-text-primary);
  padding: 10px 18px;
  font-size: 14px;
  border: 1px solid var(--app-border-color);
}

.panel {
  background: var(--app-surface);
  border: 1px solid var(--app-border-color);
  box-shadow: var(--app-shadow-elevated);
  border-radius: 28px;
  display: flex;
  flex-direction: column;
  min-height: 0;
}


.grid {
  display: flex;
  flex: 1;
  gap: 24px;
  margin-top: 24px;
  min-height: 0;
  align-items: stretch;
}

.list-panel {
  flex: 1 1 0;
  min-width: 0;
  overflow: hidden;
}

.side {
  flex: 1 1 0;
  width: auto;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.side > .panel {
  flex: 1;
}

.panel-head {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: flex-start;
  padding: 22px 24px 16px;
}

.compact {
  padding: 18px 20px 12px;
}

.panel-title {
  font-size: 22px;
  font-weight: 700;
  color: var(--app-text-primary);
}

.panel-title.small {
  font-size: 15px;
}

.panel-note {
  margin-top: 6px;
  font-size: 12px;
  color: var(--app-text-secondary);
  font-weight: 600;
}

.panel-tip {
  max-width: 260px;
  font-size: 12px;
  line-height: 1.6;
  color: var(--app-text-secondary);
  text-align: right;
}

.state {
  margin: 0 20px 20px;
  padding: 22px 18px;
  border-radius: 20px;
  background: var(--app-surface-muted);
  color: var(--app-text-secondary);
  font-size: 14px;
  line-height: 1.7;
  text-align: center;
}

.state.small {
  margin-bottom: 16px;
  padding: 18px 16px;
  font-size: 13px;
}

.state.error {
  background: var(--app-danger-soft);
  color: var(--app-danger-contrast);
}

.list {
  padding: 0 12px 12px;
  box-sizing: border-box;
}

.list-scroll {
  flex: 1;
  min-height: 0;
  height: 0;
}

.row {
  display: flex;
  justify-content: space-between;
  gap: 18px;
  padding: 18px 14px;
  border-radius: 20px;
  border: 1px solid transparent;
  transition: background 0.12s ease, border-color 0.12s ease, transform 0.12s ease;
}

.row + .row {
  margin-top: 8px;
}

.row.active {
  background: var(--app-accent-soft);
  border-color: rgba(96, 165, 250, 0.24);
  transform: translateX(2px);
}

.row-main {
  flex: 1;
  min-width: 0;
}

.row-title,
.summary-title {
  font-size: 16px;
  font-weight: 700;
  line-height: 1.45;
  color: var(--app-text-primary);
}

.row-snippet,
.message-content {
  margin-top: 6px;
  font-size: 13px;
  line-height: 1.72;
  color: var(--app-text-secondary);
  white-space: pre-wrap;
  word-break: break-word;
}

.row-side {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 8px;
  flex-shrink: 0;
}

.row-meta,
.message-role {
  font-size: 12px;
  font-weight: 600;
  color: var(--app-text-secondary);
}

.row-tag {
  background: var(--app-surface-muted);
  color: var(--app-text-secondary);
}

.row.active .row-tag {
  background: rgba(96, 165, 250, 0.18);
  color: var(--app-accent-contrast);
}

.preview {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-height: 0;
  padding: 0 20px 18px;
}

.chips,
.message-head,
.actions {
  display: flex;
}

.chips {
  gap: 10px;
  flex-wrap: wrap;
  margin-top: 10px;
}

.chip {
  background: var(--app-surface-muted);
  color: var(--app-text-primary);
}

.chip.mute {
  background: var(--app-fill-hover);
  color: var(--app-text-secondary);
}

.chip.accent {
  background: rgba(96, 165, 250, 0.18);
  color: var(--app-accent-contrast);
}

.messages {
  flex: 1;
  min-height: 0;
  height: 0;
  max-height: none;
  margin-top: 14px;
  padding-right: 4px;
}

.message {
  padding: 14px 16px;
  border-radius: 20px;
  background: var(--app-surface-muted);
  border: 1px solid var(--app-border-color-soft);
}

.message + .message {
  margin-top: 10px;
}

.message.user {
  background: var(--app-surface);
}

.message.anchor {
  box-shadow: inset 0 0 0 1px rgba(96, 165, 250, 0.42);
}

.message-head {
  justify-content: space-between;
  gap: 12px;
}

.message-time {
  font-size: 12px;
  color: var(--app-text-secondary);
}

.actions {
  gap: 10px;
  flex-wrap: wrap;
  margin-top: 16px;
}

.pill {
  min-width: 132px;
  padding: 12px 18px;
  border-radius: 999px;
  text-align: center;
  font-size: 14px;
  font-weight: 600;
  box-sizing: border-box;
}

.secondary {
  background: var(--app-surface-muted);
  border: 1px solid var(--app-border-color);
  color: var(--app-text-primary);
}

.danger {
  background: var(--app-danger-soft);
  color: var(--app-danger-contrast);
}

.grow {
  flex: 1;
}

@media screen and (max-width: 1024px) {
  .grid {
    flex-direction: column;
  }

  .side {
    width: 100%;
  }

  .history-topbar-header {
    width: 100%;
  }
}

@media screen and (max-width: 768px) {
  .shell {
    padding: 16px 16px 28px;
  }

  .toolbar,
  .panel-head,
  .row,
  .actions {
    flex-direction: column;
  }

  .history-topbar-header {
    align-items: stretch;
    width: 100%;
  }

  .history-topbar-search {
    width: 100%;
    max-width: none;
  }

  .row-side {
    width: 100%;
    align-items: flex-start;
  }

  .panel-tip {
    max-width: none;
    text-align: left;
  }
}
</style>
