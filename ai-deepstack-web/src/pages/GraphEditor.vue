<template>
  <div class="graph-page">
    <header class="page-head">
      <div>
        <h1>图编排</h1>
        <p>
          智能体 #{{ agentId }}
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
        <button class="btn-ghost" type="button" :disabled="saving || publishing" @click="save">{{ saving ? '保存中…' : '保存草稿' }}</button>
        <button class="btn-ghost" type="button" :disabled="saving || publishing" @click="publish">{{ publishing ? '发布中…' : '发布' }}</button>
        <button class="btn-ghost" type="button" @click="toggleVersions">版本</button>
        <button class="btn" type="button" @click="openTestDialog">试跑</button>
      </div>
    </header>

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
      <aside class="panel" style="padding: 0.85rem; overflow: auto">
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
          :nodes-draggable="true"
          :nodes-connectable="true"
          :elements-selectable="true"
          :edges-updatable="true"
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

      <aside class="panel" style="padding: 0.85rem; overflow: auto">
        <h3 style="margin: 0 0 0.6rem; font-size: 0.78rem; letter-spacing: 0.08em; text-transform: uppercase; color: var(--muted)">
          {{ selectionTitle }}
        </h3>

        <template v-if="selectedNode">
          <div class="field">
            <label>显示名称</label>
            <input v-model="selectedNode.data.label" />
          </div>
          <div class="field">
            <label>节点类型</label>
            <input :value="displayTypeLabel({ type: selectedNode.data.nodeType, label: selectedNode.data.typeLabel })" disabled />
          </div>
          <p
            v-if="selectedNode.data.nodeType === 'condition-node'"
            class="muted"
            style="font-size: 0.72rem; margin: 0 0 0.75rem; line-height: 1.45"
          >
            节点内表达式只负责求值。分支请在出边上的「路由条件」填写，例如
            <code>condition_result == true</code> / <code>DEFAULT</code>。
          </p>
          <p
            v-if="selectedNode.data.nodeType === 'llm-node' || selectedNode.data.nodeType === 'intent-node'"
            class="muted"
            style="font-size: 0.72rem; margin: 0 0 0.75rem"
          >
            双击节点可打开全屏配置。
          </p>
          <div v-if="selectedNode.data.nodeType === 'intent-node'" class="field">
            <label>输出变量</label>
            <input
              v-model="selectedNode.data.properties.outputVar"
              placeholder="intent"
              @blur="ensureIntentOutputVar"
            />
            <p class="muted" style="margin: 0.28rem 0 0; font-size: 0.68rem; line-height: 1.4">
              分类结果写入该变量；多个意图节点请用不同变量名，避免互相覆盖。后续可引用
              <code>{{ intentVarRef }}</code>
            </p>
          </div>
          <template v-for="field in selectedFields" :key="field.key">
            <div v-if="!(selectedNode.data.nodeType === 'intent-node' && field.key === 'outputVar')" class="field">
              <label>{{ field.label || field.key }}</label>
              <template v-if="field.widget === 'switch' || field.key === 'enableGraph'">
                <PropCheckbox
                  v-model="selectedNode.data.properties[field.key]"
                  :label="selectedNode.data.properties[field.key] ? '开' : '关'"
                />
              </template>
              <template v-else-if="isSliderField(field)">
                <input
                  v-model.number="selectedNode.data.properties[field.key]"
                  type="range"
                  :min="field.key === 'temperature' ? 0 : 0"
                  :max="field.key === 'temperature' ? 2 : 1"
                  :step="field.key === 'temperature' ? 0.1 : 0.05"
                />
                <div class="muted" style="font-size: 0.72rem">{{ selectedNode.data.properties[field.key] }}</div>
              </template>
              <PropMultiSelect
                v-else-if="isMultiSelectField(field)"
                :model-value="asArray(selectedNode.data.properties[field.key])"
                :options="multiOptions(field)"
                :placeholder="field.placeholder || '请选择（可多选）'"
                @update:model-value="(vals) => { selectedNode.data.properties[field.key] = vals }"
              />
              <PropSelect
                v-else-if="field.widget === 'select'"
                v-model="selectedNode.data.properties[field.key]"
                :options="field.options || []"
                placeholder="请选择"
              />
              <textarea
                v-else-if="field.widget === 'textarea' || field.widget === 'json'"
                :value="jsonFieldText(field.key)"
                rows="4"
                :placeholder="field.placeholder || ''"
                @input="onJsonOrTextInput(field, $event.target.value)"
              />
              <PropNumber
                v-else-if="field.widget === 'number'"
                v-model="selectedNode.data.properties[field.key]"
                :placeholder="field.placeholder || ''"
              />
              <input
                v-else
                v-model="selectedNode.data.properties[field.key]"
                :placeholder="field.placeholder || ''"
              />
            </div>
          </template>
          <button class="btn-ghost" type="button" style="width: 100%; color: var(--danger)" @click="removeSelected">删除节点</button>
        </template>

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
        <h3 style="margin: 0 0 0.8rem">测试工作流</h3>
        <div class="field">
          <label>测试消息</label>
          <textarea v-model="testMessage" rows="3" placeholder="输入测试消息" />
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

    <!-- LLM / 意图全屏配置 -->
    <div v-if="configDialogVisible && selectedNode" class="modal-mask" @click.self="configDialogVisible = false">
      <div class="modal-card modal-wide">
        <h3 style="margin: 0 0 0.8rem">
          编辑{{ displayTypeLabel({ type: selectedNode.data.nodeType }) }}配置
        </h3>
        <template v-if="selectedNode.data.nodeType === 'llm-node'">
          <div class="field">
            <label>系统提示词</label>
            <textarea v-model="selectedNode.data.properties.systemPrompt" rows="12" />
          </div>
          <div class="field">
            <label>温度 {{ selectedNode.data.properties.temperature }}</label>
            <input v-model.number="selectedNode.data.properties.temperature" type="range" min="0" max="2" step="0.1" />
          </div>
          <div class="field">
            <label>输出变量</label>
            <input v-model="selectedNode.data.properties.outputVar" />
          </div>
        </template>
        <template v-else-if="selectedNode.data.nodeType === 'intent-node'">
          <div class="field">
            <label>意图类别</label>
            <PropMultiSelect
              :model-value="asArray(selectedNode.data.properties.categories)"
              :options="intentCategoryOptions"
              placeholder="从意图字典多选"
              @update:model-value="(vals) => { selectedNode.data.properties.categories = vals }"
            />
          </div>
          <div class="field">
            <label>输出变量</label>
            <input
              v-model="selectedNode.data.properties.outputVar"
              placeholder="intent"
              @blur="ensureIntentOutputVar"
            />
            <p class="muted" style="margin: 0.28rem 0 0; font-size: 0.72rem">
              多个意图节点请使用不同变量名；边条件可写 <code>{{ intentVarRef }} == HEALTH</code>
            </p>
          </div>
          <div class="field">
            <label>分类提示词</label>
            <textarea v-model="selectedNode.data.properties.prompt" rows="10" />
          </div>
        </template>
        <div class="row-actions" style="justify-content: flex-end">
          <button class="btn" type="button" @click="configDialogVisible = false">完成</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, markRaw, onMounted, onUnmounted, ref } from 'vue'
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
import PropCheckbox from '../components/PropCheckbox.vue'
import PropMultiSelect from '../components/PropMultiSelect.vue'
import PropNumber from '../components/PropNumber.vue'
import PropSelect from '../components/PropSelect.vue'
import { get, post, put } from '../api/http'
import { GraphRunStatus } from '../constants/enums'
import { nodeIcon, nodeTypeHint, nodeTypeLabel } from '../constants/graphNodes'
import { detectCycle, getCycleEdgeIds } from '../utils/graphCycle'
import { isValidConnection } from '../utils/graphPorts'
import { confirmDialog } from '../composables/useConfirm'

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
const nodeTypeCatalog = ref([])
const selectionKind = ref(null)
const selectedNodeId = ref(null)
const selectedEdgeId = ref(null)
const selectedNodeIds = ref([])
const selectedEdgeIds = ref([])
const agentName = ref('')
const graphMeta = ref({ workflowType: '', version: null, enabled: null })
const publishedVersion = ref(null)
const versions = ref([])
const showVersions = ref(false)
const publishing = ref(false)
const error = ref('')
const okMsg = ref('')
const saving = ref(false)

