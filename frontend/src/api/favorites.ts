import http from './http'
import type { ApiEnvelope } from './types'

export interface FavoriteItem {
  id: number
  messageId: number
  createdAt: string
  content: string
  sessionId?: number
}

export function addFavorite(messageId: number) {
  return http.post<unknown, ApiEnvelope<{ id: number }>>('/api/favorites', { messageId })
}

export function deleteFavorite(id: number) {
  return http.delete<unknown, ApiEnvelope<void>>(`/api/favorites/${id}`)
}

export function listFavorites() {
  return http.get<unknown, ApiEnvelope<FavoriteItem[]>>('/api/favorites')
}
