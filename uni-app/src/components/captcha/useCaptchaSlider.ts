import { getCurrentInstance, onBeforeUnmount, type Ref } from 'vue'

type SliderTrack = { x: number; y: number }
type SliderMetrics = {
  trackWidth: number
  imageWrapWidth: number
  thumbWidth: number
  pieceWidthPx: number
}

type UseCaptchaSliderOptions = {
  sliderLeft: Ref<number>
  sliderPieceX: Ref<number>
  fillScale: Ref<number>
  ensureMeasured: () => void
  getMetrics: () => SliderMetrics
  onSubmit: (payload: { movePercent: number; tracks: SliderTrack[]; duration: number }) => void
  onTooFast: () => void
}

type PointerLike = {
  clientX?: number
  clientY?: number
}

type SliderPointerEvent = PointerLike & {
  touches?: ArrayLike<PointerLike> | null
}

export function useCaptchaSlider(options: UseCaptchaSliderOptions) {
  const { sliderLeft, sliderPieceX, fillScale, ensureMeasured, getMetrics, onSubmit, onTooFast } = options

  let sliderStartX = 0
  let slideStartedAt = 0
  let slideLastTrackAt = 0
  let slideLastY = 0
  let slideTracks: SliderTrack[] = []

  function getPointerXY(event: SliderPointerEvent): { clientX: number; clientY: number } | null {
    if (event.touches && event.touches.length > 0) {
      const touch = event.touches[0]
      if (typeof touch?.clientX === 'number' && typeof touch.clientY === 'number') {
        return { clientX: touch.clientX, clientY: touch.clientY }
      }
    }
    if (typeof event.clientX === 'number' && typeof event.clientY === 'number') {
      return { clientX: event.clientX, clientY: event.clientY }
    }
    return null
  }

  function resetSliderSession() {
    sliderLeft.value = 0
    sliderPieceX.value = 0
    fillScale.value = 0
    slideStartedAt = 0
    slideLastTrackAt = 0
    slideLastY = 0
    slideTracks = []
  }

  function onSliderStart(event: SliderPointerEvent) {
    const pt = getPointerXY(event)
    if (!pt) return
    sliderStartX = pt.clientX
    slideStartedAt = Date.now()
    slideLastTrackAt = slideStartedAt
    slideLastY = pt.clientY
    slideTracks = [{ x: 0, y: Math.round(slideLastY) }]
    ensureMeasured()
  }

  function onSliderMove(event: SliderPointerEvent) {
    const { trackWidth, imageWrapWidth, thumbWidth, pieceWidthPx } = getMetrics()
    if (!trackWidth) return
    const pt = getPointerXY(event)
    if (!pt) return
    const dx = pt.clientX - sliderStartX
    slideLastY = pt.clientY
    const thumbW = thumbWidth || 40
    const maxLeft = trackWidth - thumbW
    const left = Math.min(Math.max(0, dx), maxLeft)
    sliderLeft.value = left
    fillScale.value = left / maxLeft
    const pieceW = pieceWidthPx || imageWrapWidth * 0.185
    sliderPieceX.value = (left / maxLeft) * (imageWrapWidth - pieceW)

    const now = Date.now()
    if (slideTracks.length < 10 && now - slideLastTrackAt >= 40) {
      slideTracks.push({ x: Math.round(left), y: Math.round(slideLastY) })
      slideLastTrackAt = now
    }
  }

  function onSliderEnd() {
    cleanupMouseListeners()
    const { trackWidth, imageWrapWidth, thumbWidth, pieceWidthPx } = getMetrics()
    if (!trackWidth || !imageWrapWidth) return
    const duration = slideStartedAt ? Date.now() - slideStartedAt : 0
    if (duration < 200) {
      onTooFast()
      resetSliderSession()
      return
    }

    const thumbW = thumbWidth || 40
    const maxLeft = trackWidth - thumbW
    const pieceW = pieceWidthPx || imageWrapWidth * 0.185
    const piecePosX = (sliderLeft.value / maxLeft) * (imageWrapWidth - pieceW)
    const movePercent = (piecePosX + pieceW / 2) / imageWrapWidth

    if (slideTracks.length < 10) {
      slideTracks.push({ x: Math.round(sliderLeft.value), y: Math.round(slideLastY) })
    }
    while (slideTracks.length < 5) {
      const last = slideTracks[slideTracks.length - 1] || { x: 0, y: Math.round(slideLastY) }
      slideTracks.push({ x: last.x + 1, y: last.y })
    }

    onSubmit({ movePercent, tracks: slideTracks, duration })
  }

  const noopMouseDown = (_event: MouseEvent) => {}
  let cleanupMouseListeners = () => {}
  let onMouseDown = noopMouseDown

  // #ifdef H5
  function onDocMouseMove(e: MouseEvent) {
    e.preventDefault()
    onSliderMove(e)
  }

  function onDocMouseUp() {
    onSliderEnd()
  }

  cleanupMouseListeners = () => {
    if (typeof document !== 'undefined') {
      document.removeEventListener('mousemove', onDocMouseMove)
      document.removeEventListener('mouseup', onDocMouseUp)
    }
  }

  onMouseDown = (e: MouseEvent) => {
    onSliderStart(e)
    if (typeof document !== 'undefined') {
      document.addEventListener('mousemove', onDocMouseMove)
      document.addEventListener('mouseup', onDocMouseUp)
    }
  }
  // #endif

  if (getCurrentInstance()) {
    onBeforeUnmount(() => {
      cleanupMouseListeners()
    })
  }

  return {
    onSliderStart,
    onSliderMove,
    onSliderEnd,
    onMouseDown,
    resetSliderSession,
  }
}
