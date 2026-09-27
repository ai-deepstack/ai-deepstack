<template>
  <div class="runs-page">
    <header class="page-head runs-head">
      <div>
        <h1>运行记录</h1>
        <p>查看调用成功率、失败与慢请求；点详情可看节点时间线。</p>
      </div>
    </header>

    <div class="panel filter-panel">
      <div class="filter-bar filter-bar-top">
        <label class="filter-field">
          <span class="filter-label">时间窗</span>
          <PropSelect
            v-model="hours"
            class="filter-select"
            :options="hoursOptions"
            placeholder="时间窗"
            @change="reloadAll"
          />
        </label>
        <label class="filter-field">
          <span class="filter-label">模式</span>
          <PropSelect
            v-model="filter.orchestrateMode"
            class="filter-select"
            :options="modeOptions"
            placeholder="全部模式"
            @change="reloadAll"
          />
        </label>
        <label class="filter-field">
          <span class="filter-label">智能体</span>
          <input
            v-model="filter.agentCode"
            class="filter-input"
            placeholder="编码"
            @keyup.enter="reloadAll()"
          />
        </label>
        <label class="filter-field">
          <span class="filter-label">traceId</span>
          <input
            v-model="filter.traceId"
            class="filter-input filter-input-wide"
            placeholder="完整或部分"
            @keyup.enter="loadList(1)"
          />
        </label>
        <label class="filter-field">
          <span class="filter-label">状态</span>
          <PropSelect
            v-model="filter.status"
            class="filter-select"
            :options="statusOptions"
            placeholder="全部状态"
            @change="() => loadList(1)"
          />
        </label>
        <span v-if="filter.agentId" class="pill filter-chip">
          智能体 #{{ filter.agentId }}
          <button type="button" class="chip-x" aria-label="清除" @click="clearAgentId">×</button>
        </span>
        <div class="filter-actions">
          <button class="btn" type="button" @click="reloadAll()">查询</button>
          <button class="btn-ghost" type="button" :disabled="exporting" @click="exportJsonl">
            {{ exporting ? '导出中…' : '导出' }}
          </button>
          <button class="btn-ghost" type="button" @click="reloadAll()">刷新</button>
        </div>
      </div>
    </div>

    <p v-if="error" class="error">{{ error }}</p>

    <div class="kpi-strip" v-if="overview">
      <div class="kpi-item" v-for="k in kpiCards" :key="k.label">
        <div class="kpi-label">{{ k.label }}</div>
        <div class="kpi-value">{{ k.value }}</div>
      </div>
    </div>

    <div class="panel list-panel">
      <div class="list-toolbar">
        <h2 class="section-title">运行列表</h2>
        <span class="muted list-count" v-if="total > 0">共 {{ total }} 条</span>
      </div>

      <div class="table-wrap">
        <table class="table" v-if="list.length">
          <thead>
            <tr>
              <th>时间</th>
              <th>智能体</th>
              <th>模式</th>
              <th>状态</th>
              <th>耗时</th>
              <th>Token</th>
              <th>错误</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in list" :key="asId(item.id)">
              <td class="muted nowrap">{{ formatTime(item.createTime) }}</td>
              <td><code>{{ item.agentCode || item.agentId }}</code></td>
              <td>{{ item.orchestrateModeName || item.orchestrateMode }}</td>
              <td>
                <span class="pill" :class="statusPill(item.status)">{{ item.statusName || item.status }}</span>
              </td>
              <td class="nowrap">{{ formatMs(item.durationMs) }}</td>
              <td>{{ item.totalTokens ?? '—' }}</td>
              <td class="muted err-cell">{{ item.errorCode || '—' }}</td>
              <td>
                <button class="btn-ghost" type="button" @click="openDetail(item.id)">详情</button>
              </td>
            </tr>
          </tbody>
        </table>
        <p v-else class="muted empty-hint">暂无运行记录</p>
      </div>

      <div class="list-pager" v-if="total > 0">
        <span class="muted">第 {{ pageNum }} / {{ Math.max(totalPage, 1) }} 页</span>
        <div class="row-actions" v-if="totalPage > 1">
          <button class="btn-ghost" type="button" :disabled="pageNum <= 1" @click="loadList(pageNum - 1)">上一页</button>
          <button class="btn-ghost" type="button" :disabled="pageNum >= totalPage" @click="loadList(pageNum + 1)">下一页</button>
        </div>
      </div>
    </div>

    <details class="panel insights-panel" v-if="overview" :open="insightsOpen">
      <summary class="insights-summary" @click.prevent="insightsOpen = !insightsOpen">
        <span>概况与异常</span>
        <span class="muted insights-meta">
          缓存 {{ overview.engineHealth?.compileCacheSize ?? '—' }}
          · 执行中 {{ overview.engineHealth?.runningExecutions ?? '—' }}
          · 待确认 {{ overview.kpi?.waitingHuman ?? 0 }}
        </span>
      </summary>
      <div class="insights-grid">
        <section class="insight-card">
          <h3>错误码</h3>
          <div class="insight-body" v-if="errorCodeRows.length">
            <table class="table compact">
              <tbody>
                <tr v-for="row in errorCodeRows" :key="row.code">
                  <td><code>{{ row.code }}</code></td>
                  <td class="num">{{ row.count }}</td>
                </tr>
              </tbody>
            </table>
          </div>
          <p v-else class="muted empty-mini">暂无</p>
        </section>
        <section class="insight-card">
          <h3>失败 Top</h3>
          <div class="insight-body" v-if="overview.failTopAgents?.length">
            <table class="table compact">
              <tbody>
                <tr v-for="row in overview.failTopAgents" :key="row.agentCode">
                  <td><code>{{ row.agentCode }}</code></td>
                  <td class="num">{{ row.failed }}</td>
                </tr>
              </tbody>
            </table>
          </div>
          <p v-else class="muted empty-mini">暂无</p>
        </section>
        <section class="insight-card">
          <h3>慢请求</h3>
          <div class="insight-body" v-if="overview.slowRuns?.length">
            <table class="table compact">
              <tbody>
                <tr v-for="r in overview.slowRuns" :key="'s' + r.id">
                  <td><code>{{ r.agentCode || r.agentId }}</code></td>
                  <td class="num nowrap">{{ formatMs(r.durationMs) }}</td>
                  <td>
                    <button class="btn-ghost linkish" type="button" @click="openDetail(r.id)">查看</button>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
          <p v-else class="muted empty-mini">暂无</p>
        </section>
        <section class="insight-card">
          <h3>最近失败 / 待确认</h3>
          <div class="insight-body" v-if="overview.recentFailedOrWaiting?.length">
            <table class="table compact">
              <tbody>
                <tr v-for="r in overview.recentFailedOrWaiting" :key="'f' + r.id">
                  <td><code>{{ r.agentCode || r.agentId }}</code></td>
                  <td class="nowrap">{{ r.statusName || r.status }}</td>
                  <td>
                    <button class="btn-ghost linkish" type="button" @click="openDetail(r.id)">查看</button>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
          <p v-else class="muted empty-mini">暂无</p>
        </section>
      </div>
    </details>

    <div v-if="detail" class="drawer-mask" @click.self="detail = null">
      <aside class="drawer" style="width: min(720px, 100vw)">
        <h2>运行详情 #{{ detail.id }}</h2>
        <p class="muted">{{ detail.agentCode }} · {{ detail.orchestrateModeName }} · {{ detail.statusName }}</p>
        <div class="field"><label>traceId</label><code>{{ detail.traceId || '—' }}</code></div>
        <div class="field"><label>conversationId</label><code>{{ detail.conversationId || '—' }}</code></div>
        <div class="field"><label>threadId</label><code>{{ detail.threadId || '—' }}</code></div>
        <div class="field"><label>耗时 / Token / 人工等待</label>
          <span>{{ formatMs(detail.durationMs) }} · {{ detail.totalTokens ?? 0 }} · {{ formatMs(detail.hitlWaitMs) }}</span>
        </div>
        <div class="field" v-if="detail.errorCode || detail.errorMessage">
          <label>错误</label>
          <p class="error" style="margin: 0">{{ detail.errorCode }} {{ detail.errorMessage }}</p>
        </div>
        <div class="field" v-if="detail.userMessage">
          <label>用户消息</label>
          <pre class="obs-pre">{{ detail.userMessage }}</pre>
        </div>
        <div class="field" v-if="detail.result">
          <label>结果</label>
          <pre class="obs-pre">{{ detail.result }}</pre>
        </div>
        <div class="field" v-if="stagesObj">
          <label>阶段耗时</label>
          <pre class="obs-pre">{{ JSON.stringify(stagesObj, null, 2) }}</pre>
        </div>
        <div class="field" v-if="nodes.length">
          <label>节点时间线</label>
          <div class="table-wrap">
            <table class="table">
              <thead>
                <tr><th>节点</th><th>类型</th><th>状态</th><th>耗时</th><th>错误</th></tr>
              </thead>
              <tbody>
                <tr v-for="(n, i) in nodes" :key="i">
                  <td>{{ n.nodeName || n.name || n.nodeId || '—' }}</td>
                  <td class="muted">{{ n.nodeType || n.type || '—' }}</td>
                  <td>{{ n.statusName || (n.status ?? '—') }}</td>
                  <td>{{ formatMs(n.durationMs) }}</td>
                  <td class="muted">{{ n.error || n.errorMessage || '' }}</td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>
        <div class="drawer-actions">
          <button class="btn-ghost" type="button" @click="detail = null">关闭</button>
        </div>
      </aside>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { get, getToken, pageList } from '../api/http'
