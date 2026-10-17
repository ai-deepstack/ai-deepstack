import { createRouter, createWebHistory } from 'vue-router'
import { getToken } from '../api/http'
import Login from '../pages/Login.vue'
import Agents from '../pages/Agents.vue'
import Models from '../pages/Models.vue'
import Tools from '../pages/Tools.vue'
import McpConnections from '../pages/McpConnections.vue'
import Knowledge from '../pages/Knowledge.vue'
import KnowledgeGraph from '../pages/KnowledgeGraph.vue'
import Intents from '../pages/Intents.vue'
import Playground from '../pages/Playground.vue'
import GraphEditor from '../pages/GraphEditor.vue'
import Settings from '../pages/Settings.vue'
import AgentRuns from '../pages/AgentRuns.vue'
import AgentStats from '../pages/AgentStats.vue'
import AgentDetail from '../pages/AgentDetail.vue'
import Alerts from '../pages/Alerts.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', component: Login, meta: { public: true, title: '登录' } },
    { path: '/', redirect: '/playground' },
    { path: '/playground', component: Playground, meta: { title: '试用台' } },
    { path: '/runs', component: AgentRuns, meta: { title: '运行记录' } },
    { path: '/alerts', component: Alerts, meta: { title: '告警' } },
    { path: '/agents', component: Agents, meta: { title: 'Agent' } },
    { path: '/agents/:id/stats', component: AgentStats, meta: { title: 'Agent 统计' } },
    { path: '/agents/:id/graph', component: GraphEditor, meta: { title: '图编排' } },
    { path: '/agents/:id', component: AgentDetail, meta: { title: 'Agent 详情' } },
    { path: '/models', component: Models, meta: { title: '模型' } },
    { path: '/tools', component: Tools, meta: { title: '工具' } },
    { path: '/mcp', component: McpConnections, meta: { title: 'MCP' } },
    { path: '/intents', component: Intents, meta: { title: '意图' } },
    { path: '/knowledge', component: Knowledge, meta: { title: '知识库' } },
    { path: '/knowledge/:baseCode/graph', component: KnowledgeGraph, meta: { title: '知识图谱' } },
    { path: '/settings', component: Settings, meta: { title: '系统设置' } }
  ]
})

router.beforeEach((to) => {
  if (to.meta.public) return true
  if (!getToken()) return '/login'
  return true
})

export default router
