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
      { path: 'ugc-queue', name: 'ugcQueue', component: () => import('../views/UgcQueueView.vue'), meta: { title: 'UGC 审核队列', menu: '内容管理' } },
      { path: 'want-eat', name: 'wantEat', component: () => import('../views/WantEatView.vue'), meta: { title: '想吃记录', menu: '业务数据' } },
      { path: 'confirmations', name: 'confirmations', component: () => import('../views/ConfirmationsView.vue'), meta: { title: '确认单', menu: '业务数据' } },
      { path: 'wallets', name: 'wallets', component: () => import('../views/WalletsView.vue'), meta: { title: '钱包与流水', menu: '业务数据' } },
      { path: 'chores-health', name: 'choresHealth', component: () => import('../views/ChoresHealthView.vue'), meta: { title: '家务与打卡', menu: '业务数据' } },
      { path: 'medal-awards', name: 'medalAwards', component: () => import('../views/MedalAwardsView.vue'), meta: { title: '勋章发放', menu: '业务数据' } },
      { path: 'consent-logs', name: 'consentLogs', component: () => import('../views/ConsentLogsView.vue'), meta: { title: '同意留痕', menu: '合规与隐私' } },
      { path: 'privacy-requests', name: 'privacyRequests', component: () => import('../views/PrivacyRequestsView.vue'), meta: { title: '隐私工单', menu: '合规与隐私' } },
      { path: 'privacy-verifications', name: 'privacyVerifications', component: () => import('../views/VerificationsView.vue'), meta: { title: '核验记录', menu: '合规与隐私' } },
      { path: 'compliance', name: 'compliance', component: () => import('../views/ComplianceView.vue'), meta: { title: '合规清单', menu: '合规与隐私' } },
      { path: 'dashboard-overview', name: 'dashboardOverview', component: () => import('../views/OverviewView.vue'), meta: { title: '运营总览', menu: '运营看板' } },
      { path: 'dashboard-meals', name: 'dashboardMeals', component: () => import('../views/MealsDashboardView.vue'), meta: { title: '餐食看板', menu: '运营看板' } },
      { path: 'dashboard-allowance', name: 'dashboardAllowance', component: () => import('../views/AllowanceDashboardView.vue'), meta: { title: '零花钱看板', menu: '运营看板' } },
      { path: 'dashboard-chores', name: 'dashboardChores', component: () => import('../views/ChoresDashboardView.vue'), meta: { title: '家务健康看板', menu: '运营看板' } },
      { path: 'dashboard-medals', name: 'dashboardMedals', component: () => import('../views/MedalsDashboardView.vue'), meta: { title: '勋章看板', menu: '运营看板' } }
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
