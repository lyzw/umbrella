<template>
  <el-card shadow="never">
    <div class="toolbar">
      <span class="tip">查看各角色的「资源 × 操作」权限矩阵（SA 超级管理员默认全权限，不落库）</span>
      <el-select v-model="roleCode" style="width: 260px" @change="load">
        <el-option v-for="r in roles" :key="r.code" :value="r.code"
                   :label="`${r.name}（${r.code}）· ${r.permCount} 项权限`" />
      </el-select>
    </div>

    <el-table :data="matrix" v-loading="loading" stripe border>
      <el-table-column prop="resource" label="资源" width="180" fixed />
      <el-table-column v-for="action in actions" :key="action" :label="actionLabel(action)"
                       :prop="`perms.${action}`" width="110" align="center">
        <template #default="{ row }">
          <el-icon v-if="row.perms[action]" color="#67c23a" size="18"><CircleCheckFilled /></el-icon>
          <el-icon v-else color="#dcdfe6" size="18"><CircleCloseFilled /></el-icon>
        </template>
      </el-table-column>
    </el-table>
  </el-card>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { CircleCheckFilled, CircleCloseFilled } from '@element-plus/icons-vue'
import { listRoles, permissionMatrix } from '../api/console'

const actions = ['view', 'create', 'edit', 'delete', 'export', 'approve', 'config']
const actionLabels = {
  view: '查看', create: '新建', edit: '编辑', delete: '删除',
  export: '导出', approve: '审批', config: '配置'
}
const actionLabel = (a) => actionLabels[a] || a

const roles = ref([])
const roleCode = ref('')
const matrix = ref([])
const loading = ref(false)

async function load() {
  if (!roleCode.value) return
  loading.value = true
  try {
    matrix.value = (await permissionMatrix(roleCode.value)).data
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  roles.value = (await listRoles()).data
  // 默认展示 OP（日常运营最常用），若不存在则取第一个
  const op = roles.value.find(r => r.code === 'OP')
  roleCode.value = (op || roles.value[0])?.code || ''
  await load()
})
</script>

<style scoped>
.toolbar { display: flex; align-items: center; justify-content: space-between; margin-bottom: 14px; }
.tip { font-size: 13px; color: #909399; }
</style>
