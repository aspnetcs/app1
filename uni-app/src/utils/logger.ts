// Minimal logger wrapper to avoid noisy console output in production builds.
// Use `import.meta.env.DEV` (Vite) when available.

/* eslint-disable no-console */

const isDev = (() => {
  try {
    return !!import.meta.env?.DEV
  } catch {
    return false
  }
})()

export const logger = {
  log: (...args: unknown[]) => {
    if (isDev) console.log(...args)
  },
  warn: (...args: unknown[]) => {
    if (isDev) console.warn(...args)
  },
  error: (...args: unknown[]) => {
    if (isDev) console.error(...args)
  },
}

