import http from './http'
import type { ApiEnvelope, HealthHistory, HealthMetric, HealthProfile } from './types'

export function listProfiles() {
  return http.get<unknown, ApiEnvelope<HealthProfile[]>>('/api/health/profiles')
}

export function createProfile(payload: Partial<HealthProfile>) {
  return http.post<unknown, ApiEnvelope<HealthProfile>>('/api/health/profiles', payload)
}

export function updateProfileById(id: number, payload: Partial<HealthProfile>) {
  return http.put<unknown, ApiEnvelope<HealthProfile>>(`/api/health/profiles/${id}`, payload)
}

export function deleteProfile(id: number) {
  return http.delete<unknown, ApiEnvelope<void>>(`/api/health/profiles/${id}`)
}

export function listMetrics(profileId?: number) {
  return http.get<unknown, ApiEnvelope<HealthMetric[]>>('/api/health/metrics', { params: { profileId } })
}

export function addMetric(payload: Partial<HealthMetric> & { profileId?: number }) {
  return http.post<unknown, ApiEnvelope<HealthMetric>>('/api/health/metrics', payload)
}

/** CSV 等场景的批量写入，单次上限 500 条；返回成功条数。 */
export function addMetricsBatch(
  profileId: number,
  items: Array<{ metricType: string; value: number; unit?: string; recordedAt?: string; note?: string }>,
) {
  return http.post<unknown, ApiEnvelope<number>>('/api/health/metrics/batch', { profileId, items })
}


export interface MetricAlertItem {
  metricId: number
  profileId: number | null
  profileName: string
  metricType: string
  latestValue: number
  unit: string
  flag: 'high' | 'low'
  consecutiveAbnormal: number
  samples: number
  refRange: string
  recordedAt: string
  severity: 'warning' | 'watch'
}

/** 全档案异常提醒：连续超标（warning）+ 待观察（watch）两级。 */
export function listAlerts() {
  return http.get<unknown, ApiEnvelope<MetricAlertItem[]>>('/api/health/alerts')
}

export function deleteMetric(id: number) {
  return http.delete<unknown, ApiEnvelope<void>>(`/api/health/metrics/${id}`)
}

export function listHistories(profileId?: number) {
  return http.get<unknown, ApiEnvelope<HealthHistory[]>>('/api/health/histories', { params: { profileId } })
}

export function addHistory(payload: Partial<HealthHistory> & { profileId?: number }) {
  return http.post<unknown, ApiEnvelope<HealthHistory>>('/api/health/histories', payload)
}

export function deleteHistory(id: number) {
  return http.delete<unknown, ApiEnvelope<void>>(`/api/health/histories/${id}`)
}

export interface MetricTrend {
  metricType: string
  unit: string
  samples: number
  latest: number
  latestFlag: 'low' | 'normal' | 'high' | 'unknown'
  latestFlagText: string
  consecutiveAbnormal: number
  alert: boolean
  direction: 'rising' | 'falling' | 'flat' | 'unknown'
  note: string
}

export function listTrends(profileId?: number) {
  return http.get<unknown, ApiEnvelope<MetricTrend[]>>('/api/health/trends', { params: { profileId } })
}

export function generateAdvice(profileId?: number) {
  return http.post<unknown, ApiEnvelope<{ advice: string; basis?: string; generatedAt?: string }>>(
    '/api/health/advice',
    null,
    { params: { profileId }, timeout: 120000 },
  )
}
