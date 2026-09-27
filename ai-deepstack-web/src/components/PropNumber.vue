<template>
  <input
    class="prop-ctl prop-number"
    type="number"
    :value="display"
    :min="min"
    :max="max"
    :step="step"
    :placeholder="placeholder"
    :disabled="disabled"
    :aria-label="ariaLabel"
    @input="onInput"
    @change="onChange"
  />
</template>

<script setup>
import { computed } from 'vue'
import './prop-controls.css'

const props = defineProps({
  modelValue: { type: [Number, String, null], default: null },
  min: { type: [Number, String], default: undefined },
  max: { type: [Number, String], default: undefined },
  step: { type: [Number, String], default: 1 },
  placeholder: { type: String, default: '' },
  disabled: { type: Boolean, default: false },
  /** 空输入时发出 null（适合可选数字）；false 则发 '' */
  nullable: { type: Boolean, default: true },
  ariaLabel: { type: String, default: undefined }
})

const emit = defineEmits(['update:modelValue', 'change'])

const display = computed(() => {
  if (props.modelValue === null || props.modelValue === undefined || props.modelValue === '') {
    return ''
  }
  return props.modelValue
})

function parse(raw) {
  if (raw === '' || raw == null) {
    if (props.nullable) return null
    if (props.min != null && props.min !== '') return Number(props.min)
    return 0
  }
  const n = Number(raw)
  if (!Number.isFinite(n)) {
    return props.nullable ? null : 0
  }
  return n
}

function onInput(e) {
  emit('update:modelValue', parse(e.target.value))
}

function onChange(e) {
  const next = parse(e.target.value)
  emit('update:modelValue', next)
  emit('change', next)
}
</script>
