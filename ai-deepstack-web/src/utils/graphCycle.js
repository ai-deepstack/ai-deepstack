/** 图编排：环检测、边高亮辅助 */

/**
 * @param {Array<{id:string}>} nodes
 * @param {Array<{source:string,target:string,id?:string}>} edges
 * @returns {string[]|null} 环路径（含回到起点前的节点），无环返回 null
 */
export function detectCycle(nodes, edges) {
  const nodeIds = new Set((nodes || []).map((n) => n.id))
  const adjacency = new Map()
  for (const id of nodeIds) adjacency.set(id, [])
  for (const e of edges || []) {
    if (!nodeIds.has(e.source) || !nodeIds.has(e.target)) continue
    adjacency.get(e.source).push(e.target)
  }

  const visited = new Set()
  const inStack = new Set()
  const parent = new Map()

  function dfs(u) {
    visited.add(u)
    inStack.add(u)
    for (const v of adjacency.get(u) || []) {
      if (!visited.has(v)) {
        parent.set(v, u)
        const c = dfs(v)
        if (c) return c
      } else if (inStack.has(v)) {
        const path = [v]
        let cur = u
        while (cur && cur !== v) {
          path.push(cur)
          cur = parent.get(cur)
        }
        path.push(v)
        path.reverse()
        return path
      }
    }
    inStack.delete(u)
    return null
  }

  for (const id of nodeIds) {
    if (!visited.has(id)) {
      const c = dfs(id)
      if (c) return c
    }
  }
  return null
}

/**
 * 参与环的边 id 集合（两端都在环节点集合内的边）
 */
export function getCycleEdgeIds(nodes, edges, cyclePath) {
  if (!cyclePath || cyclePath.length < 2) return new Set()
  const inCycle = new Set(cyclePath)
  const ids = new Set()
  for (const e of edges || []) {
    if (inCycle.has(e.source) && inCycle.has(e.target)) {
      ids.add(e.id)
    }
  }
  return ids
}
