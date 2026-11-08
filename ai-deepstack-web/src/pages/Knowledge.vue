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
                <td data-label="编码"><code>{{ item.baseCode }}</code></td>
                <td data-label="名称">{{ item.baseName }}</td>
                <td class="muted" data-label="Embedding">{{ item.embeddingModelName || item.embeddingModelCode || '—' }}</td>
                <td data-label="图谱">
                  <span class="pill" :class="item.enableGraph === 1 ? 'pill-ok' : 'pill-off'">
                    {{ item.enableGraph === 1 ? '开' : '关' }}
                  </span>
                </td>
                <td data-label="状态">
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
                <th>分片</th>
                <th>嵌入</th>
                <th>写图</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="d in docs" :key="d.id">
                <td data-label="标题">{{ d.title || d.docName || d.fileName || d.id }}</td>
                <td data-label="分片">{{ d.chunkCount ?? '—' }}</td>
                <td data-label="嵌入">{{ d.embedStatusName || d.parseStatusName || d.status || d.embedStatus || '—' }}</td>
                <td data-label="写图">
                  <span :title="d.graphError || ''">{{ d.graphStatusName || '—' }}</span>
                </td>
                <td class="row-actions">
                  <button class="btn-ghost" type="button" @click="openDoc(d)">查看</button>
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
          <p v-else class="muted">{{ docsLoading ? '加载中…' : '暂无文档' }}</p>
          <div class="list-pager" v-if="docTotal > 0">
            <span class="muted">第 {{ docPageNum }} / {{ Math.max(docTotalPage, 1) }} 页 · 共 {{ docTotal }} 篇</span>
            <div class="row-actions" v-if="docTotalPage > 1">
              <button class="btn-ghost" type="button" :disabled="docPageNum <= 1 || docsLoading" @click="loadDocs(docPageNum - 1)">上一页</button>
              <button class="btn-ghost" type="button" :disabled="docPageNum >= docTotalPage || docsLoading" @click="loadDocs(docPageNum + 1)">下一页</button>
            </div>
          </div>
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

    <div v-if="docViewer" class="modal-mask" @click.self="closeDoc">
      <div class="modal-card modal-wide doc-viewer">
        <div class="doc-viewer-head">
          <div>
            <h3 style="margin: 0">{{ docViewer.title || docViewer.fileName || docViewer.id }}</h3>
            <p class="muted" style="margin: 0.3rem 0 0; font-size: 0.8rem">
              {{ docViewer.chunkCount ?? 0 }} 个分片 · {{ docViewer.embedStatusName || '—' }}
            </p>
          </div>
          <button class="btn-ghost" type="button" @click="closeDoc">关闭</button>
        </div>

        <div class="row-actions doc-viewer-tabs">
          <button
            class="btn-ghost"
            type="button"
            :style="viewerTab === 'chunks' ? 'background: var(--cyan-soft); color: var(--cyan-bright)' : ''"
            @click="switchViewerTab('chunks')"
          >分片</button>
          <button
            class="btn-ghost"
            type="button"
            :style="viewerTab === 'raw' ? 'background: var(--cyan-soft); color: var(--cyan-bright)' : ''"
            @click="switchViewerTab('raw')"
          >原文</button>
        </div>

        <template v-if="viewerTab === 'chunks'">
          <div class="doc-viewer-search">
            <input
              v-model="chunkKeyword"
              placeholder="按内容搜索分片"
              aria-label="按内容搜索分片"
              @keyup.enter="loadChunks(1)"
            />
            <button class="btn-ghost" type="button" :disabled="chunksLoading" @click="loadChunks(1)">搜索</button>
          </div>
          <p v-if="chunksLoading" class="muted">加载中…</p>
          <div v-else-if="chunks.length" class="chunk-list">
            <article v-for="c in chunks" :key="c.id" class="chunk-card">
              <div class="chunk-meta muted">
                <span>#{{ c.chunkIndex }}</span>
                <span>{{ c.tokenCount ?? '—' }} tokens</span>
                <span>{{ c.hasEmbeddingName || '—' }}</span>
              </div>
              <pre class="chunk-content">{{ c.content }}</pre>
            </article>
          </div>
          <p v-else class="muted">{{ chunkKeyword ? '没有匹配的分片' : '暂无分片' }}</p>
          <div class="list-pager" v-if="chunkTotal > 0">
            <span class="muted">第 {{ chunkPageNum }} / {{ Math.max(chunkTotalPage, 1) }} 页 · 共 {{ chunkTotal }} 条</span>
            <div class="row-actions" v-if="chunkTotalPage > 1">
              <button class="btn-ghost" type="button" :disabled="chunkPageNum <= 1 || chunksLoading" @click="loadChunks(chunkPageNum - 1)">上一页</button>
              <button class="btn-ghost" type="button" :disabled="chunkPageNum >= chunkTotalPage || chunksLoading" @click="loadChunks(chunkPageNum + 1)">下一页</button>
            </div>
          </div>
        </template>

        <template v-else>
          <p v-if="rawLoading" class="muted">加载中…</p>
          <pre v-else class="doc-raw">{{ rawContent || '（文档没有可展示的原文）' }}</pre>
        </template>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { get, getToken, pageList, post, put } from '../api/http'
import PropCheckbox from '../components/PropCheckbox.vue'
import PropSelect from '../components/PropSelect.vue'
import { usePageList } from '../composables/usePageList'
import { EnabledStatus, ModelType } from '../constants/enums'

