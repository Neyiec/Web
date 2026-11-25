export class ApiError extends Error {
  constructor(status, message) {
    super(message)
    this.status = status
  }
}

export async function request(path, { method = 'GET', body } = {}) {
  const headers = { Accept: 'application/json, application/problem+json' }
  if (method !== 'GET') {
    const csrf = await request('/api/auth/csrf')
    headers[csrf.headerName] = csrf.token
    if (body !== undefined && !(body instanceof FormData)) {
      headers['Content-Type'] = 'application/json'
      body = JSON.stringify(body)
    }
  }

  const response = await fetch(path, { method, headers, body, cache: 'no-store' })
  if (response.status === 204 || response.headers.get('content-length') === '0' && response.ok) {
    return null
  }
  const data = await response.json().catch(() => null)
  if (!response.ok) {
    const fallback = {
      400: '请检查输入后重试。',
      401: '请重新登录。',
      403: '当前账号没有权限，或请求已失效。',
      404: '内容已变更，请刷新页面。',
      413: '上传的文件过大。',
      503: '服务暂时不可用，请稍后重试。',
    }
    throw new ApiError(response.status, data?.detail ?? fallback[response.status] ?? '操作失败，请稍后重试。')
  }
  return data
}

export function post(path, body) {
  return request(path, { method: 'POST', body })
}
