import { describe, expect, it, vi } from 'vitest'
import { handleEntryPageMount } from './entryFlow'

describe('entryFlow', () => {
  it('keeps guest-to-chat entry working when guest auth is enabled', async () => {
    const ensureAuth = vi.fn().mockResolvedValue(undefined)
    const ensureGuestSession = vi.fn().mockResolvedValue('guest-token')
    const reLaunch = vi.fn()
    const showToast = vi.fn()
    const warn = vi.fn()
    let isLoggedIn = false

    await handleEntryPageMount({
      ensureAuth,
      isLoggedIn: () => isLoggedIn,
      guestAuthEnabled: true,
      ensureGuestSession,
      resolveNextUrl: async () => '/pages/onboarding/onboarding',
      reLaunch,
      showToast,
      warn,
    })

    expect(ensureAuth).toHaveBeenCalledTimes(1)
    expect(ensureGuestSession).toHaveBeenCalledTimes(1)
    expect(reLaunch).toHaveBeenCalledWith('/pages/onboarding/onboarding')
    expect(showToast).not.toHaveBeenCalled()
    expect(warn).not.toHaveBeenCalled()
  })

  it('falls back to login with a toast when guest auth cannot issue a session', async () => {
    const ensureAuth = vi.fn().mockResolvedValue(undefined)
    const ensureGuestSession = vi.fn().mockRejectedValue(new Error('guest auth unavailable'))
    const reLaunch = vi.fn()
    const showToast = vi.fn()
    const warn = vi.fn()
    let isLoggedIn = false

    await handleEntryPageMount({
      ensureAuth,
      isLoggedIn: () => isLoggedIn,
      guestAuthEnabled: true,
      ensureGuestSession,
      resolveNextUrl: async () => '/chat/pages/index/index',
      reLaunch,
      showToast,
      warn,
    })

    expect(ensureAuth).toHaveBeenCalledTimes(1)
    expect(ensureGuestSession).toHaveBeenCalledTimes(1)
    expect(warn).toHaveBeenCalledWith('[guest-auth] issue guest session failed', expect.any(Error))
    expect(showToast).toHaveBeenCalledWith({
      title: '访客模式暂不可用，请先登录',
      icon: 'none',
    })
    expect(reLaunch).toHaveBeenCalledWith('/pages/account/account')
  })

  it('uses the refreshed login state after ensureAuth succeeds', async () => {
    let isLoggedIn = false
    const ensureAuth = vi.fn().mockImplementation(async () => {
      isLoggedIn = true
    })
    const ensureGuestSession = vi.fn()
    const reLaunch = vi.fn()
    const showToast = vi.fn()
    const warn = vi.fn()

    await handleEntryPageMount({
      ensureAuth,
      isLoggedIn: () => isLoggedIn,
      guestAuthEnabled: true,
      ensureGuestSession,
      resolveNextUrl: async () => '/chat/pages/index/index',
      reLaunch,
      showToast,
      warn,
    })

    expect(ensureAuth).toHaveBeenCalledTimes(1)
    expect(ensureGuestSession).not.toHaveBeenCalled()
    expect(showToast).not.toHaveBeenCalled()
    expect(warn).not.toHaveBeenCalled()
    expect(reLaunch).toHaveBeenCalledWith('/chat/pages/index/index')
  })

  it('routes unauthenticated users to login when guest auth is disabled', async () => {
    const ensureAuth = vi.fn().mockResolvedValue(undefined)
    const ensureGuestSession = vi.fn()
    const reLaunch = vi.fn()
    const showToast = vi.fn()
    const warn = vi.fn()

    await handleEntryPageMount({
      ensureAuth,
      isLoggedIn: () => false,
      guestAuthEnabled: false,
      ensureGuestSession,
      resolveNextUrl: async () => '/chat/pages/index/index',
      reLaunch,
      showToast,
      warn,
    })

    expect(ensureAuth).toHaveBeenCalledTimes(1)
    expect(ensureGuestSession).not.toHaveBeenCalled()
    expect(showToast).not.toHaveBeenCalled()
    expect(warn).not.toHaveBeenCalled()
    expect(reLaunch).toHaveBeenCalledWith('/pages/account/account')
  })
})
