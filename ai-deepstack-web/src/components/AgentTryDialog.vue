<template>
  <Teleport to="body">
    <div
      v-if="open"
      class="drawer-mask try-mask"
      role="dialog"
      aria-modal="true"
      @click.self="close"
      @keydown.esc.prevent="close"
    >
      <aside class="try-dialog" tabindex="-1" ref="rootEl">
        <header class="try-head">
          <div class="try-head-text">
            <h2>{{ agentName || '试用对话' }}</h2>
            <code v-if="agentCode" class="try-code">{{ agentCode }}</code>
          </div>
          <div class="row-actions">
            <button class="btn-ghost" type="button" @click="resetSession">新会话</button>
            <button class="btn-ghost" type="button" :disabled="!streaming" @click="abortStream">停止</button>
            <button class="btn-ghost" type="button" @click="close">关闭</button>
          </div>
        </header>

        <p v-if="error" class="error">{{ error }}</p>

        <div ref="streamEl" class="chat-stream try-stream">
          <p v-if="!messages.length" class="muted try-empty">发一条消息试试这个 Agent。</p>
          <div
            v-for="(m, idx) in messages"
            :key="idx"
            class="msg"
            :class="m.role === 'user' ? 'msg-user' : 'msg-assistant'"
          >
            <div class="msg-meta">{{ m.role === 'user' ? 'You' : 'AI DeepStack' }}</div>
            <div class="msg-bubble" :class="{ typing: m.streaming && !m.content }">{{ m.content }}</div>
            <div v-for="(c, ci) in m.cards || []" :key="ci">
              <GenericConfirmCard
                v-if="c.cardType === CardType.GENERIC_CONFIRM"
                :card="c"
                :submit="(spec) => actCard(c, spec)"
              />
              <div v-else class="card-block">
                <div class="pill pill-brass">{{ c.cardType || 'card' }}</div>
                <h4>{{ c.title || c.cardId || '卡片' }}</h4>
                <pre class="muted" style="margin: 0; white-space: pre-wrap; font-size: 0.82rem">{{ formatPayload(c) }}</pre>
                <div class="card-actions" v-if="c.actions?.length">
                  <button
                    v-for="(act, ai) in c.actions"
                    :key="ai"
                    class="btn-ghost"
                    type="button"
                    @click="actCard(c, act)"
                  >
                    {{ act.label || act.type || '动作' }}
                  </button>
                </div>
              </div>
            </div>
          </div>
        </div>

        <form class="composer try-composer" @submit.prevent="sendStream">
          <textarea
            ref="inputEl"
            v-model="draft"
            rows="3"
            placeholder="输入消息，Enter 发送 · Shift+Enter 换行"
            @keydown="onKey"
          />
          <div class="row-actions" style="margin-top: 0.65rem; justify-content: space-between">
            <span class="muted" style="font-size: 0.8rem">
              {{ streaming ? '生成中…' : (usageLabel || '空闲') }}
            </span>
            <button class="btn" type="submit" :disabled="streaming || !draft.trim() || !agentCode">发送</button>
          </div>
        </form>
      </aside>
    </div>
  </Teleport>
</template>

<script setup>
import { computed, nextTick, onUnmounted, ref, watch } from 'vue'
import { post } from '../api/http'
import { streamSse, tryParseJson } from '../api/sse'
import GenericConfirmCard from './GenericConfirmCard.vue'
import {
  CardAction,
  CardType,
  ChatCardStatus,
  GraphRunStatus,
  toCardActionCode
} from '../constants/enums'
import { AgentRunUsageKeys, CardStatusFields, CardStatusPhases, ChatSseEvents } from '../constants/sseEvents'

const props = defineProps({
  open: { type: Boolean, default: false },
  agentCode: { type: String, default: '' },
  agentName: { type: String, default: '' }
})

const emit = defineEmits(['close'])

const conversationId = ref('')
const draft = ref('')
const messages = ref([])
const usage = ref(null)
const error = ref('')
const streaming = ref(false)
const streamEl = ref(null)
const inputEl = ref(null)
const rootEl = ref(null)
let abortCtrl = null

const usageLabel = computed(() => {
  if (!usage.value) return ''
  const ms = usage.value[AgentRunUsageKeys.LATENCY_MS]
  return ms != null ? `上次 ${ms} ms` : ''
})

watch(
  () => props.open,
  async (v) => {
    if (v) {
      resetSession()
      await nextTick()
      rootEl.value?.focus?.()
      inputEl.value?.focus?.()
    } else {
      abortStream()
    }
  }
)

watch(
  () => props.agentCode,
  () => {
    if (props.open) resetSession()
  }
)

function close() {
  abortStream()
  emit('close')
}

function resetSession() {
  conversationId.value = ''
  messages.value = []
  usage.value = null
  error.value = ''
  draft.value = ''
  abortStream()
}

function abortStream() {
  if (abortCtrl) {
    abortCtrl.abort()
    abortCtrl = null
  }
  streaming.value = false
  const last = messages.value[messages.value.length - 1]
  if (last?.streaming) last.streaming = false
}

function onKey(e) {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    sendStream()
  }
}

function formatPayload(c) {
  if (c.payload) return JSON.stringify(c.payload, null, 2)
  const { cardType, title, cardId, actions, ...rest } = c
  return JSON.stringify(rest, null, 2)
}

async function scrollBottom() {
  await nextTick()
  if (streamEl.value) streamEl.value.scrollTop = streamEl.value.scrollHeight
}

