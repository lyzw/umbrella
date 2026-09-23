<script setup>
import { ref, reactive, onMounted, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listDishes, createDish, updateDish, deleteDish, toggleDishStatus, listCategories, createCategory, updateCategory, deleteCategory, uploadToQiniu, getDishReferences } from '../api/console'
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

// 过敏原候选值来自后端发布目录（compliance.allergens），前端不硬编码：
// 取值不在目录内会被后端判 E-400，且 catalog-reference 未配置时任何过敏原组合都保存不了。
const references = reactive({ allergens: [], catalogReady: false })

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

async function loadReferences() {
  try {
    const { data } = await getDishReferences()
    references.allergens = data.allergens || []
    references.catalogReady = !!data.catalogReady
  } catch (e) {
    ElMessage.error(e.message || '过敏原字典加载失败')
  }
}

// ==================== 菜品编辑抽屉 ====================
const drawer = ref(false)
const editing = ref(null)

/**
 * 配方空态。form 是长期复用的 reactive 对象，openCreate 必须显式重置这组字段，
 * 否则会残留上一道菜编辑过的食材与步骤。
 */
const emptyRecipe = () => ({
  ingredients: [], cookSteps: [], cookTips: '', cookMinutes: null, servings: null, difficulty: ''
})

const form = reactive({
  categoryId: null, name: '', imageUrl: '', virtualPrice: 0, calories: null,
  tags: '', allergens: [], allergenStatus: 'UNKNOWN', spiceLevel: 0, status: 'OFF_SALE',
  ...emptyRecipe()
})

function openCreate() {
  editing.value = null
  Object.assign(form, {
    categoryId: categories.value[0]?.categoryId ?? null, name: '', imageUrl: '', virtualPrice: 0,
    calories: null, tags: '', allergens: [], allergenStatus: 'UNKNOWN', spiceLevel: 0, status: 'OFF_SALE'
  }, emptyRecipe())
  drawer.value = true
}

