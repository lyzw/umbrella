import { http } from './http'

/** 后台登录 */
export const login = (username, password) =>
  http.post('/api/console/auth/login', { username, password })

/** 当前管理员信息（含权限集合） */
export const me = () => http.get('/api/console/auth/me')

/** 退出登录（使 token 失效） */
export const logout = () => http.post('/api/console/auth/logout')

/** 工作台聚合（KPI + 待办） */
export const workbench = () => http.get('/api/console/workbench')

/** 账号分页列表 */
export const listAccounts = (page = 1, pageSize = 10, keyword = '') =>
  http.get(`/api/console/accounts?page=${page}&pageSize=${pageSize}&keyword=${encodeURIComponent(keyword)}`)

/** 新建账号 */
export const createAccount = (payload) => http.post('/api/console/accounts', payload)

/** 编辑账号（姓名/角色） */
export const updateAccount = (id, payload) => http.put(`/api/console/accounts/${id}`, payload)

/** 启用/停用 */
export const changeAccountStatus = (id, status) =>
  http.put(`/api/console/accounts/${id}/status`, { status })

/** 重置密码 */
export const resetPassword = (id, newPassword) =>
  http.post(`/api/console/accounts/${id}/password`, { newPassword })

/** 角色列表 */
export const listRoles = () => http.get('/api/console/roles')

/** 角色权限矩阵 */
export const permissionMatrix = (roleCode) =>
  http.get(`/api/console/roles/permissions?roleCode=${encodeURIComponent(roleCode)}`)
