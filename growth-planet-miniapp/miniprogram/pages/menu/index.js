const api = require('../../services/api');
const ui = require('../../utils/page');
const context = require('../../services/context');
const { loadChildren } = require('../../services/children');
const { cents, money, shanghaiDate, selectable, safetyLabel, filterDishes, dishRef, dishKey } = require('../../utils/domain');

const SPICE = ['无辣', '微辣', '中辣', '重辣'];
const MEALS = ['BREAKFAST', 'LUNCH', 'DINNER'];
const WEEKDAYS = ['周日', '周一', '周二', '周三', '周四', '周五', '周六'];
const WEEK_DAYS = 7;

function shiftDate(date, delta) {
  const [year, month, day] = date.split('-').map(Number);
  return new Date(Date.UTC(year, month - 1, day + delta)).toISOString().slice(0, 10);
}
function weekdayLabel(date, today) {
  if (date === today) return '今天';
  const [year, month, day] = date.split('-').map(Number);
  return WEEKDAYS[new Date(Date.UTC(year, month - 1, day)).getUTCDay()];
}

ui.page({
  data: { role: '', busy: false, error: '', children: [], childIndex: 0, childId: '', sourceType: 'FAMILY',
    menuDate: shanghaiDate(), today: shanghaiDate(), mealType: 'LUNCH', meals: ['早餐', '午餐', '晚餐'], mealIndex: 1,
    menu: null, dishes: [], mildOnly: false, favoritesOnly: false, keyword: '', total: '0.00', count: 0,
    sourceTab: 'FAMILY', missingCount: 0, missingDishIds: [], invalidSelectedCount: 0, ready: false,
    categories: [], categoryId: '', frequent: [], recommend: [], week: [] },
  onLoad(query) { this.previousConfirmId = query.previousConfirmId || null; },
  onShow() {
    if (!ui.guard(this)) return;
    this.quantities = {};
    this.allDishes = [];
    this.catalogDishes = [];
    this.selectedRefs = [];
    this.tabPinned = false;
    this.frequentDishes = [];
    this.recommendDishes = [];
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
    await this.loadFrequent();
    await this.loadRecommend();
    await this.loadWeek();
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
  // 派生「常吃」快捷区：取近 30 天每日想吃归纳出的高频菜，降低孩子的选择成本。
  async loadFrequent() {
    if (this.data.role !== 'CHILD') { this.frequentDishes = []; return; }
    try {
      const result = await api.get('/child/frequent-dish', { childId: this.data.childId, limit: 6 });
      this.frequentDishes = (result.dishes || []).map(item => ({
        ...item, key: dishKey(item), countText: '近30天 ' + item.count + ' 次'
      }));
    } catch (error) {
      this.frequentDishes = []; // 快捷区为增强体验，失败时静默降级不影响主流程。
    }
  },
  // 「今天吃什么」推荐：后端按 过敏硬过滤 → 忌口剔除 → 常吃加权 → 本周未吃加分 打分，失败静默降级。
  async loadRecommend() {
    if (this.data.role !== 'CHILD' || !this.data.menu) { this.recommendDishes = []; return; }
    try {
      const result = await api.get('/child/recommend', {
        menuId: this.data.menu.menuId, childId: this.data.childId, limit: 3
      });
      this.recommendDishes = (result.dishes || []).map(item => ({
        ...item, key: dishKey(item), reasonText: (item.reasons || []).join(' · ')
      }));
    } catch (error) {
      this.recommendDishes = []; // 推荐卡同为增强体验，失败时不影响菜单主流程。
    }
  },
  // 周条：今天起 7 天，标注哪几天已发菜单（家长用于整周发布，孩子用于提前挑选）。
  async loadWeek() {
    const today = shanghaiDate();
    const from = today;
    const to = shiftDate(today, WEEK_DAYS - 1);
    try {
      const result = this.data.role === 'PARENT'
        ? await api.get('/parent/menu-week', { from, to })
        : await api.get('/child/menu-week', { from, to, childId: this.data.childId });
      const week = (result.days || []).map(day => {
        const meals = day.meals || [];
        const published = meals.filter(meal => meal.menuId);
        return {
          date: day.menuDate,
          label: weekdayLabel(day.menuDate, today),
          dayText: day.menuDate.slice(5).replace('-', '/'),
          publishedCount: published.length,
          hasMenu: published.length > 0,
          wantEatCount: meals.reduce((sum, meal) => sum + (meal.wantEatCount || 0), 0),
          isToday: day.menuDate === today,
          active: day.menuDate === this.data.menuDate
        };
      });
      this.setData({ week });
    } catch (error) {
      this.setData({ week: [] }); // 周条失败不影响单日菜单主流程
    }
  },
  decorateRecommend(dishes) {
    return (this.recommendDishes || []).map(item => {
      const match = dishes.find(dish => dishKey(dish) === item.key);
      return { ...item, available: Boolean(match && match.selectable), isFavorite: Boolean(match && match.isFavorite) };
    });
  },
  buildCategories(dishes) {
    const seen = new Map();
    dishes.forEach(dish => {
      const key = dish.categoryId == null ? '' : String(dish.categoryId);
      if (!seen.has(key)) seen.set(key, { id: key, name: dish.categoryName || '未分类', count: 0 });
      seen.get(key).count += 1;
    });
    return [{ id: '', name: '全部', count: dishes.length }, ...Array.from(seen.values())];
  },
  decorateFrequent(dishes) {
    return (this.frequentDishes || []).map(item => {
      const match = dishes.find(dish => dishKey(dish) === item.key);
      return { ...item, available: Boolean(match && match.selectable), isFavorite: Boolean(match && match.isFavorite) };
    });
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
    await this.loadWeek();
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
      const categories = this.buildCategories(visible);
      const categoryId = categories.some(item => item.id === this.data.categoryId) ? this.data.categoryId : '';
      const inCategory = categoryId
        ? visible.filter(dish => (dish.categoryId == null ? '' : String(dish.categoryId)) === categoryId)
        : visible;
      this.setData({
        sourceTab,
        categories,
        categoryId,
        frequent: [],
        recommend: [],
        dishes: filterDishes(inCategory, { mildOnly: this.data.mildOnly, keyword: this.data.keyword }),
        count: this.selectedRefs.length,
        invalidSelectedCount
      });
      return;
    }
    const all = this.allDishes.map(dish => ({
      ...dish, key: dishKey(dish), quantity: this.quantities[dishKey(dish)] || 0, selectable: selectable(dish),
      categoryName: dish.categoryName || '',
      safetyLabel: safetyLabel(dish),
      spiceLabel: SPICE[dish.spiceLevel]
    }));
    const categories = this.buildCategories(all);
    const categoryId = categories.some(item => item.id === this.data.categoryId) ? this.data.categoryId : '';
    const inCategory = categoryId
      ? all.filter(dish => (dish.categoryId == null ? '' : String(dish.categoryId)) === categoryId)
      : all;
    const selected = all.filter(d => d.quantity > 0);
    const total = selected.reduce((sum, d) => sum + cents(d.virtualPrice) * d.quantity, 0);
    this.setData({ dishes: filterDishes(inCategory, this.data), categories, categoryId,
      frequent: this.decorateFrequent(all), recommend: this.decorateRecommend(all),
      total: money(total), count: selected.length });
  },
  source(e) {
    if (this.data.busy || this.data.role === 'PARENT') return;
    this.setData({ sourceType: e.currentTarget.dataset.source, categoryId: '' });
    ui.run(this, () => this.read());
  },
  sourceTab(e) {
    if (this.data.busy || this.data.role !== 'PARENT') return;
    this.tabPinned = true;
    this.setData({ sourceTab: e.currentTarget.dataset.tab, categoryId: '' });
    this.render();
  },
  date(e) { this.setData({ menuDate: e.detail.value }); ui.run(this, () => this.read()); },
  // 周条点选：切到该天（可能是下周），孩子可提前挑选想吃，家长可维护该天菜单。
  weekDay(e) {
    const date = e.currentTarget.dataset.date;
    if (this.data.busy || !date || date === this.data.menuDate) return;
    this.setData({ menuDate: date });
    return ui.run(this, () => this.read());
  },
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
      if (!dish || this.data.role !== 'CHILD' || !this.data.menu) return;
      await api.post('/menu/mark-favorite', {
        dishId: dish.dishId,
        dishType: dish.sourceType,
        favorite: !dish.isFavorite,
        menuId: this.data.menu.menuId,
        menuDate: this.data.menuDate,
        mealType: this.data.mealType
      });
      await this.read();
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
  goWeekPlan() {
    if (this.data.role !== 'PARENT' || this.data.busy) return;
    wx.navigateTo({ url: '/pages/menu-week/index' });
  },
  category(e) {
    this.setData({ categoryId: e.currentTarget.dataset.id || '' });
    this.render();
  },
  quickFavorite(e) {
    return ui.run(this, async () => {
      const key = e.currentTarget.dataset.key;
      // 快捷区与推荐卡共用本处理器：两处的菜品都来自后端派生，可能不在今日餐单里。
      const item = [...(this.frequentDishes || []), ...(this.recommendDishes || [])]
        .find(row => row.key === key);
      const dish = this.allDishes.find(row => dishKey(row) === key);
      if (!item || !dish || !selectable(dish)) {
        wx.showToast({ title: '今日餐单暂无可标记的菜品', icon: 'none' });
        return;
      }
      await api.post('/menu/mark-favorite', {
        dishId: item.id,
        dishType: item.type,
        favorite: !dish.isFavorite,
        menuId: this.data.menu.menuId,
        menuDate: this.data.menuDate,
        mealType: this.data.mealType
      });
      await this.read();
    });
  },
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
    this.frequentDishes = [];
    this.recommendDishes = [];
    this.setData({ dishes: [], menu: null, missingCount: 0, missingDishIds: [], invalidSelectedCount: 0,
      total: '0.00', count: 0, ready: false, categories: [], categoryId: '', frequent: [], recommend: [],
      week: [] });
  }
});
