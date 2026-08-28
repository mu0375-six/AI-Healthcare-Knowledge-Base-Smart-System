export interface ApiEnvelope<T> {
  code: number
  message: string
  data: T
}

/** 后端统一分页结构（dto/PageResult）。 */
export interface PageResult<T> {
  records: T[]
  total: number
  page: number
  size: number
}

export interface UserInfo {
  id: number
  username: string
  nickname: string
  role: 'USER' | 'ADMIN'
}

export interface ChatSession {
  id: number
  userId: number
  title: string
  createdAt: string
  updatedAt: string
}


export interface ChatAttachment {
  id: number
  filename: string
  mimeType?: string
}

export interface ChatMessage {
  id: number
  sessionId: number
  userId: number
  role: 'user' | 'assistant'
  content: string
  attachmentsJson?: string
  createdAt: string
  localPreviews?: string[]
}

export interface HealthProfile {
  id?: number
  userId?: number
  displayName?: string
  relation?: string
  age?: number | null
  sex?: string
  heightCm?: number | null
  weightKg?: number | null
  allergies?: string
  sharedToAdmin?: boolean
  lastAdvice?: string
  adviceAt?: string
  updatedAt?: string
}

export interface HealthMetric {
  id: number
  metricType: string
  value: number
  unit?: string
  recordedAt: string
  note?: string
}

export interface HealthHistory {
  id: number
  disease: string
  diagnosedAt?: string
  status?: string
  note?: string
}

export interface ExamReport {
  id: number
  filename: string
  profileId?: number
  rawText?: string
  summary?: string
  createdAt: string
}

export interface ExamReportItem {
  id: number
  name: string
  value: string
  unit?: string
  refRange?: string
  flag: string
  interpretation?: string
}

export interface KbDocument {
  id: number
  title: string
  category: string
  source: string
  sourceUrl?: string
  publisher?: string
  filename?: string
  createdAt: string
}

export interface TriageHit {
  department: string
  score: number
  reason: string
  urgency: string
}

export interface TriageResult {
  urgency: string
  summary: string
  departments: TriageHit[]
  disclaimer: string
}

export interface HomeSessionBrief {
  id: number
  title: string
  updatedAt: string
}

export interface HomeReportBrief {
  id: number
  filename: string
  createdAt: string
  hint?: string
}

/** 首页「需要留心」：各档案最新的异常指标。 */
export interface MetricAlert {
  metricId: number
  profileId?: number
  profileName: string
  metricType: string
  metricValue: number
  unit?: string
  flag: 'high' | 'low'
  refRange?: string
  recordedAt: string
  delta?: number | null
}

/** 首页迷你趋势：某一指标类型的近 8 个点。 */
export interface MetricSeries {
  metricType: string
  unit?: string
  flag: 'high' | 'low' | 'normal'
  points: { when: string; value: number }[]
}

export interface HomeOverview {
  profileCount: number
  metricCount: number
  reportCount: number
  favoriteCount: number
  sessionCount: number
  profiles: HealthProfile[]
  recentSessions: HomeSessionBrief[]
  recentReports: HomeReportBrief[]
  alerts: MetricAlert[]
  series: MetricSeries[]
}
