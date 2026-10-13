import { ref } from 'vue'
import { get, post, put } from '../api/http'
import { confirmDialog } from './useConfirm'
import { detectCycle, getCycleEdgeIds } from '../utils/graphCycle'
import { toDefinition, toFlow } from '../utils/graphModel'

/**
 * 图草稿的保存 / 发布 / 版本生命周期：统一 loading、提示与错误文案。
 * @param {object} ctx
 * @param {import('vue').Ref} ctx.agentId 当前 Agent id（字符串）
 * @param {import('vue').Ref} ctx.nodes   画布节点 ref（读写）
 * @param {import('vue').Ref} ctx.edges   画布连线 ref（读写）
 * @param {() => void} ctx.clearSelection 载入后清空选中
 * @returns {{ agentName, agentModelCode, nodeTypeCatalog, graphMeta, publishedVersion, versions, showVersions, publishing, saving, error, okMsg, load, save, publish, toggleVersions, loadVersions, rollback }}
 */
export function useGraphPersistence({ agentId, nodes, edges, clearSelection }) {
  const agentName = ref('')
  const agentModelCode = ref('')
  const nodeTypeCatalog = ref([])
  const graphMeta = ref({ workflowType: '', version: null, enabled: null })
  const publishedVersion = ref(null)
  const versions = ref([])
  const showVersions = ref(false)
  const publishing = ref(false)
  const saving = ref(false)
  const error = ref('')
  const okMsg = ref('')

  /** 清掉环高亮样式（保存前复位，避免旧高亮残留）。 */
  function clearCycleHighlight() {
    edges.value = edges.value.map((e) => {
      const cls = (e.class || '').replace(/\bcycle-edge\b/g, '').trim()
      return { ...e, class: cls || undefined, style: undefined }
    })
  }

  /** 标红环上的边，提示用户存在不合规环路。 */
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
      agentModelCode.value = agentRes.data?.modelCode || ''
      publishedVersion.value = agentRes.data?.publishedVersion ?? null
      const catalog = typesRes.data || []
      nodeTypeCatalog.value = catalog
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
      const flow = toFlow(def || { nodes: [], edges: [] }, catalog)
      nodes.value = flow.nodes
      edges.value = flow.edges
      clearSelection()
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
      const def = toDefinition(nodes.value, edges.value)
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

  return {
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
    loadVersions,
    rollback
  }
}
