import http from './http'
import type { ApiEnvelope, PageResult } from './types'

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

export function listFavorites(page = 1, size = 20) {
  return http.get<unknown, ApiEnvelope<PageResult<FavoriteItem>>>('/api/favorites', {
    params: { page, size },
  })
}
