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

export function getProfile() {
  return http.get<unknown, ApiEnvelope<HealthProfile>>('/api/health/profile')
}

export function updateProfile(payload: Partial<HealthProfile>) {
  return http.put<unknown, ApiEnvelope<HealthProfile>>('/api/health/profile', payload)
}

export function listMetrics(profileId?: number) {
  return http.get<unknown, ApiEnvelope<HealthMetric[]>>('/api/health/metrics', { params: { profileId } })
}

export function addMetric(payload: Partial<HealthMetric> & { profileId?: number }) {
  return http.post<unknown, ApiEnvelope<HealthMetric>>('/api/health/metrics', payload)
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

export function generateAdvice(profileId?: number) {
  return http.post<unknown, ApiEnvelope<{ advice: string; basis?: string; generatedAt?: string }>>(
    '/api/health/advice',
    null,
    { params: { profileId }, timeout: 120000 },
  )
}
