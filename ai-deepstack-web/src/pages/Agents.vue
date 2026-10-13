<template>
  <div>
    <header class="page-head">
      <div>
        <h1>Agent</h1>
        <p>创建和管理 Agent，绑定模型、工具、知识库，或进入图编排。</p>
      </div>
      <div class="row-actions">
        <button class="btn" type="button" @click="openCreate">新建 Agent</button>
      </div>
    </header>

    <p v-if="error" class="error">{{ error }}</p>

    <div v-if="list.length" class="agent-card-grid">
      <article
        v-for="item in list"
        :key="item.id"
        class="agent-card"
        :class="{ 'is-off': item.enabled !== EnabledStatus.ENABLED }"
      >
        <div class="agent-card-cover">
          <img
            :src="coverOf(item)"
            :alt="item.agentName"
            loading="lazy"
            @error="onCoverError"
          />
          <div class="agent-card-cover-fade" />
          <div class="agent-card-badges">
            <span class="pill" :class="item.orchestrateMode === OrchestrateMode.GRAPH ? 'pill-teal' : 'pill-brass'">
              {{ item.orchestrateModeName || (item.orchestrateMode === OrchestrateMode.GRAPH ? '编排图' : '对话') }}
            </span>
            <span class="pill" :class="item.enabled === EnabledStatus.ENABLED ? 'pill-ok' : 'pill-off'">
              {{ item.enabledName || (item.enabled === EnabledStatus.ENABLED ? '启用' : '停用') }}
            </span>
          </div>
        </div>
        <div class="agent-card-body">
          <h3 class="agent-card-title">{{ item.agentName }}</h3>
          <code class="agent-card-code" :title="item.agentCode">编码 {{ item.agentCode }}</code>
          <p class="agent-card-meta muted">
            {{ item.modelName || item.modelCode || '未绑定模型' }}
          </p>
          <div class="agent-card-actions">
            <button
              class="btn-ghost"
              type="button"
              :disabled="item.enabled !== EnabledStatus.ENABLED"
              :title="item.enabled !== EnabledStatus.ENABLED ? '请先启用再运行' : '对话试用'"
              @click="openTry(item)"
            >运行</button>
            <router-link class="btn-ghost" :to="`/agents/${item.id}`">查看</router-link>
            <button class="btn-ghost" type="button" @click="openEdit(item)">配置</button>
            <button class="btn-ghost" type="button" @click="openCopy(item)">复制</button>
            <router-link class="btn-ghost" :to="`/agents/${item.id}/stats`">统计</router-link>
            <router-link
              v-if="item.orchestrateMode === OrchestrateMode.GRAPH"
              class="btn-ghost"
              :to="`/agents/${item.id}/graph`"
            >图编排</router-link>
            <button class="btn-ghost" type="button" @click="toggle(item)">
              {{ item.enabled === EnabledStatus.ENABLED ? '停用' : '启用' }}
            </button>
          </div>
        </div>
      </article>
    </div>
    <div v-else class="panel">
      <p class="muted" style="margin: 0">暂无 Agent</p>
    </div>

    <div v-if="drawer" class="drawer-mask" @click.self="closeDrawer">
      <aside class="drawer" style="width: min(640px, 100vw)">
        <h2>{{ drawerTitle }}</h2>
        <div class="row-actions" style="margin: 0.6rem 0 1rem">
          <button
            v-for="t in tabs"
            :key="t.id"
            type="button"
            class="btn-ghost"
            :style="tab === t.id ? 'background: var(--cyan-soft); color: var(--cyan-bright)' : ''"
            @click="tab = t.id"
          >{{ t.label }}</button>
        </div>

        <template v-if="tab === 'basic'">
          <div class="field" v-if="form.id">
            <label>编码</label>
            <input :value="form.agentCode" disabled />
            <p class="muted" style="margin: 0.35rem 0 0; font-size: 0.78rem">由系统自动生成，不可修改</p>
          </div>
          <div class="grid-2">
            <div class="field">
              <label>名称</label>
              <input v-model="form.agentName" />
              <p v-if="sourceAgentId" class="muted" style="margin: 0.35rem 0 0; font-size: 0.78rem">
                名称全局唯一，请与源 Agent 区分
              </p>
            </div>
            <div class="field">
              <label>模型</label>
              <PropSelect
                v-model="form.modelCode"
                :options="modelOptions"
                placeholder="选择模型"
              />
            </div>
            <div class="field">
              <label>编排模式</label>
              <PropSelect
                v-model="form.orchestrateMode"
                :options="orchestrateOptions"
                placeholder="选择编排模式"
              />
            </div>
          </div>
          <div class="field">
            <label>封面图</label>
            <CoverImageUpload v-model="form.coverUrl" />
          </div>
          <div class="field">
            <label>系统提示词</label>
            <textarea v-model="form.systemPrompt" rows="5" />
          </div>
          <div class="grid-2">
            <div class="field">
              <label>QPS 上限</label>
              <PropNumber v-model="form.quotaQps" :min="0" placeholder="空=不限" />
            </div>
            <div class="field">
              <label>并发上限</label>
              <PropNumber v-model="form.quotaConcurrency" :min="0" placeholder="空=不限" />
            </div>
            <div class="field">
              <label>每日 token 上限</label>
              <PropNumber v-model="form.quotaDailyTokens" :min="0" placeholder="空=不限" />
            </div>
            <div class="field">
              <label>人工确认超时（分钟）</label>
              <PropNumber v-model="form.hitlTimeoutMinutes" :min="0" placeholder="空=用全局" />
            </div>
          </div>
          <p class="muted" style="margin: 0; font-size: 0.78rem">配额与人工确认超时；填 0 或留空表示不限，走系统设置。</p>
        </template>

        <template v-else-if="tab === 'tools'">
          <p class="muted" style="margin-top: 0">勾选需要的工具后点保存。</p>
          <div class="panel-quiet" style="max-height: 360px; overflow: auto; padding: 0.75rem">
            <PropCheckboxGroup v-model="boundToolIds" :options="tools" empty-text="暂无可用工具" />
          </div>
        </template>

        <template v-else>
          <p class="muted" style="margin-top: 0">按 baseCode 绑定知识库</p>
          <div class="panel-quiet" style="max-height: 360px; overflow: auto; padding: 0.75rem">
            <PropCheckboxGroup v-model="boundKbCodes" :options="kbs" empty-text="暂无知识库" />
          </div>
        </template>

        <p v-if="error" class="error">{{ error }}</p>
        <div class="drawer-actions">
          <button class="btn" type="button" :disabled="saving" @click="save">{{ saving ? '保存中…' : '保存' }}</button>
          <button class="btn-ghost" type="button" @click="closeDrawer">取消</button>
          <router-link
            v-if="form.id && form.orchestrateMode === OrchestrateMode.GRAPH"
            class="btn-ghost"
            :to="`/agents/${form.id}/graph`"
            style="display: inline-flex"
          >打开图编排</router-link>
        </div>
      </aside>
    </div>

    <AgentTryDialog
      :open="tryOpen"
      :agent-code="tryAgent?.agentCode || ''"
      :agent-name="tryAgent?.agentName || ''"
      @close="closeTry"
    />
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { get, pageList, post, put } from '../api/http'
import CoverImageUpload from '../components/CoverImageUpload.vue'
import AgentTryDialog from '../components/AgentTryDialog.vue'
import PropCheckboxGroup from '../components/PropCheckboxGroup.vue'
import PropNumber from '../components/PropNumber.vue'
import PropSelect from '../components/PropSelect.vue'
import { usePageList } from '../composables/usePageList'
import { EnabledStatus, ModelType, OrchestrateMode, orchestrateOptions } from '../constants/enums'
import { asId, sameId } from '../utils/ids'

