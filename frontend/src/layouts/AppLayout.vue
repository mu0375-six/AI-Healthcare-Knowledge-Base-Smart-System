<template>
  <div class="shell-root">
    <a class="skip-link" href="#main">跳到主要内容</a>

    <!-- 左侧常驻栏：图标 + 文字，一屏就能看见全部去处。
         内容区因此从"窄栏居中"变成"贴着侧栏铺开"。 -->
    <aside class="rail" :class="{ open: railOpen }">
      <router-link to="/home" class="brand" @click="railOpen = false">
        <BrandMark />
        <span class="brand-name">康识问诊</span>
      </router-link>

      <nav class="rail-nav" aria-label="主导航">
        <router-link
          v-for="item in PRIMARY"
          :key="item.to"
          :to="item.to"
          class="rail-item"
          @click="railOpen = false"
        >
          <span class="rail-ico" v-html="ICONS[item.icon]"></span>
          <span>{{ item.label }}</span>
        </router-link>

        <p class="rail-label">更多</p>
        <router-link
          v-for="item in secondary"
          :key="item.to"
          :to="item.to"
          class="rail-item"
          @click="railOpen = false"
        >
          <span class="rail-ico" v-html="ICONS[item.icon]"></span>
          <span>{{ item.label }}</span>
        </router-link>
      </nav>

      <!-- 侧栏底部的提示卡：Canva 在这个位置放的也是引导卡片 -->
      <div class="rail-card">
        <b>把化验单拍下来</b>
        <i>指标、高低、逐项解释会自动整理成档案。</i>
        <router-link class="btn btn-primary btn-sm" to="/reports/upload">去解读</router-link>
      </div>
    </aside>

    <div v-if="railOpen" class="scrim" @click="railOpen = false"></div>

    <div class="stage">
      <header class="topbar">
        <button class="burger" type="button" aria-label="打开菜单" @click="railOpen = true">
          <span></span><span></span>
        </button>
        <strong class="crumb">{{ String(route.meta.title || '') }}</strong>
        <div class="top-end">
          <button
            class="icon-btn"
            type="button"
            :title="dark ? '切换到浅色' : '切换到深色'"
            :aria-label="dark ? '切换到浅色' : '切换到深色'"
            @click="dark = !dark"
          >
            <span v-html="dark ? ICONS.sun : ICONS.moon"></span>
          </button>

          <el-dropdown trigger="click" @command="onCommand">
            <button class="who" type="button">
              <span class="avatar">{{ avatarChar }}</span>
              <span class="who-name">{{ user.nickname }}</span>
              <span class="caret" v-html="ICONS.chevron"></span>
            </button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item disabled>
                  <span class="menu-role">{{ user.isAdmin ? '管理员' : '成员' }} · {{ user.user?.username }}</span>
                </el-dropdown-item>
                <el-dropdown-item divided command="account">账号设置</el-dropdown-item>
                <el-dropdown-item command="logout">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </header>

      <main id="main" class="main" :class="{ 'main-fill': isChat }">
        <router-view v-slot="{ Component }">
          <Transition name="page" mode="out-in">
            <component :is="Component" />
          </Transition>
        </router-view>
      </main>
    </div>

    <AccountDialog v-model="accountVisible" />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { currentTheme, toggleTheme } from '@/utils/theme'
import { ICONS, type IconName } from '@/utils/icons'
import BrandMark from '@/components/BrandMark.vue'
import AccountDialog from '@/components/AccountDialog.vue'

const route = useRoute()
const router = useRouter()
const user = useUserStore()
const isChat = computed(() => route.path === '/chat')
const accountVisible = ref(false)
const railOpen = ref(false)
const avatarChar = computed(() => user.nickname.slice(0, 1).toUpperCase())

/** 顶级目的地收到三个：一件事一个入口，其余降为二级。 */
const PRIMARY: { to: string; label: string; icon: IconName }[] = [
  { to: '/home', label: '今天', icon: 'home' },
  { to: '/chat', label: '问诊', icon: 'chat' },
  { to: '/health', label: '档案', icon: 'file' },
]

const SECONDARY: { to: string; label: string; icon: IconName; admin?: boolean }[] = [
  { to: '/triage', label: '科室导诊', icon: 'compass' },
  { to: '/favorites', label: '我的收藏', icon: 'star' },
  { to: '/admin/knowledge', label: '知识库', icon: 'book', admin: true },
  { to: '/vectors', label: '向量检索', icon: 'dots', admin: true },
]

const secondary = computed(() => SECONDARY.filter((s) => !s.admin || user.isAdmin))

const dark = ref(currentTheme() === 'dark')
watch(dark, (v) => {
  if (currentTheme() === (v ? 'dark' : 'light')) return
  toggleTheme()
})

// 路由一变就收起抽屉，否则返回时它还开着
watch(() => route.path, () => (railOpen.value = false))

onMounted(() => {
  user.fetchMe().catch(() => undefined)
})

function onCommand(cmd: string) {
  if (cmd === 'logout') {
    user.logout()
    router.push('/login')
  } else if (cmd === 'account') {
    accountVisible.value = true
  }
}
</script>

<style scoped>
.shell-root {
  min-height: 100dvh;
  display: grid;
  grid-template-columns: 236px 1fr;
}

/* ---- 左侧常驻栏 ---- */
.rail {
  position: sticky;
  top: 0;
  height: 100dvh;
  display: flex;
  flex-direction: column;
  padding: 18px 14px 16px;
  background: var(--paper-2);
  border-right: 1px solid var(--edge);
}

.brand {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 4px 8px 20px;
  color: var(--ink);
  transition: transform 0.4s var(--ease);
}

