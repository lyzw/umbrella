import { reactive } from 'vue'
import { getToken, setToken, clearToken } from '../api/http'
import * as api from '../api/console'

// 说明：轻量响应式会话状态（未引入 pinia，保持地基切片最小依赖）
export const auth = reactive({
  adminId: null,
  username: '',
  name: '',
  roleCode: '',
  roleName: '',
  superAdmin: false,
  permissions: new Set(),
  loaded: false
})

export async function doLogin(username, password) {
  const resp = await api.login(username, password)
  setToken(resp.token)
  auth.loaded = false
  await fetchMe()
  return resp
}

export async function fetchMe() {
  if (!getToken()) return false
  try {
    const data = await api.me()
    auth.adminId = data.adminId
    auth.username = data.username
    auth.name = data.name
    auth.roleCode = data.roleCode
    auth.roleName = data.roleName
    auth.superAdmin = !!data.superAdmin
    auth.permissions = new Set(data.permissions || [])
    auth.loaded = true
    return true
  } catch (e) {
    auth.loaded = false
    return false
  }
}

export async function doLogout() {
  try { await api.logout() } catch (e) { /* 后端已失效也无妨，本地照常清理 */ }
  clearToken()
  auth.adminId = null
  auth.username = ''
  auth.name = ''
  auth.roleCode = ''
  auth.roleName = ''
  auth.superAdmin = false
  auth.permissions = new Set()
  auth.loaded = false
}

/** 是否拥有 resource:action 权限（SA 全通过） */
export function hasPerm(resource, action) {
  return auth.superAdmin || auth.permissions.has(`${resource}:${action}`)
}