const DEFAULT_COVER = '/agent-cover-default.svg'

const route = useRoute()
const router = useRouter()
const models = ref([])
const tools = ref([])
const kbs = ref([])
const saving = ref(false)
const drawer = ref(false)
const sourceAgentId = ref(null)
const tryOpen = ref(false)
const tryAgent = ref(null)
const tab = ref('basic')
const tabs = [
  { id: 'basic', label: '基础' },
  { id: 'tools', label: '工具' },
  { id: 'kb', label: '知识库' }
]
const boundToolIds = ref([])
const boundKbCodes = ref([])
// 列表分页与过期响应丢弃交给 usePageList；error 由它持有，供抽屉与列表共用
const {
  list,
  error,
  load: loadPage
} = usePageList({
  pageSize: 100,
  fetcher: ({ pageNum, pageSize }) =>
    Promise.all([
      get('/api/agents/page', { pageNum, pageSize }),
      get('/api/models/options', { modelType: ModelType.CHAT }),
      get('/api/tools/options'),
      get('/api/kb/options')
    ]),
  onData: ([a, m, t, k]) => {
    list.value = pageList(a)
    models.value = m.data || []
    tools.value = t.data || []
    kbs.value = k.data || []
  }
})
// 工具绑定请求令牌：快速切换 Agent 时丢弃过期响应
let toolsSeq = 0
const form = reactive({
  id: null,
  agentCode: '',
  agentName: '',
  modelCode: null,
  systemPrompt: 'You are a helpful assistant.',
  orchestrateMode: OrchestrateMode.CHAT,
  coverUrl: '',
  enabled: EnabledStatus.ENABLED,
  quotaQps: null,
  quotaConcurrency: null,
  quotaDailyTokens: null,
  hitlTimeoutMinutes: null
})

