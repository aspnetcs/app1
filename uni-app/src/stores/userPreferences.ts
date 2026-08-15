import { computed, reactive, ref } from 'vue'
import { defineStore } from 'pinia'
import {
  agentAssetMatchesId,
  getMarketAssetSourceId,
  listSavedMarketAssets,
  removeSavedMarketAsset,
  saveMarketAsset,
  type MarketAsset,
  type MarketAssetType,
} from '@/api/market'
import {
  getUserPreferences,
  updateUserPreferences,
  type UserPreferences,
} from '@/api/preferences'

const MARKET_ASSET_TYPES: MarketAssetType[] = ['AGENT', 'KNOWLEDGE', 'MCP', 'SKILL']
const LOCAL_PREFS_KEY = 'APP_LOCAL_PREFS_FALLBACK'

function createDefaultPreferences(): UserPreferences {
  return {
    defaultAgentId: null,
    themeMode: 'system',
    codeTheme: 'system',
    fontScale: 'md',
    mcpMode: 'auto',
    preferredMcpServerId: null,
    spacingVertical: '16px',
    spacingHorizontal: '16px',
  }
}

function createSavedAssetMap<T>(factory: () => T): Record<MarketAssetType, T> {
  return {
    AGENT: factory(),
    KNOWLEDGE: factory(),
    MCP: factory(),
    SKILL: factory(),
  }
}

function extractErrorMessage(error: unknown, fallback: string) {
  if (error instanceof Error && error.message) {
    return error.message
  }
  if (typeof error === 'object' && error && 'message' in error) {
    const message = String((error as { message?: unknown }).message ?? '').trim()
    if (message) {
      return message
    }
  }
  return fallback
}

function readLocalPreferencesFallback(): Partial<UserPreferences> {
  try {
    const stored = uni.getStorageSync(LOCAL_PREFS_KEY)
    if (!stored) {
      return {}
    }
    const parsed = JSON.parse(stored)
    return parsed && typeof parsed === 'object' ? parsed as Partial<UserPreferences> : {}
  } catch {
    return {}
  }
}

function writeLocalPreferencesFallback(snapshot: Partial<UserPreferences>) {
  try {
    uni.setStorageSync(LOCAL_PREFS_KEY, JSON.stringify(snapshot))
  } catch {
    // ignore storage write failures and continue with memory state
  }
}

