<template>
  <div class="graph-page">
    <header class="page-head">
      <div>
        <h1>图编排</h1>
        <p>
          Agent #{{ agentId }}
          <span v-if="agentName"> · {{ agentName }}</span>
          <span v-if="graphMeta.workflowType" class="pill pill-brass" style="margin-left: 0.4rem">{{ graphMeta.workflowType }}</span>
          <span v-if="graphMeta.version != null" class="pill" style="margin-left: 0.25rem">草稿 v{{ graphMeta.version }}</span>
          <span v-if="publishedVersion != null" class="pill pill-teal" style="margin-left: 0.25rem">已发布 v{{ publishedVersion }}</span>
          <span v-else class="pill pill-off" style="margin-left: 0.25rem">未发布</span>
          <span v-if="graphMeta.enabled != null" class="pill" style="margin-left: 0.25rem">{{ graphMeta.enabled ? '已启用' : '已禁用' }}</span>
          · 从节点右侧圆点拖到另一节点左侧圆点连线；节点右上角 × 可删除。
        </p>
      </div>
      <div class="row-actions">
        <router-link class="btn-ghost" to="/agents" style="display: inline-flex">返回列表</router-link>
        <button class="btn-ghost desktop-only" type="button" :disabled="saving || publishing" @click="save">{{ saving ? '保存中…' : '保存草稿' }}</button>
        <button class="btn-ghost desktop-only" type="button" :disabled="saving || publishing" @click="publish">{{ publishing ? '发布中…' : '发布' }}</button>
        <button class="btn-ghost desktop-only" type="button" @click="toggleVersions">版本</button>
        <button class="btn" type="button" @click="openTestDialog">试跑</button>
      </div>
    </header>

    <!-- 手机端：画布可缩放平移查看，不做编辑 -->
    <p v-if="isMobile" class="graph-readonly-hint">
      手机端仅支持查看图结构：单指拖动平移、双指缩放。编辑节点、连线和发布请在桌面端进行。
    </p>

    <p v-if="error" class="error">{{ error }}</p>
    <p v-if="okMsg" class="ok">{{ okMsg }}</p>

    <div v-if="showVersions" class="panel" style="margin-bottom: 0.75rem">
      <h3 style="margin: 0 0 0.5rem; font-size: 0.85rem">已发布版本 · 当前 published v{{ publishedVersion ?? '—' }}</h3>
      <div class="table-wrap" v-if="versions.length">
        <table class="table">
          <thead>
            <tr><th>版本</th><th>备注</th><th>时间</th><th></th></tr>
          </thead>
          <tbody>
            <tr v-for="v in versions" :key="v.version">
              <td>v{{ v.version }}</td>
              <td class="muted">{{ v.remark || '—' }}</td>
              <td class="muted">{{ formatTime(v.createTime) }}</td>
              <td>
                <button class="btn-ghost" type="button" @click="rollback(v.version)">回滚</button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <p v-else class="muted" style="margin: 0">暂无发布历史</p>
    </div>

    <div class="graph-studio">
      <aside class="panel graph-palette" style="padding: 0.85rem; overflow: auto">
        <h3 style="margin: 0 0 0.6rem; font-size: 0.78rem; letter-spacing: 0.08em; text-transform: uppercase; color: var(--muted)">
          节点库
        </h3>
        <div
          v-for="nt in nodeTypeCatalog"
          :key="nt.type"
          class="palette-item btn-ghost"
          :class="'palette-cat-' + (nt.category || 'basic')"
          draggable="true"
          @dragstart="onPaletteDragStart($event, nt)"
          @click="addNode(nt)"
        >
          <span class="palette-item-main">
            <NodeIcon :type="nt.type" size="sm" />
            <span>
              <strong>{{ displayTypeLabel(nt) }}</strong>
              <div v-if="nodeTypeHint(nt.type)" class="muted" style="font-size: 0.75rem">{{ nodeTypeHint(nt.type) }}</div>
            </span>
          </span>
        </div>
      </aside>

      <div
        class="graph-canvas"
        tabindex="0"
        @keydown="onCanvasKeydown"
        @dragover.prevent
        @drop="onCanvasDrop"
      >
        <VueFlow
          id="agent-graph"
          v-model:nodes="nodes"
          v-model:edges="edges"
          :node-types="flowNodeTypes"
          fit-view-on-init
          :nodes-draggable="!isMobile"
          :nodes-connectable="!isMobile"
          :elements-selectable="true"
          :edges-updatable="!isMobile"
          :pan-on-drag="true"
          :selection-key-code="'Shift'"
          :multi-selection-key-code="['Meta', 'Control']"
          :delete-key-code="null"
          :default-edge-options="{ type: 'default', animated: true, selectable: true, updatable: true }"
          @node-click="onNodeClick"
          @edge-click="onEdgeClick"
          @pane-click="onPaneClick"
          @edge-update="onEdgeUpdate"
          @node-double-click="onNodeDblClick"
          @selection-change="onSelectionChange"
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
            mask-stroke-color="rgba(61, 224, 197, 0.55)"
            :mask-stroke-width="1.5"
          />
        </VueFlow>
      </div>

      <aside class="panel graph-props" style="padding: 0.85rem; overflow: auto">
        <h3 style="margin: 0 0 0.6rem; font-size: 0.78rem; letter-spacing: 0.08em; text-transform: uppercase; color: var(--muted)">
          {{ selectionTitle }}
        </h3>

        <NodePropertyPanel
          v-if="selectedNode"
          v-model:config-open="configDialogVisible"
          :node="selectedNode"
          :nodes="nodes"
          :edges="edges"
          :catalog="nodeTypeCatalog"
          @remove="removeSelected"
        />

        <template v-else-if="selectedEdge">
          <div class="field">
            <label>连线 ID</label>
            <input :value="selectedEdge.id" disabled />
          </div>
          <div class="field">
            <label>边路径</label>
            <input :value="`${selectedEdge.source} → ${selectedEdge.target}`" disabled />
          </div>
          <div class="field">
            <label>路由条件（写在线上）</label>
            <input
              v-model="edgeCondition"
              placeholder="例如：${condition_result}==&quot;true&quot; 或 DEFAULT"
            />
            <div class="quick-conditions">
              <button type="button" class="btn-ghost" @click="setEdgeCondition('DEFAULT')">默认路由</button>
              <button type="button" class="btn-ghost" @click="setEdgeCondition('condition_result == true')">条件为真</button>
              <button type="button" class="btn-ghost" @click="setEdgeCondition('condition_result == false')">条件为假</button>
            </div>
          </div>
          <div class="field">
            <label>显示标签（可选）</label>
            <input v-model="edgeDisplayLabel" placeholder="画布上显示，如：是 / 否" />
          </div>
          <div class="edge-help muted" style="font-size: 0.72rem; line-height: 1.45; margin-bottom: 0.75rem">
            <p style="margin: 0 0 0.35rem">· 同一节点的多条出边，要么都写条件，要么只有一条不写</p>
            <p style="margin: 0 0 0.35rem">· <code>DEFAULT</code> 表示所有条件都不匹配时的兜底</p>
            <p style="margin: 0">· 条件节点下游可用 <code>condition_result == true</code> 判断求值结果</p>
          </div>
          <button class="btn-ghost" type="button" style="width: 100%; color: var(--danger)" @click="removeSelected">删除连线</button>
        </template>

        <p v-else class="muted">
          点击节点/连线编辑；从右侧锚点拖到左侧锚点连线；节点右上角 × 删除。
        </p>
      </aside>
    </div>

    <!-- 试跑对话框 -->
    <div v-if="testDialogVisible" class="modal-mask" @click.self="closeTestDialog">
      <div class="modal-card">
        <h3 style="margin: 0 0 0.8rem">试跑</h3>
        <div class="field">
          <label>消息</label>
          <textarea v-model="testMessage" rows="3" placeholder="输入一条消息" />
        </div>
        <div class="field">
          <label>用户 ID（可选）</label>
          <input v-model="testUserId" placeholder="test-user" />
        </div>
        <div class="row-actions" style="justify-content: flex-end; gap: 0.5rem">
          <button class="btn-ghost" type="button" @click="closeTestDialog">取消</button>
          <button class="btn-ghost" type="button" style="color: var(--danger)" :disabled="!testing || !currentRunId" @click="cancelTest">
            {{ cancelling ? '终止中…' : '终止' }}
          </button>
          <button class="btn" type="button" :disabled="testing" @click="runTest">{{ testing ? '运行中…' : '运行' }}</button>
        </div>
        <pre v-if="testResult" class="panel-quiet test-result-pre">{{ testResult }}</pre>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, markRaw, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { VueFlow, useVueFlow } from '@vue-flow/core'
