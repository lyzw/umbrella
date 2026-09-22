const api = require('../../services/api');
const ui = require('../../utils/page');
const { shanghaiDate, dishKey, dishRef } = require('../../utils/domain');

const MEAL_LABELS = { BREAKFAST: '早餐', LUNCH: '午餐', DINNER: '晚餐' };
const MEAL_TYPES = ['BREAKFAST', 'LUNCH', 'DINNER'];
const WEEKDAYS = ['周日', '周一', '周二', '周三', '周四', '周五', '周六'];
// 与后端批量上限一致：7 天 × 3 餐。
const BATCH_MAX_ITEMS = 21;

function shiftDate(date, delta) {
  const [year, month, day] = date.split('-').map(Number);
  return new Date(Date.UTC(year, month - 1, day + delta)).toISOString().slice(0, 10);
}
// 以周一为一周起点（国内家长排周计划的习惯），周日归到上一周。
function mondayOf(date) {
  const [year, month, day] = date.split('-').map(Number);
  const weekday = new Date(Date.UTC(year, month - 1, day)).getUTCDay();
  return shiftDate(date, -(weekday === 0 ? 6 : weekday - 1));
}
function dayLabel(date, today) {
  if (date === today) return '今天';
  const [year, month, day] = date.split('-').map(Number);
  return WEEKDAYS[new Date(Date.UTC(year, month - 1, day)).getUTCDay()];
}

