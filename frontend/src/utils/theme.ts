/**
 * 主题切换：html 上挂 .dark 类（Element Plus 暗色变量按 html.dark 生效），
 * 自绘 token 在 styles/index.css 的 html.dark 块覆盖。
 * 切换时派发 window 事件 'theme-change'，图表等 canvas 组件监听重建配色。
 */
// 键名带版本号：默认基调从深色改为浅色时，旧的 'theme' 值会让老用户
// 刷新后仍停在深色、看起来像没生效。换键 = 新默认生效一次，
// 之后用户自己的切换照常记住。
const KEY = 'theme.v2'

export type Theme = 'light' | 'dark'

export function currentTheme(): Theme {
  return document.documentElement.classList.contains('dark') ? 'dark' : 'light'
}

export function applyTheme(theme: Theme) {
  document.documentElement.classList.toggle('dark', theme === 'dark')
  localStorage.setItem(KEY, theme)
  window.dispatchEvent(new Event('theme-change'))
}

export function initTheme() {
  const saved = localStorage.getItem(KEY)
  // 没有用户偏好时默认浅色：医疗健康类产品的基调是干净、清晰、可信，
  // 浅色更贴合「体检报告 / 化验单」这类纸质语境；深色作为可切换的备选。
  // 注意这里不跟随 prefers-color-scheme：产品有明确的默认基调主张。
  const dark = saved ? saved === 'dark' : false
  document.documentElement.classList.toggle('dark', dark)
}

export function toggleTheme(): Theme {
  const next: Theme = currentTheme() === 'dark' ? 'light' : 'dark'
  applyTheme(next)
  return next
}
