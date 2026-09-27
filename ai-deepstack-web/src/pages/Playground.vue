<template>
  <div>
    <header class="page-head">
      <div>
        <h1>试用台</h1>
        <p>选一个智能体，发消息试跑；左侧会显示运行过程。</p>
      </div>
      <div class="row-actions">
        <button class="btn-ghost" type="button" @click="resetSession">新会话</button>
        <button class="btn-ghost" type="button" :disabled="!streaming" @click="abortStream">停止</button>
      </div>
    </header>

    <div class="chat-layout">
      <aside class="panel hud-corners" style="padding: 1rem">
        <div class="field">
          <label>智能体</label>
          <PropSelect
            v-model="agentCode"
            :options="agentOptions"
            placeholder="请选择"
            @change="onAgentChange"
          />
        </div>
        <div class="field">
          <label>会话 ID</label>
          <input v-model="conversationId" placeholder="自动生成" />
        </div>
        <div v-if="currentAgent" class="panel-quiet" style="margin-bottom: 1rem">
          <div class="pill pill-brass">{{ currentAgent.label }}</div>
        </div>

        <h3 style="margin: 0 0 0.55rem; font-size: 0.85rem; letter-spacing: 0.06em; text-transform: uppercase; color: var(--muted)">
          运行轨迹
        </h3>
        <div class="timeline" v-if="timeline.length">
          <div v-for="(t, i) in timeline" :key="i" class="timeline-item">
            <span class="timeline-dot" :class="t.kind" />
            <div>
              <strong>{{ t.title }}</strong>
              <div class="muted">{{ t.detail }}</div>
            </div>
          </div>
        </div>
        <p v-else class="muted" style="margin: 0; font-size: 0.85rem">发送消息后显示节点与用量。</p>

        <div v-if="usage" class="panel-quiet" style="margin-top: 1rem">
          <div class="muted" style="font-size: 0.75rem; letter-spacing: 0.06em; text-transform: uppercase">用量</div>
          <div style="margin-top: 0.35rem; font-size: 0.9rem">
            latency {{ usage[AgentRunUsageKeys.LATENCY_MS] ?? '—' }} ms
            <span v-if="usage[AgentRunUsageKeys.TRACE_MODE]"> · {{ usage[AgentRunUsageKeys.TRACE_MODE] }}</span>
          </div>
        </div>
      </aside>

      <section class="panel hud-corners" style="display: flex; flex-direction: column; min-height: 560px">
        <p v-if="error" class="error">{{ error }}</p>
        <div ref="streamEl" class="chat-stream">
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

        <form class="composer" @submit.prevent="sendStream">
          <textarea
            v-model="draft"
            rows="3"
            placeholder="输入消息，Enter 发送 · Shift+Enter 换行"
            @keydown="onKey"
          />
          <div class="row-actions" style="margin-top: 0.7rem; justify-content: space-between">
            <span class="muted" style="font-size: 0.8rem">
              {{ streaming ? '生成中…' : '空闲' }}
            </span>
            <button class="btn" type="submit" :disabled="streaming || !draft.trim()">发送</button>
          </div>
        </form>
      </section>
    </div>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, onUnmounted, ref } from 'vue'
import { get, post } from '../api/http'
import { streamSse, tryParseJson } from '../api/sse'
import PropSelect from '../components/PropSelect.vue'
import GenericConfirmCard from '../components/GenericConfirmCard.vue'
import {
  CardAction,
  CardType,
  ChatCardStatus,
  GraphRunStatus,
  toCardActionCode
} from '../constants/enums'
import { AgentRunUsageKeys, CardStatusFields, CardStatusPhases, ChatSseEvents } from '../constants/sseEvents'

const agents = ref([])
const agentCode = ref('')
const conversationId = ref('')
const draft = ref('')
const messages = ref([])
const timeline = ref([])
const usage = ref(null)
const error = ref('')
const streaming = ref(false)
const streamEl = ref(null)
let abortCtrl = null

const currentAgent = computed(() => agents.value.find((a) => a.value === agentCode.value))
const agentOptions = computed(() => agents.value)

async function loadAgents() {
  try {
    const res = await get('/api/agents/options')
    agents.value = res.data || []
    if (!agentCode.value && agents.value.length) {
      agentCode.value = agents.value.find((a) => a.value === 'demo_chat')?.value
        || agents.value[0].value
    }
  } catch (e) {
    error.value = e.message
  }
}

function onAgentChange() {
  resetSession()
}

