import http from './http'
import type { ApiEnvelope, TriageResult } from './types'

export function runTriage(payload: { symptoms: string; age?: number; sex?: string }) {
  return http.post<unknown, ApiEnvelope<TriageResult>>('/api/triage', payload)
}

export interface NearbyPoi {
  name: string
  address?: string
  tel?: string
  /** 距用户坐标直线距离，米 */
  distanceMeters?: number | null
  typeLabel?: string
}

export interface NearbyResult {
  locationLabel: string
  /** llm = 模型生成的推荐说明；template = 兜底文案 */
  adviceSource: 'llm' | 'template'
  advice: string
  hospitals: NearbyPoi[]
  pharmacies: NearbyPoi[]
}

export interface SavedLocation {
  addressText?: string
  lng?: number
  lat?: number
  savedAt?: string
}

export function nearbyMedical(payload: {
  symptoms?: string
  department?: string
  urgency?: string
  lng?: number
  lat?: number
  address?: string
  save?: boolean
}) {
  return http.post<unknown, ApiEnvelope<NearbyResult>>('/api/triage/nearby', payload)
}

export function getSavedLocation() {
  return http.get<unknown, ApiEnvelope<SavedLocation | null>>('/api/triage/location')
}

export function clearSavedLocation() {
  return http.delete<unknown, ApiEnvelope<void>>('/api/triage/location')
}
