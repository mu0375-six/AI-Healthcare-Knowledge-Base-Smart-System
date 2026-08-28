import http from '@/api/http'
import type { ApiEnvelope } from '@/api/types'

/**
 * 指标参考区间。唯一权威源是后端 MetricGuide（GET /api/health/reference），
 * 这里的本地表只作接口失败时的兜底 —— 口径以后端为准，不要在此新增指标。
 */
const FALLBACK_BANDS: Record<string, { low: number; high: number; unit: string }> = {
  收缩压: { low: 90, high: 139, unit: 'mmHg' },
  舒张压: { low: 60, high: 89, unit: 'mmHg' },
  空腹血糖: { low: 3.9, high: 6.1, unit: 'mmol/L' },
  餐后血糖: { low: 0, high: 7.8, unit: 'mmol/L' },
  糖化血红蛋白: { low: 4, high: 6, unit: '%' },
  体重: { low: 40, high: 120, unit: 'kg' },
}

let BANDS: Record<string, { low: number; high: number; unit: string }> = { ...FALLBACK_BANDS }
let loaded = false

/** 登录后调用一次：拉取后端参考区间覆盖本地兜底；失败静默沿用兜底值。 */
export async function loadReference() {
  if (loaded) return
  loaded = true
  try {
    const res = await http.get<unknown, ApiEnvelope<Array<{ type: string; low: number; high: number; unit: string }>>>(
      '/api/health/reference',
    )
    if (Array.isArray(res.data) && res.data.length) {
      const next: typeof BANDS = {}
      for (const r of res.data) {
        next[r.type] = { low: r.low, high: r.high, unit: r.unit }
      }
      BANDS = next
      listeners.forEach((fn) => fn())
    }
  } catch {
    // 未登录 / 后端未升级时保持内置表，页面照常工作
  }
}

const listeners: Array<() => void> = []

/** 参考区间到达后会整体替换，订阅以重建已渲染的判断结果。 */
export function onReferenceLoaded(fn: () => void) {
  listeners.push(fn)
}

export const CARD_TYPES = ['收缩压', '舒张压', '空腹血糖', '体重'] as const

export function bandOf(type: string) {
  return BANDS[type]
}

export function knownTypes() {
  return Object.keys(BANDS)
}

export function unitOf(type: string) {
  return BANDS[type]?.unit || ''
}

export function flagOf(type: string, value?: number | null) {
  const b = BANDS[type]
  if (!b || value == null || Number.isNaN(value)) return 'unknown'
  if (value < b.low) return 'low'
  if (value > b.high) return 'high'
  return 'normal'
}

export function flagText(flag: string) {
  return ({ high: '偏高', low: '偏低', normal: '正常', unknown: '未知' } as Record<string, string>)[flag] || flag
}

export function refText(type: string) {
  const b = BANDS[type]
  return b ? `${b.low}–${b.high} ${b.unit}` : ''
}

export function bmiOf(heightCm?: number | null, weightKg?: number | null) {
  if (!heightCm || !weightKg || heightCm <= 0) return null
  return weightKg / Math.pow(heightCm / 100, 2)
}

export function bmiLabel(bmi: number | null) {
  if (bmi == null) return ''
  if (bmi < 18.5) return '偏瘦'
  if (bmi < 24) return '正常'
  if (bmi < 28) return '超重'
  return '肥胖'
}
