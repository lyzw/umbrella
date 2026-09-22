<script setup>
import { ref, reactive, onMounted } from 'vue'
import { listVerifications } from '../api/console'

const loading = ref(false)
const rows = ref([])
const total = ref(0)
const query = reactive({ page: 1, pageSize: 10, requestId: '' })

async function load() {
  loading.value = true
  try {
    const { data } = await listVerifications(query)
    rows.value = data.items || []
    total.value = Number(data.total || 0)
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <el-card shadow="never">
    <div style="display:flex;gap:12px;flex-wrap:wrap;align-items:center">
      <el-input v-model="query.requestId" placeholder="工单号" clearable style="width:150px" />
      <el-button type="primary" @click="query.page = 1; load()">查询</el-button>
    </div>

    <el-table :data="rows" v-loading="loading" style="margin-top:16px">
      <el-table-column prop="id" label="记录 ID" width="100" />
      <el-table-column prop="requestId" label="关联工单" width="110" />
      <el-table-column prop="requesterId" label="申请人" width="110" />
      <el-table-column prop="codeHashMasked" label="核验码哈希">
        <template #default="{ row }">
          <el-text type="info" size="small">{{ row.codeHashMasked || '—' }}</el-text>
        </template>
      </el-table-column>
      <el-table-column prop="verifiedAt" label="核验时间" width="180" />
    </el-table>

    <el-pagination
      v-model:current-page="query.page" v-model:page-size="query.pageSize"
      :total="total" layout="total, prev, pager, next" style="margin-top:16px"
      @current-change="load" />
  </el-card>
</template>