export const useUserPreferencesStore = defineStore('userPreferences', () => {
  const preferences = ref<UserPreferences>(createDefaultPreferences())
  const loading = ref(false)
  const saving = ref(false)
  const error = ref('')
  const savedAssets = reactive<Record<MarketAssetType, MarketAsset[]>>(createSavedAssetMap(() => []))
  const savedAssetsLoading = reactive<Record<MarketAssetType, boolean>>(createSavedAssetMap(() => false))
  const savedAssetErrors = reactive<Record<MarketAssetType, string>>(createSavedAssetMap(() => ''))

  function applyPreferences(payload?: Partial<UserPreferences> | null) {
    let localPrefs: Partial<UserPreferences> = {}
    try {
      const stored = uni.getStorageSync(LOCAL_PREFS_KEY)
      if (stored) localPrefs = JSON.parse(stored)
    } catch {
      // ignore parse error
    }
    
    preferences.value = {
      ...createDefaultPreferences(),
      ...localPrefs,
      ...(payload ?? {}),
    }
    
    // 如果 payload 里缺失新字段，强制保留本地 fallback 并写回
    if (payload && !('spacingVertical' in payload) && localPrefs.spacingVertical) {
      preferences.value.spacingVertical = localPrefs.spacingVertical
    }
    if (payload && !('spacingHorizontal' in payload) && localPrefs.spacingHorizontal) {
      preferences.value.spacingHorizontal = localPrefs.spacingHorizontal
    }
  }

  function replaceSavedAssets(assetType: MarketAssetType, items: MarketAsset[]) {
    savedAssets[assetType] = Array.isArray(items) ? items : []
  }

  function upsertSavedAsset(assetType: MarketAssetType, asset: MarketAsset) {
    const next = [...savedAssets[assetType]]
    const index = next.findIndex((item) => item.sourceId === asset.sourceId)
    if (index >= 0) {
      next[index] = asset
    } else {
      next.unshift(asset)
    }
    savedAssets[assetType] = next
  }

  async function loadPreferences() {
    if (loading.value) {
      return preferences.value
    }
    loading.value = true
    try {
      const response = await getUserPreferences()
      applyPreferences(response.data)
      error.value = ''
    } catch (nextError) {
      error.value = extractErrorMessage(nextError, '偏好设置加载失败')
      applyPreferences(readLocalPreferencesFallback())
    } finally {
      loading.value = false
    }
    return preferences.value
  }

  async function updatePreferencesPatch(payload: Partial<UserPreferences>) {
    saving.value = true
    try {
      // 先写回本地 fallback 快照
      writeLocalPreferencesFallback({
        ...readLocalPreferencesFallback(),
        ...preferences.value,
        ...payload,
      })

      const response = await updateUserPreferences(payload)
      applyPreferences(response.data)
      
      // 倘若后端返回的 response 真的不支持这2个新字段，用 payload 手动补齐给当前状态
      if (response.data && !('spacingVertical' in response.data) && payload.spacingVertical) {
        preferences.value.spacingVertical = payload.spacingVertical
      }
      if (response.data && !('spacingHorizontal' in response.data) && payload.spacingHorizontal) {
        preferences.value.spacingHorizontal = payload.spacingHorizontal
      }

      error.value = ''
      return preferences.value
    } catch (nextError) {
      error.value = extractErrorMessage(nextError, '偏好设置保存失败')
      throw nextError
    } finally {
      saving.value = false
    }
  }

  async function loadSavedAssets(assetType: MarketAssetType) {
    savedAssetsLoading[assetType] = true
    try {
      const response = await listSavedMarketAssets(assetType)
      replaceSavedAssets(assetType, Array.isArray(response.data) ? response.data : [])
      savedAssetErrors[assetType] = ''
    } catch (nextError) {
      replaceSavedAssets(assetType, [])
      savedAssetErrors[assetType] = extractErrorMessage(nextError, `${assetType} 已保存资产加载失败`)
    } finally {
      savedAssetsLoading[assetType] = false
    }
    return savedAssets[assetType]
  }

  async function loadAllSavedAssets() {
    await Promise.all(MARKET_ASSET_TYPES.map((assetType) => loadSavedAssets(assetType)))
  }

  async function saveAssetRelation(assetType: MarketAssetType, sourceId: string) {
    const response = await saveMarketAsset(assetType, sourceId)
    if (response.data) {
      upsertSavedAsset(assetType, response.data)
    }
    return response.data
  }

  async function removeSavedAssetRelation(assetType: MarketAssetType, sourceId: string) {
    await removeSavedMarketAsset(assetType, sourceId)
    savedAssets[assetType] = savedAssets[assetType].filter((item) => item.sourceId !== sourceId)
  }

  const savedAgentAssets = computed(() => savedAssets.AGENT)
  const savedKnowledgeAssets = computed(() => savedAssets.KNOWLEDGE)
  const savedMcpAssets = computed(() => savedAssets.MCP)
  const savedSkillAssets = computed(() => savedAssets.SKILL)

  const defaultAgentAsset = computed(() =>
    savedAgentAssets.value.find((item) => agentAssetMatchesId(item, preferences.value.defaultAgentId)) ?? null,
  )

  const preferredMcpAsset = computed(() =>
    savedMcpAssets.value.find((item) => {
      const preferredId = preferences.value.preferredMcpServerId
      if (!preferredId) return false
      return getMarketAssetSourceId(item) === preferredId || item.sourceId === preferredId
    }) ?? null,
  )

  function findAgentAssetById(agentId: string | null | undefined) {
    return savedAgentAssets.value.find((item) => agentAssetMatchesId(item, agentId)) ?? null
  }

  return {
    preferences,
    loading,
    saving,
    error,
    savedAssets,
    savedAssetsLoading,
    savedAssetErrors,
    savedAgentAssets,
    savedKnowledgeAssets,
    savedMcpAssets,
    savedSkillAssets,
    defaultAgentAsset,
    preferredMcpAsset,
    loadPreferences,
    updatePreferencesPatch,
    loadSavedAssets,
    loadAllSavedAssets,
    saveAssetRelation,
    removeSavedAssetRelation,
    findAgentAssetById,
  }
})
