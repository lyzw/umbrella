const api = require('../../services/api');
const ui = require('../../utils/page');
const config = require('../../config');
const recipes = require('../../services/recipes');

const SPICE_LABELS = ['无辣', '微辣', '中辣', '重辣'];
const STATUS_OPTIONS = [{ label: '在售', value: 'ON_SALE' }, { label: '已下架', value: 'OFF_SALE' }, { label: '全部', value: '' }];
const ALLERGEN_STATUS = ['已声明（儿童可点单）', '待确认（儿童不可选）'];
const ALLERGEN_LABELS = { PEANUT: '花生', MILK: '牛奶', EGG: '鸡蛋', FISH: '鱼类', SOY: '大豆', WHEAT: '小麦' };
const allergenLabel = code => ALLERGEN_LABELS[code] || code;
const allergenOption = (code, selected = []) => ({ code, label: allergenLabel(code), checked: selected.includes(code) });

const MAX_INGREDIENTS = recipes.MAX_INGREDIENTS;
const MAX_STEPS = recipes.MAX_STEPS;
// 行 key 只用于 wx:key 稳定复用（避免增删排序时输入框串行），与后端无关。
let rowSeq = 0;
const ingredientRow = (name = '', amount = '') => ({ key: 'ing' + (++rowSeq), name, amount });
const stepRow = (text = '') => ({ key: 'step' + (++rowSeq), text });

function blankForm() {
  return {
    showForm: false, isEdit: false, formError: '',
    formDishId: '', formVersion: 0,
    formName: '', formImageUrl: '', formCategoryId: '', formCategoryIndex: -1,
    formVirtualPrice: '', formCalories: '', formTagsText: '',
    formAllergens: [], formAllergenStatus: 'DECLARED', formAllergenIndex: 0, formSpiceIndex: 0,
    formImageError: false,
    // v011 配方字段：新建时各给一行空位降低录入成本，提交时空行会被丢弃。
    formIngredients: [ingredientRow()], formCookSteps: [stepRow()], formCookTips: '',
    formCookMinutes: '', formServings: '', formDifficultyIndex: 0,
    formRecipeNotice: ''
  };
}
// 数组整体移动：越界时原样返回，避免调用方写边界判断。
function moveRow(rows, index, delta) {
  const target = index + delta;
  if (index < 0 || index >= rows.length || target < 0 || target >= rows.length) return rows;
  const next = rows.slice();
  const [row] = next.splice(index, 1);
  next.splice(target, 0, row);
  return next;
}
/**
 * 配方字段本地校验 + 归一化，规则与后端 DishRecipeService.validate 对齐
 * （此处只做提前拦截，最终裁决仍在服务端）。
 * 空行（食材名为空 / 步骤为空）直接丢弃：允许用户留空行不必手动删干净。
 */
function collectRecipe(data) {
  const ingredients = (data.formIngredients || [])
    .map(row => ({ name: String(row.name || '').trim(), amount: String(row.amount || '').trim() }))
    .filter(row => row.name);
  if (ingredients.length > MAX_INGREDIENTS) throw new Error('食材最多 ' + MAX_INGREDIENTS + ' 条');
  const seen = new Set();
  for (const item of ingredients) {
    if (item.name.length > 32) throw new Error('食材名称最长 32 字：' + item.name);
    if (item.amount.length > 32) throw new Error('食材用量最长 32 字：' + item.name);
    const key = item.name.toLowerCase();
    if (seen.has(key)) throw new Error('食材名称重复：' + item.name);
    seen.add(key);
  }
  const cookSteps = (data.formCookSteps || [])
    .map(row => String(row.text || '').trim()).filter(Boolean);
  if (cookSteps.length > MAX_STEPS) throw new Error('做法最多 ' + MAX_STEPS + ' 步');
  cookSteps.forEach((step, index) => {
    if (step.length > 300) throw new Error('第 ' + (index + 1) + ' 步做法最长 300 字');
  });
  const cookTips = String(data.formCookTips || '').trim();
  if (cookTips.length > 500) throw new Error('小贴士最长 500 字');
  const minutesText = String(data.formCookMinutes === null || data.formCookMinutes === undefined
    ? '' : data.formCookMinutes).trim();
  if (minutesText && !/^\d{1,4}$/.test(minutesText)) throw new Error('烹饪时长需为 1-1440 的整数分钟');
  const cookMinutes = minutesText ? Number(minutesText) : null;
  if (cookMinutes !== null && (cookMinutes < 1 || cookMinutes > 1440)) {
    throw new Error('烹饪时长需在 1 至 1440 分钟之间');
  }
  const servingsText = String(data.formServings === null || data.formServings === undefined
    ? '' : data.formServings).trim();
  if (servingsText && !/^\d{1,2}$/.test(servingsText)) throw new Error('份量需为 1-20 的整数');
  const servings = servingsText ? Number(servingsText) : null;
  if (servings !== null && (servings < 1 || servings > 20)) throw new Error('份量需在 1 至 20 人份之间');
  return { ingredients, cookSteps, cookTips, cookMinutes, servings,
    difficulty: recipes.difficultyValue(data.formDifficultyIndex) };
}

