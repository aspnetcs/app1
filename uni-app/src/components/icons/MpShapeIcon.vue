<script setup lang="ts">
import { computed } from 'vue'

const props = withDefaults(
  defineProps<{
    name:
      | 'archive'
      | 'book'
      | 'box'
      | 'camera'
      | 'chat'
      | 'chevron-left'
      | 'chevron-down'
      | 'chevron-right'
      | 'check'
      | 'close'
      | 'clock'
      | 'copy'
      | 'crop'
      | 'edit'
      | 'image'
      | 'menu'
      | 'minus'
      | 'more'
      | 'plus'
      | 'refresh'
      | 'search'
      | 'send'
      | 'settings'
      | 'stop'
      | 'user'
    size?: number | string
    color?: string
    strokeWidth?: number | string
  }>(),
  {
    size: 18,
    color: 'currentColor',
    strokeWidth: 2,
  },
)

const resolvedSize = computed(() => (typeof props.size === 'number' ? `${props.size}px` : props.size))
const resolvedStrokeWidth = computed(() =>
  typeof props.strokeWidth === 'number' ? `${props.strokeWidth}px` : props.strokeWidth,
)
const wrapperStyle = computed(
  () =>
    `width:${resolvedSize.value};height:${resolvedSize.value};--icon-color:${props.color};--icon-stroke:${resolvedStrokeWidth.value};`,
)
</script>

<template>
  <view class="mp-shape-icon" :class="`mp-shape-icon-${name}`" :style="wrapperStyle">
    <template v-if="name === 'archive'">
      <view class="mp-shape-box mp-archive-lid"></view>
      <view class="mp-shape-box mp-archive-body"></view>
      <view class="mp-shape-stroke mp-archive-line"></view>
    </template>

    <template v-else-if="name === 'box'">
      <view class="mp-shape-box mp-box-outline"></view>
      <view class="mp-shape-stroke mp-box-top"></view>
      <view class="mp-shape-stroke mp-box-center"></view>
    </template>

    <template v-else-if="name === 'camera'">
      <view class="mp-shape-box mp-camera-body"></view>
      <view class="mp-shape-circle mp-camera-lens"></view>
      <view class="mp-shape-fill mp-camera-top"></view>
    </template>

    <template v-else-if="name === 'chat'">
      <view class="mp-shape-box mp-chat-bubble"></view>
      <view class="mp-chat-tail"></view>
    </template>

    <template v-else-if="name === 'chevron-left'">
      <view class="mp-shape-stroke mp-chevron-left-top"></view>
      <view class="mp-shape-stroke mp-chevron-left-bottom"></view>
    </template>

    <template v-else-if="name === 'chevron-down'">
      <view class="mp-shape-stroke mp-chevron-down-left"></view>
      <view class="mp-shape-stroke mp-chevron-down-right"></view>
    </template>

    <template v-else-if="name === 'chevron-right'">
      <view class="mp-shape-stroke mp-chevron-right-top"></view>
      <view class="mp-shape-stroke mp-chevron-right-bottom"></view>
    </template>

    <template v-else-if="name === 'check'">
      <view class="mp-shape-stroke mp-check-left"></view>
      <view class="mp-shape-stroke mp-check-right"></view>
    </template>

    <template v-else-if="name === 'close'">
      <view class="mp-shape-stroke mp-close-left"></view>
      <view class="mp-shape-stroke mp-close-right"></view>
    </template>

    <template v-else-if="name === 'clock'">
      <view class="mp-shape-circle mp-clock-ring"></view>
      <view class="mp-shape-stroke mp-clock-hand-hour"></view>
      <view class="mp-shape-stroke mp-clock-hand-minute"></view>
    </template>

    <template v-else-if="name === 'copy'">
      <view class="mp-shape-box mp-copy-front"></view>
      <view class="mp-shape-box mp-copy-back"></view>
    </template>

    <template v-else-if="name === 'crop'">
      <view class="mp-shape-corner mp-crop-top-left"></view>
      <view class="mp-shape-corner mp-crop-top-right"></view>
      <view class="mp-shape-corner mp-crop-bottom-left"></view>
      <view class="mp-shape-corner mp-crop-bottom-right"></view>
    </template>

    <template v-else-if="name === 'edit'">
      <view class="mp-shape-stroke mp-edit-body"></view>
      <view class="mp-shape-box mp-edit-tip"></view>
    </template>

    <template v-else-if="name === 'image'">
      <view class="mp-shape-box mp-image-frame"></view>
      <view class="mp-shape-fill mp-image-sun"></view>
      <view class="mp-shape-stroke mp-image-slope"></view>
    </template>

    <template v-else-if="name === 'book'">
      <view class="mp-shape-box mp-book-left"></view>
      <view class="mp-shape-box mp-book-right"></view>
      <view class="mp-shape-stroke mp-book-spine"></view>
    </template>

    <template v-else-if="name === 'menu'">
      <view class="mp-shape-stroke mp-menu-line mp-menu-line-top"></view>
      <view class="mp-shape-stroke mp-menu-line mp-menu-line-middle"></view>
      <view class="mp-shape-stroke mp-menu-line mp-menu-line-bottom"></view>
    </template>

    <template v-else-if="name === 'minus'">
      <view class="mp-shape-stroke mp-minus-line"></view>
    </template>

    <template v-else-if="name === 'more'">
      <view class="mp-shape-fill mp-dot mp-dot-left"></view>
      <view class="mp-shape-fill mp-dot mp-dot-center"></view>
      <view class="mp-shape-fill mp-dot mp-dot-right"></view>
    </template>

    <template v-else-if="name === 'plus'">
      <view class="mp-shape-stroke mp-plus-horizontal"></view>
      <view class="mp-shape-stroke mp-plus-vertical"></view>
    </template>

    <template v-else-if="name === 'refresh'">
      <view class="mp-shape-circle mp-refresh-ring"></view>
      <view class="mp-shape-stroke mp-refresh-arrow-a"></view>
      <view class="mp-shape-stroke mp-refresh-arrow-b"></view>
    </template>

    <template v-else-if="name === 'search'">
      <view class="mp-shape-circle mp-search-ring"></view>
      <view class="mp-shape-stroke mp-search-handle"></view>
    </template>

    <template v-else-if="name === 'send'">
      <view class="mp-shape-stroke mp-send-line"></view>
      <view class="mp-shape-stroke mp-send-left"></view>
      <view class="mp-shape-stroke mp-send-right"></view>
    </template>

    <template v-else-if="name === 'settings'">
      <view class="mp-shape-circle mp-settings-ring"></view>
      <view class="mp-shape-fill mp-settings-dot"></view>
      <view class="mp-shape-stroke mp-settings-spoke-top"></view>
      <view class="mp-shape-stroke mp-settings-spoke-right"></view>
      <view class="mp-shape-stroke mp-settings-spoke-bottom"></view>
      <view class="mp-shape-stroke mp-settings-spoke-left"></view>
    </template>

    <template v-else-if="name === 'stop'">
      <view class="mp-shape-fill mp-stop-box"></view>
    </template>

    <template v-else-if="name === 'user'">
      <view class="mp-shape-circle mp-user-head"></view>
      <view class="mp-shape-box mp-user-body"></view>
    </template>
  </view>