import PropSelect from '../components/PropSelect.vue'
import { GraphRunStatus, OrchestrateMode } from '../constants/enums'
import { asId } from '../utils/ids'

const route = useRoute()
const hours = ref(24)
const overview = ref(null)
const list = ref([])
const pageNum = ref(1)
const pageSize = 20
const total = ref(0)
const totalPage = ref(0)
const error = ref('')
const detail = ref(null)
const exporting = ref(false)
const insightsOpen = ref(false)
const filter = reactive({
  agentId: '',
  agentCode: '',
  traceId: '',
  status: '',
  orchestrateMode: ''
})

const hoursOptions = [
  { value: 1, label: '近 1 小时' },
  { value: 24, label: '近 24 小时' },
  { value: 168, label: '近 7 天' }
]
const modeOptions = [
  { value: '', label: '全部模式' },
  { value: OrchestrateMode.CHAT, label: '对话' },
  { value: OrchestrateMode.GRAPH, label: '流程图' }
]
const statusOptions = [
  { value: '', label: '全部状态' },
  { value: GraphRunStatus.RUNNING, label: '运行中' },
  { value: GraphRunStatus.SUCCESS, label: '成功' },
  { value: GraphRunStatus.FAILED, label: '失败' },
  { value: GraphRunStatus.CANCELLED, label: '已取消' },
  { value: GraphRunStatus.WAITING_HUMAN, label: '等待人工' }
]

