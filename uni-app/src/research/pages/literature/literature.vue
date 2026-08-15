<script setup lang="ts">
import { ref } from 'vue'
import AppLayout from '@/layouts/AppLayout.vue'
import { searchLiterature, type LiteratureItem } from '@/api/research'
import { createCompatActionRunner, type CompatEventLike } from '@/utils/h5EventCompat'
// #ifdef MP-WEIXIN
import MpShapeIcon from '@/components/icons/MpShapeIcon.vue'
// #endif

const query = ref('')
const loading = ref(false)
const results = ref<LiteratureItem[]>([])
const searched = ref(false)
const runCompatAction = createCompatActionRunner()

const handleSearch = async () => {
  const q = query.value.trim()
  if (!q) {
    uni.showToast({ title: '请输入搜索关键词', icon: 'none' })
    return
  }
  loading.value = true
  searched.value = true
  try {
    const res = await searchLiterature(q, 30)
    results.value = Array.isArray(res.data) ? res.data : []
  } catch {
    results.value = []
  } finally {
    loading.value = false
  }
}

const goBack = () => {
  uni.navigateBack()
}

const copyDoi = (doi: string) => {
  uni.setClipboardData({
    data: doi,
    success: () => uni.showToast({ title: 'DOI 已复制', icon: 'success' }),
  })
}

const openUrl = (url: string) => {
  if (!url) return
  // #ifdef H5
  window.open(url, '_blank')
  // #endif
  // #ifndef H5
  uni.setClipboardData({
    data: url,
    success: () => uni.showToast({ title: '链接已复制到剪贴板', icon: 'success' }),
  })
  // #endif
}

const handleGoBackActivate = (event?: CompatEventLike) => {
  runCompatAction('research-literature-back', event, () => {
    goBack()
  })
}

const handleSearchActivate = (event?: CompatEventLike) => {
  runCompatAction('research-literature-search', event, () => {
    void handleSearch()
  })
}

const handleCopyDoiActivate = (doi: string, event?: CompatEventLike) => {
  runCompatAction(`research-literature-copy-doi:${doi}`, event, () => {
    copyDoi(doi)
  })
}

const handleOpenUrlActivate = (url: string, event?: CompatEventLike) => {
  runCompatAction(`research-literature-open-url:${url}`, event, () => {
    openUrl(url)
  })
}

const formatAuthors = (authors: string[]) => {
  if (!authors || authors.length === 0) return ''
  if (authors.length <= 3) return authors.join(', ')
  return authors.slice(0, 3).join(', ') + ' et al.'
}

const sourceLabels: Record<string, string> = {
  openalex: 'OpenAlex',
  semantic_scholar: 'S2',
  arxiv: 'arXiv',
}

const sourceColors: Record<string, string> = {
  openalex: 'var(--app-text-primary)',
  semantic_scholar: 'var(--app-text-secondary)',
  arxiv: 'var(--app-danger)',
}
</script>