ui.page({
  data: {
    role: '', busy: false, error: '', receipt: '', ready: false, keyword: '',
    today: '', weekFrom: '', weekTo: '', weekLabel: '',
    days: [], cellCount: 0, catalog: [], dishCount: 0, results: []
  },
  onShow() {
    if (!ui.guard(this, 'PARENT')) return;
    const today = shanghaiDate();
    const from = mondayOf(today);
    const to = shiftDate(from, 6);
    this.selectedCells = new Set();
    this.selectedDishKeys = new Set();
    this.catalogDishes = [];
    this.setData({ today, weekFrom: from, weekTo: to, weekLabel: from.slice(5).replace('-', '/') + ' - ' + to.slice(5).replace('-', '/'),
      receipt: '', results: [], keyword: '' });
    return ui.run(this, async () => {
      await this.loadCatalog();
      await this.read();
    });
  },
  async loadCatalog() {
    this.presetDishes = await this.loadAll('/parent/dish');
    this.familyDishes = (await this.loadAll('/parent/family-dish', { status: 'ON_SALE' }))
      .map(item => ({ ...item, sourceType: 'FAMILY' }));
    this.catalogDishes = [...this.familyDishes, ...this.presetDishes.map(item => ({ ...item, sourceType: 'PRESET' }))]
      .map(item => ({ ...item, key: dishKey(item), type: item.sourceType, id: String(item.dishId) }));
    this.decorateCatalog();
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
    const result = await api.get('/parent/menu-week', { from: this.data.weekFrom, to: this.data.weekTo });
    const days = (result.days || []).map(day => ({
      date: day.menuDate,
      label: dayLabel(day.menuDate, this.data.today),
      dateText: day.menuDate.slice(5).replace('-', '/'),
      isToday: day.menuDate === this.data.today,
      meals: (day.meals || []).map(meal => {
        const hasMenu = Boolean(meal.menuId);
        return {
          mealType: meal.mealType,
          label: MEAL_LABELS[meal.mealType] || meal.mealType,
          empty: !hasMenu,
          selected: this.selectedCells.has(day.menuDate + '|' + meal.mealType),
          statusText: hasMenu ? (meal.status === 'DRAFT' ? '草稿 · ' + meal.dishCount + ' 道' : meal.dishCount + ' 道') : '空'
        };
      })
    }));
    this.setData({ days, ready: true, cellCount: this.selectedCells.size });
  },
  decorateCatalog() {
    const keyword = (this.data.keyword || '').trim();
    const catalog = this.catalogDishes
      .filter(item => !keyword || item.name.includes(keyword))
      .map(item => ({ ...item, selected: this.selectedDishKeys.has(item.key) }));
    this.setData({ catalog, dishCount: this.selectedDishKeys.size });
  },
  shiftWeek(e) {
    if (this.data.busy) return;
    const from = shiftDate(this.data.weekFrom, Number(e.currentTarget.dataset.delta) * 7);
    const to = shiftDate(from, 6);
    this.setData({ weekFrom: from, weekTo: to, receipt: '', results: [],
      weekLabel: from.slice(5).replace('-', '/') + ' - ' + to.slice(5).replace('-', '/') });
    return ui.run(this, () => this.read());
  },
  toggleCell(e) {
    if (this.data.busy) return;
    const key = e.currentTarget.dataset.date + '|' + e.currentTarget.dataset.meal;
    if (this.selectedCells.has(key)) {
      this.selectedCells.delete(key);
    } else {
      if (this.selectedCells.size >= BATCH_MAX_ITEMS) {
        this.setData({ error: '一次最多发布 ' + BATCH_MAX_ITEMS + ' 个餐次' });
        return;
      }
      this.selectedCells.add(key);
    }
    this.setData({ error: '' });
    const days = this.data.days.map(day => ({
      ...day,
      meals: day.meals.map(meal => ({
        ...meal, selected: this.selectedCells.has(day.date + '|' + meal.mealType)
      }))
    }));
    this.setData({ days, cellCount: this.selectedCells.size });
  },
  // 以某个已有菜单为模板：把它的菜品设为本次要铺的菜，避免整周重复挑菜。
  useAsTemplate(e) {
    return ui.run(this, async () => {
      const date = e.currentTarget.dataset.date;
      const mealType = e.currentTarget.dataset.meal;
      const menu = await api.get('/parent/menu-daily', { menuDate: date, mealType });
      // 未发布的格子后端返回 data=null，这里按空模板处理而不是报错。
      const refs = ((menu && menu.dishes) || []).map(item => dishRef(item));
      if (!refs.length) {
        this.setData({ receipt: date.slice(5) + ' ' + MEAL_LABELS[mealType] + ' 还没有菜品，无法作为模板' });
        return;
      }
      this.selectedDishKeys = new Set(refs.map(ref => dishKey(ref)));
      this.decorateCatalog();
      this.setData({ receipt: '已用 ' + date.slice(5) + ' ' + MEAL_LABELS[mealType] + ' 的 ' + refs.length + ' 道菜作为模板' });
    });
  },
  toggleDish(e) {
    if (this.data.busy) return;
    const key = e.currentTarget.dataset.key;
    if (this.selectedDishKeys.has(key)) {
      this.selectedDishKeys.delete(key);
    } else {
      if (this.selectedDishKeys.size >= 50) {
        this.setData({ error: '每个餐次最多选择 50 种餐食' });
        return;
      }
      this.selectedDishKeys.add(key);
    }
    this.setData({ error: '' });
    this.decorateCatalog();
  },
  search(e) {
    this.setData({ keyword: e.detail.value });
    this.decorateCatalog();
  },
  publish() {
    return ui.run(this, async () => {
      const dishIds = this.catalogDishes.filter(item => this.selectedDishKeys.has(item.key))
        .map(item => ({ type: item.type, id: item.id }));
      const items = [];
      this.data.days.forEach(day => day.meals.forEach(meal => {
        if (this.selectedCells.has(day.date + '|' + meal.mealType)) {
          items.push({ menuDate: day.date, mealType: meal.mealType, dishIds, status: 'PUBLISHED' });
        }
      }));
      if (!items.length) throw new Error('请先点选要发布的餐次');
      if (!dishIds.length) throw new Error('请先选择要铺的菜品');
      const result = await api.post('/parent/menu-daily/batch', { items });
      const results = (result.results || []).map(row => ({
        key: row.menuDate + '|' + row.mealType,
        ok: row.ok,
        text: row.menuDate.slice(5).replace('-', '/') + ' ' + MEAL_LABELS[row.mealType]
          + (row.ok ? ' 已发布' : ' 失败（' + row.code + '）')
      }));
      // 部分成功语义：只保留失败项为选中态，家长可以直接再点一次重试。
      this.selectedCells = new Set(results.filter(row => !row.ok).map(row => row.key));
      this.setData({ results,
        receipt: '成功 ' + result.okCount + ' 条，失败 ' + result.failCount + ' 条'
          + (result.failCount ? '，失败项已保留勾选，可再点一次重试' : '') });
      await this.read();
    });
  },
  retry() { return ui.run(this, () => this.read()); },
  onHide() {
    this.selectedCells = new Set();
    this.selectedDishKeys = new Set();
    this.catalogDishes = [];
    this.setData({ days: [], catalog: [], cellCount: 0, dishCount: 0, results: [], receipt: '', error: '', ready: false });
  }
});
