<template>
  <el-dialog v-model="visible" title="账号设置" width="420px" :append-to-body="true">
    <div class="info">
      <div class="row"><span>用户名</span><strong>{{ user.user?.username }}</strong></div>
      <div class="row"><span>昵称</span><strong>{{ user.nickname }}</strong></div>
      <div class="row"><span>角色</span><strong>{{ user.isAdmin ? '管理员' : '成员' }}</strong></div>
    </div>

    <el-divider content-position="left">修改密码</el-divider>

    <el-form label-position="top" @submit.prevent>
      <el-form-item label="原密码">
        <el-input v-model="form.oldPassword" type="password" show-password autocomplete="current-password" />
      </el-form-item>
      <el-form-item label="新密码（6-64 位）">
        <el-input v-model="form.newPassword" type="password" show-password autocomplete="new-password" />
      </el-form-item>
      <el-form-item label="确认新密码">
        <el-input v-model="form.confirm" type="password" show-password autocomplete="new-password" />
      </el-form-item>
    </el-form>

    <p class="notice">修改成功后会退出登录，请用新密码重新登录。</p>

    <template #footer>
      <el-button @click="visible = false">关闭</el-button>
      <el-button type="primary" :loading="saving" :disabled="!canSave" @click="save">保存并重新登录</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { changePassword } from '@/api/auth'
import { useUserStore } from '@/stores/user'

const visible = defineModel<boolean>({ default: false })
const user = useUserStore()
const router = useRouter()

const form = reactive({ oldPassword: '', newPassword: '', confirm: '' })
const saving = ref(false)

watch(visible, (v) => {
  if (v) {
    form.oldPassword = ''
    form.newPassword = ''
    form.confirm = ''
  }
})

const canSave = computed(() =>
  form.oldPassword.length > 0 && form.newPassword.length >= 6 && form.newPassword === form.confirm)

async function save() {
  if (form.newPassword !== form.confirm) {
    ElMessage.warning('两次输入的新密码不一致')
    return
  }
  saving.value = true
  try {
    await changePassword({ oldPassword: form.oldPassword, newPassword: form.newPassword })
    ElMessage.success('密码已修改，请重新登录')
    visible.value = false
    user.logout()
    router.push('/login')
  } catch {
    // 业务错误（原密码不对等）由 http 拦截器统一提示
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.info {
  margin-bottom: 4px;
}
.row {
  display: flex;
  justify-content: space-between;
  padding: 6px 0;
  font-size: 14px;
}
.row span {
  color: var(--ink-faint);
}
.notice {
  margin: 8px 0 0;
  font-size: 12px;
  color: var(--flag-high);
}
</style>