async function openEdit(row) {
  editing.value = row
  try {
    const { data } = await (await import('../api/console')).getDish(row.dishId)
    // 配方回填必须归一化：详情路径才带 recipe，且字段可能为 null。
    // 一律先兜底再绑定——上一批刚因「多解一层 data」让整页 TypeError。
    const recipe = data.recipe || {}
    Object.assign(form, {
      categoryId: Number(data.categoryId), name: data.name, imageUrl: data.imageUrl || '',
      virtualPrice: Number(data.virtualPrice), calories: data.calories, tags: data.tags || '',
      allergens: data.allergens || [], allergenStatus: data.allergenStatus, spiceLevel: data.spiceLevel,
      status: data.status,
      ingredients: (recipe.ingredients || []).map((it) => ({ name: it?.name || '', amount: it?.amount || '' })),
      cookSteps: [...(recipe.cookSteps || [])],
      cookTips: recipe.cookTips || '',
      cookMinutes: recipe.cookMinutes ?? null,
      servings: recipe.servings ?? null,
      difficulty: recipe.difficulty || ''
    })
    drawer.value = true
  } catch (e) {
    ElMessage.error(e.message || '菜品详情加载失败')
  }
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

// ==================== 配方编辑（食材行 / 有序步骤卡片） ====================
// 上限与后端 DishRecipeService 常量逐一对齐，前端只做「提前拦截」，最终判定仍以后端为准。
const MAX_INGREDIENTS = 30
const MAX_STEP_LENGTH = 300
const MAX_STEPS = 20
const MAX_TIPS = 500
const MAX_COOK_MINUTES = 1440
const MAX_SERVINGS = 20

/** 难度枚举 → 中文标签（落库仍是英文枚举，与后端 CHECK 约束一致）。 */
const DIFFICULTIES = [
  { value: 'EASY', label: '简单' },
  { value: 'MEDIUM', label: '中等' },
  { value: 'HARD', label: '有挑战' }
]

function addIngredient() {
  if (form.ingredients.length >= MAX_INGREDIENTS) return
  form.ingredients.push({ name: '', amount: '' })
}

function addStep() {
  if (form.cookSteps.length >= MAX_STEPS) return
  form.cookSteps.push('')
}

/** 上移 / 下移：数组下标即顺序，落库时按下标写 sort，故交换元素即可。 */
function moveItem(list, idx, delta) {
  const target = idx + delta
  if (target < 0 || target >= list.length) return
  const [item] = list.splice(idx, 1)
  list.splice(target, 0, item)
}

function difficultyLabel(value) {
  return DIFFICULTIES.find((d) => d.value === value)?.label || ''
}

/** 列表「配方」列第二行：时长 / 份量 / 难度（缺失项自动跳过）。 */
function recipeMeta(row) {
  const parts = []
  if (row.cookMinutes) parts.push(`${row.cookMinutes} 分钟`)
  if (row.servings) parts.push(`${row.servings} 人份`)
  const diff = difficultyLabel(row.difficulty)
  if (diff) parts.push(diff)
  return parts.join(' · ')
}

/** 一行菜品是否已有任何配方信息（列表显示 — 与否，编辑时决定是否给补齐提示）。 */
function hasRecipe(row) {
  if (!row) return false
  return !!((row.ingredientCount || 0) > 0 || (row.stepCount || 0) > 0 || recipeMeta(row))
}

/**
 * 提交前置校验：把必填项与后端 R5-c 红线（在售必须先声明过敏原）提前到本地，
 * 避免「填完点保存才吃 E-400」；过敏原取值也要与发布目录一致。
 * @returns 问题描述；无问题返回 null
 */
function validateForm() {
  if (!form.name || !form.name.trim()) return '请填写菜品名称'
  if (!form.categoryId) return '请选择菜品分类'
  if (form.virtualPrice === null || form.virtualPrice === undefined || form.virtualPrice < 0) {
    return '售价不能为空且不能为负'
  }
  if (form.status === 'ON_SALE' && form.allergenStatus !== 'DECLARED') {
    return '在售菜品必须先声明过敏原；可先按下架草稿保存'
  }
  const invalid = (form.allergens || []).filter((a) => !references.allergens.includes(a))
  if (invalid.length) return `过敏原「${invalid.join('、')}」不在发布目录内`
  const recipeProblem = validateRecipe()
  if (recipeProblem) return recipeProblem
  return null
}

/** 配方校验：规则与后端 DishRecipeService.validate 一致（含中文原因），报错文案也保持同义。 */
function validateRecipe() {
  if (form.ingredients.length > MAX_INGREDIENTS) return `食材最多 ${MAX_INGREDIENTS} 条`
  const seen = new Set()
  for (let i = 0; i < form.ingredients.length; i++) {
    const name = (form.ingredients[i].name || '').trim()
    if (!name) return `第 ${i + 1} 条食材名称不能为空`
    if (name.length > 32) return `第 ${i + 1} 条食材名称最长 32 字`
    if ((form.ingredients[i].amount || '').trim().length > 32) return `第 ${i + 1} 条食材用量最长 32 字`
    const key = name.toLowerCase()
    if (seen.has(key)) return `食材名称重复：${name}`
    seen.add(key)
  }
  if (form.cookSteps.length > MAX_STEPS) return `做法最多 ${MAX_STEPS} 步`
  for (let i = 0; i < form.cookSteps.length; i++) {
    if (!(form.cookSteps[i] || '').trim()) return `第 ${i + 1} 步做法不能为空，请填写内容或删除该步`
    if (form.cookSteps[i].trim().length > MAX_STEP_LENGTH) {
      return `第 ${i + 1} 步做法最长 ${MAX_STEP_LENGTH} 字`
    }
  }
  if ((form.cookTips || '').length > MAX_TIPS) return `小贴士最长 ${MAX_TIPS} 字`
  // el-input-number 清空后是 null/undefined，故用 != null 同时排除两者
  if (form.cookMinutes != null && (form.cookMinutes < 1 || form.cookMinutes > MAX_COOK_MINUTES)) {
    return `烹饪时长需在 1 至 ${MAX_COOK_MINUTES} 分钟之间`
  }
  if (form.servings != null && (form.servings < 1 || form.servings > MAX_SERVINGS)) {
    return `份量需在 1 至 ${MAX_SERVINGS} 人份之间`
  }
  if (form.difficulty && !DIFFICULTIES.some((d) => d.value === form.difficulty)) return '难度取值不合法'
  return null
}

async function submit() {
  const problem = validateForm()
  if (problem) {
    ElMessage.warning(problem)
    return
  }
  // 归一化后再提交，与后端 normalize* 保持一致：
  // 空用量 / 空小贴士 / 空难度一律传 null —— 传空串会被后端判「难度取值不合法」。
  const payload = {
    ...form,
    ingredients: form.ingredients.map((it) => ({
      name: it.name.trim(),
      amount: (it.amount || '').trim() || null
    })),
    cookSteps: form.cookSteps.map((s) => s.trim()),
    cookTips: (form.cookTips || '').trim() || null,
    difficulty: form.difficulty || null
  }
  try {
    if (editing.value) {
      await updateDish(editing.value.dishId, payload)
      ElMessage.success('菜品已更新')
    } else {
      await createDish(payload)
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

onMounted(() => { load(); loadCategories(); loadReferences() })
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
        <el-table-column label="配方" min-width="150">
          <template #default="{ row }">
            <template v-if="hasRecipe(row)">
              <div>食材 {{ row.ingredientCount || 0 }} · 步骤 {{ row.stepCount || 0 }}</div>
              <div v-if="recipeMeta(row)" style="font-size:12px;color:#909399">{{ recipeMeta(row) }}</div>
            </template>
            <span v-else style="color:#909399">—</span>
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

    <!-- 菜品编辑抽屉：基础信息 / 配方信息 / 烹饪属性 三组（760px 容纳食材行与步骤卡片） -->
    <el-drawer v-model="drawer" :title="editing ? '编辑菜品' : '新建菜品'" size="760px">
      <el-form label-width="90px">
        <el-divider content-position="left">基础信息</el-divider>
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
          <el-select v-model="form.allergens" multiple filterable style="width:100%"
            :disabled="!references.catalogReady" placeholder="从发布目录选择">
            <el-option v-for="a in references.allergens" :key="a" :label="a" :value="a" />
          </el-select>
          <div style="font-size:12px;color:#909399;line-height:1.5">
            候选值来自后端过敏原发布目录（英文标识，如 PEANUT / MILK）；在售菜品必须先声明过敏原
          </div>
          <div v-if="!references.catalogReady" style="font-size:12px;color:#e6a23c;line-height:1.5">
            发布目录未配置（compliance.catalog-reference / allergens），当前无法保存菜品，请联系运维
          </div>
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

        <el-divider content-position="left">配方信息</el-divider>
        <el-alert v-if="editing && !hasRecipe(editing)" type="info" :closable="false" show-icon
          title="建议补齐食材与做法，便于家长照做" style="margin-bottom:12px" />
        <el-form-item label="食材">
          <div class="recipe-block">
            <div v-for="(it, idx) in form.ingredients" :key="`ing-${idx}`" class="recipe-row">
              <span class="recipe-index">{{ idx + 1 }}</span>
              <el-input v-model="it.name" maxlength="32" placeholder="食材名称（如 猪里脊）" style="width:200px" />
              <el-input v-model="it.amount" maxlength="32" placeholder="用量（如 200g，可留空）" style="width:200px" />
              <el-button link :disabled="idx === 0" @click="moveItem(form.ingredients, idx, -1)">上移</el-button>
              <el-button link :disabled="idx === form.ingredients.length - 1"
                @click="moveItem(form.ingredients, idx, 1)">下移</el-button>
              <el-button link type="danger" @click="form.ingredients.splice(idx, 1)">删除</el-button>
            </div>
            <div class="recipe-actions">
              <el-button :disabled="form.ingredients.length >= MAX_INGREDIENTS" @click="addIngredient">添加食材</el-button>
              <span class="recipe-hint">
                共 {{ form.ingredients.length }} 条，上限 {{ MAX_INGREDIENTS }} 条（顺序即展示顺序）
              </span>
            </div>
          </div>
        </el-form-item>
        <el-form-item label="做法步骤">
          <div class="recipe-block">
            <div v-for="(step, idx) in form.cookSteps" :key="`step-${idx}`" class="recipe-step">
              <div class="recipe-step-head">
                <span class="recipe-index">{{ idx + 1 }}</span>
                <span style="font-size:13px;color:#606266">第 {{ idx + 1 }} 步</span>
                <div style="flex:1" />
                <el-button link :disabled="idx === 0" @click="moveItem(form.cookSteps, idx, -1)">上移</el-button>
                <el-button link :disabled="idx === form.cookSteps.length - 1"
                  @click="moveItem(form.cookSteps, idx, 1)">下移</el-button>
                <el-button link type="danger" @click="form.cookSteps.splice(idx, 1)">删除</el-button>
              </div>
              <el-input v-model="form.cookSteps[idx]" type="textarea" :rows="3" maxlength="300" show-word-limit
                placeholder="描述这一步怎么做" />
            </div>
            <div class="recipe-actions">
              <el-button :disabled="form.cookSteps.length >= MAX_STEPS" @click="addStep">添加步骤</el-button>
              <span class="recipe-hint">共 {{ form.cookSteps.length }} 步，上限 {{ MAX_STEPS }} 步（按顺序展示）</span>
            </div>
          </div>
        </el-form-item>
        <el-form-item label="小贴士">
          <el-input v-model="form.cookTips" type="textarea" :rows="2" maxlength="500" show-word-limit
            placeholder="给孩子吃怎么调整，如「辣椒减半、肉切小块」" />
        </el-form-item>

        <el-divider content-position="left">烹饪属性</el-divider>
        <el-form-item label="时长(分钟)">
          <el-input-number v-model="form.cookMinutes" :min="1" :max="MAX_COOK_MINUTES" />
        </el-form-item>
        <el-form-item label="份量(人份)">
          <el-input-number v-model="form.servings" :min="1" :max="MAX_SERVINGS" />
        </el-form-item>
        <el-form-item label="难度">
          <el-select v-model="form.difficulty" clearable placeholder="不填则不显示" style="width:200px">
            <el-option v-for="d in DIFFICULTIES" :key="d.value" :label="d.label" :value="d.value" />
          </el-select>
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

<style scoped>
/* 配方编辑区：食材行与有序步骤卡片（抽屉已加宽到 760px，这里只控行内节奏） */
.recipe-block {
  width: 100%;
}

.recipe-row {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 0;
}

.recipe-index {
  flex: 0 0 22px;
  height: 22px;
  line-height: 22px;
  text-align: center;
  border-radius: 4px;
  background: #f0f2f5;
  color: #606266;
  font-size: 12px;
}

.recipe-step {
  width: 100%;
  padding: 10px 12px;
  margin-bottom: 10px;
  border: 1px solid #e4e7ed;
  border-radius: 6px;
  background: #fafafa;
}

.recipe-step-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
}

.recipe-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 4px;
}

.recipe-hint {
  font-size: 12px;
  color: #909399;
}
</style>
