const api = require('../../services/api');
const ui = require('../../utils/page');
const context = require('../../services/context');
const { loadChildren, displayChildren } = require('../../services/children');
const { cents, money, shanghaiDate, selectable, safetyLabel, filterDishes, dishRef, dishKey } = require('../../utils/domain');

const SPICE = ['无辣', '微辣', '中辣', '重辣'];
const MEALS = ['BREAKFAST', 'LUNCH', 'DINNER'];
const WEEKDAYS = ['周日', '周一', '周二', '周三', '周四', '周五', '周六'];
const WEEK_DAYS = 7;
const CHILD_MEAL_SOURCES = ['FAMILY', 'SCHOOL'];

function shiftDate(date, delta) {
  const [year, month, day] = date.split('-').map(Number);
  return new Date(Date.UTC(year, month - 1, day + delta)).toISOString().slice(0, 10);
}
// 心愿菜单状态文案（NONE = 当天未创建，功能可选，不产生占位记录）。
const WISH_STATUS_LABELS = { NONE: '未创建', SUBMITTED: '已提交', WITHDRAWN: '已撤回' };
// 心愿菜单可编辑窗口：今天起 7 天，与后端 WINDOW_DAYS 保持一致。
const WISH_WINDOW_DAYS = 6;
function weekdayLabel(date, today) {
  if (date === today) return '今天';
  const [year, month, day] = date.split('-').map(Number);
  return WEEKDAYS[new Date(Date.UTC(year, month - 1, day)).getUTCDay()];
}