<template>
  <AppLayout>
    <view class="research-literature-page h-full flex flex-col bg-gray-50">
      <!-- Header -->
      <view class="bg-white border-b border-gray-200 px-4 py-3 shrink-0">
        <view class="flex items-center gap-3 mb-3">
          <view @click="handleGoBackActivate" @tap="handleGoBackActivate" class="w-8 h-8 flex items-center justify-center rounded-lg hover:bg-gray-100 cursor-pointer">
            <!-- #ifdef MP-WEIXIN -->
            <MpShapeIcon name="chevron-left" :size="20" color="currentColor" :stroke-width="2" />
            <!-- #endif -->
            <!-- #ifndef MP-WEIXIN -->
            <svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="m15 18-6-6 6-6"/></svg>
            <!-- #endif -->
          </view>
          <text class="text-lg font-bold text-gray-900">文献搜索</text>
        </view>
        <!-- Search Bar -->
        <view class="flex gap-2">
          <view class="flex-1 relative">
            <!-- #ifdef MP-WEIXIN -->
            <view class="absolute left-3 top-1/2 -translate-y-1/2">
              <MpShapeIcon name="search" :size="16" color="currentColor" :stroke-width="2" />
            </view>
            <!-- #endif -->
            <!-- #ifndef MP-WEIXIN -->
            <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" class="absolute left-3 top-1/2 -translate-y-1/2"><circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/></svg>
            <!-- #endif -->
            <input
              v-model="query"
              @confirm="handleSearch"
              placeholder="搜索论文标题、关键词、DOI..."
              class="w-full bg-gray-50 border border-gray-200 rounded-xl pl-10 pr-4 py-2.5 text-sm focus:ring-2 focus:ring-blue-500 focus:outline-none"
            />
          </view>
          <button
            @click="handleSearchActivate"
            @tap="handleSearchActivate"
            :disabled="loading"
            class="m-0 px-5 py-2.5 rounded-xl bg-gray-900 text-white text-sm font-medium shadow-sm shrink-0"
          >
            {{ loading ? '搜索中' : '搜索' }}
          </button>
        </view>
        <text class="text-xs text-gray-400 mt-2 block">
          数据源: OpenAlex / Semantic Scholar / arXiv
        </text>
      </view>

      <!-- Results -->
      <scroll-view scroll-y class="flex-1 px-4 pb-6">
        <view v-if="loading" class="h-40 flex items-center justify-center text-gray-400">
          <text>正在搜索学术数据库...</text>
        </view>
        <view v-else-if="!searched" class="h-60 flex flex-col items-center justify-center text-gray-400">
          <!-- #ifdef MP-WEIXIN -->
          <MpShapeIcon name="search" :size="48" color="currentColor" :stroke-width="1.5" class="mb-4 text-gray-300" />
          <!-- #endif -->
          <!-- #ifndef MP-WEIXIN -->
          <svg xmlns="http://www.w3.org/2000/svg" width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" class="mb-4 text-gray-300">
            <circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/>
          </svg>
          <!-- #endif -->
          <text class="text-base mb-2">输入关键词开始搜索</text>
          <text class="text-sm">同时检索 OpenAlex、Semantic Scholar、arXiv</text>
        </view>
        <view v-else-if="results.length === 0" class="h-40 flex items-center justify-center text-gray-400">
          <text>未找到相关文献</text>
        </view>
        <view v-else class="flex flex-col gap-3 mt-3">
          <text class="text-xs text-gray-500">找到 {{ results.length }} 篇文献</text>
          <view
            v-for="(paper, idx) in results"
            :key="idx"
            class="bg-white border border-gray-200 rounded-xl p-4 shadow-sm"
          >
            <!-- Title + Source Badge -->
            <view class="flex items-start gap-2 mb-2">
              <text class="text-sm font-medium text-gray-900 flex-1 leading-relaxed">{{ paper.title }}</text>
              <view
                class="px-1.5 py-0.5 rounded text-[10px] font-medium shrink-0"
                :style="{ backgroundColor: (sourceColors[paper.source] || 'var(--app-text-secondary)') + '18', color: sourceColors[paper.source] || 'var(--app-text-secondary)' }"
              >
                {{ sourceLabels[paper.source] || paper.source }}
              </view>
            </view>

            <!-- Authors -->
            <text v-if="paper.authors?.length" class="text-xs text-gray-500 block mb-1.5">
              {{ formatAuthors(paper.authors) }}
            </text>

            <!-- Meta Row -->
            <view class="flex items-center gap-3 text-xs text-gray-400 mb-2">
              <text v-if="paper.year">{{ paper.year }}</text>
              <text v-if="paper.citationCount > 0">引用 {{ paper.citationCount }}</text>
              <text
                v-if="paper.doi"
                @click="handleCopyDoiActivate(paper.doi, $event)"
                @tap="handleCopyDoiActivate(paper.doi, $event)"
                class="text-blue-500 cursor-pointer"
              >
                DOI
              </text>
              <text
                v-if="paper.url"
                @click="handleOpenUrlActivate(paper.url, $event)"
                @tap="handleOpenUrlActivate(paper.url, $event)"
                class="text-blue-500 cursor-pointer"
              >
                原文
              </text>
            </view>

            <!-- Abstract -->
            <text v-if="paper.abstract" class="text-xs text-gray-600 leading-relaxed line-clamp-3 block">
              {{ paper.abstract }}
            </text>
          </view>
        </view>
      </scroll-view>
    </view>
  </AppLayout>
</template>

<style scoped>
.research-literature-page .bg-white {
  background-color: var(--app-surface) !important;
}

.research-literature-page .bg-gray-50 {
  background-color: var(--app-page-bg) !important;
}

.research-literature-page .border-gray-200 {
  border-color: var(--app-border-color) !important;
}

.research-literature-page .text-gray-900,
.research-literature-page .text-white {
  color: var(--app-text-primary) !important;
}

.research-literature-page .text-gray-600,
.research-literature-page .text-gray-500,
.research-literature-page .text-gray-400,
.research-literature-page .text-gray-300,
.research-literature-page .text-blue-500 {
  color: var(--app-text-secondary) !important;
}

.research-literature-page .bg-gray-900 {
  background-color: var(--app-surface-raised) !important;
  border: 1px solid var(--app-border-color) !important;
}

.research-literature-page .hover\:bg-gray-100:hover {
  background-color: var(--app-fill-hover) !important;
}

.research-literature-page .focus\:ring-blue-500:focus {
  --tw-ring-color: rgba(255, 255, 255, 0.12) !important;
}
</style>
