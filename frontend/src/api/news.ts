import http from './http'
import type { ApiEnvelope } from './types'
import { authedFetch } from '@/utils/authedFetch'

export interface NewsListItem {
  id: number
  title: string
  summary?: string
  sourceName?: string
  category?: string
  publishedOn?: string
  /** 是否有配图（走 /api/news/{id}/image 取 blob） */
  image: boolean
}

export interface NewsDetail {
  id: number
  title: string
  summary?: string
  /** 段落间以空行分隔的纯文本 */
  content?: string
  sourceName?: string
  sourceUrl?: string
  category?: string
  publishedOn?: string
  image: boolean
}

export function listNews(limit = 12) {
  return http.get<unknown, ApiEnvelope<NewsListItem[]>>('/api/news', { params: { limit } })
}

export function getNews(id: number | string) {
  return http.get<unknown, ApiEnvelope<NewsDetail>>(`/api/news/${id}`)
}

/** 配图和聊天图片一样走鉴权取 blob，不热链外站。 */
export function fetchNewsImage(id: number | string) {
  return authedFetch(`/api/news/${id}/image`)
}
