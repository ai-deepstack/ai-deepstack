<template>
  <div>
    <header class="page-head">
      <div>
        <h1>知识库</h1>
        <p>上传文档、重新向量化，需要时可以写图谱。</p>
      </div>
      <button class="btn" type="button" @click="openCreate">新建知识库</button>
    </header>

    <p v-if="error" class="error">{{ error }}</p>

    <div class="grid-2">
      <div class="panel">
        <h3 style="margin: 0 0 0.8rem; font-family: var(--font-brand); font-weight: 400; font-size: 1.45rem">库列表</h3>
        <div class="table-wrap">
          <table class="table" v-if="list.length">
            <thead>
              <tr>
                <th>编码</th>
                <th>名称</th>
                <th>Embedding</th>
                <th>图谱</th>
                <th>状态</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              <tr
                v-for="item in list"
                :key="item.id"
                :style="selected?.id === item.id ? 'background: var(--brass-soft)' : ''"
              >
                <td><code>{{ item.baseCode }}</code></td>
                <td>{{ item.baseName }}</td>
                <td class="muted">{{ item.embeddingModelName || item.embeddingModelCode || '—' }}</td>
                <td>
                  <span class="pill" :class="item.enableGraph === 1 ? 'pill-ok' : 'pill-off'">
                    {{ item.enableGraph === 1 ? '开' : '关' }}
                  </span>
                </td>
                <td>
                  <span class="pill" :class="item.enabled === EnabledStatus.ENABLED ? 'pill-ok' : 'pill-off'">
                    {{ item.enabledName || (item.enabled === EnabledStatus.ENABLED ? '启用' : '停用') }}
                  </span>
                </td>
                <td class="row-actions">
                  <button class="btn-ghost" type="button" @click="select(item)">文档</button>
                  <router-link
                    class="btn-ghost"
                    :to="`/knowledge/${encodeURIComponent(item.baseCode)}/graph`"
                  >知识图谱</router-link>
                  <button class="btn-ghost" type="button" @click="toggle(item)">
                    {{ item.enabled === EnabledStatus.ENABLED ? '停用' : '启用' }}
                  </button>
                </td>
              </tr>
            </tbody>
          </table>
          <p v-else class="muted">暂无知识库</p>
        </div>
      </div>

      <div class="panel">
        <div class="page-head" style="margin-bottom: 0.8rem">
          <div>
            <h3 style="margin: 0; font-family: var(--font-brand); font-weight: 400; font-size: 1.45rem">
              文档 {{ selected ? `· ${selected.baseName}` : '' }}
            </h3>
          </div>
          <label v-if="selected" class="btn" style="cursor: pointer">
            上传
            <input type="file" hidden @change="onUpload" />
          </label>
        </div>
        <p v-if="!selected" class="muted">选择左侧知识库查看文档</p>
        <div v-else class="table-wrap">
          <table class="table" v-if="docs.length">
            <thead>
              <tr>
                <th>标题</th>
                <th>嵌入</th>
                <th>写图</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="d in docs" :key="d.id">
                <td>{{ d.title || d.docName || d.fileName || d.id }}</td>
                <td>{{ d.embedStatusName || d.parseStatusName || d.status || d.embedStatus || '—' }}</td>
                <td>
                  <span :title="d.graphError || ''">{{ d.graphStatusName || '—' }}</span>
                </td>
                <td class="row-actions">
                  <button class="btn-ghost" type="button" @click="reembed(d)">重新嵌入</button>
                  <button
                    v-if="canRegraph(d)"
                    class="btn-ghost"
                    type="button"
                    @click="regraph(d)"
                  >重写图</button>
                  <router-link
                    v-if="selected"
                    class="btn-ghost"
                    :to="`/knowledge/${encodeURIComponent(selected.baseCode)}/graph?documentId=${d.id}`"
                  >看图</router-link>
                </td>
              </tr>
            </tbody>
          </table>
          <p v-else class="muted">暂无文档</p>
        </div>
      </div>
    </div>

    <div v-if="drawer" class="drawer-mask" @click.self="drawer = false">
      <aside class="drawer">
        <h2>{{ form.id ? '编辑知识库' : '新建知识库' }}</h2>
        <div class="field">
          <label>编码 baseCode</label>
          <input v-model="form.baseCode" :disabled="!!form.id" />
        </div>
        <div class="field">
          <label>名称</label>
          <input v-model="form.baseName" />
        </div>
        <div class="field">
          <label>Embedding 模型</label>
          <PropSelect
            v-model="form.embeddingModelCode"
            :options="embeddingOptions"
            placeholder="选择 Embedding 模型"
          />
        </div>
        <div class="field">
          <PropCheckbox
            v-model="form.enableGraph"
            :true-value="1"
            :false-value="0"
            label="启用知识图谱"
          />
        </div>
        <div class="field">
          <label>图谱抽取 CHAT 模型</label>
          <PropSelect
            v-model="form.graphModelCode"
            :options="chatOptions"
            placeholder="可选，空则用系统默认"
          />
        </div>
        <div class="field">
          <label>描述</label>
          <textarea v-model="form.description" rows="3" />
        </div>
        <div class="drawer-actions">
          <button class="btn" type="button" :disabled="saving" @click="save">保存</button>
          <button class="btn-ghost" type="button" @click="drawer = false">取消</button>
        </div>
      </aside>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { get, getToken, pageList, post, put } from '../api/http'
