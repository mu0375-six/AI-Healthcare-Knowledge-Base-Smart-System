import MarkdownIt from 'markdown-it'
import katex from 'katex'

const md = new MarkdownIt({
  html: false,
  linkify: true,
  breaks: true,
})

function renderKatex(src: string, display: boolean): string {
  try {
    return katex.renderToString(src, { displayMode: display, throwOnError: false })
  } catch {
    return src
  }
}

function applyMath(src: string): string {
  let out = src.replace(/\$\$([\s\S]+?)\$\$/g, (_, expr: string) => renderKatex(expr, true))
  out = out.replace(/\$([^$\n]+?)\$/g, (_, expr: string) => renderKatex(expr, false))
  return out
}

function escapeReg(s: string): string {
  return s.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
}

export function renderMarkdown(text: string, terms: string[] = []): string {
  const html = md.render(text || '')
  const withMath = html.replace(/(<[^>]+>)|([^<]+)/g, (_full, tag: string, txt: string) => {
    if (tag) return tag
    return applyMath(txt)
  })
  if (!terms.length) return withMath
  const sorted = [...terms].filter((t) => t && t.length >= 2).sort((a, b) => b.length - a.length)
  return withMath.replace(/(<[^>]+>)|([^<]+)/g, (_full, tag: string, txt: string) => {
    if (tag) return tag
    let s = txt
    for (const term of sorted) {
      s = s.replace(new RegExp(escapeReg(term), 'g'), '<span class="med-term">$&</span>')
    }
    return s
  })
}

const CITE_STOP = new Set([
  '最近', '有点', '并且', '怎么', '什么', '哪些', '一下', '还是', '比较', '感觉',
  '注意', '事项', '这个', '一个', '可以', '需要', '如果', '或者', '以及', '进行',
  '出现', '相关', '问题', '情况', '时候', '之后', '之前', '我们', '自己', '怎么办',
  '注意事项', '请问', '想问', '咨询', '了解', '是否', '怎样', '为何',
  '因为', '所以', '但是', '然后', '现在', '今天', '昨天', '有些', '一点',
])

export function questionTerms(query: string): string[] {
  if (!query) return []
  let remaining = query.replace(/[^\u4e00-\u9fffA-Za-z0-9]+/g, ' ')
  for (const s of CITE_STOP) remaining = remaining.split(s).join(' ')
  const terms = new Set<string>()
  for (const part of remaining.split(/\s+/)) {
    if (part.length < 2 || CITE_STOP.has(part)) continue
    terms.add(part)
    if (part.length >= 4) {
      for (let n = 2; n <= 3; n++) {
        for (let i = 0; i + n <= part.length; i++) terms.add(part.slice(i, i + n))
      }
    }
  }
  return [...terms]
}

export function citationFitsQuestion(
  citation: { title?: string; snippet?: string },
  question: string,
): boolean {
  const blob = `${citation.title || ''}\n${citation.snippet || ''}`
  if (!blob.trim()) return false
  return questionTerms(question).some((t) => t.length >= 2 && blob.includes(t))
}

export function parseAttachments(raw?: string | null): { id: number; filename: string; mimeType?: string }[] {
  if (!raw) return []
  try {
    const v = JSON.parse(raw)
    return Array.isArray(v) ? v : []
  } catch {
    return []
  }
}