import { Background } from '@vue-flow/background'
import { Controls } from '@vue-flow/controls'
import { MiniMap } from '@vue-flow/minimap'
import '@vue-flow/core/dist/style.css'
import '@vue-flow/core/dist/theme-default.css'
import '@vue-flow/controls/dist/style.css'
import '@vue-flow/minimap/dist/style.css'
import AgentFlowNode from '../components/AgentFlowNode.vue'
import NodeIcon from '../components/NodeIcon.vue'
import NodePropertyPanel from '../components/graph/NodePropertyPanel.vue'
import { nodeIcon, nodeTypeHint } from '../constants/graphNodes'
import { isValidConnection } from '../utils/graphPorts'
import { createNodeObject, displayTypeLabel, setEdgeCondition as applyEdgeCondition } from '../utils/graphModel'
import { confirmDialog } from '../composables/useConfirm'
import { useGraphPersistence } from '../composables/useGraphPersistence'
import { useGraphTestRun } from '../composables/useGraphTestRun'
import { useIsMobile } from '../composables/useIsMobile'

/** 手机端画布只读：禁掉拖节点、拉连线、改边，只保留平移与缩放 */
const isMobile = useIsMobile()

const route = useRoute()
const agentId = computed(() => route.params.id)
const flowNodeTypes = { agent: markRaw(AgentFlowNode) }

