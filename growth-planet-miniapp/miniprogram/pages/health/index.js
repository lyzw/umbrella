const api = require('../../services/api');
const ui = require('../../utils/page');
const lifecycle = require('../../services/lifecycle');
const session = require('../../services/session');
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
const MONTH_PATTERN = /^[1-9]\d{3}-(0[1-9]|1[0-2])$/;

const text = value => String(value === null || value === undefined ? '' : value).trim();
const itemId = value => {
  const id = text(value);
  return /^[1-9]\d*$/.test(id) ? id : '';
};
const validVersion = value => /^\d+$/.test(text(value)) && Number.isSafeInteger(Number(value));
const protectedError = error => error.status === 401 || error.status === 403 || error.code === 'E-010';

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
    checkInPending: {},
    weekLabels: WEEK_LABELS, cells: [], monthLabel: '', monthChecked: 0, canNextMonth: false,
    // 家长端（F-033）
    managed: [],
    presets: PRESETS.map(item => Object.assign({}, item, { label: item.icon + ' ' + item.name }))
  }),
  input: ui.input,
  onShow() {
    this.viewToken = (this.viewToken || 0) + 1;
    this.readToken = (this.readToken || 0) + 1;
    this.clearContent();
    this.setData(Object.assign(blankForm(), { receipt: '' }));
    if (!ui.guard(this)) return;
    const today = shanghaiDate();
    this.setData(Object.assign(blankForm(), {
      today, month: today.slice(0, 7), checkInPending: {}
    }));
    return ui.run(this, () => this.read());
  },
  clearContent() {
    this.setData({
      checkItems: [], managed: [], cells: [], checkInPending: {},
      doneCount: 0, totalCount: 0, streak: 0, monthChecked: 0,
      monthLabel: '', canNextMonth: false, ready: false
    });
  },
  refresh() {
    return ui.run(this, async () => {
      if (!ui.guard(this)) return;
      await this.read();
    });
  },
  async read() {
    if (this.data.role === 'PARENT') {
      await this.readManaged();
      return;
    }
    if (this.data.role === 'CHILD') await this.readChild();
  },

  // ---- 儿童端：今日打卡 + 日历（F-034/F-035）----

  async readChild() {
    if (this.data.role !== 'CHILD') return false;
    const today = shanghaiDate();
    const currentMonth = today.slice(0, 7);
    const month = MONTH_PATTERN.test(this.data.month) && this.data.month <= currentMonth
      ? this.data.month : currentMonth;
    const readToken = (this.readToken || 0) + 1;
    const viewToken = this.viewToken;
    const revision = lifecycle.current();
    const generation = session.generation();
    this.readToken = readToken;
    this.setData({ today, month, ready: false });
    try {
    const [items, todayRows, calendar] = await Promise.all([
      api.get('/child/check-in/items'),
      api.get('/child/check-in/today'),
      api.get('/child/check-in/calendar', { month })
    ]);
    if (!this.isCurrentView(viewToken, revision, generation)
      || readToken !== this.readToken || this.data.role !== 'CHILD' || this.data.month !== month) return false;
    if (today !== shanghaiDate()) throw new Error('日期已更新，请刷新今日打卡');
    if (!Array.isArray(items) || !Array.isArray(todayRows)) throw new Error('打卡数据异常，请刷新重试');
    const rows = new Map((todayRows || []).map(row => [String(row.itemId), row]));
    const checkItems = (items || []).map(item => {
      const row = rows.get(String(item.itemId)) || {};
      const dailyTarget = row.dailyTarget === undefined ? Number(item.dailyTarget || 0) : Number(row.dailyTarget);
      const count = Number(row.count || 0);
      const reached = row.reached === true || (dailyTarget > 0 && count >= dailyTarget);
      const unit = item.unit || '次';
      const percent = dailyTarget > 0 ? Math.min(100, Math.round(count / dailyTarget * 100)) : 0;
      return Object.assign({}, item, {
        key: String(item.itemId),
        count, dailyTarget, reached, percent,
        pending: this.data.checkInPending[String(item.itemId)] === true,
        targetText: dailyTarget > 0 ? count + ' / ' + dailyTarget + unit : '已打卡 ' + count + ' 次',
        limitText: dailyTarget > 0 ? '每日上限 ' + dailyTarget + unit : '不限次数',
        actionLabel: reached ? '今日已完成' : '打卡'
      });
    });
    const doneCount = checkItems.filter(item =>
      item.dailyTarget > 0 ? item.reached : item.count > 0).length;
    const dates = monthCells(month, new Set(), today).filter(cell => !cell.pad && !cell.future).map(cell => cell.key);
    const checked = new Set(((calendar && calendar.checkedDates) || []).filter(date => dates.includes(date)));
    this.setData({
      month,
      checkItems, totalCount: checkItems.length, doneCount,
      streak: (calendar && calendar.currentStreak) || 0,
      cells: monthCells(month, checked, today),
      monthLabel: monthLabel(month),
      monthChecked: checked.size,
      canNextMonth: month < currentMonth,
      ready: true
    });
    return true;
    } catch (error) {
      if (!this.isCurrentView(viewToken, revision, generation) || readToken !== this.readToken) {
        if (error.status === 401 || error.cancelled) throw error;
        return false;
      }
      this.clearContent();
      throw error;
    }
  },
  isCurrentView(viewToken, revision, generation) {
    return viewToken === this.viewToken
      && revision === lifecycle.current()
      && generation === session.generation();
  },
  setCheckInPending(id, pending) {
    const key = itemId(id);
    if (!key) return;
    const next = Object.assign({}, this.data.checkInPending);
    if (pending) next[key] = true;
    else delete next[key];
    const checkItems = (this.data.checkItems || []).map(item => item.key === key
      ? Object.assign({}, item, { pending })
      : item);
    this.setData({ checkInPending: next, checkItems });
  },
  checkIn(e) {
    const id = itemId(e && e.currentTarget && e.currentTarget.dataset && e.currentTarget.dataset.id);
    if (!id || !(this.data.checkItems || []).some(item => item.key === id)) return;
    return ui.run(this, async () => {
      if (this.data.role !== 'CHILD') return;
      this.setData({ receipt: '' });
      if (this.data.today && this.data.today !== shanghaiDate()) {
        await this.readChild();
        return;
      }
      const selected = this.data.checkItems.find(item => item.key === id);
      if (!this.data.ready || !selected || selected.reached) return;
      if (this.data.checkInPending[id]) return;
      const viewToken = this.viewToken;
      const revision = lifecycle.current();
      const generation = session.generation();
      this.setCheckInPending(id, true);
      try {
        const record = await api.post('/child/check-in?itemId=' + id, {});
        if (!this.isCurrentView(viewToken, revision, generation)) return;
        // 写入成功先确认结果；刷新失败时只重读，不诱导再次打卡。
        this.setData({ receipt: '本次打卡已记录' });
        try {
          if (!await this.readChild()) return;
        } catch (error) {
          if (!error.cancelled && !protectedError(error) && this.isCurrentView(viewToken, revision, generation)) {
            this.setData({ receipt: '本次打卡已记录，数据刷新失败，请刷新查看，无需再次打卡' });
          }
          throw error;
        }
        if (!this.isCurrentView(viewToken, revision, generation)) return;
        const item = this.data.checkItems.find(entry => entry.key === id);
        const name = (item && item.name) || (record && record.itemName) || '打卡项';
        this.setData({ receipt: this.streakReceipt(name) });
      } catch (error) {
        if (this.isCurrentView(viewToken, revision, generation)) {
          if (protectedError(error) || error.unknown || error.code === 'E-013') this.clearContent();
          if (protectedError(error)) this.setData({ receipt: '' });
          if (error.unknown) error.message = '打卡结果尚未确认，请先刷新记录，勿重复打卡';
        }
        throw error;
      } finally {
        if (this.isCurrentView(viewToken, revision, generation)) this.setCheckInPending(id, false);
      }
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
    if (!MONTH_PATTERN.test(this.data.month)) return;
    const delta = Number(e && e.currentTarget && e.currentTarget.dataset && e.currentTarget.dataset.delta);
    if (delta !== -1 && delta !== 1) return;
    const target = monthShift(this.data.month, delta);
    return ui.run(this, async () => {
      if (this.data.role !== 'CHILD') return;
      if (!MONTH_PATTERN.test(target) || target > shanghaiDate().slice(0, 7)) return;
      this.setData({ month: target });
      await this.readChild();
    });
  },

  // ---- 家长端：打卡项配置（F-033）----

  async readManaged() {
    const viewToken = this.viewToken;
    const revision = lifecycle.current();
    const generation = session.generation();
    const readToken = (this.readToken || 0) + 1;
    this.readToken = readToken;
    this.setData({ ready: false });
    try {
    const response = await api.get('/parent/check-item');
    if (!this.isCurrentView(viewToken, revision, generation) || this.data.role !== 'PARENT') return;
    if (readToken !== this.readToken) return;
    if (!Array.isArray(response)) throw new Error('打卡项数据异常，请刷新重试');
    const managed = response.map(item => Object.assign({}, item, {
      key: String(item.itemId),
      unitText: item.unit || '次',
      targetText: Number(item.dailyTarget || 0) > 0
        ? '每日上限 ' + item.dailyTarget + (item.unit || '次')
        : '不限次数'
    }));
    this.setData({ managed, ready: true });
    } catch (error) {
      if (this.isCurrentView(viewToken, revision, generation) && readToken === this.readToken) {
        this.clearContent();
        if (protectedError(error)) this.setData(Object.assign(blankForm(), { receipt: '' }));
      }
      throw error;
    }
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
    if (this.data.role !== 'PARENT') return;
    const id = text(e && e.currentTarget && e.currentTarget.dataset && e.currentTarget.dataset.id);
    const item = this.data.managed.find(entry => entry.key === id);
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
      const viewToken = this.viewToken, revision = lifecycle.current(), generation = session.generation();
      this.setData({ receipt: '' });
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
        const formItemId = itemId(this.data.formItemId);
        const formVersion = Number(this.data.formVersion);
        if (!formItemId) throw new Error('编辑目标已失效，请重新选择打卡项');
        if (!validVersion(this.data.formVersion)) throw new Error('打卡项版本已失效，请重新加载');
        await this.writeManaged(() => api.put('/parent/check-item/' + formItemId + '?expectedVersion=' + formVersion, body));
      } else {
        await this.writeManaged(() => api.post('/parent/check-item', body));
      }
      if (!this.isCurrentView(viewToken, revision, generation)) return;
      this.setData(Object.assign(blankForm(), {
        receipt: isEdit ? '打卡项已更新' : '打卡项已添加，孩子现在就能打卡了'
      }));
      await this.readManaged();
    });
  },
  async writeManaged(action) {
    const viewToken = this.viewToken, revision = lifecycle.current(), generation = session.generation();
    try {
      await action();
    } catch (error) {
      if (this.isCurrentView(viewToken, revision, generation) && protectedError(error)) {
        this.clearContent();
        this.setData(Object.assign(blankForm(), { receipt: '' }));
      }
      throw error;
    }
  },
  remove(e) {
    const id = text(e && e.currentTarget && e.currentTarget.dataset && e.currentTarget.dataset.id);
    const item = this.data.managed.find(entry => entry.key === id);
    return ui.run(this, async () => {
      if (this.data.role !== 'PARENT' || !item) return;
      const viewToken = this.viewToken, revision = lifecycle.current(), generation = session.generation();
      this.setData({ receipt: '' });
      if (!await ui.confirm('删除后孩子将不再看到该项，历史打卡记录仍会保留。确认删除「' + item.name + '」？',
        '删除打卡项')) return;
      if (!this.isCurrentView(viewToken, revision, generation)) return;
      const version = Number(item.version);
      if (!itemId(item.itemId) || !validVersion(item.version)) throw new Error('打卡项版本已失效，请重新加载');
      await this.writeManaged(() => api.del('/parent/check-item/' + item.itemId, { expectedVersion: version }));
      if (!this.isCurrentView(viewToken, revision, generation)) return;
      this.setData(Object.assign(blankForm(), { receipt: '已删除「' + item.name + '」' }));
      await this.readManaged();
    });
  },
  onHide() {
    this.viewToken = (this.viewToken || 0) + 1;
    this.readToken = (this.readToken || 0) + 1;
    this.clearContent();
    this.setData(Object.assign(blankForm(), { today: '', month: '', receipt: '', error: '' }));
  }
});
