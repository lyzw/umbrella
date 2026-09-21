const api = require('../../services/api');
const ui = require('../../utils/page');
const context = require('../../services/context');
const { loadChildren } = require('../../services/children');
const { cents, money, shanghaiDate, selectable, filterDishes, dishRef, dishKey } = require('../../utils/domain');

const SPICE = ['无辣', '微辣', '中辣', '重辣'];
const MEALS = ['BREAKFAST', 'LUNCH', 'DINNER'];

ui.page({
  data: { role: '', busy: false, error: '', children: [], childIndex: 0, childId: '', sourceType: 'FAMILY',
    menuDate: shanghaiDate(), today: shanghaiDate(), mealType: 'LUNCH', meals: ['早餐', '午餐', '晚餐'], mealIndex: 1,
    menu: null, dishes: [], mildOnly: false, favoritesOnly: false, keyword: '', total: '0.00', count: 0,
    sourceTab: 'FAMILY', missingCount: 0, missingDishIds: [], invalidSelectedCount: 0, ready: false },
  onLoad(query) { this.previousConfirmId = query.previousConfirmId || null; },
  onShow() {
    if (!ui.guard(this)) return;
    this.quantities = {};
    this.allDishes = [];
    this.catalogDishes = [];
    this.selectedRefs = [];
    this.tabPinned = false;
    ui.run(this, async () => {
      if (this.data.role === 'PARENT') {
        await this.loadParent();
        return;
      }
      const children = await loadChildren();
      this.setData({ children, childId: children[0].childId, childIndex: 0, today: shanghaiDate() });
      if (this.previousConfirmId && this.data.role === 'CHILD') {
        const previous = await api.get('/menu/confirm/' + this.previousConfirmId);
        if (!['REJECTED', 'CANCELLED'].includes(previous.status)) throw new Error('仅拒绝或撤回的确认单可重新提报');
        if (previous.menuDate !== shanghaiDate()) throw new Error('原餐单已过期，请返回首页选择今日餐单');
        this.setData({ menuDate: previous.menuDate, mealType: previous.mealType, sourceType: 'FAMILY',
          mealIndex: MEALS.indexOf(previous.mealType) });
      }
      await this.read();
    });
  },
  async loadParent() {
    // 两个来源分别加载：预置菜品（运营维护）+ 家庭私有菜品（家长录入）。
    this.presetDishes = await this.loadAll('/parent/dish');
    this.familyDishes = (await this.loadAll('/parent/family-dish', { status: 'ON_SALE' }))
      .map(item => ({ ...item, sourceType: 'FAMILY' }));
    this.catalogDishes = [...this.familyDishes, ...this.presetDishes.map(item => ({ ...item, sourceType: 'PRESET' }))];
    await this.readParent();
  },
  async loadAll(endpoint, query) {
    const items = [];
    let page = 1;
    let total = 0;
    do {
      const result = await api.get(endpoint, { ...(query || {}), page, pageSize: 100 });
      items.push(...(result.items || []));
      total = result.total;
      if (!result.items.length) break;
      page++;
    } while (items.length < total);
    return items;
  },
  async read() {
    if (this.data.role === 'PARENT') {
      await this.readParent();
      return;
    }
    this.quantities = {};
    this.allDishes = [];
    this.setData({ menu: null, dishes: [], ready: false, total: '0.00', count: 0 });
    const { sourceType, menuDate, mealType, childId } = this.data;
    const menu = await api.get('/menu/daily', { sourceType, menuDate, mealType, childId });
    this.allDishes = menu.dishes || [];
    this.setData({ menu, ready: true });
    if (this.previousConfirmId && sourceType === 'FAMILY' && this.data.role === 'CHILD') {
      const previous = await api.get('/menu/confirm/' + this.previousConfirmId);
      if (!['REJECTED', 'CANCELLED'].includes(previous.status)) throw new Error('仅拒绝或撤回的确认单可重新提报');
      if (previous.menuId !== menu.menuId) throw new Error('原餐单不属于当前日期或餐次，请重新选择');
      const items = previous.suggestedItems.length ? previous.suggestedItems : previous.items;
      items.forEach(item => {
        const dish = this.allDishes.find(d => String(d.dishId) === String(item.dishId)
          && (!item.sourceType || item.sourceType === d.sourceType) && selectable(d));
        if (dish) this.quantities[dishKey(dish)] = item.quantity;
      });
    }
    this.render();
  },
  async readParent() {
    this.selectedRefs = [];
    this.allDishes = [];
    this.setData({ menu: null, dishes: [], missingCount: 0, missingDishIds: [], invalidSelectedCount: 0,
      ready: false, count: 0 });
    let menu = null;
    try {
      menu = await api.get('/parent/menu-daily', {
        menuDate: this.data.menuDate,
        mealType: this.data.mealType
      });
    } catch (error) {
      // Compatible with older services that represented an unpublished menu as 404.
      if (error.status !== 404 && error.code !== 'E-404') throw error;
    }
    const menuDishes = menu ? menu.dishes || [] : [];
    const catalogKeys = new Set(this.catalogDishes.map(item => dishKey(item)));
    const unavailable = menuDishes.filter(item => !catalogKeys.has(dishKey(item)));
    this.allDishes = [...this.catalogDishes, ...unavailable.map(item => ({ ...item, unavailable: true }))];
    this.selectedRefs = menuDishes.map(item => dishRef(item));
    this.setData({ menu, missingCount: menu ? (menu.missingDishIds || []).length : 0,
      missingDishIds: menu ? menu.missingDishIds || [] : [], ready: true });
    this.render();
  },
  render() {
    if (this.data.role === 'PARENT') {
      const selectedKeys = new Set(this.selectedRefs.map(item => dishKey(item)));
      const catalogKeys = new Set(this.catalogDishes.map(item => dishKey(item)));
      const all = this.allDishes.map(dish => ({
        ...dish,
        key: dishKey(dish),
        selected: selectedKeys.has(dishKey(dish)),
        available: dish.status === 'ON_SALE',
        sourceLabel: dish.sourceType === 'FAMILY' ? '家庭' : '预置',
        safetyLabel: dish.status !== 'ON_SALE' ? '已下架，发布前需移除'
          : dish.allergenStatus !== 'DECLARED' ? '过敏信息待确认' : '过敏信息已声明',
        spiceLabel: SPICE[dish.spiceLevel]
      })).sort((left, right) => Number(right.selected) - Number(left.selected));
      // 家长未手动切过 Tab 时，默认落在有菜品的来源；家庭还没录入菜品就直接展示预置菜品。
      const sourceTab = this.tabPinned ? this.data.sourceTab
        : (this.catalogDishes.some(item => item.sourceType === 'FAMILY') ? 'FAMILY' : 'PRESET');
      const visible = all.filter(dish => dish.sourceType === sourceTab);
      const invalidSelectedCount = this.selectedRefs.filter(ref => !catalogKeys.has(dishKey(ref))).length
        + (Number(this.data.missingCount) || 0);
      this.setData({
        sourceTab,
        dishes: filterDishes(visible, { mildOnly: this.data.mildOnly, keyword: this.data.keyword }),
        count: this.selectedRefs.length,
        invalidSelectedCount
      });
      return;
    }
    const all = this.allDishes.map(dish => ({
      ...dish, key: dishKey(dish), quantity: this.quantities[dishKey(dish)] || 0, selectable: selectable(dish),
      safetyLabel: dish.allergyConflict ? '含过敏原，不可选择' : dish.allergenStatus !== 'DECLARED'
        ? '过敏信息待确认' : dish.status !== 'ON_SALE' ? '已下架' : '过敏信息已声明',
      spiceLabel: SPICE[dish.spiceLevel]
    }));
    const selected = all.filter(d => d.quantity > 0);
    const total = selected.reduce((sum, d) => sum + cents(d.virtualPrice) * d.quantity, 0);
    this.setData({ dishes: filterDishes(all, this.data), total: money(total), count: selected.length });
  },
  source(e) {
    if (this.data.busy || this.data.role === 'PARENT') return;
    this.setData({ sourceType: e.currentTarget.dataset.source });
    ui.run(this, () => this.read());
  },
  sourceTab(e) {
    if (this.data.busy || this.data.role !== 'PARENT') return;
    this.tabPinned = true;
    this.setData({ sourceTab: e.currentTarget.dataset.tab });
    this.render();
  },
  date(e) { this.setData({ menuDate: e.detail.value }); ui.run(this, () => this.read()); },
  meal(e) { const index = Number(e.detail.value); this.setData({ mealIndex: index, mealType: MEALS[index] }); ui.run(this, () => this.read()); },
  child(e) { const index = Number(e.detail.value); this.setData({ childIndex: index, childId: this.data.children[index].childId }); ui.run(this, () => this.read()); },
  filters(e) { this.setData({ mildOnly: e.detail.value.includes('mild'), favoritesOnly: e.detail.value.includes('favorite') }); this.render(); },
  search(e) { this.setData({ keyword: e.detail.value }); this.render(); },
  toggleDish(e) {
    if (this.data.role !== 'PARENT' || this.data.busy) return;
    const key = e.currentTarget.dataset.key;
    const selected = this.selectedRefs.some(ref => dishKey(ref) === key);
    if (selected) {
      this.selectedRefs = this.selectedRefs.filter(ref => dishKey(ref) !== key);
    } else {
      const dish = this.allDishes.find(item => dishKey(item) === key);
      if (!dish || dish.status !== 'ON_SALE') return;
      if (this.selectedRefs.length >= 50) {
        this.setData({ error: '每个餐次最多选择50种餐食' });
        return;
      }
      this.selectedRefs = [...this.selectedRefs, dishRef(dish)];
    }
    this.setData({ error: '' });
    this.render();
  },
  clearUnavailable() {
    if (this.data.role !== 'PARENT' || this.data.busy) return;
    const catalogKeys = new Set(this.catalogDishes.map(item => dishKey(item)));
    this.selectedRefs = this.selectedRefs.filter(ref => catalogKeys.has(dishKey(ref)));
    this.setData({ missingCount: 0, missingDishIds: [], error: '' });
    this.render();
  },
  saveMenu() {
    return ui.run(this, () => this.data.role === 'PARENT' ? this.publish('PUBLISHED') : undefined);
  },
  saveDraft() {
    return ui.run(this, () => this.data.role === 'PARENT' ? this.publish('DRAFT') : undefined);
  },
  async publish(status) {
    if (!this.selectedRefs.length) throw new Error('请至少选择一种餐食');
    if (this.selectedRefs.length > 50) throw new Error('每个餐次最多选择50种餐食');
    if (this.data.invalidSelectedCount) throw new Error('请先移除已下架或已删除的餐食');
    await api.post('/parent/menu-daily', {
      menuDate: this.data.menuDate,
      mealType: this.data.mealType,
      dishIds: this.selectedRefs.map(ref => ({ type: ref.type, id: ref.id })),
      status
    });
    await this.readParent();
    wx.showToast({ title: status === 'PUBLISHED' ? '家庭餐单已发布' : '已存草稿', icon: 'success' });
  },
  quantity(e) {
    const key = e.currentTarget.dataset.key;
    const delta = Number(e.currentTarget.dataset.delta);
    const dish = this.allDishes.find(d => dishKey(d) === key);
    if (!dish || !selectable(dish) || this.data.role !== 'CHILD' || !this.data.menu.canSubmit || this.data.busy) return;
    const quantity = (this.quantities[key] || 0) + delta;
    if (quantity < 0 || quantity > 9) return;
    if (delta > 0 && !this.quantities[key] && this.data.count >= 20) return this.setData({ error: '每次最多选择20种餐食' });
    this.quantities[key] = quantity;
    this.render();
  },
  favorite(e) {
    return ui.run(this, async () => {
      const key = e.currentTarget.dataset.key;
      const dish = this.allDishes.find(d => dishKey(d) === key);
      if (!dish || this.data.role !== 'CHILD' || dish.sourceType !== 'PRESET') return;
      const result = await api.post('/menu/mark-favorite', { dishId: dish.dishId, favorite: !dish.isFavorite });
      this.allDishes = this.allDishes.map(d => d.sourceType === 'PRESET'
        ? { ...d, isFavorite: result.favoriteDishIds.includes(d.dishId) } : d);
      this.render();
    });
  },
  checkout() {
    if (this.data.busy || !this.data.menu || !this.data.count || this.data.role !== 'CHILD'
        || !this.data.menu.canSubmit || this.data.sourceType !== 'FAMILY' || this.data.menuDate !== shanghaiDate()
        || this.data.menu.menuDate !== this.data.menuDate || this.data.menu.mealType !== this.data.mealType) {
      return this.setData({ error: '仅可提报当日家庭餐单，请重新确认日期和菜品' });
    }
    const items = this.allDishes.filter(d => this.quantities[dishKey(d)] > 0).map(d => ({
      key: dishKey(d), dishRef: dishRef(d), quantity: this.quantities[dishKey(d)],
      dishName: d.name, unitPrice: d.virtualPrice
    }));
    context.setCart({ menuId: this.data.menu.menuId, childId: this.data.childId, items,
      previousConfirmId: this.previousConfirmId, total: this.data.total });
    wx.navigateTo({ url: '/pages/confirmation/index?compose=1' });
  },
  goDishManage() { wx.navigateTo({ url: '/pages/dish-manage/index' }); },
  retry() { ui.run(this, () => this.data.role === 'PARENT' ? this.loadParent() : this.read()); },
  imageError(e) {
    const key = e.currentTarget.dataset.key;
    this.allDishes = this.allDishes.map(d => dishKey(d) === key ? { ...d, imageUrl: null } : d);
    this.catalogDishes = this.catalogDishes.map(d => dishKey(d) === key ? { ...d, imageUrl: null } : d);
    this.render();
  },
  onHide() {
    this.quantities = {};
    this.allDishes = [];
    this.catalogDishes = [];
    this.selectedRefs = [];
    this.tabPinned = false;
    this.setData({ dishes: [], menu: null, missingCount: 0, missingDishIds: [], invalidSelectedCount: 0,
      total: '0.00', count: 0, ready: false });
  }
});
