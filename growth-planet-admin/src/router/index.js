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
      { path: 'role-perms', name: 'rolePerms', component: () => import('../views/RolePermView.vue'), meta: { title: '角色权限矩阵', menu: '用户与权限' } }
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
