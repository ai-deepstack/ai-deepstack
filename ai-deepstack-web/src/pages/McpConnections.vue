<template>
  <div>
    <header class="page-head">
      <div>
        <h1>MCP</h1>
        <p>配置 MCP 服务连接。同步后工具会出现在工具列表，再去智能体页绑定。</p>
      </div>
      <button class="btn" type="button" @click="openCreate">添加连接</button>
    </header>

    <p v-if="error" class="error">{{ error }}</p>
    <div class="panel">
      <div class="table-wrap">
        <table class="table" v-if="list.length">
          <thead>
            <tr>
              <th>编码</th>
              <th>名称</th>
              <th>传输</th>
              <th>工具数</th>
              <th>状态</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in list" :key="item.id">
              <td><code>{{ item.connectionCode }}</code></td>
              <td>
                {{ item.connectionName }}
                <div v-if="item.lastError" class="error" style="font-size: 0.8rem; margin-top: 0.25rem">
                  {{ item.lastError }}
                </div>
              </td>
              <td><span class="pill">{{ item.transportName || item.transport }}</span></td>
              <td>{{ item.toolCount ?? 0 }}</td>
              <td>
                <span class="pill" :class="item.enabled === EnabledStatus.ENABLED ? 'pill-ok' : 'pill-off'">
                  {{ item.enabledName || (item.enabled === EnabledStatus.ENABLED ? '启用' : '停用') }}
                </span>
                <span v-if="item.secretConfigured" class="pill" style="margin-left: 0.25rem">密钥</span>
              </td>
              <td class="row-actions">
                <button class="btn-ghost" type="button" @click="openEdit(item)">编辑</button>
                <button class="btn-ghost" type="button" @click="openSecret(item)">密钥</button>
                <button class="btn-ghost" type="button" :disabled="syncingId === item.id" @click="sync(item)">
                  {{ syncingId === item.id ? '同步中…' : '同步工具' }}
                </button>
                <button class="btn-ghost" type="button" @click="toggle(item)">
                  {{ item.enabled === EnabledStatus.ENABLED ? '停用' : '启用' }}
                </button>
                <button class="btn-ghost" type="button" @click="remove(item)">删除</button>
              </td>
            </tr>
          </tbody>
        </table>
        <p v-else class="muted">暂无 MCP 连接。添加后点「同步工具」拉取远端清单。</p>
      </div>
    </div>

    <div v-if="drawer" class="drawer-mask" @click.self="closeDrawer">
      <aside class="drawer">
        <h2>
          {{ drawer === 'secret' ? '更新密钥' : form.id ? '编辑连接' : '添加连接' }}
        </h2>

        <template v-if="drawer === 'secret'">
          <div class="field">
            <label>密钥 / Bearer Token</label>
            <input v-model="secret" type="password" autocomplete="off" placeholder="留空则清除" />
          </div>
        </template>
        <template v-else>
          <div class="grid-2">
            <div class="field">
              <label>连接编码</label>
              <input v-model="form.connectionCode" :disabled="!!form.id" placeholder="如 fs / crm" />
            </div>
            <div class="field">
              <label>显示名称</label>
              <input v-model="form.connectionName" />
            </div>
            <div class="field">
              <label>传输类型</label>
              <PropSelect
                v-model="form.transport"
                :options="transportOptions"
                placeholder="选择传输"
              />
            </div>
            <div class="field">
              <label>超时 (ms)</label>
              <PropNumber v-model="form.requestTimeoutMs" :min="1000" :nullable="false" />
            </div>
          </div>

          <template v-if="form.transport === McpTransport.STDIO">
            <div class="field">
              <label>Command</label>
              <input v-model="form.command" placeholder="npx" />
            </div>
            <div class="field">
              <label>Args (JSON 数组)</label>
              <textarea
                v-model="form.argsJson"
                rows="3"
                placeholder='["-y","@modelcontextprotocol/server-filesystem","E:/data"]'
              />
            </div>
          </template>
          <template v-else>
            <div class="field">
              <label>Endpoint URL</label>
              <input v-model="form.endpoint" placeholder="https://mcp.example.com/sse" />
            </div>
            <div class="field" v-if="!form.id">
              <label>密钥（可选）</label>
              <input v-model="form.secret" type="password" autocomplete="off" />
            </div>
            <div class="field">
              <label>额外 Headers (JSON 对象，可选)</label>
              <textarea v-model="form.headersJson" rows="2" placeholder='{"X-Tenant":"demo"}' />
            </div>
          </template>
        </template>

        <p v-if="error" class="error">{{ error }}</p>
        <div class="drawer-actions">
          <button class="btn" type="button" :disabled="saving" @click="save">
            {{ saving ? '保存中…' : '保存' }}
          </button>
          <button class="btn-ghost" type="button" @click="closeDrawer">取消</button>
        </div>
      </aside>
    </div>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { del, get, pageList, post, put } from '../api/http'
