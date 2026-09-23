<template>
  <div class="page">
    <div class="page-head">
      <h2>运营总览</h2>
      <el-button type="primary" :loading="exporting" @click="onExport">导出报表(CSV)</el-button>
    </div>
    <el-row :gutter="16" class="cards">
      <el-col v-for="s in stats" :key="s.label" :xs="12" :sm="8" :md="6">
        <el-card shadow="hover">
          <div class="card-label">{{ s.label }}</div>
          <div class="card-value">{{ s.value }}</div>
          <div class="card-hint">{{ s.hint }}</div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import * as echarts from 'echarts'
import { getDashboardOverview, exportReport } from '../api/console'
import { ElMessage } from 'element-plus'
import { downloadBlob } from '../utils/file'

const stats = ref([])
const exporting = ref(false)

async function load() {
  const { data } = await getDashboardOverview()
  stats.value = (data.highlights || []).map((h) => ({ label: h.label, value: h.value, hint: h.hint }))
}

async function onExport() {
  exporting.value = true
  try {
    const res = await exportReport('overview')
    downloadBlob(res.data, `report-overview-${Date.now()}.csv`)
    ElMessage.success('导出成功')
  } catch (e) {
    ElMessage.error('导出失败：无权限或网络异常')
  } finally {
    exporting.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.page-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.cards { margin-bottom: 8px; }
.card-label { color: #909399; font-size: 13px; }
.card-value { font-size: 26px; font-weight: 600; margin: 6px 0; color: #303133; }
.card-hint { font-size: 12px; color: #c0c4cc; }
</style>
