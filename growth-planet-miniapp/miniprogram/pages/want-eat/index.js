const api = require('../../services/api');
const ui = require('../../utils/page');
const { shanghaiDate } = require('../../utils/domain');
const { loadChildren } = require('../../services/children');
const recipes = require('../../services/recipes');

// 看板一次展示 7 天；与后端 31 天上限相比留有充足余量。
const RANGE_DAYS = 7;
const MEAL_LABELS = { BREAKFAST: '早餐', LUNCH: '午餐', DINNER: '晚餐', ALL: '未指定餐次' };
const STATUS_LABELS = { MARKED: '未处理', ADOPTED: '已采购', COOKED: '已做' };
const WEEKDAYS = ['周日', '周一', '周二', '周三', '周四', '周五', '周六'];
// 家长可执行的状态流转（当前状态自身不作为动作展示）。
const STATUS_ACTIONS = [
  { status: 'ADOPTED', label: '已采购' },
  { status: 'COOKED', label: '已做' },
  { status: 'MARKED', label: '撤回' }
];

function shiftDate(date, delta) {
  const [year, month, day] = date.split('-').map(Number);
  return new Date(Date.UTC(year, month - 1, day + delta)).toISOString().slice(0, 10);
}
function dateLabel(date) {
  const [year, month, day] = date.split('-').map(Number);
  return month + '/' + day + ' ' + WEEKDAYS[new Date(Date.UTC(year, month - 1, day)).getUTCDay()];
}
function flagText(item) {
  const flags = [];
  if (item.missing) flags.push('菜品已下架');
  if (item.expired) flags.push('已过期');
  if (item.allergyConflict) flags.push('含过敏原');
  if (item.disliked) flags.push('孩子忌口');
  return flags.join(' · ');
}
function statusClass(status) {
  if (status === 'COOKED') return 'ok';
  return status === 'ADOPTED' ? 'info' : 'normal';
}
// 家庭儿童接口只返回关系标签（如「儿子」），用关系 + ID 保证多孩可区分。
function childLabel(child) {
  return (child.relationLabel || '孩子') + ' · ' + child.childId;
}

