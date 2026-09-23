<template>
  <div v-loading="loading">
    <el-row :gutter="16">
      <el-col :span="6" v-for="card in kpiCards" :key="card.label">
        <el-card shadow="hover" class="kpi-card">
          <div class="kpi-value">{{ card.value }}</div>
          <div class="kpi-label">{{ card.label }}</div>
        </el-card>
      </el-col>
    </el-row>

    <el-card shadow="never" style="margin-top: 16px">
      <template #header><b>待办事项</b></template>
      <el-table :data="todos" stripe>
        <el-table-column prop="module" label="模块" width="160" />
        <el-table-column prop="label" label="事项" min-width="240" />
        <el-table-column prop="count" label="数量" width="120" align="right" />
        <el-table-column prop="route" label="跳转" width="220" />
      </el-table>
      <div class="gen-time">数据生成时间：{{ generatedAt }}</div>
    </el-card>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { workbench } from '../api/console'

const loading = ref(false)
const data = ref(null)

const kpiCards = computed(() => {
  const k = data.value?.kpis || {}
  return [
    { label: '家庭总数', value: k.familyTotal ?? '-' },
    { label: '孩子总数', value: k.childTotal ?? '-' },
    { label: '后台账号数', value: k.adminAccountTotal ?? '-' },
    { label: '待处理（确认单/隐私）', value: (Number(k.pendingConfirmTotal || 0) + Number(k.pendingPrivacyTotal || 0)) || '-' }
  ]
})

const todos = computed(() => data.value?.todos || [])
const generatedAt = computed(() => data.value?.generatedAt || '-')

onMounted(async () => {
  loading.value = true
  try {
    data.value = (await workbench()).data
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.kpi-card { text-align: center; }
.kpi-value { font-size: 28px; font-weight: 700; color: #303133; }
.kpi-label { font-size: 13px; color: #909399; margin-top: 4px; }
.gen-time { font-size: 12px; color: #c0c4cc; margin-top: 10px; text-align: right; }
</style>