</template>

<style scoped>
.mp-shape-icon {
  position: relative;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  box-sizing: border-box;
  flex-shrink: 0;
}

.mp-shape-stroke,
.mp-shape-fill,
.mp-shape-box,
.mp-shape-circle,
.mp-chat-tail,
.mp-shape-corner {
  position: absolute;
  box-sizing: border-box;
}

.mp-shape-stroke,
.mp-shape-fill {
  background: var(--icon-color);
  border-radius: 999px;
}

.mp-shape-box {
  border: var(--icon-stroke) solid var(--icon-color);
}

.mp-shape-circle {
  border: var(--icon-stroke) solid var(--icon-color);
  border-radius: 999px;
}

.mp-chat-tail {
  width: 20%;
  height: 20%;
  left: 18%;
  top: 68%;
  background: #ffffff;
  border-left: var(--icon-stroke) solid var(--icon-color);
  border-bottom: var(--icon-stroke) solid var(--icon-color);
  transform: rotate(-20deg);
}

.mp-dot {
  width: 14%;
  height: 14%;
  top: 43%;
  border-radius: 999px;
}

.mp-dot-left {
  left: 18%;
}

.mp-check-left {
  width: 28%;
  height: var(--icon-stroke);
  left: 26%;
  top: 55%;
  transform: rotate(45deg);
  transform-origin: left center;
}

.mp-check-right {
  width: 48%;
  height: var(--icon-stroke);
  left: 38%;
  top: 50%;
  transform: rotate(-45deg);
  transform-origin: left center;
}

.mp-dot-center {
  left: 43%;
}

