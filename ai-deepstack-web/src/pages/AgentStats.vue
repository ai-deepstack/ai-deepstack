<template>
  <div>
    <header class="page-head">
      <div>
        <h1>{{ agent?.agentName || '智能体统计' }}</h1>
        <p>
          <code v-if="agent">{{ agent.agentCode }}</code>
          只统计该智能体的运行，数据来自运行记录。
        </p>
      </div>
      <div class="row-actions">
        <router-link class="btn-ghost" to="/agents">返回列表</router-link>
        <PropSelect
          v-model="hours"
          class="hours-select"
          :options="hoursOptions"
          placeholder="时间窗"
          @change="load"
        />
        <button class="btn" type="button" @click="load()">刷新</button>
      </div>
    </header>

    <p v-if="error" class="error">{{ error }}</p>
    <p v-else-if="!overview" class="muted">加载中…</p>

    <div v-if="overview" class="kpi-grid">
      <router-link v-for="k in kpiCards" :key="k.label" class="panel kpi-card" :to="k.to">
        <div class="kpi-label">{{ k.label }}</div>
        <div class="kpi-value">{{ k.value }}</div>
      </router-link>
    </div>

    <div v-if="overview" class="panel">
      <h2 class="section-title">按天趋势</h2>
      <div v-if="dailyTrend.length" class="table-wrap">
        <table class="table">
          <thead>
            <tr><th>日期</th><th>调用</th><th>失败</th></tr>
          </thead>
          <tbody>
            <tr v-for="row in dailyTrend" :key="row.day">
              <td>{{ row.day }}</td>
              <td>{{ row.total }}</td>
              <td>{{ row.failed }}</td>
            </tr>
          </tbody>
        </table>
      </div>
      <p v-else class="muted" style="margin: 0">该时间窗内没有运行</p>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { get } from '../api/http'
import PropSelect from '../components/PropSelect.vue'
import { GraphRunStatus } from '../constants/enums'
import { asId } from '../utils/ids'

const route = useRoute()
const agentId = computed(() => asId(route.params.id))
const hours = ref(24)
const hoursOptions = [
  { value: 24, label: '近 24 小时' },
  { value: 168, label: '近 7 天' },
  { value: 720, label: '近 30 天' }
]
const agent = ref(null)
const overview = ref(null)
const error = ref('')

const runsQuery = computed(() => ({ agentId: String(agentId.value) }))

const kpiCards = computed(() => {
  const k = overview.value?.kpi || {}
  const rate = k.successRate != null ? `${(k.successRate * 100).toFixed(1)}%` : '—'
  const base = { path: '/runs', query: runsQuery.value }
  return [
    { label: '调用次数', value: k.total ?? 0, to: base },
    { label: '成功率', value: rate, to: { path: '/runs', query: { ...runsQuery.value, status: GraphRunStatus.SUCCESS } } },
    { label: '失败', value: k.failed ?? 0, to: { path: '/runs', query: { ...runsQuery.value, status: GraphRunStatus.FAILED } } },
    { label: '等待人工', value: k.waitingHuman ?? 0, to: { path: '/runs', query: { ...runsQuery.value, status: GraphRunStatus.WAITING_HUMAN } } },
    { label: 'Token 合计', value: k.totalTokens ?? 0, to: base },
    { label: '中位耗时', value: `${k.p50DurationMs ?? 0} ms`, to: base },
    { label: 'P95 耗时', value: `${k.p95DurationMs ?? 0} ms`, to: base }
  ]
})

const dailyTrend = computed(() => {
  const buckets = new Map()
  for (const row of overview.value?.trend || []) {
    const day = String(row.hour || '').slice(0, 10)
    if (!day) continue
    const cur = buckets.get(day) || { day, total: 0, failed: 0 }
    cur.total += Number(row.total) || 0
    cur.failed += Number(row.failed) || 0
    buckets.set(day, cur)
  }
  return [...buckets.values()].sort((a, b) => a.day.localeCompare(b.day))
})

async function load() {
  error.value = ''
  try {
    const [agentRes, statsRes] = await Promise.all([
      get(`/api/agents/${agentId.value}`),
      get('/api/agent-runs/overview', { hours: hours.value, agentId: agentId.value })
    ])
    agent.value = agentRes.data
    overview.value = statsRes.data
    if (!agent.value) error.value = '智能体不存在'
  } catch (e) {
    error.value = e.message
  }
}

watch(agentId, load)
onMounted(load)
</script>

<style scoped>
.kpi-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(9.5rem, 1fr));
  gap: 0.75rem;
  margin-bottom: 1rem;
}
.kpi-card {
  padding: 0.9rem 1rem;
  text-decoration: none;
  color: inherit;
}
.kpi-card:hover { outline: 1px solid var(--cyan-bright); }
.kpi-label { font-size: 0.75rem; color: var(--muted, #8a93a6); }
.kpi-value { font-size: 1.25rem; font-weight: 600; margin-top: 0.25rem; color: var(--cyan-bright); }
.section-title { font-size: 0.95rem; margin: 0 0 0.6rem; }
.hours-select {
  width: 8.5rem;
}
</style>
