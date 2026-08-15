/**
 * useChatMode - shared state for chat mode selection.
 *
 * Three modes:
 * - single: one model, SSE streaming
 * - compare: N models side-by-side, independent SSE streams
 * - team: N models consensus debate engine
 *
 * compare and team share the same multiModelIds list.
 */
import { ref, computed, type Ref, type ComputedRef, type InjectionKey, inject, provide } from 'vue'

export type ChatMode = 'single' | 'compare' | 'team'

export interface ChatModeState {
  chatMode: Ref<ChatMode>
  /** shared model list for compare and team modes (2+) */
  multiModelIds: Ref<string[]>
  captainMode: Ref<'auto' | 'fixed_first'>
  isTeamMode: ComputedRef<boolean>
  isCompareMode: ComputedRef<boolean>
  isSingleMode: ComputedRef<boolean>
}

export const CHAT_MODE_KEY: InjectionKey<ChatModeState> = Symbol('chatMode')

function createState(): ChatModeState {
  const chatMode = ref<ChatMode>('single')
  const multiModelIds = ref<string[]>([])
  const captainMode = ref<'auto' | 'fixed_first'>('auto')
  const isTeamMode = computed(() => chatMode.value === 'team')
  const isCompareMode = computed(() => chatMode.value === 'compare')
  const isSingleMode = computed(() => chatMode.value === 'single')
  return { chatMode, multiModelIds, captainMode, isTeamMode, isCompareMode, isSingleMode }
}

export function provideChatMode(): ChatModeState {
  const state = createState()
  provide(CHAT_MODE_KEY, state)
  return state
}

export function useChatMode(): ChatModeState {
  const state = inject(CHAT_MODE_KEY)
  if (!state) return createState()
  return state
}

// -- ChatConfigSheet visibility (provide/inject) --
const CHAT_CONFIG_SHEET_KEY: InjectionKey<Ref<boolean>> = Symbol('chatConfigSheet')

export function provideChatConfigSheet() {
  const visible = ref(false)
  provide(CHAT_CONFIG_SHEET_KEY, visible)
  return visible
}

export function useChatConfigSheet() {
  return inject(CHAT_CONFIG_SHEET_KEY, ref(false))
}

// -- Chat input blur signal (provide/inject) --
// Used to coordinate "hide keyboard / blur input" actions from top-level UI (e.g. AppTopbar)
// without directly coupling layout components to page refs.
const CHAT_INPUT_BLUR_SIGNAL_KEY: InjectionKey<Ref<number>> = Symbol('chatInputBlurSignal')

export function provideChatInputBlurSignal() {
  const signal = ref(0)
  provide(CHAT_INPUT_BLUR_SIGNAL_KEY, signal)
  return signal
}

export function useChatInputBlurSignal() {
  return inject(CHAT_INPUT_BLUR_SIGNAL_KEY, ref(0))
}
