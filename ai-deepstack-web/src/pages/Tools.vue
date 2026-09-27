<template>
  <div>
    <header class="page-head">
      <div>
        <h1>工具</h1>
        <p>本地工具和 MCP 同步进来的工具。在智能体页勾选绑定后才会参与对话。</p>
      </div>
      <button class="btn" type="button" @click="openCreate">注册本地工具</button>
    </header>

    <p v-if="error" class="error">{{ error }}</p>
    <div class="panel">
      <div class="table-wrap">
        <table class="table" v-if="list.length">
          <thead>
            <tr>
              <th>编码</th>
              <th>名称</th>
              <th>来源</th>
              <th>Handler / 远端</th>
              <th>状态</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in list" :key="item.id">
              <td><code>{{ item.toolCode }}</code></td>
              <td>{{ item.toolName }}</td>
              <td>
                <span class="pill" :class="isMcp(item) ? 'pill-brass' : 'pill-teal'">
                  {{ item.sourceTypeName || (isMcp(item) ? 'MCP' : 'LOCAL') }}
                </span>
              </td>
              <td class="muted">
                <template v-if="isMcp(item)">
                  {{ item.mcpConnectionCode }} / {{ item.mcpToolName }}
                </template>
                <template v-else>{{ item.handlerBean || '—' }}</template>
              </td>
              <td>
                <span class="pill" :class="item.enabled === EnabledStatus.ENABLED ? 'pill-ok' : 'pill-off'">
                  {{ item.enabledName || (item.enabled === EnabledStatus.ENABLED ? '启用' : '停用') }}
                </span>
              </td>
              <td class="row-actions">
                <button class="btn-ghost" type="button" @click="openEdit(item)">编辑</button>
                <button class="btn-ghost" type="button" @click="toggle(item)">
                  {{ item.enabled === EnabledStatus.ENABLED ? '停用' : '启用' }}
                </button>
              </td>
            </tr>
          </tbody>
        </table>
        <p v-else class="muted">暂无工具。本地在此注册；MCP 请到「MCP」页同步。</p>
      </div>
    </div>

    <div v-if="drawer" class="drawer-mask" @click.self="drawer = false">
      <aside class="drawer">
        <h2>{{ form.id ? '编辑工具' : '注册本地工具' }}</h2>
        <p v-if="isMcp(form)" class="muted" style="margin-top: 0">
          MCP 工具由连接同步维护；此处仅可改显示名与描述。
        </p>
        <div class="field">
          <label>工具编码</label>
          <input v-model="form.toolCode" :disabled="!!form.id" />
        </div>
        <div class="field">
          <label>工具名称</label>
          <input v-model="form.toolName" />
        </div>
        <div class="field" v-if="!isMcp(form)">
          <label>Handler Bean</label>
          <input v-model="form.handlerBean" placeholder="Spring bean 名" :disabled="isMcp(form)" />
        </div>
        <div class="field" v-else>
          <label>MCP</label>
          <input
            :value="`${form.mcpConnectionCode || ''} / ${form.mcpToolName || ''}`"
            disabled
          />
        </div>
        <div class="field">
          <label>描述</label>
          <textarea v-model="form.description" rows="3" />
        </div>
        <p v-if="error" class="error">{{ error }}</p>
        <div class="drawer-actions">
          <button class="btn" type="button" :disabled="saving" @click="save">保存</button>
          <button class="btn-ghost" type="button" @click="drawer = false">取消</button>
        </div>
      </aside>
    </div>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { get, pageList, post, put } from '../api/http'
import { EnabledStatus, ToolSourceType } from '../constants/enums'

const list = ref([])
const error = ref('')
const saving = ref(false)
const drawer = ref(false)
const form = reactive({
  id: null,
  toolCode: '',
  toolName: '',
  handlerBean: '',
  description: '',
  enabled: 1,
  sourceType: ToolSourceType.LOCAL,
  mcpConnectionCode: '',
  mcpToolName: ''
})

function isMcp(item) {
  return item?.sourceType === ToolSourceType.MCP
}

async function load() {
  error.value = ''
  try {
    const res = await get('/api/tools/page', { pageNum: 1, pageSize: 100 })
    list.value = pageList(res)
  } catch (e) {
    error.value = e.message
  }
}

function openCreate() {
  Object.assign(form, {
    id: null,
    toolCode: '',
    toolName: '',
    handlerBean: '',
    description: '',
    enabled: 1,
    sourceType: ToolSourceType.LOCAL,
    mcpConnectionCode: '',
    mcpToolName: ''
  })
  drawer.value = true
}

function openEdit(item) {
  Object.assign(form, {
    id: item.id,
    toolCode: item.toolCode,
    toolName: item.toolName,
    handlerBean: item.handlerBean || '',
    description: item.description || '',
    enabled: item.enabled,
    sourceType: item.sourceType ?? ToolSourceType.LOCAL,
    mcpConnectionCode: item.mcpConnectionCode || '',
    mcpToolName: item.mcpToolName || ''
  })
  drawer.value = true
}

async function save() {
  saving.value = true
  error.value = ''
  try {
    if (isMcp(form)) {
      // MCP：仅允许改名称/描述（后端若拒绝 handler 变更也无妨）
      await put('/api/tools', {
        id: form.id,
        toolName: form.toolName,
        description: form.description
      })
    } else if (form.id) {
      await put('/api/tools', {
        id: form.id,
        toolName: form.toolName,
        handlerBean: form.handlerBean,
        description: form.description
      })
    } else {
      await post('/api/tools', {
        toolCode: form.toolCode,
        toolName: form.toolName,
        handlerBean: form.handlerBean,
        description: form.description
      })
    }
    drawer.value = false
    await load()
  } catch (e) {
    error.value = e.message
  } finally {
    saving.value = false
  }
}

async function toggle(item) {
  try {
    if (item.enabled === EnabledStatus.ENABLED) await put(`/api/tools/${item.id}/disable`)
    else await put(`/api/tools/${item.id}/enable`)
    await load()
  } catch (e) {
    error.value = e.message
  }
}

onMounted(load)
</script>
