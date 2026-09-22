<script setup>
import { ref, reactive, onMounted, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { medalAwards, reissueMedal, listMedals, exportBizCsv } from '../api/console'
import { hasPerm } from '../stores/auth'

const loading = ref(false)
const rows = ref([])
const total = ref(0)
const query = reactive({ page: 1, pageSize: 10, familyId: '', childId: '', definitionId: '' })
const medals = ref([])

const canReissue = computed(() => hasPerm('勋章发放/家庭设置', 'edit'))

const reissueVisible = ref(false)
const reissueForm = reactive({ childId: '', code: '', reason: '' })

async function load() {
  loading.value = true
  try {
    const { data } = await medalAwards(query)
    rows.value = data.items || []
    total.value = Number(data.total || 0)
  } finally {
    loading.value = false
  }
}

async function loadMedals() {
  const { data } = await listMedals('NORMAL')
  medals.value = data || []
}

function openReissue() {
  reissueForm.childId = query.childId || ''
  reissueForm.code = ''
  reissueForm.reason = ''
  reissueVisible.value = true
}

/** 勋章补发（L4 二次确认 + 后端审计留痕；ref_id=0 人工通道，每孩子每勋章一次） */
async function submitReissue() {
  if (!reissueForm.childId || !reissueForm.code || !reissueForm.reason.trim()) {
    ElMessage.warning('孩子 ID、勋章、原因均必填')
    return
  }
  try {
    await ElMessageBox.confirm(
      `确认向孩子 #${reissueForm.childId} 补发勋章「${reissueForm.code}」？该操作敏感（L4）：幂等（同勋章同孩子仅一次）、全量审计留痕。`,
      '勋章补发确认', { type: 'warning', confirmButtonText: '确认补发' })
  } catch {
    return
  }
  try {
    await reissueMedal({
      childId: Number(reissueForm.childId), code: reissueForm.code, reason: reissueForm.reason.trim()
    })
    ElMessage.success('补发成功（已落审计）')
    reissueVisible.value = false
    load()
  } catch (e) {
    ElMessage.error(e.message || '补发失败')
  }
}

async function doExport() {
  try {
    await exportBizCsv('medal-awards', query)
    ElMessage.success('已导出 CSV')
  } catch (e) {
    ElMessage.error(e.message || '导出失败')
  }
}

function fmtAwarded(at) {
  return at ? new Date(Number(at)).toLocaleString() : '-'
}

onMounted(() => { load(); loadMedals() })
</script>

<template>
  <el-card shadow="never">
    <div style="display:flex;gap:12px;flex-wrap:wrap;align-items:center">
      <el-input v-model="query.familyId" placeholder="家庭 ID" clearable style="width:130px" />
      <el-input v-model="query.childId" placeholder="孩子 ID" clearable style="width:130px" />
      <el-select v-model="query.definitionId" placeholder="勋章" clearable filterable style="width:200px">
        <el-option v-for="m in medals" :key="m.id" :label="`${m.name}（${m.code}）`" :value="m.id" />
      </el-select>
      <el-button type="primary" @click="query.page = 1; load()">查询</el-button>
      <el-button @click="doExport">导出 CSV</el-button>
      <el-button v-if="canReissue" type="warning" @click="openReissue">人工补发</el-button>
    </div>

    <el-table :data="rows" v-loading="loading" style="margin-top:16px">
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column prop="medalName" label="勋章" min-width="140">
        <template #default="{ row }">{{ row.medalName || row.medalCode }}</template>
      </el-table-column>
      <el-table-column prop="medalCode" label="编码" width="160" show-overflow-tooltip />
      <el-table-column prop="childName" label="孩子" width="110" />
      <el-table-column prop="childId" label="孩子ID" width="90" />
      <el-table-column prop="familyId" label="家庭" width="90" />
      <el-table-column label="发放时间" width="180">
        <template #default="{ row }">{{ fmtAwarded(row.awardedAt) }}</template>
      </el-table-column>
      <el-table-column prop="consecutiveCount" label="连续次数" width="90" />
      <el-table-column label="来源" width="100">
        <template #default="{ row }">
          <el-tag v-if="row.refId === 0" type="warning" size="small">人工补发</el-tag>
          <span v-else>业务触发</span>
        </template>
      </el-table-column>
    </el-table>
    <el-pagination style="margin-top:16px;justify-content:flex-end" layout="total, prev, pager, next"
      :total="total" :page-size="query.pageSize" :current-page="query.page"
      @current-change="(p) => { query.page = p; load() }" />

    <el-dialog v-model="reissueVisible" title="勋章人工补发（L4）" width="460px">
      <el-form label-width="90px">
        <el-form-item label="孩子 ID" required>
          <el-input v-model="reissueForm.childId" placeholder="C 端儿童用户 ID" />
        </el-form-item>
        <el-form-item label="勋章" required>
          <el-select v-model="reissueForm.code" filterable placeholder="选择勋章" style="width:100%">
            <el-option v-for="m in medals" :key="m.code" :label="`${m.name}（${m.code}）`" :value="m.code" />
          </el-select>
        </el-form-item>
        <el-form-item label="补发原因" required>
          <el-input v-model="reissueForm.reason" type="textarea" :rows="2" placeholder="必填，将记入审计日志" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="reissueVisible = false">取消</el-button>
        <el-button type="warning" @click="submitReissue">提交补发</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>
