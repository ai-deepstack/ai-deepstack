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
import Alerts from '../pages/Alerts.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', component: Login, meta: { public: true } },
    { path: '/', redirect: '/playground' },
    { path: '/playground', component: Playground },
    { path: '/runs', component: AgentRuns },
    { path: '/alerts', component: Alerts },
    { path: '/agents', component: Agents },
    { path: '/agents/:id/stats', component: AgentStats },
    { path: '/agents/:id/graph', component: GraphEditor },
    { path: '/models', component: Models },
    { path: '/tools', component: Tools },
    { path: '/mcp', component: McpConnections },
    { path: '/intents', component: Intents },
    { path: '/knowledge', component: Knowledge },
    { path: '/knowledge/:baseCode/graph', component: KnowledgeGraph },
    { path: '/settings', component: Settings }
  ]
})

router.beforeEach((to) => {
  if (to.meta.public) return true
  if (!getToken()) return '/login'
  return true
})

export default router
