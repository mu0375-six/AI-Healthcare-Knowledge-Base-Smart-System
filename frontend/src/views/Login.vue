<template>
  <div class="auth">
    <div class="shell">
      <!-- 左栏讲清"这是什么、能帮你做什么"。登录页是第一印象，
           只摆一个表单会浪费这块版面；窄屏整块让位给表单。 -->
      <aside class="pitch">
        <div class="pitch-top">
          <BrandMark />
          <span><b>康识问诊</b><small>Clinical workspace</small></span>
        </div>
        <p class="pitch-kicker">个人健康信息工作台</p>
        <h2>把零散健康信息<br />整理成清晰线索。</h2>
        <p class="pitch-desc">从一次提问到一份长期档案，让检查结果、症状和建议保持在同一条时间线上。</p>
        <ul class="points">
          <li v-for="p in points" :key="p.title">
            <span class="p-ico" v-html="ICONS[p.icon]"></span>
            <span>
              <b>{{ p.title }}</b>
              <i>{{ p.desc }}</i>
            </span>
          </li>
        </ul>
        <p class="fine">内容仅供健康科普，不能替代执业医师的面诊。</p>
      </aside>

      <div class="box">
        <BrandMark class="mobile-logo" />
        <div class="auth-tabs" role="tablist" aria-label="账号入口">
          <button type="button" role="tab" :aria-selected="mode === 'login'" :class="{ active: mode === 'login' }" @click="switchMode('login')">登录</button>
          <button type="button" role="tab" :aria-selected="mode === 'register'" :class="{ active: mode === 'register' }" @click="switchMode('register')">注册</button>
        </div>
        <p class="eyebrow">{{ mode === 'login' ? '欢迎回来' : '开始使用' }}</p>
        <h1>{{ mode === 'login' ? '登录' : '创建账号' }}</h1>
        <p class="sub">
          {{ mode === 'login' ? '登录以继续管理你和家人的健康档案。' : '注册后即可为家人建档、提问和解读报告。' }}
        </p>

        <form class="form" @submit.prevent="onSubmit">
          <label class="field">
            <span>用户名</span>
            <el-input v-model="form.username" size="large" autocomplete="username" />
          </label>
          <label v-if="mode === 'register'" class="field">
            <span>昵称（可选）</span>
            <el-input v-model="form.nickname" size="large" placeholder="用于首页称呼" />
          </label>
          <label class="field">
            <span>密码</span>
            <el-input
              v-model="form.password"
              type="password"
              show-password
              size="large"
              :autocomplete="mode === 'login' ? 'current-password' : 'new-password'"
              :aria-describedby="mode === 'register' ? 'password-rule' : undefined"
            />
            <small v-if="mode === 'register'" id="password-rule" class="field-help">
              密码至少 6 位
            </small>
          </label>
          <button
            class="btn btn-primary btn-cta btn-block go"
            type="submit"
            :disabled="loading"
            :aria-busy="loading"
          >
            <span>{{ submitLabel }}</span>
            <span class="knob" :class="{ loading }" aria-hidden="true">
              <span v-if="!loading" v-html="ICONS.arrow"></span>
            </span>
          </button>
        </form>

        <p class="switch">{{ mode === 'login' ? '使用你的账号继续' : '创建后将自动登录' }}</p>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { register } from '@/api/auth'
import { useUserStore } from '@/stores/user'
import BrandMark from '@/components/BrandMark.vue'
import { ICONS, type IconName } from '@/utils/icons'

// 登录/注册共用一个 apple.com 式页面，切换即原地换文案，不做两个路由两套排版
const mode = ref<'login' | 'register'>('login')
const store = useUserStore()
const router = useRouter()
const route = useRoute()
const loading = ref(false)
const form = reactive({ username: '', nickname: '', password: '' })
const submitLabel = computed(() => {
  if (loading.value) return mode.value === 'login' ? '正在进入…' : '创建中…'
  return mode.value === 'login' ? '登录' : '注册并进入'
})

const points: { icon: IconName; title: string; desc: string }[] = [
  { icon: 'chat', title: '有据可依的问答', desc: '答案标出引用来源，不是凭空生成' },
  { icon: 'report', title: '化验单直接读图', desc: '拍一张照，指标高低逐项讲清楚' },
  { icon: 'pulse', title: '一家人的趋势', desc: '给爸妈孩子各建一份档案，长期看变化' },
]

watch(
  () => route.path,
  (p) => {
    mode.value = p.endsWith('register') ? 'register' : 'login'
  },
  { immediate: true },
)

function switchMode(m: 'login' | 'register') {
  router.push(m === 'login' ? '/login' : '/register')
}

