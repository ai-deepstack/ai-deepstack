<template>
  <div class="prop-datetime-wrap">
    <input
      class="prop-ctl prop-datetime"
      :type="inputType"
      :value="modelValue ?? ''"
      :min="min"
      :max="max"
      :step="step"
      :placeholder="placeholder"
      :disabled="disabled"
      :aria-label="ariaLabel"
      @input="onInput"
      @change="onChange"
    />
  </div>
</template>

<script setup>
import { computed } from 'vue'
import './prop-controls.css'

const props = defineProps({
  modelValue: { type: String, default: '' },
  /** date | time | datetime（映射为 datetime-local） */
  type: { type: String, default: 'datetime' },
  min: { type: String, default: undefined },
  max: { type: String, default: undefined },
  step: { type: [Number, String], default: undefined },
  placeholder: { type: String, default: '' },
  disabled: { type: Boolean, default: false },
  ariaLabel: { type: String, default: undefined }
})

const emit = defineEmits(['update:modelValue', 'change'])

const inputType = computed(() => {
  if (props.type === 'date') return 'date'
  if (props.type === 'time') return 'time'
  return 'datetime-local'
})

function onInput(e) {
  emit('update:modelValue', e.target.value || '')
}

function onChange(e) {
  const next = e.target.value || ''
  emit('update:modelValue', next)
  emit('change', next)
}
</script>
