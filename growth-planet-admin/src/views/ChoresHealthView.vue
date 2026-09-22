<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { choreInstances, checkRecords, exportBizCsv } from '../api/console'

const active = ref('chores')
const loading = ref(false)
const choreRows = ref([])
const recordRows = ref([])
const choreTotal = ref(0)
const recordTotal = ref(0)
const summary = ref(null)
const choreQuery = reactive({ page: 1, pageSize: 10, familyId: '', childId: '', status: '', from: '', to: '' })
const recordQuery = reactive({ page: 1, pageSize: 10, familyId: '', childId: '', from: '', to: '' })

const CHORE_STATUS = {
  CLAIMED: { label: '已领取', type: 'info' },
  SUBMITTED: { label: '待确认', type: 'warning' },
  CONFIRMED: { label: '已确认', type: 'success' },
  REJECTED: { label: '被驳回', type: 'danger' }
}

async function loadChores() {
  loading.value = true
  try {
    const { data } = await choreInstances(choreQuery)
    choreRows.value = data.items || []
    choreTotal.value = Number(data.total || 0)
    summary.value = data
  } finally {
    loading.value = false
  }
}

async function loadRecords() {
  loading.value = true
  try {
    const { data } = await checkRecords(recordQuery)
    recordRows.value = data.items || []
    recordTotal.value = Number(data.total || 0)
  } finally {
    loading.value = false
  }
}

async function doExport() {
  try {
    await exportBizCsv(active.value === 'chores' ? 'chore-instances' : 'check-records',
      active.value === 'chores' ? choreQuery : recordQuery)
    ElMessage.success('已导出 CSV')
  } catch (e) {
    ElMessage.error(e.message || '导出失败')
  }
}

onMounted(loadChores)
</script>

<template>
  <el-card shadow="never">
    <el-tabs v-model="active" @tab-change="(name) => (name === 'chores' ? loadChores() : loadRecords())">
      <el-tab-pane label="家务完成" name="chores">
        <div style="display:flex;gap:12px;flex-wrap:wrap;align-items:center">
          <el-input v-model="choreQuery.familyId" placeholder="家庭 ID" clearable style="width:130px" />
          <el-input v-model="choreQuery.childId" placeholder="孩子 ID" clearable style="width:130px" />
          <el-select v-model="choreQuery.status" placeholder="状态" clearable style="width:120px">
            <el-option v-for="(v, k) in CHORE_STATUS" :key="k" :label="v.label" :value="k" />
          </el-select>
          <el-date-picker v-model="choreQuery.from" type="date" placeholder="开始日期" value-format="YYYY-MM-DD" style="width:150px" />
          <el-date-picker v-model="choreQuery.to" type="date" placeholder="结束日期" value-format="YYYY-MM-DD" style="width:150px" />
          <el-button type="primary" @click="choreQuery.page = 1; loadChores()">查询</el-button>
          <el-button @click="doExport">导出 CSV</el-button>
        </div>
        <div v-if="summary" style="display:flex;gap:20px;margin-top:14px;align-items:center">
          <el-statistic title="完成率" :value="summary.completionRate" suffix="%" />
          <el-tag v-for="(n, s) in summary.statusCounts" :key="s"
                  :type="CHORE_STATUS[s]?.type || 'info'">{{ CHORE_STATUS[s]?.label || s }} {{ n }}</el-tag>
        </div>
        <el-table :data="choreRows" v-loading="loading" style="margin-top:14px">
          <el-table-column prop="claimDate" label="领取日期" width="110" />
          <el-table-column prop="childName" label="孩子" width="110" />
          <el-table-column prop="familyId" label="家庭" width="90" />
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag size="small" :type="CHORE_STATUS[row.status]?.type || 'info'">{{ CHORE_STATUS[row.status]?.label || row.status }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="奖励(¥)" width="100">
            <template #default="{ row }">{{ row.rewardAmount }}{{ row.rewardGranted ? ' ✓' : '' }}</template>
          </el-table-column>
          <el-table-column prop="medalCode" label="勋章" width="140" />
          <el-table-column prop="rejectReason" label="驳回原因" min-width="130" show-overflow-tooltip />
          <el-table-column prop="submitTime" label="提交时间" width="170" />
          <el-table-column prop="confirmTime" label="确认时间" width="170" />
        </el-table>
        <el-pagination style="margin-top:16px;justify-content:flex-end" layout="total, prev, pager, next"
          :total="choreTotal" :page-size="choreQuery.pageSize" :current-page="choreQuery.page"
          @current-change="(p) => { choreQuery.page = p; loadChores() }" />
      </el-tab-pane>

      <el-tab-pane label="健康打卡" name="records">
        <div style="display:flex;gap:12px;flex-wrap:wrap;align-items:center">
          <el-input v-model="recordQuery.familyId" placeholder="家庭 ID" clearable style="width:130px" />
          <el-input v-model="recordQuery.childId" placeholder="孩子 ID" clearable style="width:130px" />
          <el-date-picker v-model="recordQuery.from" type="date" placeholder="开始日期" value-format="YYYY-MM-DD" style="width:150px" />
          <el-date-picker v-model="recordQuery.to" type="date" placeholder="结束日期" value-format="YYYY-MM-DD" style="width:150px" />
          <el-button type="primary" @click="recordQuery.page = 1; loadRecords()">查询</el-button>
          <el-button @click="doExport">导出 CSV</el-button>
        </div>
        <el-table :data="recordRows" v-loading="loading" style="margin-top:16px">
          <el-table-column prop="checkDate" label="打卡日期" width="110" />
          <el-table-column prop="childName" label="孩子" width="110" />
          <el-table-column prop="familyId" label="家庭" width="90" />
          <el-table-column prop="itemName" label="打卡项" min-width="130" />
          <el-table-column prop="checkTime" label="打卡时间" width="170" />
        </el-table>
        <el-pagination style="margin-top:16px;justify-content:flex-end" layout="total, prev, pager, next"
          :total="recordTotal" :page-size="recordQuery.pageSize" :current-page="recordQuery.page"
          @current-change="(p) => { recordQuery.page = p; loadRecords() }" />
      </el-tab-pane>
    </el-tabs>
  </el-card>
</template>
