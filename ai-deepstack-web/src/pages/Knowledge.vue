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
            <tr v-for="item in list" :key="item.id">
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
                <router-link class="btn-ghost" :to="`/knowledge/${item.id}/docs`">文档</router-link>
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
import { get, pageList, post, put } from '../api/http'
import PropCheckbox from '../components/PropCheckbox.vue'
import PropSelect from '../components/PropSelect.vue'
import { usePageList } from '../composables/usePageList'
import { EnabledStatus, ModelType } from '../constants/enums'

const embeddingModels = ref([])
const chatModels = ref([])
const saving = ref(false)
const drawer = ref(false)

// 库列表分页与过期响应丢弃交给 usePageList；error 由它持有
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

onMounted(load)
</script>