function miniMapNodeColor(node) {
  const type = node?.data?.nodeType
  return nodeIcon(type).color || '#3de0c5'
}

const {
  screenToFlowCoordinate,
  onConnect,
  addEdges
} = useVueFlow({ id: 'agent-graph' })

const nodes = ref([])
const edges = ref([])
const selectionKind = ref(null)
const selectedNodeId = ref(null)
const selectedEdgeId = ref(null)
const selectedNodeIds = ref([])
const selectedEdgeIds = ref([])
const configDialogVisible = ref(false)

// 保存/发布/版本与试跑各自独立，通过组合式函数持有状态
const {
  agentName,
  agentModelCode,
  nodeTypeCatalog,
  graphMeta,
  publishedVersion,
  versions,
  showVersions,
  publishing,
  saving,
  error,
  okMsg,
  load,
  save,
  publish,
  toggleVersions,
  rollback
} = useGraphPersistence({ agentId, nodes, edges, clearSelection })

const {
  testDialogVisible,
  testing,
  cancelling,
  testMessage,
  testUserId,
  testResult,
  currentRunId,
  openTestDialog,
  closeTestDialog,
  runTest,
  cancelTest
} = useGraphTestRun({ agentId, save, error, okMsg })

const selectedNode = computed(() => {
  if (selectionKind.value !== 'node' || !selectedNodeId.value) return null
  return nodes.value.find((n) => n.id === selectedNodeId.value) || null
})

const selectedEdge = computed(() => {
  if (selectionKind.value !== 'edge' || !selectedEdgeId.value) return null
  return edges.value.find((e) => e.id === selectedEdgeId.value) || null
})

const selectionTitle = computed(() => {
  if (selectedNode.value) return '节点属性'
  if (selectedEdge.value) return '连线属性'
  return '属性'
})

