<template>
  <label class="prop-check" :class="{ 'is-on': isOn, 'is-disabled': disabled }">
    <input
      class="prop-check-input"
      type="checkbox"
      :checked="isOn"
      :disabled="disabled"
      @change="onChange"
    />
    <span class="prop-check-box" aria-hidden="true" />
    <span v-if="label || $slots.default" class="prop-check-text">
      <slot>{{ label }}</slot>
    </span>
  </label>
</template>

<script setup>
import { computed } from 'vue'
import './prop-controls.css'

const props = defineProps({
  modelValue: { type: [Boolean, Number, String], default: false },
  /** 选中时写入的值；默认 true */
  trueValue: { type: [Boolean, Number, String], default: true },
  /** 未选中时写入的值；默认 false */
  falseValue: { type: [Boolean, Number, String], default: false },
  label: { type: String, default: '' },
  disabled: { type: Boolean, default: false }
})

const emit = defineEmits(['update:modelValue', 'change'])

const isOn = computed(() => String(props.modelValue) === String(props.trueValue))

function onChange(e) {
  if (props.disabled) return
  const next = e.target.checked ? props.trueValue : props.falseValue
  emit('update:modelValue', next)
  emit('change', next)
}
</script>
