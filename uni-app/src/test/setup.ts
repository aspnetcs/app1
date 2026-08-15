import { afterEach } from 'vitest'

afterEach(() => {
  delete (globalThis as Record<string, unknown>).uni
  delete (globalThis as Record<string, unknown>).getCurrentPages
})