const testDialogVisible = ref(false)
const testing = ref(false)
const cancelling = ref(false)
const testMessage = ref('你好，请做一次图试跑。')
const testUserId = ref('')
const testResult = ref('')
const currentRunId = ref(null)
let pollTimer = null

const configDialogVisible = ref(false)

/** 各节点类型默认写出的变量名（未配置 outputVar 时） */
const DEFAULT_OUTPUT_VAR = {
  'start-node': 'user_message',
  'intent-node': 'intent',
  'llm-node': 'response',
  'rag-node': 'rag_context',
  'tool-node': 'tool_result',
  'condition-node': 'condition_result',
  'memory-node': 'memory_context'
}

const selectedNode = computed(() => {
  if (selectionKind.value !== 'node' || !selectedNodeId.value) return null
  return nodes.value.find((n) => n.id === selectedNodeId.value) || null
})

const selectedEdge = computed(() => {
  if (selectionKind.value !== 'edge' || !selectedEdgeId.value) return null
  return edges.value.find((e) => e.id === selectedEdgeId.value) || null
})

const selectedFields = computed(() => {
  if (!selectedNode.value) return []
  const nt = nodeTypeCatalog.value.find((x) => x.type === selectedNode.value.data.nodeType)
  return nt?.propertyFields || []
})

