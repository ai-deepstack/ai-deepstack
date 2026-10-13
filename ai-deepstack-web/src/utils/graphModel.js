/** 图模型：VueFlow 画布数据与后端图定义之间的纯转换（无 ref、无请求、无组件状态）。 */
import { nodeTypeLabel } from '../constants/graphNodes'

/** 各节点类型默认写出的变量名（未配置 outputVar 时） */
export const DEFAULT_OUTPUT_VAR = {
  'start-node': 'user_message',
  'intent-node': 'intent',
  'llm-node': 'response',
  'rag-node': 'rag_context',
  'tool-node': 'tool_result',
  'condition-node': 'condition_result',
  'memory-node': 'memory_context'
}

/** 归一为数组：兼容数组、JSON 字符串、逗号串、单值。 */
export function asArray(val) {
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

/** 滑杆字段：温度、相似度阈值用 range 控件。 */
export function isSliderField(field) {
  return field.key === 'temperature' || field.key === 'similarityThreshold'
}

/** 多选字段：输入变量、知识库、意图类别用多选控件。 */
export function isMultiSelectField(field) {
  return field.key === 'inputVars' || field.key === 'knowledgeBaseCodes' || field.key === 'categories'
}

/** 节点类型中文名（优先字典 label 回退）。 */
export function displayTypeLabel(nt) {
  return nodeTypeLabel(nt?.type, nt?.label)
}

/** 意图节点：类别用逗号字符串；补默认 outputVar。 */
export function normalizeNodeProperties(nodeType, props) {
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
export function nextUniqueIntentOutputVar(preferred = 'intent', nodes = []) {
  const base = String(preferred || 'intent').trim() || 'intent'
  const used = new Set(
    (nodes || [])
      .filter((n) => n.data?.nodeType === 'intent-node')
      .map((n) => String(n.data?.properties?.outputVar || 'intent').trim())
      .filter(Boolean)
  )
  if (!used.has(base)) return base
  let i = 2
  while (used.has(`${base}_${i}`)) i += 1
  return `${base}_${i}`
}

/**
 * 由节点库条目构造画布节点对象。
 * @param {object} nt 节点库条目 { type, label, defaultProperties }
 * @param {object} [position] 画布坐标；缺省按已有节点数错落摆放
 * @param {{nodes?: Array, agentModelCode?: string}} [ctx] 需要读取的当前图状态
 */
export function createNodeObject(nt, position, { nodes = [], agentModelCode = '' } = {}) {
  const id = `node_${Date.now()}_${Math.floor(Math.random() * 1000)}`
  const label = displayTypeLabel(nt)
  const properties = normalizeNodeProperties(nt.type, nt.defaultProperties || {})
  if (nt.type === 'intent-node') {
    // 新建节点不预选字典项，避免被旧 default_props / 缓存带上全量意图
    properties.categories = []
    properties.outputVar = nextUniqueIntentOutputVar(properties.outputVar || 'intent', nodes)
  }
  if ((nt.type === 'llm-node' || nt.type === 'intent-node')
      && (!properties.modelCode || String(properties.modelCode).trim() === '')
      && agentModelCode) {
    properties.modelCode = agentModelCode
  }
  return {
    id,
    type: 'agent',
    position: position || { x: 80 + nodes.length * 40, y: 80 + (nodes.length % 4) * 90 },
    data: {
      label,
      typeLabel: label,
      nodeType: nt.type,
      properties
    }
  }
}

/** 沿入边回溯，收集当前节点的所有上游节点 id（不含自身）。 */
export function collectUpstreamNodeIds(nodeId, edges = []) {
  if (!nodeId) return new Set()
  const incoming = new Map()
  for (const e of edges || []) {
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
export function collectGraphInputVarOptions(nodeId, nodes = [], edges = []) {
  const opts = []
  const seen = new Set()
  const upstream = collectUpstreamNodeIds(nodeId, edges)
  for (const n of nodes || []) {
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

/** 多选字段选项：叠加图上/字典外的历史已选值，避免编辑时丢值。 */
export function multiOptions(field, node, nodes = [], edges = []) {
  if (field.key === 'inputVars') {
    const fromGraph = collectGraphInputVarOptions(node?.id, nodes, edges)
    const known = new Set(fromGraph.map((o) => String(o.value)))
    const extras = asArray(node?.data?.properties?.inputVars)
      .filter((v) => !known.has(String(v)))
      .map((v) => ({ value: v, label: `${v} · 已选（非上游节点或不存在）` }))
    return [...fromGraph, ...extras]
  }
  if (field.key === 'categories') {
    const fromDict = field.options || []
    const known = new Set(fromDict.map((o) => String(o.value)))
    const extras = asArray(node?.data?.properties?.categories)
      .filter((v) => !known.has(String(v)))
      .map((v) => ({ value: v, label: `${v} · 已选（字典中已停用或不存在）` }))
    return [...fromDict, ...extras]
  }
  return field.options || []
}

/** textarea 展示文本：对象/数组转 JSON，字符串原样。 */
export function jsonFieldText(node, key) {
  const val = node?.data?.properties?.[key]
  if (val == null) return ''
  if (typeof val === 'string') return val
  try {
    return JSON.stringify(val, null, 2)
  } catch {
    return String(val)
  }
}

/** 载入时的节点显示名：忽略历史英文/占位名，回退节点库中文名。 */
function resolveNodeLabel(n, catalog = []) {
  const fromCatalog = catalog.find((x) => x.type === n.type)?.label
  const mapped = nodeTypeLabel(n.type, fromCatalog)
  const text = n.text || ''
  const englishLegacy = ['Start', 'Intent', 'LLM', 'RAG', 'Tool', 'Condition', 'Assign', 'End', 'Memory']
  if (!text || englishLegacy.includes(text) || text === n.type || text === n.id) {
    return mapped
  }
  return text
}

/**
 * 后端图定义 -> 画布节点/边。纯函数：调用方负责写入 ref 并清空选中。
 * @param {object} definition { nodes, edges }
 * @param {Array} catalog 节点库（用于解析显示名）
 */
export function toFlow(definition, catalog = []) {
  const rawNodes = definition?.nodes || []
  const rawEdges = definition?.edges || []
  const nodes = rawNodes.map((n, i) => {
    const label = resolveNodeLabel(n, catalog)
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
  const edges = rawEdges.map((e, i) => {
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
  return { nodes, edges }
}

/** 画布节点/边 -> 后端图定义（纯函数；形状须与既有接口一致）。 */
export function toDefinition(nodes = [], edges = []) {
  return {
    nodes: (nodes || []).map((n) => ({
      id: n.id,
      type: n.data.nodeType,
      text: n.data.label,
      position: n.position,
      properties: n.data.properties || {}
    })),
    edges: (edges || []).map((e) => {
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

/** 写回某条边的路由条件；标签若此前镜像条件则跟随更新（纯函数，返回新数组）。 */
export function setEdgeCondition(edges = [], edgeId, val) {
  if (!edgeId) return edges
  const next = val ?? ''
  return (edges || []).map((e) => {
    if (e.id !== edgeId) return e
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
