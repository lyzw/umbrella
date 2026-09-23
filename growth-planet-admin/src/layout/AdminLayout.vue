<template>
  <el-container class="layout">
    <el-aside width="220px" class="aside">
      <div class="logo">🪐 成长星球 · 运营后台</div>
      <el-menu :default-active="activeMenu" router background-color="#1f2d3d" text-color="#bfcbd9"
               active-text-color="#409eff">
        <el-menu-item index="/workbench">
          <el-icon><Monitor /></el-icon><span>工作台</span>
        </el-menu-item>
        <el-sub-menu v-if="canSeeAccounts" index="user-perm">
          <template #title>
            <el-icon><User /></el-icon><span>用户与权限</span>
          </template>
          <el-menu-item index="/accounts">后台账号</el-menu-item>
          <el-menu-item index="/role-perms">角色权限矩阵</el-menu-item>
        </el-sub-menu>
        <el-sub-menu v-if="canSeeAudit" index="audit">
          <template #title>
            <el-icon><Document /></el-icon><span>审计与日志</span>
          </template>
          <el-menu-item index="/audit-logs">操作日志</el-menu-item>
          <el-menu-item index="/c-audit-logs">C 端关键操作</el-menu-item>
        </el-sub-menu>
        <el-sub-menu v-if="canSeeContent" index="content">
          <template #title>
            <el-icon><Goods /></el-icon><span>内容管理</span>
          </template>
          <el-menu-item index="/dishes">菜品库</el-menu-item>
          <el-menu-item index="/school-menus">菜单编排</el-menu-item>
          <el-menu-item index="/chore-tasks">任务库</el-menu-item>
          <el-menu-item index="/medals">勋章配置</el-menu-item>
          <el-menu-item index="/ugc-queue">UGC 审核队列</el-menu-item>
        </el-sub-menu>
        <el-sub-menu v-if="canSeeBiz" index="biz">
          <template #title>
            <el-icon><DataAnalysis /></el-icon><span>业务数据</span>
          </template>
          <el-menu-item index="/want-eat">想吃记录</el-menu-item>
          <el-menu-item index="/confirmations">确认单</el-menu-item>
          <el-menu-item index="/wallets">钱包与流水</el-menu-item>
          <el-menu-item index="/chores-health">家务与打卡</el-menu-item>
          <el-menu-item index="/medal-awards">勋章发放</el-menu-item>
        </el-sub-menu>
        <el-sub-menu v-if="canSeePrivacy" index="privacy">
          <template #title>
            <el-icon><Lock /></el-icon><span>合规与隐私</span>
          </template>
          <el-menu-item index="/consent-logs">同意留痕</el-menu-item>
          <el-menu-item index="/privacy-requests">隐私工单</el-menu-item>
          <el-menu-item index="/privacy-verifications">核验记录</el-menu-item>
          <el-menu-item index="/compliance">合规清单</el-menu-item>
        </el-sub-menu>
        <el-sub-menu v-if="canSeeDashboard" index="dashboard">
          <template #title>
            <el-icon><TrendCharts /></el-icon><span>运营看板</span>
          </template>
          <el-menu-item index="/dashboard-overview">运营总览</el-menu-item>
          <el-menu-item index="/dashboard-meals">餐食看板</el-menu-item>
          <el-menu-item index="/dashboard-allowance">零花钱看板</el-menu-item>
          <el-menu-item index="/dashboard-chores">家务健康看板</el-menu-item>
          <el-menu-item index="/dashboard-medals">勋章看板</el-menu-item>
        </el-sub-menu>
      </el-menu>
    </el-aside>

    <el-container>
      <el-header class="header">
        <span class="page-title">{{ route.meta.title }}</span>
        <div class="user-box">
          <el-tag :type="auth.superAdmin ? 'danger' : 'info'" size="small">{{ auth.roleName }}</el-tag>
          <span class="user-name">{{ auth.name }}（{{ auth.username }}）</span>
          <el-button link type="primary" @click="onLogout">退出</el-button>
        </div>
      </el-header>
      <el-main class="main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Monitor, User, Document, Goods, DataAnalysis, Lock, TrendCharts } from '@element-plus/icons-vue'
import { auth, doLogout, hasPerm } from '../stores/auth'

const route = useRoute()
const router = useRouter()
const activeMenu = computed(() => route.path)
// 后台账号菜单：拥有「后台账号:view」权限才可见（SA 全可见）
const canSeeAccounts = computed(() => hasPerm('后台账号', 'view'))
// 审计菜单：拥有「操作日志:view」或「C端关键操作:view」权限可见
const canSeeAudit = computed(() => hasPerm('操作日志', 'view') || hasPerm('C端关键操作', 'view'))
// 内容管理菜单：任一内容资源有查看权限即可见
const canSeeContent = computed(() =>
  hasPerm('菜品库', 'view') || hasPerm('菜单编排', 'view') || hasPerm('任务库/奖励库', 'view')
  || hasPerm('勋章配置', 'view') || hasPerm('UGC审核队列', 'view'))
// 业务数据菜单：任一业务数据资源有查看权限即可见
const canSeeBiz = computed(() =>
  hasPerm('每日想吃', 'view') || hasPerm('确认单', 'view') || hasPerm('审批记录', 'view')
  || hasPerm('钱包与流水', 'view') || hasPerm('家务健康', 'view') || hasPerm('勋章发放/家庭设置', 'view'))
// 合规与隐私菜单：隐私域隔离（SA/CP/RA），任一隐私资源有查看权限即可见
const canSeePrivacy = computed(() =>
  hasPerm('同意留痕', 'view') || hasPerm('隐私工单', 'view')
  || hasPerm('核验记录', 'view') || hasPerm('合规清单', 'view'))
// 运营看板菜单：拥有「运营看板:view」权限可见（SA 全可见；CP 无，符合 §3.4 矩阵）
const canSeeDashboard = computed(() => hasPerm('运营看板', 'view'))

async function onLogout() {
  await doLogout()
  router.push('/login')
}
</script>

<style scoped>
.layout { height: 100%; }
.aside { background: #1f2d3d; }
.logo {
  height: 56px; line-height: 56px; padding: 0 18px;
  color: #fff; font-weight: 600; font-size: 15px;
  border-bottom: 1px solid rgba(255,255,255,.08);
}
.header {
  background: #fff; display: flex; align-items: center; justify-content: space-between;
  border-bottom: 1px solid #e4e7ed; height: 56px;
}
.page-title { font-size: 16px; font-weight: 600; }
.user-box { display: flex; align-items: center; gap: 10px; }
.user-name { font-size: 13px; color: #606266; }
.main { padding: 16px; overflow-y: auto; }
</style>
