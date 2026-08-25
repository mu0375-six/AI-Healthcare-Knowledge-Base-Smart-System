import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '@/stores/user'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', component: () => import('@/views/Login.vue'), meta: { public: true } },
    { path: '/register', component: () => import('@/views/Register.vue'), meta: { public: true } },
    {
      path: '/',
      component: () => import('@/layouts/AppLayout.vue'),
      redirect: '/home',
      children: [
        { path: 'home', component: () => import('@/views/Home.vue'), meta: { title: '今日', kicker: '首页' } },
        { path: 'chat', component: () => import('@/views/Chat.vue'), meta: { title: '智能问答', kicker: '问诊' } },
        { path: 'health', component: () => import('@/views/Health.vue'), meta: { title: '健康档案', kicker: '家庭' } },
        { path: 'reports', component: () => import('@/views/Reports.vue'), meta: { title: '报告解读', kicker: '检查' } },
        { path: 'reports/:id', component: () => import('@/views/ReportDetail.vue'), meta: { title: '报告详情', kicker: '检查' } },
        { path: 'triage', component: () => import('@/views/Triage.vue'), meta: { title: '科室导诊', kicker: '分诊' } },
        {
          // 开发者/管理员调试面板：Milvus 连接状态、向量维度、召回打分
          path: 'vectors',
          component: () => import('@/views/Vectors.vue'),
          meta: { title: '向量检索', kicker: '管理', admin: true },
        },
        { path: 'favorites', component: () => import('@/views/Favorites.vue'), meta: { title: '我的收藏', kicker: '记录' } },
        {
          path: 'admin/knowledge',
          component: () => import('@/views/admin/Knowledge.vue'),
          meta: { title: '知识库', kicker: '管理', admin: true },
        },
      ],
    },
  ],
})

router.beforeEach((to) => {
  const token = localStorage.getItem('token')
  if (!to.meta.public && !token) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  if ((to.path === '/login' || to.path === '/register') && token) {
    return { path: '/home' }
  }
  if (to.meta.admin) {
    const store = useUserStore()
    if (!store.isAdmin) {
      return { path: '/home' }
    }
  }
  return true
})

export default router