/** DocGraphStatusEnum: WRITTEN=2, FAILED=3 */
const GRAPH_WRITTEN = 2
const GRAPH_FAILED = 3

const embeddingModels = ref([])
const chatModels = ref([])
const docs = ref([])
const selected = ref(null)
const saving = ref(false)
const drawer = ref(false)

const docsLoading = ref(false)
const docPageNum = ref(1)
const docPageSize = 20
const docTotal = ref(0)
const docTotalPage = ref(0)

const docViewer = ref(null)
const viewerTab = ref('chunks')
const chunks = ref([])
const chunksLoading = ref(false)
const chunkPageNum = ref(1)
const chunkPageSize = 20
const chunkTotal = ref(0)
const chunkTotalPage = ref(0)
const chunkKeyword = ref('')
const rawContent = ref('')
const rawLoading = ref(false)
const rawLoaded = ref(false)

// 库列表分页与过期响应丢弃交给 usePageList；error 由它持有，供文档操作共用
const { list, error, load } = usePageList({
  pageSize: 100,
  fetcher: ({ pageNum, pageSize }) =>
    Promise.all([
      get('/api/kb/page', { pageNum, pageSize }),
      get('/api/models/options', { modelType: ModelType.EMBEDDING }),
      get('/api/models/options', { modelType: ModelType.CHAT })
    ]),
  onData: ([k, emb, chat]) => {
    list.value = pageList(k)
    embeddingModels.value = emb.data || []
    chatModels.value = chat.data || []
  }
})
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

async function select(item) {
  selected.value = item
  docs.value = []
  docTotal.value = 0
  docTotalPage.value = 0
  closeDoc()
  await loadDocs(1)
}

/** 文档列表分页 */
async function loadDocs(page = 1) {
  if (!selected.value) return
  docsLoading.value = true
  try {
    const res = await get('/api/kb/docs/page', {
      pageNum: page,
      pageSize: docPageSize,
      knowledgeBaseId: selected.value.id
    })
    docs.value = pageList(res)
    docPageNum.value = page
    docTotal.value = res?.data?.total ?? 0
    docTotalPage.value = res?.data?.totalPage ?? 0
  } catch (e) {
    error.value = e.message
    docs.value = []
  } finally {
    docsLoading.value = false
  }
}

/** 打开文档详情：默认看分片 */
async function openDoc(d) {
  docViewer.value = d
  viewerTab.value = 'chunks'
  chunkKeyword.value = ''
  chunks.value = []
  chunkTotal.value = 0
  chunkTotalPage.value = 0
  rawContent.value = ''
  rawLoaded.value = false
  await loadChunks(1)
}

function closeDoc() {
  docViewer.value = null
}

/** 分片分页 + 内容搜索 */
async function loadChunks(page = 1) {
  if (!docViewer.value) return
  chunksLoading.value = true
  try {
    const res = await get('/api/kb/chunks/page', {
      documentId: docViewer.value.id,
      pageNum: page,
      pageSize: chunkPageSize,
      keyword: chunkKeyword.value || undefined
    })
    chunks.value = pageList(res)
    chunkPageNum.value = page
    chunkTotal.value = res?.data?.total ?? 0
    chunkTotalPage.value = res?.data?.totalPage ?? 0
  } catch (e) {
    error.value = e.message
    chunks.value = []
  } finally {
    chunksLoading.value = false
  }
}

async function switchViewerTab(tab) {
  viewerTab.value = tab
  if (tab === 'raw' && !rawLoaded.value) await loadRaw()
}

/** 原文按需加载（详情接口才带 rawContent） */
async function loadRaw() {
  if (!docViewer.value) return
  rawLoading.value = true
  try {
    const res = await get(`/api/kb/docs/${docViewer.value.id}`)
    rawContent.value = res?.data?.rawContent || ''
    rawLoaded.value = true
  } catch (e) {
    error.value = e.message
  } finally {
    rawLoading.value = false
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
    await loadDocs(docPageNum.value)
  } catch (e) {
    error.value = e.message
  }
}

async function regraph(d) {
  try {
    await put(`/api/kb/docs/${d.id}/regraph`)
    await loadDocs(docPageNum.value)
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
    await loadDocs(1)
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
.doc-viewer {
  display: flex;
  flex-direction: column;
  gap: 0.85rem;
}
.doc-viewer-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 1rem;
}
.doc-viewer-tabs {
  gap: 0.5rem;
}
.doc-viewer-search {
  display: flex;
  gap: 0.5rem;
}
.doc-viewer-search input {
  flex: 1;
  min-width: 0;
  padding: 0.55rem 0.7rem;
  border-radius: 8px;
  border: 1px solid var(--line-strong);
  background: rgba(8, 14, 26, 0.6);
  color: var(--ink);
}
.chunk-list {
  display: flex;
  flex-direction: column;
  gap: 0.7rem;
  max-height: 52vh;
  overflow: auto;
}
.chunk-card {
  border: 1px solid var(--line-strong);
  border-radius: 10px;
  padding: 0.7rem 0.8rem;
  background: rgba(8, 14, 26, 0.45);
}
.chunk-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 0.9rem;
  font-size: 0.75rem;
  margin-bottom: 0.45rem;
}
.chunk-content,
.doc-raw {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-word;
  font-family: var(--font-mono);
  font-size: 0.82rem;
  line-height: 1.55;
}
.doc-raw {
  max-height: 58vh;
  overflow: auto;
  border: 1px solid var(--line-strong);
  border-radius: 10px;
  padding: 0.8rem;
  background: rgba(8, 14, 26, 0.45);
}
</style>
