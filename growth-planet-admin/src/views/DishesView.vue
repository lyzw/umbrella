<script setup>
import { ref, reactive, onMounted, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listDishes, createDish, updateDish, deleteDish, toggleDishStatus, listCategories, createCategory, updateCategory, deleteCategory, uploadToQiniu } from '../api/console'
import { hasPerm } from '../stores/auth'

const loading = ref(false)
const dishes = ref([])
const total = ref(0)
const categories = ref([])
const query = reactive({ page: 1, pageSize: 10, status: '', categoryId: null, keyword: '' })

const canCreate = computed(() => hasPerm('菜品库', 'create'))
const canEdit = computed(() => hasPerm('菜品库', 'edit'))
const canDelete = computed(() => hasPerm('菜品库', 'delete'))
const canManageCategory = computed(() => hasPerm('菜品分类', 'view'))
// 对象存储上传权限（七牛直传凭证签发）
const canUploadImage = computed(() => hasPerm('对象存储', 'create'))
const uploading = ref(false)

async function load() {
  loading.value = true
  try {
    const { data } = await listDishes(query)
    dishes.value = data.items || []
    total.value = Number(data.total || 0)
  } finally {
    loading.value = false
  }
}

async function loadCategories() {
  const { data } = await listCategories()
  categories.value = data.items || []
}

// ==================== 菜品编辑抽屉 ====================
const drawer = ref(false)
const editing = ref(null)
const form = reactive({
  categoryId: null, name: '', imageUrl: '', virtualPrice: 0, calories: null,
  tags: '', allergens: [], allergenStatus: 'UNKNOWN', spiceLevel: 0, status: 'OFF_SALE'
})

function openCreate() {
  editing.value = null
  Object.assign(form, {
    categoryId: categories.value[0]?.categoryId ?? null, name: '', imageUrl: '', virtualPrice: 0,
    calories: null, tags: '', allergens: [], allergenStatus: 'UNKNOWN', spiceLevel: 0, status: 'OFF_SALE'
  })
  drawer.value = true
}

async function openEdit(row) {
  editing.value = row
  const { data } = await (await import('../api/console')).getDish(row.dishId)
  Object.assign(form, {
    categoryId: Number(data.categoryId), name: data.name, imageUrl: data.imageUrl || '',
    virtualPrice: Number(data.virtualPrice), calories: data.calories, tags: data.tags || '',
    allergens: data.allergens || [], allergenStatus: data.allergenStatus, spiceLevel: data.spiceLevel,
    status: data.status
  })
  drawer.value = true
}

// 图片大小上限（与后端 fsizeLimit 一致）
const IMAGE_MAX_BYTES = 5 * 1024 * 1024

/**
 * el-upload 自定义上传：取七牛凭证 → 直传 → 回填 form.imageUrl。
 * 后端不接触文件字节，仅签发短时效凭证。
 */
async function uploadImage({ file }) {
  if (!file) return
  if (file.size > IMAGE_MAX_BYTES) {
    ElMessage.warning('图片不能超过 5MB')
    return
  }
  uploading.value = true
  try {
    const { publicUrl } = await uploadToQiniu(file, 'dish')
    form.imageUrl = publicUrl
    ElMessage.success('图片上传成功')
  } catch (e) {
    ElMessage.error(e.message || '图片上传失败')
  } finally {
    uploading.value = false
  }
}

async function submit() {
  try {
    if (editing.value) {
      await updateDish(editing.value.dishId, form)
      ElMessage.success('菜品已更新')
    } else {
      await createDish(form)
      ElMessage.success('菜品已创建')
    }
    drawer.value = false
    load()
  } catch (e) {
    ElMessage.error(e.message || '保存失败')
  }
}

