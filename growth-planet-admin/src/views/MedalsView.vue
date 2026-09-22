<script setup>
import { ref, reactive, onMounted, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listMedals, createMedal, updateMedal } from '../api/console'
import { hasPerm } from '../stores/auth'

const loading = ref(false)
const rows = ref([])
const statusFilter = ref('')
const canCreate = computed(() => hasPerm('勋章配置', 'create'))
const canEdit = computed(() => hasPerm('勋章配置', 'edit'))

const CONDITIONS = { COUNT: '累计次数', STREAK: '连续天数' }

async function load() {
  loading.value = true
  try {
    const { data } = await listMedals(statusFilter.value)
    rows.value = data || []
  } finally {
    loading.value = false
  }
}

const dialog = ref(false)
const editing = ref(null)
const form = reactive({
  code: '', name: '', description: '', icon: '', category: 'MEAL',
  conditionType: 'COUNT', threshold: 1, sortOrder: 0, status: 'NORMAL'
})

function openCreate() {
  editing.value = null
  Object.assign(form, { code: '', name: '', description: '', icon: '', category: 'MEAL', conditionType: 'COUNT', threshold: 1, sortOrder: 0, status: 'NORMAL' })
  dialog.value = true
}

function openEdit(row) {
  editing.value = row
  Object.assign(form, {
    code: row.code, name: row.name, description: row.description || '', icon: row.icon || '',
    category: row.category, conditionType: row.conditionType, threshold: row.threshold,
    sortOrder: row.sortOrder, status: row.status
  })
  dialog.value = true
}

async function submit() {
  try {
    if (editing.value) {
      await updateMedal(editing.value.id, form)
      ElMessage.success('勋章已更新')
    } else {
      await createMedal(form)
      ElMessage.success('勋章已创建')
    }
    dialog.value = false
    load()
  } catch (e) {
    ElMessage.error(e.message || '保存失败')
  }
}

async function toggle(row) {
  const next = row.status === 'NORMAL' ? 'DISABLED' : 'NORMAL'
  await ElMessageBox.confirm(`确认${next === 'DISABLED' ? '停用' : '启用'}勋章「${row.name}」？`, '确认', { type: 'warning' })
  await updateMedal(row.id, { ...row, status: next })
  ElMessage.success('已更新')
  load()
}

onMounted(load)
</script>

<template>
  <el-card shadow="never">
    <div style="display:flex;gap:12px;align-items:center">
      <el-select v-model="statusFilter" placeholder="状态" clearable style="width:130px" @change="load">
        <el-option label="启用" value="NORMAL" />
        <el-option label="停用" value="DISABLED" />
      </el-select>
      <div style="flex:1" />
      <el-button v-if="canCreate" type="primary" @click="openCreate">新建勋章</el-button>
    </div>

    <el-table :data="rows" v-loading="loading" style="margin-top:16px">
      <el-table-column prop="code" label="代码" width="160" />
      <el-table-column prop="name" label="名称" min-width="140" />
      <el-table-column prop="category" label="分类" width="100" />
      <el-table-column label="达成条件" min-width="160">
        <template #default="{ row }">{{ CONDITIONS[row.conditionType] || row.conditionType }} ≥ {{ row.threshold }}</template>
      </el-table-column>
      <el-table-column prop="awardedCount" label="已发放" width="90" />
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="row.status === 'NORMAL' ? 'success' : 'info'">
            {{ row.status === 'NORMAL' ? '启用' : '停用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="140" fixed="right">
        <template #default="{ row }">
          <el-button v-if="canEdit" link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button v-if="canEdit" link :type="row.status === 'NORMAL' ? 'warning' : 'success'" @click="toggle(row)">
            {{ row.status === 'NORMAL' ? '停用' : '启用' }}
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialog" :title="editing ? '编辑勋章' : '新建勋章'" width="480px">
      <el-form label-width="90px">
        <el-form-item label="代码"><el-input v-model="form.code" :disabled="!!editing" placeholder="大写字母/数字/下划线" /></el-form-item>
        <el-form-item label="名称"><el-input v-model="form.name" maxlength="64" /></el-form-item>
        <el-form-item label="描述"><el-input v-model="form.description" type="textarea" :rows="2" /></el-form-item>
        <el-form-item label="分类"><el-input v-model="form.category" placeholder="如 MEAL / CHORE" /></el-form-item>
        <el-form-item label="条件类型">
          <el-select v-model="form.conditionType">
            <el-option label="累计次数" value="COUNT" />
            <el-option label="连续天数" value="STREAK" />
          </el-select>
        </el-form-item>
        <el-form-item label="阈值"><el-input-number v-model="form.threshold" :min="1" :max="9999" /></el-form-item>
        <el-form-item label="排序"><el-input-number v-model="form.sortOrder" :min="0" /></el-form-item>
        <el-form-item><el-button type="primary" @click="submit">保存</el-button></el-form-item>
      </el-form>
    </el-dialog>
  </el-card>
</template>
