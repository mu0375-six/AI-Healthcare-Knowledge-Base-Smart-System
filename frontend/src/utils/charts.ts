import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { BarChart, LineChart, PieChart } from 'echarts/charts'
import {
  GridComponent,
  TooltipComponent,
  LegendComponent,
  MarkAreaComponent,
  MarkLineComponent,
} from 'echarts/components'

/**
 * 图表调色板。canvas 里读不到 CSS 变量，只能给字面值 ——
 * 这里的值必须与 styles/index.css 的 token 手工保持一致：
 * 首色 = --accent，其余取自数据色与其邻近色相。
 */
export const CHART_COLORS = ['#16745b', '#3267c8', '#b8422d', '#3f7f8d', '#74689b']
export const CHART_COLORS_DARK = ['#4ac39a', '#7fa9f5', '#e08a5f', '#70b8c4', '#ab9bd2']

/**
 * 依当前主题返回 canvas 内不可用 CSS 的文字/轴线配色。
 * 暗色切换后组件应重建 option（监听 utils/theme 派发的 'theme-change'）。
 */
export function chartTheme() {
  const dark = document.documentElement.classList.contains('dark')
  return {
    colors: dark ? CHART_COLORS_DARK : CHART_COLORS,
    // 与 --ink-soft / --ink-mute 对齐
    label: dark ? '#c1cec9' : '#42514b',
    axisLine: { lineStyle: { color: dark ? 'rgba(238, 245, 242, 0.16)' : 'rgba(23, 33, 29, 0.17)' } },
    splitLine: {
      lineStyle: { color: dark ? 'rgba(238, 245, 242, 0.08)' : 'rgba(23, 33, 29, 0.07)' },
    },
    normalBand: dark ? 'rgba(74, 195, 154, 0.14)' : 'rgba(35, 117, 87, 0.10)',
    normalLine: dark ? 'rgba(74, 195, 154, 0.44)' : 'rgba(35, 117, 87, 0.42)',
  }
}

let installed = false

/** ECharts 按需注册只做一次；各视图 import 本模块即完成注册。 */
export function ensureCharts() {
  if (installed) return
  use([
    CanvasRenderer,
    LineChart,
    BarChart,
    PieChart,
    GridComponent,
    TooltipComponent,
    LegendComponent,
    MarkAreaComponent,
    MarkLineComponent,
  ])
  installed = true
}

ensureCharts()
