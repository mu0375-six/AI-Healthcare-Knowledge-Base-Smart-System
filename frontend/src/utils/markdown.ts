import MarkdownIt from 'markdown-it'
import DOMPurify from 'dompurify'
import katex from 'katex'
import 'katex/dist/katex.min.css'

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

/** HTML 实体模式：&lt; &amp; &#39; 等。术语替换必须绕开它们，否则会把实体内部字符改坏。 */
const ENTITY = /&[a-zA-Z#0-9]+;/

function highlightTerms(html: string, terms: string[]): string {
  const sorted = [...terms].filter((t) => t && t.length >= 2).sort((a, b) => b.length - a.length)
  if (!sorted.length) return html
  // 单轮替换：多轮顺序替换会把上一轮刚插进去的 <span class="med-term"> 标签
  // 本身卷进下一轮匹配。合并为一条 alternation（长词在前保证优先级）一次完成。
  const pattern = new RegExp(`(${sorted.map(escapeReg).join('|')})`, 'g')
  return html.replace(/(<[^>]+>)|([^<]+)/g, (_full, tag: string, txt: string) => {
    if (tag) return tag
    // 文本段再按实体切开，只在非实体片段里替换，防止「ALT」命中 &lt; 里的 lt
    return txt
      .split(new RegExp(`(${ENTITY.source})`, 'g'))
      .map((piece) => (ENTITY.test(piece) ? piece : piece.replace(pattern, '<span class="med-term">$&</span>')))
      .join('')
  })
}

export function renderMarkdown(text: string, terms: string[] = []): string {
  const html = md.render(text || '')
  const withMath = html.replace(/(<[^>]+>)|([^<]+)/g, (_full, tag: string, txt: string) => {
    if (tag) return tag
    return applyMath(txt)
  })
  const highlighted = terms.length ? highlightTerms(withMath, terms) : withMath
  // 纵深防御：当前 html:false + KaTeX 默认配置已挡住注入，这一层防的是
  // 将来有人放开 html 选项或新增直出 HTML 的插件时把风险兜住。
  return DOMPurify.sanitize(highlighted, { ADD_ATTR: ['target'] })
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
