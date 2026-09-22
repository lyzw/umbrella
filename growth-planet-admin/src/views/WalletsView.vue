<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { wallets, allowanceLogs, exportBizCsv } from '../api/console'

const active = ref('wallets')
const loading = ref(false)
const walletRows = ref([])
const logRows = ref([])
const walletTotal = ref(0)
const logTotal = ref(0)
const walletQuery = reactive({ page: 1, pageSize: 10, familyId: '', childId: '' })
const logQuery = reactive({ page: 1, pageSize: 10, familyId: '', childId: '', transType: '', from: '', to: '' })

const TRANS = { GRANT: '发放', DEDUCT: '扣款', REFUND: '退款', ADJUST: '调整' }

async function loadWallets() {
  loading.value = true
  try {
    const { data } = await wallets(walletQuery)
    walletRows.value = data.items || []
    walletTotal.value = Number(data.total || 0)
  } finally {
    loading.value = false
  }
}

async function loadLogs() {
  loading.value = true
  try {
    const { data } = await allowanceLogs(logQuery)
    logRows.value = data.items || []
    logTotal.value = Number(data.total || 0)
  } finally {
    loading.value = false
  }
}

async function doExport() {
  try {
    await exportBizCsv(active.value === 'wallets' ? 'wallets' : 'allowance-logs',
      active.value === 'wallets' ? walletQuery : logQuery)
    ElMessage.success('已导出 CSV')
  } catch (e) {
    ElMessage.error(e.message || '导出失败')
  }
}

onMounted(loadWallets)
</script>

<template>
  <el-card shadow="never">
    <el-tabs v-model="active" @tab-change="(name) => (name === 'wallets' ? loadWallets() : loadLogs())">
      <el-tab-pane label="钱包余额" name="wallets">
        <div style="display:flex;gap:12px;flex-wrap:wrap;align-items:center">
          <el-input v-model="walletQuery.familyId" placeholder="家庭 ID" clearable style="width:130px" />
          <el-input v-model="walletQuery.childId" placeholder="孩子 ID" clearable style="width:130px" />
          <el-button type="primary" @click="walletQuery.page = 1; loadWallets()">查询</el-button>
          <el-button @click="doExport">导出 CSV</el-button>
        </div>
        <el-table :data="walletRows" v-loading="loading" style="margin-top:16px">
          <el-table-column prop="childName" label="孩子" width="120" />
          <el-table-column prop="childId" label="孩子ID" width="90" />
          <el-table-column prop="familyId" label="家庭" width="90" />
          <el-table-column label="余额(¥)" width="110">
            <template #default="{ row }"><b>{{ row.balance }}</b></template>
          </el-table-column>
          <el-table-column prop="status" label="状态" width="100" />
          <el-table-column label="单次限额" width="100">
            <template #default="{ row }">{{ row.singleLimit ?? '-' }}</template>
          </el-table-column>
          <el-table-column label="日限额/已用" width="130">
            <template #default="{ row }">{{ row.dailyLimit ?? '-' }} / {{ row.dailyUsed ?? '-' }}</template>
          </el-table-column>
          <el-table-column label="周限额/已用" min-width="130">
            <template #default="{ row }">{{ row.weeklyLimit ?? '-' }} / {{ row.weeklyUsed ?? '-' }}</template>
          </el-table-column>
          <el-table-column prop="updateTime" label="更新时间" width="170" />
        </el-table>
        <el-pagination style="margin-top:16px;justify-content:flex-end" layout="total, prev, pager, next"
          :total="walletTotal" :page-size="walletQuery.pageSize" :current-page="walletQuery.page"
          @current-change="(p) => { walletQuery.page = p; loadWallets() }" />
      </el-tab-pane>

      <el-tab-pane label="零花钱流水" name="logs">
        <div style="display:flex;gap:12px;flex-wrap:wrap;align-items:center">
          <el-input v-model="logQuery.familyId" placeholder="家庭 ID" clearable style="width:130px" />
          <el-input v-model="logQuery.childId" placeholder="孩子 ID" clearable style="width:130px" />
          <el-select v-model="logQuery.transType" placeholder="交易类型" clearable style="width:120px">
            <el-option v-for="(v, k) in TRANS" :key="k" :label="v" :value="k" />
          </el-select>
          <el-date-picker v-model="logQuery.from" type="date" placeholder="开始日期" value-format="YYYY-MM-DD" style="width:150px" />
          <el-date-picker v-model="logQuery.to" type="date" placeholder="结束日期" value-format="YYYY-MM-DD" style="width:150px" />
          <el-button type="primary" @click="logQuery.page = 1; loadLogs()">查询</el-button>
          <el-button @click="doExport">导出 CSV</el-button>
        </div>
        <el-table :data="logRows" v-loading="loading" style="margin-top:16px">
          <el-table-column prop="usageDate" label="使用日期" width="110" />
          <el-table-column prop="childName" label="孩子" width="110" />
          <el-table-column prop="familyId" label="家庭" width="90" />
          <el-table-column label="类型" width="90">
            <template #default="{ row }">{{ TRANS[row.transType] || row.transType }}</template>
          </el-table-column>
          <el-table-column prop="scene" label="场景" width="110" />
          <el-table-column label="金额(¥)" width="100">
            <template #default="{ row }">+{{ row.amount }}</template>
          </el-table-column>
          <el-table-column label="变动前→后" width="140">
            <template #default="{ row }">{{ row.balanceBefore }} → {{ row.balanceAfter }}</template>
          </el-table-column>
          <el-table-column prop="reason" label="原因" min-width="140" show-overflow-tooltip />
          <el-table-column prop="createTime" label="时间" width="170" />
        </el-table>
        <el-pagination style="margin-top:16px;justify-content:flex-end" layout="total, prev, pager, next"
          :total="logTotal" :page-size="logQuery.pageSize" :current-page="logQuery.page"
          @current-change="(p) => { logQuery.page = p; loadLogs() }" />
      </el-tab-pane>
    </el-tabs>
  </el-card>
</template>
