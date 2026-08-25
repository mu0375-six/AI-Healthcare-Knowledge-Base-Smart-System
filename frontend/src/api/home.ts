import http from './http'
import type { ApiEnvelope, HomeOverview } from './types'

export function getOverview() {
  return http.get<unknown, ApiEnvelope<HomeOverview>>('/api/home/overview')
}
