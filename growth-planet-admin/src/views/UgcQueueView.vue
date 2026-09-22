<script setup>
import { ref, reactive, onMounted, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ugcQueue, reviewUgc, deleteUgc } from '../api/console'
import { hasPerm } from '../stores/auth'

const loading = ref(false)
const rows = ref([])
const total = ref(0)
const query = reactive({ page: 1, pageSize: 10, reviewStatus: '', keyword: '' })

const canApprove = computed(() => hasPerm('UGC审核队列', 'approve'))
const canDelete = computed(() => hasPerm('UGC审核队列', 'delete'))

const REVIEW = {
  NONE: { label: '未送审', type: 'info' },
  PENDING: { label: '待审核', type: 'warning' },
  APPROVED: { label: '已通过', type: 'success' },
  REJECTED: { label: '已驳回', type: 'danger' }
}

async function load() {
  loading.value = true
  try {
    const { data } = await ugcQueue(query)
    rows.value = data.items || []
    total.value = Number(data.total || 0)
  } finally {
    loading.value = false
  }
}

async function approve(row) {
  await ElMessageBox.confirm(
    `确认通过家庭菜品「${row.name}」（家庭 ${row.familyId}）？通过后将转为公开（PUBLIC）。`,
    '审核通过', { type: 'warning' })
  try {
    await reviewUgc(row.id, { action: 'APPROVE', expectedVersion: row.version })
    ElMessage.success('已通过')
    load()
  } catch (e) {
    ElMessage.error(e.message || '操作失败（可能已被他人审核）')
    load()
  }
}

async function reject(row) {
  const { value } = await ElMessageBox.prompt('请填写驳回原因（必填）', `驳回「${row.name}」`, {
    inputValidator: (v) => (v && v.trim() ? true : '驳回原因不能为空')
  })
  try {
    await reviewUgc(row.id, { action: 'REJECT', reason: value.trim(), expectedVersion: row.version })
    ElMessage.success('已驳回')
    load()
  } catch (e) {
    ElMessage.error(e.message || '操作失败（可能已被他人审核）')
    load()
  }
}

async function remove(row) {
  await ElMessageBox.confirm(`确认移除违规菜品「${row.name}」？该操作为软删除且不可恢复。`, '移除确认', { type: 'error' })
  try {
    await deleteUgc(row.id)
    ElMessage.success('已移除')
    load()
  } catch (e) {
    ElMessage.error(e.message || '操作失败')
  }
}

onMounted(load)
</script>

<template>
  <el-card shadow="never">
    <div style="display:flex;gap:12px;flex-wrap:wrap;align-items:center">
      <el-select v-model="query.reviewStatus" placeholder="审核状态" clearable style="width:130px">
        <el-option v-for="(v, k) in REVIEW" :key="k" :label="v.label" :value="k" />
      </el-select>
      <el-input v-model="query.keyword" placeholder="菜品名称" clearable style="width:180px" @keyup.enter="query.page = 1; load()" />
      <el-button type="primary" @click="query.page = 1; load()">查询</el-button>
    </div>

    <el-table :data="rows" v-loading="loading" style="margin-top:16px">
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="familyId" label="家庭" width="80" />
      <el-table-column prop="name" label="菜品" min-width="130" />
      <el-table-column label="过敏原声明" min-width="140">
        <template #default="{ row }">
          <el-tag v-if="row.allergenStatus === 'DECLARED'" type="warning" size="small">
            {{ (row.allergens || []).join('、') || '已声明' }}
          </el-tag>
          <el-tag v-else type="danger" size="small">未声明过敏原</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="可见性" width="90">
        <template #default="{ row }">{{ row.visibility === 'PUBLIC' ? '公开' : '私有' }}</template>
      </el-table-column>
      <el-table-column label="审核状态" width="100">
        <template #default="{ row }">
          <el-tag :type="REVIEW[row.reviewStatus]?.type || 'info'">{{ REVIEW[row.reviewStatus]?.label || row.reviewStatus }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="rejectReason" label="驳回原因" min-width="130" show-overflow-tooltip />
      <el-table-column label="操作" width="170" fixed="right">
        <template #default="{ row }">
          <template v-if="canApprove && row.visibility === 'PRIVATE' && row.reviewStatus !== 'APPROVED'">
            <el-button link type="success" @click="approve(row)">通过</el-button>
            <el-button link type="danger" @click="reject(row)">驳回</el-button>
          </template>
          <el-button v-if="canDelete" link type="danger" @click="remove(row)">移除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-pagination style="margin-top:16px;justify-content:flex-end" layout="total, prev, pager, next"
      :total="total" :page-size="query.pageSize" :current-page="query.page"
      @current-change="(p) => { query.page = p; load() }" />
  </el-card>
</template>