async function onSubmit() {
  if (loading.value) return
  if (!form.username.trim() || !form.password) {
    ElMessage.warning('请输入用户名和密码')
    return
  }
  loading.value = true
  try {
    if (mode.value === 'register') {
      if (form.username.trim().length < 3 || form.password.length < 6) {
        ElMessage.warning('用户名至少 3 位、密码至少 6 位')
        return
      }
      await register({ username: form.username.trim(), password: form.password, nickname: form.nickname || undefined })
      ElMessage.success('注册成功')
    }
    await store.login(form.username.trim(), form.password)
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/home'
    router.push(redirect)
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.auth {
  min-height: 100dvh;
  display: grid;
  place-items: center;
  padding: 28px;
  background: var(--paper);
}

.shell {
  width: min(1040px, 100%);
  min-height: 640px;
  display: grid;
  grid-template-columns: 1.12fr 0.88fr;
  background: var(--card);
  border: 1px solid var(--edge);
  border-radius: var(--r-shell);
  box-shadow: var(--shadow-3);
  overflow: hidden;
}

.pitch {
  padding: 42px;
  background: var(--nav-bg);
  color: var(--nav-ink);
  border-right: 1px solid var(--nav-border);
  display: flex;
  flex-direction: column;
}

.pitch-top {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  margin-bottom: 64px;
  font-size: 14px;
  letter-spacing: 0;
}

.pitch-top > span {
  display: grid;
  gap: 1px;
}

.pitch-top b {
  font-weight: 680;
}

.pitch-top small {
  color: var(--nav-mute);
  font-size: 9px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.pitch-kicker {
  margin-bottom: 12px;
  color: var(--nav-active);
  font-size: 11px;
  font-weight: 650;
}

.pitch h2 {
  color: var(--nav-ink);
  margin-bottom: var(--space-4);
  font-size: clamp(30px, 3.6vw, 44px);
  line-height: 1.16;
}

.pitch-desc {
  max-width: 36em;
  margin-bottom: 36px;
  color: var(--nav-mute);
  font-size: 13px;
  line-height: 1.75;
}

.points {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  gap: 0;
}

.points li {
  display: flex;
  gap: var(--space-3);
  align-items: center;
  padding: 14px 0;
  border-top: 1px solid var(--nav-border);
}

.p-ico {
  display: grid;
  place-items: center;
  width: 32px;
  height: 32px;
  border-radius: var(--r-control);
  background: rgba(74, 195, 154, 0.13);
  border: 1px solid rgba(74, 195, 154, 0.24);
  color: var(--nav-active);
  flex-shrink: 0;
}

.p-ico :deep(svg) {
  width: 17px;
  height: 17px;
  display: block;
}

.points b {
  display: block;
  color: var(--nav-ink);
  font-size: 13px;
  font-weight: 620;
}

.points i {
  display: block;
  font-style: normal;
  font-size: 12.5px;
  line-height: 1.6;
  color: var(--nav-mute);
  margin-top: 2px;
}

.fine {
  margin-top: auto;
  padding-top: 28px;
  font-size: 11.5px;
  line-height: 1.7;
  color: var(--nav-mute);
}

.box {
  padding: 50px 46px;
  display: flex;
  flex-direction: column;
  justify-content: center;
}

.box > .eyebrow {
  align-self: flex-start;
}

.auth-tabs {
  align-self: flex-start;
  display: grid;
  grid-template-columns: 1fr 1fr;
  width: 168px;
  margin-bottom: 42px;
  padding: 3px;
  border-radius: var(--r-control);
  background: var(--sunk);
  border: 1px solid var(--edge);
}

.auth-tabs button {
  min-height: 32px;
  padding: 0 14px;
  border: 0;
  border-radius: 4px;
  background: transparent;
  color: var(--ink-mute);
  font-size: 12.5px;
  font-weight: 600;
  cursor: pointer;
}

.auth-tabs button.active {
  background: var(--card);
  color: var(--ink);
  box-shadow: var(--shadow-1);
}

.mobile-logo {
  display: none;
  margin-bottom: 16px;
}

.box h1 {
  margin: 8px 0;
  font-size: 34px;
}

.sub {
  margin-bottom: 30px;
  color: var(--ink-mute);
  line-height: 1.65;
}

.form {
  display: grid;
  gap: var(--space-4);
}

.go {
  min-height: 46px;
  margin-top: var(--space-1);
}

.go .knob.loading::after {
  content: '';
  width: 14px;
  height: 14px;
  border: 1.5px solid color-mix(in srgb, var(--on-accent) 45%, transparent);
  border-top-color: var(--on-accent);
  border-radius: var(--r-pill);
  animation: spin 0.8s var(--ease-soft) infinite;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

.field-help {
  display: block;
  margin-top: var(--space-1);
  color: var(--ink-faint);
  font-size: 12px;
  line-height: 1.5;
}

.switch {
  margin-top: var(--space-4);
  color: var(--ink-faint);
  font-size: 12px;
}

@media (max-width: 880px) {
  .shell {
    grid-template-columns: 1fr;
    min-height: auto;
    box-shadow: var(--shadow-2);
  }
  .pitch {
    display: none;
  }
  .mobile-logo {
    display: block;
  }
  .box {
    padding: 32px 24px;
  }

  .auth-tabs {
    margin-bottom: var(--space-6);
  }
}
</style>
