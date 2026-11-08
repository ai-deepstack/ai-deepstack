<template>
  <div class="chat-page">
    <header class="page-head">
      <div>
        <h1>试用台</h1>
        <p>选一个 Agent，发消息试跑；左侧会显示运行过程。</p>
      </div>
      <div class="row-actions">
        <button class="btn-ghost" type="button" @click="resetSession">新会话</button>
        <button class="btn-ghost" type="button" :disabled="!streaming" @click="abortStream">停止</button>
      </div>
    </header>

    <!-- 手机端：配置与轨迹面板默认收起，避免占满首屏把对话挤到折叠线以下 -->
    <button class="chat-side-toggle" type="button" @click="sideOpen = !sideOpen">
      <span>配置与运行轨迹</span>
      <span class="muted">{{ sideOpen ? '收起' : '展开' }}</span>
    </button>

    <div class="chat-layout" :class="{ 'side-open': sideOpen }">
      <aside class="panel hud-corners chat-side" style="padding: 1rem">
        <div class="field">
          <label>Agent</label>
          <PropSelect
            v-model="agentCode"
            :options="agentOptions"
            placeholder="请选择"
            @change="onAgentChange"
          />
        </div>
        <button class="btn conv-new" type="button" @click="resetSession">+ 新会话</button>

        <div class="conv-head">
          <span>会话历史</span>
          <span class="muted">{{ currentAgent?.label || '—' }}</span>
        </div>
        <div class="conv-list" v-if="conversations.length">
          <div
            v-for="c in conversations"
            :key="c.conversationId"
            class="conv-row"
            :class="{ active: c.conversationId === conversationId }"
          >
            <template v-if="renamingId === c.conversationId">
              <input
                v-model="renameDraft"
                class="conv-rename"
                maxlength="256"
                @keyup.enter="saveRename(c)"
                @keyup.esc="cancelRename"
              />
              <div class="conv-row-actions">
                <button class="conv-mini" type="button" title="保存" aria-label="保存" @click.stop="saveRename(c)">
                  <svg viewBox="0 0 24 24" aria-hidden="true">
                    <path d="M20 6 9 17l-5-5" />
                  </svg>
                </button>
                <button class="conv-mini" type="button" title="取消" aria-label="取消" @click.stop="cancelRename">
                  <svg viewBox="0 0 24 24" aria-hidden="true">
                    <path d="M18 6 6 18M6 6l12 12" />
                  </svg>
                </button>
              </div>
            </template>
            <template v-else>
              <button class="conv-item" type="button" @click="openConversation(c)">
                <span class="conv-title">{{ c.title || shortId(c.conversationId) }}</span>
                <span class="conv-meta">{{ c.messageCount ?? 0 }} 轮 · {{ formatTime(c.lastMessageAt) }}</span>
              </button>
              <div class="conv-row-actions">
                <button class="conv-mini" type="button" title="重命名" aria-label="重命名" @click.stop="startRename(c)">
                  <svg viewBox="0 0 24 24" aria-hidden="true">
                    <path d="M12 20h9" />
                    <path d="M16.5 3.5a2.1 2.1 0 0 1 3 3L7 19l-4 1 1-4Z" />
                  </svg>
                </button>
                <button class="conv-mini danger" type="button" title="删除" aria-label="删除" @click.stop="deleteConversation(c)">
                  <svg viewBox="0 0 24 24" aria-hidden="true">
                    <path d="M3 6h18" />
                    <path d="M8 6V4h8v2" />
                    <path d="M19 6l-1 14H6L5 6" />
                    <path d="M10 11v6M14 11v6" />
                  </svg>
                </button>
              </div>
            </template>
          </div>
        </div>
        <p v-else class="muted conv-empty">
          {{ convLoading ? '加载中…' : '该 Agent 暂无会话' }}
        </p>
        <div class="conv-pager" v-if="convTotalPage > 1">
          <button class="btn-ghost" type="button" :disabled="convPageNum <= 1 || convLoading" @click="loadConversations(convPageNum - 1)">上一页</button>
          <span class="muted">{{ convPageNum }} / {{ convTotalPage }}</span>
          <button class="btn-ghost" type="button" :disabled="convPageNum >= convTotalPage || convLoading" @click="loadConversations(convPageNum + 1)">下一页</button>
        </div>

        <details class="conv-debug">
          <summary>运行轨迹与用量</summary>
          <div class="conv-id muted">会话 {{ conversationId || '（发送后自动生成）' }}</div>
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

          <div v-if="usage" class="panel-quiet" style="margin-top: 0.8rem">
            <div class="muted" style="font-size: 0.75rem; letter-spacing: 0.06em; text-transform: uppercase">用量</div>
            <div style="margin-top: 0.35rem; font-size: 0.9rem">
              latency {{ usage[AgentRunUsageKeys.LATENCY_MS] ?? '—' }} ms
              <span v-if="usage[AgentRunUsageKeys.TRACE_MODE]"> · {{ usage[AgentRunUsageKeys.TRACE_MODE] }}</span>
            </div>
          </div>
        </details>
      </aside>

      <section class="panel hud-corners chat-main">
        <div class="chat-head">
          <div class="chat-head-main">
            <strong>{{ currentAgent?.label || '未选择 Agent' }}</strong>
            <span class="muted chat-head-title">{{ conversationTitle }}</span>
          </div>
          <span class="pill" :class="streaming ? 'pill-brass' : 'pill-teal'">
            {{ streaming ? '生成中' : '就绪' }}
          </span>
        </div>
        <p v-if="error" class="error">{{ error }}</p>
        <div ref="streamEl" class="chat-stream">
          <div v-if="!messages.length" class="chat-empty">
            <h3>{{ currentAgent?.label || '选择一个 Agent' }}</h3>
            <p class="muted">发一条消息开始新会话，或从左侧会话历史继续之前的对话。</p>
          </div>
          <div
            v-for="(m, idx) in messages"
            :key="idx"
            class="msg"
            :class="m.role === 'user' ? 'msg-user' : 'msg-assistant'"
          >
            <div class="msg-meta">
              <span>{{ m.role === 'user' ? 'You' : (currentAgent?.label || 'AI DeepStack') }}</span>
              <span v-if="m.time" class="muted msg-time">{{ formatTime(m.time) }}</span>
            </div>
            <div v-if="m.reasoning" class="msg-reasoning">
              <button class="msg-reasoning-head" type="button" @click="m.reasoningOpen = !m.reasoningOpen">
                <span>{{ m.streaming && !m.content ? '思考中…' : '思考过程' }}</span>
                <span class="muted">{{ m.reasoningOpen ? '收起' : '展开' }}</span>
              </button>
              <pre v-show="m.reasoningOpen" class="msg-reasoning-body">{{ m.reasoning }}</pre>
            </div>
            <div class="msg-bubble" v-if="m.role === 'user'">{{ m.content }}</div>
            <div
              v-else
              class="msg-bubble markdown-body"
              :class="{ typing: m.streaming && !m.content }"
              v-html="renderMarkdown(m.content)"
            ></div>
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
import { computed, nextTick, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { get, post, put, del } from '../api/http'
import { streamSse, tryParseJson } from '../api/sse'
import { confirmDialog } from '../composables/useConfirm'
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
import { renderMarkdown } from '../utils/markdown'

const route = useRoute()
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
/** 手机端配置面板是否展开；桌面端由 CSS 忽略此状态，面板始终可见 */
const sideOpen = ref(false)
const conversations = ref([])
const convPageNum = ref(1)
const convPageSize = 10
const convTotal = ref(0)
const convTotalPage = ref(0)
const convLoading = ref(false)
const renamingId = ref('')
const renameDraft = ref('')
let abortCtrl = null

const currentAgent = computed(() => agents.value.find((a) => a.value === agentCode.value))
const agentOptions = computed(() => agents.value)
const conversationTitle = computed(() => {
  const active = conversations.value.find((c) => c.conversationId === conversationId.value)
  if (active?.title) return active.title
  return conversationId.value ? shortId(conversationId.value) : '新会话'
})

async function loadAgents() {
  try {
    const res = await get('/api/agents/options')
    agents.value = res.data || []
    const fromQuery = String(route.query.agent || '').trim()
    if (fromQuery && agents.value.some((a) => a.value === fromQuery)) {
      agentCode.value = fromQuery
    } else if (!agentCode.value && agents.value.length) {
      agentCode.value = agents.value.find((a) => a.value === 'demo_chat')?.value
        || agents.value[0].value
    }
  } catch (e) {
    error.value = e.message
  }
}

/** 会话历史分页（当前用户 + 当前 Agent） */
async function loadConversations(page = 1) {
  convLoading.value = true
  try {
    const res = await get('/api/chat/conversations/page', {
      pageNum: page,
      pageSize: convPageSize,
      agentCode: agentCode.value || undefined
    })
    const data = res?.data || {}
    conversations.value = data.list || []
    convPageNum.value = page
    convTotal.value = data.total ?? 0
    convTotalPage.value = data.totalPage ?? 0
  } catch (e) {
    error.value = e.message
  } finally {
    convLoading.value = false
  }
}

function shortId(id) {
  const text = String(id || '')
  return text.length > 10 ? `${text.slice(0, 10)}…` : text || '—'
}

function formatTime(value) {
  const text = String(value || '')
  return text ? text.replace('T', ' ').slice(0, 16) : '—'
}

/** 选中历史会话：恢复 Agent、会话 ID 与消息列表 */
async function openConversation(c) {
  if (!c?.conversationId) return
  abortStream()
  error.value = ''
  timeline.value = []
  usage.value = null
  conversationId.value = c.conversationId
  if (c.agentCode) agentCode.value = c.agentCode
  messages.value = []
  try {
    const res = await get(`/api/chat/conversations/${encodeURIComponent(c.conversationId)}/messages`)
    messages.value = (res?.data || []).map((m) => ({
      role: m.role === 'assistant' ? 'assistant' : 'user',
      content: m.content || '',
      reasoning: '',
      reasoningOpen: false,
      cards: [],
      streaming: false,
      time: m.createTime
    }))
    await scrollBottom()
  } catch (e) {
    error.value = e.message
  }
}

/** 重命名：行内编辑 */
function startRename(c) {
  renamingId.value = c.conversationId
  renameDraft.value = c.title || ''
}

function cancelRename() {
  renamingId.value = ''
  renameDraft.value = ''
}

async function saveRename(c) {
  const title = renameDraft.value.trim()
  if (!title) {
    error.value = '标题不能为空'
    return
  }
  try {
    await put(`/api/chat/conversations/${encodeURIComponent(c.conversationId)}`, { title })
    c.title = title
    cancelRename()
  } catch (e) {
    error.value = e.message
  }
}

/** 删除会话（含历史消息），需二次确认 */
async function deleteConversation(c) {
  const ok = await confirmDialog({
    title: '删除会话',
    message: '删除后该会话的历史消息也会被清除，且不可恢复。',
    confirmText: '删除'
  })
  if (!ok) return
  try {
    await del(`/api/chat/conversations/${encodeURIComponent(c.conversationId)}`)
    if (conversationId.value === c.conversationId) resetSession()
    const page = conversations.value.length === 1 && convPageNum.value > 1
      ? convPageNum.value - 1
      : convPageNum.value
    await loadConversations(page)
  } catch (e) {
    error.value = e.message
  }
}

watch(() => route.query.agent, (code) => {
  const next = String(code || '').trim()
  if (next && next !== agentCode.value && agents.value.some((a) => a.value === next)) {
    agentCode.value = next
    resetSession()
    loadConversations(1)
  }
})

function onAgentChange() {
  resetSession()
  loadConversations(1)
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
  const wasNew = !conversationId.value
  error.value = ''
  usage.value = null
  timeline.value = []
  messages.value.push({ role: 'user', content: text, time: new Date().toISOString() })
  const assistant = reactive({ role: 'assistant', content: '', reasoning: '', reasoningOpen: true, cards: [], streaming: true, time: new Date().toISOString() })
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
    // 刷新历史列表：新会话排到第一页，已有会话保持当前页
    await loadConversations(wasNew ? 1 : convPageNum.value)
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
  if (event === E.REASONING) {
    assistant.reasoning += typeof parsed === 'string' ? parsed : data
    return
  }
  if (event === E.USAGE) {
    usage.value = parsed && typeof parsed === 'object' ? parsed : {}
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
        title: `流程图 · ${p.statusName || (p.statusCode ?? 'DONE')}`,
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
      if (!assistant.content && assistant.reasoning) assistant.reasoningOpen = false
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

onMounted(async () => {
  await loadAgents()
  await loadConversations(1)
})
onUnmounted(abortStream)
</script>

<style scoped>
.composer textarea {
  border-radius: 16px;
  min-height: 96px;
}
.conv-new {
  width: 100%;
}
.conv-head {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  gap: 0.5rem;
  margin: 0.9rem 0 0.45rem;
  font-size: 0.78rem;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: var(--muted);
}
.chat-side {
  display: flex;
  flex-direction: column;
  min-height: 0;
}
.conv-list {
  display: flex;
  flex-direction: column;
  gap: 0.4rem;
  flex: 1;
  min-height: 80px;
  max-height: none;
  overflow: auto;
}
.conv-row {
  display: flex;
  align-items: stretch;
  gap: 0.3rem;
}
.conv-row.active .conv-item {
  background: var(--cyan-soft);
  border-color: var(--cyan-bright);
}
.conv-item {
  display: block;
  flex: 1;
  min-width: 0;
  text-align: left;
  padding: 0.5rem 0.6rem;
  border-radius: 8px;
  border: 1px solid var(--line-strong);
  background: rgba(8, 14, 26, 0.45);
  color: var(--ink);
  cursor: pointer;
}
.conv-item:hover {
  border-color: var(--cyan-bright);
}
.conv-row-actions {
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 0.2rem;
  opacity: 0;
  transition: opacity 0.15s ease;
}
.conv-row:hover .conv-row-actions,
.conv-row:focus-within .conv-row-actions {
  opacity: 1;
}
.conv-mini {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 1.4rem;
  height: 1.4rem;
  padding: 0;
  border: 1px solid var(--line-strong);
  border-radius: 6px;
  background: transparent;
  color: var(--muted);
  cursor: pointer;
}
.conv-mini svg {
  width: 13px;
  height: 13px;
  fill: none;
  stroke: currentColor;
  stroke-width: 1.8;
  stroke-linecap: round;
  stroke-linejoin: round;
}
.conv-mini:hover {
  color: var(--ink);
  border-color: var(--cyan-bright);
}
.conv-mini.danger:hover {
  color: #ff9b9b;
  border-color: rgba(255, 107, 107, 0.5);
}
.conv-rename {
  flex: 1;
  min-width: 0;
  padding: 0.45rem 0.5rem;
  border-radius: 8px;
  border: 1px solid var(--cyan-bright);
  background: rgba(8, 14, 26, 0.6);
  color: var(--ink);
  font-size: 0.82rem;
}
@media (max-width: 980px) {
  .conv-row-actions {
    opacity: 1;
  }
}
.conv-title {
  display: block;
  font-size: 0.85rem;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.conv-meta {
  display: block;
  margin-top: 0.2rem;
  font-size: 0.72rem;
  color: var(--muted);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.conv-empty {
  margin: 0;
  font-size: 0.82rem;
}
.conv-pager {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 0.4rem;
  margin-top: 0.4rem;
  font-size: 0.75rem;
}
.conv-debug {
  margin-top: 1rem;
  padding-top: 0.7rem;
  border-top: 1px solid var(--line);
}
.conv-debug summary {
  cursor: pointer;
  margin-bottom: 0.5rem;
  font-size: 0.8rem;
  color: var(--ink-soft);
  user-select: none;
}
.conv-id {
  margin-bottom: 0.5rem;
  font-size: 0.72rem;
  word-break: break-all;
}
.chat-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.75rem;
  padding-bottom: 0.6rem;
  margin-bottom: 0.6rem;
  border-bottom: 1px solid var(--line);
}
.chat-head-main {
  display: flex;
  flex-direction: column;
  gap: 0.15rem;
  min-width: 0;
}
.chat-head-title {
  font-size: 0.8rem;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.chat-empty {
  margin: auto;
  padding: 2.5rem 1rem;
  text-align: center;
}
.chat-empty h3 {
  margin: 0 0 0.4rem;
  font-family: var(--font-brand);
  font-weight: 400;
  font-size: 1.4rem;
  color: var(--ink);
}
.msg-meta {
  display: flex;
  justify-content: space-between;
  gap: 0.6rem;
}
.msg-time {
  letter-spacing: 0;
  text-transform: none;
}
</style>
