import http from './http'
import type { ApiEnvelope, ChatMessage, ChatSession } from './types'

export function createSession(title?: string) {
  return http.post<unknown, ApiEnvelope<ChatSession>>('/api/chat/sessions', { title })
}

export function listSessions() {
  return http.get<unknown, ApiEnvelope<ChatSession[]>>('/api/chat/sessions')
}

export function listMessages(id: number) {
  return http.get<unknown, ApiEnvelope<ChatMessage[]>>(`/api/chat/sessions/${id}/messages`)
}

export function deleteSession(id: number) {
  return http.delete<unknown, ApiEnvelope<void>>(`/api/chat/sessions/${id}`)
}

export function uploadChatImage(file: File) {
  const form = new FormData()
  form.append('file', file)
  return http.post<unknown, ApiEnvelope<{ id: number; filename: string; mimeType: string }>>('/api/chat/images', form)
}
