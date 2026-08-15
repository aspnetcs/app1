import { describe, expect, it, vi } from 'vitest'
import { createCompatActionRunner } from './h5EventCompat'

describe('createCompatActionRunner', () => {
  it('dedupes duplicated same-event dispatches for one gesture', () => {
    const runner = createCompatActionRunner()
    const action = vi.fn()
    const event = { type: 'click', timeStamp: 120 }

    runner('sidebar-toggle', event, action)
    runner('sidebar-toggle', event, action)

    expect(action).toHaveBeenCalledTimes(1)
  })

  it('dedupes cross-type tap followed by click within the compat window', () => {
    const runner = createCompatActionRunner()
    const action = vi.fn()

    vi.useFakeTimers()
    vi.setSystemTime(1000)

    runner('mode-trigger', { type: 'tap', timeStamp: 10 }, action)
    vi.setSystemTime(1120)
    runner('mode-trigger', { type: 'click', timeStamp: 130 }, action)

    expect(action).toHaveBeenCalledTimes(1)

    vi.useRealTimers()
  })

  it('keeps rapid legitimate repeated clicks working', () => {
    const runner = createCompatActionRunner()
    const action = vi.fn()

    vi.useFakeTimers()
    vi.setSystemTime(2000)

    runner('sidebar-toggle', { type: 'click', timeStamp: 10 }, action)
    vi.setSystemTime(2090)
    runner('sidebar-toggle', { type: 'click', timeStamp: 100 }, action)

    expect(action).toHaveBeenCalledTimes(2)

    vi.useRealTimers()
  })

  it('does not mix independent action keys', () => {
    const runner = createCompatActionRunner()
    const action = vi.fn()

    runner('model-selector', { type: 'click', timeStamp: 10 }, action)
    runner('mode-selector', { type: 'click', timeStamp: 10 }, action)

    expect(action).toHaveBeenCalledTimes(2)
  })
})
