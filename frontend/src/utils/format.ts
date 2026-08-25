export function formatWhen(s?: string | null) {
  if (!s) return ''
  return s.replace('T', ' ').replace(/\.\d+$/, '').slice(0, 16)
}

export function greeting() {
  const h = new Date().getHours()
  if (h < 5) return '夜深了'
  if (h < 11) return '早上好'
  if (h < 14) return '中午好'
  if (h < 18) return '下午好'
  return '晚上好'
}

export function initial(name?: string | null) {
  const t = (name || '').trim()
  return t ? t.slice(0, 1) : '?'
}

export function clip(s?: string | null, n = 80) {
  const t = (s || '').replace(/\s+/g, ' ').trim()
  return t.length > n ? t.slice(0, n) + '…' : t
}