const modelOptions = computed(() => models.value)
const drawerTitle = computed(() => {
  if (form.id) return '配置 Agent'
  if (sourceAgentId.value) return '复制 Agent'
  return '新建 Agent'
})

function coverOf(item) {
  const url = (item?.coverUrl || '').trim()
  return url || DEFAULT_COVER
}

function onCoverError(e) {
  if (e?.target && e.target.src !== DEFAULT_COVER) {
    e.target.src = DEFAULT_COVER
  }
}

function uniqueCopyName(baseName) {
  const root = (baseName || 'Agent').trim() || 'Agent'
  const existing = new Set(list.value.map((x) => x.agentName))
  let candidate = `${root} 副本`
  if (!existing.has(candidate)) return candidate
  for (let i = 2; i < 100; i++) {
    candidate = `${root} 副本 ${i}`
    if (!existing.has(candidate)) return candidate
  }
  return `${root} 副本 ${Date.now()}`
}

async function openEditFromQuery() {
  const editId = asId(route.query.edit)
  if (!editId || !list.value.length) return
  const item = list.value.find((x) => sameId(x.id, editId))
  if (!item) return
  await openEdit(item)
  const q = { ...route.query }
  delete q.edit
  router.replace({ path: '/agents', query: q })
}

async function load() {
  await loadPage()
  await openEditFromQuery()
}

watch(() => route.query.edit, () => {
  if (route.path === '/agents' && route.query.edit) openEditFromQuery()
})

function openTry(item) {
  if (item.enabled !== EnabledStatus.ENABLED) return
  tryAgent.value = item
  tryOpen.value = true
}

function closeTry() {
  tryOpen.value = false
  tryAgent.value = null
}

function openCreate() {
  toolsSeq++ // 使在途的工具请求失效
  sourceAgentId.value = null
  Object.assign(form, {
    id: null,
    agentCode: '',
    agentName: '',
    modelCode: models.value[0]?.value ?? null,
    systemPrompt: 'You are a helpful assistant.',
    orchestrateMode: OrchestrateMode.CHAT,
    coverUrl: '',
    enabled: EnabledStatus.ENABLED,
    quotaQps: null,
    quotaConcurrency: null,
    quotaDailyTokens: null,
    hitlTimeoutMinutes: null
  })
  boundToolIds.value = []
  boundKbCodes.value = []
  tab.value = 'basic'
  drawer.value = true
}

