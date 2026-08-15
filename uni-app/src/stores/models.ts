/**
 * 模型选择状态管理 (Pinia)
 * - 从后端获取可用模型列表
 * - 本地头像映射（avatarKey -> static 图片）
 * - 记住用户上次选择
 */
import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { getModelCatalog } from '@/api/models'
import { APP_CONFIG, config } from '@/config'
import { useGroupStore } from '@/stores/group'
import { extractErrorMessage } from '@/utils/errorMessage'

export interface ModelInfo {
  id: string
  name: string
  avatar: string
  description: string
  pinned?: boolean
  sortOrder?: number
  defaultSelected?: boolean
  isDefault?: boolean
  multiChatEnabled?: boolean
  billingEnabled?: boolean
  requestPriceUsd?: number | string | null
  promptPriceUsd?: number | string | null
  inputPriceUsdPer1M?: number | string | null
  outputPriceUsdPer1M?: number | string | null
  supportsImageParsing?: boolean
  supportsImageParsingSource?: 'manual' | 'inferred' | 'unknown'
}

/** platform key -> 本地 static 路径 */
const AVATAR_MAP: Record<string, string> = {
  openai: '/static/images/models/openai.png',
  claude: '/static/images/models/claude.png',
  gemini: '/static/images/models/gemini.png',
  deepseek: '/static/images/models/deepseek.png',
  qwen: '/static/images/models/qwen.png',
  meta: '/static/images/models/default.png',
  mistral: '/static/images/models/default.png',
  grok: '/static/images/models/default.png',
  doubao: '/static/images/models/default.png',
  kimi: '/static/images/models/default.png',
  zhipu: '/static/images/models/default.png',
  yi: '/static/images/models/default.png',
  hunyuan: '/static/images/models/default.png',
  ernie: '/static/images/models/default.png',
  default: '/static/images/models/default.png',
}

export const useModelStore = defineStore('models', () => {
  const models = ref<ModelInfo[]>([])
  const selectedModelId = ref<string>('')
  const loading = ref(false)
  const loaded = ref(false)
  const loadError = ref('')

  const selectedModel = computed(() =>
    models.value.find(m => m.id === selectedModelId.value) || models.value[0] || null
  )

  /** 获取模型头像的本地路径 */
  function getAvatarPath(avatarKey: string): string {
    return AVATAR_MAP[avatarKey] || AVATAR_MAP.default
  }

  function persistSelectedModel(id: string) {
    if (!id) {
      uni.removeStorageSync('selectedModelId')
      return
    }
    uni.setStorageSync('selectedModelId', id)
  }

  /** 当前选中模型的头像路径 */
  const currentAvatar = computed(() => {
    if (!selectedModel.value) return '/static/images/logo.png'
    return getAvatarPath(selectedModel.value.avatar)
  })

  /** 当前选中模型的显示名 */
  const currentName = computed(() => {
    return selectedModel.value?.name || APP_CONFIG.aiName
  })

  function resolveSelectableModelId(nextModels: ModelInfo[]) {
    const saved = selectedModelId.value || (uni.getStorageSync('selectedModelId') as string)
    if (saved && nextModels.some(m => m.id === saved)) {
      return saved
    }

    const backendDefault = nextModels.find(m => m.defaultSelected === true || m.isDefault === true)
    if (backendDefault) {
      return backendDefault.id
    }

    const defaultMatch = nextModels.find(m => m.id === APP_CONFIG.defaultModel)
    if (defaultMatch) {
      return defaultMatch.id
    }

    return nextModels[0]?.id || ''
  }

  /** 从后端加载可用模型列表 */
  async function fetchModels() {
    if (loading.value) return
    const groupStore = useGroupStore()
    loading.value = true
    loadError.value = ''
    try {
      await groupStore.ensureProfileLoaded()
      const res = await getModelCatalog()
      if (!Array.isArray(res.data)) {
        throw new Error('模型目录返回格式无效')
      }

      const incoming = [...res.data].filter(model => groupStore.isModelAllowed(model.id))
      const nextModels = config.features.modelCatalog
        ? incoming.sort((a, b) => {
            const aPinned = a.pinned === true ? 0 : 1
            const bPinned = b.pinned === true ? 0 : 1
            if (aPinned !== bPinned) return aPinned - bPinned
            const aOrder = a.sortOrder ?? 0
            const bOrder = b.sortOrder ?? 0
            if (aOrder !== bOrder) return aOrder - bOrder
            return a.name.localeCompare(b.name)
          })
        : incoming

      models.value = nextModels
      selectedModelId.value = resolveSelectableModelId(nextModels)
      persistSelectedModel(selectedModelId.value)
      loadError.value = ''
      loaded.value = true
    } catch (error) {
      loadError.value = extractErrorMessage(error, '模型目录加载失败')
      loaded.value = true
    } finally {
      loading.value = false
    }
  }

  /** 切换模型 */
  function selectModel(id: string) {
    if (models.value.some(m => m.id === id)) {
      selectedModelId.value = id
      persistSelectedModel(id)
    }
  }

  return {
    models,
    selectedModelId,
    selectedModel,
    loading,
    loaded,
    loadError,
    currentAvatar,
    currentName,
    getAvatarPath,
    fetchModels,
    selectModel,
  }
})