ui.page({
  data: {
    role: '', busy: false, error: '', receipt: '', ready: false,
    children: [], childLabels: [], childIndex: 0, childId: '',
    today: '', from: '', to: '', rangeDays: RANGE_DAYS, rangeLabel: '',
    days: [], summary: [], totalItems: 0, expiredCount: 0,
    // 配方按菜品缓存：同一道菜多次展开只请求一次。键 = type:dishId。
    recipeOpenKey: '', recipeStates: {}
  },
  onLoad(query) {
    this.requestedChildId = query && query.childId ? query.childId : '';
    this.singleDay = query && query.range === 'today';
  },
  onShow() {
    if (!ui.guard(this, 'PARENT')) return;
    const today = shanghaiDate();
    const rangeDays = this.singleDay ? 1 : RANGE_DAYS;
    this.setData({ today, from: today, to: shiftDate(today, rangeDays - 1), rangeDays, receipt: '' });
    return ui.run(this, async () => {
      const children = await loadChildren();
      const requestedChildId = this.requestedChildId || this.data.childId;
      const childIndex = Math.max(0, children.findIndex(item => item.childId === requestedChildId));
      this.requestedChildId = '';
      this.setData({
        children,
        childLabels: children.map(childLabel),
        childIndex,
        childId: children[childIndex].childId
      });
      await this.read();
    });
  },
  async read() {
    const { childId, from, to } = this.data;
    if (!childId) {
      this.setData({ days: [], summary: [], totalItems: 0, expiredCount: 0, ready: true });
      return;
    }
    const board = await api.get('/parent/want-eat', { childId, from, to });
    this.setData({
      today: board.today,
      days: this.presentDays(board.days),
      summary: ((board.summary && board.summary.dishes) || []).map(item => Object.assign({}, item, {
        key: item.type + ':' + item.id,
        countText: '共 ' + item.count + ' 天',
        datesText: (item.dates || []).join('、')
      })),
      totalItems: (board.summary && board.summary.totalItems) || 0,
      expiredCount: board.expiredCount || 0,
      rangeLabel: board.from === board.to ? board.from : board.from + ' ~ ' + board.to,
      ready: true
    });
  },
  presentDays(days) {
    return (days || []).map(day => ({
      menuDate: day.menuDate,
      dateLabel: dateLabel(day.menuDate),
      meals: (day.meals || []).map(meal => ({
        mealType: meal.mealType,
        mealLabel: MEAL_LABELS[meal.mealType] || meal.mealType,
        // WISH = 心愿菜谱目录里的标记（meal_type=ALL，无当天菜单上下文），必须与菜单标记区分开。
        sourceLabel: meal.sourceType === 'SCHOOL' ? '学校餐单'
          : meal.sourceType === 'WISH' ? '心愿菜谱' : '家庭菜单',
        items: (meal.items || []).map(item => Object.assign({}, item, {
          key: String(item.wantEatId),
          displayName: item.name || '（菜品信息不可用）',
          statusLabel: STATUS_LABELS[item.status] || item.status,
          statusClass: statusClass(item.status),
          flags: flagText(item),
          // 菜品已下架/删除时后端按 404 处理，不给「看做法」入口，省一次必然失败的请求。
          canViewRecipe: !item.missing && !!item.name,
          recipeKey: recipes.recipeKey(item.type, item.id),
          actions: STATUS_ACTIONS.filter(action => action.status !== item.status)
        }))
      }))
    }));
  },
  switchChild(e) {
    const index = Number(e.detail.value);
    const child = this.data.children[index];
    if (!child) return;
    return ui.run(this, async () => {
      // 换孩子即换一批菜，配方缓存必须一起清，否则会串显示前一个孩子的菜。
      this.setData({ childIndex: index, childId: child.childId, receipt: '',
        recipeOpenKey: '', recipeStates: {} });
      await this.read();
    });
  },
  shiftRange(e) {
    const delta = Number(e.currentTarget.dataset.delta) * this.data.rangeDays;
    return ui.run(this, async () => {
      const from = shiftDate(this.data.from, delta);
      this.setData({ from, to: shiftDate(from, this.data.rangeDays - 1), receipt: '',
        recipeOpenKey: '', recipeStates: {} });
      await this.read();
    });
  },
  /**
   * 「看做法」：首次点击懒加载配方并展开，已加载的直接切换展开/折叠（不重复请求）。
   * 加载失败只写本条目的状态并给出重试入口；401 / E-010 仍交给 ui.run 统一跳转。
   */
  toggleRecipe(e) {
    const key = e.currentTarget.dataset.key;
    const loaded = this.data.recipeStates[key];
    if (loaded && loaded.status === 'ready') {
      this.setData({ recipeOpenKey: this.data.recipeOpenKey === key ? '' : key });
      return;
    }
    const [type, dishId] = String(key).split(':');
    return ui.run(this, async () => {
      this.setData({ recipeOpenKey: key,
        recipeStates: Object.assign({}, this.data.recipeStates, { [key]: { status: 'loading' } }) });
      try {
        const payload = await recipes.fetchRecipe(type, dishId);
        this.setData({ recipeStates: Object.assign({}, this.data.recipeStates,
          { [key]: recipes.presentRecipe(payload) }) });
      } catch (error) {
        if (error.status === 401 || error.code === 'E-010') throw error;
        this.setData({ recipeStates: Object.assign({}, this.data.recipeStates,
          { [key]: { status: 'error', message: error.message || '配方加载失败，请重试' } }) });
      }
    });
  },
  mark(e) {
    const { id, status, version } = e.currentTarget.dataset;
    return ui.run(this, async () => {
      await api.post('/parent/want-eat/' + id + '/status',
        { status, expectedVersion: Number(version) });
      this.setData({ receipt: '已标记为「' + (STATUS_LABELS[status] || status) + '」' });
      await this.read();
    });
  },
  onHide() {
    this.setData({ days: [], summary: [], ready: false, receipt: '', error: '',
      recipeOpenKey: '', recipeStates: {} });
  }
});
