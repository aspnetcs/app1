<template>
  <CaptchaModalShell
    :visible="visible"
    :show-anim="showAnim"
    :refreshing="refreshing"
    :loading="firstLoad || refreshing"
    @close="close"
    @refresh="onRefresh"
  >
    <SliderCaptchaPanel
      v-if="mode === 'slider'"
      :bg-image="bgImage"
      :slider-image="sliderImage"
      :slider-piece-x="sliderPieceX"
      :piece-y-style="pieceYStyle"
      :fill-scale="fillScale"
      :slider-left="sliderLeft"
      @slider-start="onSliderStart"
      @slider-move="onSliderMove"
      @slider-end="onSliderEnd"
      @mouse-down="onMouseDown"
    />

    <MathCaptchaPanel
      v-else-if="mode === 'math'"
      :math-image="mathImage"
      :math-question="mathQuestion"
      :math-answer="mathAnswer"
      :is-submitting="isSubmitting"
      @update:math-answer="mathAnswer = $event"
      @confirm="onMathConfirm"
    />

    <TextCaptchaPanel
      v-else-if="mode === 'text'"
      :bg-image="bgImage"
      :target-chars-arr="targetCharsArr"
      :clicked-points="clickedPoints"
      :is-submitting="isSubmitting"
      @touch="onTextTouch"
      @remove-point="onRemovePoint"
      @confirm="onTextConfirm"
    />
  </CaptchaModalShell>
</template>

<script setup lang="ts">
import { computed, getCurrentInstance, nextTick, ref } from 'vue'
import { generateCaptcha, verifyCaptcha } from '@/api/risk'
import type { CaptchaData, CaptchaType, CaptchaVerifyData } from '@/api/types'
import CaptchaModalShell from './CaptchaModalShell.vue'
import MathCaptchaPanel from './MathCaptchaPanel.vue'
import SliderCaptchaPanel from './SliderCaptchaPanel.vue'
import TextCaptchaPanel from './TextCaptchaPanel.vue'
import type { CaptchaClickPoint } from './captchaTypes'
import { useCaptchaSlider } from './useCaptchaSlider'
import { useCaptchaTempFiles } from './useCaptchaTempFiles'
import { extractErrorMessage } from '@/utils/errorMessage'

const instance = getCurrentInstance()

type BoundingRect = {
  width?: number
  height?: number
  left?: number
  top?: number
} | null

type SliderMeasureRects = [
  BoundingRect?,
  BoundingRect?,
  BoundingRect?,
  BoundingRect?,
]

type TextTouchPoint = {
  clientX?: number
  clientY?: number
  x?: number
  y?: number
}

type TextTouchEventLike = TextTouchPoint & {
  detail?: TextTouchPoint | null
  touches?: ArrayLike<TextTouchPoint> | null
}

function normalizeBoundingRect(
  result: UniApp.NodeInfo | UniApp.NodeInfo[] | null | undefined,
): BoundingRect {
  if (!result || Array.isArray(result)) return null
  return {
    width: typeof result.width === 'number' ? result.width : undefined,
    height: typeof result.height === 'number' ? result.height : undefined,
    left: typeof result.left === 'number' ? result.left : undefined,
    top: typeof result.top === 'number' ? result.top : undefined,
  }
}

const emit = defineEmits<{
  (e: 'success', token: string): void
  (e: 'close'): void
}>()

const visible = ref(false)
const showAnim = ref(false)
const firstLoad = ref(true)
const refreshing = ref(false)
const isSubmitting = ref(false)

const mode = ref<CaptchaType>('math')
const captchaId = ref('')

const mathImage = ref('')
const mathQuestion = ref('')
const mathAnswer = ref('')

const bgImage = ref('')
const sliderImage = ref('')
const pieceY = ref(0)
const sliderLeft = ref(0)
const sliderPieceX = ref(0)
const fillScale = ref(0)
let trackWidth = 0
let imageWrapWidth = 0
let thumbWidth = 40
let pieceWidthPx = 0

const targetCharsArr = ref<string[]>([])
const clickedPoints = ref<CaptchaClickPoint[]>([])
let textImageWidth = 0
let textImageHeight = 0

const pieceYStyle = computed(() => `${(pieceY.value * 100).toFixed(1)}%`)

const { cleanupTempFiles, resolveDataUrlToTempFile } = useCaptchaTempFiles()
const {
  onSliderStart: handleSliderStart,
  onSliderMove: handleSliderMove,
  onSliderEnd: handleSliderEnd,
  onMouseDown: handleSliderMouseDown,
  resetSliderSession,
} = useCaptchaSlider({
  sliderLeft,
  sliderPieceX,
  fillScale,
  ensureMeasured: () => {
    if (!trackWidth || !imageWrapWidth) {
      measureImageWrap()
    }
  },
  getMetrics: () => ({
    trackWidth,
    imageWrapWidth,
    thumbWidth,
    pieceWidthPx,
  }),
  onSubmit: (payload) => {
    submitAnswer(payload)
  },
  onTooFast: () => {
    uni.showToast({ title: '滑动太快，请重试', icon: 'none' })
  },
})

async function open() {
  visible.value = true
  firstLoad.value = true
  showAnim.value = false
  await nextTick()
  showAnim.value = true
  await fetchCaptcha()
}

function close() {
  visible.value = false
  showAnim.value = false
  cleanupTempFiles()
  emit('close')
}

async function onRefresh() {
  if (refreshing.value) return
  await fetchCaptcha()
}

