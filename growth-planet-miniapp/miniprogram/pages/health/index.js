const api = require('../../services/api');
const ui = require('../../utils/page');
const { shanghaiDate } = require('../../utils/domain');

// F-033：预设打卡项仅用于表单快速填充，落库仍以家长确认后的配置为准。
const PRESETS = [
  { name: '喝水', icon: '💧', unit: '杯', dailyTarget: 6 },
  { name: '睡眠', icon: '😴', unit: '小时', dailyTarget: 1 },
  { name: '运动', icon: '🏃', unit: '次', dailyTarget: 1 },
  { name: '刷牙', icon: '🪥', unit: '次', dailyTarget: 2 },
  { name: '阅读', icon: '📚', unit: '分钟', dailyTarget: 1 },
  { name: '洗手', icon: '🧼', unit: '次', dailyTarget: 0 }
];
const WEEK_LABELS = ['日', '一', '二', '三', '四', '五', '六'];
// F-035：v004 播种的连续打卡勋章阈值（HEALTH_STREAK_3/7/14/30），仅用于打卡后的成就提示。
const STREAK_MILESTONES = [3, 7, 14, 30];
const MONTH_PATTERN = /^\d{4}-\d{2}$/;

const text = value => String(value === null || value === undefined ? '' : value).trim();

function monthShift(month, delta) {
  const [year, index] = month.split('-').map(Number);
  return new Date(Date.UTC(year, index - 1 + delta, 1)).toISOString().slice(0, 7);
}
function monthLabel(month) {
  const [year, index] = month.split('-').map(Number);
  return year + ' 年 ' + index + ' 月';
}
function monthCells(month, checked, today) {
  const [year, index] = month.split('-').map(Number);
  const offset = new Date(Date.UTC(year, index - 1, 1)).getUTCDay();
  const days = new Date(Date.UTC(year, index, 0)).getUTCDate();
  const cells = [];
  for (let pad = 0; pad < offset; pad += 1) cells.push({ key: 'pad-' + pad, label: '', pad: true });
  for (let day = 1; day <= days; day += 1) {
    const date = month + '-' + String(day).padStart(2, '0');
    cells.push({
      key: date, label: String(day),
      checked: checked.has(date), today: date === today, future: date > today
    });
  }
  return cells;
}
function blankForm() {
  return {
    showForm: false, isEdit: false,
    formItemId: '', formVersion: 0,
    formName: '', formIcon: '', formUnit: '', formTarget: '', formOrder: ''
  };
}

