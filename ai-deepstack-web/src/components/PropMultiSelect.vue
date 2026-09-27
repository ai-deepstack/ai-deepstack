<template>
  <div class="prop-dd prop-dd-multi" :class="{ open }" ref="rootRef">
    <button
      type="button"
      class="prop-dd-trigger"
      :disabled="disabled"
      :aria-expanded="open"
      @click="toggle"
    >
      <span class="prop-dd-value" :class="{ placeholder: !selected.length }">
        <template v-if="!selected.length">{{ placeholder }}</template>
        <template v-else-if="selectedLabels.length <= 2">{{ selectedLabels.join('、') }}</template>
        <template v-else>已选 {{ selected.length }} 项</template>
      </span>
      <span class="prop-dd-meta">
        <span v-if="selected.length" class="prop-dd-count">{{ selected.length }}</span>
        <span class="prop-dd-caret" aria-hidden="true" />
      </span>
    </button>
    <Teleport to="body">
      <Transition name="prop-dd-pop">
        <div
          v-if="open"
          ref="menuRef"
          class="prop-dd-menu prop-dd-menu-portal prop-dd-menu-multi"
          role="listbox"
          aria-multiselectable="true"
          :style="menuStyle"
        >
          <button
            v-for="opt in normalized"
            :key="String(opt.value)"
            type="button"
            class="prop-dd-item"
            role="option"
            :class="{ active: isSelected(opt.value) }"
            :aria-selected="isSelected(opt.value)"
            @click="toggleOption(opt.value)"
          >
            <span class="prop-dd-check" aria-hidden="true" />
            <span class="prop-dd-text">{{ opt.label }}</span>
          </button>
          <p v-if="!normalized.length" class="prop-dd-empty">暂无可选项</p>
          <div v-if="selected.length" class="prop-dd-footer">
            <button type="button" class="prop-dd-clear" @click="clearAll">清空</button>
          </div>
        </div>
      </Transition>
    </Teleport>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'

const props = defineProps({
  modelValue: { type: Array, default: () => [] },
  options: { type: Array, default: () => [] },
  placeholder: { type: String, default: '请选择（可多选）' },
  disabled: { type: Boolean, default: false }
})

const emit = defineEmits(['update:modelValue'])

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

const selected = computed(() => {
  const raw = props.modelValue
  if (!Array.isArray(raw)) {
    if (raw == null || raw === '') return []
    return [raw].map(String)
  }
  return raw.map(String)
})

const selectedLabels = computed(() =>
  selected.value.map((v) => {
    const hit = normalized.value.find((o) => String(o.value) === v)
    return hit?.label ?? v
  })
)

function isSelected(value) {
  return selected.value.includes(String(value))
}

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
  const maxH = 220
  const spaceBelow = window.innerHeight - rect.bottom - gap
  const openUp = spaceBelow < 140 && rect.top > spaceBelow
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

function toggleOption(value) {
  const v = String(value)
  const cur = [...selected.value]
  const next = cur.includes(v) ? cur.filter((x) => x !== v) : [...cur, v]
  const typed = next.map((s) => {
    const hit = normalized.value.find((o) => String(o.value) === s)
    return hit ? hit.value : s
  })
  emit('update:modelValue', typed)
}

function clearAll() {
  emit('update:modelValue', [])
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
  flex: 1;
}

.prop-dd-value.placeholder {
  color: var(--muted, #7d8ba3);
}

.prop-dd-meta {
  display: inline-flex;
  align-items: center;
  gap: 0.45rem;
  flex-shrink: 0;
}

.prop-dd-count {
  min-width: 1.15rem;
  height: 1.15rem;
  padding: 0 0.28rem;
  border-radius: 999px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 0.68rem;
  font-weight: 600;
  color: #d4e4ff;
  background: rgba(78, 168, 255, 0.22);
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
.prop-dd-menu.prop-dd-menu-multi .prop-dd-check {
  width: 0.9rem;
  height: 0.9rem;
  flex-shrink: 0;
  border-radius: 999px;
  border: 1px solid rgba(148, 163, 184, 0.35);
  background: rgba(12, 18, 28, 0.85);
  position: relative;
}
.prop-dd-menu.prop-dd-menu-multi .prop-dd-item.active .prop-dd-check {
  background: linear-gradient(145deg, #7eb6ff, #5a8fd4);
  border-color: transparent;
  box-shadow: 0 0 0 2px rgba(78, 168, 255, 0.18);
}
.prop-dd-menu.prop-dd-menu-multi .prop-dd-item.active .prop-dd-check::after {
  content: "";
  position: absolute;
  left: 0.26rem;
  top: 0.12rem;
  width: 0.22rem;
  height: 0.38rem;
  border-right: 1.5px solid #0a1220;
  border-bottom: 1.5px solid #0a1220;
  transform: rotate(40deg);
}
.prop-dd-menu.prop-dd-menu-multi .prop-dd-footer {
  display: flex;
  justify-content: flex-end;
  padding: 0.25rem 0.35rem 0.1rem;
  border-top: 1px solid rgba(148, 163, 184, 0.1);
  margin-top: 0.25rem;
}
.prop-dd-menu.prop-dd-menu-multi .prop-dd-clear {
  all: unset;
  cursor: pointer;
  font-size: 0.72rem;
  color: var(--muted, #7d8ba3);
  padding: 0.28rem 0.45rem;
  border-radius: 999px;
}
.prop-dd-menu.prop-dd-menu-multi .prop-dd-clear:hover {
  color: #ffb4b4;
  background: rgba(255, 107, 107, 0.1);
}
</style>