const kpiCards = computed(() => {
  const k = overview.value?.kpi || {}
  const rate = k.successRate != null ? (k.successRate * 100).toFixed(1) + '%' : '—'
  return [
    { label: '调用', value: k.total ?? 0 },
    { label: '成功率', value: rate },
    { label: '失败', value: k.failed ?? 0 },
    { label: '待确认', value: k.waitingHuman ?? 0 },
    { label: '中位 / P95', value: `${k.p50DurationMs ?? 0} / ${k.p95DurationMs ?? 0} ms` },
    { label: 'Token', value: k.totalTokens ?? 0 }
  ]
})

const errorCodeRows = computed(() => {
  const map = overview.value?.errorCodes || {}
  return Object.entries(map)
    .map(([code, count]) => ({ code, count }))
    .sort((a, b) => b.count - a.count)
})

const stagesObj = computed(() => parseJson(detail.value?.stages))
const nodes = computed(() => {
  const raw = parseJson(detail.value?.nodeExecutions)
  return Array.isArray(raw) ? raw : []
})

function parseJson(v) {
  if (v == null || v === '') return null
  if (typeof v === 'object') return v
  try {
    return JSON.parse(v)
  } catch {
    return null
  }
}

function formatTime(t) {
  if (!t) return '—'
  return String(t).replace('T', ' ').slice(0, 19)
}