async function toggleStatus(row) {
  const next = row.status === 'ON_SALE' ? 'OFF_SALE' : 'ON_SALE'
  try {
    await toggleDishStatus(row.dishId, next)
    ElMessage.success(next === 'ON_SALE' ? '已上架' : '已下架')
    load()
  } catch (e) {
    ElMessage.error(e.message || '操作失败')
  }
}

async function remove(row) {
  await ElMessageBox.confirm(`确认删除菜品「${row.name}」？删除后不可恢复。`, '删除确认', { type: 'warning' })
  await deleteDish(row.dishId)
  ElMessage.success('已删除')
  load()
}

// ==================== 分类管理 ====================
const catDialog = ref(false)
const catForm = reactive({ id: null, name: '', sort: 0, status: 'ENABLED' })

function openCategories() {
  catDialog.value = true
  loadCategories()
}

async function saveCategory() {
  try {
    if (catForm.id) {
      await updateCategory(catForm.id, { name: catForm.name, sort: catForm.sort, status: catForm.status })
    } else {
      await createCategory({ name: catForm.name, sort: catForm.sort, status: catForm.status })
    }
    ElMessage.success('分类已保存')
    loadCategories()
    load()
  } catch (e) {
    ElMessage.error(e.message || '保存失败')
  }
}

async function removeCategory(row) {
  await ElMessageBox.confirm(`确认删除分类「${row.name}」？`, '删除确认', { type: 'warning' })
  try {
    await deleteCategory(row.categoryId)
    ElMessage.success('已删除')
    loadCategories()
  } catch (e) {
    ElMessage.error(e.message || '删除失败（可能被菜品引用）')
  }
}

onMounted(() => { load(); loadCategories() })
</script>