const intentCategoryOptions = computed(() => {
  const nt = nodeTypeCatalog.value.find((x) => x.type === 'intent-node')
  const field = (nt?.propertyFields || []).find((f) => f.key === 'categories')
  return field?.options || []
})

const intentOutputVar = computed(() => {
  const v = selectedNode.value?.data?.properties?.outputVar
  return v != null && String(v).trim() ? String(v).trim() : 'intent'
})

const intentVarRef = computed(() => `\${${intentOutputVar.value}}`)

function ensureIntentOutputVar() {
  if (!selectedNode.value || selectedNode.value.data.nodeType !== 'intent-node') return
  const v = selectedNode.value.data.properties.outputVar
  if (v == null || String(v).trim() === '') {
    selectedNode.value.data.properties.outputVar = 'intent'
  } else {
    selectedNode.value.data.properties.outputVar = String(v).trim()
  }
}

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

function setEdgeCondition(val) {
  const id = selectedEdgeId.value
  if (!id) return
  const next = val ?? ''
  edges.value = edges.value.map((e) => {
    if (e.id !== id) return e
    const prevCond = e.data?.properties?.condition ?? ''
    const props = { ...(e.data?.properties || {}), condition: next }
    const mirrorLabel = !e.label || e.label === prevCond
    return {
      ...e,
      data: { ...(e.data || {}), properties: props },
      label: mirrorLabel ? next : e.label,
      class: e.class
    }
  })
}

function displayTypeLabel(nt) {
  return nodeTypeLabel(nt?.type, nt?.label)
}

function asArray(val) {
  if (Array.isArray(val)) return val
  if (val == null || val === '') return []
  if (typeof val === 'string') {
    try {
      const parsed = JSON.parse(val)
      return Array.isArray(parsed) ? parsed : [val]
    } catch {
      return val.split(',').map((s) => s.trim()).filter(Boolean)
    }
  }
  return [val]
}

function isSliderField(field) {
  return field.key === 'temperature' || field.key === 'similarityThreshold'
}

function isMultiSelectField(field) {
  return field.key === 'inputVars' || field.key === 'knowledgeBaseCodes' || field.key === 'categories'
}

/** 沿入边回溯，收集当前节点的所有上游节点 id（不含自身）。 */
function collectUpstreamNodeIds(nodeId) {
  if (!nodeId) return new Set()
  const incoming = new Map()
  for (const e of edges.value) {
    const src = e.source
    const tgt = e.target
    if (!src || !tgt) continue
    if (!incoming.has(tgt)) incoming.set(tgt, [])
    incoming.get(tgt).push(src)
  }
  const upstream = new Set()
  const stack = [...(incoming.get(nodeId) || [])]
  while (stack.length) {
    const id = stack.pop()
    if (!id || upstream.has(id) || id === nodeId) continue
    upstream.add(id)
    const preds = incoming.get(id) || []
    for (const p of preds) {
      if (!upstream.has(p) && p !== nodeId) stack.push(p)
    }
  }
  return upstream
}

