<template>
  <div class="auth">
    <div class="shell">
      <!-- 左栏讲清"这是什么、能帮你做什么"。登录页是第一印象，
           只摆一个表单会浪费这块版面；窄屏整块让位给表单。 -->
      <aside class="pitch">
        <div class="pitch-top">
          <BrandMark />
          <span>康识问诊</span>
        </div>
        <h2>看得懂的<br />健康解释</h2>
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

        <p class="switch">
          <template v-if="mode === 'login'">
            还没有账号？<button type="button" class="link" @click="switchMode('register')">创建一个</button>
          </template>
          <template v-else>
            已有账号？<button type="button" class="link" @click="switchMode('login')">返回登录</button>
          </template>
        </p>
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
  padding: 32px 20px;
  background: var(--paper);
}

.shell {
  width: min(940px, 100%);
  display: grid;
  grid-template-columns: 1.02fr 1fr;
  background: var(--card);
  border: 1px solid var(--edge);
  border-radius: var(--r-shell);
  box-shadow: var(--shadow-4);
  overflow: hidden;
}

/* 左栏用同色系更深一档，而不是突然跳到近黑 ——
   浅色页面里插一块纯黑会像复制粘贴事故。 */
.pitch {
  padding: 40px 36px;
  background: var(--sunk);
  border-right: 1px solid var(--edge);
  display: flex;
  flex-direction: column;
}

.pitch-top {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  margin-bottom: var(--space-6);
  font-size: 15px;
  font-weight: 650;
  letter-spacing: 0;
}

.pitch h2 {
  margin-bottom: 26px;
  line-height: 1.2;
}

.points {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  gap: var(--space-5);
}

.points li {
  display: flex;
  gap: 12px;
  align-items: flex-start;
}

.p-ico {
  display: grid;
  place-items: center;
  width: 32px;
  height: 32px;
  border-radius: var(--r-avatar);
  background: var(--accent-wash);
  border: 1px solid var(--accent-line);
  color: var(--accent);
  flex-shrink: 0;
}

.p-ico :deep(svg) {
  width: 17px;
  height: 17px;
  display: block;
}

.points b {
  display: block;
  font-size: 14px;
  font-weight: 600;
}

.points i {
  display: block;
  font-style: normal;
  font-size: 12.5px;
  line-height: 1.6;
  color: var(--ink-mute);
  margin-top: 2px;
}

.fine {
  margin-top: auto;
  padding-top: 28px;
  font-size: 11.5px;
  line-height: 1.7;
  color: var(--ink-faint);
}

.box {
  padding: 44px 40px;
  display: flex;
  flex-direction: column;
  justify-content: center;
}

.mobile-logo {
  display: none;
  margin-bottom: 16px;
}

.box h1 {
  margin: 6px 0 8px;
}

.sub {
  margin-bottom: 26px;
  color: var(--ink-mute);
  line-height: 1.65;
}

.form {
  display: grid;
  gap: var(--space-4);
}

.go {
  min-height: 50px;
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
  margin-top: 20px;
  color: var(--ink-mute);
  font-size: 14px;
}

.link {
  border: 0;
  background: none;
  padding: 0;
  color: var(--accent);
  font-size: 14px;
  font-weight: 550;
  cursor: pointer;
}

.link:hover {
  color: var(--accent-hover);
}

@media (max-width: 880px) {
  .shell {
    grid-template-columns: 1fr;
    box-shadow: var(--shadow-3);
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
}
</style>
