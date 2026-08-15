export interface EntryFlowOptions {
  ensureAuth: () => Promise<void>
  isLoggedIn: () => boolean
  guestAuthEnabled: boolean
  ensureGuestSession: () => Promise<unknown>
  resolveNextUrl?: () => Promise<string>
  reLaunch: (url: string) => void
  showToast: (options: { title: string; icon: 'none' }) => void
  warn: (message: string, error: unknown) => void
}

const CHAT_ENTRY_URL = '/chat/pages/index/index'
const LOGIN_ENTRY_URL = '/pages/account/account'
const GUEST_AUTH_FAILURE_MESSAGE = '\u8bbf\u5ba2\u6a21\u5f0f\u6682\u4e0d\u53ef\u7528\uff0c\u8bf7\u5148\u767b\u5f55'

export async function handleEntryPageMount(options: EntryFlowOptions) {
  await options.ensureAuth()

  if (!options.isLoggedIn()) {
    if (options.guestAuthEnabled) {
      try {
        await options.ensureGuestSession()
      } catch (error) {
        options.warn('[guest-auth] issue guest session failed', error)
        options.showToast({ title: GUEST_AUTH_FAILURE_MESSAGE, icon: 'none' })
        options.reLaunch(LOGIN_ENTRY_URL)
        return
      }
    } else {
      options.reLaunch(LOGIN_ENTRY_URL)
      return
    }
  }

  const nextUrl = options.resolveNextUrl ? await options.resolveNextUrl() : CHAT_ENTRY_URL
  options.reLaunch(nextUrl || CHAT_ENTRY_URL)
}
