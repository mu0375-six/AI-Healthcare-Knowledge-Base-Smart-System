<template>
  <div class="app" :class="{ fill: isChat }">
    <aside class="rail" :class="{ open: navOpen }">
      <router-link to="/home" class="brand" @click="navOpen = false">
        <BrandMark tone="light" />
        <div>
          <div class="brand-name">康识问诊</div>
          <div class="brand-sub">把知识讲成人话</div>
        </div>
      </router-link>

      <nav class="nav">
        <router-link v-for="item in mainNav" :key="item.to" :to="item.to" class="nav-item" @click="navOpen = false">
          <span class="ico" v-html="item.icon"></span>
          <span>{{ item.label }}</span>
        </router-link>
        <template v-if="user.isAdmin">
          <p class="nav-label">管理</p>
          <router-link to="/admin/knowledge" class="nav-item" @click="navOpen = false">
            <span class="ico" v-html="icons.book"></span>
            <span>知识库</span>
          </router-link>
          <router-link to="/vectors" class="nav-item" @click="navOpen = false">
            <span class="ico" v-html="icons.dots"></span>
            <span>向量检索</span>
          </router-link>
        </template>
      </nav>

      <div class="rail-foot">
        内容仅供科普，不能替代面诊。
      </div>
    </aside>

    <div v-if="navOpen" class="scrim" @click="navOpen = false"></div>

    <section class="stage">
      <header class="top">
        <button class="menu-btn" type="button" @click="navOpen = true" aria-label="打开菜单">
          <span></span><span></span><span></span>
        </button>
        <div class="crumb">
          <span class="crumb-kicker">{{ String(route.meta.kicker || '康识') }}</span>
          <strong>{{ String(route.meta.title || '问诊') }}</strong>
        </div>
        <div class="who">
          <span class="role">{{ user.isAdmin ? '管理员' : '成员' }}</span>
          <span class="nick">{{ user.nickname }}</span>
          <button class="ghost-btn slim" type="button" @click="onLogout">退出</button>
        </div>
      </header>
      <main class="main" :class="{ 'main-fill': isChat }">
        <router-view />
      </main>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import BrandMark from '@/components/BrandMark.vue'

const route = useRoute()
const router = useRouter()
const user = useUserStore()
const navOpen = ref(false)
const isChat = computed(() => route.path === '/chat')

onMounted(() => {
  user.fetchMe().catch(() => undefined)
})

const icons = {
  home: '<svg viewBox="0 0 24 24"><path d="M4 11.5 12 5l8 6.5V20H4z" fill="none" stroke="currentColor" stroke-width="1.6"/><path d="M9 20v-6h6v6" fill="none" stroke="currentColor" stroke-width="1.6"/></svg>',
  chat: '<svg viewBox="0 0 24 24"><path d="M5 6h14v9H8l-3 3z" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linejoin="round"/></svg>',
  file: '<svg viewBox="0 0 24 24"><rect x="6" y="4" width="12" height="16" rx="1.5" fill="none" stroke="currentColor" stroke-width="1.6"/><path d="M9 9h6M9 13h6M9 17h4" fill="none" stroke="currentColor" stroke-width="1.6"/></svg>',
  report: '<svg viewBox="0 0 24 24"><path d="M7 4h7l4 4v12H7z" fill="none" stroke="currentColor" stroke-width="1.6"/><path d="M14 4v4h4" fill="none" stroke="currentColor" stroke-width="1.6"/></svg>',
  compass: '<svg viewBox="0 0 24 24"><circle cx="12" cy="12" r="8" fill="none" stroke="currentColor" stroke-width="1.6"/><path d="m10 14 1.2-3.8L15 9l-1.2 3.8z" fill="currentColor"/></svg>',
  dots: '<svg viewBox="0 0 24 24"><circle cx="7" cy="8" r="1.6" fill="currentColor"/><circle cx="12" cy="14" r="2.1" fill="currentColor"/><circle cx="17" cy="9" r="1.4" fill="currentColor"/><circle cx="9" cy="18" r="1.2" fill="currentColor"/></svg>',
  clock: '<svg viewBox="0 0 24 24"><circle cx="12" cy="12" r="8" fill="none" stroke="currentColor" stroke-width="1.6"/><path d="M12 8v5l3 2" fill="none" stroke="currentColor" stroke-width="1.6"/></svg>',
  star: '<svg viewBox="0 0 24 24"><path d="m12 5 1.8 4.6L19 11l-3.6 3.1L16.5 19 12 16.6 7.5 19l1.1-4.9L5 11l5.2-1.4z" fill="none" stroke="currentColor" stroke-width="1.6"/></svg>',
  book: '<svg viewBox="0 0 24 24"><path d="M6 5h11v14H8a2 2 0 0 1-2-2z" fill="none" stroke="currentColor" stroke-width="1.6"/><path d="M6 17h11" fill="none" stroke="currentColor" stroke-width="1.6"/></svg>',
}

