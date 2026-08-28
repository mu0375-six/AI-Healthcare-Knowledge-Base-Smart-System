import { onBeforeUnmount, ref, type Ref } from 'vue'
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
  /** 当前流式请求的中止器：停止按钮与离开页面都靠它断开 SSE。 */
  let activeStream: AbortController | null = null

  function stop() {
    activeStream?.abort()
  }

  onBeforeUnmount(() => {
    // 离开页面不再让流在后台空跑：断开后 sse.ts 抛 StreamAborted，这里已无人接，无害
    activeStream?.abort()
  })

  async function send(question: string, files: File[], profileId: number | null) {
    const hasFiles = files.length > 0
    if ((!question && !hasFiles) || streaming.value) return
    const display = question || '请看我发的图片，结合健康知识帮我解读。'
    const sid = await ensureSession(hasFiles ? '图片问诊' : display.slice(0, 24))

    messages.value.push({
      id: -Date.now(),
      sessionId: sid,
      userId: 0,
      role: 'user',
      content: display,
      localPreviews: hasFiles ? files.map((f) => URL.createObjectURL(f)) : [],
      createdAt: '',
    })
    const bot: ChatMessage = {
      id: -Date.now() - 1,
      sessionId: sid,
      userId: 0,
      role: 'assistant',
      content: '',
      createdAt: '',
    }
    messages.value.push(bot)
    streaming.value = true
    try {
      const imageIds: number[] = []
      for (const file of files) {
        const up = await uploadChatImage(file)
        imageIds.push(up.data.id)
      }
      activeStream = new AbortController()
      await askStream(
        { sessionId: sid, question: display, imageIds, profileId },
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
          },
          onError: (msg) => ElMessage.error(msg),
        },
        activeStream.signal,
      )
      await onFinished()
    } catch (e) {
      // 主动停止：保留已生成的部分回答即可，不算错误；其余失败兜底提示
      if (e instanceof StreamAborted) {
        bot.content = bot.content || '已停止生成。'
      } else {
        bot.content = bot.content || '回答中断，请重试。'
      }
    } finally {
      activeStream = null
      streaming.value = false
    }
  }

  return { streaming, send, stop }
}