function resetSession() {
  conversationId.value = ''
  messages.value = []
  timeline.value = []
  usage.value = null
  error.value = ''
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
  if (!agentCode.value || !draft.value.trim() || streaming.value) return
  const text = draft.value.trim()
  draft.value = ''
  error.value = ''
  usage.value = null
  timeline.value = []
  messages.value.push({ role: 'user', content: text })
  const assistant = { role: 'assistant', content: '', cards: [], streaming: true }
  messages.value.push(assistant)
  streaming.value = true
  abortCtrl = new AbortController()
  await scrollBottom()

  try {
    for await (const frame of streamSse('/api/chat/stream', {
      agentCode: agentCode.value,
      conversationId: conversationId.value || undefined,
      message: text
    }, { signal: abortCtrl.signal })) {
      handleFrame(frame, assistant)
      await scrollBottom()
    }
  } catch (e) {
    if (e.name !== 'AbortError') {
      error.value = e.message || '流式失败'
      timeline.value.push({ kind: 'err', title: '错误', detail: error.value })
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
    timeline.value.push({ kind: '', title: '会话', detail: conversationId.value })
    return
  }
  if (event === E.USAGE) {
    usage.value = typeof parsed === 'object' ? parsed : {}
    timeline.value.push({
      kind: 'done',
      title: '完成',
      detail: `latency ${usage.value[AgentRunUsageKeys.LATENCY_MS] ?? '—'} ms`
    })
    return
  }
  if (event === E.CARD) {
    const card = typeof parsed === 'object' ? parsed : { payload: parsed }
    assistant.cards.push(card)
    timeline.value.push({ kind: '', title: '卡片', detail: card.cardType || card.title || 'emitted' })
    return
  }
  if (event === E.CARD_STATUS) {
    const p = typeof parsed === 'object' ? parsed : {}
    timeline.value.push({
      kind: p[CardStatusFields.PHASE] === CardStatusPhases.FAILED ? 'err' : '',
      title: `卡片 · ${p[CardStatusFields.PHASE] || 'status'}`,
      detail: p[CardStatusFields.MESSAGE] || p[CardStatusFields.TOOL_NAME] || ''
    })
    return
  }
  if (event === E.AGENT_NODE_START) {
    const p = typeof parsed === 'object' ? parsed : {}
    timeline.value.push({
      kind: '',
      title: `节点开始 · ${p.nodeName || p.nodeId || ''}`,
      detail: p.nodeId || ''
    })
    return
  }
  if (event === E.AGENT_NODE_COMPLETE) {
    const p = typeof parsed === 'object' ? parsed : {}
    timeline.value.push({
      kind: p.statusCode === GraphRunStatus.FAILED ? 'err' : 'done',
      title: `节点完成 · ${p.nodeName || p.nodeId || ''}`,
      detail: p.statusName || String(p.statusCode ?? '')
    })
    return
  }
  if (event === E.WORKFLOW_COMPLETE) {
    const p = typeof parsed === 'object' ? parsed : {}
    if (p.result && !assistant.content) assistant.content = String(p.result)
    timeline.value.push({
      kind: p.statusCode === GraphRunStatus.FAILED ? 'err' : 'done',
        title: `工作流 · ${p.statusName || (p.statusCode ?? 'DONE')}`,
      detail: p.errorMessage || ''
    })
    return
  }
  if (event === E.AGENT_ERROR) {
    const p = typeof parsed === 'object' ? parsed : { error: data }
    error.value = p.error || data
    timeline.value.push({ kind: 'err', title: 'Agent 错误', detail: error.value })
    return
  }

  // CHAT default chunks or GRAPH message tokens
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
      agentCode: agentCode.value || undefined,
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
    const graphLabel = data.graphStatusName || data.graphStatus
    const actionLabel = ({
      [CardAction.CONFIRM]: 'confirm',
      [CardAction.EDIT]: 'edit',
      [CardAction.REJECT]: 'reject'
    })[action] || String(action)
    timeline.value.push({
      kind: 'done',
      title: '卡片动作',
      detail: `${actionLabel} · ${card.cardId}${graphLabel ? ' · ' + graphLabel : ''}`
    })
    await scrollBottom()
  } catch (e) {
    error.value = e.message
    throw e
  }
}

onMounted(loadAgents)
onUnmounted(abortStream)
</script>

<style scoped>
.composer textarea {
  border-radius: 16px;
  min-height: 96px;
}
</style>
