<template>
  <div class="page">
    <div class="page-head">
      <h2>家务与健康看板</h2>
      <el-button type="primary" :loading="exporting" @click="onExport">导出报表(CSV)</el-button>
    </div>
    <el-row :gutter="16">
      <el-col :xs="24" :md="12">
        <el-card shadow="hover" title="任务完成率 / 打卡覆盖率">
          <div ref="rateChart" class="chart"></div>
        </el-card>
      </el-col>
      <el-col :xs="24" :md="12">
        <el-card shadow="hover" title="连续打卡天数分布">
          <div ref="streakChart" class="chart"></div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { reactive, ref, onMounted, nextTick } from 'vue'
import * as echarts from 'echarts'
import { getDashboardChores, exportReport } from '../api/console'
import { ElMessage } from 'element-plus'
import { downloadBlob } from '../utils/file'

const d = reactive({ taskCompletionRate: 0, checkCoverageRate: 0, streakDistribution: [] })
const rateChart = ref(null)
const streakChart = ref(null)
const exporting = ref(false)

async function load() {
  const { data } = await getDashboardChores()
  Object.assign(d, data)
  await nextTick()
  const rc = echarts.init(rateChart.value)
  rc.setOption({
    tooltip: {},
    legend: { bottom: 0 },
    series: [{
      type: 'pie',
      radius: ['40%', '70%'],
      data: [
        { name: '任务完成率', value: d.taskCompletionRate },
        { name: '打卡覆盖率', value: d.checkCoverageRate }
      ]
    }]
  })
  const sc = echarts.init(streakChart.value)
  sc.setOption({
    tooltip: {},
    grid: { left: 50, right: 20, top: 20, bottom: 30 },
    xAxis: { type: 'category', data: d.streakDistribution.map((b) => b.bucket + '天') },
    yAxis: { type: 'value' },
    series: [{ type: 'bar', data: d.streakDistribution.map((b) => b.count), itemStyle: { color: '#409eff' } }]
  })
}

async function onExport() {
  exporting.value = true
  try {
    const res = await exportReport('chores')
    downloadBlob(res.data, `report-chores-${Date.now()}.csv`)
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
.chart { height: 320px; }
</style>
