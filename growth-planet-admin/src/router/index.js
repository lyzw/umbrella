import { createRouter, createWebHashHistory } from 'vue-router'
import { getToken } from '../api/http'
import { fetchMe } from '../stores/auth'

const routes = [
  { path: '/login', name: 'login', component: () => import('../views/LoginView.vue'), meta: { public: true } },
  {
    path: '/',
    component: () => import('../layout/AdminLayout.vue'),
    redirect: '/workbench',
    children: [
      { path: 'workbench', name: 'workbench', component: () => import('../views/WorkbenchView.vue'), meta: { title: '工作台', menu: '工作台' } },
      { path: 'accounts', name: 'accounts', component: () => import('../views/AccountsView.vue'), meta: { title: '账号管理', menu: '用户与权限' } },
      { path: 'role-perms', name: 'rolePerms', component: () => import('../views/RolePermView.vue'), meta: { title: '角色权限矩阵', menu: '用户与权限' } },
      { path: 'audit-logs', name: 'auditLogs', component: () => import('../views/AuditLogView.vue'), meta: { title: '操作日志', menu: '审计与日志' } },
      { path: 'c-audit-logs', name: 'cAuditLogs', component: () => import('../views/CAuditView.vue'), meta: { title: 'C 端关键操作', menu: '审计与日志' } },
      { path: 'dishes', name: 'dishes', component: () => import('../views/DishesView.vue'), meta: { title: '菜品库', menu: '内容管理' } },
      { path: 'school-menus', name: 'schoolMenus', component: () => import('../views/SchoolMenusView.vue'), meta: { title: '菜单编排', menu: '内容管理' } },
      { path: 'chore-tasks', name: 'choreTasks', component: () => import('../views/ChoreTasksView.vue'), meta: { title: '任务库', menu: '内容管理' } },
      { path: 'medals', name: 'medals', component: () => import('../views/MedalsView.vue'), meta: { title: '勋章配置', menu: '内容管理' } },
      { path: 'ugc-queue', name: 'ugcQueue', component: () => import('../views/UgcQueueView.vue'), meta: { title: 'UGC 审核队列', menu: '内容管理' } }
    ]
  },
  { path: '/:pathMatch(.*)*', redirect: '/workbench' }
]

const router = createRouter({
  history: createWebHashHistory(),
  routes
})

// 全局守卫：无 token → 登录页；有 token 但未加载会话 → 先拉 /me
router.beforeEach(async (to) => {
  if (to.meta.public) return true
  if (!getToken()) return { name: 'login' }
  const ok = await fetchMe()
  if (!ok) return { name: 'login' }
  return true
})

export default router
