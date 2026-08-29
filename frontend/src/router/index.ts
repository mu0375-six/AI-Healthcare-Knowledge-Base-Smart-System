import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '@/stores/user'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', component: () => import('@/views/Login.vue'), meta: { public: true } },
    { path: '/register', component: () => import('@/views/Login.vue'), meta: { public: true } },
    {
      path: '/',
      component: () => import('@/layouts/AppLayout.vue'),
      redirect: '/home',
      children: [
        {
          path: 'home',
          component: () => import('@/views/Home.vue'),
          meta: { title: '今天', topAction: { label: '上传报告', to: '/reports/upload', icon: 'upload' } },
        },
        {
          path: 'news/:id',
          component: () => import('@/views/NewsDetail.vue'),
          meta: { title: '健康新闻', topAction: { label: '返回今天', to: '/home', icon: 'home' } },
        },
        {
          path: 'chat',
          component: () => import('@/views/Chat.vue'),
          meta: {
            title: '问诊',
            topAction: { label: '新对话', to: { path: '/chat', query: { new: '1' } }, icon: 'plus' },
          },
        },
        {
          path: 'health',
          component: () => import('@/views/Health.vue'),
          meta: {
            title: '档案',
            topAction: { label: '新建档案', to: { path: '/health', query: { new: '1' } }, icon: 'plus' },
          },
        },
        // 报告解读并入「档案」：上传报告产出的指标本来就写进档案，
        // 拆成两个顶级目的地会让一条流程跨页。旧链接重定向过去。
        { path: 'reports', redirect: { path: '/health', query: { tab: 'reports' } } },
        {
          path: 'reports/upload',
          component: () => import('@/views/Reports.vue'),
          meta: {
            title: '上传报告',
            breadcrumbParent: { label: '档案', to: { path: '/health', query: { tab: 'reports' } } },
            topAction: {
              label: '查看报告',
              to: { path: '/health', query: { tab: 'reports' } },
              icon: 'report',
            },
          },
        },
        {
          path: 'reports/:id',
          component: () => import('@/views/ReportDetail.vue'),
          meta: {
            title: '报告详情',
            breadcrumbParent: { label: '档案', to: { path: '/health', query: { tab: 'reports' } } },
          },
        },
        {
          path: 'triage',
          component: () => import('@/views/Triage.vue'),
          meta: {
            title: '科室导诊',
            topAction: { label: '转到问诊', to: '/chat', icon: 'chat' },
          },
        },
        {
          // 开发者/管理员调试面板：Milvus 连接状态、向量维度、召回打分
          path: 'vectors',
          component: () => import('@/views/Vectors.vue'),
          meta: {
            title: '向量检索',
            admin: true,
            topAction: { label: '知识库管理', to: '/admin/knowledge', icon: 'book' },
          },
        },
        {
          path: 'favorites',
          component: () => import('@/views/Favorites.vue'),
          meta: {
            title: '我的收藏',
            topAction: { label: '继续问诊', to: '/chat', icon: 'chat' },
          },
        },
        {
          path: 'admin/knowledge',
          component: () => import('@/views/admin/Knowledge.vue'),
          meta: {
            title: '知识库',
            admin: true,
            topAction: { label: '向量检索', to: '/vectors', icon: 'dots' },
          },
        },
      ],
    },
  ],
})

router.beforeEach(async (to) => {
  const token = localStorage.getItem('token')
  if (!to.meta.public && !token) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  if ((to.path === '/login' || to.path === '/register') && token) {
    return { path: '/home' }
  }
  if (to.meta.admin) {
    const store = useUserStore()
    // 刚刷新页面时内存里还没有 user（只有 localStorage 里的旧值，可能被手改），
    // 先向服务端要一次真实身份再判 admin。后端对 /api/admin/** 另有角色校验兜底。
    if (token && !store.user) {
      try {
        await store.fetchMe()
      } catch {
        return { path: '/login', query: { redirect: to.fullPath } }
      }
    }
    if (!store.isAdmin) {
      return { path: '/home' }
    }
  }
  return true
})

export default router
