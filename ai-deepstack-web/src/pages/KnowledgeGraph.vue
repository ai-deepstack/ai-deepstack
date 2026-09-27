<template>
  <div class="graph-page kb-graph-page">
    <header class="page-head">
      <div>
        <h1>知识图谱 · {{ baseCode }}</h1>
        <p>
          浏览文档、切片和实体。点击节点可看属性，并展开相邻节点。
        </p>
      </div>
      <div class="row-actions">
        <router-link class="btn-ghost" to="/knowledge">返回知识库</router-link>
        <button class="btn" type="button" :disabled="loading" @click="reloadFromScratch">
          {{ loading ? '加载中…' : '刷新' }}
        </button>
      </div>
    </header>

    <p v-if="error" class="error">{{ error }}</p>
    <p v-if="viewMessage" class="muted">{{ viewMessage }}</p>

    <div class="panel kb-toolbar">
      <div class="field-row">
        <div class="field">
          <label>documentId（可选）</label>
          <input v-model="documentId" placeholder="留空则从首个 chunk 扩展" />
        </div>
        <div class="field">
          <label>hops</label>
          <PropNumber v-model="hops" :min="1" :max="5" :nullable="false" />
        </div>
        <div class="field">
          <label>limit</label>
          <PropNumber v-model="limit" :min="1" :max="500" :nullable="false" />
        </div>
        <div class="field field-action">
          <label>&nbsp;</label>
          <button class="btn-ghost" type="button" :disabled="loading" @click="reloadFromScratch">
            应用条件
          </button>
        </div>
      </div>
      <p class="muted" style="margin: 0.45rem 0 0">
        scope={{ scopeText }} · startKey={{ startKeyText }} ·
        节点 {{ flowNodes.length }} · 边 {{ flowEdges.length }}
        <template v-if="summary">
          · 全库约 {{ summary.nodeCount ?? 0 }} 节点 / {{ summary.edgeCount ?? 0 }} 边
          <span v-if="summary.lastGraphTime"> · 最近写图 {{ summary.lastGraphTime }}</span>
        </template>
      </p>
    </div>

    <div class="graph-studio kb-graph-studio">
      <aside class="panel kb-legend">
        <h3>图例</h3>
        <div class="legend-item"><span class="swatch doc" /> Document</div>
        <div class="legend-item"><span class="swatch chk" /> Chunk</div>
        <div class="legend-item"><span class="swatch ent" /> Entity</div>
        <p class="muted" style="margin-top: 0.85rem; font-size: 0.68rem; line-height: 1.45">
          画布只读：可缩放、拖拽节点查看布局；不支持手工增删边。
          节点较多时请缩小 hops / limit，或从选中节点「展开邻居」懒加载。
        </p>
      </aside>

      <div class="graph-canvas" tabindex="0">
        <VueFlow
          id="kb-knowledge-graph"
          v-model:nodes="flowNodes"
          v-model:edges="flowEdges"
          :node-types="nodeTypes"
          fit-view-on-init
          :nodes-draggable="true"
          :nodes-connectable="false"
          :elements-selectable="true"
          :edges-updatable="false"
          :pan-on-drag="true"
          :default-edge-options="{ type: 'default', animated: false, selectable: true }"
          @node-click="onNodeClick"
          @pane-click="onPaneClick"
        >
          <Background :gap="20" variant="dots" pattern-color="#3a465c" />
          <Controls />
          <MiniMap
            pannable
            zoomable
            :node-color="miniMapNodeColor"
            node-stroke-color="#3de0c5"
            :node-stroke-width="1.5"
            :node-border-radius="4"
            mask-color="rgba(7, 13, 24, 0.72)"
          />
        </VueFlow>
      </div>

      <aside class="panel kb-props">
        <h3>{{ selectedNode ? '节点属性' : '属性' }}</h3>
        <template v-if="selectedNode">
          <div class="field">
            <label>key</label>
            <input :value="selectedNode.id" disabled />
          </div>
          <div class="field">
            <label>label</label>
            <input :value="selectedNode.data.label" disabled />
          </div>
          <div class="field">
            <label>属性</label>
            <pre class="prop-pre">{{ propJson(selectedNode.data.properties) }}</pre>
          </div>
          <button
            class="btn"
            type="button"
            style="width: 100%"
            :disabled="loading || expanding"
            @click="expandSelected"
          >
            {{ expanding ? '展开中…' : '展开邻居' }}
          </button>
        </template>
        <p v-else class="muted" style="font-size: 0.72rem; line-height: 1.45">
          点击画布上的节点查看属性；选中后可从该节点继续 expand（懒加载合并到当前图）。
        </p>
      </aside>
    </div>
  </div>
</template>

<script setup>
/**
 * 知识库图谱页（Vue Flow 只读画布）。
 * - 初次按 documentId / bootstrap chunk 拉邻域
 * - 「展开邻居」带 startKey 再请求，合并节点/边，避免一次拉全库
 */
