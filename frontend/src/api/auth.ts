import http from './http'
import type { ApiEnvelope, UserInfo } from './types'

export function register(payload: { username: string; password: string; nickname?: string }) {
  return http.post<unknown, ApiEnvelope<UserInfo>>('/api/auth/register', payload)
}

export function login(payload: { username: string; password: string }) {
  return http.post<unknown, ApiEnvelope<{ token: string; user: UserInfo }>>('/api/auth/login', payload)
}

export function me() {
  return http.get<unknown, ApiEnvelope<UserInfo>>('/api/auth/me')
}

/** 改密码成功后后端会注销当前会话，调用方需清本地凭证并回登录页。 */
export function changePassword(payload: { oldPassword: string; newPassword: string }) {
  return http.post<unknown, ApiEnvelope<null>>('/api/auth/password', payload)
}
