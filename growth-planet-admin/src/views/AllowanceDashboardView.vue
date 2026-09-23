<template>
  <div class="page">
    <div class="page-head">
      <h2>零花钱看板</h2>
      <el-button type="primary" :loading="exporting" @click="onExport">导出报表(CSV)</el-button>
    </div>
    <el-row :gutter="16" class="mt">
      <el-col :span="6"><el-card shadow="hover"><div class="kpi">累计发放(分)</div><div class="kpi-num">{{ d.grantedTotal }}</div></el-card></el-col>
      <el-col :span="6"><el-card shadow="hover"><div class="kpi">累计消耗(分)</div><div class="kpi-num">{{ d.consumedTotal }}</div></el-card></el-col>
      <el-col :span="6"><el-card shadow="hover"><div class="kpi">钱包沉淀(分)</div><div class="kpi-num">{{ d.balanceTotal }}</div></el-card></el-col>
      <el-col :span="6"><el-card shadow="hover"><div class="kpi">消耗/发放</div><div class="kpi-num">{{ d.grantConsumeRatio }}%</div></el-card></el-col>
    </el-row>
    <el-card shadow="hover" title="发放 vs 消耗" class="mt">
      <div ref="pie" class="chart"></div>
    </el-card>
  </div>
</template>

<script setup>
import { reactive, ref, onMounted, nextTick } from 'vue'
import * as echarts from 'echarts'
import { getDashboardAllowance, exportReport } from '../api/console'
import { ElMessage } from 'element-plus'
import { downloadBlob } from '../utils/file'

const d = reactive({ grantedTotal: 0, consumedTotal: 0, balanceTotal: 0, grantConsumeRatio: 0 })
const pie = ref(null)
const exporting = ref(false)

async function load() {
  const { data } = await getDashboardAllowance()
  Object.assign(d, data)
  await nextTick()
  const chart = echarts.init(pie.value)
  chart.setOption({
    tooltip: {},
    legend: { bottom: 0 },
    series: [{
      type: 'pie',
      radius: ['40%', '70%'],
      data: [
        { name: '累计发放', value: d.grantedTotal },
        { name: '累计消耗', value: d.consumedTotal }
      ]
    }]
  })
}

async function onExport() {
  exporting.value = true
  try {
    const res = await exportReport('allowance')
    downloadBlob(res.data, `report-allowance-${Date.now()}.csv`)
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
.kpi { color: #909399; font-size: 13px; }
.kpi-num { font-size: 24px; font-weight: 600; margin-top: 6px; color: #303133; }
.mt { margin-top: 16px; }
.chart { height: 320px; }
</style>
