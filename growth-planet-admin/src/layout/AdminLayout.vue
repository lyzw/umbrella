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
import { Monitor, User } from '@element-plus/icons-vue'
import { auth, doLogout, hasPerm } from '../stores/auth'

const route = useRoute()
const router = useRouter()
const activeMenu = computed(() => route.path)
// 后台账号菜单：拥有「后台账号:view」权限才可见（SA 全可见）
const canSeeAccounts = computed(() => hasPerm('后台账号', 'view'))

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