async function fetchCaptcha() {
  refreshing.value = true
  cleanupTempFiles()
  resetState()
  try {
    const res = await generateCaptcha()
    const data: CaptchaData = res.data
    captchaId.value = data.captchaId
    mode.value = data.type

    if (data.type === 'math') {
      const image = data.mathImage || ''
      mathQuestion.value = (data.question || '').trim()
      mathImage.value =
        image && image.startsWith('data:image/')
          ? await resolveDataUrlToTempFile(image, 'math')
          : ''
      return
    }

    if (data.type === 'slider') {
      bgImage.value = await resolveDataUrlToTempFile(data.backgroundImage, 'bg')
      sliderImage.value = await resolveDataUrlToTempFile(data.sliderImage, 'piece')
      pieceY.value = data.pieceY || 0
      return
    }

    bgImage.value = await resolveDataUrlToTempFile(data.backgroundImage, 'bg')
    targetCharsArr.value = data.targetChars || []
  } catch {
    uni.showToast({ title: '获取验证码失败', icon: 'none' })
  } finally {
    firstLoad.value = false
    refreshing.value = false
    await nextTick()
    measureImageWrap()
  }
}

function resetState() {
  mathAnswer.value = ''
  mathQuestion.value = ''
  mathImage.value = ''
  bgImage.value = ''
  sliderImage.value = ''
  targetCharsArr.value = []
  clickedPoints.value = []
  resetSliderSession()
}

function measureImageWrap() {
  if (mode.value === 'slider') {
    const query = uni.createSelectorQuery().in(instance)
    query.select('#sliderImageWrap').boundingClientRect()
    query.select('#sliderTrack').boundingClientRect()
    query.select('#sliderThumb').boundingClientRect()
    query.select('#sliderPiece').boundingClientRect()
    query.exec((rects) => {
      const normalized = (Array.isArray(rects) ? rects : []).map((rect) => normalizeBoundingRect(rect)) as SliderMeasureRects
      const wrap = normalized?.[0]
      const track = normalized?.[1]
      const thumb = normalized?.[2]
      const piece = normalized?.[3]
      if (wrap?.width) imageWrapWidth = wrap.width
      if (track?.width) trackWidth = track.width
      if (thumb?.width) thumbWidth = thumb.width
      if (piece?.width) pieceWidthPx = piece.width
    })
    return
  }

  if (mode.value === 'text') {
    const query = uni.createSelectorQuery().in(instance)
    query
      .select('#textImageWrap')
      .boundingClientRect((result) => {
        const rect = normalizeBoundingRect(result)
        if (!rect) return
        textImageWidth = Number(rect.width || 0)
        textImageHeight = Number(rect.height || 0)
      })
      .exec()
  }
}

function onSliderStart(event: TouchEvent) {
  handleSliderStart(event)
}

function onSliderMove(event: TouchEvent) {
  handleSliderMove(event)
}

function onSliderEnd() {
  handleSliderEnd()
}

function onMouseDown(event: MouseEvent) {
  handleSliderMouseDown(event)
}

function onMathConfirm() {
  if (!mathAnswer.value || isSubmitting.value) return
  submitAnswer({ answer: Number(mathAnswer.value) })
}

function onTextTouch(event: Event) {
  if (clickedPoints.value.length >= targetCharsArr.value.length) return

  const touchEvent = event as unknown as TextTouchEventLike
  const touch = touchEvent.detail || touchEvent.touches?.[0] || touchEvent
  let offsetX = 0
  let offsetY = 0

  const query = uni.createSelectorQuery().in(instance)
  query
    .select('#textImageWrap')
    .boundingClientRect((result) => {
      const rect = normalizeBoundingRect(result)
      if (!rect) return
      textImageWidth = Number(rect.width || 0)
      textImageHeight = Number(rect.height || 0)

      if (typeof touch.clientX === 'number' && typeof touch.clientY === 'number') {
        offsetX = touch.clientX - Number(rect.left || 0)
        offsetY = touch.clientY - Number(rect.top || 0)
      } else if (typeof touch.x === 'number' && typeof touch.y === 'number') {
        offsetX = touch.x - Number(rect.left || 0)
        offsetY = touch.y - Number(rect.top || 0)
      } else {
        return
      }

      clickedPoints.value.push({
        displayX: offsetX,
        displayY: offsetY,
        px: offsetX / textImageWidth,
        py: offsetY / textImageHeight,
      })
    })
    .exec()
}

function onRemovePoint(index: number) {
  clickedPoints.value.splice(index, 1)
}

function onTextConfirm() {
  if (clickedPoints.value.length < targetCharsArr.value.length || isSubmitting.value) return
  const points = clickedPoints.value.map((point) => ({ px: point.px, py: point.py }))
  submitAnswer({ points })
}

async function submitAnswer(data: CaptchaVerifyData) {
  if (isSubmitting.value) return
  if (!captchaId.value) {
    uni.showToast({ title: '验证码未加载，请重试', icon: 'none' })
    await fetchCaptcha()
    return
  }

  isSubmitting.value = true
  try {
    const res = await verifyCaptcha({ captchaId: captchaId.value, data })
    visible.value = false
    emit('success', res.data.token)
  } catch (error: unknown) {
    uni.showToast({ title: extractErrorMessage(error, '验证失败'), icon: 'none' })
    await fetchCaptcha()
  } finally {
    isSubmitting.value = false
  }
}

defineExpose({ open })
</script>
