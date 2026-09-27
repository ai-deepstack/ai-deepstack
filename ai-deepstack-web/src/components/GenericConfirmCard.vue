<template>
  <div class="card-block" :class="{ 'is-done': done }">
    <div class="pill pill-brass">确认</div>
    <h4>{{ title }}</h4>
    <p v-if="summary" class="confirm-summary">{{ summary }}</p>
    <dl v-if="fields.length" class="confirm-fields">
      <div v-for="(f, i) in fields" :key="f.key || i" class="confirm-field">
        <dt>{{ f.label || f.key }}</dt>
        <dd>
          <input
            v-if="f.editable && !done"
            v-model="f.value"
            type="text"
          />
          <span v-else>{{ displayValue(f.value) }}</span>
        </dd>
      </div>
    </dl>
    <div class="card-actions" v-if="!done">
      <button class="btn-ghost" type="button" :disabled="busy" @click="reject">
        {{ cancelLabel }}
      </button>
      <button class="btn" type="button" :disabled="busy" @click="confirm">
        {{ confirmLabel }}
      </button>
    </div>
    <p v-else class="muted confirm-done">{{ doneLabel }}</p>
  </div>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { CardAction, ChatCardStatus } from '../constants/enums'

const props = defineProps({
  card: { type: Object, required: true },
  submit: { type: Function, required: true }
})

const busy = ref(false)
const done = ref(false)
const doneLabel = ref('')
const fields = ref([])

const payload = computed(() => props.card?.payload || {})
const title = computed(() => payload.value.title || props.card.title || '请确认')
const summary = computed(() => payload.value.summary || '')
const confirmLabel = computed(() => payload.value.confirmLabel || '确认')
const cancelLabel = computed(() => payload.value.cancelLabel || '取消')

watch(
  () => props.card,
  (card) => {
    fields.value = cloneFields(card?.payload?.fields)
    const st = card?.status
    done.value = st === ChatCardStatus.CONFIRMED
      || st === ChatCardStatus.REJECTED
      || st === ChatCardStatus.EDITED
      || st === 'CONFIRMED'
      || st === 'REJECTED'
      || st === 'EDITED'
  },
  { immediate: true }
)

function cloneFields(raw) {
  let list = raw
  if (typeof list === 'string') {
    try {
      list = JSON.parse(list)
    } catch {
      return []
    }
  }
  if (!Array.isArray(list)) return []
  return list.map((f) => ({
    key: f.key,
    label: f.label || f.key,
    value: f.value == null ? '' : String(f.value),
    editable: f.editable === true || f.editable === 'true'
  }))
}

function displayValue(v) {
  return v == null || v === '' ? '—' : String(v)
}

function fieldsChanged() {
  const orig = cloneFields(props.card?.payload?.fields)
  if (orig.length !== fields.value.length) return true
  return fields.value.some((f, i) => String(f.value) !== String(orig[i]?.value ?? ''))
}

function buildPayload() {
  const next = { ...(props.card.payload || {}) }
  next.fields = fields.value.map((f) => ({
    key: f.key,
    label: f.label,
    value: f.value,
    editable: f.editable
  }))
  return next
}

async function confirm() {
  busy.value = true
  try {
    const edited = fieldsChanged()
    await props.submit({
      action: edited ? CardAction.EDIT : CardAction.CONFIRM,
      modifiedPayload: edited ? buildPayload() : undefined
    })
    done.value = true
    doneLabel.value = edited ? '已按修改确认' : '已确认'
  } finally {
    busy.value = false
  }
}

async function reject() {
  busy.value = true
  try {
    await props.submit({ action: CardAction.REJECT })
    done.value = true
    doneLabel.value = '已取消'
  } finally {
    busy.value = false
  }
}
</script>