.brand:active {
  transform: scale(0.97);
}

.brand-name {
  font-family: var(--font-display);
  font-size: 19px;
  font-weight: 600;
  letter-spacing: -0.02em;
}

.rail-nav {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 2px;
  margin: 0 -4px;
  padding: 0 4px;
}

.rail-label {
  margin: 18px 10px 6px;
  font-size: 10px;
  font-weight: 600;
  letter-spacing: 0.18em;
  text-transform: uppercase;
  color: var(--ink-faint);
}

.rail-item {
  display: flex;
  align-items: center;
  gap: 11px;
  padding: 9px 11px;
  border-radius: var(--r-control);
  color: var(--ink-soft);
  font-size: 14px;
  font-weight: 500;
  transition: background 0.4s var(--ease), color 0.3s var(--ease-soft),
    transform 0.4s var(--ease);
}

.rail-item:active {
  transform: scale(0.985);
}

@media (hover: hover) and (pointer: fine) {
  .rail-item:hover {
    background: var(--tray);
    color: var(--ink);
  }
}

/* 当前位置：主色实底。左栏里实底比下划线更明确 */
.rail-item.router-link-active {
  background: var(--accent);
  color: var(--on-accent);
  font-weight: 550;
}

.rail-ico {
  display: grid;
  place-items: center;
  width: 20px;
  flex-shrink: 0;
}

.rail-ico :deep(svg) {
  width: 19px;
  height: 19px;
  display: block;
}

/* 侧栏底部引导卡 */
.rail-card {
  margin-top: 14px;
  padding: 14px;
  border-radius: var(--r-card);
  background: var(--card);
  border: 1px solid var(--edge);
  box-shadow: var(--inner-light);
}

.rail-card b {
  display: block;
  font-size: 13.5px;
  font-weight: 600;
}

.rail-card i {
  display: block;
  font-style: normal;
  font-size: 12px;
  line-height: 1.6;
  color: var(--ink-mute);
  margin: 4px 0 10px;
}

/* ---- 主区 ---- */
.stage {
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.topbar {
  position: sticky;
  top: 0;
  z-index: var(--z-nav);
  height: 58px;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 0 28px;
  background: var(--chrome);
  backdrop-filter: blur(24px) saturate(180%);
  -webkit-backdrop-filter: blur(24px) saturate(180%);
  border-bottom: 1px solid var(--edge);
}

.crumb {
  font-size: 14px;
  font-weight: 600;
  color: var(--ink-mute);
}

.top-end {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: 4px;
}

.icon-btn {
  display: grid;
  place-items: center;
  width: 34px;
  height: 34px;
  border: 0;
  background: none;
  color: var(--ink-mute);
  cursor: pointer;
  border-radius: 999px;
  transition: color 0.3s var(--ease-soft), background 0.4s var(--ease),
    transform 0.4s var(--ease);
}

.icon-btn:active {
  transform: scale(0.9);
}

.icon-btn :deep(svg) {
  width: 18px;
  height: 18px;
}

@media (hover: hover) and (pointer: fine) {
  .icon-btn:hover {
    color: var(--ink);
    background: var(--tray);
  }
}

.who {
  display: flex;
  align-items: center;
  gap: 8px;
  border: 0;
  background: none;
  cursor: pointer;
  padding: 3px 12px 3px 3px;
  border-radius: 999px;
  color: var(--ink-soft);
  transition: background 0.4s var(--ease), transform 0.4s var(--ease);
}

.who:active {
  transform: scale(0.97);
}

@media (hover: hover) and (pointer: fine) {
  .who:hover {
    background: var(--tray);
  }
}

.avatar {
  width: 30px;
  height: 30px;
  border-radius: 10px;
  background: var(--accent);
  color: var(--on-accent);
  font-size: 13px;
  font-weight: 600;
  display: grid;
  place-items: center;
}

.who-name {
  font-size: 14px;
  font-weight: 550;
  max-width: 110px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.caret :deep(svg) {
  width: 14px;
  height: 14px;
  display: block;
}

.menu-role {
  font-size: 12px;
  color: var(--ink-faint);
}

.main {
  flex: 1;
  min-height: 0;
  padding: 26px 28px 48px;
}

.main-fill {
  display: flex;
  flex-direction: column;
  overflow: hidden;
  height: calc(100dvh - 58px);
  padding-bottom: 20px;
}

.main-fill > * {
  flex: 1;
  min-height: 0;
}

.burger,
.scrim {
  display: none;
}

/* ---- 窄屏：侧栏变抽屉 ---- */
@media (max-width: 900px) {
  .shell-root {
    grid-template-columns: 1fr;
  }

  .rail {
    position: fixed;
    inset: 0 auto 0 0;
    width: 254px;
    z-index: var(--z-modal);
    transform: translateX(-105%);
    transition: transform 0.42s var(--ease);
    box-shadow: var(--shadow-4);
  }

  .rail.open {
    transform: none;
  }

  .scrim {
    display: block;
    position: fixed;
    inset: 0;
    z-index: var(--z-grain);
    background: rgba(34, 30, 26, 0.42);
    animation: scrim-in 0.3s var(--ease);
  }

  @keyframes scrim-in {
    from {
      opacity: 0;
    }
  }

  .burger {
    display: grid;
    gap: 5px;
    padding: 8px 4px;
    border: 0;
    background: none;
    cursor: pointer;
  }

  .burger span {
    display: block;
    width: 18px;
    height: 1.5px;
    border-radius: 2px;
    background: var(--ink);
  }

  .topbar {
    padding: 0 16px;
  }

  .main {
    padding: 18px 16px 40px;
  }

  .who-name,
  .caret {
    display: none;
  }
}
</style>
