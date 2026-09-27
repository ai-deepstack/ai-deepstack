<template>
  <div v-if="isLoginPage">
    <router-view />
  </div>
  <div v-else class="shell">
    <aside class="sidebar">
      <div class="brand-block">
        <div class="brand-row">
          <img class="brand-icon" src="/favicon.svg" alt="" width="28" height="28" />
          <div class="brand-mark">AI <em>DeepStack</em></div>
        </div>
        <div class="brand-sub">管理后台</div>
      </div>
      <nav class="nav">
        <router-link to="/playground">试用台</router-link>
        <router-link to="/runs">运行记录</router-link>
        <router-link to="/alerts">告警</router-link>
        <router-link to="/agents">智能体</router-link>
        <router-link to="/models">模型</router-link>
        <router-link to="/tools">工具</router-link>
        <router-link to="/mcp">MCP</router-link>
        <router-link to="/intents">意图</router-link>
        <router-link to="/knowledge">知识库</router-link>
        <router-link to="/settings">系统设置</router-link>
      </nav>
      <div class="sidebar-foot">
        <div class="user-chip">{{ displayName }}</div>
        <button class="btn-ghost" type="button" @click="logout">退出</button>
      </div>
    </aside>
    <main class="main">
      <router-view />
    </main>
  </div>
  <ConfirmDialog />
</template>

<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { clearAuth, getDisplayName, getToken } from './api/http'
import ConfirmDialog from './components/ConfirmDialog.vue'

const route = useRoute()
const router = useRouter()
const isLoginPage = computed(() => route.path === '/login')
const displayName = computed(() => getDisplayName() || '用户')

async function logout() {
  try {
    await fetch('/api/auth/logout', {
      method: 'POST',
      headers: { Authorization: `Bearer ${getToken()}` }
    })
  } catch (_) { /* ignore */ }
  clearAuth()
  router.push('/login')
}
</script>
