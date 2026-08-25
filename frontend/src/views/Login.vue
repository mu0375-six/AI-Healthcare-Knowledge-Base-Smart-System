<template>
  <div class="auth">
    <aside class="visual" aria-hidden="true">
      <img src="/art/login.svg" alt="" />
      <div class="veil">
        <BrandMark tone="light" />
        <p class="kicker">Kangshi</p>
        <h1>康识问诊</h1>
        <p class="lead">把 WHO 与国家卫健委的公开知识，讲成你听得懂的话。</p>
      </div>
    </aside>
    <main class="panel">
      <div class="inner">
        <h2>欢迎回来</h2>
        <p class="hint">演示账号 user / User123!　管理员 admin / Admin123!</p>
        <form class="form" @submit.prevent="onSubmit">
          <label>
            <span>用户名</span>
            <el-input v-model="form.username" size="large" autocomplete="username" />
          </label>
          <label>
            <span>密码</span>
            <el-input v-model="form.password" type="password" show-password size="large" autocomplete="current-password" />
          </label>
          <button class="copper-btn full" type="submit" :disabled="loading">{{ loading ? '正在进入…' : '进入系统' }}</button>
        </form>
        <p class="switch">还没有账号？<router-link to="/register">创建一个</router-link></p>
      </div>
    </main>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'
import BrandMark from '@/components/BrandMark.vue'

const store = useUserStore()
const router = useRouter()
const route = useRoute()
const loading = ref(false)
const form = reactive({ username: 'user', password: 'User123!' })

async function onSubmit() {
  if (!form.username || !form.password) {
    ElMessage.warning('请输入用户名和密码')
    return
  }
  loading.value = true
  try {
    await store.login(form.username, form.password)
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/home'
    router.push(redirect)
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.auth {
  min-height: 100%;
  display: grid;
  grid-template-columns: 1.05fr 0.95fr;
  background: var(--paper);
}
.visual {
  position: relative;
  overflow: hidden;
  min-height: 100vh;
}
.visual img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  filter: saturate(0.9);
}
.veil {
  position: absolute;
  inset: 0;
  background: linear-gradient(180deg, rgba(16, 28, 24, 0.18), rgba(16, 28, 24, 0.62));
  color: #f6f0e6;
  padding: 48px 44px;
  display: flex;
  flex-direction: column;
  justify-content: flex-end;
}
.veil :deep(.mark) {
  margin-bottom: 18px;
}
.kicker {
  margin: 0 0 8px;
  letter-spacing: 0.18em;
  font-size: 12px;
  color: #e0b08a;
}
h1,
h2 {
  font-family: var(--font-serif);
  margin: 0 0 10px;
}
h1 {
  font-size: 44px;
  letter-spacing: 0.12em;
}
.lead {
  margin: 0;
  max-width: 360px;
  line-height: 1.7;
  color: rgba(246, 240, 230, 0.82);
}
.panel {
  display: grid;
  place-items: center;
  padding: 40px 24px;
}
.inner {
  width: min(420px, 100%);
}
h2 {
  font-size: 34px;
}
.hint {
  color: var(--ink-3);
  font-size: 13px;
  margin: 0 0 28px;
  line-height: 1.6;
}
.form {
  display: grid;
  gap: 16px;
}
label span {
  display: block;
  font-size: 12px;
  letter-spacing: 0.12em;
  color: var(--ink-3);
  margin-bottom: 6px;
}
.full {
  width: 100%;
  height: 46px;
  margin-top: 8px;
}
.switch {
  margin-top: 22px;
  color: var(--ink-3);
  font-size: 13px;
}
@media (max-width: 860px) {
  .auth {
    grid-template-columns: 1fr;
  }
  .visual {
    min-height: 240px;
  }
  h1 {
    font-size: 32px;
  }
}
</style>
