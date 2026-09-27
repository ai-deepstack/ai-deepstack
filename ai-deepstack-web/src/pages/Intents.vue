<template>
  <div>
    <header class="page-head">
      <div>
        <h1>意图</h1>
        <p>意图分类列表，供流程图里的「意图分类」节点选用。</p>
      </div>
      <button class="btn" type="button" @click="openCreate">新建意图</button>
    </header>

    <p v-if="error" class="error">{{ error }}</p>
    <div class="panel">
      <div class="table-wrap">
        <table class="table" v-if="list.length">
          <thead>
            <tr>
              <th>编码</th>
              <th>名称</th>
              <th>排序</th>
              <th>状态</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in list" :key="item.id">
              <td><code>{{ item.intentCode }}</code></td>
              <td>{{ item.intentName }}</td>
              <td class="muted">{{ item.sortOrder }}</td>
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
        <p v-else class="muted">暂无意图</p>
      </div>
    </div>

    <div v-if="drawer" class="drawer-mask" @click.self="drawer = false">
      <aside class="drawer">
        <h2>{{ form.id ? '编辑意图' : '新建意图' }}</h2>
        <div class="field">
          <label>意图编码</label>
          <input v-model="form.intentCode" :disabled="!!form.id" placeholder="如 HEALTH" />
        </div>
        <div class="field">
          <label>意图名称</label>
          <input v-model="form.intentName" placeholder="显示名称" />
        </div>
        <div class="field">
          <label>排序</label>
          <PropNumber v-model="form.sortOrder" :min="0" />
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
import PropNumber from '../components/PropNumber.vue'
import { EnabledStatus } from '../constants/enums'

const list = ref([])
const error = ref('')
const saving = ref(false)
const drawer = ref(false)
const form = reactive({
  id: null,
  intentCode: '',
  intentName: '',
  description: '',
  sortOrder: 0,
  enabled: 1
})

async function load() {
  error.value = ''
  try {
    const res = await get('/api/intents/page', { pageNum: 1, pageSize: 200 })
    list.value = pageList(res)
  } catch (e) {
    error.value = e.message
  }
}

function openCreate() {
  Object.assign(form, {
    id: null,
    intentCode: '',
    intentName: '',
    description: '',
    sortOrder: 0,
    enabled: 1
  })
  drawer.value = true
}

function openEdit(item) {
  Object.assign(form, {
    id: item.id,
    intentCode: item.intentCode,
    intentName: item.intentName,
    description: item.description || '',
    sortOrder: item.sortOrder ?? 0,
    enabled: item.enabled
  })
  drawer.value = true
}

async function save() {
  saving.value = true
  error.value = ''
  try {
    if (form.id) await put('/api/intents', { ...form })
    else await post('/api/intents', { ...form })
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
    if (item.enabled === EnabledStatus.ENABLED) await put(`/api/intents/${item.id}/disable`)
    else await put(`/api/intents/${item.id}/enable`)
    await load()
  } catch (e) {
    error.value = e.message
  }
}

onMounted(load)
</script>
