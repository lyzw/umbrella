<template>
  <div class="page">
    <div class="page-head">
      <h2>勋章看板</h2>
      <el-button type="primary" :loading="exporting" @click="onExport">导出报表(CSV)</el-button>
    </div>
    <el-row :gutter="16" class="mt">
      <el-col :span="8"><el-card shadow="hover"><div class="kpi">勋章发放总量</div><div class="kpi-num">{{ d.awardTotal }}</div></el-card></el-col>
      <el-col :span="8"><el-card shadow="hover"><div class="kpi">获得率</div><div class="kpi-num">{{ d.obtainRate }}%</div></el-card></el-col>
      <el-col :span="8"><el-card shadow="hover"><div class="kpi">热门勋章数</div><div class="kpi-num">{{ d.topMedals.length }}</div></el-card></el-col>
    </el-row>
    <el-card shadow="hover" title="热门勋章 Top" class="mt">
      <div ref="bar" class="chart"></div>
    </el-card>
  </div>
</template>

<script setup>
import { reactive, ref, onMounted, nextTick } from 'vue'
import * as echarts from 'echarts'
import { getDashboardMedals, exportReport } from '../api/console'
import { ElMessage } from 'element-plus'
import { downloadBlob } from '../utils/file'

const d = reactive({ awardTotal: 0, obtainRate: 0, topMedals: [] })
const bar = ref(null)
const exporting = ref(false)

async function load() {
  const { data } = await getDashboardMedals()
  Object.assign(d, data)
  await nextTick()
  const chart = echarts.init(bar.value)
  chart.setOption({
    tooltip: {},
    grid: { left: 100, right: 20, top: 20, bottom: 20 },
    xAxis: { type: 'value' },
    yAxis: { type: 'category', data: d.topMedals.map((m) => m.name).reverse() },
    series: [{ type: 'bar', data: d.topMedals.map((m) => m.count).reverse(), itemStyle: { color: '#e6a23c' } }]
  })
}

async function onExport() {
  exporting.value = true
  try {
    const blob = await exportReport('medals')
    downloadBlob(blob, `report-medals-${Date.now()}.csv`)
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
