<template>
  <div>
    <header class="page-head">
      <div>
        <h1>系统设置</h1>
        <p>按分类调整开关和阈值。是否类用 0/1；模型类从下拉选择。</p>
      </div>
      <button class="btn" type="button" :disabled="saving" @click="save">{{ saving ? '保存中…' : '保存' }}</button>
    </header>

    <p v-if="error" class="error">{{ error }}</p>
    <p v-if="okMsg" class="muted">{{ okMsg }}</p>

    <div v-if="groups.length" class="row-actions settings-tabs">
      <button
        v-for="group in groups"
        :key="group"
        type="button"
        class="btn-ghost"
        :style="activeGroup === group ? 'background: var(--cyan-soft); color: var(--cyan-bright)' : ''"
        @click="activeGroup = group"
      >
        {{ group }}
        <span class="tab-count">{{ byGroup[group].length }}</span>
      </button>
    </div>

    <div v-if="activeGroup" class="panel">
      <div class="table-wrap">
        <table class="table" v-if="byGroup[activeGroup]?.length">
          <thead>
            <tr>
              <th style="min-width: 10rem">配置名</th>
              <th>说明</th>
              <th style="width: 4.5rem">来源</th>
              <th style="min-width: 12rem">值</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in byGroup[activeGroup]" :key="item.configKey">
              <td>
                <div class="cfg-name">{{ item.configName || item.configKey }}</div>
                <code class="cfg-key">{{ item.configKey }}</code>
              </td>
              <td class="muted">{{ item.description || '—' }}</td>
              <td><span class="src-tag" :data-src="item.source">{{ sourceLabel(item.source) }}</span></td>
              <td>
                <PropCheckbox
                  v-if="item.valueType === 'yes_no'"
                  :model-value="item.configValue === '1' || item.configValue === 1 ? '1' : '0'"
                  true-value="1"
                  false-value="0"
                  :label="item.valueName || ((item.configValue === '1' || item.configValue === 1) ? '是' : '否')"
                  @update:model-value="(v) => onToggle(item, v === '1')"
                />
                <PropSelect
                  v-else-if="item.valueType === 'model'"
                  v-model="item.configValue"
                  class="settings-input"
                  :options="[{ value: '', label: '（未指定）' }, ...modelOptionsFor(item)]"
                  placeholder="（未指定）"
                />
                <PropNumber
                  v-else-if="item.valueType === 'int'"
                  class="settings-input"
                  :model-value="item.configValue === '' || item.configValue == null ? null : Number(item.configValue)"
                  :nullable="true"
                  placeholder="整数"
                  :aria-label="item.configName || item.configKey"
                  @update:model-value="(v) => { item.configValue = v == null ? '' : String(v) }"
                />
                <input
                  v-else
                  v-model="item.configValue"
                  class="settings-input"
                  placeholder="文本"
                  :aria-label="item.configName || item.configKey"
                />
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { get, put } from '../api/http'
import PropCheckbox from '../components/PropCheckbox.vue'
import PropNumber from '../components/PropNumber.vue'
import PropSelect from '../components/PropSelect.vue'
import { ModelType } from '../constants/enums'

const list = ref([])
const modelOptionsByType = reactive({
  [ModelType.CHAT]: [],
  [ModelType.EMBEDDING]: [],
  all: []
})
const error = ref('')
const okMsg = ref('')
const saving = ref(false)

/** 分类展示顺序；接口未归组的落到「其它」。 */
const GROUP_ORDER = [
  '图谱',
  '长期记忆',
  '知识库召回',
  '工具 / MCP',
  'Checkpoint',
  '模型 / 对话',
  '配额',
  '轨迹脱敏',
  '人工确认',
  '告警',
  '其它'
]

const activeGroup = ref('')

function groupOf(item) {
  return item.group || '其它'
}

const groups = computed(() => {
  const present = new Set(list.value.map(groupOf))
  const ordered = GROUP_ORDER.filter((g) => present.has(g))
  for (const g of present) {
    if (!ordered.includes(g)) ordered.push(g)
  }
  return ordered
})

const byGroup = computed(() => {
  const map = {}
  for (const item of list.value) {
    const g = groupOf(item)
    if (!map[g]) map[g] = []
    map[g].push(item)
  }
  return map
})

watch(groups, (gs) => {
  if (!gs.includes(activeGroup.value)) {
    activeGroup.value = gs[0] || ''
  }
}, { immediate: true })

function modelOptionsFor(item) {
  const mt = item.modelType
  if (mt === ModelType.CHAT || mt === ModelType.EMBEDDING) {
    return modelOptionsByType[mt] || []
  }
  return modelOptionsByType.all
}

function onToggle(item, checked) {
  item.configValue = checked ? '1' : '0'
  item.valueName = checked ? '是' : '否'
}

function sourceLabel(source) {
  if (source === 'db') return '库'
  if (source === 'yml') return 'yml'
  if (source === 'default') return '默认'
  return source || '—'
}

async function loadModelOptions() {
  const [chat, emb, all] = await Promise.all([
    get('/api/models/options', { modelType: ModelType.CHAT }),
    get('/api/models/options', { modelType: ModelType.EMBEDDING }),
    get('/api/models/options')
  ])
  modelOptionsByType[ModelType.CHAT] = chat?.data || []
  modelOptionsByType[ModelType.EMBEDDING] = emb?.data || []
  modelOptionsByType.all = all?.data || []
}

async function load() {
  error.value = ''
  okMsg.value = ''
  try {
    const [settingsRes] = await Promise.all([
      get('/api/settings'),
      loadModelOptions()
    ])
    list.value = (settingsRes?.data || []).map((x) => reactive({
      configKey: x.configKey,
      configName: x.configName || x.configKey,
      configValue: x.configValue == null ? '' : String(x.configValue),
      valueType: x.valueType,
      description: x.description,
      valueName: x.valueName,
      modelType: x.modelType,
      group: x.group,
      source: x.source
    }))
  } catch (e) {
    error.value = e.message || '加载失败'
  }
}

async function save() {
  error.value = ''
  okMsg.value = ''
  saving.value = true
  try {
    const values = {}
    for (const item of list.value) {
      values[item.configKey] = item.configValue == null ? '' : String(item.configValue)
    }
    await put('/api/settings', { values })
    okMsg.value = '已保存'
    await load()
  } catch (e) {
    error.value = e.message || '保存失败'
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.switch-row {
  display: inline-flex;
  align-items: center;
  gap: 0.5rem;
  cursor: pointer;
}
.settings-tabs {
  margin-bottom: 0.85rem;
}
.tab-count {
  margin-left: 0.35rem;
  font-size: 0.75rem;
  opacity: 0.7;
}
.settings-input {
  width: min(100%, 280px);
}
.settings-input :deep(.prop-dd-trigger),
.settings-input.prop-ctl {
  width: 100%;
}
.cfg-name {
  font-weight: 600;
  line-height: 1.3;
}
.cfg-key {
  display: inline-block;
  margin-top: 0.2rem;
  font-size: 0.75rem;
  opacity: 0.65;
}
.src-tag {
  font-size: 0.75rem;
  opacity: 0.75;
}
.src-tag[data-src='db'] {
  opacity: 1;
  font-weight: 600;
}
</style>