ui.page({
  data: Object.assign(blankForm(), {
    role: '', busy: false, error: '', receipt: '', ready: false,
    today: '', month: '',
    // 儿童端（F-034/F-035）
    checkItems: [], doneCount: 0, totalCount: 0, streak: 0,
    weekLabels: WEEK_LABELS, cells: [], monthLabel: '', monthChecked: 0, canNextMonth: false,
    // 家长端（F-033）
    managed: [],
    presets: PRESETS.map(item => Object.assign({}, item, { label: item.icon + ' ' + item.name }))
  }),
  input: ui.input,
  onShow() {
    if (!ui.guard(this)) return;
    const today = shanghaiDate();
    this.setData(Object.assign(blankForm(), { today, month: today.slice(0, 7) }));
    return ui.run(this, () => this.read());
  },
  async read() {
    if (this.data.role === 'PARENT') {
      await this.readManaged();
      return;
    }
    await this.readChild();
  },

  // ---- 儿童端：今日打卡 + 日历（F-034/F-035）----

  async readChild() {
    const month = MONTH_PATTERN.test(this.data.month) ? this.data.month : this.data.today.slice(0, 7);
    const [items, todayRows, calendar] = await Promise.all([
      api.get('/child/check-in/items'),
      api.get('/child/check-in/today'),
      api.get('/child/check-in/calendar', { month })
    ]);
    const rows = new Map((todayRows || []).map(row => [String(row.itemId), row]));
    const checkItems = (items || []).map(item => {
      const row = rows.get(String(item.itemId)) || {};
      const dailyTarget = row.dailyTarget === undefined ? Number(item.dailyTarget || 0) : Number(row.dailyTarget);
      const count = Number(row.count || 0);
      const reached = row.reached === true;
      const unit = item.unit || '次';
      const percent = dailyTarget > 0 ? Math.min(100, Math.round(count / dailyTarget * 100)) : 0;
      return Object.assign({}, item, {
        key: String(item.itemId),
        count, dailyTarget, reached, percent,
        targetText: dailyTarget > 0 ? count + ' / ' + dailyTarget + unit : '已打卡 ' + count + ' 次',
        limitText: dailyTarget > 0 ? '每日上限 ' + dailyTarget + unit : '不限次数',
        actionLabel: reached ? '今日已完成' : '打卡'
      });
    });
    const doneCount = checkItems.filter(item =>
      item.dailyTarget > 0 ? item.reached : item.count > 0).length;
    const currentMonth = this.data.today.slice(0, 7);
    const checked = new Set((calendar && calendar.checkedDates) || []);
    this.setData({
      month,
      checkItems, totalCount: checkItems.length, doneCount,
      streak: (calendar && calendar.currentStreak) || 0,
      cells: monthCells(month, checked, this.data.today),
      monthLabel: monthLabel(month),
      monthChecked: checked.size,
      canNextMonth: month < currentMonth,
      ready: true
    });
  },
  checkIn(e) {
    const itemId = e.currentTarget.dataset.id;
    return ui.run(this, async () => {
      if (this.data.role !== 'CHILD') return;
      const record = await api.post('/child/check-in?itemId=' + itemId, {});
      await this.readChild();
      const item = this.data.checkItems.find(entry => entry.key === String(itemId));
      const name = (item && item.name) || (record && record.itemName) || '打卡项';
      this.setData({ receipt: this.streakReceipt(name) });
    });
  },
  streakReceipt(name) {
    const streak = this.data.streak;
    const base = '「' + name + '」打卡成功，已连续打卡 ' + streak + ' 天';
    return STREAK_MILESTONES.indexOf(streak) >= 0
      ? base + '，去勋章墙看看新解锁的成就吧 🏅'
      : base + '，继续保持 💪';
  },
  shiftMonth(e) {
    const target = monthShift(this.data.month, Number(e.currentTarget.dataset.delta));
    return ui.run(this, async () => {
      if (this.data.role !== 'CHILD') return;
      if (target > this.data.today.slice(0, 7)) return;
      this.setData({ month: target });
      await this.readChild();
    });
  },

  // ---- 家长端：打卡项配置（F-033）----

  async readManaged() {
    const managed = (await api.get('/parent/check-item')).map(item => Object.assign({}, item, {
      key: String(item.itemId),
      unitText: item.unit || '次',
      targetText: Number(item.dailyTarget || 0) > 0
        ? '每日上限 ' + item.dailyTarget + (item.unit || '次')
        : '不限次数'
    }));
    this.setData({ managed, ready: true });
  },
  toggleForm() {
    if (this.data.role !== 'PARENT') return;
    if (this.data.showForm) {
      this.setData(blankForm());
      return;
    }
    this.setData(Object.assign(blankForm(), { showForm: true, formOrder: String(this.data.managed.length + 1) }));
  },
  applyPreset(e) {
    if (this.data.role !== 'PARENT') return;
    const preset = PRESETS[Number(e.currentTarget.dataset.index)];
    if (!preset) return;
    this.setData(Object.assign(blankForm(), {
      showForm: true, isEdit: false,
      formName: preset.name, formIcon: preset.icon, formUnit: preset.unit,
      formTarget: String(preset.dailyTarget),
      formOrder: String(this.data.managed.length + 1)
    }));
  },
  edit(e) {
    const item = this.data.managed.find(entry => entry.key === e.currentTarget.dataset.id);
    if (!item) return;
    this.setData(Object.assign(blankForm(), {
      showForm: true, isEdit: true,
      formItemId: item.itemId, formVersion: item.version,
      formName: item.name, formIcon: item.icon || '', formUnit: item.unit || '',
      formTarget: String(item.dailyTarget || 0), formOrder: String(item.sortOrder || 0)
    }));
  },
  save() {
    return ui.run(this, async () => {
      if (this.data.role !== 'PARENT') return;
      const name = text(this.data.formName);
      if (!name || name.length > 32) throw new Error('请填写打卡项名称（1-32 字）');
      const icon = text(this.data.formIcon);
      if (icon.length > 64) throw new Error('图标最多 64 字，建议使用 1 个 emoji');
      const unit = text(this.data.formUnit);
      if (unit.length > 8) throw new Error('单位最多 8 字，例如「杯」「次」「小时」');
      const target = text(this.data.formTarget);
      if (target && !/^\d{1,4}$/.test(target)) throw new Error('每日上限需为 0-9999 的整数，0 表示不限次数');
      const order = text(this.data.formOrder);
      if (order && !/^\d{1,4}$/.test(order)) throw new Error('排序需为 0-9999 的整数，数字小的排在前');
      const body = {
        name,
        dailyTarget: target ? Number(target) : 0,
        sortOrder: order ? Number(order) : 0
      };
      if (icon) body.icon = icon;
      if (unit) body.unit = unit;
      const isEdit = this.data.isEdit;
      if (isEdit) {
        await api.put('/parent/check-item/' + this.data.formItemId + '?expectedVersion=' + this.data.formVersion, body);
      } else {
        await api.post('/parent/check-item', body);
      }
      this.setData(Object.assign(blankForm(), {
        receipt: isEdit ? '打卡项已更新' : '打卡项已添加，孩子现在就能打卡了'
      }));
      await this.readManaged();
    });
  },
  remove(e) {
    const item = this.data.managed.find(entry => entry.key === e.currentTarget.dataset.id);
    return ui.run(this, async () => {
      if (this.data.role !== 'PARENT' || !item) return;
      if (!await ui.confirm('删除后孩子将不再看到该项，历史打卡记录仍会保留。确认删除「' + item.name + '」？',
        '删除打卡项')) return;
      await api.del('/parent/check-item/' + item.itemId, { expectedVersion: item.version });
      this.setData(Object.assign(blankForm(), { receipt: '已删除「' + item.name + '」' }));
      await this.readManaged();
    });
  },
  onHide() {
    this.setData(Object.assign(blankForm(), {
      checkItems: [], managed: [], cells: [], ready: false, receipt: '', error: ''
    }));
  }
});