import PropNumber from '../components/PropNumber.vue'
import PropSelect from '../components/PropSelect.vue'
import { EnabledStatus, McpTransport, transportOptions } from '../constants/enums'
import { confirmDialog } from '../composables/useConfirm'

const list = ref([])
const error = ref('')
const saving = ref(false)
const drawer = ref(null)
const syncingId = ref(null)
const secret = ref('')


const form = reactive({
  id: null,
  connectionCode: '',
  connectionName: '',
  transport: McpTransport.SSE,
  endpoint: '',
  command: '',
  argsJson: '',
  secret: '',
  headersJson: '',
  requestTimeoutMs: 30000,
  enabled: 1
})

async function load() {
  error.value = ''
  try {
    const res = await get('/api/mcp/connections/page', { pageNum: 1, pageSize: 100 })
    list.value = pageList(res)
  } catch (e) {
    error.value = e.message
  }
}

function resetForm() {
  Object.assign(form, {
    id: null,
    connectionCode: '',
    connectionName: '',
    transport: McpTransport.SSE,
    endpoint: '',
    command: '',
    argsJson: '',
    secret: '',
    headersJson: '',
    requestTimeoutMs: 30000,
    enabled: 1
  })
}

function openCreate() {
  resetForm()
  drawer.value = 'edit'
}

function openEdit(item) {
  Object.assign(form, {
    id: item.id,
    connectionCode: item.connectionCode,
    connectionName: item.connectionName || '',
    transport: item.transport ?? McpTransport.SSE,
    endpoint: item.endpoint || '',
    command: item.command || '',
    argsJson: item.argsJson || '',
    secret: '',
    headersJson: item.headersJson || '',
    requestTimeoutMs: item.requestTimeoutMs || 30000,
    enabled: item.enabled
  })
  drawer.value = 'edit'
}

function openSecret(item) {
  form.id = item.id
  form.connectionCode = item.connectionCode
  secret.value = ''
  drawer.value = 'secret'
}

function closeDrawer() {
  drawer.value = null
}

async function save() {
  saving.value = true
  error.value = ''
  try {
    if (drawer.value === 'secret') {
      await put(`/api/mcp/connections/${form.id}/secret`, { secret: secret.value })
    } else if (form.id) {
      await put('/api/mcp/connections', {
        id: form.id,
        connectionName: form.connectionName,
        transport: form.transport,
        endpoint: form.endpoint,
        command: form.command,
        argsJson: form.argsJson,
        headersJson: form.headersJson,
        requestTimeoutMs: form.requestTimeoutMs,
        enabled: form.enabled
      })
    } else {
      await post('/api/mcp/connections', { ...form })
    }
    closeDrawer()
    await load()
  } catch (e) {
    error.value = e.message
  } finally {
    saving.value = false
  }
}

async function sync(item) {
  syncingId.value = item.id
  error.value = ''
  try {
    const res = await post(`/api/mcp/connections/${item.id}/sync`)
    const n = res?.data?.upserted
    await load()
    if (typeof n === 'number') {
      // 成功后清掉上次错误展示
      error.value = ''
    }
  } catch (e) {
    error.value = e.message
    await load()
  } finally {
    syncingId.value = null
  }
}

async function toggle(item) {
  try {
    if (item.enabled === EnabledStatus.ENABLED) await put(`/api/mcp/connections/${item.id}/disable`)
    else await put(`/api/mcp/connections/${item.id}/enable`)
    await load()
  } catch (e) {
    error.value = e.message
  }
}

async function remove(item) {
  const ok = await confirmDialog({
    title: '删除 MCP 连接',
    message: `删除「${item.connectionName || item.connectionCode}」？该连接下工具将停用。`
  })
  if (!ok) return
  try {
    await del(`/api/mcp/connections/${item.id}`)
    await load()
  } catch (e) {
    error.value = e.message
  }
}

onMounted(load)
</script>