/** 仅从上游节点收集可选输入变量（下游尚未执行，不可引用）。 */
function collectGraphInputVarOptions(nodeId) {
  const opts = []
  const seen = new Set()
  const upstream = collectUpstreamNodeIds(nodeId)
  for (const n of nodes.value) {
    if (!n?.data || !upstream.has(n.id)) continue
    const type = n.data.nodeType
    // 用户原话由运行时从 messages 自动带入，不出现在输入变量选项里
    if (type === 'end-node' || type === 'start-node') continue
    let varName = ''
    if (type === 'assign-node') {
      varName = String(n.data.properties?.variableName || '').trim()
    } else if (type === 'memory-node') {
      varName = String(n.data.properties?.outputVar || DEFAULT_OUTPUT_VAR[type] || 'memory_context').trim()
    } else {
      varName = String(n.data.properties?.outputVar || DEFAULT_OUTPUT_VAR[type] || '').trim()
    }
    if (!varName || seen.has(varName)) continue
    // 运行时保留名，不作为可选输入变量
    if (varName === 'user_message' || varName === 'userMessage') continue
    seen.add(varName)
    const typeLabel = n.data.typeLabel || nodeTypeLabel(type) || type
    const nodeLabel = n.data.label && n.data.label !== typeLabel ? n.data.label : ''
    const source = nodeLabel ? `${typeLabel}「${nodeLabel}」` : typeLabel
    opts.push({ value: varName, label: `${varName} · ${source}` })
  }
  return opts
}

function multiOptions(field) {
  if (field.key === 'inputVars') {
    const fromGraph = collectGraphInputVarOptions(selectedNode.value?.id)
    const known = new Set(fromGraph.map((o) => String(o.value)))
    const extras = asArray(selectedNode.value?.data?.properties?.inputVars)
      .filter((v) => !known.has(String(v)))
      .map((v) => ({ value: v, label: `${v} · 已选（非上游节点或不存在）` }))
    return [...fromGraph, ...extras]
  }
  if (field.key === 'categories') {
    const fromDict = field.options || []
    const known = new Set(fromDict.map((o) => String(o.value)))
    const extras = asArray(selectedNode.value?.data?.properties?.categories)
      .filter((v) => !known.has(String(v)))
      .map((v) => ({ value: v, label: `${v} · 已选（字典中已停用或不存在）` }))
    return [...fromDict, ...extras]
  }
  return field.options || []
}

function jsonFieldText(key) {
  const val = selectedNode.value?.data?.properties?.[key]
  if (val == null) return ''
  if (typeof val === 'string') return val
  try {
    return JSON.stringify(val, null, 2)
  } catch {
    return String(val)
  }
}

