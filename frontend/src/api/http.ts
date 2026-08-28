import axios from 'axios'
import { ElMessage } from 'element-plus'
import router from '@/router'

const http = axios.create({
  baseURL: '',
  timeout: 60000,
})

http.interceptors.request.use((config) => {
  const token = localStorage.getItem('token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

http.interceptors.response.use(
  (resp) => {
    const payload = resp.data
    if (payload && typeof payload.code === 'number' && payload.code !== 0) {
      ElMessage.error(payload.message || '请求失败')
      return Promise.reject(payload)
    }
    return payload
  },
  (err) => {
    const status = err.response?.status
    const msg = err.response?.data?.message || err.message || '网络异常'
    if (status === 401) {
      clearSessionAndRedirect()
    }
    ElMessage.error(msg)
    return Promise.reject(err)
  },
)

export async function clearSessionAndRedirect() {
  localStorage.removeItem('token')
  localStorage.removeItem('user')
  // 复位 Pinia 内存态：此前只清 localStorage，store 里的 token/user 仍保留旧身份，
  // 组件层 isAdmin/nickname 与路由守卫判定不一致。动态引入避免与 user.ts 的循环依赖。
  try {
    const { useUserStore } = await import('@/stores/user')
    useUserStore().logout()
  } catch {
    // store 尚未就绪（应用启动早期）时 localStorage 已清，无需处理
  }
  if (router.currentRoute.value.path !== '/login') {
    router.push({ path: '/login', query: { redirect: router.currentRoute.value.fullPath } })
  }
}

export default http
