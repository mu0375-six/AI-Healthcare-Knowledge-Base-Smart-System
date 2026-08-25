<template>
  <div class="auth">
    <div class="box">
      <BrandMark />
      <h2>创建账号</h2>
      <p class="hint">注册后即可为家人建档、提问和解读报告。</p>
      <el-form label-position="top" class="form">
        <el-form-item label="用户名">
          <el-input v-model="form.username" placeholder="3–32 个字符" />
        </el-form-item>
        <el-form-item label="昵称">
          <el-input v-model="form.nickname" placeholder="可选，用于首页称呼" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input v-model="form.password" type="password" show-password placeholder="至少 6 位" />
        </el-form-item>
        <button class="copper-btn full" type="button" :disabled="loading" @click="onSubmit">
          {{ loading ? '创建中…' : '注册并进入' }}
        </button>
      </el-form>
      <p class="switch">已有账号？<router-link to="/login">返回登录</router-link></p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { register } from '@/api/auth'
import { useUserStore } from '@/stores/user'
import BrandMark from '@/components/BrandMark.vue'

const router = useRouter()
const store = useUserStore()
const loading = ref(false)
const form = reactive({ username: '', nickname: '', password: '' })

async function onSubmit() {
  if (form.username.trim().length < 3 || form.password.length < 6) {
    ElMessage.warning('请填写有效的用户名和密码')
    return
  }
  loading.value = true
  try {
    await register(form)
    ElMessage.success('注册成功')
    await store.login(form.username, form.password)
    router.push('/home')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.auth {
  min-height: 100%;
  display: grid;
  place-items: center;
  background:
    radial-gradient(900px 400px at 10% -10%, rgba(196, 93, 58, 0.12), transparent),
    var(--paper);
  padding: 32px 16px;
}
.box {
  width: min(440px, 100%);
  background: var(--card);
  border: 1px solid var(--line);
  border-radius: var(--r-panel);
  padding: 36px 32px;
  box-shadow: var(--shadow);
}
.kicker {
  margin: 18px 0 6px;
  color: var(--copper);
  letter-spacing: 0.2em;
  font-size: 12px;
}
h2 {
  margin: 0 0 8px;
  font-size: 30px;
}
.hint,
.switch {
  color: var(--ink-3);
  font-size: 13px;
}
.form {
  margin-top: 18px;
}
.full {
  width: 100%;
  height: 44px;
}
.switch {
  margin-top: 18px;
}
</style>
