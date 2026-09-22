/**
 * 后台 API 客户端：统一附加 Admin JWT、解析 Result 信封、401 处理。
 * 约定：后端统一返回 { code, message, data, requestId }，code=0 表示成功。
 */
const TOKEN_KEY = 'gp_admin_token'

export function getToken() {
  return localStorage.getItem(TOKEN_KEY) || ''
}

export function setToken(token) {
  localStorage.setItem(TOKEN_KEY, token)
}

export function clearToken() {
  localStorage.removeItem(TOKEN_KEY)
}

async function request(method, url, body) {
  const headers = { 'Content-Type': 'application/json' }
  const token = getToken()
  if (token) headers['Authorization'] = `Bearer ${token}`

  let resp
  try {
    resp = await fetch(url, {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body)
    })
  } catch (e) {
    // 网络层失败（离线/后端未启动）
    throw new Error('网络异常，请检查后端服务是否已启动')
  }

  if (resp.status === 401) {
    clearToken()
    // 统一跳登录页（由路由守卫兜底，这里直接刷新触发守卫）
    if (!location.hash.includes('/login')) {
      location.hash = '#/login'
      location.reload()
    }
    throw new Error('登录已失效，请重新登录')
  }

  const envelope = await resp.json().catch(() => null)
  if (envelope == null) {
    throw new Error(`响应解析失败（HTTP ${resp.status}）`)
  }
  if (Number(envelope.code) !== 0) {
    throw new Error(envelope.message || `业务错误（code=${envelope.code}）`)
  }
  return envelope.data
}

export const http = {
  get: (url) => request('GET', url),
  post: (url, body) => request('POST', url, body),
  put: (url, body) => request('PUT', url, body)
}
