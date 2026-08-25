import http from './http'
import type { ApiEnvelope, ExamReport, ExamReportItem } from './types'

export function uploadReport(file: File, extractedText?: string, profileId?: number) {
  const form = new FormData()
  form.append('file', file)
  if (extractedText) form.append('extractedText', extractedText)
  return http.post<unknown, ApiEnvelope<{ report: ExamReport; items: ExamReportItem[]; disclaimer: string }>>(
    '/api/reports/upload',
    form,
    { headers: { 'Content-Type': 'multipart/form-data' }, params: { profileId } },
  )
}

export function importReportToProfile(reportId: number, profileId: number) {
  return http.post<unknown, ApiEnvelope<{ imported: number }>>(`/api/reports/${reportId}/import`, null, {
    params: { profileId },
  })
}

export function listReports() {
  return http.get<unknown, ApiEnvelope<ExamReport[]>>('/api/reports')
}

export function reportDetail(id: number) {
  return http.get<unknown, ApiEnvelope<{ report: ExamReport; items: ExamReportItem[]; disclaimer: string }>>(
    `/api/reports/${id}`,
  )
}
