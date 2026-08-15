<template>
  <view class="captcha-slider-mode">
    <text class="captcha-hint">请拖动滑块完成拼图</text>
    <view id="sliderImageWrap" class="captcha-slider-image-wrap">
      <image class="captcha-slider-bg" :src="bgImage" mode="widthFix" />
      <view
        id="sliderPiece"
        class="captcha-slider-piece"
        :style="{ transform: `translate3d(${sliderPieceX}px, 0, 0)`, top: pieceYStyle }"
      >
        <image class="captcha-slider-piece-img" :src="sliderImage" mode="aspectFit" />
      </view>
    </view>
    <view
      id="sliderTrack"
      class="captcha-slider-track"
      @touchstart="$emit('slider-start', $event)"
      @touchmove.stop.prevent="$emit('slider-move', $event)"
      @touchend="$emit('slider-end')"
      @mousedown.prevent="$emit('mouse-down', $event)"
    >
      <view class="captcha-slider-fill" :style="`transform:scaleX(${fillScale})`"></view>
      <view
        id="sliderThumb"
        class="captcha-slider-thumb"
        :style="`transform:translate3d(${sliderLeft}px,0,0)`"
      >
        <view class="captcha-slider-arrow">
          <!-- #ifdef MP-WEIXIN -->
          <MpShapeIcon name="chevron-right" :size="16" color="currentColor" :stroke-width="2.5" />
          <!-- #endif -->
          <!-- #ifndef MP-WEIXIN -->
          <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
            <path d="M9 18l6-6-6-6"/>
          </svg>
          <!-- #endif -->
        </view>
      </view>
      <text v-if="sliderLeft < 5" class="captcha-slider-text">向右滑动验证</text>
    </view>
  </view>
</template>

<script setup lang="ts">
// #ifdef MP-WEIXIN
import MpShapeIcon from '@/components/icons/MpShapeIcon.vue'
// #endif

defineProps<{
  bgImage: string
  sliderImage: string
  sliderPieceX: number
  pieceYStyle: string
  fillScale: number
  sliderLeft: number
}>()

defineEmits<{
  (e: 'slider-start', event: TouchEvent): void
  (e: 'slider-move', event: TouchEvent): void
  (e: 'slider-end'): void
  (e: 'mouse-down', event: MouseEvent): void
}>()
</script>

<style scoped>
.captcha-hint {
  display: block;
  font-size: 13px;
  color: #666;
  margin-bottom: 10px;
  text-align: center;
}

.captcha-slider-image-wrap {
  position: relative;
  width: 100%;
  overflow: hidden;
  border-radius: 8px;
  margin-bottom: 10px;
}

.captcha-slider-bg {
  width: 100%;
  display: block;
}

.captcha-slider-piece {
  position: absolute;
  left: 0;
  width: 18.5%;
  padding-bottom: 18.5%;
  z-index: 2;
  pointer-events: none;
  will-change: transform;
  backface-visibility: hidden;
}

.captcha-slider-piece-img {
  position: absolute;
  left: 0;
  top: 0;
  width: 100%;
  height: 100%;
}

.captcha-slider-track {
  position: relative;
  width: 100%;
  height: 40px;
  background: #f0f0f0;
  border-radius: 20px;
  overflow: hidden;
  user-select: none;
  -webkit-user-select: none;
  cursor: grab;
}

.captcha-slider-fill {
  position: absolute;
  left: 0;
  top: 0;
  width: 100%;
  height: 100%;
  background: linear-gradient(90deg, #4c7cf6, #6a8eff);
  border-radius: 20px;
  transform-origin: left;
  opacity: 0.35;
}

.captcha-slider-thumb {
  position: absolute;
  top: 2px;
  left: 0;
  width: 36px;
  height: 36px;
  background: #fff;
  border-radius: 50%;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.18);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 2;
}

.captcha-slider-arrow {
  display: flex;
  align-items: center;
  justify-content: center;
  color: #4c7cf6;
}

.captcha-slider-text {
  position: absolute;
  width: 100%;
  text-align: center;
  line-height: 40px;
  font-size: 13px;
  color: #999;
}
</style>