ui.page({
  data: Object.assign(blankForm(), {
    role: '', busy: false, error: '', ready: false,
    dishes: [], page: 1, pageSize: 20, total: 0, hasPrev: false, hasNext: false,
    keyword: '', statusIndex: 0, statusOptions: STATUS_OPTIONS.map(option => option.label),
    categories: [], categoryNames: [],
    spiceOptions: SPICE_LABELS, allergenStatusOptions: ALLERGEN_STATUS,
    difficultyOptions: recipes.DIFFICULTY_OPTIONS,
    maxIngredients: MAX_INGREDIENTS, maxSteps: MAX_STEPS,
    allergenOptions: config.allergens.map(code => allergenOption(code))
  }),
  input: ui.input,
  onShow() {
    if (!ui.guard(this, 'PARENT')) return;
    this.setData(blankForm());
    ui.run(this, async () => {
      await this.loadCategories();
      await this.read();
    });
  },
  async loadCategories() {
    const categories = await api.get('/parent/dish-category');
    this.setData({ categories, categoryNames: categories.map(category => category.name) });
  },
  async read() {
    const { page, pageSize, keyword, statusIndex } = this.data;
    const status = STATUS_OPTIONS[statusIndex].value;
    const result = await api.get('/parent/family-dish', {
      page, pageSize, status: status || undefined, keyword: keyword || undefined
    });
    const dishes = (result.items || []).map(item => ({
      ...item,
      statusLabel: item.status === 'ON_SALE' ? '在售' : '已下架',
      spiceLabel: SPICE_LABELS[item.spiceLevel] || '无辣',
      allergenLabel: item.allergens && item.allergens.length
        ? item.allergens.map(allergenLabel).join('、') : '未声明过敏原',
      // 列表摘要全部取自列表响应已有字段，不为每行发配方请求（明细只在编辑时懒加载）。
      recipeSummary: recipes.recipeSummary(item),
      difficultyLabel: recipes.difficultyLabel(item.difficulty),
      toggleLabel: item.status === 'ON_SALE' ? '下架' : '上架'
    }));
    this.setData({ dishes, total: result.total, ready: true,
      hasPrev: page > 1, hasNext: page * pageSize < result.total });
  },
  search(e) { this.setData({ keyword: e.detail.value, page: 1 }); ui.run(this, () => this.read()); },
  filter(e) { this.setData({ statusIndex: Number(e.detail.value), page: 1 }); ui.run(this, () => this.read()); },
  prev() { if (this.data.hasPrev) { this.setData({ page: this.data.page - 1 }); ui.run(this, () => this.read()); } },
  nextPage() { if (this.data.hasNext) { this.setData({ page: this.data.page + 1 }); ui.run(this, () => this.read()); } },
  create() {
    this.setData(Object.assign(blankForm(), {
      showForm: true, isEdit: false,
      allergenOptions: config.allergens.map(code => allergenOption(code))
    }));
  },
  edit(e) {
    const dish = this.data.dishes.find(item => item.dishId === e.currentTarget.dataset.id);
    if (!dish) return;
    const categoryIndex = this.data.categories.findIndex(category => category.categoryId === dish.categoryId);
    this.setData({
      showForm: true, isEdit: true, formError: '', formRecipeNotice: '',
      formDishId: dish.dishId, formVersion: dish.version,
      formName: dish.name, formImageUrl: dish.imageUrl || '',
      formCategoryId: dish.categoryId, formCategoryIndex: categoryIndex,
      formVirtualPrice: dish.virtualPrice,
      formCalories: dish.calories === null || dish.calories === undefined ? '' : String(dish.calories),
      formTagsText: dish.tags || '', formAllergens: [...(dish.allergens || [])],
      formAllergenStatus: dish.allergenStatus, formAllergenIndex: dish.allergenStatus === 'DECLARED' ? 0 : 1,
      formSpiceIndex: dish.spiceLevel,
      formCookMinutes: dish.cookMinutes ? String(dish.cookMinutes) : '',
      formServings: dish.servings ? String(dish.servings) : '',
      formDifficultyIndex: recipes.difficultyIndex(dish.difficulty),
      // 配方明细不在列表响应里（只给摘要），必须单独拉；先把表单清空，避免残留上一道菜的配方。
      formIngredients: [], formCookSteps: [], formCookTips: '',
      allergenOptions: Array.from(new Set([...config.allergens, ...(dish.allergens || [])]))
        .map(code => allergenOption(code, dish.allergens || []))
    });
    return ui.run(this, async () => {
      try {
        const payload = await recipes.fetchRecipe('FAMILY', dish.dishId);
        // 用户可能已关闭表单或改编辑别的菜：仅在仍停留在这道菜时回填。
        if (!this.data.isEdit || this.data.formDishId !== dish.dishId) return;
        this.setData({
          formIngredients: (payload.ingredients || []).map(item => ingredientRow(item.name, item.amount || '')),
          formCookSteps: (payload.cookSteps || []).map(text => stepRow(text)),
          formCookTips: payload.cookTips || '',
          // 时长 / 份量 / 难度以配方端点为准（列表摘要只服务列表展示）：
          // 配方是单一权威来源，避免两处取值在未来某处缺字段时静默留旧值。
          formCookMinutes: payload.cookMinutes ? String(payload.cookMinutes) : '',
          formServings: payload.servings ? String(payload.servings) : '',
          formDifficultyIndex: recipes.difficultyIndex(payload.difficulty)
        });
      } catch (error) {
        if (error.status === 401 || error.code === 'E-010') throw error;
        this.setData({ formRecipeNotice: '配方明细加载失败：' + (error.message || '未知原因')
          + '。此时保存会用当前空配方覆盖原有食材与做法，建议先关闭表单重试。' });
      }
    });
  },
  closeForm() { this.setData(blankForm()); },
  pickCategory(e) {
    const index = Number(e.detail.value);
    const category = this.data.categories[index];
    if (category) this.setData({ formCategoryIndex: index, formCategoryId: category.categoryId });
  },
  pickSpice(e) { this.setData({ formSpiceIndex: Number(e.detail.value) }); },
  pickDifficulty(e) { this.setData({ formDifficultyIndex: Number(e.detail.value) }); },
  addIngredient() {
    if (this.data.formIngredients.length >= MAX_INGREDIENTS) return;
    this.setData({ formIngredients: [...this.data.formIngredients, ingredientRow()] });
  },
  removeIngredient(e) {
    const index = Number(e.currentTarget.dataset.index);
    this.setData({ formIngredients: this.data.formIngredients.filter((row, i) => i !== index) });
  },
  moveIngredient(e) {
    const { index, delta } = e.currentTarget.dataset;
    this.setData({ formIngredients: moveRow(this.data.formIngredients, Number(index), Number(delta)) });
  },
  addStep() {
    if (this.data.formCookSteps.length >= MAX_STEPS) return;
    this.setData({ formCookSteps: [...this.data.formCookSteps, stepRow()] });
  },
  removeStep(e) {
    const index = Number(e.currentTarget.dataset.index);
    this.setData({ formCookSteps: this.data.formCookSteps.filter((row, i) => i !== index) });
  },
  moveStep(e) {
    const { index, delta } = e.currentTarget.dataset;
    this.setData({ formCookSteps: moveRow(this.data.formCookSteps, Number(index), Number(delta)) });
  },
  pickAllergenStatus(e) {
    const index = Number(e.detail.value);
    this.setData({ formAllergenIndex: index, formAllergenStatus: index === 0 ? 'DECLARED' : 'UNKNOWN' });
  },
  allergens(e) { this.setData({ formAllergens: e.detail.value }); },
  imageUrlInput(e) {
    this.setData({ formImageUrl: e.detail.value, formImageError: false });
  },
  clearImage() {
    this.setData({ formImageUrl: '', formImageError: false });
  },
  save() {
    return ui.run(this, async () => {
      const name = String(this.data.formName || '').trim();
      if (!name || name.length > 64) throw new Error('请填写菜品名称（1-64 字）');
      if (!this.data.formCategoryId) throw new Error('请选择分类');
      const price = String(this.data.formVirtualPrice === null || this.data.formVirtualPrice === undefined
        ? '' : this.data.formVirtualPrice).trim();
      if (!/^\d{1,8}(\.\d{1,2})?$/.test(price)) throw new Error('虚拟价格需为最多两位小数的非负金额');
      const calories = String(this.data.formCalories === null || this.data.formCalories === undefined
        ? '' : this.data.formCalories).trim();
      if (calories && !/^\d{1,5}$/.test(calories)) throw new Error('卡路里需为非负整数（最大 99999）');
      const tags = Array.from(new Set(String(this.data.formTagsText || '')
        .split(/[,，\n]/).map(tag => tag.trim()).filter(Boolean)));
      if (tags.length > 5 || tags.some(tag => tag.length > 16)) throw new Error('标签最多 5 项，每项最多 16 字');
      const allergens = this.data.formAllergens || [];
      if (allergens.length && this.data.formAllergenStatus !== 'DECLARED') {
        throw new Error('已选择过敏原时，请将过敏原状态设为「已声明」');
      }
      const recipe = collectRecipe(this.data);
      const body = {
        categoryId: this.data.formCategoryId,
        name,
        virtualPrice: price,
        allergens,
        allergenStatus: this.data.formAllergenStatus,
        spiceLevel: this.data.formSpiceIndex
      };
      const imageUrl = String(this.data.formImageUrl || '').trim();
      if (imageUrl && !/^https:\/\/\S+$/i.test(imageUrl)) throw new Error('图片地址必须是有效的 HTTPS 链接');
      if (imageUrl) body.imageUrl = imageUrl;
      if (tags.length) body.tags = tags.join(',');
      if (calories) body.calories = Number(calories);
      // 配方六字段全部可选，且后端按「整体替换」处理：不传即等同清空。
      // 因此每次保存都会提交当前表单的完整配方集合，不存在「只改某一条食材」的局部提交。
      if (recipe.ingredients.length) body.ingredients = recipe.ingredients;
      if (recipe.cookSteps.length) body.cookSteps = recipe.cookSteps;
      if (recipe.cookTips) body.cookTips = recipe.cookTips;
      if (recipe.cookMinutes !== null) body.cookMinutes = recipe.cookMinutes;
      if (recipe.servings !== null) body.servings = recipe.servings;
      if (recipe.difficulty) body.difficulty = recipe.difficulty;
      const isEdit = this.data.isEdit;
      if (isEdit) {
        await api.put('/parent/family-dish/' + this.data.formDishId + '?expectedVersion=' + this.data.formVersion, body);
      } else {
        await api.post('/parent/family-dish', body);
      }
      this.setData(blankForm());
      wx.showToast({ title: isEdit ? '已保存' : '已新增', icon: 'success' });
      await this.read();
    });
  },
  toggleStatus(e) {
    return ui.run(this, async () => {
      const dish = this.data.dishes.find(item => item.dishId === e.currentTarget.dataset.id);
      if (!dish) return;
      const target = dish.status === 'ON_SALE' ? 'OFF_SALE' : 'ON_SALE';
      const label = target === 'OFF_SALE' ? '下架' : '上架';
      if (!await ui.confirm('确认' + label + '「' + dish.name + '」？')) return;
      await api.post('/parent/family-dish/' + dish.dishId + '/status?targetStatus=' + target
        + '&expectedVersion=' + dish.version);
      await this.read();
    });
  },
  remove(e) {
    return ui.run(this, async () => {
      const dish = this.data.dishes.find(item => item.dishId === e.currentTarget.dataset.id);
      if (!dish) return;
      const reference = await api.get('/parent/family-dish/' + dish.dishId + '/references');
      const count = reference && reference.menuCount ? reference.menuCount : 0;
      const content = count > 0
        ? '该菜被 ' + count + ' 份菜单引用，删除后这些菜单将标记缺失，确认删除？'
        : '确认删除「' + dish.name + '」？删除后不可再用于菜单。';
      if (!await ui.confirm(content, '删除菜品')) return;
      await api.del('/parent/family-dish/' + dish.dishId, { expectedVersion: dish.version });
      wx.showToast({ title: '已删除', icon: 'success' });
      await this.read();
    });
  },
  imageError(e) {
    if (e.currentTarget.dataset.target === 'form') {
      this.setData({ formImageError: true });
      return;
    }
    this.setData({ dishes: this.data.dishes.map(item =>
      item.dishId === e.currentTarget.dataset.id ? { ...item, imageUrl: null } : item) });
  },
  onHide() { this.setData(Object.assign(blankForm(), { dishes: [], ready: false, error: '' })); }
});
