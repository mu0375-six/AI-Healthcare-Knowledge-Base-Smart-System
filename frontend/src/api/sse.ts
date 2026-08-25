import { clearSessionAndRedirect } from './http'

export interface AskHandlers {
  onMeta?: (data: { messageId: number; sessionId: number }) => void
  onDelta?: (content: string) => void
  onDone?: (data: { messageId: number; fullContent: string }) => void
  onError?: (message: string) => void
}

export async function askStream(
  body: { sessionId?: number | null; question: string; imageIds?: number[]; profileId?: number | null },
  handlers: AskHandlers,
  signal?: AbortSignal,
) {
  const token = localStorage.getItem('token') || ''
  const resp = await fetch('/api/chat/ask', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${token}`,
      Accept: 'text/event-stream',
    },
    body: JSON.stringify(body),
    signal,
  })
  if (resp.status === 401) {
    clearSessionAndRedirect()
    throw new Error('未登录或登录已过期')
  }
  if (!resp.ok || !resp.body) {
    throw new Error('问答服务暂时不可用')
  }
  const reader = resp.body.getReader()
  const decoder = new TextDecoder('utf-8')
  let buf = ''
  while (true) {
    const { value, done } = await reader.read()
    if (done) break
    buf += decoder.decode(value, { stream: true })
    const parts = buf.split('\n\n')
    buf = parts.pop() || ''
    for (const block of parts) {
      dispatchBlock(block, handlers)
    }
  }
  if (buf.trim()) {
    dispatchBlock(buf, handlers)
  }
}

function dispatchBlock(block: string, handlers: AskHandlers) {
  let event = 'message'
  const dataLines: string[] = []
  for (const raw of block.split('\n')) {
    if (raw.startsWith('event:')) {
      event = raw.slice(6).trim()
    } else if (raw.startsWith('data:')) {
      dataLines.push(raw.slice(5).trim())
    }
  }
  if (!dataLines.length) return
  let payload: unknown = dataLines.join('\n')
  try {
    payload = JSON.parse(dataLines.join('\n'))
  } catch {
  }
  if (event === 'meta') handlers.onMeta?.(payload as { messageId: number; sessionId: number })
  else if (event === 'delta') {
    const obj = payload as { content?: string }
    handlers.onDelta?.(typeof payload === 'string' ? payload : obj.content || '')
  } else if (event === 'done') {
    handlers.onDone?.(payload as { messageId: number; fullContent: string })
  } else if (event === 'error') {
    const obj = payload as { message?: string }
    handlers.onError?.(obj.message || '生成失败')
  }
}
