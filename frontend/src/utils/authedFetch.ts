/**
 * 带 Bearer 的裸 fetch：SSE 与图片二进制这两条路走不了 axios 实例
 * （前者要流式读取，后者要 blob），此前三处各自手拼 Authorization，收口到这里。
 */
export function authedFetch(input: string, init?: RequestInit): Promise<Response> {
  const token = localStorage.getItem('token') || ''
  const headers = new Headers(init?.headers)
  if (token) {
    headers.set('Authorization', `Bearer ${token}`)
  }
  return fetch(input, { ...init, headers })
}
