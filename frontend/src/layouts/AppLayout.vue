<template>
  <div class="shell-root">
    <a class="skip-link" href="#main">跳到主要内容</a>

    <!-- 左侧常驻栏：图标 + 文字，一屏就能看见全部去处。
         内容区因此从"窄栏居中"变成"贴着侧栏铺开"。 -->
    <aside id="primary-navigation" class="rail" :class="{ open: railOpen }">
      <router-link to="/home" class="brand" @click="railOpen = false">
        <BrandMark />
        <span class="brand-copy">
          <span class="brand-name">康识问诊</span>
          <small>Clinical workspace</small>
        </span>
      </router-link>

      <nav class="rail-nav" aria-label="主导航">
        <p class="rail-label">工作区</p>
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

        <p class="rail-label">工具</p>
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

      <div v-if="railHint" class="rail-card">
        <b>{{ railHint.title }}</b>
        <i>{{ railHint.desc }}</i>
        <router-link class="btn btn-primary btn-sm" :to="railHint.to" @click="railOpen = false">
          {{ railHint.action }}
        </router-link>
      </div>
    </aside>

    <button
      v-if="railOpen"
      class="scrim"
      type="button"
      aria-label="关闭主菜单"
      @click="railOpen = false"
    ></button>

    <div class="stage">
      <header class="topbar">
        <button
          class="burger"
          type="button"
          aria-label="打开菜单"
          aria-controls="primary-navigation"
          :aria-expanded="railOpen"
          @click="railOpen = true"
        >
          <span></span><span></span>
        </button>
        <nav class="breadcrumbs" aria-label="面包屑">
          <span class="workspace-label">健康工作台</span>
          <span class="crumb-separator" aria-hidden="true">/</span>
          <template v-for="(crumb, index) in breadcrumbs" :key="crumb.label">
            <span v-if="index" class="crumb-separator" aria-hidden="true">/</span>
            <router-link v-if="crumb.to" class="crumb-link" :to="crumb.to">{{ crumb.label }}</router-link>
            <strong v-else class="crumb-current" aria-current="page">{{ crumb.label }}</strong>
          </template>
        </nav>
        <div class="top-end">
          <router-link
            v-if="routeAction"
            class="btn btn-quiet btn-sm top-action"
            :to="routeAction.to"
            :aria-label="routeAction.label"
          >
            <span class="top-action-icon" aria-hidden="true" v-html="ICONS[routeAction.icon]"></span>
            <span class="top-action-label">{{ routeAction.label }}</span>
          </router-link>

          <el-dropdown trigger="click" @command="onThemeCommand">
            <button
              class="icon-btn"
              type="button"
              aria-haspopup="menu"
              :title="`主题：${themeModeLabel}`"
              :aria-label="`选择主题，当前为${themeModeLabel}`"
            >
              <span aria-hidden="true" v-html="themeIcon"></span>
            </button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item
                  v-for="option in THEME_OPTIONS"
                  :key="option.value"
                  :command="option.value"
                >
                  <span>{{ option.label }}</span>
                  <span v-if="themeMode === option.value" class="theme-current">当前</span>
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>

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
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter, type RouteLocationRaw } from 'vue-router'
import { useUserStore } from '@/stores/user'
import {
  applyThemeMode,
  currentTheme,
  currentThemeMode,
  type ThemeMode,
} from '@/utils/theme'
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

type Breadcrumb = { label: string; to?: RouteLocationRaw }
type RouteAction = { label: string; to: RouteLocationRaw; icon: IconName }
type RailHint = { title: string; desc: string; action: string; to: RouteLocationRaw }

const breadcrumbs = computed<Breadcrumb[]>(() => {
  const current = { label: String(route.meta.title || '') }
  const parent = route.meta.breadcrumbParent as Breadcrumb | undefined
  return parent ? [parent, current] : [current]
})

const routeAction = computed(
  () => (route.meta.topAction as RouteAction | undefined) || null,
)

