const api = require('../../services/api');
const ui = require('../../utils/page');
const { shanghaiDate } = require('../../utils/domain');
const { loadChildren } = require('../../services/children');

// 看板一次展示 7 天；与后端 31 天上限相比留有充足余量。
const RANGE_DAYS = 7;
const MEAL_LABELS = { BREAKFAST: '早餐', LUNCH: '午餐', DINNER: '晚餐' };
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
    today: '', from: '', to: '', rangeLabel: '',
    days: [], summary: [], totalItems: 0, expiredCount: 0
  },
  onShow() {
    if (!ui.guard(this, 'PARENT')) return;
    const today = shanghaiDate();
    this.setData({ today, from: today, to: shiftDate(today, RANGE_DAYS - 1), receipt: '' });
    return ui.run(this, async () => {
      const children = await loadChildren();
      this.setData({
        children,
        childLabels: children.map(childLabel),
        childIndex: 0,
        childId: children[0].childId
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
        sourceLabel: meal.sourceType === 'SCHOOL' ? '学校餐单' : '家庭菜单',
        items: (meal.items || []).map(item => Object.assign({}, item, {
          key: String(item.wantEatId),
          displayName: item.name || '（菜品信息不可用）',
          statusLabel: STATUS_LABELS[item.status] || item.status,
          statusClass: statusClass(item.status),
          flags: flagText(item),
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
      this.setData({ childIndex: index, childId: child.childId, receipt: '' });
      await this.read();
    });
  },
  shiftRange(e) {
    const delta = Number(e.currentTarget.dataset.delta) * RANGE_DAYS;
    return ui.run(this, async () => {
      const from = shiftDate(this.data.from, delta);
      this.setData({ from, to: shiftDate(from, RANGE_DAYS - 1), receipt: '' });
      await this.read();
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
    this.setData({ days: [], summary: [], ready: false, receipt: '', error: '' });
  }
});
