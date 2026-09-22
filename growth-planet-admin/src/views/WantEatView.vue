<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { wantEat, exportBizCsv } from '../api/console'

const loading = ref(false)
const rows = ref([])
const total = ref(0)
const query = reactive({ page: 1, pageSize: 10, familyId: '', childId: '', mealType: '', from: '', to: '' })

const MEALS = { BREAKFAST: '早餐', LUNCH: '午餐', DINNER: '晚餐' }

async function load() {
  loading.value = true
  try {
    const { data } = await wantEat(query)
    rows.value = data.items || []
    total.value = Number(data.total || 0)
  } finally {
    loading.value = false
  }
}

async function doExport() {
  try {
    await exportBizCsv('want-eat', query)
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
      <el-select v-model="query.mealType" placeholder="餐次" clearable style="width:110px">
        <el-option v-for="(v, k) in MEALS" :key="k" :label="v" :value="k" />
      </el-select>
      <el-date-picker v-model="query.from" type="date" placeholder="开始日期" value-format="YYYY-MM-DD" style="width:150px" />
      <el-date-picker v-model="query.to" type="date" placeholder="结束日期" value-format="YYYY-MM-DD" style="width:150px" />
      <el-button type="primary" @click="query.page = 1; load()">查询</el-button>
      <el-button @click="doExport">导出 CSV</el-button>
    </div>

    <el-table :data="rows" v-loading="loading" style="margin-top:16px">
      <el-table-column prop="menuDate" label="菜单日期" width="110" />
      <el-table-column label="餐次" width="80">
        <template #default="{ row }">{{ MEALS[row.mealType] || row.mealType }}</template>
      </el-table-column>
      <el-table-column prop="childName" label="孩子" width="120" />
      <el-table-column prop="familyId" label="家庭" width="80" />
      <el-table-column prop="dishName" label="菜品" min-width="150">
        <template #default="{ row }">{{ row.dishName || `#${row.dishId}` }}</template>
      </el-table-column>
      <el-table-column label="来源" width="90">
        <template #default="{ row }">{{ row.sourceType === 'SCHOOL' ? '校餐' : '家庭' }}</template>
      </el-table-column>
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag size="small" :type="row.status === 'MARKED' ? 'info' : 'success'">
            {{ { MARKED: '想吃', ADOPTED: '已采纳', COOKED: '已做' }[row.status] || row.status }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="标记时间" width="170" />
    </el-table>
    <el-pagination style="margin-top:16px;justify-content:flex-end" layout="total, prev, pager, next"
      :total="total" :page-size="query.pageSize" :current-page="query.page"
      @current-change="(p) => { query.page = p; load() }" />
  </el-card>
</template>
