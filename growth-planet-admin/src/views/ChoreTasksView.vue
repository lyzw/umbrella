<script setup>
import { ref, reactive, onMounted, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { listChoreTasks, createChoreTask, updateChoreTask } from '../api/console'
import { hasPerm } from '../stores/auth'

const loading = ref(false)
const rows = ref([])
const total = ref(0)
const query = reactive({ page: 1, pageSize: 10, status: '', keyword: '', familyId: null })

const canCreate = computed(() => hasPerm('任务库/奖励库', 'create'))
const canEdit = computed(() => hasPerm('任务库/奖励库', 'edit'))

const CYCLES = { DAILY: '每日', WEEKLY: '每周', ONCE: '一次性' }
const STATUS = { NORMAL: '正常', ARCHIVED: '已归档' }

async function load() {
  loading.value = true
  try {
    const { data } = await listChoreTasks(query)
    rows.value = data.items || []
    total.value = Number(data.total || 0)
  } finally {
    loading.value = false
  }
}

const dialog = ref(false)
const editing = ref(null)
const form = reactive({ familyId: null, title: '', cycle: 'DAILY', rewardAmount: 0, estimatedMinutes: 10, status: 'NORMAL' })

function openCreate() {
  editing.value = null
  Object.assign(form, { familyId: null, title: '', cycle: 'DAILY', rewardAmount: 0, estimatedMinutes: 10, status: 'NORMAL' })
  dialog.value = true
}

function openEdit(row) {
  editing.value = row
  Object.assign(form, {
    familyId: row.familyId, title: row.title, cycle: row.cycle,
    rewardAmount: Number(row.rewardAmount || 0), estimatedMinutes: row.estimatedMinutes, status: row.status
  })
  dialog.value = true
}

async function submit() {
  try {
    if (editing.value) {
      await updateChoreTask(editing.value.id, form)
      ElMessage.success('任务已更新')
    } else {
      await createChoreTask(form)
      ElMessage.success('任务已创建')
    }
    dialog.value = false
    load()
  } catch (e) {
    ElMessage.error(e.message || '保存失败')
  }
}

async function archive(row) {
  await updateChoreTask(row.id, { status: 'ARCHIVED' })
  ElMessage.success('已归档')
  load()
}

onMounted(load)
</script>

<template>
  <el-card shadow="never">
    <div style="display:flex;gap:12px;flex-wrap:wrap;align-items:center">
      <el-select v-model="query.status" placeholder="状态" clearable style="width:130px">
        <el-option label="正常" value="NORMAL" />
        <el-option label="已归档" value="ARCHIVED" />
      </el-select>
      <el-input v-model="query.keyword" placeholder="任务标题" clearable style="width:180px" @keyup.enter="query.page = 1; load()" />
      <el-button type="primary" @click="query.page = 1; load()">查询</el-button>
      <div style="flex:1" />
      <el-button v-if="canCreate" type="primary" @click="openCreate">代家庭创建任务</el-button>
    </div>

    <el-table :data="rows" v-loading="loading" style="margin-top:16px">
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="familyId" label="家庭ID" width="90" />
      <el-table-column prop="title" label="标题" min-width="160" />
      <el-table-column label="周期" width="90">
        <template #default="{ row }">{{ CYCLES[row.cycle] || row.cycle }}</template>
      </el-table-column>
      <el-table-column label="奖励(星币)" width="110">
        <template #default="{ row }">¥{{ row.rewardAmount }}</template>
      </el-table-column>
      <el-table-column label="预计(分)" width="90" prop="estimatedMinutes" />
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="row.status === 'NORMAL' ? 'success' : 'info'">{{ STATUS[row.status] || row.status }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="130" fixed="right">
        <template #default="{ row }">
          <el-button v-if="canEdit" link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button v-if="canEdit && row.status === 'NORMAL'" link type="warning" @click="archive(row)">归档</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-pagination style="margin-top:16px;justify-content:flex-end" layout="total, prev, pager, next"
      :total="total" :page-size="query.pageSize" :current-page="query.page"
      @current-change="(p) => { query.page = p; load() }" />

    <el-dialog v-model="dialog" :title="editing ? '编辑任务' : '代家庭创建任务'" width="460px">
      <el-form label-width="100px">
        <el-form-item label="家庭ID">
          <el-input-number v-model="form.familyId" :min="1" :disabled="!!editing" />
        </el-form-item>
        <el-form-item label="标题"><el-input v-model="form.title" maxlength="64" /></el-form-item>
        <el-form-item label="周期">
          <el-select v-model="form.cycle">
            <el-option label="每日" value="DAILY" />
            <el-option label="每周" value="WEEKLY" />
            <el-option label="一次性" value="ONCE" />
          </el-select>
        </el-form-item>
        <el-form-item label="奖励(星币)"><el-input-number v-model="form.rewardAmount" :min="0" :precision="2" /></el-form-item>
        <el-form-item label="预计(分钟)"><el-input-number v-model="form.estimatedMinutes" :min="0" /></el-form-item>
        <el-form-item v-if="editing" label="状态">
          <el-radio-group v-model="form.status">
            <el-radio value="NORMAL">正常</el-radio>
            <el-radio value="ARCHIVED">归档</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item><el-button type="primary" @click="submit">保存</el-button></el-form-item>
      </el-form>
    </el-dialog>
  </el-card>
</template>
