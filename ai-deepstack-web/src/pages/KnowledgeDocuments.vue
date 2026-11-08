<template>
  <div>
    <header class="page-head">
      <div>
        <h1>文档 {{ kb ? `· ${kb.baseName}` : '' }}</h1>
        <p>
          <template v-if="kb">
            <code>{{ kb.baseCode }}</code> · Embedding {{ kb.embeddingModelCode || '—' }} ·
            图谱{{ kb.enableGraph === 1 ? '开' : '关' }}
          </template>
          <template v-else>上传文档、重新向量化，查看分片与原文。</template>
        </p>
      </div>
      <div class="row-actions">
        <router-link class="btn-ghost" to="/knowledge">返回知识库</router-link>
        <router-link
          v-if="kb"
          class="btn-ghost"
          :to="`/knowledge/${encodeURIComponent(kb.baseCode)}/graph`"
        >知识图谱</router-link>
        <label class="btn" style="cursor: pointer">
          上传
          <input type="file" hidden @change="onUpload" />
        </label>
      </div>
    </header>

    <p v-if="error" class="error">{{ error }}</p>

    <div class="panel">
      <div class="table-wrap">
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
                  v-if="kb"
                  class="btn-ghost"
                  :to="`/knowledge/${encodeURIComponent(kb.baseCode)}/graph?documentId=${d.id}`"
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
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { get, getToken, pageList, put } from '../api/http'

/** DocGraphStatusEnum: WRITTEN=2, FAILED=3 */
const GRAPH_WRITTEN = 2
const GRAPH_FAILED = 3

const route = useRoute()
const kbId = computed(() => route.params.id)

const kb = ref(null)
const error = ref('')

const docs = ref([])
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

function canRegraph(d) {
  return d.graphStatus === GRAPH_WRITTEN || d.graphStatus === GRAPH_FAILED
}

async function loadKb() {
  if (!kbId.value) return
  try {
    const res = await get(`/api/kb/${kbId.value}`)
    kb.value = res?.data || null
  } catch (e) {
    error.value = e.message
    kb.value = null
  }
}

/** 文档列表分页 */
async function loadDocs(page = 1) {
  if (!kbId.value) return
  docsLoading.value = true
  try {
    const res = await get('/api/kb/docs/page', {
      pageNum: page,
      pageSize: docPageSize,
      knowledgeBaseId: kbId.value
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
  if (!file || !kbId.value) return
  const fd = new FormData()
  fd.append('file', file)
  fd.append('knowledgeBaseId', String(kbId.value))
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

watch(kbId, async () => {
  kb.value = null
  docs.value = []
  closeDoc()
  await loadKb()
  await loadDocs(1)
})

onMounted(async () => {
  await loadKb()
  await loadDocs(1)
})
</script>

<style scoped>
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
