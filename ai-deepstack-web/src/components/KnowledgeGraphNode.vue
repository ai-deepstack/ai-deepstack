<template>
  <div class="kb-graph-node" :class="['lbl-' + (data.label || 'Node').toLowerCase(), { selected }]">
    <Handle id="target" class="kb-handle" type="target" :position="Position.Left" />
    <Handle id="source" class="kb-handle" type="source" :position="Position.Right" />
    <div class="kb-node-type">{{ data.label || 'Node' }}</div>
    <div class="kb-node-title" :title="data.displayTitle">{{ data.displayTitle }}</div>
  </div>
</template>

<script setup>
/**
 * 知识图谱只读节点：Document / Chunk / Entity 分色展示。
 * 无删除手柄；连线仅展示，不可编辑。
 */
import { Handle, Position } from '@vue-flow/core'

defineProps({
  id: { type: String, required: true },
  data: { type: Object, required: true },
  selected: { type: Boolean, default: false }
})
</script>

<style scoped>
.kb-graph-node {
  min-width: 128px;
  max-width: 200px;
  padding: 0.45rem 0.65rem;
  border-radius: 10px;
  border: 1px solid rgba(148, 163, 184, 0.35);
  background: linear-gradient(160deg, rgba(14, 26, 42, 0.96), rgba(8, 14, 26, 0.96));
  box-shadow: 0 0 14px rgba(0, 0, 0, 0.25);
  color: var(--ink);
  font-size: 0.78rem;
}
.kb-graph-node.selected {
  box-shadow: 0 0 0 2px rgba(61, 224, 197, 0.45), 0 0 18px rgba(61, 224, 197, 0.2);
}
.kb-node-type {
  font-family: var(--font-mono);
  font-size: 0.58rem;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  margin-bottom: 0.12rem;
  opacity: 0.85;
}
.kb-node-title {
  font-weight: 600;
  font-size: 0.78rem;
  line-height: 1.25;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.lbl-document {
  border-color: rgba(78, 168, 255, 0.55);
  box-shadow: 0 0 14px rgba(78, 168, 255, 0.12);
}
.lbl-document .kb-node-type { color: #4ea8ff; }
.lbl-chunk {
  border-color: rgba(61, 224, 197, 0.5);
  box-shadow: 0 0 14px rgba(61, 224, 197, 0.12);
}
.lbl-chunk .kb-node-type { color: var(--cyan); }
.lbl-entity {
  border-color: rgba(232, 185, 92, 0.55);
  box-shadow: 0 0 14px rgba(232, 185, 92, 0.12);
}
.lbl-entity .kb-node-type { color: #e8b95c; }
.kb-handle {
  width: 6px !important;
  height: 6px !important;
  min-width: 6px !important;
  min-height: 6px !important;
  border-radius: 50% !important;
  background: var(--cyan) !important;
  border: 1.5px solid #07101c !important;
  opacity: 0.7;
}
</style>