const mainNav = [
  { to: '/home', label: '首页', icon: icons.home },
  { to: '/chat', label: '智能问答', icon: icons.chat },
  { to: '/health', label: '健康档案', icon: icons.file },
  { to: '/reports', label: '报告解读', icon: icons.report },
  { to: '/triage', label: '科室导诊', icon: icons.compass },
  // 会话历史已并入「智能问答」侧栏：那里能直接续问，比只读的历史页强
  { to: '/favorites', label: '我的收藏', icon: icons.star },
]

function onLogout() {
  user.logout()
  router.push('/login')
}
</script>

<style scoped>
.app {
  height: 100%;
  display: grid;
  grid-template-columns: var(--nav) 1fr;
  background: var(--paper);
}
.rail {
  background: var(--rail);
  color: #e7dfd0;
  padding: 22px 16px 18px;
  display: flex;
  flex-direction: column;
  min-height: 0;
  z-index: 30;
}
.brand {
  display: flex;
  gap: 10px;
  align-items: center;
  color: inherit;
  padding: 4px 8px 22px;
}
.brand-name {
  font-family: var(--font-serif);
  font-size: 18px;
  letter-spacing: 0.08em;
}
.brand-sub {
  font-size: 11px;
  color: rgba(231, 223, 208, 0.55);
  margin-top: 2px;
}
.nav {
  flex: 1;
  overflow: auto;
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.nav-label {
  margin: 16px 10px 6px;
  font-size: 11px;
  letter-spacing: 0.18em;
  color: rgba(231, 223, 208, 0.38);
}
.nav-item {
  display: flex;
  align-items: center;
  gap: 10px;
  color: rgba(231, 223, 208, 0.78);
  padding: 9px 12px;
  border-radius: var(--r-control);
  font-size: 14px;
}
.nav-item:hover {
  background: rgba(255, 255, 255, 0.05);
  color: #fff;
}
.nav-item.router-link-active {
  background: rgba(196, 93, 58, 0.18);
  color: #fff;
}
.ico {
  width: 20px;
  height: 20px;
  display: grid;
  place-items: center;
}
.ico :deep(svg) {
  width: 18px;
  height: 18px;
}
.rail-foot {
  font-size: 11px;
  line-height: 1.6;
  color: rgba(231, 223, 208, 0.4);
  padding: 16px 10px 4px;
}
.stage {
  min-width: 0;
  min-height: 0;
  display: flex;
  flex-direction: column;
}
.top {
  height: 64px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 28px;
  border-bottom: 1px solid var(--line);
}
.crumb-kicker {
  display: block;
  font-size: 11px;
  letter-spacing: 0.06em;
  color: var(--copper);
}
.crumb strong {
  font-family: var(--font-serif);
  font-size: 18px;
  font-weight: 600;
}
.who {
  display: flex;
  align-items: center;
  gap: 10px;
}
.role {
  font-size: 11px;
  letter-spacing: 0.12em;
  color: var(--moss);
  border: 1px solid var(--line-strong);
  padding: 3px 8px;
  border-radius: 999px;
}
.nick {
  color: var(--ink-2);
  font-size: 14px;
}
.slim {
  padding: 6px 12px;
  font-size: 13px;
}
.main {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 24px 28px 36px;
}
.main-fill {
  overflow: hidden;
  display: flex;
  flex-direction: column;
  padding-bottom: 16px;
}
.main-fill > * {
  flex: 1;
  min-height: 0;
}
.menu-btn,
.scrim {
  display: none;
}
@media (max-width: 860px) {
  .app {
    grid-template-columns: 1fr;
  }
  .rail {
    position: fixed;
    inset: 0 auto 0 0;
    width: min(80vw, 280px);
    transform: translateX(-105%);
    transition: transform 0.2s ease;
  }
  .rail.open {
    transform: none;
  }
  .scrim {
    display: block;
    position: fixed;
    inset: 0;
    background: rgba(16, 22, 19, 0.4);
    z-index: 25;
  }
  .menu-btn {
    display: inline-flex;
    flex-direction: column;
    gap: 4px;
    background: none;
    border: 0;
    padding: 6px;
    margin-right: 8px;
  }
  .menu-btn span {
    width: 16px;
    height: 1.5px;
    background: var(--ink);
  }
  .top {
    padding: 0 16px;
  }
  .main {
    padding: 16px 16px 28px;
  }
  .crumb {
    flex: 1;
  }
}
</style>
