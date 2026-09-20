const api = require('../../services/api');
const ui = require('../../utils/page');
const context = require('../../services/context');
const { loadChildren } = require('../../services/children');
const { cents, money, shanghaiDate, selectable, filterDishes } = require('../../utils/domain');
ui.page({
  data: { role: '', busy: false, error: '', children: [], childIndex: 0, childId: '', sourceType: 'FAMILY',
    menuDate: shanghaiDate(), today: shanghaiDate(), mealType: 'LUNCH', meals: ['早餐', '午餐', '晚餐'], mealIndex: 1,
    menu: null, dishes: [], mildOnly: false, favoritesOnly: false, keyword: '', total: '0.00', count: 0,
    missingDishIds: [], invalidSelectedCount: 0, ready: false },
  onLoad(query) { this.previousConfirmId = query.previousConfirmId || null; },
  onShow() {
    if (!ui.guard(this)) return;
    this.quantities = {};
    this.allDishes = [];
    this.catalogDishes = [];
    this.selectedDishIds = [];
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
          mealIndex: ['BREAKFAST', 'LUNCH', 'DINNER'].indexOf(previous.mealType) });
      }
      await this.read();
    });
  },
  async loadParent() {
    const items = [];
    let page = 1;
    let total = 0;
    do {
      const result = await api.get('/parent/dish', { page, pageSize: 100 });
      items.push(...result.items);
      total = result.total;
      if (!result.items.length) break;
      page++;
    } while (items.length < total);
    this.catalogDishes = items;
    await this.readParent();
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
        if (this.allDishes.some(d => d.dishId === item.dishId && selectable(d))) this.quantities[item.dishId] = item.quantity;
      });
    }
    this.render();
  },
  async readParent() {
    this.selectedDishIds = [];
    this.allDishes = [];
    this.setData({ menu: null, dishes: [], missingDishIds: [], invalidSelectedCount: 0,
      ready: false, total: '0.00', count: 0 });
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
    const catalogIds = new Set(this.catalogDishes.map(item => item.dishId));
    const unavailable = menuDishes.filter(item => !catalogIds.has(item.dishId));
    this.allDishes = [...this.catalogDishes, ...unavailable];
    this.selectedDishIds = menuDishes.map(item => item.dishId)
      .concat(menu ? menu.missingDishIds || [] : []);
    this.setData({ menu, missingDishIds: menu ? menu.missingDishIds || [] : [], ready: true });
    this.render();
  },
  render() {
    if (this.data.role === 'PARENT') {
      const selected = new Set(this.selectedDishIds);
      const all = this.allDishes.map(dish => ({
        ...dish,
        selected: selected.has(dish.dishId),
        available: dish.status === 'ON_SALE',
        safetyLabel: dish.status !== 'ON_SALE' ? '已下架，发布前需移除'
          : dish.allergenStatus !== 'DECLARED' ? '过敏信息待确认' : '过敏信息已声明',
        spiceLabel: ['无辣', '微辣', '中辣', '重辣'][dish.spiceLevel]
      })).sort((left, right) => Number(right.selected) - Number(left.selected));
      const invalidSelectedCount = this.selectedDishIds.filter(id => {
        const dish = this.allDishes.find(item => item.dishId === id);
        return !dish || dish.status !== 'ON_SALE';
      }).length;
      this.setData({
        dishes: filterDishes(all, { mildOnly: this.data.mildOnly, keyword: this.data.keyword }),
        count: this.selectedDishIds.length,
        invalidSelectedCount
      });
      return;
    }
    const all = this.allDishes.map(dish => ({
      ...dish, quantity: this.quantities[dish.dishId] || 0, selectable: selectable(dish),
      safetyLabel: dish.allergyConflict ? '含过敏原，不可选择' : dish.allergenStatus !== 'DECLARED'
        ? '过敏信息待确认' : dish.status !== 'ON_SALE' ? '已下架' : '过敏信息已声明',
      spiceLabel: ['无辣', '微辣', '中辣', '重辣'][dish.spiceLevel]
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
  date(e) { this.setData({ menuDate: e.detail.value }); ui.run(this, () => this.read()); },
  meal(e) { const index = Number(e.detail.value); this.setData({ mealIndex: index, mealType: ['BREAKFAST', 'LUNCH', 'DINNER'][index] }); ui.run(this, () => this.read()); },
  child(e) { const index = Number(e.detail.value); this.setData({ childIndex: index, childId: this.data.children[index].childId }); ui.run(this, () => this.read()); },
  filters(e) { this.setData({ mildOnly: e.detail.value.includes('mild'), favoritesOnly: e.detail.value.includes('favorite') }); this.render(); },
  search(e) { this.setData({ keyword: e.detail.value }); this.render(); },
  toggleDish(e) {
    if (this.data.role !== 'PARENT' || this.data.busy) return;
    const dishId = e.currentTarget.dataset.id;
    const selected = this.selectedDishIds.includes(dishId);
    if (selected) {
      this.selectedDishIds = this.selectedDishIds.filter(id => id !== dishId);
    } else {
      const dish = this.allDishes.find(item => item.dishId === dishId);
      if (!dish || dish.status !== 'ON_SALE') return;
      if (this.selectedDishIds.length >= 50) {
        this.setData({ error: '每个餐次最多选择50种餐食' });
        return;
      }
      this.selectedDishIds = [...this.selectedDishIds, dishId];
    }
    this.setData({ error: '' });
    this.render();
  },
  clearUnavailable() {
    if (this.data.role !== 'PARENT' || this.data.busy) return;
    const available = new Set(this.catalogDishes.map(item => item.dishId));
    this.selectedDishIds = this.selectedDishIds.filter(id => available.has(id));
    this.setData({ missingDishIds: [], error: '' });
    this.render();
  },
  saveMenu() {
    return ui.run(this, async () => {
      if (this.data.role !== 'PARENT') return;
      if (!this.selectedDishIds.length) throw new Error('请至少选择一种餐食');
      if (this.selectedDishIds.length > 50) throw new Error('每个餐次最多选择50种餐食');
      if (this.data.invalidSelectedCount) throw new Error('请先移除已下架或已删除的餐食');
      await api.post('/parent/menu-daily', {
        menuDate: this.data.menuDate,
        mealType: this.data.mealType,
        dishIds: [...this.selectedDishIds],
        status: 'PUBLISHED'
      });
      await this.readParent();
      wx.showToast({ title: '家庭餐单已发布', icon: 'success' });
    });
  },
  quantity(e) {
    const id = e.currentTarget.dataset.id, delta = Number(e.currentTarget.dataset.delta);
    const dish = this.allDishes.find(d => d.dishId === id);
    if (!dish || !selectable(dish) || this.data.role !== 'CHILD' || !this.data.menu.canSubmit || this.data.busy) return;
    const quantity = (this.quantities[id] || 0) + delta;
    if (quantity < 0 || quantity > 9) return;
    if (delta > 0 && !this.quantities[id] && this.data.count >= 20) return this.setData({ error: '每次最多选择20种餐食' });
    this.quantities[id] = quantity;
    this.render();
  },
  favorite(e) {
    return ui.run(this, async () => {
      const dish = this.allDishes.find(d => d.dishId === e.currentTarget.dataset.id);
      if (!dish || this.data.role !== 'CHILD') return;
      const result = await api.post('/menu/mark-favorite', { dishId: dish.dishId, favorite: !dish.isFavorite });
      this.allDishes = this.allDishes.map(d => ({ ...d, isFavorite: result.favoriteDishIds.includes(d.dishId) }));
      this.render();
    });
  },
  checkout() {
    if (this.data.busy || !this.data.menu || !this.data.count || this.data.role !== 'CHILD'
        || !this.data.menu.canSubmit || this.data.sourceType !== 'FAMILY' || this.data.menuDate !== shanghaiDate()
        || this.data.menu.menuDate !== this.data.menuDate || this.data.menu.mealType !== this.data.mealType) {
      return this.setData({ error: '仅可提报当日家庭餐单，请重新确认日期和菜品' });
    }
    const items = this.allDishes.filter(d => this.quantities[d.dishId] > 0).map(d => ({
      dishId: d.dishId, quantity: this.quantities[d.dishId], dishName: d.name, unitPrice: d.virtualPrice
    }));
    context.setCart({ menuId: this.data.menu.menuId, childId: this.data.childId, items,
      previousConfirmId: this.previousConfirmId, total: this.data.total });
    wx.navigateTo({ url: '/pages/confirmation/index?compose=1' });
  },
  retry() { ui.run(this, () => this.data.role === 'PARENT' ? this.loadParent() : this.read()); },
  imageError(e) {
    this.allDishes = this.allDishes.map(d => d.dishId === e.currentTarget.dataset.id ? { ...d, imageUrl: null } : d);
    this.catalogDishes = this.catalogDishes.map(d => d.dishId === e.currentTarget.dataset.id ? { ...d, imageUrl: null } : d);
    this.render();
  },
  onHide() {
    this.quantities = {};
    this.allDishes = [];
    this.catalogDishes = [];
    this.selectedDishIds = [];
    this.setData({ dishes: [], menu: null, missingDishIds: [], invalidSelectedCount: 0,
      total: '0.00', count: 0, ready: false });
  }
});
