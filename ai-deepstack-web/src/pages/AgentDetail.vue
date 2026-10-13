<template>
  <div>
    <header class="page-head">
      <div>
        <h1>{{ agent?.agentName || 'Agent 详情' }}</h1>
        <p v-if="agent">只读查看配置与绑定；改配置请回列表打开「配置」。</p>
        <p v-else-if="!error">加载中…</p>
      </div>
      <div class="row-actions" v-if="agent">
        <router-link class="btn-ghost" to="/agents">返回列表</router-link>
        <router-link class="btn-ghost" :to="`/agents?edit=${agent.id}`">配置</router-link>
        <router-link class="btn-ghost" :to="`/agents/${agent.id}/stats`">统计</router-link>
        <router-link
          v-if="agent.orchestrateMode === OrchestrateMode.GRAPH"
          class="btn-ghost"
          :to="`/agents/${agent.id}/graph`"
        >图编排</router-link>
        <router-link
          class="btn"
          :to="{ path: '/playground', query: { agent: agent.agentCode } }"
        >试跑</router-link>
      </div>
    </header>

    <p v-if="error" class="error">{{ error }}</p>

    <template v-if="agent">
      <section class="detail-hero panel">
        <div class="detail-cover">
          <img :src="coverOf(agent)" :alt="agent.agentName" @error="onCoverError" />
        </div>
        <div class="detail-hero-body">
          <div class="detail-badges">
            <span class="pill" :class="agent.orchestrateMode === OrchestrateMode.GRAPH ? 'pill-teal' : 'pill-brass'">
              {{ agent.orchestrateModeName || (agent.orchestrateMode === OrchestrateMode.GRAPH ? '编排图' : '对话') }}
            </span>
            <span class="pill" :class="agent.enabled === EnabledStatus.ENABLED ? 'pill-ok' : 'pill-off'">
              {{ agent.enabledName || (agent.enabled === EnabledStatus.ENABLED ? '启用' : '停用') }}
            </span>
            <span v-if="agent.traceModeName" class="pill pill-brass">{{ agent.traceModeName }}</span>
          </div>
          <code class="detail-code">{{ agent.agentCode }}</code>
          <p class="muted detail-sub">
            模型 {{ agent.modelName || agent.modelCode || '未绑定' }}
            <span v-if="agent.publishedVersion != null"> · 已发布 v{{ agent.publishedVersion }}</span>
            <span v-if="agent.graphVersion != null"> · 草稿 v{{ agent.graphVersion }}</span>
          </p>
        </div>
      </section>

      <div class="detail-grid">
        <section class="panel">
          <h2 class="section-title">基础</h2>
          <dl class="kv">
            <div><dt>名称</dt><dd>{{ agent.agentName }}</dd></div>
            <div><dt>编码</dt><dd><code>{{ agent.agentCode }}</code></dd></div>
            <div><dt>模型</dt><dd>{{ agent.modelName || agent.modelCode || '—' }}</dd></div>
            <div><dt>编排</dt><dd>{{ agent.orchestrateModeName || '—' }}</dd></div>
            <div><dt>状态</dt><dd>{{ agent.enabledName || '—' }}</dd></div>
            <div><dt>创建时间</dt><dd>{{ fmtTime(agent.createTime) }}</dd></div>
            <div><dt>更新时间</dt><dd>{{ fmtTime(agent.updateTime) }}</dd></div>
          </dl>
        </section>

        <section class="panel">
          <h2 class="section-title">配额与超时</h2>
          <dl class="kv">
            <div><dt>QPS</dt><dd>{{ limitText(agent.quotaQps) }}</dd></div>
            <div><dt>并发</dt><dd>{{ limitText(agent.quotaConcurrency) }}</dd></div>
            <div><dt>每日 token</dt><dd>{{ limitText(agent.quotaDailyTokens) }}</dd></div>
            <div><dt>人工确认超时</dt><dd>{{ agent.hitlTimeoutMinutes ? `${agent.hitlTimeoutMinutes} 分钟` : '用全局' }}</dd></div>
          </dl>
        </section>

        <section class="panel detail-span">
          <h2 class="section-title">系统提示词</h2>
          <pre class="prompt-block">{{ agent.systemPrompt || '（空）' }}</pre>
        </section>

        <section class="panel">
          <h2 class="section-title">工具（{{ tools.length }}）</h2>
          <ul v-if="tools.length" class="bind-list">
            <li v-for="t in tools" :key="t.toolId">
              <strong>{{ t.toolName || t.toolCode || t.toolId }}</strong>
              <code v-if="t.toolCode">{{ t.toolCode }}</code>
              <span class="muted">{{ t.enabledName || '' }}</span>
            </li>
          </ul>
          <p v-else class="muted" style="margin: 0">未绑定工具</p>
        </section>

        <section class="panel">
          <h2 class="section-title">知识库（{{ kbs.length }}）</h2>
          <ul v-if="kbs.length" class="bind-list">
            <li v-for="k in kbs" :key="k.baseCode">
              <strong>{{ k.baseName || k.baseCode }}</strong>
              <code>{{ k.baseCode }}</code>
            </li>
          </ul>
          <p v-else class="muted" style="margin: 0">未绑定知识库</p>
        </section>
      </div>
    </template>
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { get } from '../api/http'
import { EnabledStatus, OrchestrateMode } from '../constants/enums'
import { asId } from '../utils/ids'