function formatMs(v) {
  if (v == null || v === '') return '—'
  return `${v} ms`
}

function statusPill(status) {
  if (status === GraphRunStatus.SUCCESS) return 'pill-ok'
  if (status === GraphRunStatus.FAILED) return 'pill-off'
  if (status === GraphRunStatus.WAITING_HUMAN) return 'pill-brass'
  return ''
}

function clearAgentId() {
  filter.agentId = ''
  reloadAll()
}

async function loadOverview() {
  const res = await get('/api/agent-runs/overview', {
    hours: hours.value,
    agentId: asId(filter.agentId) || undefined,
    orchestrateMode: filter.orchestrateMode === '' ? undefined : Number(filter.orchestrateMode)
  })
  overview.value = res.data
}

async function loadList(page = 1) {
  const next = Number(page)
  pageNum.value = Number.isFinite(next) && next > 0 ? next : 1
  const res = await get('/api/agent-runs', {
    pageNum: pageNum.value,
    pageSize,
    agentId: asId(filter.agentId) || undefined,
    agentCode: filter.agentCode || undefined,
    traceId: filter.traceId || undefined,
    status: filter.status === '' ? undefined : Number(filter.status),
    orchestrateMode: filter.orchestrateMode === '' ? undefined : Number(filter.orchestrateMode)
  })
  list.value = pageList(res)
  total.value = res.data?.total ?? 0
  totalPage.value = res.data?.totalPage ?? 0
}

async function openDetail(id) {
  const runId = asId(id)
  if (!runId) {
    error.value = '无效的运行 ID'
    return
  }
  error.value = ''
  try {
    const res = await get(`/api/agent-runs/${encodeURIComponent(runId)}`)
    detail.value = res.data
  } catch (e) {
    error.value = e.message
  }
}

async function exportJsonl() {
  exporting.value = true
  error.value = ''
  try {
    const params = new URLSearchParams(
      Object.entries({
        agentCode: filter.agentCode || undefined,
        agentId: asId(filter.agentId) || undefined
      }).filter(([, v]) => v !== undefined && v !== null && v !== '')
    )
    const qs = params.toString() ? `?${params}` : ''
    const headers = {}
    const token = getToken()
    if (token) headers.Authorization = `Bearer ${token}`
    const res = await fetch(`/api/agent-runs/export${qs}`, { headers })
    if (!res.ok) {
      throw new Error(`导出失败 (${res.status})`)
    }
    const blob = await res.blob()
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = 'agent-runs-export.jsonl'
    a.click()
    URL.revokeObjectURL(url)
  } catch (e) {
    error.value = e.message
  } finally {
    exporting.value = false
  }
}

async function reloadAll() {
  error.value = ''
  try {
    await Promise.all([loadOverview(), loadList(1)])
  } catch (e) {
    error.value = e.message
  }
}

function applyRouteQuery() {
  const q = route.query
  filter.agentId = q.agentId != null && q.agentId !== '' ? asId(q.agentId) : ''
  filter.agentCode = q.agentCode != null && q.agentCode !== '' ? String(q.agentCode) : filter.agentCode
  filter.status = q.status != null && q.status !== '' ? Number(q.status) : ''
}

watch(() => [route.query.agentId, route.query.agentCode, route.query.status], () => {
  applyRouteQuery()
  reloadAll()
})

onMounted(() => {
  applyRouteQuery()
  reloadAll()
})
</script>

<style scoped>
.runs-page {
  display: flex;
  flex-direction: column;
  gap: 0.85rem;
}
.runs-head {
  margin-bottom: 0;
}