async function openEdit(item) {
  const seq = ++toolsSeq
  sourceAgentId.value = null
  Object.assign(form, {
    id: item.id,
    agentCode: item.agentCode,
    agentName: item.agentName,
    modelCode: item.modelCode,
    systemPrompt: item.systemPrompt || '',
    orchestrateMode: item.orchestrateMode ?? OrchestrateMode.CHAT,
    coverUrl: item.coverUrl || '',
    enabled: item.enabled,
    quotaQps: item.quotaQps ?? null,
    quotaConcurrency: item.quotaConcurrency ?? null,
    quotaDailyTokens: item.quotaDailyTokens ?? null,
    hitlTimeoutMinutes: item.hitlTimeoutMinutes ?? null
  })
  tab.value = 'basic'
  drawer.value = true
  boundKbCodes.value = (item.knowledgeBases || []).map((x) => x.baseCode)
  try {
    const res = await get(`/api/agents/${item.id}/tools`)
    if (seq !== toolsSeq) return
    boundToolIds.value = (res.data || []).map((x) => x.toolId)
  } catch (_) {
    if (seq !== toolsSeq) return
    boundToolIds.value = []
  }
}

async function openCopy(item) {
  const seq = ++toolsSeq
  error.value = ''
  sourceAgentId.value = item.id
  Object.assign(form, {
    id: null,
    agentCode: '',
    agentName: uniqueCopyName(item.agentName),
    modelCode: item.modelCode,
    systemPrompt: item.systemPrompt || '',
    orchestrateMode: item.orchestrateMode ?? OrchestrateMode.CHAT,
    coverUrl: item.coverUrl || '',
    enabled: EnabledStatus.DISABLED,
    quotaQps: item.quotaQps ?? null,
    quotaConcurrency: item.quotaConcurrency ?? null,
    quotaDailyTokens: item.quotaDailyTokens ?? null,
    hitlTimeoutMinutes: item.hitlTimeoutMinutes ?? null
  })
  boundKbCodes.value = (item.knowledgeBases || []).map((x) => x.baseCode)
  tab.value = 'basic'
  drawer.value = true
  try {
    const res = await get(`/api/agents/${item.id}/tools`)
    if (seq !== toolsSeq) return
    boundToolIds.value = (res.data || []).map((x) => x.toolId)
  } catch (_) {
    if (seq !== toolsSeq) return
    boundToolIds.value = []
  }
}

function closeDrawer() {
  drawer.value = false
  sourceAgentId.value = null
}

async function save() {
  saving.value = true
  error.value = ''
  try {
    const payload = {
      id: form.id,
      agentName: form.agentName,
      modelCode: form.modelCode,
      systemPrompt: form.systemPrompt,
      orchestrateMode: form.orchestrateMode,
      enabled: form.enabled,
      coverUrl: (form.coverUrl || '').trim(),
      quotaQps: form.quotaQps || 0,
      quotaConcurrency: form.quotaConcurrency || 0,
      quotaDailyTokens: form.quotaDailyTokens || 0,
      hitlTimeoutMinutes: form.hitlTimeoutMinutes || 0,
      knowledgeBaseCodes: boundKbCodes.value
    }
    if (form.id) {
      await put('/api/agents', payload)
      await put(`/api/agents/${form.id}/tools`, boundToolIds.value)
      await put(`/api/agents/${form.id}/knowledge-bases`, boundKbCodes.value)
    } else {
      if (sourceAgentId.value) {
        payload.sourceAgentId = sourceAgentId.value
        payload.enabled = EnabledStatus.DISABLED
      }
      const created = await post('/api/agents', payload)
      const newId = created?.data
      if (newId) {
        await put(`/api/agents/${newId}/tools`, boundToolIds.value)
        await put(`/api/agents/${newId}/knowledge-bases`, boundKbCodes.value)
      }
    }
    drawer.value = false
    sourceAgentId.value = null
    await load()
  } catch (e) {
    error.value = e.message
  } finally {
    saving.value = false
  }
}

async function toggle(item) {
  try {
    if (item.enabled === EnabledStatus.ENABLED) await put(`/api/agents/${item.id}/disable`)
    else await put(`/api/agents/${item.id}/enable`)
    await load()
  } catch (e) {
    error.value = e.message
  }
}

onMounted(load)
</script>
