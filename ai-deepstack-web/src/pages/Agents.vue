<template>
  <div>
    <header class="page-head">
      <div>
        <h1>智能体</h1>
        <p>创建和管理智能体，绑定模型、工具、知识库，或进入图编排。</p>
      </div>
      <div class="row-actions">
        <button class="btn-ghost" type="button" @click="showTemplates = !showTemplates">
          {{ showTemplates ? '返回智能体' : '模板库' }}
        </button>
        <button v-if="!showTemplates" class="btn" type="button" @click="openCreate">新建智能体</button>
      </div>
    </header>

    <p v-if="error" class="error">{{ error }}</p>

    <!-- Templates -->
    <div v-if="showTemplates">
      <div class="panel" style="margin-bottom: 1rem">
        <p class="muted" style="margin: 0">模板不可对话调用；可复制为新智能体后启用。</p>
      </div>
      <div v-if="templates.length" class="agent-card-grid">
        <article v-for="item in templates" :key="'t' + item.id" class="agent-card is-off">
          <div class="agent-card-body">
            <h3 class="agent-card-title">{{ item.agentName }}</h3>
            <code class="agent-card-code" :title="item.agentCode">编码 {{ item.agentCode }}</code>
            <p class="agent-card-meta muted">
              {{ item.orchestrateModeName || (item.orchestrateMode === OrchestrateMode.GRAPH ? '编排图' : '对话') }}
              · {{ item.modelCode || '未绑定模型' }}
            </p>
            <div class="agent-card-actions">
              <button class="btn-ghost" type="button" @click="copyFromTemplate(item)">复制为智能体</button>
            </div>
          </div>
        </article>
      </div>
      <div v-else class="panel">
        <p class="muted" style="margin: 0">暂无模板</p>
      </div>
    </div>

    <div v-else>
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
            <button class="btn-ghost" type="button" @click="openEdit(item)">配置</button>
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
      <p class="muted" style="margin: 0">暂无智能体</p>
    </div>
    </div>

    <div v-if="drawer" class="drawer-mask" @click.self="closeDrawer">
      <aside class="drawer" style="width: min(640px, 100vw)">
        <h2>{{ form.id ? '配置智能体' : '新建智能体' }}</h2>
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
          <div v-if="!form.id" class="muted">请先保存智能体再绑定工具</div>
          <div v-else class="panel-quiet" style="max-height: 360px; overflow: auto; padding: 0.75rem">
            <PropCheckboxGroup v-model="boundToolIds" :options="tools" empty-text="暂无可用工具" />
          </div>
        </template>

        <template v-else>
          <p class="muted" style="margin-top: 0">按 baseCode 绑定知识库</p>
          <div v-if="!form.id" class="muted">请先保存智能体再绑定知识库</div>
          <div v-else class="panel-quiet" style="max-height: 360px; overflow: auto; padding: 0.75rem">
            <PropCheckboxGroup v-model="boundKbCodes" :options="kbs" empty-text="暂无知识库" />
          </div>
        </template>

        <p v-if="error" class="error">{{ error }}</p>
        <div class="drawer-actions">
          <button class="btn" type="button" :disabled="saving" @click="save">{{ saving ? '保存中…' : '保存' }}</button>
          <button
            v-if="form.id"
            class="btn-ghost"
            type="button"
            :disabled="saving"
            @click="saveAsTemplate"
          >另存为模板</button>
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
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { get, pageList, post, put } from '../api/http'
import CoverImageUpload from '../components/CoverImageUpload.vue'
import PropCheckboxGroup from '../components/PropCheckboxGroup.vue'
import PropNumber from '../components/PropNumber.vue'
import PropSelect from '../components/PropSelect.vue'
import { EnabledStatus, ModelType, OrchestrateMode, orchestrateOptions } from '../constants/enums'

const DEFAULT_COVER = '/agent-cover-default.svg'

const list = ref([])
const templates = ref([])
const showTemplates = ref(false)
const models = ref([])
const tools = ref([])
const kbs = ref([])
const error = ref('')
const saving = ref(false)
const drawer = ref(false)
const tab = ref('basic')
const tabs = [
  { id: 'basic', label: '基础' },
  { id: 'tools', label: '工具' },
  { id: 'kb', label: '知识库' }
]
const boundToolIds = ref([])
const boundKbCodes = ref([])
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

function coverOf(item) {
  const url = (item?.coverUrl || '').trim()
  return url || DEFAULT_COVER
}

function onCoverError(e) {
  if (e?.target && e.target.src !== DEFAULT_COVER) {
    e.target.src = DEFAULT_COVER
  }
}

async function load() {
  error.value = ''
  try {
    const [a, m, t, k, tpl] = await Promise.all([
      get('/api/agents/page', { pageNum: 1, pageSize: 100 }),
      get('/api/models/options', { modelType: ModelType.CHAT }),
      get('/api/tools/options'),
      get('/api/kb/options'),
      get('/api/agents/templates', { pageNum: 1, pageSize: 100 })
    ])
    list.value = pageList(a)
    models.value = m.data || []
    tools.value = t.data || []
    kbs.value = k.data || []
    templates.value = pageList(tpl)
  } catch (e) {
    error.value = e.message
  }
}

function openCreate() {
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
    boundToolIds.value = (res.data || []).map((x) => x.toolId)
  } catch (_) {
    boundToolIds.value = []
  }
}

function closeDrawer() {
  drawer.value = false
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
      hitlTimeoutMinutes: form.hitlTimeoutMinutes || 0
    }
    if (form.id) {
      await put('/api/agents', payload)
    } else {
      const created = await post('/api/agents', payload)
      form.id = created?.data
    }

    if (form.id) {
      await put(`/api/agents/${form.id}/tools`, boundToolIds.value)
      await put(`/api/agents/${form.id}/knowledge-bases`, boundKbCodes.value)
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
    if (item.enabled === EnabledStatus.ENABLED) await put(`/api/agents/${item.id}/disable`)
    else await put(`/api/agents/${item.id}/enable`)
    await load()
  } catch (e) {
    error.value = e.message
  }
}

async function saveAsTemplate() {
  if (!form.id) return
  const agentName = window.prompt('模板名称', (form.agentName || '') + ' 模板')
  if (agentName == null) return
  saving.value = true
  error.value = ''
  try {
    await post(`/api/agents/${form.id}/save-as-template`, {
      agentName: agentName.trim() || undefined
    })
    drawer.value = false
    await load()
  } catch (e) {
    error.value = e.message
  } finally {
    saving.value = false
  }
}

async function copyFromTemplate(item) {
  const agentName = window.prompt('新智能体名称', (item.agentName || '').replace(/\s*模板$/, '') || '新智能体')
  if (agentName == null) return
  error.value = ''
  try {
    await post(`/api/agents/from-template/${item.id}`, {
      agentName: agentName.trim() || undefined
    })
    showTemplates.value = false
    await load()
  } catch (e) {
    error.value = e.message
  }
}

onMounted(load)
</script>
