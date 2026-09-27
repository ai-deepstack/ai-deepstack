<template>
  <div>
    <header class="page-head">
      <div>
        <h1>告警</h1>
        <p>按错误率、耗时、人工待办积压和在跑数量触发。阈值在系统设置里改。</p>
      </div>
      <div class="row-actions">
        <button class="btn" type="button" @click="load()">刷新</button>
      </div>
    </header>

    <p v-if="error" class="error">{{ error }}</p>

    <div class="panel">
      <div class="table-wrap" v-if="list.length">
        <table class="table">
          <thead>
            <tr>
              <th>时间</th>
              <th>规则</th>
              <th>智能体</th>
              <th>级别</th>
              <th>标题</th>
              <th>详情</th>
              <th>状态</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in list" :key="item.id">
              <td class="muted">{{ formatTime(item.firedAt) }}</td>
              <td><code>{{ item.ruleCode }}</code></td>
              <td>
                <router-link
                  v-if="item.agentCode"
                  class="btn-ghost"
                  :to="{ path: '/runs', query: { agentCode: item.agentCode } }"
                  style="padding: 0.2rem 0.45rem"
                >{{ item.agentCode }}</router-link>
                <span v-else class="muted">全站</span>
              </td>
              <td>{{ item.severity || '—' }}</td>
              <td>{{ item.title }}</td>
              <td class="muted" style="max-width: 18rem; white-space: normal">{{ item.detail || '—' }}</td>
              <td>
                <span class="pill" :class="item.acknowledged ? 'pill-ok' : 'pill-brass'">
                  {{ item.acknowledged ? '已确认' : '未确认' }}
                </span>
              </td>
              <td>
                <button
                  v-if="!item.acknowledged"
                  class="btn-ghost"
                  type="button"
                  @click="ack(item)"
                >确认</button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <p v-else class="muted" style="margin: 0">暂无告警</p>
      <div v-if="totalPage > 1" class="row-actions" style="margin-top: 0.75rem">
        <button class="btn-ghost" type="button" :disabled="pageNum <= 1" @click="load(pageNum - 1)">上一页</button>
        <span class="muted">{{ pageNum }} / {{ totalPage }}</span>
        <button class="btn-ghost" type="button" :disabled="pageNum >= totalPage" @click="load(pageNum + 1)">下一页</button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { get, pageList, post } from '../api/http'

const list = ref([])
const pageNum = ref(1)
const totalPage = ref(0)
const error = ref('')

function formatTime(t) {
  if (!t) return '—'
  return String(t).replace('T', ' ').slice(0, 19)
}

async function load(page = 1) {
  const next = Number(page)
  pageNum.value = Number.isFinite(next) && next > 0 ? next : 1
  error.value = ''
  try {
    const res = await get('/api/agent-alerts', { pageNum: pageNum.value, pageSize: 20 })
    list.value = pageList(res)
    totalPage.value = res.data?.totalPage ?? 0
  } catch (e) {
    error.value = e.message
  }
}

async function ack(item) {
  error.value = ''
  try {
    await post(`/api/agent-alerts/${item.id}/ack`)
    await load(pageNum.value)
  } catch (e) {
    error.value = e.message
  }
}

onMounted(() => load(1))
</script>