const railHint = computed<RailHint | null>(() => {
  if (route.path === '/reports/upload') return null
  if (route.path === '/chat') {
    return {
      title: '图片也可以直接问',
      desc: '化验单、药盒或患处照片，发图后补一句问题即可。',
      action: '发照片',
      to: { path: '/chat', query: { ...route.query, photo: '1' } },
    }
  }
  if (route.path === '/health') {
    return {
      title: '让档案更完整',
      desc: '上传一份报告，指标和高低会自动归入档案。',
      action: '上传报告',
      to: '/reports/upload',
    }
  }
  if (route.path.startsWith('/reports/')) {
    return {
      title: '解读会留在档案里',
      desc: '回到报告列表，可继续查看同一份健康记录。',
      action: '返回档案',
      to: { path: '/health', query: { tab: 'reports' } },
    }
  }
  if (route.path === '/triage') {
    return {
      title: '症状还说不清？',
      desc: '转到问诊，可以继续补充症状、用药和图片。',
      action: '去问诊',
      to: '/chat',
    }
  }
  return {
    title: '把化验单拍下来',
    desc: '指标、高低、逐项解释会自动整理成档案。',
    action: '去解读',
    to: '/reports/upload',
  }
})

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

const THEME_OPTIONS: { value: ThemeMode; label: string }[] = [
  { value: 'light', label: '浅色' },
  { value: 'dark', label: '深色' },
  { value: 'system', label: '跟随系统' },
]
const themeMode = ref<ThemeMode>(currentThemeMode())
const effectiveTheme = ref(currentTheme())
const themeModeLabel = computed(
  () => THEME_OPTIONS.find((option) => option.value === themeMode.value)?.label || '浅色',
)
const themeIcon = computed(() =>
  effectiveTheme.value === 'dark' ? ICONS.moon : ICONS.sun,
)

// 路由一变就收起抽屉，否则返回时它还开着
watch(() => route.path, () => (railOpen.value = false))

onMounted(() => {
  user.fetchMe().catch(() => undefined)
  window.addEventListener('theme-change', syncThemeState)
})

onBeforeUnmount(() => window.removeEventListener('theme-change', syncThemeState))

function syncThemeState() {
  themeMode.value = currentThemeMode()
  effectiveTheme.value = currentTheme()
}

function onThemeCommand(command: ThemeMode) {
  applyThemeMode(command)
  syncThemeState()
}

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
  grid-template-columns: 228px minmax(0, 1fr);
  background: var(--paper);
}

/* ---- 左侧常驻栏 ---- */
.rail {
  position: sticky;
  top: 0;
  height: 100dvh;
  display: flex;
  flex-direction: column;
  padding: 18px 12px 14px;
  background: var(--nav-bg);
  border-right: 1px solid var(--nav-border);
  color: var(--nav-ink);
}

.brand {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  padding: 0 var(--space-2) 20px;
  color: var(--nav-ink);
  border-bottom: 1px solid var(--nav-border);
  transition: opacity 0.16s var(--ease-soft);
}

.brand:active {
  opacity: 0.72;
}

.brand-copy {
  min-width: 0;
  display: grid;
  gap: 1px;
}

.brand-name {
  font-family: var(--font);
  font-size: 16px;
  font-weight: 680;
  letter-spacing: 0;
}