const DEFAULT_COVER = '/agent-cover-default.svg'

const route = useRoute()
const agentId = computed(() => asId(route.params.id))
const agent = ref(null)
const tools = ref([])
const error = ref('')

const kbs = computed(() => agent.value?.knowledgeBases || [])

function coverOf(item) {
  const url = (item?.coverUrl || '').trim()
  return url || DEFAULT_COVER
}

function onCoverError(e) {
  if (e?.target && e.target.src !== DEFAULT_COVER) e.target.src = DEFAULT_COVER
}

function limitText(v) {
  if (v == null || v === 0) return '不限'
  return String(v)
}

function fmtTime(v) {
  if (!v) return '—'
  return String(v).replace('T', ' ').slice(0, 19)
}

async function load() {
  error.value = ''
  agent.value = null
  tools.value = []
  if (!agentId.value) {
    error.value = '缺少 Agent id'
    return
  }
  try {
    const [agentRes, toolsRes] = await Promise.all([
      get(`/api/agents/${agentId.value}`),
      get(`/api/agents/${agentId.value}/tools`)
    ])
    agent.value = agentRes.data
    tools.value = toolsRes.data || []
    if (!agent.value) error.value = 'Agent 不存在'
  } catch (e) {
    error.value = e.message
  }
}

watch(agentId, load)
onMounted(load)
</script>

<style scoped>
.detail-hero {
  display: grid;
  grid-template-columns: 11rem 1fr;
  gap: 1.1rem;
  align-items: center;
  margin-bottom: 1rem;
  padding: 1rem;
}
.detail-cover {
  width: 11rem;
  aspect-ratio: 16 / 10;
  border-radius: calc(var(--radius) - 2px);
  overflow: hidden;
  border: 1px solid var(--line-strong);
  background: #0a1220;
}
.detail-cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}
.detail-hero-body { min-width: 0; }
.detail-badges {
  display: flex;
  flex-wrap: wrap;
  gap: 0.4rem;
  margin-bottom: 0.55rem;
}
.detail-code {
  display: block;
  font-size: 0.9rem;
  color: var(--cyan-bright);
  margin-bottom: 0.35rem;
  word-break: break-all;
}
.detail-sub { margin: 0; font-size: 0.88rem; }

.detail-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0.85rem;
}
.detail-span { grid-column: 1 / -1; }
.section-title {
  font-size: 0.95rem;
  margin: 0 0 0.7rem;
}

.kv {
  margin: 0;
  display: grid;
  gap: 0.55rem;
}
.kv > div {
  display: grid;
  grid-template-columns: 7.5rem 1fr;
  gap: 0.5rem;
  align-items: baseline;
}
.kv dt {
  margin: 0;
  color: var(--muted);
  font-size: 0.8rem;
}
.kv dd {
  margin: 0;
  word-break: break-word;
}

.prompt-block {
  margin: 0;
  padding: 0.85rem 0.95rem;
  background: rgba(8, 14, 26, 0.55);
  border: 1px dashed var(--line-strong);
  border-radius: calc(var(--radius) - 2px);
  white-space: pre-wrap;
  word-break: break-word;
  font-size: 0.85rem;
  line-height: 1.55;
  max-height: 22rem;
  overflow: auto;
}

.bind-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  gap: 0.45rem;
}
.bind-list li {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: 0.45rem 0.7rem;
  padding: 0.45rem 0.55rem;
  background: rgba(8, 14, 26, 0.45);
  border-radius: 6px;
  border: 1px solid var(--line);
}
.bind-list code {
  font-size: 0.78rem;
  color: var(--cyan-bright);
}

@media (max-width: 780px) {
  .detail-hero { grid-template-columns: 1fr; }
  .detail-cover { width: 100%; max-width: 18rem; }
  .detail-grid { grid-template-columns: 1fr; }
  .kv > div { grid-template-columns: 1fr; gap: 0.15rem; }
}
</style>
