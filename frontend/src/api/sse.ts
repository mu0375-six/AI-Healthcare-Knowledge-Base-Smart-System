import { clearSessionAndRedirect } from './http'
import { authedFetch } from '@/utils/authedFetch'

/** 回答空闲看门狗：服务端这么久一个字节都没吐，视为挂起并中止。 */
const IDLE_TIMEOUT_MS = 75_000

/** 用户主动停止（或离开页面）时抛出，调用方据此保留已生成的部分回答。 */
export class StreamAborted extends Error {
  constructor() {
    super('已停止生成')
    this.name = 'StreamAborted'
  }
}

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
  // 内部 controller 与外部 signal 联动：外部「停止」、内部看门狗超时、任一触发都走同一条中断路径
  const controller = new AbortController()
  const forwardAbort = () => controller.abort()
  signal?.addEventListener('abort', forwardAbort)

  let timedOut = false
  let watchdog: ReturnType<typeof setTimeout> | undefined
  const armWatchdog = () => {
    clearTimeout(watchdog)
    watchdog = setTimeout(() => {
      timedOut = true
      controller.abort()
    }, IDLE_TIMEOUT_MS)
  }

  try {
    const resp = await authedFetch('/api/chat/ask', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Accept: 'text/event-stream',
      },
      body: JSON.stringify(body),
      signal: controller.signal,
    })
    if (resp.status === 401) {
      clearSessionAndRedirect()
      throw new Error('未登录或登录已过期')
    }
    if (!resp.ok || !resp.body) {
      throw new Error(await errorMessage(resp))
    }
    const reader = resp.body.getReader()
    const decoder = new TextDecoder('utf-8')
    let buf = ''
    armWatchdog()
    while (true) {
      const { value, done } = await reader.read()
      if (done) break
      armWatchdog()
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
  } catch (e) {
    if (isAbortError(e)) {
      if (timedOut) throw new Error('回答超时，请稍后重试')
      throw new StreamAborted()
    }
    throw e
  } finally {
    clearTimeout(watchdog)
    signal?.removeEventListener('abort', forwardAbort)
  }
}

function isAbortError(e: unknown): boolean {
  return e instanceof DOMException && e.name === 'AbortError'
}

/** 非 2xx 时优先透出后端的业务 message（如限流文案），拿不到再给笼统提示。 */
async function errorMessage(resp: Response): Promise<string> {
  try {
    const data = await resp.json()
    if (data?.message) return String(data.message)
  } catch {
  }
  return resp.status === 429 ? '提问太频繁了，休息一下再试' : '问答服务暂时不可用'
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
