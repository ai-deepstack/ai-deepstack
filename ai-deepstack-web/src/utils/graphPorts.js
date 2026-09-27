/** 入出度限制校验（对齐节点库 maxInPorts / maxOutPorts） */

/**
 * @param {object} params
 * @param {string} params.source
 * @param {string} params.target
 * @param {Array} params.nodes
 * @param {Array} params.edges
 * @param {Array} params.catalog
 * @param {string} [params.ignoreEdgeId] 改接时忽略原边
 */
export function isValidConnection({ source, target, nodes, edges, catalog, ignoreEdgeId }) {
  if (!source || !target) return true
  if (source === target) return false
  const sourceNode = nodes.find((n) => n.id === source)
  const targetNode = nodes.find((n) => n.id === target)
  if (!sourceNode || !targetNode) return true

  const sourceMeta = catalog?.find((x) => x.type === sourceNode.data?.nodeType)
  const targetMeta = catalog?.find((x) => x.type === targetNode.data?.nodeType)
  const maxOut = Number(sourceMeta?.maxOutPorts ?? -1)
  const maxIn = Number(targetMeta?.maxInPorts ?? -1)

  const otherEdges = (edges || []).filter((e) => e.id !== ignoreEdgeId)
  if (maxOut === 0) return false
  if (maxIn === 0) return false
  if (maxOut > 0) {
    const outCount = otherEdges.filter((e) => e.source === source).length
    if (outCount >= maxOut) return false
  }
  if (maxIn > 0) {
    const inCount = otherEdges.filter((e) => e.target === target).length
    if (inCount >= maxIn) return false
  }
  return true
}
