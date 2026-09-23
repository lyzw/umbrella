<template>
  <div class="page">
    <div class="page-head">
      <h2>餐食看板</h2>
      <el-button type="primary" :loading="exporting" @click="onExport">导出报表(CSV)</el-button>
    </div>
    <el-row :gutter="16">
      <el-col :xs="24" :md="14">
        <el-card shadow="hover" title="想吃热度 Top">
          <div ref="topChart" class="chart"></div>
        </el-card>
      </el-col>
      <el-col :xs="24" :md="10">
        <el-card shadow="hover" title="确认单来源占比">
          <div ref="srcChart" class="chart"></div>
        </el-card>
      </el-col>
    </el-row>
    <el-row :gutter="16" class="mt">
      <el-col :span="8"><el-card shadow="hover"><div class="kpi">确认单总量</div><div class="kpi-num">{{ d.confirmTotal }}</div></el-card></el-col>
      <el-col :span="8"><el-card shadow="hover"><div class="kpi">超额率</div><div class="kpi-num">{{ d.overLimitRate }}%</div></el-card></el-col>
      <el-col :span="8"><el-card shadow="hover"><div class="kpi">想吃 Top 数目</div><div class="kpi-num">{{ d.wantEatTop.length }}</div></el-card></el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, nextTick } from 'vue'
import * as echarts from 'echarts'
import { getDashboardMeals, exportReport } from '../api/console'
import { ElMessage } from 'element-plus'
import { downloadBlob } from '../utils/file'

const d = reactive({ confirmTotal: 0, overLimitRate: 0, wantEatTop: [], sourceRatio: [] })
const topChart = ref(null)
const srcChart = ref(null)
const exporting = ref(false)

async function load() {
  const { data } = await getDashboardMeals()
  Object.assign(d, data)
  await nextTick()
  renderTop()
  renderSource()
}

function renderTop() {
  const chart = echarts.init(topChart.value)
  chart.setOption({
    tooltip: {},
    grid: { left: 90, right: 20, top: 20, bottom: 20 },
    xAxis: { type: 'value' },
    yAxis: { type: 'category', data: d.wantEatTop.map((t) => t.dishName).reverse() },
    series: [{ type: 'bar', data: d.wantEatTop.map((t) => t.count).reverse(), itemStyle: { color: '#67c23a' } }]
  })
}

function renderSource() {
  const chart = echarts.init(srcChart.value)
  chart.setOption({
    tooltip: {},
    legend: { bottom: 0 },
    series: [{
      type: 'pie',
      radius: ['40%', '70%'],
      data: d.sourceRatio.map((s) => ({ name: s.sourceType, value: s.count }))
    }]
  })
}

async function onExport() {
  exporting.value = true
  try {
    const res = await exportReport('meals')
    downloadBlob(res.data, `report-meals-${Date.now()}.csv`)
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
.mt { margin-top: 16px; }
.kpi { color: #909399; font-size: 13px; }
.kpi-num { font-size: 24px; font-weight: 600; margin-top: 6px; color: #303133; }
</style>
