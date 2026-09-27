<template>
  <div class="graph-node" :class="{ selected: selected, ['nt-' + (data.nodeType || '')]: true }">
    <button
      type="button"
      class="node-del nodrag nopan"
      title="删除节点"
      aria-label="删除节点"
      @click.stop="removeSelf"
      @mousedown.stop
    >
      <svg class="node-del-icon" viewBox="0 0 10 10" aria-hidden="true">
        <path d="M2.2 2.2l5.6 5.6M7.8 2.2L2.2 7.8" />
      </svg>
    </button>

    <Handle id="target" class="node-handle" type="target" :position="Position.Left" />
    <Handle id="source" class="node-handle" type="source" :position="Position.Right" />
    <Handle id="target-top" class="node-handle" type="target" :position="Position.Top" />
    <Handle id="source-bottom" class="node-handle" type="source" :position="Position.Bottom" />

    <div class="graph-node-head">
      <NodeIcon :type="data.nodeType" size="md" />
      <div class="graph-node-text">
        <div class="type">{{ typeTitle }}</div>
        <div class="title">{{ data.label || typeTitle }}</div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { Handle, Position, useVueFlow } from '@vue-flow/core'
import { nodeTypeLabel } from '../constants/graphNodes'
import NodeIcon from './NodeIcon.vue'
import { confirmDialog } from '../composables/useConfirm'

const props = defineProps({
  id: { type: String, required: true },
  data: { type: Object, required: true },
  selected: { type: Boolean, default: false }
})

const { removeNodes } = useVueFlow()

const typeTitle = computed(() =>
  nodeTypeLabel(props.data.nodeType, props.data.typeLabel)
)

async function removeSelf() {
  const name = props.data?.label || typeTitle.value || '该节点'
  const ok = await confirmDialog({
    title: '删除节点',
    message: `确定删除节点「${name}」吗？删除后相连的连线也会一并移除。`,
    confirmText: '删除',
    cancelText: '取消',
    danger: true
  })
  if (!ok) return
  removeNodes([props.id])
}
</script>
