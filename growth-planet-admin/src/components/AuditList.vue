<template>
  <el-card shadow="never">
    <!-- 筛选区 -->
    <div class="toolbar">
      <el-input v-model="filter.action" placeholder="动作（如 WALLET_GRANT）" clearable style="width: 190px" @keyup.enter="load(1)" />
      <el-input v-model="filter.targetType" placeholder="目标类型（如 WALLET）" clearable style="width: 170px" @keyup.enter="load(1)" />
      <el-select v-model="filter.result" placeholder="结果" clearable style="width: 110px">
        <el-option label="成功" value="SUCCESS" />
        <el-option label="失败" value="FAILURE" />
        <el-option label="已提交" value="SUBMITTED" />
      </el-select>
      <el-date-picker v-model="dateRange" type="daterange" value-format="YYYY-MM-DD"
                      start-placeholder="开始日期" end-placeholder="结束日期" style="width: 240px" />
      <el-button type="primary" @click="load(1)">查询</el-button>
      <el-button v-if="canExport && mode === 'admin'" type="warning" plain :loading="exporting" @click="onExport">
        导出 CSV
      </el-button>
    </div>

    <!-- 列表 -->
    <el-table :data="rows" v-loading="loading" stripe size="small">
      <el-table-column prop="id" label="编号" width="80" />
      <el-table-column prop="actorLabel" label="操作人" min-width="140" show-overflow-tooltip />
      <el-table-column prop="action" label="动作" width="170" show-overflow-tooltip />
      <el-table-column prop="targetType" label="目标类型" width="140" show-overflow-tooltip />
      <el-table-column prop="targetId" label="目标ID" width="90" />
      <el-table-column prop="familyId" label="家庭" width="80" />
      <el-table-column prop="result" label="结果" width="90">
        <template #default="{ row }">
          <el-tag :type="row.result === 'SUCCESS' ? 'success' : row.result === 'FAILURE' ? 'danger' : 'info'" size="small">
            {{ row.result }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="时间" width="170" />
      <el-table-column label="" width="70" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openDetail(row)">详情</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination class="pager" layout="total, prev, pager, next" :total="total"
                   :page-size="filter.pageSize" :current-page="filter.page" @current-change="load" />

    <!-- 详情抽屉 -->
    <el-drawer v-model="detail.visible" title="审计详情" size="420px">
      <el-descriptions :column="1" border size="small" v-if="detail.data">
        <el-descriptions-item label="编号">{{ detail.data.id }}</el-descriptions-item>
        <el-descriptions-item label="操作人">{{ detail.data.actorLabel }}（#{{ detail.data.actorUserId }}）</el-descriptions-item>
        <el-descriptions-item label="动作">{{ detail.data.action }}</el-descriptions-item>
        <el-descriptions-item label="目标">{{ detail.data.targetType }} #{{ detail.data.targetId }}</el-descriptions-item>
        <el-descriptions-item label="家庭">{{ detail.data.familyId ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="结果">{{ detail.data.result }} {{ detail.data.errorCode || '' }}</el-descriptions-item>
        <el-descriptions-item label="IP">{{ detail.data.ip || '-' }}</el-descriptions-item>
        <el-descriptions-item label="请求 ID">{{ detail.data.requestId || '-' }}</el-descriptions-item>
        <el-descriptions-item label="时间">{{ detail.data.createTime }}</el-descriptions-item>
        <el-descriptions-item label="明细">
          <pre class="detail-json">{{ prettyDetail }}</pre>
        </el-descriptions-item>
      </el-descriptions>
    </el-drawer>
  </el-card>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { auditLogs, cAuditLogs, auditLogDetail, exportAuditLogs } from '../api/console'
import { hasPerm } from '../stores/auth'

// mode: 'admin' = 全量操作日志；'c-end' = C 端关键操作（白名单动作）
const props = defineProps({ mode: { type: String, default: 'admin' } })

const canExport = computed(() => hasPerm('操作日志', 'export'))
const exporter = props.mode === 'admin' ? auditLogs : cAuditLogs

const rows = ref([])
const total = ref(0)
const loading = ref(false)
const exporting = ref(false)
const dateRange = ref(null)
const filter = reactive({
  action: '', targetType: '', result: '', page: 1, pageSize: 20,
  get begin() { return dateRange.value?.[0] || '' },
  get end() { return dateRange.value?.[1] || '' }
})
const detail = reactive({ visible: false, data: null })

const prettyDetail = computed(() => {
  const raw = detail.data?.detail
  if (!raw) return '-'
  try { return JSON.stringify(JSON.parse(raw), null, 2) } catch { return raw }
})

async function load(page = filter.page) {
  loading.value = true
  filter.page = page
  try {
    const { data } = await exporter(filter)
    rows.value = data.items
    total.value = data.total
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    loading.value = false
  }
}

async function openDetail(row) {
  try {
    detail.data = (await auditLogDetail(row.id)).data
    detail.visible = true
  } catch (e) {
    ElMessage.error(e.message)
  }
}

async function onExport() {
  exporting.value = true
  try {
    await exportAuditLogs(filter)
    ElMessage.success('已导出（该操作已记入审计）')
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    exporting.value = false
  }
}

onMounted(() => load(1))
</script>

<style scoped>
.toolbar { display: flex; gap: 10px; margin-bottom: 14px; flex-wrap: wrap; }
.pager { margin-top: 14px; justify-content: flex-end; }
.detail-json { margin: 0; font-size: 12px; white-space: pre-wrap; word-break: break-all; }
</style>
