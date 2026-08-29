export function formatWhen(s?: string | null) {
  if (!s) return ''
  return s.replace('T', ' ').replace(/\.\d+$/, '').slice(0, 16)
}

export function initial(name?: string | null) {
  const t = (name || '').trim()
  return t ? t.slice(0, 1) : '?'
}
