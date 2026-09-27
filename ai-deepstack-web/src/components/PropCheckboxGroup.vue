<template>
  <div class="prop-check-group" role="group">
    <PropCheckbox
      v-for="opt in normalized"
      :key="String(opt.value)"
      :model-value="isChecked(opt.value)"
      :true-value="true"
      :false-value="false"
      :label="opt.label"
      :disabled="disabled || opt.disabled"
      @update:model-value="(on) => toggle(opt.value, on)"
    />
    <p v-if="!normalized.length" class="prop-empty muted">{{ emptyText }}</p>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import PropCheckbox from './PropCheckbox.vue'
import './prop-controls.css'

const props = defineProps({
  /** 已选值数组 */
  modelValue: { type: Array, default: () => [] },
  options: { type: Array, default: () => [] },
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

function same(a, b) {
  return String(a) === String(b)
}

function isChecked(value) {
  return (props.modelValue || []).some((v) => same(v, value))
}

function toggle(value, on) {
  const cur = [...(props.modelValue || [])]
  const idx = cur.findIndex((v) => same(v, value))
  if (on && idx < 0) cur.push(value)
  if (!on && idx >= 0) cur.splice(idx, 1)
  emit('update:modelValue', cur)
  emit('change', cur)
}
</script>

<style scoped>
.prop-empty {
  margin: 0;
  font-size: 0.82rem;
}
</style>