<template>
  <div>
    <el-card shadow="never">
      <div style="display:flex;gap:12px;flex-wrap:wrap;align-items:center">
        <el-select v-model="query.status" placeholder="状态" clearable style="width:120px">
          <el-option label="在售" value="ON_SALE" />
          <el-option label="下架" value="OFF_SALE" />
        </el-select>
        <el-select v-model="query.categoryId" placeholder="分类" clearable style="width:140px">
          <el-option v-for="c in categories" :key="c.categoryId" :label="c.name" :value="c.categoryId" />
        </el-select>
        <el-input v-model="query.keyword" placeholder="菜品名称" clearable style="width:180px" @keyup.enter="query.page = 1; load()" />
        <el-button type="primary" @click="query.page = 1; load()">查询</el-button>
        <div style="flex:1" />
        <el-button v-if="canManageCategory" @click="openCategories">分类管理</el-button>
        <el-button v-if="canCreate" type="primary" @click="openCreate">新建菜品</el-button>
      </div>

      <el-table :data="dishes" v-loading="loading" style="margin-top:16px">
        <el-table-column prop="dishId" label="ID" width="70" />
        <el-table-column prop="name" label="名称" min-width="140" />
        <el-table-column prop="categoryName" label="分类" width="120" />
        <el-table-column label="售价(星币)" width="110">
          <template #default="{ row }">¥{{ row.virtualPrice }}</template>
        </el-table-column>
        <el-table-column label="过敏原" min-width="140">
          <template #default="{ row }">
            <el-tag v-if="row.allergenStatus === 'DECLARED'" type="warning" size="small">
              {{ (row.allergens || []).join('、') || '已声明' }}
            </el-tag>
            <el-tag v-else type="info" size="small">未声明</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 'ON_SALE' ? 'success' : 'info'">
              {{ row.status === 'ON_SALE' ? '在售' : '下架' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button v-if="canEdit" link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button v-if="canEdit" link :type="row.status === 'ON_SALE' ? 'warning' : 'success'" @click="toggleStatus(row)">
              {{ row.status === 'ON_SALE' ? '下架' : '上架' }}
            </el-button>
            <el-button v-if="canDelete" link type="danger" @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination style="margin-top:16px;justify-content:flex-end" layout="total, prev, pager, next"
        :total="total" :page-size="query.pageSize" :current-page="query.page"
        @current-change="(p) => { query.page = p; load() }" />
    </el-card>

    <!-- 菜品编辑抽屉 -->
    <el-drawer v-model="drawer" :title="editing ? '编辑菜品' : '新建菜品'" size="420px">
      <el-form label-width="90px">
        <el-form-item label="名称"><el-input v-model="form.name" maxlength="64" /></el-form-item>
        <el-form-item label="分类">
          <el-select v-model="form.categoryId" style="width:100%">
            <el-option v-for="c in categories" :key="c.categoryId" :label="c.name" :value="c.categoryId" />
          </el-select>
        </el-form-item>
        <el-form-item label="菜品图片">
          <div style="display:flex;align-items:center;gap:12px">
            <el-image v-if="form.imageUrl" :src="form.imageUrl" fit="cover"
              style="width:88px;height:88px;border-radius:6px;border:1px solid #e4e7ed;background:#f5f7fa" />
            <el-upload v-if="canUploadImage" :show-file-list="false" :http-request="uploadImage"
              accept="image/jpeg,image/png,image/webp,image/gif" :disabled="uploading">
              <el-button :loading="uploading">{{ form.imageUrl ? '更换图片' : '上传图片' }}</el-button>
            </el-upload>
            <el-button v-if="canUploadImage && form.imageUrl" link type="danger" @click="form.imageUrl = ''">移除</el-button>
            <span v-if="!canUploadImage" style="font-size:12px;color:#909399">无上传权限</span>
          </div>
          <div style="font-size:12px;color:#909399;line-height:1.5">
            支持 jpg / png / webp / gif，单个不超过 5MB；由七牛云对象存储托管
          </div>
        </el-form-item>
        <el-form-item label="售价(星币)"><el-input-number v-model="form.virtualPrice" :min="0" :precision="2" /></el-form-item>
        <el-form-item label="卡路里"><el-input-number v-model="form.calories" :min="0" /></el-form-item>
        <el-form-item label="辣度"><el-input-number v-model="form.spiceLevel" :min="0" :max="3" /></el-form-item>
        <el-form-item label="标签"><el-input v-model="form.tags" placeholder="逗号分隔" /></el-form-item>
        <el-form-item label="过敏原">
          <el-select v-model="form.allergens" multiple allow-create filterable style="width:100%" placeholder="选择或输入">
          </el-select>
        </el-form-item>
        <el-form-item label="过敏原状态">
          <el-radio-group v-model="form.allergenStatus">
            <el-radio value="DECLARED">已声明</el-radio>
            <el-radio value="UNKNOWN">未声明</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio value="ON_SALE">在售</el-radio>
            <el-radio value="OFF_SALE">下架</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="submit">保存</el-button>
        </el-form-item>
      </el-form>
    </el-drawer>

    <!-- 分类管理 -->
    <el-dialog v-model="catDialog" title="菜品分类管理" width="520px">
      <div style="display:flex;gap:8px;margin-bottom:12px">
        <el-input v-model="catForm.name" placeholder="分类名" style="width:180px" />
        <el-input-number v-model="catForm.sort" :min="0" placeholder="排序" />
        <el-button type="primary" :disabled="!catForm.name" @click="catForm.id = null; saveCategory()">新增</el-button>
      </div>
      <el-table :data="categories" size="small">
        <el-table-column prop="name" label="名称" />
        <el-table-column prop="sort" label="排序" width="70" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 'ENABLED' ? 'success' : 'info'" size="small">
              {{ row.status === 'ENABLED' ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="140">
          <template #default="{ row }">
            <el-button link type="primary" size="small"
              @click="Object.assign(catForm, { id: row.categoryId, name: row.name, sort: row.sort, status: row.status }); saveCategory()">
              保存修改
            </el-button>
            <el-button link type="danger" size="small" @click="removeCategory(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>
  </div>
</template>