ui.page({
  data: { role: '', busy: false, error: '', children: [], childLabels: [], childIndex: 0, childId: '', sourceType: 'FAMILY',
    menuDate: shanghaiDate(), today: shanghaiDate(), mealType: 'LUNCH', meals: ['早餐', '午餐', '晚餐'], mealIndex: 1,
    menu: null, dishes: [], mildOnly: false, favoritesOnly: false, keyword: '', total: '0.00', count: 0,
    sourceTab: 'FAMILY', schoolBoundaryText: '', missingCount: 0, missingDishIds: [], invalidSelectedCount: 0, ready: false,
    categories: [], categoryId: '', frequent: [], recommend: [], week: [],
    // 心愿菜单（P3）：候选池跟随菜单日期；儿童勾选提交，家长查看并配置上限。
    wish: null, wishSelected: [], wishDishes: [], wishTab: 'FAMILY', wishKeyword: '',
    wishPage: 1, wishPageSize: 10, wishTotal: 0, wishSettings: null, wishMaxDraft: 5,
    wishEnabledDraft: true, wishDishRange: [1, 2, 3, 4, 5, 6, 7, 8, 9, 10] },
  onLoad(query) {
    const params = query || {};
    this.previousConfirmId = params.previousConfirmId || null;
    this.initialChildId = params.childId || null;
    this.initialSourceType = CHILD_MEAL_SOURCES.includes(params.sourceType) ? params.sourceType : null;
  },
  onShow() {
    if (!ui.guard(this)) return;
    const sourceType = this.data.role === 'CHILD' && !this.previousConfirmId
      ? (this.initialSourceType || 'FAMILY')
      : 'FAMILY';
    this.initialSourceType = null;
    this.setData({ sourceType });
    this.renderSourceNotice();
    this.quantities = {};
    this.allDishes = [];
    this.catalogDishes = [];
    this.selectedRefs = [];
    this.tabPinned = false;
    this.frequentDishes = [];
    this.recommendDishes = [];
    this.wishRaw = null;
    this.wishSelectionTouched = false;
    return ui.run(this, async () => {
      if (this.data.role === 'PARENT') {
        await this.loadParent();
        return;
      }
      const children = displayChildren(await loadChildren());
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
    // 孩子列表：菜单维护本身是家庭级、与具体孩子无关，但「孩子的心愿菜单」卡需要 childId，
    // 多孩家庭还要能切换。取不到（尚未绑定儿童）不阻断菜单维护主流程。
    try {
      const children = displayChildren(await loadChildren());
      // 通知中心点入时带 childId，落到该孩子的心愿菜单；不在已绑定列表里（如同意已撤回）则退回第一个。
      const index = Math.max(0, children.findIndex(item => item.childId === this.initialChildId));
      this.setData({ children, childLabels: children.map(item => item.displayName), childIndex: index,
        childId: children[index].childId });
    } catch (error) {
      // 与 loadFrequent/loadRecommend/loadWish 同口径：增强区块的失败一律静默降级。
      // 会话/页面变化（cancelled）无需在这里处理——同一次 ui.run 里的主流程会原样抛出并接管。
      this.setData({ children: [], childLabels: [], childIndex: 0, childId: '' });
    }
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
    // 切换日期后先清除旧心愿入口，主餐单加载失败时也不能继续操作旧目录。
    this.wishRaw = null;
    this.setData({ menu: null, dishes: [], ready: false, total: '0.00', count: 0,
      wish: null, wishDishes: [], wishTotal: 0 });
    this.renderSourceNotice();
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
    await this.loadWish();
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
      const available = Boolean(match && match.selectable);
      const isFavorite = Boolean(match && match.isFavorite);
      return { ...item, available, isFavorite,
        actionLabel: available ? (isFavorite ? '取消收藏' : '收藏这道') : '今日餐单暂无',
        actionHint: available ? '只记录你的偏好，不会提交确认单' : '当前日期没有可收藏的菜品' };
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
      const available = Boolean(match && match.selectable);
      const isFavorite = Boolean(match && match.isFavorite);
      return { ...item, available, isFavorite,
        actionLabel: available ? (isFavorite ? '取消收藏' : '收藏这道') : '今日餐单暂无',
        actionHint: available ? '只记录你的偏好，不会提交确认单' : '当前日期没有可收藏的菜品' };
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
    // 家长也要看孩子当天提交的心愿菜单（通知落地同页），与孩子侧共用同一份加载逻辑。
    await this.loadWish();
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
      spiceLabel: SPICE[dish.spiceLevel],
      favoriteAriaLabel: dish.isFavorite ? '取消收藏' : '收藏这道菜'
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
  renderSourceNotice() {
    this.setData({
      schoolBoundaryText: this.data.role === 'CHILD' && this.data.sourceType === 'SCHOOL'
        ? '学校餐单只能查看和标记今天想吃，不会生成家长确认单'
        : ''
    });
  },
  source(e) {
    if (this.data.busy || this.data.role === 'PARENT') return;
    this.setData({ sourceType: e.currentTarget.dataset.source, categoryId: '' });
    this.renderSourceNotice();
    ui.run(this, () => this.read());
  },
  sourceTab(e) {
    if (this.data.busy || this.data.role !== 'PARENT') return;
    this.tabPinned = true;
    this.setData({ sourceTab: e.currentTarget.dataset.tab, categoryId: '' });
    this.render();
  },
  date(e) { this.setData({ menuDate: e.detail.value }); this.wishSelectionTouched = false; ui.run(this, () => this.read()); },
  // 周条点选：切到该天（可能是下周），孩子可提前挑选想吃，家长可维护该天菜单。
  weekDay(e) {
    const date = e.currentTarget.dataset.date;
    if (this.data.busy || !date || date === this.data.menuDate) return;
    this.setData({ menuDate: date });
    this.wishSelectionTouched = false;
    return ui.run(this, () => this.read());
  },
  meal(e) { const index = Number(e.detail.value); this.setData({ mealIndex: index, mealType: MEALS[index] }); ui.run(this, () => this.read()); },
  // 切换孩子：菜单维护是家庭级、与具体孩子无关，但心愿菜单卡与周条的"想吃"计数按孩子变化。
  child(e) {
    const index = Number(e.detail.value);
    this.setData({ childIndex: index, childId: this.data.children[index].childId });
    this.wishSelectionTouched = false;
    ui.run(this, () => this.read());
  },
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
    return ui.openPage(this, '/pages/confirmation/index?compose=1');
  },
  goDishManage() { return ui.openPage(this, '/pages/dish-manage/index'); },
  goWeekPlan() {
    if (this.data.role !== 'PARENT' || this.data.busy) return;
    return ui.openPage(this, '/pages/menu-week/index');
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
  // ---------- 心愿菜单（P3）----------
  // 心愿菜单为增强区块：无同意、超窗口、无绑定儿童等场景一律静默降级，不阻断点餐主流程。
  async loadWish() {
    const { role, menuDate, childId } = this.data;
    if (!menuDate || (role === 'PARENT' && !childId)) {
      this.wishRaw = null;
      this.setData({ wish: null, wishDishes: [], wishTotal: 0 });
      return;
    }
    try {
      const wish = role === 'PARENT'
        ? await api.get('/parent/wish-menu', { childId, menuDate })
        : await api.get('/child/wish-menu', { menuDate });
      this.wishRaw = wish;
      this.renderWish();
      if (role === 'PARENT') {
        const setting = await api.get('/parent/wish-setting');
        this.setData({ wishSettings: setting, wishMaxDraft: setting.maxDishes, wishEnabledDraft: setting.enabled });
        return;
      }
      await this.loadWishCatalog();
    } catch (error) {
      if (error.cancelled || error.status === 401 || error.code === 'E-010') throw error;
      this.wishRaw = null;
      this.setData({ wish: null, wishDishes: [], wishTotal: 0 });
    }
  },
  renderWish() {
    const wish = this.wishRaw;
    if (!wish) return;
    const today = wish.today || shanghaiDate();
    const inWindow = wish.menuDate >= today && wish.menuDate <= shiftDate(today, WISH_WINDOW_DAYS);
    const items = (wish.items || []).map(item => {
      const flags = [];
      // 不可选时复用菜单页统一安全提示：区分"菜品未登记（找管理员）"与"档案过敏信息需更新（找家长）"。
      if (item.missing || !item.selectable) flags.push(safetyLabel(item));
      if (item.disliked) flags.push('孩子忌口');
      return Object.assign({}, item, {
        key: dishKey(item),
        displayName: item.name || '（菜品已下架）',
        flags: flags.join(' · ')
      });
    });
    const keys = items.map(item => item.key);
    let selected = (this.data.wishSelected || []).filter(key => keys.includes(key));
    if (!this.wishSelectionTouched) {
      // 默认勾选上次提交的菜；从未提交过则按上限预勾选候选池里仍可选的前几道，减少孩子的操作次数。
      const submitted = items.filter(item => item.submitted).map(item => item.key);
      const usable = items.filter(item => !item.missing && item.selectable).map(item => item.key);
      selected = submitted.length ? submitted : usable.slice(0, Math.max(0, wish.maxDishes || 0));
    }
    const selectedSet = new Set(selected);
    // 家长端只看"孩子确实提交了"的菜；候选池明细属于孩子端，撤回后仍回显上次提交集合。
    const visible = this.data.role === 'PARENT' ? items.filter(item => item.submitted) : items;
    const statusLabel = WISH_STATUS_LABELS[wish.status] || wish.status;
    const canEdit = Boolean(wish.enabled && wish.canEdit && inWindow && !wish.locked);
    const stateLabel = !wish.enabled
      ? '未开启'
      : wish.locked
        ? '已提交并锁定'
        : !inWindow
          ? '日期不可编辑'
          : wish.canEdit
            ? (wish.status === 'WITHDRAWN' ? '已撤回，可重新编辑' : '可编辑')
            : '暂不可编辑';
    const noticeText = !wish.enabled
      ? '家长尚未开启心愿菜单；你仍可以用“今天想吃”做轻量标记。'
      : wish.locked
        ? '已提交给家长，当前不可修改；如需调整请先撤回。'
        : !inWindow
          ? '该日期不在可编辑范围（今天起 7 天内），只能查看已有记录。'
          : wish.canEdit
            ? '先加入心愿候选，再提交给家长；提交后会进入家长可见清单。'
            : '当前暂不可编辑，请稍后重试。';
    this.setData({
      wishSelected: this.data.role === 'PARENT' ? [] : selected,
      wish: Object.assign({}, wish, {
        items: visible.map(item => Object.assign({}, item, { checked: selectedSet.has(item.key) })),
        today,
        inWindow,
        canEdit,
        stateLabel,
        showCatalog: canEdit,
        statusLabel,
        titleText: wish.menuDate === today ? '今天的心愿菜单' : wish.menuDate + '的心愿菜单',
        submitTimeText: String(wish.submitTime || '').replace('T', ' ').slice(0, 16),
        countText: selected.length + ' / ' + wish.maxDishes,
        noticeText
      }),
      wishDishes: canEdit ? this.data.wishDishes : [],
      wishTotal: canEdit ? this.data.wishTotal : 0
    });
  },
  wishEditableWindow() {
    const wish = this.data.wish;
    const today = wish && wish.today || shanghaiDate();
    return Boolean(this.data.role === 'CHILD' && wish && wish.showCatalog
      && wish.enabled && wish.canEdit && !wish.locked && wish.menuDate === this.data.menuDate
      && this.data.menuDate >= today && this.data.menuDate <= shiftDate(today, WISH_WINDOW_DAYS));
  },
  async loadWishCatalog() {
    if (!this.wishEditableWindow()) {
      this.setData({ wishDishes: [], wishTotal: 0 });
      return;
    }
    const { wishTab, wishKeyword, wishPage, wishPageSize, menuDate } = this.data;
    const result = await api.get('/child/wish-catalog', {
      menuDate, sourceType: wishTab, keyword: wishKeyword || undefined,
      page: wishPage, pageSize: wishPageSize
    });
    this.setData({
      wishTotal: result.total,
      wishDishes: (result.items || []).map(item => Object.assign({}, item, {
        key: dishKey(item),
        typeLabel: item.type === 'FAMILY' ? '家庭菜谱' : '预置菜谱',
        // 与菜单页共用同一套安全提示：区分"菜品未登记"（找管理员）与"档案过敏信息需更新"（找家长）。
        safetyLabel: safetyLabel(item),
        actionLabel: item.marked ? '移出心愿' : '加入心愿'
      }))
    });
  },
  wishToggle(e) {
    const wish = this.data.wish;
    if (this.data.busy || !wish || !wish.showCatalog || !wish.canEdit || !wish.inWindow) return;
    const key = e.currentTarget.dataset.key;
    const selected = (this.data.wishSelected || []).slice();
    const index = selected.indexOf(key);
    if (index >= 0) {
      // 取消勾选永远允许：菜品可能在下架/新增过敏原后仍需被移除。
      selected.splice(index, 1);
    } else {
      const item = (wish.items || []).find(row => row.key === key);
      if (!item || item.missing || !item.selectable) {
        this.setData({ error: '该菜品已下架或过敏信息待确认，不能加入心愿菜单' });
        return;
      }
      if (selected.length >= wish.maxDishes) {
        this.setData({ error: '最多勾选 ' + wish.maxDishes + ' 道菜，请先取消一道' });
        return;
      }
      selected.push(key);
    }
    this.wishSelectionTouched = true;
    this.setData({ wishSelected: selected, error: '' });
    this.renderWish();
  },
  wishSubmit() {
    return ui.run(this, async () => {
      const { wish, wishSelected } = this.data;
      if (!wish || !wish.canEdit) return;
      if (!wishSelected.length) throw new Error('请至少勾选一道菜');
      if (wishSelected.length > wish.maxDishes) throw new Error('最多勾选 ' + wish.maxDishes + ' 道菜');
      // 标记 → 提交之间菜品可能下架或补录过敏原，提交前再挡一次（服务端还有同口径硬校验）。
      const rows = wish.items || [];
      const usable = wishSelected.filter(key => {
        const item = rows.find(row => row.key === key);
        return item && !item.missing && item.selectable;
      });
      if (usable.length !== wishSelected.length) {
        throw new Error('有 ' + (wishSelected.length - usable.length) + ' 道菜已下架或过敏信息待确认，请取消勾选后再提交');
      }
      await api.post('/child/wish-menu/submit', {
        menuDate: wish.menuDate,
        refs: usable.map(key => {
          const index = key.indexOf(':');
          return { type: key.slice(0, index), id: key.slice(index + 1) };
        }),
        expectedVersion: wish.version
      });
      this.wishSelectionTouched = false;
      await this.loadWish();
      wx.showToast({ title: '已提交给爸妈', icon: 'success' });
    });
  },
  wishWithdraw() {
    return ui.run(this, async () => {
      const wish = this.data.wish;
      if (!wish || !wish.locked) return;
      if (!await ui.confirm('撤回后家长看到的清单会失效，可修改后重新提交。')) return;
      await api.post('/child/wish-menu/withdraw', { menuDate: wish.menuDate, expectedVersion: wish.version });
      this.wishSelectionTouched = false;
      await this.loadWish();
      wx.showToast({ title: '已撤回心愿菜单', icon: 'success' });
    });
  },
  wishMark(e) {
    const wish = this.data.wish;
    if (this.data.busy || !this.wishEditableWindow()) return;
    return ui.run(this, async () => {
      const { type, id, marked } = e.currentTarget.dataset;
      const adding = !(marked === true || marked === 'true');
      const key = type + ':' + id;
      const previous = (this.data.wishSelected || []).slice();
      await api.post('/child/wish-mark', { menuDate: this.data.menuDate, type, id, selected: adding });
      const selected = previous.filter(item => item !== key);
      if (adding && wish && selected.length < wish.maxDishes) selected.push(key);
      this.wishSelectionTouched = true;
      this.setData({ wishSelected: selected });
      await this.loadWish();
    });
  },
  wishCatalogTab(e) {
    if (this.data.busy || !this.wishEditableWindow()) return;
    this.setData({ wishTab: e.currentTarget.dataset.tab, wishPage: 1, wishKeyword: '' });
    return ui.run(this, () => this.loadWishCatalog());
  },
  wishSearch(e) {
    if (this.data.busy || !this.wishEditableWindow()) return;
    this.setData({ wishKeyword: e.detail.value, wishPage: 1 });
    return ui.run(this, () => this.loadWishCatalog());
  },
  wishPageDelta(e) {
    if (this.data.busy || !this.wishEditableWindow()) return;
    const delta = Number(e.currentTarget.dataset.delta);
    const page = this.data.wishPage + delta;
    if (page < 1 || page * this.data.wishPageSize - this.data.wishPageSize >= this.data.wishTotal) return;
    this.setData({ wishPage: page });
    return ui.run(this, () => this.loadWishCatalog());
  },
  wishMax(e) {
    this.setData({ wishMaxDraft: this.data.wishDishRange[Number(e.detail.value)] });
  },
  wishEnabled(e) {
    this.setData({ wishEnabledDraft: e.detail.value });
  },
  wishSettingSave() {
    return ui.run(this, async () => {
      const setting = this.data.wishSettings;
      if (!setting) return;
      await api.put('/parent/wish-setting', {
        maxDishes: Number(this.data.wishMaxDraft),
        enabled: this.data.wishEnabledDraft,
        expectedVersion: setting.version
      });
      await this.loadWish();
      wx.showToast({ title: '心愿菜单设置已保存', icon: 'success' });
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
    this.wishRaw = null;
    this.wishSelectionTouched = false;
    this.setData({ dishes: [], menu: null, missingCount: 0, missingDishIds: [], invalidSelectedCount: 0,
      total: '0.00', count: 0, ready: false, categories: [], categoryId: '', frequent: [], recommend: [],
      week: [], schoolBoundaryText: '', wish: null, wishSelected: [], wishDishes: [], wishTotal: 0, wishSettings: null,
      wishKeyword: '', wishPage: 1 });
  }
});
