import { http } from './http'

/** 后台登录 */
export const login = (username, password) =>
  http.post('/api/admin/auth/login', { username, password })

/** 当前管理员信息（含权限集合） */
export const me = () => http.get('/api/admin/auth/me')

/** 退出登录（使 token 失效） */
export const logout = () => http.post('/api/admin/auth/logout')

/** 工作台聚合（KPI + 待办） */
export const workbench = () => http.get('/api/admin/workbench')

/** 账号分页列表 */
export const listAccounts = (page = 1, pageSize = 10, keyword = '') =>
  http.get(`/api/admin/accounts?page=${page}&pageSize=${pageSize}&keyword=${encodeURIComponent(keyword)}`)

/** 新建账号 */
export const createAccount = (payload) => http.post('/api/admin/accounts', payload)

/** 编辑账号（姓名/角色） */
export const updateAccount = (id, payload) => http.put(`/api/admin/accounts/${id}`, payload)

/** 启用/停用 */
export const changeAccountStatus = (id, status) =>
  http.put(`/api/admin/accounts/${id}/status`, { status })

/** 重置密码 */
export const resetPassword = (id, newPassword) =>
  http.post(`/api/admin/accounts/${id}/password`, { newPassword })

/** 角色列表 */
export const listRoles = () => http.get('/api/admin/roles')

/** 角色权限矩阵 */
export const permissionMatrix = (roleCode) =>
  http.get(`/api/admin/roles/permissions?roleCode=${encodeURIComponent(roleCode)}`)

// ==================== M6 审计 ====================

/** 审计日志查询参数拼接 */
function auditQuery(f, { withPage = true } = {}) {
  const p = new URLSearchParams()
  if (f.actorUserId) p.set('actorUserId', f.actorUserId)
  if (f.action) p.set('action', f.action)
  if (f.targetType) p.set('targetType', f.targetType)
  if (f.result) p.set('result', f.result)
  if (f.begin) p.set('begin', f.begin)
  if (f.end) p.set('end', f.end)
  if (withPage) {
    p.set('page', f.page || 1)
    p.set('pageSize', f.pageSize || 20)
  }
  return p.toString()
}

/** 操作日志分页 */
export const auditLogs = (filter) => http.get(`/api/admin/audit-logs?${auditQuery(filter)}`)

/** 审计详情 */
export const auditLogDetail = (id) => http.get(`/api/admin/audit-logs/${id}`)

/** C 端关键操作分页（白名单动作） */
export const cAuditLogs = (filter) => http.get(`/api/admin/c-audit-logs?${auditQuery(filter)}`)

/** 审计日志 CSV 导出（Blob 下载；导出行为在后端落审计） */
export async function exportAuditLogs(filter) {
  const resp = await fetch(`/api/admin/audit-logs/export?${auditQuery(filter, { withPage: false })}`, {
    headers: { Authorization: `Bearer ${getToken()}` }
  })
  if (!resp.ok) {
    const env = await resp.json().catch(() => null)
    throw new Error(env?.message || `导出失败（HTTP ${resp.status}）`)
  }
  const blob = await resp.blob()
  const disposition = resp.headers.get('Content-Disposition') || ''
  const m = disposition.match(/filename="([^"]+)"/)
  const a = document.createElement('a')
  a.href = URL.createObjectURL(blob)
  a.download = m ? m[1] : 'audit-logs.csv'
  a.click()
  URL.revokeObjectURL(a.href)
}
