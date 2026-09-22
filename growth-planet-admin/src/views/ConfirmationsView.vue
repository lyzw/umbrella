<script setup>
import { ref, reactive, onMounted, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { confirmations, confirmationDetail, reviewConfirmation, exportBizCsv } from '../api/console'
import { hasPerm } from '../stores/auth'

const loading = ref(false)
const rows = ref([])
const total = ref(0)
const query = reactive({
  page: 1, pageSize: 10, familyId: '', childId: '', status: '', overLimit: '', from: '', to: ''
})

const canReview = computed(() => hasPerm('确认单', 'approve') || hasPerm('确认单', 'view'))

const STATUS = {
  PENDING: { label: '待确认', type: 'warning' },
  COMPLETED: { label: '已完成', type: 'success' },
  CANCELLED: { label: '已撤回', type: 'info' },
  REJECTED: { label: '已驳回', type: 'danger' }
}

const detailVisible = ref(false)
const detail = ref(null)

async function load() {
  loading.value = true
  try {
    const { data } = await confirmations(query)
    rows.value = data.items || []
    total.value = Number(data.total || 0)
  } finally {
    loading.value = false
  }
}

async function openDetail(row) {
  const { data } = await confirmationDetail(row.id)
  detail.value = data
  detailVisible.value = true
}

/** 超额复核（L4）：结论仅落审计，不改业务状态 */
async function review(row) {
  const { value } = await ElMessageBox.prompt(
    '复核说明（必填）。注意：复核结论仅记录到审计日志，不会改变确认单状态与钱包余额。',
    `超额复核「${row.confirmNo}」`, { inputValidator: (v) => (v && v.trim() ? true : '说明不能为空') })
  const { value: decision } = await ElMessageBox.confirm(
    '确认提交复核？RESOLVED=关注无风险/已闭环；FOLLOW_UP=需继续跟进。', '选择结论', {
      type: 'warning',
      confirmButtonText: 'RESOLVED',
      cancelButtonText: 'FOLLOW_UP',
      distinguishCancelAndClose: true
    }).then(() => 'RESOLVED').catch((action) => (action === 'cancel' ? 'FOLLOW_UP' : null))
  if (!decision) return
  try {
    await reviewConfirmation(row.id, { decision, note: value.trim() })
    ElMessage.success('复核已记录（审计留痕）')
    load()
  } catch (e) {
    ElMessage.error(e.message || '复核失败')
  }
}

async function doExport() {
  try {
    await exportBizCsv('confirmations', query)
    ElMessage.success('已导出 CSV')
  } catch (e) {
    ElMessage.error(e.message || '导出失败')
  }
}

onMounted(load)
</script>

<template>
  <el-card shadow="never">
    <div style="display:flex;gap:12px;flex-wrap:wrap;align-items:center">
      <el-input v-model="query.familyId" placeholder="家庭 ID" clearable style="width:130px" />
      <el-input v-model="query.childId" placeholder="孩子 ID" clearable style="width:130px" />
      <el-select v-model="query.status" placeholder="状态" clearable style="width:120px">
        <el-option v-for="(v, k) in STATUS" :key="k" :label="v.label" :value="k" />
      </el-select>
      <el-select v-model="query.overLimit" placeholder="超额" clearable style="width:100px">
        <el-option label="仅超额" value="true" />
        <el-option label="未超额" value="false" />
      </el-select>
      <el-date-picker v-model="query.from" type="date" placeholder="开始日期" value-format="YYYY-MM-DD" style="width:150px" />
      <el-date-picker v-model="query.to" type="date" placeholder="结束日期" value-format="YYYY-MM-DD" style="width:150px" />
      <el-button type="primary" @click="query.page = 1; load()">查询</el-button>
      <el-button @click="doExport">导出 CSV</el-button>
    </div>

    <el-table :data="rows" v-loading="loading" style="margin-top:16px">
      <el-table-column prop="confirmNo" label="确认单号" width="170" show-overflow-tooltip />
      <el-table-column prop="menuDate" label="菜单日期" width="110" />
      <el-table-column label="餐次" width="80">
        <template #default="{ row }">{{ { BREAKFAST: '早餐', LUNCH: '午餐', DINNER: '晚餐' }[row.mealType] || row.mealType }}</template>
      </el-table-column>
      <el-table-column prop="childName" label="孩子" width="110" />
      <el-table-column prop="familyId" label="家庭" width="80" />
      <el-table-column label="金额(¥)" width="100">
        <template #default="{ row }">{{ row.totalAmount }}</template>
      </el-table-column>
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag size="small" :type="STATUS[row.status]?.type || 'info'">{{ STATUS[row.status]?.label || row.status }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="超额" width="80">
        <template #default="{ row }">
          <el-tag v-if="row.isOverLimit" type="danger" size="small">超额</el-tag>
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column prop="submitTime" label="提交时间" width="170" />
      <el-table-column label="操作" width="130" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openDetail(row)">详情</el-button>
          <el-button v-if="canReview && row.isOverLimit" link type="warning" @click="review(row)">复核</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-pagination style="margin-top:16px;justify-content:flex-end" layout="total, prev, pager, next"
      :total="total" :page-size="query.pageSize" :current-page="query.page"
      @current-change="(p) => { query.page = p; load() }" />

    <el-dialog v-model="detailVisible" :title="`确认单 ${detail?.confirmNo || ''}`" width="640px">
      <template v-if="detail">
        <el-descriptions :column="2" size="small" border>
          <el-descriptions-item label="菜单日期">{{ detail.menuDate }}</el-descriptions-item>
          <el-descriptions-item label="餐次">{{ detail.mealType }}</el-descriptions-item>
          <el-descriptions-item label="孩子">{{ detail.childName }}（#{{ detail.childId }}）</el-descriptions-item>
          <el-descriptions-item label="家庭">#{{ detail.familyId }}</el-descriptions-item>
          <el-descriptions-item label="金额">¥{{ detail.totalAmount }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{ STATUS[detail.status]?.label || detail.status }}
            <el-tag v-if="detail.isOverLimit" type="danger" size="small">超额</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="预估余额">¥{{ detail.estimatedBalance ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="完成余额">¥{{ detail.completedBalance ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="备注" :span="2">{{ detail.remark || '-' }}</el-descriptions-item>
        </el-descriptions>
        <el-table :data="detail.items" size="small" style="margin-top:12px">
          <el-table-column prop="dishName" label="菜品" min-width="130" />
          <el-table-column prop="quantity" label="数量" width="70" />
          <el-table-column label="单价" width="90">
            <template #default="{ row }">{{ row.unitPrice }}</template>
          </el-table-column>
          <el-table-column label="小计" width="90">
            <template #default="{ row }">{{ row.subtotal }}</template>
          </el-table-column>
          <el-table-column prop="note" label="备注" min-width="110" show-overflow-tooltip />
        </el-table>
      </template>
    </el-dialog>
  </el-card>
</template>
