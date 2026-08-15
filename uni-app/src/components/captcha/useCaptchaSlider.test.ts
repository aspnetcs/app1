import * as Vue from 'vue'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { useCaptchaSlider } from './useCaptchaSlider'

describe('useCaptchaSlider', () => {
  const warnSpy = vi.spyOn(console, 'warn').mockImplementation(() => undefined)

  beforeEach(() => {
    warnSpy.mockClear()
  })

  afterEach(() => {
    warnSpy.mockReset()
  })

  it('tracks pointer movement and submits normalized payload on touch end', () => {
    const sliderLeft = Vue.ref(0)
    const sliderPieceX = Vue.ref(0)
    const fillScale = Vue.ref(0)
    const onSubmit = vi.fn()
    const onTooFast = vi.fn()

    const originalNow = Date.now
    let now = 1000
    Date.now = () => now

    const slider = useCaptchaSlider({
      sliderLeft,
      sliderPieceX,
      fillScale,
      ensureMeasured: vi.fn(),
      getMetrics: () => ({
        trackWidth: 200,
        imageWrapWidth: 300,
        thumbWidth: 40,
        pieceWidthPx: 60,
      }),
      onSubmit,
      onTooFast,
    })

    slider.onSliderStart({
      clientX: 10,
      clientY: 20,
    })

    now = 1300
    slider.onSliderMove({
      clientX: 90,
      clientY: 24,
    })
    slider.onSliderEnd()

    expect(sliderLeft.value).toBe(80)
    expect(fillScale.value).toBeCloseTo(0.5)
    expect(sliderPieceX.value).toBeCloseTo(120)
    expect(onTooFast).not.toHaveBeenCalled()
    expect(onSubmit).toHaveBeenCalledWith({
      movePercent: 0.5,
      tracks: expect.any(Array),
      duration: 300,
    })
    Date.now = originalNow
  })

  it('resets the session when sliding too fast', () => {
    const sliderLeft = Vue.ref(0)
    const sliderPieceX = Vue.ref(0)
    const fillScale = Vue.ref(0)
    const onSubmit = vi.fn()
    const onTooFast = vi.fn()

    const originalNow = Date.now
    let now = 2000
    Date.now = () => now

    const slider = useCaptchaSlider({
      sliderLeft,
      sliderPieceX,
      fillScale,
      ensureMeasured: vi.fn(),
      getMetrics: () => ({
        trackWidth: 200,
        imageWrapWidth: 300,
        thumbWidth: 40,
        pieceWidthPx: 60,
      }),
      onSubmit,
      onTooFast,
    })

    slider.onSliderStart({
      clientX: 10,
      clientY: 20,
    })

    now = 2100
    slider.onSliderMove({
      clientX: 70,
      clientY: 25,
    })
    slider.onSliderEnd()

    expect(onTooFast).toHaveBeenCalledTimes(1)
    expect(onSubmit).not.toHaveBeenCalled()
    expect(sliderLeft.value).toBe(0)
    expect(sliderPieceX.value).toBe(0)
    expect(fillScale.value).toBe(0)

    Date.now = originalNow
  })
})
