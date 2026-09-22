<script setup>
import { ref, reactive, onMounted, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { listSchoolMenus, upsertSchoolMenu, listDishes } from '../api/console'
import { hasPerm } from '../stores/auth'

const SCHOOLS = ['合成学校', '合成第一小学', '合成第二小学']
const MEALS = [{ v: 'BREAKFAST', l: '早餐' }, { v: 'LUNCH', l: '午餐' }, { v: 'DINNER', l: '晚餐' }]

const loading = ref(false)
const rows = ref([])
const total = ref(0)
const query = reactive({ school: '', mealType: '', page: 1, pageSize: 20 })
const canConfig = computed(() => hasPerm('菜单编排', 'config'))
const presetDishes = ref([])

async function load() {
  loading.value = true
  try {
    const { data } = await listSchoolMenus(query)
    rows.value = data.items || []
    total.value = Number(data.total || 0)
  } finally {
    loading.value = false
  }
}

// ==================== 编排抽屉 ====================
const drawer = ref(false)
const form = reactive({ school: '', menuDate: '', mealType: 'LUNCH', selected: [], status: 'PUBLISHED' })

async function openUpsert(row) {
  form.school = row?.school || SCHOOLS[0]
  form.menuDate = row?.menuDate || ''
  form.mealType = row?.mealType || 'LUNCH'
  form.selected = (row?.dishIds || []).filter(r => r.type === 'PRESET').map(r => r.id)
  if (!presetDishes.value.length) {
    const { data } = await listDishes({ status: 'ON_SALE', pageSize: 100 })
    presetDishes.value = data.items || []
  }
  drawer.value = true
}

async function submit() {
  if (!form.menuDate) {
    ElMessage.warning('请选择日期')
    return
  }
  try {
    await upsertSchoolMenu({
      school: form.school, menuDate: form.menuDate, mealType: form.mealType,
      dishIds: form.selected.map(id => ({ type: 'PRESET', id })), status: form.status
    })
    ElMessage.success('菜单已发布')
    drawer.value = false
    load()
  } catch (e) {
    ElMessage.error(e.message || '发布失败')
  }
}

const mealLabel = (v) => MEALS.find(m => m.v === v)?.l || v

onMounted(load)
</script>

<template>
  <el-card shadow="never">
    <div style="display:flex;gap:12px;flex-wrap:wrap;align-items:center">
      <el-select v-model="query.school" placeholder="学校" clearable style="width:180px">
        <el-option v-for="s in SCHOOLS" :key="s" :label="s" :value="s" />
      </el-select>
      <el-select v-model="query.mealType" placeholder="餐次" clearable style="width:120px">
        <el-option v-for="m in MEALS" :key="m.v" :label="m.l" :value="m.v" />
      </el-select>
      <el-button type="primary" @click="query.page = 1; load()">查询</el-button>
      <div style="flex:1" />
      <el-button v-if="canConfig" type="primary" @click="openUpsert(null)">编排/发布菜单</el-button>
    </div>

    <el-table :data="rows" v-loading="loading" style="margin-top:16px">
      <el-table-column prop="school" label="学校" min-width="160" />
      <el-table-column prop="menuDate" label="日期" width="120" />
      <el-table-column label="餐次" width="90">
        <template #default="{ row }">{{ mealLabel(row.mealType) }}</template>
      </el-table-column>
      <el-table-column label="菜品数" width="90">
        <template #default="{ row }">{{ (row.dishIds || []).length }}</template>
      </el-table-column>
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="row.status === 'PUBLISHED' ? 'success' : 'info'">
            {{ row.status === 'PUBLISHED' ? '已发布' : '草稿' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="100" fixed="right">
        <template #default="{ row }">
          <el-button v-if="canConfig" link type="primary" @click="openUpsert(row)">编辑</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-pagination style="margin-top:16px;justify-content:flex-end" layout="total, prev, pager, next"
      :total="total" :page-size="query.pageSize" :current-page="query.page"
      @current-change="(p) => { query.page = p; load() }" />

    <el-drawer v-model="drawer" title="编排学校菜单" size="420px">
      <el-form label-width="80px">
        <el-form-item label="学校">
          <el-select v-model="form.school" style="width:100%">
            <el-option v-for="s in SCHOOLS" :key="s" :label="s" :value="s" />
          </el-select>
        </el-form-item>
        <el-form-item label="日期"><el-date-picker v-model="form.menuDate" type="date" value-format="YYYY-MM-DD" /></el-form-item>
        <el-form-item label="餐次">
          <el-select v-model="form.mealType">
            <el-option v-for="m in MEALS" :key="m.v" :label="m.l" :value="m.v" />
          </el-select>
        </el-form-item>
        <el-form-item label="菜品">
          <el-select v-model="form.selected" multiple filterable style="width:100%">
            <el-option v-for="d in presetDishes" :key="d.dishId" :label="d.name" :value="d.dishId" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio value="PUBLISHED">发布</el-radio>
            <el-radio value="DRAFT">草稿</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item><el-button type="primary" @click="submit">保存并发布</el-button></el-form-item>
      </el-form>
    </el-drawer>
  </el-card>
</template>
