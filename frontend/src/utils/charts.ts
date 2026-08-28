import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { BarChart, LineChart, PieChart } from 'echarts/charts'
import { GridComponent, TooltipComponent, LegendComponent } from 'echarts/components'

/**
 * 图表调色板。canvas 里读不到 CSS 变量，只能给字面值 ——
 * 这里的值必须与 styles/index.css 的 token 手工保持一致：
 * 首色 = --accent，其余取自数据色与其邻近色相。
 */
export const CHART_COLORS = ['#40614b', '#9c3b18', '#2f5d7c', '#8a6d3b', '#6b7f5e']

/**
 * 依当前主题返回 canvas 内不可用 CSS 的文字/轴线配色。
 * 暗色切换后组件应重建 option（监听 utils/theme 派发的 'theme-change'）。
 */
export function chartTheme() {
  const dark = document.documentElement.classList.contains('dark')
  return {
    // 与 --ink-soft / --ink-mute 对齐
    label: dark ? '#b5aa9c' : '#564e45',
    axisLine: { lineStyle: { color: dark ? '#37302a' : '#ddd5c9' } },
    splitLine: {
      lineStyle: { color: dark ? 'rgba(255, 255, 255, 0.07)' : 'rgba(34, 30, 26, 0.07)' },
    },
  }
}

let installed = false

/** ECharts 按需注册只做一次；各视图 import 本模块即完成注册。 */
export function ensureCharts() {
  if (installed) return
  use([CanvasRenderer, LineChart, BarChart, PieChart, GridComponent, TooltipComponent, LegendComponent])
  installed = true
}

ensureCharts()
