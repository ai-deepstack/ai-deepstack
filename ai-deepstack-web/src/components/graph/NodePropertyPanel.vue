<template>
  <div class="field">
    <label>显示名称</label>
    <input v-model="node.data.label" />
  </div>
  <div class="field">
    <label>节点类型</label>
    <input :value="displayTypeLabel({ type: node.data.nodeType, label: node.data.typeLabel })" disabled />
  </div>
  <p
    v-if="node.data.nodeType === 'condition-node'"
    class="muted"
    style="font-size: 0.72rem; margin: 0 0 0.75rem; line-height: 1.45"
  >
    节点内表达式只负责求值。分支请在出边上的「路由条件」填写，例如
    <code>condition_result == true</code> / <code>DEFAULT</code>。
  </p>
  <p
    v-if="node.data.nodeType === 'llm-node' || node.data.nodeType === 'intent-node'"
    class="muted"
    style="font-size: 0.72rem; margin: 0 0 0.75rem"
  >
    双击节点可打开全屏配置。
  </p>
  <div v-if="node.data.nodeType === 'intent-node'" class="field">
    <label>输出变量</label>
    <input
      v-model="node.data.properties.outputVar"
      placeholder="intent"
      @blur="ensureIntentOutputVar"
    />
    <p class="muted" style="margin: 0.28rem 0 0; font-size: 0.68rem; line-height: 1.4">
      分类结果写入该变量；多个意图节点请用不同变量名，避免互相覆盖。后续可引用
      <code>{{ intentVarRef }}</code>
    </p>
  </div>
  <template v-for="field in selectedFields" :key="field.key">
    <div v-if="!(node.data.nodeType === 'intent-node' && field.key === 'outputVar')" class="field">
      <label>{{ field.label || field.key }}</label>
      <template v-if="field.widget === 'switch' || field.key === 'enableGraph'">
        <PropCheckbox
          v-model="node.data.properties[field.key]"
          :label="node.data.properties[field.key] ? '开' : '关'"
        />
      </template>
      <template v-else-if="isSliderField(field)">
        <input
          v-model.number="node.data.properties[field.key]"
          type="range"
          :min="field.key === 'temperature' ? 0 : 0"
          :max="field.key === 'temperature' ? 2 : 1"
          :step="field.key === 'temperature' ? 0.1 : 0.05"
        />
        <div class="muted" style="font-size: 0.72rem">{{ node.data.properties[field.key] }}</div>
      </template>
      <PropMultiSelect
        v-else-if="isMultiSelectField(field)"
        :model-value="asArray(node.data.properties[field.key])"
        :options="multiOptions(field, node, nodes, edges)"
        :placeholder="field.placeholder || '请选择（可多选）'"
        @update:model-value="(vals) => { node.data.properties[field.key] = vals }"
      />
      <PropSelect
        v-else-if="field.widget === 'select'"
        v-model="node.data.properties[field.key]"
        :options="field.options || []"
        placeholder="请选择"
      />
      <textarea
        v-else-if="field.widget === 'textarea' || field.widget === 'json'"
        :value="jsonFieldText(node, field.key)"
        rows="4"
        :placeholder="field.placeholder || ''"
        @input="onJsonOrTextInput(field, $event.target.value)"
      />
      <PropNumber
        v-else-if="field.widget === 'number'"
        v-model="node.data.properties[field.key]"
        :placeholder="field.placeholder || ''"
      />
      <input
        v-else
        v-model="node.data.properties[field.key]"
        :placeholder="field.placeholder || ''"
      />
    </div>
  </template>
  <button class="btn-ghost" type="button" style="width: 100%; color: var(--danger)" @click="emit('remove')">删除节点</button>

  <!-- LLM / 意图全屏配置：面板带 backdrop-filter 会约束 fixed，Teleport 到 body 保持全屏 -->
  <Teleport to="body">
    <div v-if="configOpen" class="modal-mask" @click.self="closeConfig">
        <div class="modal-card modal-wide">
          <h3 style="margin: 0 0 0.8rem">
            编辑{{ displayTypeLabel({ type: node.data.nodeType }) }}配置
          </h3>
          <template v-if="node.data.nodeType === 'llm-node'">
            <div class="field">
              <label>系统提示词</label>
              <textarea v-model="node.data.properties.systemPrompt" rows="12" />
            </div>
            <div class="field">
              <label>温度 {{ node.data.properties.temperature }}</label>
              <input v-model.number="node.data.properties.temperature" type="range" min="0" max="2" step="0.1" />
            </div>
            <div class="field">
              <label>输出变量</label>
              <input v-model="node.data.properties.outputVar" />
            </div>
          </template>
          <template v-else-if="node.data.nodeType === 'intent-node'">
            <div class="field">
              <label>意图类别</label>
              <PropMultiSelect
                :model-value="asArray(node.data.properties.categories)"
                :options="intentCategoryOptions"
                placeholder="从意图字典多选"
                @update:model-value="(vals) => { node.data.properties.categories = vals }"
              />
            </div>
            <div class="field">
              <label>输出变量</label>
              <input
                v-model="node.data.properties.outputVar"
                placeholder="intent"
                @blur="ensureIntentOutputVar"
              />
              <p class="muted" style="margin: 0.28rem 0 0; font-size: 0.72rem">
                多个意图节点请使用不同变量名；边条件可写 <code>{{ intentVarRef }} == HEALTH</code>
              </p>
            </div>
            <div class="field">
              <label>分类提示词</label>
              <textarea v-model="node.data.properties.prompt" rows="10" />
            </div>
          </template>
          <div class="row-actions" style="justify-content: flex-end">
            <button class="btn" type="button" @click="closeConfig">完成</button>
          </div>
        </div>
    </div>
  </Teleport>
