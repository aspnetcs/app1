<template>
  <view
    class="float-input"
    :class="{ 'float-input--focused': focused, 'float-input--filled': !!modelValue }"
  >
    <text class="float-input__label">{{ label }}</text>
    <input
      class="float-input__field"
      :type="type"
      :password="password"
      :maxlength="maxlength"
      :value="modelValue"
      @input="onInput"
      @focus="focused = true"
      @blur="focused = false"
      @confirm="$emit('confirm')"
    />
    <text
      v-if="modelValue && clearable"
      class="float-input__clear"
      @click.stop="handleClear"
      @tap.stop="handleClear"
    >x</text>
    <slot name="suffix" />
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { createCompatActionRunner } from '@/utils/h5EventCompat'

defineProps<{
  modelValue: string
  label: string
  type?: string
  password?: boolean
  maxlength?: number
  clearable?: boolean
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', val: string): void
  (e: 'confirm'): void
}>()

const focused = ref(false)
const runCompatAction = createCompatActionRunner()

type FloatInputEvent = {
  detail?: {
    value?: string | number | null
  } | null
}

function onInput(event: InputEvent | FloatInputEvent) {
  const detail = typeof event.detail === 'object' && event.detail !== null
    ? event.detail
    : null
  const value = detail?.value
  emit('update:modelValue', value == null ? '' : String(value))
}

function handleClear(event?: Event) {
  runCompatAction('float-input-clear', event, () => {
    emit('update:modelValue', '')
  })
}
</script>

<style scoped>
.float-input {
  position: relative;
  border: 1px solid #d9d9d9;
  border-radius: 6px;
  padding: 12px;
  margin-bottom: 4px;
  margin-top: 10px;
  display: flex;
  align-items: center;
  box-sizing: border-box;
  transition: border-color 150ms ease;
}

.float-input--focused {
  border-color: #4c7cf6;
}

.float-input__label {
  position: absolute;
  left: 10px;
  top: 50%;
  transform: translateY(-50%);
  font-size: 15px;
  color: #999;
  background: #fff;
  padding: 0 4px;
  transition: all 150ms ease;
  pointer-events: none;
  z-index: 1;
}

.float-input--focused .float-input__label,
.float-input--filled .float-input__label {
  top: 0;
  transform: translateY(-50%);
  font-size: 11px;
  color: #4c7cf6;
}

.float-input--filled:not(.float-input--focused) .float-input__label {
  color: #999;
}

.float-input__field {
  font-size: 15px;
  height: 22px;
  color: #333;
  flex: 1;
  width: 100%;
}

.float-input__clear {
  font-size: 14px;
  color: #ccc;
  padding: 4px 2px 4px 8px;
}
</style>
