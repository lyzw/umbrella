<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getComplianceChecklist, checkCompliance } from '../api/console'
import { hasPerm } from '../stores/auth'

const loading = ref(false)
const items = ref([])
// 合规清单勾检 = config 权限（CP/SA；RA 仅可见）
const canCheck = hasPerm('合规清单', 'config')

async function load() {
  loading.value = true
  try {
    const { data } = await getComplianceChecklist()
    items.value = data || []
  } finally {
    loading.value = false
  }
}

async function onToggle(item) {
  try {
    await checkCompliance(item.itemKey, item.checked)
    ElMessage.success(item.checked ? `已勾检：${item.itemText}` : `已取消勾检`)
  } catch (e) {
    item.checked = !item.checked
    ElMessage.error(e?.response?.data?.message || '操作失败')
  }
}

onMounted(load)
</script>

<template>
  <el-card shadow="never">
    <el-alert type="info" :closable="false" style="margin-bottom:16px"
      title="合规清单为周期性自查留痕：勾检动作落审计（COMPLIANCE_CHECK），取消勾检同样留痕。" />

    <el-table :data="items" v-loading="loading">
      <el-table-column prop="itemKey" label="键" width="200" />
      <el-table-column prop="itemText" label="清单项" min-width="320" />
      <el-table-column label="状态" width="110">
        <template #default="{ row }">
          <el-tag :type="row.checked ? 'success' : 'info'">{{ row.checked ? '已勾检' : '未勾检' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="checkedBy" label="勾检人" width="100">
        <template #default="{ row }">{{ row.checkedBy || '—' }}</template>
      </el-table-column>
      <el-table-column prop="checkedAt" label="勾检时间" width="180">
        <template #default="{ row }">{{ row.checkedAt || '—' }}</template>
      </el-table-column>
      <el-table-column v-if="canCheck" label="操作" width="120">
        <template #default="{ row }">
          <el-switch v-model="row.checked" @change="onToggle(row)" />
        </template>
      </el-table-column>
    </el-table>
  </el-card>
</template>
