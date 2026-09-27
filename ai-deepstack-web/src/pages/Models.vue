<template>
  <div>
    <header class="page-head">
      <div>
        <h1>模型</h1>
        <p>配置对话模型和向量模型，可单独更换 API Key、启用或停用。</p>
      </div>
      <button class="btn" type="button" @click="openCreate">新建模型</button>
    </header>

    <p v-if="error" class="error">{{ error }}</p>
    <div class="panel">
      <div class="table-wrap">
        <table class="table" v-if="list.length">
          <thead>
            <tr>
              <th>编码</th>
              <th>名称</th>
              <th>类型</th>
              <th>可见性</th>
              <th>所有者</th>
              <th>供应商</th>
              <th>状态</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in list" :key="item.id">
              <td><code>{{ item.modelCode }}</code></td>
              <td>{{ item.modelName }}</td>
              <td><span class="pill">{{ item.modelTypeName || item.modelType }}</span></td>
              <td>
                <span class="pill" :class="item.visibility === ModelVisibility.PRIVATE ? 'pill-brass' : 'pill-teal'">
                  {{ item.visibilityName || (item.visibility === ModelVisibility.PRIVATE ? '私有' : '公共') }}
                </span>
              </td>
              <td class="muted">{{ item.ownerId ?? '—' }}</td>
              <td>{{ item.provider }}</td>
              <td>
                <span class="pill" :class="item.enabled === EnabledStatus.ENABLED ? 'pill-ok' : 'pill-off'">
                  {{ item.enabledName || (item.enabled === EnabledStatus.ENABLED ? '启用' : '停用') }}
                </span>
              </td>
              <td class="row-actions">
                <button class="btn-ghost" type="button" @click="openEdit(item)">编辑</button>
                <button class="btn-ghost" type="button" @click="openKey(item)">API Key</button>
                <button class="btn-ghost" type="button" @click="toggle(item)">
                  {{ item.enabled === EnabledStatus.ENABLED ? '停用' : '启用' }}
                </button>
              </td>
            </tr>
          </tbody>
        </table>
        <p v-else class="muted">暂无模型</p>
      </div>
    </div>

    <div v-if="drawer" class="drawer-mask" @click.self="drawer = null">
      <aside class="drawer">
        <h2>{{ drawer === 'key' ? '更新 API Key' : form.id ? '编辑模型' : '新建模型' }}</h2>
        <p class="muted" style="margin-top: 0">{{ form.modelCode || '填写配置后保存' }}</p>

        <template v-if="drawer === 'key'">
          <div class="field">
            <label>新 API Key</label>
            <input v-model="apiKey" type="password" placeholder="sk-…" autocomplete="off" />
          </div>
        </template>
        <template v-else>
          <div class="grid-2">
            <div class="field">
              <label>模型编码</label>
              <input v-model="form.modelCode" :disabled="!!form.id" />
            </div>
            <div class="field">
              <label>模型名称</label>
              <input v-model="form.modelName" />
            </div>
            <div class="field">
              <label>类型</label>
              <PropSelect
                v-model="form.modelType"
                :options="modelTypeOptions"
                placeholder="选择类型"
              />
            </div>
            <div class="field">
              <label>供应商</label>
              <input v-model="form.provider" />
            </div>
            <div class="field">
              <label>Base URL</label>
              <input v-model="form.baseUrl" />
            </div>
            <div class="field">
              <label>API Model Name</label>
              <input v-model="form.apiModelName" />
            </div>
            <div class="field">
              <label>可见性</label>
              <PropSelect
                v-model="form.visibility"
                :options="visibilitySelectOptions"
                placeholder="选择可见性"
              />
              <p v-if="!isAdmin" class="muted" style="margin: 0.35rem 0 0; font-size: 0.78rem">
                非管理员仅可创建/保留私有模型
              </p>
            </div>
            <div class="field" v-if="!form.id">
              <label>API Key</label>
              <input v-model="form.apiKey" type="password" />
            </div>
          </div>
        </template>

        <p v-if="error" class="error">{{ error }}</p>
        <div class="drawer-actions">
          <button class="btn" type="button" :disabled="saving" @click="save">{{ saving ? '保存中…' : '保存' }}</button>
          <button class="btn-ghost" type="button" @click="drawer = null">取消</button>
        </div>
      </aside>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { get, getIsAdmin, pageList, post, put } from '../api/http'
import PropSelect from '../components/PropSelect.vue'
import {
  EnabledStatus,
  ModelType,
  ModelVisibility,
  modelTypeOptions,
  modelVisibilityOptions
} from '../constants/enums'

const list = ref([])
const error = ref('')
const saving = ref(false)
const drawer = ref(null)
const apiKey = ref('')
const isAdmin = getIsAdmin()

const visibilitySelectOptions = computed(() =>
  isAdmin
    ? modelVisibilityOptions
    : modelVisibilityOptions.filter((o) => o.value === ModelVisibility.PRIVATE)
)

const form = reactive({
  id: null,
  modelCode: '',
  modelName: '',
  modelType: ModelType.CHAT,
  provider: 'openai',
  baseUrl: 'https://api.openai.com',
  apiKey: '',
  apiModelName: 'gpt-4o-mini',
  visibility: ModelVisibility.PRIVATE,
  enabled: 1
})

async function load() {
  error.value = ''
  try {
    const res = await get('/api/models/page', { pageNum: 1, pageSize: 100 })
    list.value = pageList(res)
  } catch (e) {
    error.value = e.message
  }
}

function openCreate() {
  Object.assign(form, {
    id: null,
    modelCode: '',
    modelName: '',
    modelType: ModelType.CHAT,
    provider: 'openai',
    baseUrl: 'https://api.openai.com',
    apiKey: '',
    apiModelName: 'gpt-4o-mini',
    visibility: isAdmin ? ModelVisibility.PUBLIC : ModelVisibility.PRIVATE,
    enabled: 1
  })
  drawer.value = 'edit'
}

function openEdit(item) {
  Object.assign(form, {
    id: item.id,
    modelCode: item.modelCode,
    modelName: item.modelName,
    modelType: item.modelType,
    provider: item.provider,
    baseUrl: item.baseUrl,
    apiKey: '',
    apiModelName: item.apiModelName,
    visibility: item.visibility ?? ModelVisibility.PUBLIC,
    enabled: item.enabled
  })
  drawer.value = 'edit'
}

function openKey(item) {
  form.id = item.id
  form.modelCode = item.modelCode
  apiKey.value = ''
  drawer.value = 'key'
}

async function save() {
  saving.value = true
  error.value = ''
  try {
    if (drawer.value === 'key') {
      await put(`/api/models/${form.id}/apiKey`, { apiKey: apiKey.value })
    } else if (form.id) {
      await put('/api/models', { ...form })
    } else {
      await post('/api/models', { ...form })
    }
    drawer.value = null
    await load()
  } catch (e) {
    error.value = e.message
  } finally {
    saving.value = false
  }
}

async function toggle(item) {
  try {
    if (item.enabled === EnabledStatus.ENABLED) await put(`/api/models/${item.id}/disable`)
    else await put(`/api/models/${item.id}/enable`)
    await load()
  } catch (e) {
    error.value = e.message
  }
}

onMounted(load)
</script>
