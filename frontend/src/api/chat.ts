import http from './http'
import type { ApiEnvelope, ChatMessage, ChatSession, PageResult } from './types'

export function createSession(title?: string) {
  return http.post<unknown, ApiEnvelope<ChatSession>>('/api/chat/sessions', { title })
}

/** 分页拉取会话（按 updatedAt 倒序，第 1 页=最近）。 */
export function listSessions(page = 1, size = 50) {
  return http.get<unknown, ApiEnvelope<PageResult<ChatSession>>>('/api/chat/sessions', {
    params: { page, size },
  })
}

/** 分页拉取消息：第 1 页=最新一页，records 已转回时间正序，「加载更早」往前翻。 */
export function listMessages(id: number, page = 1, size = 50) {
  return http.get<unknown, ApiEnvelope<PageResult<ChatMessage>>>(`/api/chat/sessions/${id}/messages`, {
    params: { page, size },
  })
}

export function deleteSession(id: number) {
  return http.delete<unknown, ApiEnvelope<void>>(`/api/chat/sessions/${id}`)
}

/** 重命名会话。 */
export function renameSession(id: number, title: string) {
  return http.put<unknown, ApiEnvelope<ChatSession>>(`/api/chat/sessions/${id}`, { title })
}

export function uploadChatImage(file: File) {
  const form = new FormData()
  form.append('file', file)
  return http.post<unknown, ApiEnvelope<{ id: number; filename: string; mimeType: string }>>('/api/chat/images', form)
}