function onJsonOrTextInput(field, text) {
  if (!selectedNode.value) return
  if (field.widget === 'json' || field.key === 'inputVars' || field.key === 'paramMappings') {
    try {
      selectedNode.value.data.properties[field.key] = JSON.parse(text)
    } catch {
      selectedNode.value.data.properties[field.key] = text
    }
  } else {
    selectedNode.value.data.properties[field.key] = text
  }
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

function createNodeObject(nt, position) {
  const id = `node_${Date.now()}_${Math.floor(Math.random() * 1000)}`
  const label = displayTypeLabel(nt)
  const properties = normalizeNodeProperties(nt.type, nt.defaultProperties || {})
  if (nt.type === 'intent-node') {
    // 新建节点不预选字典项，避免被旧 default_props / 缓存带上全量意图
    properties.categories = []
    properties.outputVar = nextUniqueIntentOutputVar(properties.outputVar || 'intent')
  }
  return {
    id,
    type: 'agent',
    position: position || { x: 80 + nodes.value.length * 40, y: 80 + (nodes.value.length % 4) * 90 },
    data: {
      label,
      typeLabel: label,
      nodeType: nt.type,
      properties
    }
  }
}

/** 意图节点：类别用逗号字符串；补默认 outputVar。 */
function normalizeNodeProperties(nodeType, props) {
  const properties = { ...(props || {}) }
  if (nodeType === 'intent-node') {
    if (typeof properties.categories === 'string') {
      properties.categories = properties.categories
        .split(',')
        .map((s) => s.trim())
        .filter(Boolean)
    } else if (Array.isArray(properties.categories)) {
      properties.categories = properties.categories.map(String)
    } else if (properties.categories == null) {
      properties.categories = []
    }
    if (properties.outputVar == null || String(properties.outputVar).trim() === '') {
      properties.outputVar = 'intent'
    }
  }
  return properties
}

/** 新建意图节点时避开已占用的输出变量名。 */
function nextUniqueIntentOutputVar(preferred = 'intent') {
  const base = String(preferred || 'intent').trim() || 'intent'
  const used = new Set(
    nodes.value
      .filter((n) => n.data?.nodeType === 'intent-node')
      .map((n) => String(n.data?.properties?.outputVar || 'intent').trim())
      .filter(Boolean)
  )
  if (!used.has(base)) return base
  let i = 2
  while (used.has(`${base}_${i}`)) i += 1
  return `${base}_${i}`
}

function addNode(nt) {
  nodes.value = [...nodes.value, createNodeObject(nt)]
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
  nodes.value = [...nodes.value, createNodeObject(nt, position)]
}

function resolveNodeLabel(n) {
  const fromCatalog = nodeTypeCatalog.value.find((x) => x.type === n.type)?.label
  const mapped = nodeTypeLabel(n.type, fromCatalog)
  const text = n.text || ''
  const englishLegacy = ['Start', 'Intent', 'LLM', 'RAG', 'Tool', 'Condition', 'Assign', 'End', 'Memory']
  if (!text || englishLegacy.includes(text) || text === n.type || text === n.id) {
    return mapped
  }
  return text
}

function toFlow(definition) {
  const rawNodes = definition?.nodes || []
  const rawEdges = definition?.edges || []
  nodes.value = rawNodes.map((n, i) => {
    const label = resolveNodeLabel(n)
    return {
      id: n.id,
      type: 'agent',
      position: n.position || { x: 80 + i * 220, y: 100 + i * 120 },
      data: {
        label,
        typeLabel: nodeTypeLabel(n.type),
        nodeType: n.type,
        properties: normalizeNodeProperties(n.type, n.properties || {})
      }
    }
  })
  edges.value = rawEdges.map((e, i) => {
    const props = { ...(e.properties || {}) }
    const condition = props.condition || ''
    const label = e.label || props.label || condition || ''
    return {
      id: e.id || `e${i}`,
      source: e.sourceNodeId || e.source,
      target: e.targetNodeId || e.target,
      sourceHandle: e.sourceHandle || null,
      targetHandle: e.targetHandle || null,
      type: 'default',
      animated: true,
      selectable: true,
      updatable: true,
      label,
      data: { properties: props }
    }
  })
  clearSelection()
}

function toDefinition() {
  return {
    nodes: nodes.value.map((n) => ({
      id: n.id,
      type: n.data.nodeType,
      text: n.data.label,
      position: n.position,
      properties: n.data.properties || {}
    })),
    edges: edges.value.map((e) => {
      const properties = { ...(e.data?.properties || {}) }
      if (e.label) properties.label = e.label
      const edge = {
        id: e.id,
        sourceNodeId: e.source,
        targetNodeId: e.target,
        label: e.label || '',
        properties
      }
      if (e.sourceHandle) edge.sourceHandle = e.sourceHandle
      if (e.targetHandle) edge.targetHandle = e.targetHandle
      return edge
    }),
    variables: {}
  }
}

function clearCycleHighlight() {
  edges.value = edges.value.map((e) => {
    const cls = (e.class || '').replace(/\bcycle-edge\b/g, '').trim()
    return { ...e, class: cls || undefined, style: undefined }
  })
}

function highlightCycle(cyclePath) {
  const ids = getCycleEdgeIds(nodes.value, edges.value, cyclePath)
  edges.value = edges.value.map((e) => {
    if (!ids.has(e.id)) {
      const cls = (e.class || '').replace(/\bcycle-edge\b/g, '').trim()
      return { ...e, class: cls || undefined, style: undefined }
    }
    return {
      ...e,
      class: 'cycle-edge',
      style: { stroke: '#F56C6C', strokeWidth: 2.5 }
    }
  })
}

async function load() {
  error.value = ''
  try {
    const [agentRes, graphRes, typesRes] = await Promise.all([
      get(`/api/agents/${agentId.value}`),
      get(`/api/agents/${agentId.value}/graph`),
      get('/api/agents/node-types')
    ])
    agentName.value = agentRes.data?.agentName || ''
    publishedVersion.value = agentRes.data?.publishedVersion ?? null
    nodeTypeCatalog.value = typesRes.data || []
    graphMeta.value = {
      workflowType: graphRes.data?.workflowType || '',
      version: graphRes.data?.version ?? null,
      enabled: graphRes.data?.enabled ?? null
    }
    let def = graphRes.data?.definition
    if (!def && agentRes.data?.graphDefinition) {
      try {
        def = typeof agentRes.data.graphDefinition === 'string'
          ? JSON.parse(agentRes.data.graphDefinition)
          : agentRes.data.graphDefinition
      } catch (_) {
        def = null
      }
    }
    toFlow(def || { nodes: [], edges: [] })
  } catch (e) {
    error.value = e.message
  }
}

async function save() {
  saving.value = true
  error.value = ''
  okMsg.value = ''
  clearCycleHighlight()
  try {
    const def = toDefinition()
    const wfType = graphMeta.value.workflowType || 'SEQ'
    const cycle = detectCycle(
      def.nodes,
      def.edges.map((e) => ({ id: e.id, source: e.sourceNodeId, target: e.targetNodeId }))
    )
    if (cycle && wfType !== 'GRAPH') {
      highlightCycle(cycle)
      error.value = `存在环路，SEQ/DAG 不允许：${cycle.join(' → ')}。请调整连线或改为 GRAPH 类型。`
      return
    }
    await put(`/api/agents/${agentId.value}/graph`, def)
    okMsg.value = '草稿已保存'
  } catch (e) {
    error.value = e.message
  } finally {
    saving.value = false
  }
}

async function publish() {
  publishing.value = true
  error.value = ''
  okMsg.value = ''
  try {
    await save()
    if (error.value) return
    const remark = window.prompt('发布备注（可选）', '') 
    if (remark === null) return
    const res = await post(`/api/agents/${agentId.value}/graph/publish`, {
      remark: remark.trim() || undefined
    })
    publishedVersion.value = res.data
    okMsg.value = `已发布 v${res.data}`
    if (showVersions.value) await loadVersions()
  } catch (e) {
    error.value = e.message
  } finally {
    publishing.value = false
  }
}

async function toggleVersions() {
  showVersions.value = !showVersions.value
  if (showVersions.value) await loadVersions()
}

async function loadVersions() {
  try {
    const res = await get(`/api/agents/${agentId.value}/graph/versions`)
    versions.value = res.data || []
  } catch (e) {
    error.value = e.message
  }
}

async function rollback(version) {
  const ok = await confirmDialog({
    title: '回滚图版本',
    message: `确认回滚到 v${version}？将同时覆盖已发布定义与草稿。`
  })
  if (!ok) return
  error.value = ''
  okMsg.value = ''
  try {
    await post(`/api/agents/${agentId.value}/graph/rollback/${version}`)
    publishedVersion.value = version
    okMsg.value = `已回滚到 v${version}`
    await load()
    if (showVersions.value) await loadVersions()
  } catch (e) {
    error.value = e.message
  }
}

function formatTime(t) {
  if (!t) return '—'
  return String(t).replace('T', ' ').slice(0, 19)
}

function openTestDialog() {
  testDialogVisible.value = true
  testResult.value = ''
}

function closeTestDialog() {
  testDialogVisible.value = false
  stopPoll()
}

function stopPoll() {
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
}

async function runTest() {
  if (!testMessage.value.trim()) {
    error.value = '请输入测试消息'
    return
  }
  testing.value = true
  error.value = ''
  testResult.value = ''
  currentRunId.value = null
  try {
    await save()
    if (error.value) {
      testing.value = false
      return
    }
    const startRes = await post(`/api/agents/${agentId.value}/graph/test/start`, {
      message: testMessage.value,
      userId: testUserId.value || undefined
    })
    const runId = startRes.data?.runId
    currentRunId.value = runId
    stopPoll()
    pollTimer = setInterval(async () => {
      try {
        const res = await get(`/api/agents/runs/${runId}`)
        const data = res.data
        if (!data || data.status === GraphRunStatus.RUNNING) return
        stopPoll()
        testing.value = false
        testResult.value = JSON.stringify(data, null, 2)
      } catch (err) {
        stopPoll()
        testing.value = false
        testResult.value = 'ERROR: ' + err.message
      }
    }, 500)
  } catch (e) {
    testing.value = false
    error.value = e.message
  }
}

async function cancelTest() {
  if (!currentRunId.value) return
  cancelling.value = true
  try {
    await post(`/api/agents/runs/${currentRunId.value}/cancel`)
    okMsg.value = '已发送终止请求'
  } catch (e) {
    error.value = e.message
  } finally {
    cancelling.value = false
  }
}

onMounted(load)

onUnmounted(() => {
  stopPoll()
})
</script>
