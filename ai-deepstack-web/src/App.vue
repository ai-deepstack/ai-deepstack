<template>
  <!-- 登录：无壳 -->
  <div v-if="isLoginPage">
    <router-view />
  </div>

  <!-- 管理后台 -->
  <div v-else class="shell" :class="{ 'nav-open': navOpen }">
    <!-- 手机端顶栏：桌面端由 CSS 隐藏 -->
    <header class="mobile-topbar">
      <button class="nav-toggle" type="button" aria-label="打开导航" @click="navOpen = true">
        <span /><span /><span />
      </button>
      <div class="mobile-title">{{ pageTitle }}</div>
      <img class="mobile-brand-icon" src="/favicon.svg" alt="" width="26" height="26" />
    </header>

    <div v-if="navOpen" class="sidebar-mask" @click="navOpen = false" />

    <aside class="sidebar">
      <div class="brand-block">
        <div class="brand-row">
          <img class="brand-icon" src="/favicon.svg" alt="" width="28" height="28" />
          <div class="brand-mark">AI <em>DeepStack</em></div>
          <button class="nav-close" type="button" aria-label="关闭导航" @click="navOpen = false">×</button>
        </div>
        <div class="brand-sub">管理后台</div>
      </div>
      <nav class="nav" @click="navOpen = false">
        <router-link to="/playground">试用台</router-link>
        <router-link to="/runs">运行记录</router-link>
        <router-link to="/alerts">告警</router-link>
        <router-link to="/agents">Agent</router-link>
        <router-link to="/models">模型</router-link>
        <router-link to="/tools">工具</router-link>
        <router-link to="/mcp">MCP</router-link>
        <router-link to="/intents">意图</router-link>
        <router-link to="/knowledge">知识库</router-link>
        <router-link to="/settings">系统设置</router-link>
        <div class="nav-divider" aria-hidden="true" />
        <a :href="SITE_URL" target="_blank" rel="noopener">项目介绍</a>
        <a :href="`${SITE_URL}/docs`" target="_blank" rel="noopener">项目文档</a>
      </nav>
      <div class="sidebar-foot">
        <a class="sidebar-gh" :href="GITHUB_URL" target="_blank" rel="noopener" title="GitHub 仓库">
          <GithubIcon class="btn-ico" />
          <span>{{ GITHUB_REPO }}</span>
        </a>
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
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { clearAuth, getDisplayName, getToken } from './api/http'
import ConfirmDialog from './components/ConfirmDialog.vue'
import GithubIcon from './components/GithubIcon.vue'
import { GITHUB_REPO, GITHUB_URL, SITE_URL } from './config/site'

const route = useRoute()
const router = useRouter()
const isLoginPage = computed(() => route.path === '/login')
const displayName = computed(() => getDisplayName() || '用户')

const navOpen = ref(false)
const pageTitle = computed(() => route.meta?.title || 'AI DeepStack')

// 手机上点导航后收起抽屉
watch(() => route.fullPath, () => { navOpen.value = false })

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