import PropCheckbox from '../components/PropCheckbox.vue'
import PropSelect from '../components/PropSelect.vue'
import { EnabledStatus, ModelType } from '../constants/enums'

/** DocGraphStatusEnum: WRITTEN=2, FAILED=3 */
const GRAPH_WRITTEN = 2
const GRAPH_FAILED = 3

const list = ref([])
const embeddingModels = ref([])
const chatModels = ref([])
const docs = ref([])
const selected = ref(null)
const error = ref('')
const saving = ref(false)
const drawer = ref(false)
const form = reactive({
  id: null,
  baseCode: '',
  baseName: '',
  embeddingModelCode: null,
  graphModelCode: null,
  enableGraph: 0,
  description: '',
  enabled: EnabledStatus.ENABLED
})

const embeddingOptions = computed(() => embeddingModels.value)
const chatOptions = computed(() => chatModels.value)

function canRegraph(d) {
  return d.graphStatus === GRAPH_WRITTEN || d.graphStatus === GRAPH_FAILED
}

async function load() {
  error.value = ''
  try {
    const [k, emb, chat] = await Promise.all([
      get('/api/kb/page', { pageNum: 1, pageSize: 100 }),
      get('/api/models/options', { modelType: ModelType.EMBEDDING }),
      get('/api/models/options', { modelType: ModelType.CHAT })
    ])
    list.value = pageList(k)
    embeddingModels.value = emb.data || []
    chatModels.value = chat.data || []
  } catch (e) {
    error.value = e.message
  }
}

async function select(item) {
  selected.value = item
  try {
    const res = await get('/api/kb/docs/page', {
      pageNum: 1,
      pageSize: 100,
      knowledgeBaseId: item.id
    })
    docs.value = pageList(res)
  } catch (e) {
    error.value = e.message
    docs.value = []
  }
}

function openCreate() {
  Object.assign(form, {
    id: null,
    baseCode: '',
    baseName: '',
    embeddingModelCode: embeddingModels.value[0]?.value ?? null,
    graphModelCode: null,
    enableGraph: 0,
    description: '',
    enabled: EnabledStatus.ENABLED
  })
  drawer.value = true
}

async function save() {
  saving.value = true
  error.value = ''
  try {
    const payload = {
      baseCode: form.baseCode,
      baseName: form.baseName,
      embeddingModelCode: form.embeddingModelCode,
      graphModelCode: form.graphModelCode || null,
      enableGraph: form.enableGraph,
      description: form.description,
      enabled: form.enabled
    }
    if (form.id) {
      await put('/api/kb', { id: form.id, ...payload })
    } else {
      await post('/api/kb', payload)
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
    if (item.enabled === EnabledStatus.ENABLED) await put(`/api/kb/${item.id}/disable`)
    else await put(`/api/kb/${item.id}/enable`)
    await load()
  } catch (e) {
    error.value = e.message
  }
}

async function reembed(d) {
  try {
    await put(`/api/kb/docs/${d.id}/reembed`)
    await select(selected.value)
  } catch (e) {
    error.value = e.message
  }
}

async function regraph(d) {
  try {
    await put(`/api/kb/docs/${d.id}/regraph`)
    await select(selected.value)
  } catch (e) {
    error.value = e.message
  }
}

async function onUpload(e) {
  const file = e.target.files?.[0]
  e.target.value = ''
  if (!file || !selected.value) return
  const fd = new FormData()
  fd.append('file', file)
  fd.append('knowledgeBaseId', String(selected.value.id))
  fd.append('title', file.name)
  try {
    const res = await fetch('/api/kb/docs/upload', {
      method: 'POST',
      headers: { Authorization: `Bearer ${getToken()}` },
      body: fd
    })
    const body = await res.json()
    if (!res.ok || body.code !== 200) throw new Error(body.message || '上传失败')
    await select(selected.value)
  } catch (err) {
    error.value = err.message
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
</style>
