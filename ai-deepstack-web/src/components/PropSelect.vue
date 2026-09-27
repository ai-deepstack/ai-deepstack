<template>
  <div class="prop-dd" :class="{ open }" ref="rootRef">
    <button
      type="button"
      class="prop-dd-trigger"
      :disabled="disabled"
      :aria-expanded="open"
      @click="toggle"
    >
      <span class="prop-dd-value" :class="{ placeholder: !hasValue }">{{ displayLabel }}</span>
      <span class="prop-dd-caret" aria-hidden="true" />
    </button>
    <Teleport to="body">
      <Transition name="prop-dd-pop">
        <div
          v-if="open"
          ref="menuRef"
          class="prop-dd-menu prop-dd-menu-portal"
          role="listbox"
          :style="menuStyle"
        >
          <button
            v-for="opt in normalized"
            :key="String(opt.value)"
            type="button"
            class="prop-dd-item"
            role="option"
            :class="{ active: String(opt.value) === String(modelValue ?? '') }"
            :aria-selected="String(opt.value) === String(modelValue ?? '')"
            @click="pick(opt.value)"
          >
            <span class="prop-dd-dot" aria-hidden="true" />
            <span class="prop-dd-text">{{ opt.label }}</span>
          </button>
          <p v-if="!normalized.length" class="prop-dd-empty">暂无可选项</p>
        </div>
      </Transition>
    </Teleport>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'

const props = defineProps({
  modelValue: { type: [String, Number, Boolean], default: '' },
  options: { type: Array, default: () => [] },
  placeholder: { type: String, default: '请选择' },
  disabled: { type: Boolean, default: false }
})

const emit = defineEmits(['update:modelValue', 'change'])

const open = ref(false)
const rootRef = ref(null)
const menuRef = ref(null)
const menuStyle = ref({})

const normalized = computed(() =>
  (props.options || []).map((opt) => {
    if (opt != null && typeof opt === 'object') {
      return { value: opt.value, label: opt.label ?? String(opt.value) }
    }
    return { value: opt, label: String(opt) }
  })
)

const hasValue = computed(() => {
  const v = props.modelValue
  return v !== '' && v != null
})

const displayLabel = computed(() => {
  if (!hasValue.value) return props.placeholder
  const hit = normalized.value.find((o) => String(o.value) === String(props.modelValue))
  return hit?.label ?? String(props.modelValue)
})

async function toggle() {
  if (props.disabled) return
  open.value = !open.value
  if (open.value) {
    await nextTick()
    placeMenu()
  }
}

function placeMenu() {
  const el = rootRef.value
  if (!el) return
  const rect = el.getBoundingClientRect()
  const gap = 8
  const maxH = 200
  const spaceBelow = window.innerHeight - rect.bottom - gap
  const openUp = spaceBelow < 120 && rect.top > spaceBelow
  const top = openUp
    ? Math.max(8, rect.top - Math.min(maxH, rect.top - 8) - gap)
    : rect.bottom + gap
  menuStyle.value = {
    position: 'fixed',
    top: `${top}px`,
    left: `${rect.left}px`,
    width: `${rect.width}px`,
    maxHeight: `${openUp ? Math.min(maxH, rect.top - 16) : Math.min(maxH, spaceBelow)}px`,
    zIndex: 200
  }
}

function pick(value) {
  emit('update:modelValue', value)
  emit('change', value)
  open.value = false
}

function onDocPointer(e) {
  if (!open.value) return
  const t = e.target
  if (rootRef.value?.contains(t) || menuRef.value?.contains(t)) return
  open.value = false
}

function onKey(e) {
  if (e.key === 'Escape') open.value = false
}

function onReposition() {
  if (open.value) placeMenu()
}

watch(open, (v) => {
  if (v) nextTick(placeMenu)
})

onMounted(() => {
  document.addEventListener('pointerdown', onDocPointer, true)
  document.addEventListener('keydown', onKey)
  window.addEventListener('resize', onReposition)
  window.addEventListener('scroll', onReposition, true)
})

onUnmounted(() => {
  document.removeEventListener('pointerdown', onDocPointer, true)
  document.removeEventListener('keydown', onKey)
  window.removeEventListener('resize', onReposition)
  window.removeEventListener('scroll', onReposition, true)
})
</script>

<style scoped>
.prop-dd {
  position: relative;
  width: 100%;
}

