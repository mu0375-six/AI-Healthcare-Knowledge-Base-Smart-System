import { onBeforeUnmount, ref, watch, type Ref } from 'vue'
import { ElMessage } from 'element-plus'
import { uploadChatImage } from '@/api/chat'
import { askStream, StreamAborted } from '@/api/sse'
import type { ChatMessage } from '@/api/types'

/**
 * 问答流式状态机：发送（含图片上传）、停止、离开页面自动断流。
 * 会话创建与列表刷新属于视图职责，通过回调注入。
 */
export function useChatStream(
  messages: Ref<ChatMessage[]>,
  ensureSession: (firstTitle: string) => Promise<number>,
  onFinished: () => void | Promise<void>,
) {
  const streaming = ref(false)
  const announcement = ref('')
  /** 当前流式请求的中止器：停止按钮与离开页面都靠它断开 SSE。 */
  let activeStream: AbortController | null = null
  let disposed = false
  let stopRequested = false
  const ownedPreviewUrls = new Set<string>()

  function createPreview(file: File) {
    const url = URL.createObjectURL(file)
    ownedPreviewUrls.add(url)
    return url
  }

  function revokePreview(url: string) {
    if (!ownedPreviewUrls.delete(url)) return
    URL.revokeObjectURL(url)
  }

  function releaseMessagePreviews(message: ChatMessage) {
    for (const url of message.localPreviews || []) revokePreview(url)
    message.localPreviews = undefined
  }

  function releaseAllPreviews() {
    for (const url of ownedPreviewUrls) URL.revokeObjectURL(url)
    ownedPreviewUrls.clear()
  }

  // Chat.vue replaces the array when opening or clearing a session. Reclaim URLs
  // that no longer belong to any rendered message at that boundary.
  watch(messages, (current) => {
    const live = new Set(current.flatMap((message) => message.localPreviews || []))
    for (const url of [...ownedPreviewUrls]) {
      if (!live.has(url)) revokePreview(url)
    }
  })

  function stop() {
    stopRequested = true
    activeStream?.abort()
  }

  onBeforeUnmount(() => {
    disposed = true
    // 离开页面不再让流在后台空跑：断开后 sse.ts 抛 StreamAborted，这里已无人接，无害
    activeStream?.abort()
    releaseAllPreviews()
  })

  async function send(question: string, files: File[], profileId: number | null) {
    const hasFiles = files.length > 0
    if ((!question && !hasFiles) || streaming.value) return false
    const display = question || '请看我发的图片，结合健康知识帮我解读。'
    stopRequested = false
    streaming.value = true
    announcement.value = '正在准备问诊'
    let sid: number
    try {
      sid = await ensureSession(hasFiles ? '图片问诊' : display.slice(0, 24))
    } catch {
      streaming.value = false
      if (disposed) return false
      announcement.value = '问诊会话创建失败，输入内容已保留'
      ElMessage.error('暂时无法创建问诊会话，输入内容已保留')
      return false
    }
    if (disposed) {
      streaming.value = false
      return false
    }
    if (stopRequested) {
      streaming.value = false
      announcement.value = '已停止回答，输入内容已保留'
      return false
    }

    const userMessage: ChatMessage = {
      id: -Date.now(),
      sessionId: sid,
      userId: 0,
      role: 'user',
      content: display,
      localPreviews: hasFiles ? files.map(createPreview) : [],
      createdAt: '',
    }
    messages.value.push(userMessage)
    const bot: ChatMessage = {
      id: -Date.now() - 1,
      sessionId: sid,
      userId: 0,
      role: 'assistant',
      content: '',
      createdAt: '',
    }
    messages.value.push(bot)
    announcement.value = '正在回答'
    let answerCompleted = false
    try {
      const attachments: { id: number; filename: string; mimeType: string }[] = []
      for (const file of files) {
        const up = await uploadChatImage(file)
        attachments.push(up.data)
        if (disposed) {
          releaseMessagePreviews(userMessage)
          return true
        }
        if (stopRequested) {
          bot.content = '已停止生成。'
          announcement.value = '已停止回答'
          return true
        }
      }
      if (attachments.length) {
        userMessage.attachmentsJson = JSON.stringify(attachments)
        releaseMessagePreviews(userMessage)
      }
      if (disposed) return true
      if (stopRequested) {
        bot.content = '已停止生成。'
        announcement.value = '已停止回答'
        return true
      }
      activeStream = new AbortController()
      await askStream(
        { sessionId: sid, question: display, imageIds: attachments.map((item) => item.id), profileId },
        {
          onMeta: (meta) => {
            bot.id = meta.messageId
          },
          onDelta: (chunk) => {
            bot.content += chunk
          },
          onDone: (done) => {
            bot.id = done.messageId
            bot.content = done.fullContent
            answerCompleted = true
            announcement.value = `回答完成。${done.fullContent}`
          },
          onError: (msg) => ElMessage.error(msg),
        },
        activeStream.signal,
      )
      await onFinished()
    } catch (e) {
      // 主动停止：保留已生成的部分回答即可，不算错误；其余失败兜底提示
      // Session-list refresh failures must not turn a completed answer into an error.
      if (!answerCompleted) {
        if (e instanceof StreamAborted) {
          bot.content = bot.content || '已停止生成。'
          announcement.value = '已停止回答'
        } else {
          bot.content = bot.content || '回答中断，请重试。'
          announcement.value = '回答中断，请重试'
        }
      }
    } finally {
      activeStream = null
      streaming.value = false
    }
    return true
  }

  return { streaming, announcement, send, stop }
}