const edgeCondition = computed({
  get() {
    return selectedEdge.value?.data?.properties?.condition ?? ''
  },
  set(val) {
    setEdgeCondition(val ?? '')
  }
})

const edgeDisplayLabel = computed({
  get() {
    return selectedEdge.value?.label ?? ''
  },
  set(val) {
    const id = selectedEdgeId.value
    if (!id) return
    edges.value = edges.value.map((e) => (e.id === id ? { ...e, label: val ?? '' } : e))
  }
})

/** 把路由条件写回选中边（纯转换在 graphModel.setEdgeCondition）。 */
function setEdgeCondition(val) {
  const id = selectedEdgeId.value
  if (!id) return
  edges.value = applyEdgeCondition(edges.value, id, val)
}

function clearSelection() {
  selectionKind.value = null
  selectedNodeId.value = null
  selectedEdgeId.value = null
  selectedNodeIds.value = []
  selectedEdgeIds.value = []
  nodes.value = nodes.value.map((n) => ({ ...n, selected: false }))
  edges.value = edges.value.map((e) => ({ ...e, selected: false, class: e.class?.includes('cycle-edge') ? 'cycle-edge' : undefined }))
}

function onPaneClick() {
  clearSelection()
}

onConnect((params) => {
  if (!params?.source || !params?.target) return
  const ok = isValidConnection({
    source: params.source,
    target: params.target,
    nodes: nodes.value,
    edges: edges.value,
    catalog: nodeTypeCatalog.value
  })
  if (!ok) {
    error.value = '连线超出节点入/出度限制（如开始不能有入边、结束不能有出边）'
    return
  }
  const sourceNode = nodes.value.find((n) => n.id === params.source)
  const fromCondition = sourceNode?.data?.nodeType === 'condition-node'
  addEdges([
    {
      ...params,
      id: `e_${params.source}_${params.target}_${Date.now()}`,
      type: 'default',
      animated: true,
      selectable: true,
      updatable: true,
      label: '',
      data: {
        properties: fromCondition ? { condition: '' } : {}
      }
    }
  ])
  error.value = ''
})

function onNodeClick({ node, event }) {
  event?.stopPropagation?.()
  selectionKind.value = 'node'
  selectedNodeId.value = node.id
  selectedEdgeId.value = null
  selectedNodeIds.value = [node.id]
  selectedEdgeIds.value = []
  nodes.value = nodes.value.map((n) => ({ ...n, selected: n.id === node.id }))
  edges.value = edges.value.map((e) => ({ ...e, selected: false }))
}

function onEdgeClick({ edge, event }) {
  event?.stopPropagation?.()
  selectionKind.value = 'edge'
  selectedEdgeId.value = edge.id
  selectedNodeId.value = null
  selectedEdgeIds.value = [edge.id]
  selectedNodeIds.value = []
  edges.value = edges.value.map((e) => ({ ...e, selected: e.id === edge.id }))
  nodes.value = nodes.value.map((n) => ({ ...n, selected: false }))
}

function onNodeDblClick({ node }) {
  onNodeClick({ node })
  if (node.data?.nodeType === 'llm-node' || node.data?.nodeType === 'intent-node') {
    configDialogVisible.value = true
  }
}

function onSelectionChange({ nodes: selNodes, edges: selEdges }) {
  selectedNodeIds.value = (selNodes || []).map((n) => n.id)
  selectedEdgeIds.value = (selEdges || []).map((e) => e.id)
  if (selectedNodeIds.value.length === 1 && selectedEdgeIds.value.length === 0) {
    selectionKind.value = 'node'
    selectedNodeId.value = selectedNodeIds.value[0]
    selectedEdgeId.value = null
  } else if (selectedEdgeIds.value.length === 1 && selectedNodeIds.value.length === 0) {
    selectionKind.value = 'edge'
    selectedEdgeId.value = selectedEdgeIds.value[0]
    selectedNodeId.value = null
  } else if (selectedNodeIds.value.length > 1 || selectedEdgeIds.value.length > 1) {
    selectionKind.value = null
    selectedNodeId.value = null
    selectedEdgeId.value = null
  }
}

