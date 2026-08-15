import { describe, expect, it } from 'vitest'
import { resolveSafeAreaInsets } from './app'

describe('resolveSafeAreaInsets', () => {
  it('uses explicit safeAreaInsets when present', () => {
    expect(
      resolveSafeAreaInsets({
        statusBarHeight: 20,
        safeAreaInsets: {
          top: 24,
          bottom: 16,
        },
      }),
    ).toEqual({
      top: 24,
      bottom: 16,
    })
  })

  it('derives the bottom inset from safeArea and screen height', () => {
    expect(
      resolveSafeAreaInsets({
        statusBarHeight: 20,
        screenHeight: 844,
        safeArea: {
          top: 47,
          bottom: 810,
        },
      }),
    ).toEqual({
      top: 47,
      bottom: 34,
    })
  })

  it('falls back to status bar height when safe area data is unavailable', () => {
    expect(
      resolveSafeAreaInsets({
        statusBarHeight: 28,
      }),
    ).toEqual({
      top: 28,
      bottom: 0,
    })
  })

  it('ignores obviously invalid safeArea.bottom values', () => {
    expect(
      resolveSafeAreaInsets({
        statusBarHeight: 20,
        screenHeight: 844,
        safeArea: {
          top: 47,
          bottom: 0,
        },
      }),
    ).toEqual({
      top: 47,
      bottom: 0,
    })
  })
})
