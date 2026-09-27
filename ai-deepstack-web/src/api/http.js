const TOKEN_KEY = 'token'
const USER_KEY = 'user'

export function getToken() {
  return localStorage.getItem(TOKEN_KEY) || ''
}

export function getDisplayName() {
  try {
    const u = JSON.parse(localStorage.getItem(USER_KEY) || '{}')
    return u.displayName || u.username || ''
  } catch {
    return ''
  }
}

/** 当前登录用户是否管理员（isAdmin === 1）。 */
export function getIsAdmin() {
  try {
    const u = JSON.parse(localStorage.getItem(USER_KEY) || '{}')
    return Number(u.isAdmin) === 1
  } catch {
    return false
  }
}

/** 当前登录用户 id（字符串，与后端 Long 序列化一致）。 */
export function getUserId() {
  try {
    const u = JSON.parse(localStorage.getItem(USER_KEY) || '{}')
    if (u.userId == null || u.userId === '') return null
    return String(u.userId)
  } catch {
    return null
  }
}

export function setAuth({ token, userId, displayName, isAdmin }) {
  localStorage.setItem(TOKEN_KEY, token || '')
  localStorage.setItem(USER_KEY, JSON.stringify({
    userId: userId == null || userId === '' ? null : String(userId),
    displayName,
    isAdmin: isAdmin != null ? Number(isAdmin) : 0
  }))
}

export function clearAuth() {
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(USER_KEY)
}

export async function http(path, options = {}) {
  const headers = {
    ...(options.body instanceof FormData ? {} : { 'Content-Type': 'application/json' }),
    ...(options.headers || {})
  }
  const token = getToken()
  if (token) headers.Authorization = `Bearer ${token}`

  const res = await fetch(path, { ...options, headers })
  const contentType = res.headers.get('content-type') || ''
  if (!contentType.includes('application/json')) {
    if (!res.ok) throw new Error(`请求失败 (${res.status})`)
    return res
  }
  const body = await res.json()
  if (!res.ok || (body.code != null && body.code !== 200)) {
    if (body.code === 401 || res.status === 401) {
      clearAuth()
      if (location.pathname !== '/login') location.href = '/login'
    }
    throw new Error(body.message || `请求失败 (${res.status})`)
  }
  return body
}

export function get(path, params) {
  const qs = params
    ? '?' + new URLSearchParams(
        Object.entries(params).filter(([, v]) => v !== undefined && v !== null && v !== '')
      ).toString()
    : ''
  return http(path + qs)
}

export function post(path, data) {
  return http(path, { method: 'POST', body: JSON.stringify(data ?? {}) })
}

export function put(path, data) {
  return http(path, { method: 'PUT', body: JSON.stringify(data ?? {}) })
}

export function del(path) {
  return http(path, { method: 'DELETE' })
}

export function pageList(res) {
  return res?.data?.list || []
}
