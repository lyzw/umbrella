<template>
  <el-card shadow="never">
    <div class="toolbar">
      <el-input v-model="keyword" placeholder="按账号/姓名搜索" clearable style="width: 240px"
                @keyup.enter="load(1)" @clear="load(1)" />
      <el-button type="primary" @click="load(1)">搜索</el-button>
      <el-button v-if="canCreate" type="success" @click="openCreate">新建账号</el-button>
    </div>

    <el-table :data="rows" v-loading="loading" stripe>
      <el-table-column prop="username" label="账号" width="160" />
      <el-table-column prop="name" label="姓名" width="140" />
      <el-table-column prop="roleName" label="角色" width="180">
        <template #default="{ row }">
          <el-tag size="small">{{ row.roleName }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="status" label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'danger'" size="small">
            {{ row.status === 'ACTIVE' ? '启用' : '停用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="lastLoginTime" label="最近登录" width="180" />
      <el-table-column prop="createTime" label="创建时间" width="180" />
      <el-table-column label="操作" min-width="260" fixed="right">
        <template #default="{ row }">
          <el-button v-if="canEdit" link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button v-if="canEdit" link :type="row.status === 'ACTIVE' ? 'danger' : 'success'"
                     @click="toggleStatus(row)">
            {{ row.status === 'ACTIVE' ? '停用' : '启用' }}
          </el-button>
          <el-button v-if="canEdit" link type="warning" @click="openResetPwd(row)">重置密码</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination class="pager" layout="total, prev, pager, next" :total="total"
                   :page-size="pageSize" :current-page="page" @current-change="load" />

    <!-- 新建/编辑 -->
    <el-dialog v-model="dialog.visible" :title="dialog.isCreate ? '新建账号' : '编辑账号'" width="460px">
      <el-form :model="dialog.form" label-width="80px">
        <el-form-item label="账号">
          <el-input v-model="dialog.form.username" :disabled="!dialog.isCreate" placeholder="登录账号" />
        </el-form-item>
        <el-form-item label="姓名">
          <el-input v-model="dialog.form.name" placeholder="显示姓名" />
        </el-form-item>
        <el-form-item label="角色">
          <el-select v-model="dialog.form.roleCode" placeholder="选择角色" style="width: 100%">
            <el-option v-for="r in roles" :key="r.code" :value="r.code"
                       :label="`${r.name}（${r.code}）`" :disabled="r.code === 'SA'" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="dialog.isCreate" label="初始密码">
          <el-input v-model="dialog.form.password" type="password" show-password
                    placeholder="不少于 8 位，含字母与数字" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="dialog.saving" @click="save">保存</el-button>
      </template>
    </el-dialog>

    <!-- 重置密码 -->
    <el-dialog v-model="pwd.visible" title="重置密码" width="420px">
      <el-input v-model="pwd.value" type="password" show-password placeholder="新密码（不少于 8 位，含字母与数字）" />
      <template #footer>
        <el-button @click="pwd.visible = false">取消</el-button>
        <el-button type="primary" :loading="pwd.saving" @click="savePwd">确认重置</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listAccounts, createAccount, updateAccount, changeAccountStatus, resetPassword, listRoles } from '../api/console'
import { hasPerm } from '../stores/auth'

const canCreate = computed(() => hasPerm('后台账号', 'create'))
const canEdit = computed(() => hasPerm('后台账号', 'edit'))

const rows = ref([])
const roles = ref([])
const total = ref(0)
const page = ref(1)
const pageSize = 10
const keyword = ref('')
const loading = ref(false)

const dialog = reactive({
  visible: false, isCreate: true, saving: false, editId: null,
  form: { username: '', name: '', roleCode: '', password: '' }
})
const pwd = reactive({ visible: false, saving: false, id: null, value: '' })

async function load(p = page.value) {
  loading.value = true
  page.value = p
  try {
    const data = await listAccounts(p, pageSize, keyword.value.trim())
    rows.value = data.items
    total.value = data.total
  } finally {
    loading.value = false
  }
}

async function loadRoles() {
  roles.value = await listRoles()
}

function openCreate() {
  dialog.isCreate = true
  dialog.editId = null
  dialog.form = { username: '', name: '', roleCode: '', password: '' }
  dialog.visible = true
}

function openEdit(row) {
  dialog.isCreate = false
  dialog.editId = row.id
  dialog.form = { username: row.username, name: row.name, roleCode: row.roleCode, password: '' }
  dialog.visible = true
}

async function save() {
  dialog.saving = true
  try {
    if (dialog.isCreate) {
      await createAccount({ ...dialog.form })
      ElMessage.success('账号已创建')
    } else {
      await updateAccount(dialog.editId, { name: dialog.form.name, roleCode: dialog.form.roleCode })
      ElMessage.success('账号已更新')
    }
    dialog.visible = false
    await load(1)
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    dialog.saving = false
  }
}

async function toggleStatus(row) {
  const target = row.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE'
  const tip = target === 'DISABLED' ? '停用后该账号所有令牌立即失效，确定停用？' : '确定启用该账号？'
  try {
    await ElMessageBox.confirm(tip, '确认', { type: 'warning' })
  } catch { return }
  try {
    await changeAccountStatus(row.id, target)
    ElMessage.success(target === 'DISABLED' ? '已停用' : '已启用')
    await load()
  } catch (e) {
    ElMessage.error(e.message)
  }
}

function openResetPwd(row) {
  pwd.id = row.id
  pwd.value = ''
  pwd.visible = true
}

async function savePwd() {
  pwd.saving = true
  try {
    await resetPassword(pwd.id, pwd.value)
    ElMessage.success('密码已重置，该账号需重新登录')
    pwd.visible = false
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    pwd.saving = false
  }
}

onMounted(async () => {
  await Promise.all([load(1), loadRoles()])
})
</script>

<style scoped>
.toolbar { display: flex; gap: 10px; margin-bottom: 14px; }
.pager { margin-top: 14px; justify-content: flex-end; }
</style>
