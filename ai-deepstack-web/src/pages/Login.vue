<template>
  <div class="login-stage">
    <section class="login-hero">
      <div class="login-hero-grid" />
      <svg class="login-neural" viewBox="0 0 640 420" fill="none" aria-hidden="true">
        <defs>
          <linearGradient id="edge" x1="0" y1="0" x2="1" y2="1">
            <stop offset="0%" stop-color="#3de0c5" stop-opacity="0.15" />
            <stop offset="50%" stop-color="#3de0c5" stop-opacity="0.85" />
            <stop offset="100%" stop-color="#4ea8ff" stop-opacity="0.2" />
          </linearGradient>
          <filter id="glow">
            <feGaussianBlur stdDeviation="2.2" result="b" />
            <feMerge>
              <feMergeNode in="b" />
              <feMergeNode in="SourceGraphic" />
            </feMerge>
          </filter>
        </defs>
        <g stroke="url(#edge)" stroke-width="1.2" filter="url(#glow)">
          <path d="M80 210 C160 80, 280 80, 320 210 C360 340, 480 340, 560 210" />
          <path d="M120 320 C220 260, 260 120, 400 140 C520 160, 540 280, 580 300" />
          <path d="M60 120 C180 160, 240 300, 360 280 C470 260, 520 120, 600 160" />
          <path class="pulse-line" d="M100 180 L220 140 L300 250 L420 120 L520 240 L600 180" />
        </g>
        <g filter="url(#glow)">
          <circle class="n" cx="100" cy="180" r="5" fill="#3de0c5" />
          <circle class="n" cx="220" cy="140" r="4" fill="#7ff5df" />
          <circle class="n" cx="300" cy="250" r="6" fill="#4ea8ff" />
          <circle class="n" cx="420" cy="120" r="4" fill="#3de0c5" />
          <circle class="n" cx="520" cy="240" r="5" fill="#7ff5df" />
          <circle class="n" cx="600" cy="180" r="4" fill="#4ea8ff" />
          <circle class="n" cx="160" cy="300" r="3.5" fill="#3de0c5" />
          <circle class="n" cx="480" cy="300" r="3.5" fill="#4ea8ff" />
        </g>
      </svg>
      <div class="login-hero-inner">
        <div class="login-brand">
          <img class="login-brand-icon" src="/favicon.svg" alt="" width="40" height="40" />
          <div class="eyebrow">AI DeepStack</div>
        </div>
        <h1>智能体管理平台</h1>
        <p>配置模型与工具，编排智能体流程，在试用台里对话验证。</p>
        <div class="login-stats">
          <div class="login-stat"><strong>对话 / 流程图</strong><span>两种编排方式</span></div>
          <div class="login-stat"><strong>流式输出</strong><span>边生成边看结果</span></div>
          <div class="login-stat"><strong>模型 · 工具 · 知识库</strong><span>统一配置入口</span></div>
        </div>
      </div>
    </section>
    <section class="login-panel">
      <form class="panel login-card hud-corners" @submit.prevent="onSubmit">
        <h2>登录</h2>
        <p class="muted" style="margin: 0 0 1.2rem; font-size: 0.85rem">
          默认账号 admin / admin123
        </p>
        <div class="field">
          <label>用户名</label>
          <input v-model="form.username" autocomplete="username" required />
        </div>
        <div class="field">
          <label>密码</label>
          <input v-model="form.password" type="password" autocomplete="current-password" required />
        </div>
        <p v-if="error" class="error">{{ error }}</p>
        <button class="btn" type="submit" :disabled="loading" style="width: 100%">
          {{ loading ? '登录中…' : '登录' }}
        </button>
      </form>
    </section>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { post, setAuth } from '../api/http'

const router = useRouter()
const loading = ref(false)
const error = ref('')
const form = reactive({ username: 'admin', password: 'admin123' })

async function onSubmit() {
  loading.value = true
  error.value = ''
  try {
    const res = await post('/api/auth/login', form)
    const data = res.data || {}
    setAuth({
      token: data.token,
      userId: data.userId,
      displayName: data.displayName,
      isAdmin: data.isAdmin
    })
    router.replace('/playground')
  } catch (e) {
    error.value = e.message || '登录失败'
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.pulse-line {
  stroke-dasharray: 8 10;
  animation: dash 4.5s linear infinite;
}
.n {
  animation: nodePulse 2.4s ease-in-out infinite;
}
.n:nth-child(2) { animation-delay: 0.2s; }
.n:nth-child(3) { animation-delay: 0.4s; }
.n:nth-child(4) { animation-delay: 0.55s; }
.n:nth-child(5) { animation-delay: 0.7s; }
.n:nth-child(6) { animation-delay: 0.9s; }
@keyframes dash {
  to { stroke-dashoffset: -120; }
}
@keyframes nodePulse {
  0%, 100% { opacity: 0.45; }
  50% { opacity: 1; }
}
</style>
