<template>
  <Teleport to="body">
    <Transition name="confirm-fade">
      <div
        v-if="state.visible"
        class="confirm-mask"
        role="dialog"
        aria-modal="true"
        @keydown.esc.prevent="dismiss"
        @click.self="dismiss"
      >
        <div class="confirm-card" tabindex="-1" ref="cardRef">
          <div class="confirm-accent" />
          <h3 class="confirm-title">{{ state.title }}</h3>
          <p class="confirm-msg">{{ state.message }}</p>
          <div class="confirm-actions">
            <button class="btn-ghost" type="button" @click="dismiss">{{ state.cancelText }}</button>
            <button
              class="btn"
              type="button"
              :class="{ 'btn-danger': state.danger }"
              @click="accept"
            >
              {{ state.confirmText }}
            </button>
          </div>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<script setup>
import { nextTick, watch, ref } from 'vue'
import { useConfirmState } from '../composables/useConfirm'

const { state, accept, dismiss } = useConfirmState()
const cardRef = ref(null)

watch(
  () => state.visible,
  async (v) => {
    if (!v) return
    await nextTick()
    cardRef.value?.focus?.()
  }
)
</script>
