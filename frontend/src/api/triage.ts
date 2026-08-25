import http from './http'
import type { ApiEnvelope, TriageResult } from './types'

export function runTriage(payload: { symptoms: string; age?: number; sex?: string }) {
  return http.post<unknown, ApiEnvelope<TriageResult>>('/api/triage', payload)
}
