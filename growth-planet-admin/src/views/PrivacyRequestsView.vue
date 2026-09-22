<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listPrivacyRequests, verifyPrivacyRequest, rejectPrivacyRequest } from '../api/console'

const loading = ref(false)
const rows = ref([])
const total = ref(0)
const query = reactive({ page: 1, pageSize: 10, childId: '', requestType: '', status: '' })

const TYPES = { EXPORT: '数据导出', DELETE: '数据删除' }
const STATUS = {
  RECEIVED: { label: '待核验', type: 'warning' },
  PROCESSING: { label: '处理中', type: 'primary' },
  READY: { label: '待取件', type: 'success' },
  COMPLETED: { label: '已完成', type: 'success' },
  FAILED: { label: '失败', type: 'danger' },
  REJECTED: { label: '已驳回', type: 'danger' },
  EXPIRED: { label: '已过期', type: 'info' }
}

// 核验抽屉（L4：线下核身凭证 → 哈希落库，明文不保存）
const verifyOpen = ref(false)
const verifyRow = ref(null)
const verifyCode = ref('')
// 驳回抽屉（L4）
const rejectOpen = ref(false)
const rejectRow = ref(null)
const rejectReason = ref('')

async function load() {
  loading.value = true
  try {
    const { data } = await listPrivacyRequests(query)
    rows.value = data.items || []
    total.value = Number(data.total || 0)
  } finally {
    loading.value = false
  }
}

function openVerify(row) {
  verifyRow.value = row
  verifyCode.value = ''
  verifyOpen.value = true
}

async function doVerify() {
  if (!verifyCode.value.trim()) {
    ElMessage.warning('请输入线下核验码')
    return
  }
  try {
    await ElMessageBox.confirm(
      `确认为工单 #${verifyRow.value.id} 完成监护人身份核验？核验码将以哈希落库、明文不保存，工单将进入处理中。`,
      'L4 敏感操作确认', { type: 'warning', confirmButtonText: '确认核验', cancelButtonText: '取消' })
  } catch { return }
  try {
    await verifyPrivacyRequest(verifyRow.value.id, verifyCode.value.trim())
    ElMessage.success('核验完成，工单已进入处理中')
    verifyOpen.value = false
    load()
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || '核验失败')
  }
}

function openReject(row) {
  rejectRow.value = row
  rejectReason.value = ''
  rejectOpen.value = true
}

async function doReject() {
  if (!rejectReason.value.trim()) {
    ElMessage.warning('请填写驳回原因')
    return
  }
  try {
    await ElMessageBox.confirm(
      `确认为工单 #${rejectRow.value.id} 填写驳回结论？原因将落审计留痕。`,
      'L4 敏感操作确认', { type: 'warning', confirmButtonText: '确认驳回', cancelButtonText: '取消' })
  } catch { return }
  try {
    await rejectPrivacyRequest(rejectRow.value.id, rejectReason.value.trim())
    ElMessage.success('已驳回')
    rejectOpen.value = false
    load()
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || '驳回失败')
  }
}

const fmt = (t) => (t ? new Date(t).toLocaleString() : '—')

onMounted(load)
</script>

<template>
  <el-card shadow="never">
    <div style="display:flex;gap:12px;flex-wrap:wrap;align-items:center">
      <el-input v-model="query.childId" placeholder="孩子 ID" clearable style="width:130px" />
      <el-select v-model="query.requestType" placeholder="工单类型" clearable style="width:130px">
        <el-option v-for="(v, k) in TYPES" :key="k" :label="v" :value="k" />
      </el-select>
      <el-select v-model="query.status" placeholder="状态" clearable style="width:130px">
        <el-option v-for="(v, k) in STATUS" :key="k" :label="v.label" :value="k" />
      </el-select>
      <el-button type="primary" @click="query.page = 1; load()">查询</el-button>
    </div>

    <el-table :data="rows" v-loading="loading" style="margin-top:16px">
      <el-table-column prop="id" label="工单号" width="90" />
      <el-table-column label="类型" width="100">
        <template #default="{ row }">{{ TYPES[row.requestType] || row.requestType }}</template>
      </el-table-column>
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="STATUS[row.status]?.type || 'info'">{{ STATUS[row.status]?.label || row.status }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="孩子" width="130">
        <template #default="{ row }">{{ row.childName || `#${row.childId}` }}</template>
      </el-table-column>
      <el-table-column prop="familyId" label="家庭" width="80" />
      <el-table-column label="核验时间" width="170">
        <template #default="{ row }">{{ fmt(row.verifiedAt) }}</template>
      </el-table-column>
      <el-table-column label="备注/原因" min-width="180">
        <template #default="{ row }">{{ row.errorCode || row.resultRef || '—' }}</template>
      </el-table-column>
      <el-table-column prop="createTime" label="申请时间" width="170" />
      <el-table-column label="操作" width="170" fixed="right">
        <template #default="{ row }">
          <el-button v-if="row.status === 'RECEIVED'" size="small" type="primary" @click="openVerify(row)">核验</el-button>
          <el-button v-if="['RECEIVED', 'PROCESSING'].includes(row.status)" size="small" type="danger" plain @click="openReject(row)">驳回</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      v-model:current-page="query.page" v-model:page-size="query.pageSize"
      :total="total" layout="total, prev, pager, next" style="margin-top:16px"
      @current-change="load" />

    <el-dialog v-model="verifyOpen" title="监护人身份核验" width="460px">
      <el-alert type="warning" :closable="false" style="margin-bottom:12px"
        title="L4 敏感操作：核验码将 SHA-256 哈希后落库，明文不保存；动作全程审计。" />
      <p v-if="verifyRow" style="margin:0 0 12px;color:#909399">
        工单 #{{ verifyRow.id }} · {{ TYPES[verifyRow.requestType] }} · 孩子 #{{ verifyRow.childId }}
      </p>
      <el-input v-model="verifyCode" placeholder="输入线下核验码（1-128 位）" maxlength="128" show-word-limit />
      <template #footer>
        <el-button @click="verifyOpen = false">取消</el-button>
        <el-button type="primary" @click="doVerify">确认核验</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="rejectOpen" title="驳回隐私工单" width="460px">
      <el-alert type="warning" :closable="false" style="margin-bottom:12px"
        title="L4 敏感操作：驳回原因将写入工单并落审计留痕。" />
      <el-input v-model="rejectReason" type="textarea" :rows="3" maxlength="255" show-word-limit
        placeholder="驳回原因（必填，如：核验材料不足，已联系监护人补充）" />
      <template #footer>
        <el-button @click="rejectOpen = false">取消</el-button>
        <el-button type="danger" @click="doReject">确认驳回</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>