</template>

<script setup>
import { computed } from 'vue'
import PropCheckbox from '../PropCheckbox.vue'
import PropMultiSelect from '../PropMultiSelect.vue'
import PropNumber from '../PropNumber.vue'
import PropSelect from '../PropSelect.vue'
import {
  asArray,
  displayTypeLabel,
  isMultiSelectField,
  isSliderField,
  jsonFieldText,
  multiOptions
} from '../../utils/graphModel'

const props = defineProps({
  node: { type: Object, required: true },
  nodes: { type: Array, default: () => [] },
  edges: { type: Array, default: () => [] },
  catalog: { type: Array, default: () => [] },
  configOpen: { type: Boolean, default: false }
})

const emit = defineEmits(['remove', 'update:configOpen'])

const selectedFields = computed(() => {
  const nt = props.catalog.find((x) => x.type === props.node?.data?.nodeType)
  return nt?.propertyFields || []
})

const intentCategoryOptions = computed(() => {
  const nt = props.catalog.find((x) => x.type === 'intent-node')
  const field = (nt?.propertyFields || []).find((f) => f.key === 'categories')
  return field?.options || []
})

const intentOutputVar = computed(() => {
  const v = props.node?.data?.properties?.outputVar
  return v != null && String(v).trim() ? String(v).trim() : 'intent'
})

const intentVarRef = computed(() => `\${${intentOutputVar.value}}`)

/** 意图节点 outputVar 失焦归位：空则回退 intent，非空去空白。 */
function ensureIntentOutputVar() {
  if (props.node?.data?.nodeType !== 'intent-node') return
  const v = props.node.data.properties.outputVar
  if (v == null || String(v).trim() === '') {
    props.node.data.properties.outputVar = 'intent'
  } else {
    props.node.data.properties.outputVar = String(v).trim()
  }
}

function onJsonOrTextInput(field, text) {
  if (field.widget === 'json' || field.key === 'inputVars' || field.key === 'paramMappings') {
    try {
      props.node.data.properties[field.key] = JSON.parse(text)
    } catch {
      props.node.data.properties[field.key] = text
    }
  } else {
    props.node.data.properties[field.key] = text
  }
}

function closeConfig() {
  emit('update:configOpen', false)
}
</script>
