<template>
  <div class="prop-radio-group" :class="{ 'is-row': direction === 'row' }" role="radiogroup">
    <label
      v-for="opt in normalized"
      :key="String(opt.value)"
      class="prop-radio"
      :class="{ 'is-on': isOn(opt.value), 'is-disabled': disabled || opt.disabled }"
    >
      <input
        class="prop-radio-input"
        type="radio"
        :name="name"
        :value="String(opt.value)"
        :checked="isOn(opt.value)"
        :disabled="disabled || opt.disabled"
        @change="pick(opt.value)"
      />
      <span class="prop-radio-dot" aria-hidden="true" />
      <span class="prop-radio-text">{{ opt.label }}</span>
    </label>
    <p v-if="!normalized.length" class="prop-empty muted">{{ emptyText }}</p>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import './prop-controls.css'

const props = defineProps({
  modelValue: { type: [String, Number, Boolean], default: '' },
  options: { type: Array, default: () => [] },
  name: { type: String, default: () => `prop-radio-${Math.random().toString(36).slice(2, 8)}` },
  /** column | row */
  direction: { type: String, default: 'column' },
  disabled: { type: Boolean, default: false },
  emptyText: { type: String, default: '暂无可选项' }
})

const emit = defineEmits(['update:modelValue', 'change'])

const normalized = computed(() =>
  (props.options || []).map((opt) => {
    if (opt != null && typeof opt === 'object') {
      return {
        value: opt.value,
        label: opt.label ?? String(opt.value),
        disabled: !!opt.disabled
      }
    }
    return { value: opt, label: String(opt), disabled: false }
  })
)

function isOn(value) {
  return String(props.modelValue) === String(value)
}

function pick(value) {
  if (props.disabled) return
  emit('update:modelValue', value)
  emit('change', value)
}
</script>

<style scoped>
.prop-empty {
  margin: 0;
  font-size: 0.82rem;
}
</style>