.mp-dot-right {
  left: 68%;
}

.mp-menu-line {
  width: 72%;
  height: var(--icon-stroke);
  left: 14%;
}

.mp-menu-line-top {
  top: 22%;
}

.mp-menu-line-middle {
  top: 47%;
}

.mp-menu-line-bottom {
  top: 72%;
}

.mp-search-ring {
  width: 54%;
  height: 54%;
  top: 12%;
  left: 10%;
}

.mp-search-handle {
  width: 32%;
  height: var(--icon-stroke);
  left: 58%;
  top: 66%;
  transform: rotate(45deg);
}

.mp-edit-body {
  width: 58%;
  height: var(--icon-stroke);
  left: 24%;
  top: 56%;
  transform: rotate(-45deg);
}

.mp-edit-tip {
  width: 20%;
  height: 20%;
  right: 12%;
  top: 12%;
  transform: rotate(45deg);
  border-radius: 3px;
}

.mp-chevron-down-left,
.mp-chevron-down-right,
.mp-chevron-left-top,
.mp-chevron-left-bottom,
.mp-chevron-right-top,
.mp-chevron-right-bottom {
  width: 42%;
  height: var(--icon-stroke);
  top: 52%;
}

.mp-chevron-down-left {
  left: 16%;
  transform: rotate(45deg);
}

.mp-chevron-down-right {
  right: 16%;
  transform: rotate(-45deg);
}

.mp-chevron-left-top {
  right: 34%;
  top: 32%;
  transform: rotate(-45deg);
}

.mp-chevron-left-bottom {
  right: 34%;
  top: 58%;
  transform: rotate(45deg);
}

.mp-chevron-right-top {
  left: 34%;
  top: 32%;
  transform: rotate(45deg);
}

.mp-chevron-right-bottom {
  left: 34%;
  top: 58%;
  transform: rotate(-45deg);
}

.mp-plus-horizontal,
.mp-plus-vertical,
.mp-minus-line,
.mp-send-line {
  left: 50%;
  top: 50%;
  transform: translate(-50%, -50%);
}

.mp-plus-horizontal {
  width: 68%;
  height: var(--icon-stroke);
}

.mp-plus-vertical {
  width: var(--icon-stroke);
  height: 68%;
}

.mp-minus-line {
  width: 68%;
  height: var(--icon-stroke);
}

.mp-send-line {
  width: var(--icon-stroke);
  height: 56%;
  top: 48%;
}

.mp-send-left,
.mp-send-right {
  width: 38%;
  height: var(--icon-stroke);
  top: 24%;
}

.mp-send-left {
  left: 18%;
  transform: rotate(-45deg);
}

.mp-send-right {
  right: 18%;
  transform: rotate(45deg);
}

.mp-stop-box {
  width: 48%;
  height: 48%;
  border-radius: 4px;
}

.mp-close-left,
.mp-close-right {
  width: 72%;
  height: var(--icon-stroke);
  top: 48%;
  left: 14%;
}

.mp-close-left {
  transform: rotate(45deg);
}

.mp-close-right {
  transform: rotate(-45deg);
}

.mp-copy-front,
.mp-copy-back {
  width: 54%;
  height: 54%;
  border-radius: 4px;
}

.mp-copy-front {
  top: 28%;
  left: 34%;
}

.mp-copy-back {
  top: 12%;
  left: 16%;
}

.mp-clock-ring {
  width: 72%;
  height: 72%;
  top: 14%;
  left: 14%;
}

.mp-clock-hand-hour,
.mp-clock-hand-minute {
  left: 50%;
  top: 50%;
  transform-origin: left center;
}

.mp-clock-hand-hour {
  width: 20%;
  height: var(--icon-stroke);
  transform: translateY(-50%) rotate(-90deg);
}

.mp-clock-hand-minute {
  width: 28%;
  height: var(--icon-stroke);
  transform: translateY(-50%) rotate(-35deg);
}

.mp-archive-lid {
  width: 76%;
  height: 22%;
  left: 12%;
  top: 12%;
  border-radius: 4px;
}

.mp-archive-body {
  width: 68%;
  height: 50%;
  left: 16%;
  top: 34%;
  border-radius: 4px;
}

.mp-archive-line {
  width: 24%;
  height: var(--icon-stroke);
  left: 38%;
  top: 54%;
}

.mp-book-left,
.mp-book-right {
  width: 34%;
  height: 64%;
  top: 18%;
  border-radius: 4px;
}

