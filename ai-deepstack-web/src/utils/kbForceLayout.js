/**
 * 知识图谱力导向布局（只读画布）。
 * 以当前坐标为初值，迭代斥力 + 边弹簧 + 中心引力，避免节点挤成一行。
 *
 * @param nodes Vue Flow 节点（需含 id、position）
 * @param edges Vue Flow 边（source / target）
 * @param options iterations / width / height
 * @returns id → {x,y}
 */
export function forceLayout(nodes, edges, options = {}) {
  const iterations = options.iterations ?? 90
  const width = options.width ?? 960
  const height = options.height ?? 640
  const linkLen = options.linkLen ?? 150

  const pos = new Map()
  nodes.forEach((n, i) => {
    const angle = (i / Math.max(nodes.length, 1)) * Math.PI * 2
    const ring = 80 + (i % 7) * 18
    pos.set(n.id, {
      x: n.position?.x ?? width / 2 + Math.cos(angle) * ring,
      y: n.position?.y ?? height / 2 + Math.sin(angle) * ring,
      vx: 0,
      vy: 0
    })
  })

  const links = (edges || []).filter((e) => pos.has(e.source) && pos.has(e.target))
  const ids = [...pos.keys()]
  const cx = width / 2
  const cy = height / 2

  for (let iter = 0; iter < iterations; iter++) {
    const cool = 1 - iter / iterations

    for (let i = 0; i < ids.length; i++) {
      const a = pos.get(ids[i])
      for (let j = i + 1; j < ids.length; j++) {
        const b = pos.get(ids[j])
        let dx = a.x - b.x
        let dy = a.y - b.y
        let dist = Math.hypot(dx, dy) || 0.01
        if (dist < 1) {
          dx = (i - j) * 0.5
          dy = 0.5
          dist = 1
        }
        const force = (4200 / (dist * dist)) * cool
        const fx = (dx / dist) * force
        const fy = (dy / dist) * force
        a.vx += fx
        a.vy += fy
        b.vx -= fx
        b.vy -= fy
      }
    }

    for (const e of links) {
      const a = pos.get(e.source)
      const b = pos.get(e.target)
      const dx = b.x - a.x
      const dy = b.y - a.y
      const dist = Math.hypot(dx, dy) || 0.01
      const force = (dist - linkLen) * 0.045 * cool
      const fx = (dx / dist) * force
      const fy = (dy / dist) * force
      a.vx += fx
      a.vy += fy
      b.vx -= fx
      b.vy -= fy
    }

    for (const id of ids) {
      const p = pos.get(id)
      p.vx += (cx - p.x) * 0.01 * cool
      p.vy += (cy - p.y) * 0.01 * cool
      p.vx *= 0.72
      p.vy *= 0.72
      p.x += p.vx
      p.y += p.vy
    }
  }

  const out = new Map()
  for (const [id, p] of pos) {
    out.set(id, { x: p.x, y: p.y })
  }
  return out
}