import { computed, markRaw, nextTick, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { VueFlow, useVueFlow } from '@vue-flow/core'
import { Background } from '@vue-flow/background'
import { Controls } from '@vue-flow/controls'
import { MiniMap } from '@vue-flow/minimap'
import '@vue-flow/core/dist/style.css'
import '@vue-flow/core/dist/theme-default.css'
import '@vue-flow/controls/dist/style.css'
import '@vue-flow/minimap/dist/style.css'
import { get } from '../api/http'
import KnowledgeGraphNode from '../components/KnowledgeGraphNode.vue'
import PropNumber from '../components/PropNumber.vue'
import { forceLayout } from '../utils/kbForceLayout'

const route = useRoute()
const baseCode = computed(() => String(route.params.baseCode || ''))
const documentId = ref(route.query.documentId ? String(route.query.documentId) : '')
const hops = ref(Number(route.query.hops) || 1)
const limit = ref(Number(route.query.limit) || 80)

const viewMessage = ref('')
const scopeText = ref('—')
const startKeyText = ref('—')
const summary = ref(null)
const error = ref('')
const loading = ref(false)
const expanding = ref(false)

const flowNodes = ref([])
const flowEdges = ref([])
const selectedNode = ref(null)

const nodeTypes = {
  kbNode: markRaw(KnowledgeGraphNode)
}

const { fitView } = useVueFlow({ id: 'kb-knowledge-graph' })

/** 按 label 分色给 minimap */
function miniMapNodeColor(node) {
  const label = (node?.data?.label || '').toLowerCase()
  if (label === 'document') return '#4ea8ff'
  if (label === 'chunk') return '#3de0c5'
  if (label === 'entity') return '#e8b95c'
  return '#94a3b8'
}

function displayTitle(apiNode) {
  const props = apiNode?.properties || {}
  const label = apiNode?.label || 'Node'
  if (label === 'Entity' && props.name) return String(props.name)
  if (label === 'Document' && props.title) return String(props.title)
  if (label === 'Chunk' && props.preview) {
    const p = String(props.preview)
    return p.length > 36 ? p.slice(0, 36) + '…' : p
  }
  const key = apiNode?.key || ''
  return key.length > 28 ? key.slice(0, 28) + '…' : key || label
}

/**
 * 分层布局：Document / Chunk / Entity 分行，同层横向均分。
 * 已有 position 的节点（懒加载合并）保持原坐标，只给新节点排位。
 */
function layoutNewNodes(apiNodes, existingIds) {
  const groups = { Document: [], Chunk: [], Entity: [], Other: [] }
  for (const n of apiNodes) {
    if (!n?.key || existingIds.has(n.key)) continue
    const label = n.label || 'Other'
    if (groups[label]) groups[label].push(n)
    else groups.Other.push(n)
  }
  const yOf = { Document: 40, Chunk: 200, Entity: 360, Other: 520 }
  const result = []
  for (const [label, list] of Object.entries(groups)) {
    const y = yOf[label] ?? 520
    const gap = 190
    const startX = 40
    list.forEach((n, i) => {
      result.push({
        id: n.key,
        type: 'kbNode',
        position: { x: startX + i * gap, y },
        data: {
          label: n.label || 'Node',
          displayTitle: displayTitle(n),
          properties: n.properties || {}
        },
        selectable: true,
        draggable: true,
        connectable: false
      })
    })
  }
  return result
}

function toFlowEdges(apiEdges, existingEdgeIds) {
  const out = []
  if (!apiEdges) return out
  apiEdges.forEach((e, i) => {
    if (!e?.fromKey || !e?.toKey) return
    const id = `${e.fromKey}->${e.type || 'REL'}->${e.toKey}`
    if (existingEdgeIds.has(id)) return
    existingEdgeIds.add(id)
    out.push({
      id: id || `e-${i}`,
      source: e.fromKey,
      target: e.toKey,
      label: e.type || '',
      selectable: true,
      updatable: false,
      style: { stroke: 'rgba(148,163,184,0.55)' },
      labelStyle: { fill: '#94a3b8', fontSize: 10 },
      labelBgStyle: { fill: 'rgba(7,13,24,0.85)' },
      labelBgPadding: [2, 4],
      labelBgBorderRadius: 4
    })
  })
  return out
}

function propJson(props) {
  if (!props || typeof props !== 'object') return '—'
  try {
    return JSON.stringify(props, null, 2)
  } catch {
    return String(props)
  }
}

function onNodeClick({ node }) {
  selectedNode.value = node
}

function onPaneClick() {
  selectedNode.value = null
}

/**
 * 合并 API 邻域到画布（首次加载 replace=false 时也会在空图上合并）。
 */
async function fetchAndMerge({ startKey, replace }) {
  if (!baseCode.value) return
  const params = { hops: hops.value, limit: limit.value }
  if (startKey) {
    params.startKey = startKey
  } else if (documentId.value.trim()) {
    params.documentId = documentId.value.trim()
  }

  console.info('[kb-graph] fetch', { baseCode: baseCode.value, params, replace })
  const res = await get(`/api/kb/${encodeURIComponent(baseCode.value)}/graph`, params)
  const data = res.data || {}
  viewMessage.value = data.message || ''
  scopeText.value = data.scope || '—'
  startKeyText.value = data.startKey || '—'

  const apiNodes = data.nodes || []
  const apiEdges = data.edges || []

  if (replace) {
    flowNodes.value = []
    flowEdges.value = []
    selectedNode.value = null
  }

  const existingIds = new Set(flowNodes.value.map((n) => n.id))
  const newNodes = layoutNewNodes(apiNodes, existingIds)
  // 起点若不在邻居列表里，API 会补占位节点；layout 已覆盖
  // 若 API 返回了已存在节点的更新属性，刷新 data
  const propByKey = new Map(apiNodes.map((n) => [n.key, n]))
  flowNodes.value = [
    ...flowNodes.value.map((n) => {
      const fresh = propByKey.get(n.id)
      if (!fresh) return n
      return {
        ...n,
        data: {
          ...n.data,
          label: fresh.label || n.data.label,
          displayTitle: displayTitle(fresh),
          properties: fresh.properties || n.data.properties
        }
      }
    }),
    ...newNodes
  ]

  const edgeIds = new Set(flowEdges.value.map((e) => e.id))
  flowEdges.value = [...flowEdges.value, ...toFlowEdges(apiEdges, edgeIds)]

  applyForceLayout()

  await nextTick()
  try {
    fitView({ padding: 0.2, duration: 300 })
  } catch (e) {
    console.warn('[kb-graph] fitView skipped', e)
  }
  console.info('[kb-graph] merged', {
    nodes: flowNodes.value.length,
    edges: flowEdges.value.length,
    addedNodes: newNodes.length
  })
}

/**
 * 力导向重排当前画布（含懒加载合并后的新节点）。
 */
function applyForceLayout() {
  if (flowNodes.value.length < 2) return
  const placed = forceLayout(flowNodes.value, flowEdges.value, {
    iterations: flowNodes.value.length > 40 ? 60 : 90
  })
  flowNodes.value = flowNodes.value.map((n) => {
    const p = placed.get(n.id)
    return p ? { ...n, position: p } : n
  })
  console.info('[kb-graph] force layout', { nodes: flowNodes.value.length })
}

async function loadSummary() {
  if (!baseCode.value) return
  try {
    const res = await get(`/api/kb/${encodeURIComponent(baseCode.value)}/graph/summary`)
    summary.value = res.data || null
    if (summary.value?.message && !viewMessage.value) {
      viewMessage.value = summary.value.message
    }
    console.info('[kb-graph] summary', summary.value)
  } catch (e) {
    console.warn('[kb-graph] summary failed', e)
  }
}

async function reloadFromScratch() {
  loading.value = true
  error.value = ''
  try {
    await fetchAndMerge({ startKey: null, replace: true })
  } catch (e) {
    error.value = e.message || String(e)
    flowNodes.value = []
    flowEdges.value = []
    console.error('[kb-graph] reload failed', e)
  } finally {
    loading.value = false
  }
}

async function expandSelected() {
  if (!selectedNode.value?.id) return
  expanding.value = true
  error.value = ''
  try {
    console.info('[kb-graph] expand neighbor', selectedNode.value.id)
    await fetchAndMerge({ startKey: selectedNode.value.id, replace: false })
  } catch (e) {
    error.value = e.message || String(e)
    console.error('[kb-graph] expand failed', e)
  } finally {
    expanding.value = false
  }
}

watch(baseCode, () => {
  loadSummary()
  reloadFromScratch()
})
onMounted(() => {
  loadSummary()
  reloadFromScratch()
})
</script>

<style scoped>
.kb-graph-page {
  height: calc(100vh - 4.5rem);
}
.kb-toolbar {
  flex-shrink: 0;
  margin-bottom: 0.55rem;
}
.field-row {
  display: grid;
  grid-template-columns: 2fr 1fr 1fr auto;
  gap: 0.8rem;
  align-items: end;
}
.field-action button {
  width: 100%;
}
.kb-graph-studio {
  flex: 1;
  min-height: 0;
  grid-template-columns: 160px 1fr 240px;
}
.kb-legend .legend-item,
.kb-props .legend-item {
  display: flex;
  align-items: center;
  gap: 0.45rem;
  font-size: 0.78rem;
  margin-bottom: 0.35rem;
}
.swatch {
  width: 0.7rem;
  height: 0.7rem;
  border-radius: 3px;
  flex-shrink: 0;
}
.swatch.doc { background: #4ea8ff; }
.swatch.chk { background: #3de0c5; }
.swatch.ent { background: #e8b95c; }
.prop-pre {
  margin: 0;
  padding: 0.5rem 0.6rem;
  max-height: 220px;
  overflow: auto;
  font-family: var(--font-mono);
  font-size: 0.68rem;
  line-height: 1.4;
  border-radius: 10px;
  background: rgba(7, 13, 24, 0.65);
  border: 1px solid rgba(148, 163, 184, 0.18);
  white-space: pre-wrap;
  word-break: break-all;
  color: var(--ink-soft);
}
@media (max-width: 960px) {
  .kb-graph-studio {
    grid-template-columns: 1fr;
    grid-template-rows: auto minmax(320px, 1fr) auto;
  }
  .field-row {
    grid-template-columns: 1fr;
  }
}
</style>
