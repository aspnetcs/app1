import { computed } from 'vue'
import { describe, expect, it, vi } from 'vitest'
import { buildFeatureMenuEntries, createAppLayoutNavigation } from './appLayoutNavigation'

describe('appLayoutNavigation', () => {
  it('builds the live feature menu from a single source of truth', () => {
    const calls: string[] = []
    const entries = buildFeatureMenuEntries({
      openHistoryPage: () => calls.push('history'),
      openTranslationPage: () => calls.push('translation'),
      openResearchPage: () => calls.push('research'),
      openMarketPage: () => calls.push('market'),
      openAccountPage: () => calls.push('account'),
    })

    // Verify base entries exist
    expect(entries.length).toBeGreaterThanOrEqual(3)
    expect(entries[0]?.label).toBe('历史')
    expect(entries[entries.length - 1]?.label).toBe('设置和帮助')

    // Verify actions work
    const translationIndex = entries.findIndex(e => e.label === '文本翻译')
    const researchIndex = entries.findIndex(e => e.label === '科研助理')

    if (translationIndex >= 0) {
      entries[translationIndex]?.action()
      expect(calls).toContain('translation')
    }
    if (researchIndex >= 0) {
      entries[researchIndex]?.action()
      expect(calls).toContain('research')
    }
  })

  it('uses the feature-menu order when dispatching an action-sheet selection', () => {
    const redirectTo = vi.fn()
    const reLaunch = vi.fn()
    const showToast = vi.fn()

    ;(globalThis as Record<string, unknown>).getCurrentPages = () => [{ route: 'pages/index/index' }]
    ;(globalThis as Record<string, unknown>).uni = {
      showActionSheet: ({
        itemList,
        success,
      }: {
        itemList: string[]
        success?: (payload: { tapIndex: number }) => void
      }) => {
        // Click on first menu item (历史)
        success?.({ tapIndex: 0 })
      },
      redirectTo,
      reLaunch,
      showToast,
    }

    const navigation = createAppLayoutNavigation({
      isMobileLayout: computed(() => false),
      closeSidebar: vi.fn(),
    })

    navigation.openFeatureMenu()

    // First item should be 历史 (history)
    expect(redirectTo).toHaveBeenCalledWith({
      url: '/chat/pages/history/history',
      fail: expect.any(Function),
    })
    expect(reLaunch).not.toHaveBeenCalled()
    expect(showToast).not.toHaveBeenCalled()
  })
})