.mp-book-left {
  left: 14%;
  border-right: none;
}

.mp-book-right {
  right: 14%;
  border-left: none;
}

.mp-book-spine {
  width: var(--icon-stroke);
  height: 62%;
  top: 19%;
  left: 50%;
  transform: translateX(-50%);
}

.mp-user-head {
  width: 34%;
  height: 34%;
  top: 10%;
  left: 33%;
}

.mp-user-body {
  width: 58%;
  height: 30%;
  left: 21%;
  top: 56%;
  border-top-left-radius: 18px;
  border-top-right-radius: 18px;
  border-bottom: none;
}

.mp-refresh-ring {
  width: 58%;
  height: 58%;
  top: 18%;
  left: 18%;
}

.mp-refresh-arrow-a,
.mp-refresh-arrow-b {
  width: 24%;
  height: var(--icon-stroke);
  right: 10%;
  top: 28%;
}

.mp-refresh-arrow-a {
  transform: rotate(8deg);
}

.mp-refresh-arrow-b {
  top: 38%;
  transform: rotate(48deg);
}

.mp-image-frame {
  width: 76%;
  height: 62%;
  left: 12%;
  top: 18%;
  border-radius: 4px;
}

.mp-image-sun {
  width: 14%;
  height: 14%;
  top: 28%;
  left: 28%;
  border-radius: 999px;
}

.mp-image-slope {
  width: 46%;
  height: var(--icon-stroke);
  left: 30%;
  top: 60%;
  transform: rotate(-24deg);
}

.mp-camera-body {
  width: 78%;
  height: 52%;
  left: 11%;
  top: 24%;
  border-radius: 6px;
}

.mp-camera-lens {
  width: 28%;
  height: 28%;
  left: 36%;
  top: 36%;
}

.mp-camera-top {
  width: 18%;
  height: 10%;
  left: 22%;
  top: 16%;
  border-radius: 3px;
}

.mp-crop-top-left,
.mp-crop-top-right,
.mp-crop-bottom-left,
.mp-crop-bottom-right {
  width: 30%;
  height: 30%;
  border-color: var(--icon-color);
}

.mp-crop-top-left {
  top: 10%;
  left: 10%;
  border-top: var(--icon-stroke) solid var(--icon-color);
  border-left: var(--icon-stroke) solid var(--icon-color);
}

.mp-crop-top-right {
  top: 10%;
  right: 10%;
  border-top: var(--icon-stroke) solid var(--icon-color);
  border-right: var(--icon-stroke) solid var(--icon-color);
}

.mp-crop-bottom-left {
  bottom: 10%;
  left: 10%;
  border-bottom: var(--icon-stroke) solid var(--icon-color);
  border-left: var(--icon-stroke) solid var(--icon-color);
}

.mp-crop-bottom-right {
  right: 10%;
  bottom: 10%;
  border-bottom: var(--icon-stroke) solid var(--icon-color);
  border-right: var(--icon-stroke) solid var(--icon-color);
}

.mp-settings-ring {
  width: 54%;
  height: 54%;
  top: 23%;
  left: 23%;
}

.mp-settings-dot {
  width: 16%;
  height: 16%;
  top: 42%;
  left: 42%;
  border-radius: 999px;
}

.mp-settings-spoke-top,
.mp-settings-spoke-right,
.mp-settings-spoke-bottom,
.mp-settings-spoke-left {
  width: var(--icon-stroke);
  height: 18%;
  left: 50%;
  top: 6%;
  transform: translateX(-50%);
}

.mp-settings-spoke-right,
.mp-settings-spoke-left {
  width: 18%;
  height: var(--icon-stroke);
  top: 50%;
  left: auto;
  transform: translateY(-50%);
}

.mp-settings-spoke-right {
  right: 6%;
}

.mp-settings-spoke-bottom {
  top: auto;
  bottom: 6%;
}

.mp-settings-spoke-left {
  left: 6%;
}

.mp-box-outline {
  width: 68%;
  height: 62%;
  top: 19%;
  left: 16%;
  border-radius: 5px;
}

.mp-box-top {
  width: 46%;
  height: var(--icon-stroke);
  top: 34%;
  left: 27%;
}

.mp-box-center {
  width: var(--icon-stroke);
  height: 38%;
  top: 31%;
  left: 50%;
  transform: translateX(-50%);
}

.mp-chat-bubble {
  width: 72%;
  height: 56%;
  left: 14%;
  top: 14%;
  border-radius: 8px;
}
</style>
