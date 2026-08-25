const BANDS: Record<string, { low: number; high: number; unit: string }> = {
  收缩压: { low: 90, high: 139, unit: 'mmHg' },
  舒张压: { low: 60, high: 89, unit: 'mmHg' },
  空腹血糖: { low: 3.9, high: 6.1, unit: 'mmol/L' },
  餐后血糖: { low: 0, high: 7.8, unit: 'mmol/L' },
  糖化血红蛋白: { low: 4, high: 6, unit: '%' },
  体重: { low: 40, high: 120, unit: 'kg' },
}

export const CARD_TYPES = ['收缩压', '舒张压', '空腹血糖', '体重'] as const

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
  return ({ high: '偏高', low: '偏低', normal: '正常', unknown: '待看' } as Record<string, string>)[flag] || flag
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
