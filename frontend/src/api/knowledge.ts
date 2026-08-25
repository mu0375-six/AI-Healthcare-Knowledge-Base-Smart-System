import http from './http'
import type { ApiEnvelope, KbDocument } from './types'

export function listKnowledge() {
  return http.get<unknown, ApiEnvelope<KbDocument[]>>('/api/admin/knowledge')
}

export function addKnowledgeText(payload: { title: string; category?: string; content: string; source?: string }) {
  return http.post<unknown, ApiEnvelope<KbDocument>>('/api/admin/knowledge/text', payload)
}

export function syncOfficialKnowledge(replaceDemo = true) {
  return http.post<unknown, ApiEnvelope<{
    fetched: number
    fromSnapshot: number
    skipped: number
    failed: number
    removedDemo: number
    titles: string[]
    errors: string[]
  }>>('/api/admin/knowledge/sync-official', null, { params: { replaceDemo }, timeout: 180000 })
}

export function uploadKnowledge(file: File, extra?: { title?: string; category?: string; source?: string; extractedText?: string }) {
  const form = new FormData()
  form.append('file', file)
  if (extra?.title) form.append('title', extra.title)
  if (extra?.category) form.append('category', extra.category)
  if (extra?.source) form.append('source', extra.source)
  if (extra?.extractedText) form.append('extractedText', extra.extractedText)
  return http.post<unknown, ApiEnvelope<KbDocument>>('/api/admin/knowledge/upload', form, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}

export function deleteKnowledge(id: number) {
  return http.delete<unknown, ApiEnvelope<void>>(`/api/admin/knowledge/${id}`)
}

export function searchKnowledge(q: string) {
  return http.get<unknown, ApiEnvelope<Array<{ title: string; category: string; source: string; snippet: string; score: number }>>>(
    '/api/knowledge/search',
    { params: { q } },
  )
}

export function listTerms() {
  return http.get<unknown, ApiEnvelope<string[]>>('/api/knowledge/terms')
}

export interface VectorStoreInfo {
  backend: string
  connected: boolean
  count: number
  dim: number
  collection: string
  detail: string
}

export interface VectorHit {
  chunkId: number
  documentId: number
  title: string
  category: string
  source: string
  snippet: string
  score: number
  lexicalHit: boolean
}

export interface VectorInspect {
  query: string
  elapsedMs: number
  dim: number
  store: VectorStoreInfo
  rawHits: VectorHit[]
  keptHits: VectorHit[]
}

export function vectorStatus() {
  return http.get<unknown, ApiEnvelope<VectorStoreInfo>>('/api/knowledge/vectors/status')
}

export function inspectVectors(q: string) {
  return http.get<unknown, ApiEnvelope<VectorInspect>>('/api/knowledge/vectors/inspect', { params: { q } })
}

export function reindexVectors() {
  return http.post<unknown, ApiEnvelope<{ count: number; store: VectorStoreInfo }>>('/api/admin/knowledge/reindex')
}
