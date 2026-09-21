const api = require('../../services/api');
const ui = require('../../utils/page');
const config = require('../../config');

const SPICE_LABELS = ['无辣', '微辣', '中辣', '重辣'];
const STATUS_OPTIONS = [{ label: '在售', value: 'ON_SALE' }, { label: '已下架', value: 'OFF_SALE' }, { label: '全部', value: '' }];
const ALLERGEN_STATUS = ['已声明（儿童可点单）', '待确认（儿童不可选）'];

function blankForm() {
  return {
    showForm: false, isEdit: false, formError: '',
    formDishId: '', formVersion: 0,
    formName: '', formImageUrl: '', formCategoryId: '', formCategoryIndex: -1,
    formVirtualPrice: '', formCalories: '', formTagsText: '',
    formAllergens: [], formAllergenStatus: 'DECLARED', formAllergenIndex: 0, formSpiceIndex: 0
  };
}

ui.page({
  data: Object.assign(blankForm(), {
    role: '', busy: false, error: '', ready: false,
    dishes: [], page: 1, pageSize: 20, total: 0, hasPrev: false, hasNext: false,
    keyword: '', statusIndex: 0, statusOptions: STATUS_OPTIONS.map(option => option.label),
    categories: [], categoryNames: [],
    spiceOptions: SPICE_LABELS, allergenStatusOptions: ALLERGEN_STATUS,
    allergenOptions: config.allergens.map(code => ({ code, checked: false }))
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
      allergenLabel: item.allergens && item.allergens.length ? item.allergens.join('、') : '未声明过敏原',
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
      allergenOptions: config.allergens.map(code => ({ code, checked: false }))
    }));
  },
  edit(e) {
    const dish = this.data.dishes.find(item => item.dishId === e.currentTarget.dataset.id);
    if (!dish) return;
    const categoryIndex = this.data.categories.findIndex(category => category.categoryId === dish.categoryId);
    this.setData({
      showForm: true, isEdit: true, formError: '',
      formDishId: dish.dishId, formVersion: dish.version,
      formName: dish.name, formImageUrl: dish.imageUrl || '',
      formCategoryId: dish.categoryId, formCategoryIndex: categoryIndex,
      formVirtualPrice: dish.virtualPrice,
      formCalories: dish.calories === null || dish.calories === undefined ? '' : String(dish.calories),
      formTagsText: dish.tags || '', formAllergens: [...(dish.allergens || [])],
      formAllergenStatus: dish.allergenStatus, formAllergenIndex: dish.allergenStatus === 'DECLARED' ? 0 : 1,
      formSpiceIndex: dish.spiceLevel,
      allergenOptions: config.allergens.map(code => ({ code, checked: (dish.allergens || []).includes(code) }))
    });
  },
  closeForm() { this.setData(blankForm()); },
  pickCategory(e) {
    const index = Number(e.detail.value);
    const category = this.data.categories[index];
    if (category) this.setData({ formCategoryIndex: index, formCategoryId: category.categoryId });
  },
  pickSpice(e) { this.setData({ formSpiceIndex: Number(e.detail.value) }); },
  pickAllergenStatus(e) {
    const index = Number(e.detail.value);
    this.setData({ formAllergenIndex: index, formAllergenStatus: index === 0 ? 'DECLARED' : 'UNKNOWN' });
  },
  allergens(e) { this.setData({ formAllergens: e.detail.value }); },
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
      const body = {
        categoryId: this.data.formCategoryId,
        name,
        virtualPrice: price,
        allergens,
        allergenStatus: this.data.formAllergenStatus,
        spiceLevel: this.data.formSpiceIndex
      };
      const imageUrl = String(this.data.formImageUrl || '').trim();
      if (imageUrl) body.imageUrl = imageUrl;
      if (tags.length) body.tags = tags.join(',');
      if (calories) body.calories = Number(calories);
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
    this.setData({ dishes: this.data.dishes.map(item =>
      item.dishId === e.currentTarget.dataset.id ? { ...item, imageUrl: null } : item) });
  },
  onHide() { this.setData(Object.assign(blankForm(), { dishes: [], ready: false, error: '' })); }
});
