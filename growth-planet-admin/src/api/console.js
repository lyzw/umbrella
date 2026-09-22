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

// ==================== M3 内容管理 ====================

/** 菜品分页（预置菜品库） */
export const listDishes = (filter) => {
  const p = new URLSearchParams()
  if (filter.categoryId) p.set('categoryId', filter.categoryId)
  if (filter.status) p.set('status', filter.status)
  if (filter.keyword) p.set('keyword', filter.keyword)
  if (filter.allergenStatus) p.set('allergenStatus', filter.allergenStatus)
  p.set('page', filter.page || 1)
  p.set('pageSize', filter.pageSize || 20)
  return http.get(`/api/admin/dishes?${p.toString()}`)
}

export const getDish = (id) => http.get(`/api/admin/dishes/${id}`)
export const createDish = (payload) => http.post('/api/admin/dishes', payload)
export const updateDish = (id, payload) => http.put(`/api/admin/dishes/${id}`, payload)
export const deleteDish = (id) => http.delete(`/api/admin/dishes/${id}`)
export const toggleDishStatus = (id, status) =>
  http.put(`/api/admin/dishes/${id}/status`, { status })

/** 菜品分类 */
export const listCategories = (page = 1, pageSize = 50) =>
  http.get(`/api/admin/dish-categories?page=${page}&pageSize=${pageSize}`)
export const createCategory = (payload) => http.post('/api/admin/dish-categories', payload)
export const updateCategory = (id, payload) => http.put(`/api/admin/dish-categories/${id}`, payload)
export const deleteCategory = (id) => http.delete(`/api/admin/dish-categories/${id}`)

/** 校餐菜单（SCHOOL） */
export const listSchoolMenus = (filter) => {
  const p = new URLSearchParams()
  if (filter.school) p.set('school', filter.school)
  if (filter.from) p.set('from', filter.from)
  if (filter.to) p.set('to', filter.to)
  if (filter.mealType) p.set('mealType', filter.mealType)
  p.set('page', filter.page || 1)
  p.set('pageSize', filter.pageSize || 20)
  return http.get(`/api/admin/menus/school?${p.toString()}`)
}
export const upsertSchoolMenu = (payload) => http.post('/api/admin/menus/school', payload)

/** 任务库（跨家庭治理） */
export const listChoreTasks = (filter) => {
  const p = new URLSearchParams()
  if (filter.familyId) p.set('familyId', filter.familyId)
  if (filter.status) p.set('status', filter.status)
  if (filter.keyword) p.set('keyword', filter.keyword)
  p.set('page', filter.page || 1)
  p.set('pageSize', filter.pageSize || 20)
  return http.get(`/api/admin/chore-tasks?${p.toString()}`)
}
export const createChoreTask = (payload) => http.post('/api/admin/chore-tasks', payload)
export const updateChoreTask = (id, payload) => http.put(`/api/admin/chore-tasks/${id}`, payload)

/** 勋章配置 */
export const listMedals = (status = '') =>
  http.get(`/api/admin/medals${status ? `?status=${status}` : ''}`)
export const createMedal = (payload) => http.post('/api/admin/medals', payload)
export const updateMedal = (id, payload) => http.put(`/api/admin/medals/${id}`, payload)

/** UGC 审核队列 */
export const ugcQueue = (filter) => {
  const p = new URLSearchParams()
  if (filter.reviewStatus) p.set('reviewStatus', filter.reviewStatus)
  if (filter.keyword) p.set('keyword', filter.keyword)
  p.set('page', filter.page || 1)
  p.set('pageSize', filter.pageSize || 20)
  return http.get(`/api/admin/ugc/queue?${p.toString()}`)
}
export const reviewUgc = (id, payload) => http.post(`/api/admin/ugc/${id}/review`, payload)
export const deleteUgc = (id) => http.delete(`/api/admin/ugc/${id}`)

/** 心愿菜单家庭配置（页面归 M4 家庭详情，API 先行） */
export const getWishConfig = (familyId) => http.get(`/api/admin/wish-menu-config/${familyId}`)
export const updateWishConfig = (familyId, payload) =>
  http.put(`/api/admin/wish-menu-config/${familyId}`, payload)

// ==================== M4 业务数据 ====================

/** 业务数据查询参数拼接（列表/导出共用） */
function bizQuery(f, { withPage = true } = {}) {
  const p = new URLSearchParams()
  if (f.familyId) p.set('familyId', f.familyId)
  if (f.childId) p.set('childId', f.childId)
  if (f.status) p.set('status', f.status)
  if (f.mealType) p.set('mealType', f.mealType)
  if (f.transType) p.set('transType', f.transType)
  if (f.itemId) p.set('itemId', f.itemId)
  if (f.overLimit !== undefined && f.overLimit !== null && f.overLimit !== '') p.set('overLimit', f.overLimit)
  if (f.from) p.set('from', f.from)
  if (f.to) p.set('to', f.to)
  if (withPage) {
    p.set('page', f.page || 1)
    p.set('pageSize', f.pageSize || 20)
  }
  return p.toString()
}

/** 每日想吃分页 */
export const wantEat = (filter) => http.get(`/api/admin/want-eat?${bizQuery(filter)}`)

/** 确认单分页 */
export const confirmations = (filter) => http.get(`/api/admin/confirmations?${bizQuery(filter)}`)

/** 确认单详情 */
export const confirmationDetail = (id) => http.get(`/api/admin/confirmations/${id}`)

/** 超额确认单人工复核（结论落审计，不改业务状态） */
export const reviewConfirmation = (id, payload) =>
  http.post(`/api/admin/confirmations/${id}/review`, payload)

/** 审批记录分页 */
export const confirmApprovals = (filter) => http.get(`/api/admin/confirm-approvals?${bizQuery(filter)}`)

/** 钱包余额分页 */
export const wallets = (filter) => http.get(`/api/admin/wallets?${bizQuery(filter)}`)

/** 零花钱流水分页 */
export const allowanceLogs = (filter) => http.get(`/api/admin/allowance-logs?${bizQuery(filter)}`)

/** 家务实例分页（含状态汇总/完成率） */
export const choreInstances = (filter) => http.get(`/api/admin/chore-instances?${bizQuery(filter)}`)

/** 打卡记录分页 */
export const checkRecords = (filter) => http.get(`/api/admin/check-records?${bizQuery(filter)}`)

/** 勋章发放分页 */
export const medalAwards = (filter) => {
  const p = new URLSearchParams()
  if (filter.definitionId) p.set('definitionId', filter.definitionId)
  if (filter.familyId) p.set('familyId', filter.familyId)
  if (filter.childId) p.set('childId', filter.childId)
  p.set('page', filter.page || 1)
  p.set('pageSize', filter.pageSize || 20)
  return http.get(`/api/admin/medal-awards?${p.toString()}`)
}

/** 勋章人工补发（L4） */
export const reissueMedal = (payload) => http.post('/api/admin/medal-awards', payload)

/** 业务数据 CSV 导出（Blob 下载；导出行为在后端落审计） */
export async function exportBizCsv(domain, filter) {
  const resp = await fetch(`/api/admin/export/${domain}`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${getToken()}` },
    body: JSON.stringify({
      familyId: filter.familyId || null,
      childId: filter.childId || null,
      status: filter.status || null,
      mealType: filter.mealType || null,
      from: filter.from || null,
      to: filter.to || null
    })
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
  a.download = m ? m[1] : `biz-${domain}.csv`
  a.click()
  URL.revokeObjectURL(a.href)
}
