import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { getMyGroupProfile } from '@/api/groups'
import type { UserGroupProfileResponse } from '@/api/types'
import { config } from '@/config'
import { useAuthStore } from '@/stores/auth'

const STORAGE_KEY = 'userGroupProfile'

function normalizeFeatureKey(featureKey: string) {
  return featureKey.trim().toLowerCase().replace(/-/g, '_')
}

function readStoredProfile() {
  try {
    const stored = uni.getStorageSync(STORAGE_KEY)
    if (stored && typeof stored === 'object') {
      return stored as UserGroupProfileResponse
    }
  } catch {
    /* ignore */
  }
  return null
}

function persistProfile(profile: UserGroupProfileResponse | null) {
  try {
    if (profile) {
      uni.setStorageSync(STORAGE_KEY, profile)
    } else {
      uni.removeStorageSync(STORAGE_KEY)
    }
  } catch {
    /* ignore */
  }
}

export const useGroupStore = defineStore('group', () => {
  const profile = ref<UserGroupProfileResponse | null>(readStoredProfile())
  const loading = ref(false)
  const loaded = ref(profile.value !== null || !config.features.userGroups)
  const error = ref<string | null>(null)
  let pendingFetch: Promise<UserGroupProfileResponse | null> | null = null

  const normalizedFeatureFlags = computed(() => {
    return new Set(
      (profile.value?.featureFlags || [])
        .filter((item): item is string => typeof item === 'string' && item.trim().length > 0)
        .map(normalizeFeatureKey),
    )
  })

  const normalizedAllowedModels = computed(() => {
    return new Set(
      (profile.value?.allowedModels || [])
        .filter((item): item is string => typeof item === 'string' && item.trim().length > 0)
        .map(item => item.trim()),
    )
  })

  function loadFromStorage() {
    profile.value = readStoredProfile()
    loaded.value = profile.value !== null || !config.features.userGroups
  }

  function clearProfile() {
    profile.value = null
    loaded.value = !config.features.userGroups
    error.value = null
    persistProfile(null)
  }

  async function fetchProfile(options: { force?: boolean } = {}) {
    if (!config.features.userGroups) {
      clearProfile()
      loaded.value = true
      return null
    }
    if (loading.value) {
      return pendingFetch ?? profile.value
    }
    if (loaded.value && !options.force) {
      return profile.value
    }

    const authStore = useAuthStore()
    authStore.loadFromStorage()
    if (!authStore.isLoggedIn || authStore.isGuest) {
      clearProfile()
      loaded.value = true
      return null
    }

    loading.value = true
    error.value = null
    pendingFetch = (async () => {
      try {
        const result = await getMyGroupProfile()
        profile.value = result.data ?? null
        persistProfile(profile.value)
        loaded.value = true
        return profile.value
      } catch (err) {
        error.value = err instanceof Error ? err.message : 'Failed to load group profile'
        loaded.value = profile.value !== null
        return profile.value
      } finally {
        loading.value = false
        pendingFetch = null
      }
    })()
    return pendingFetch
  }

  async function ensureProfileLoaded() {
    if (!config.features.userGroups || loaded.value) {
      return profile.value
    }
    if (loading.value) {
      return pendingFetch ?? profile.value
    }
    return fetchProfile()
  }

  function isFeatureEnabled(featureKey: string, brandEnabled = true) {
    if (!brandEnabled) {
      return false
    }
    if (!config.features.userGroups) {
      return true
    }
    if (!featureKey || !featureKey.trim()) {
      return true
    }
    if (normalizedFeatureFlags.value.size === 0) {
      return true
    }
    return normalizedFeatureFlags.value.has(normalizeFeatureKey(featureKey))
  }

  function hasAnyFeature(featureKeys: string[], brandEnabled = true) {
    if (!brandEnabled) {
      return false
    }
    if (!config.features.userGroups) {
      return true
    }
    if (normalizedFeatureFlags.value.size === 0) {
      return true
    }
    return featureKeys.some(featureKey => {
      if (!featureKey || !featureKey.trim()) {
        return false
      }
      return normalizedFeatureFlags.value.has(normalizeFeatureKey(featureKey))
    })
  }

  function isModelAllowed(modelId: string) {
    if (!modelId || !modelId.trim()) {
      return true
    }
    if (!config.features.userGroups) {
      return true
    }
    if (normalizedAllowedModels.value.size === 0) {
      return true
    }
    return normalizedAllowedModels.value.has(modelId.trim())
  }

  return {
    profile,
    loading,
    loaded,
    error,
    loadFromStorage,
    clearProfile,
    fetchProfile,
    ensureProfileLoaded,
    isFeatureEnabled,
    hasAnyFeature,
    isModelAllowed,
  }
})