function onEdgeUpdate({ edge, connection }) {
  if (!connection?.source || !connection?.target) return
  if (!isValidConnection({
    source: connection.source,
    target: connection.target,
    nodes: nodes.value,
    edges: edges.value,
    catalog: nodeTypeCatalog.value,
    ignoreEdgeId: edge.id
  })) {
    error.value = '改接失败：超出入/出度限制'
    return
  }
  edges.value = edges.value.map((e) =>
    e.id === edge.id
      ? {
          ...e,
          source: connection.source,
          target: connection.target,
          sourceHandle: connection.sourceHandle,
          targetHandle: connection.targetHandle
        }
      : e
  )
}

async function removeSelected() {
  const nIds = new Set(selectedNodeIds.value.length ? selectedNodeIds.value : selectedNodeId.value ? [selectedNodeId.value] : [])
  const eIds = new Set(selectedEdgeIds.value.length ? selectedEdgeIds.value : selectedEdgeId.value ? [selectedEdgeId.value] : [])
  if (!nIds.size && !eIds.size) return
  if (nIds.size) {
    const tip = nIds.size === 1
      ? `确定删除节点「${nodes.value.find((n) => nIds.has(n.id))?.data?.label || '该节点'}」吗？删除后相连的连线也会一并移除。`
      : `确定删除选中的 ${nIds.size} 个节点吗？相连连线也会一并移除。`
    const ok = await confirmDialog({
      title: '删除节点',
      message: tip,
      confirmText: '删除',
      cancelText: '取消',
      danger: true
    })
    if (!ok) return
    nodes.value = nodes.value.filter((n) => !nIds.has(n.id))
    edges.value = edges.value.filter((e) => !nIds.has(e.source) && !nIds.has(e.target) && !eIds.has(e.id))
  } else {
    const tip = eIds.size === 1 ? '确定删除该连线吗？' : `确定删除选中的 ${eIds.size} 条连线吗？`
    const ok = await confirmDialog({
      title: '删除连线',
      message: tip,
      confirmText: '删除',
      cancelText: '取消',
      danger: true
    })
    if (!ok) return
    edges.value = edges.value.filter((e) => !eIds.has(e.id))
  }
  clearSelection()
}

function onCanvasKeydown(e) {
  if (e.key !== 'Delete' && e.key !== 'Backspace') return
  const tag = (e.target?.tagName || '').toLowerCase()
  if (tag === 'input' || tag === 'textarea' || tag === 'select' || e.target?.isContentEditable) return
  if (!selectionKind.value && !selectedNodeIds.value.length && !selectedEdgeIds.value.length) return
  e.preventDefault()
  removeSelected()
}

function addNode(nt) {
  nodes.value = [...nodes.value, createNodeObject(nt, undefined, { nodes: nodes.value, agentModelCode: agentModelCode.value })]
}

function onPaletteDragStart(e, nt) {
  e.dataTransfer.setData('application/deepstack-node', JSON.stringify({
    type: nt.type,
    label: nt.label,
    defaultProperties: nt.defaultProperties || {},
    category: nt.category
  }))
  e.dataTransfer.effectAllowed = 'copy'
}

function onCanvasDrop(e) {
  e.preventDefault()
  const raw = e.dataTransfer.getData('application/deepstack-node')
  if (!raw) return
  let payload
  try {
    payload = JSON.parse(raw)
  } catch {
    return
  }
  const nt = nodeTypeCatalog.value.find((x) => x.type === payload.type) || payload
  let position = { x: 120, y: 120 }
  try {
    const flowPos = screenToFlowCoordinate({ x: e.clientX, y: e.clientY })
    if (flowPos) position = flowPos
  } catch (_) { /* ignore */ }
  nodes.value = [...nodes.value, createNodeObject(nt, position, { nodes: nodes.value, agentModelCode: agentModelCode.value })]
}

function formatTime(t) {
  if (!t) return '—'
  return String(t).replace('T', ' ').slice(0, 19)
}

onMounted(load)
</script>
