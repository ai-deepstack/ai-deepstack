<template>
  <span class="node-icon" :class="[`tone-${size}`]" :style="{ '--icon-color': icon.color }" aria-hidden="true">
    <svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
      <path
        v-for="(d, i) in icon.paths"
        :key="i"
        :d="d"
        :fill="icon.filled ? 'currentColor' : 'none'"
        stroke="currentColor"
        :stroke-width="icon.filled ? 0 : 1.75"
        stroke-linecap="round"
        stroke-linejoin="round"
      />
    </svg>
  </span>
</template>

<script setup>
import { computed } from 'vue'
import { nodeIcon } from '../constants/graphNodes'

const props = defineProps({
  type: { type: String, default: '' },
  size: { type: String, default: 'md' }
})

const icon = computed(() => nodeIcon(props.type))
</script>