async function sendStream() {
  if (!props.agentCode || !draft.value.trim() || streaming.value) return
  const text = draft.value.trim()
  draft.value = ''
  error.value = ''
  usage.value = null
  messages.value.push({ role: 'user', content: text })
  const assistant = { role: 'assistant', content: '', cards: [], streaming: true }
  messages.value.push(assistant)
  streaming.value = true
  abortCtrl = new AbortController()
  await scrollBottom()

  try {
    for await (const frame of streamSse('/api/chat/stream', {
      agentCode: props.agentCode,
      conversationId: conversationId.value || undefined,
      message: text
    }, { signal: abortCtrl.signal })) {
      handleFrame(frame, assistant)
      await scrollBottom()
    }
  } catch (e) {
    if (e.name !== 'AbortError') {
      error.value = e.message || '流式失败'
    }
  } finally {
    assistant.streaming = false
    streaming.value = false
    abortCtrl = null
  }
}

function handleFrame({ event, data }, assistant) {
  const parsed = tryParseJson(data)
  const E = ChatSseEvents

  if (event === E.CONVERSATION) {
    conversationId.value = typeof parsed === 'string' ? parsed : (parsed?.conversationId || data)
    return
  }
  if (event === E.USAGE) {
    usage.value = typeof parsed === 'object' ? parsed : {}
    return
  }
  if (event === E.CARD) {
    const card = typeof parsed === 'object' ? parsed : { payload: parsed }
    assistant.cards.push(card)
    return
  }
  if (event === E.CARD_STATUS) {
    const p = typeof parsed === 'object' ? parsed : {}
    if (p[CardStatusFields.PHASE] === CardStatusPhases.FAILED) {
      error.value = p[CardStatusFields.MESSAGE] || '卡片处理失败'
    }
    return
  }
  if (event === E.WORKFLOW_COMPLETE) {
    const p = typeof parsed === 'object' ? parsed : {}
    if (p.result && !assistant.content) assistant.content = String(p.result)
    if (p.statusCode === GraphRunStatus.FAILED && p.errorMessage) {
      error.value = p.errorMessage
    }
    return
  }
  if (event === E.AGENT_ERROR) {
    const p = typeof parsed === 'object' ? parsed : { error: data }
    error.value = p.error || data
    return
  }
  if (event === E.AGENT_NODE_START || event === E.AGENT_NODE_COMPLETE) {
    return
  }
  appendText(assistant, parsed, data)
}

function appendText(assistant, parsed, raw) {
  if (parsed && typeof parsed === 'object') {
    const chunk = parsed.content ?? parsed.delta ?? parsed.text ?? parsed.result
    if (chunk != null) {
      assistant.content += String(chunk)
      return
    }
    if (parsed.conversationId) conversationId.value = parsed.conversationId
  }
  if (typeof parsed === 'string') {
    assistant.content += parsed
    return
  }
  if (raw) assistant.content += raw
}

async function actCard(card, act) {
  if (!card.cardId) {
    error.value = '卡片缺少 cardId'
    return
  }
  const raw = act.action ?? act.type ?? CardAction.CONFIRM
  const action = toCardActionCode(raw)
  try {
    const res = await post('/api/chat-cards/act', {
      cardId: card.cardId,
      action,
      threadId: card.threadId,
      conversationId: card.conversationId || conversationId.value || undefined,
      agentCode: props.agentCode || undefined,
      modifiedPayload: act.modifiedPayload
    })
    const data = res?.data || {}
    card.status = data.status ?? (action === CardAction.REJECT ? ChatCardStatus.REJECTED : ChatCardStatus.CONFIRMED)
    card.statusName = data.statusName || (action === CardAction.REJECT ? '已拒绝' : '已确认')
    if (data.graphResult) {
      const last = messages.value[messages.value.length - 1]
      if (last?.role === 'assistant') {
        last.content = (last.content ? last.content + '\n' : '') + data.graphResult
      }
    }
    await scrollBottom()
  } catch (e) {
    error.value = e.message
    throw e
  }
}

onUnmounted(abortStream)
</script>

<style scoped>
.try-mask {
  z-index: 80;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 1rem;
}
.try-dialog {
  width: min(720px, 100%);
  max-height: min(88vh, 820px);
  display: flex;
  flex-direction: column;
  background: var(--panel);
  border: 1px solid var(--line-strong);
  border-radius: var(--radius);
  box-shadow: var(--shadow);
  padding: 1rem 1.1rem 1.05rem;
  outline: none;
}
.try-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 0.75rem;
  margin-bottom: 0.65rem;
}
.try-head h2 {
  margin: 0;
  font-size: 1.15rem;
}
.try-code {
  display: block;
  margin-top: 0.25rem;
  font-size: 0.78rem;
  color: var(--cyan-bright);
  word-break: break-all;
}
.try-stream {
  flex: 1;
  min-height: 280px;
  max-height: none;
  margin-bottom: 0.75rem;
}
.try-empty {
  margin: 1.5rem 0;
  text-align: center;
}
.try-composer textarea {
  border-radius: 14px;
  min-height: 84px;
  width: 100%;
  box-sizing: border-box;
}

@media (max-width: 768px) {
  .try-mask { padding: 0.6rem; }
  .try-dialog {
    width: 100%;
    max-height: 90dvh;
    padding: 0.95rem 0.9rem calc(0.95rem + env(safe-area-inset-bottom));
  }
  /* 标题和操作按钮并排会互相挤（Agent 名一换行就压到按钮上），改成上下堆叠 */
  .try-head {
    flex-direction: column;
    align-items: stretch;
    gap: 0.55rem;
  }
  .try-head h2 { font-size: 1.05rem; }
  .try-head .row-actions { flex-wrap: wrap; }
  .try-head .row-actions > * { flex: 1 1 auto; }
  .try-stream { min-height: 200px; }
}
</style>