.filter-panel {
  margin: 0;
  padding: 0.85rem 1rem;
}
.filter-bar {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-end;
  gap: 0.65rem 0.75rem;
}
.filter-bar-top {
  width: 100%;
}
.filter-field {
  display: flex;
  flex-direction: column;
  gap: 0.28rem;
  min-width: 0;
}
.filter-label {
  font-size: 0.72rem;
  color: var(--muted, #8a93a6);
  line-height: 1;
}
.filter-input,
.filter-select {
  width: 9.5rem;
  min-width: 0;
}
.filter-select :deep(.prop-dd-trigger),
.filter-field :deep(.prop-dd-trigger) {
  width: 100%;
}
.filter-input-wide {
  width: 14rem;
}
.filter-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.45rem;
  margin-left: auto;
}
.filter-chip {
  display: inline-flex;
  align-items: center;
  gap: 0.3rem;
  align-self: flex-end;
  margin-bottom: 0.15rem;
}
.chip-x {
  border: 0;
  background: transparent;
  color: inherit;
  cursor: pointer;
  font-size: 1rem;
  line-height: 1;
  padding: 0 0.15rem;
  opacity: 0.75;
}
.chip-x:hover {
  opacity: 1;
}

.kpi-strip {
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  gap: 0.55rem;
}
.kpi-item {
  padding: 0.7rem 0.85rem;
  border-radius: 12px;
  background: rgba(12, 18, 32, 0.55);
  border: 1px solid rgba(125, 139, 163, 0.16);
}
.kpi-label {
  font-size: 0.72rem;
  color: var(--muted, #8a93a6);
}
.kpi-value {
  margin-top: 0.2rem;
  font-size: 1.05rem;
  font-weight: 650;
  color: var(--cyan-bright);
  line-height: 1.25;
  word-break: break-word;
}

.list-panel {
  margin: 0;
}
.list-toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  justify-content: space-between;
  gap: 0.5rem;
  margin-bottom: 0.85rem;
}
.section-title {
  margin: 0;
  font-size: 1rem;
}
.list-count {
  font-size: 0.82rem;
}
.nowrap {
  white-space: nowrap;
}
.err-cell {
  max-width: 8rem;
  overflow: hidden;
  text-overflow: ellipsis;
}
.empty-hint {
  margin: 1.2rem 0 0.4rem;
}
.list-pager {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.75rem;
  margin-top: 0.85rem;
  flex-wrap: wrap;
}

.insights-panel {
  margin: 0;
  padding-top: 0.65rem;
  padding-bottom: 0.85rem;
}
.insights-summary {
  list-style: none;
  cursor: pointer;
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  justify-content: space-between;
  gap: 0.5rem 1rem;
  font-weight: 600;
  user-select: none;
}
.insights-summary::-webkit-details-marker {
  display: none;
}
.insights-summary::before {
  content: '▸';
  display: inline-block;
  margin-right: 0.4rem;
  color: var(--muted);
  transition: transform 0.15s ease;
}
.insights-panel[open] .insights-summary::before {
  transform: rotate(90deg);
}
.insights-meta {
  font-weight: 400;
  font-size: 0.82rem;
}
.insights-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 0.75rem;
  margin-top: 0.85rem;
}
.insight-card {
  min-width: 0;
  padding: 0.65rem 0.75rem;
  border-radius: 10px;
  background: rgba(0, 0, 0, 0.18);
  border: 1px solid rgba(125, 139, 163, 0.12);
}
.insight-card h3 {
  margin: 0 0 0.45rem;
  font-size: 0.82rem;
  font-weight: 600;
}
.insight-body {
  max-height: 11rem;
  overflow: auto;
}
.table.compact td {
  padding: 0.35rem 0.4rem;
  font-size: 0.82rem;
}
.table.compact .num {
  text-align: right;
  white-space: nowrap;
}
.empty-mini {
  margin: 0.35rem 0 0;
  font-size: 0.82rem;
}
.linkish {
  padding: 0.1rem 0.35rem;
  font-size: 0.78rem;
}

.obs-pre {
  margin: 0;
  max-height: 12rem;
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-word;
  font-size: 0.78rem;
  background: rgba(0, 0, 0, 0.25);
  padding: 0.6rem;
  border-radius: 6px;
}

@media (max-width: 1100px) {
  .kpi-strip {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
  .insights-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .filter-actions {
    margin-left: 0;
  }
}
@media (max-width: 700px) {
  .kpi-strip {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .insights-grid {
    grid-template-columns: 1fr;
  }
  .filter-field {
    flex: 1 1 9rem;
  }
  .filter-input,
  .filter-select,
  .filter-input-wide {
    width: 100%;
  }
  .filter-select :deep(.prop-dd-trigger) {
    width: 100%;
  }
}
</style>