.prop-dd-trigger {
  all: unset;
  box-sizing: border-box;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.6rem;
  width: 100%;
  min-height: 2.15rem;
  padding: 0.5rem 0.85rem;
  border-radius: 999px;
  border: 1px solid rgba(148, 163, 184, 0.2);
  background: linear-gradient(180deg, rgba(30, 40, 58, 0.96), rgba(18, 26, 40, 0.98));
  color: var(--ink-soft, #b7c3d9);
  font-size: 0.8rem;
  line-height: 1.35;
  cursor: pointer;
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.04);
  transition: border-color 0.15s ease, box-shadow 0.15s ease, color 0.15s ease;
}

.prop-dd-trigger:hover:not(:disabled) {
  border-color: rgba(148, 183, 255, 0.34);
  color: var(--ink, #e8eef8);
}

.prop-dd.open .prop-dd-trigger,
.prop-dd-trigger:focus-visible {
  border-color: rgba(148, 183, 255, 0.48);
  box-shadow: 0 0 0 3px rgba(78, 168, 255, 0.12);
  color: var(--ink, #e8eef8);
}

.prop-dd-trigger:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.prop-dd-value {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.prop-dd-value.placeholder {
  color: var(--muted, #7d8ba3);
}

.prop-dd-caret {
  width: 0.38rem;
  height: 0.38rem;
  flex-shrink: 0;
  border-right: 1.5px solid rgba(167, 180, 204, 0.75);
  border-bottom: 1.5px solid rgba(167, 180, 204, 0.75);
  transform: rotate(45deg) translateY(-1px);
  transition: transform 0.18s ease;
  opacity: 0.85;
}

.prop-dd.open .prop-dd-caret {
  transform: rotate(-135deg) translateY(-1px);
}

.prop-dd-pop-enter-active,
.prop-dd-pop-leave-active {
  transition: opacity 0.14s ease, transform 0.14s ease;
}

.prop-dd-pop-enter-from,
.prop-dd-pop-leave-to {
  opacity: 0;
  transform: translateY(-4px) scale(0.98);
}
</style>

<style>
/* Teleport 到 body，需非 scoped */
.prop-dd-menu.prop-dd-menu-portal {
  overflow: auto;
  padding: 0.4rem;
  border-radius: 18px;
  border: 1px solid rgba(148, 163, 184, 0.16);
  background:
    linear-gradient(165deg, rgba(28, 38, 56, 0.98), rgba(14, 20, 34, 0.99));
  box-shadow:
    0 18px 40px rgba(0, 0, 0, 0.42),
    0 0 0 1px rgba(255, 255, 255, 0.03) inset;
  backdrop-filter: blur(12px);
  box-sizing: border-box;
}
.prop-dd-menu.prop-dd-menu-portal .prop-dd-item {
  all: unset;
  box-sizing: border-box;
  display: flex;
  align-items: center;
  gap: 0.55rem;
  width: 100%;
  padding: 0.52rem 0.7rem;
  border-radius: 999px;
  cursor: pointer;
  color: var(--ink-soft, #b7c3d9);
  font-size: 0.78rem;
  line-height: 1.3;
  transition: background 0.12s ease, color 0.12s ease;
}
.prop-dd-menu.prop-dd-menu-portal .prop-dd-item:hover {
  background: rgba(148, 183, 255, 0.1);
  color: var(--ink, #e8eef8);
}
.prop-dd-menu.prop-dd-menu-portal .prop-dd-item.active {
  background: rgba(78, 168, 255, 0.16);
  color: #d4e4ff;
}
.prop-dd-menu.prop-dd-menu-portal .prop-dd-dot {
  width: 0.42rem;
  height: 0.42rem;
  border-radius: 999px;
  flex-shrink: 0;
  background: rgba(148, 163, 184, 0.35);
}
.prop-dd-menu.prop-dd-menu-portal .prop-dd-item.active .prop-dd-dot {
  background: #8eb8ff;
  box-shadow: 0 0 0 3px rgba(78, 168, 255, 0.18);
}
.prop-dd-menu.prop-dd-menu-portal .prop-dd-text {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.prop-dd-menu.prop-dd-menu-portal .prop-dd-empty {
  margin: 0;
  padding: 0.55rem 0.65rem;
  font-size: 0.72rem;
  color: var(--muted, #7d8ba3);
}
</style>
