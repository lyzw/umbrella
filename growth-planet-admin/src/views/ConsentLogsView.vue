<script setup>
import { ref, reactive, onMounted } from 'vue'
import { listConsents } from '../api/console'

const loading = ref(false)
const rows = ref([])
const total = ref(0)
const query = reactive({ page: 1, pageSize: 10, childId: '', consentType: '', action: '' })

const TYPES = { ORDER: '下单同意书', PROFILE: '档案同意书' }
const ACTIONS = { GRANT: '授予', REVOKE: '撤回' }
const GUARDIAN = { UNVERIFIED: '未核验', SELF_ATTESTED: '自声明', VERIFIED: '已核验' }

async function load() {
  loading.value = true
  try {
    const { data } = await listConsents(query)
    rows.value = data.items || []
    total.value = Number(data.total || 0)
  } finally {
    loading.value = false
  }
}

const fmt = (t) => (t ? new Date(t).toLocaleString() : '—')

onMounted(load)
</script>

<template>
  <el-card shadow="never">
    <div style="display:flex;gap:12px;flex-wrap:wrap;align-items:center">
      <el-input v-model="query.childId" placeholder="孩子 ID" clearable style="width:130px" />
      <el-select v-model="query.consentType" placeholder="同意书类型" clearable style="width:150px">
        <el-option v-for="(v, k) in TYPES" :key="k" :label="v" :value="k" />
      </el-select>
      <el-select v-model="query.action" placeholder="动作" clearable style="width:110px">
        <el-option v-for="(v, k) in ACTIONS" :key="k" :label="v" :value="k" />
      </el-select>
      <el-button type="primary" @click="query.page = 1; load()">查询</el-button>
    </div>

    <el-table :data="rows" v-loading="loading" style="margin-top:16px">
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column label="同意书类型" width="130">
        <template #default="{ row }">{{ TYPES[row.consentType] || row.consentType }}</template>
      </el-table-column>
      <el-table-column label="动作" width="80">
        <template #default="{ row }">
          <el-tag :type="row.action === 'GRANT' ? 'success' : 'danger'">{{ ACTIONS[row.action] || row.action }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="version" label="版本" width="70" />
      <el-table-column label="监护人状态" width="110">
        <template #default="{ row }">{{ GUARDIAN[row.guardianStatus] || row.guardianStatus }}</template>
      </el-table-column>
      <el-table-column prop="childName" label="孩子">
        <template #default="{ row }">{{ row.childName || `#${row.childId}` }}</template>
      </el-table-column>
      <el-table-column prop="familyId" label="家庭" width="80" />
      <el-table-column label="签署时间" width="170">
        <template #default="{ row }">{{ fmt(row.signedAt) }}</template>
      </el-table-column>
      <el-table-column label="到期时间" width="170">
        <template #default="{ row }">{{ fmt(row.expireAt) }}</template>
      </el-table-column>
      <el-table-column prop="createTime" label="记录时间" width="170" />
    </el-table>

    <el-pagination
      v-model:current-page="query.page" v-model:page-size="query.pageSize"
      :total="total" layout="total, prev, pager, next" style="margin-top:16px"
      @current-change="load" />
  </el-card>
</template>