.brand-copy small {
  color: var(--nav-mute);
  font-size: 9px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.rail-nav {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 2px;
  margin-top: var(--space-3);
  padding: 0;
}

.rail-label {
  margin: var(--space-4) var(--space-3) 6px;
  font-size: 9px;
  font-weight: 650;
  letter-spacing: 0;
  text-transform: uppercase;
  color: var(--nav-mute);
}

.rail-label:first-child {
  margin-top: var(--space-1);
}

.rail-item {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  min-height: 40px;
  padding: 8px 11px;
  border-radius: var(--r-control);
  color: var(--nav-mute);
  font-size: 13.5px;
  font-weight: 520;
  transition: background 0.16s var(--ease-soft), color 0.16s var(--ease-soft);
}

.rail-item:active {
  background: rgba(255, 255, 255, 0.13);
}

@media (hover: hover) and (pointer: fine) {
  .rail-item:hover {
    background: rgba(255, 255, 255, 0.07);
    color: var(--nav-ink);
  }
}

.rail-item.router-link-active {
  background: rgba(74, 195, 154, 0.14);
  color: #ffffff;
  box-shadow: inset 3px 0 var(--nav-active);
  font-weight: 620;
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
  margin-top: var(--space-3);
  padding: 13px;
  border-radius: var(--r-control);
  background: rgba(255, 255, 255, 0.055);
  border: 1px solid var(--nav-border);
  box-shadow: none;
}

.rail-card b {
  display: block;
  color: var(--nav-ink);
  font-size: 12.5px;
  font-weight: 650;
}

.rail-card i {
  display: block;
  font-style: normal;
  font-size: 12px;
  line-height: 1.6;
  color: var(--nav-mute);
  margin: var(--space-1) 0 var(--space-3);
}

/* ---- 主区 ---- */
.stage {
  min-width: 0;
  display: flex;
  flex-direction: column;
  background: var(--paper);
}

.topbar {
  position: sticky;
  top: 0;
  z-index: var(--z-nav);
  height: var(--topbar-h);
  display: flex;
  align-items: center;
  gap: var(--space-3);
  padding: 0 var(--main-pad);
  background: var(--chrome);
  backdrop-filter: blur(16px);
  -webkit-backdrop-filter: blur(16px);
  border-bottom: 1px solid var(--edge);
}

.breadcrumbs {
  min-width: 0;
  display: flex;
  align-items: center;
  gap: var(--space-2);
  font-size: 13px;
  color: var(--ink-mute);
  white-space: nowrap;
  overflow: hidden;
}

.workspace-label {
  color: var(--ink-faint);
  font-size: 11px;
  font-weight: 600;
  text-transform: uppercase;
}

.crumb-link {
  flex-shrink: 0;
  color: var(--ink-faint);
  transition: color 0.18s var(--ease-soft);
}

.crumb-link:hover {
  color: var(--accent);
}

.crumb-separator {
  flex-shrink: 0;
  color: var(--edge-strong);
}

.crumb-current {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  color: var(--ink);
  font-weight: 650;
}

.top-end {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: var(--space-2);
  flex-shrink: 0;
}

.top-action {
  border: 1px solid var(--edge);
  color: var(--ink-soft);
  background: var(--card);
}

.top-action-icon {
  display: grid;
  place-items: center;
}

.top-action-icon :deep(svg) {
  width: 14px;
  height: 14px;
}

.icon-btn {
  display: grid;
  place-items: center;
  width: 32px;
  height: 32px;
  border: 1px solid var(--edge);
  background: var(--card);
  color: var(--ink-mute);
  cursor: pointer;
  border-radius: var(--r-control);
  transition: color 0.16s var(--ease-soft), background 0.16s var(--ease-soft);
}

.icon-btn:active {
  background: var(--sunk);
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
  gap: var(--space-2);
  border: 1px solid var(--edge);
  background: var(--card);
  cursor: pointer;
  padding: 3px 10px 3px 3px;
  border-radius: var(--r-control);
  color: var(--ink-soft);
  transition: background 0.16s var(--ease-soft), border-color 0.16s var(--ease-soft);
}

.who:active {
  background: var(--sunk);
}

@media (hover: hover) and (pointer: fine) {
  .who:hover {
    background: var(--tray);
  }
}

.avatar {
  width: 30px;
  height: 30px;
  border-radius: 4px;
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

.theme-current {
  margin-left: var(--space-4);
  color: var(--accent);
  font-size: 11px;
}

.main {
  flex: 1;
  min-height: 0;
  padding: 26px var(--main-pad) var(--space-7);
}

.main-fill {
  display: flex;
  flex-direction: column;
  overflow: hidden;
  height: calc(100dvh - var(--topbar-h));
  padding-bottom: var(--space-5);
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
    width: auto;
    height: auto;
    padding: 0;
    border: 0;
    background: color-mix(in srgb, var(--ink) 42%, transparent);
    animation: scrim-in 0.3s var(--ease);
  }

  @keyframes scrim-in {
    from {
      opacity: 0;
    }
  }

  .burger {
    display: grid;
    gap: var(--space-1);
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
    padding: 0 var(--space-4);
  }

  .main {
    padding: var(--space-4) var(--space-4) var(--space-6);
  }

  .who-name,
  .caret {
    display: none;
  }
}

@media (max-width: 520px) {
  .top-action {
    width: 34px;
    height: 34px;
    padding: 0;
  }

  .top-action-label {
    position: absolute;
    width: 1px;
    height: 1px;
    padding: 0;
    margin: -1px;
    overflow: hidden;
    clip: rect(0, 0, 0, 0);
    white-space: nowrap;
    border: 0;
  }
}

:global(html.dark) .scrim {
  background: color-mix(in srgb, var(--paper) 72%, transparent);
}
</style>
