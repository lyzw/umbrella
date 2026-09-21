const api = require('../../services/api');
const ui = require('../../utils/page');
const { loadChildren } = require('../../services/children');
const { shanghaiDate } = require('../../utils/domain');

const CATEGORIES = ['HOMEWORK', 'CLASS', 'MEDICINE', 'OTHER'];
const CATEGORY = {
  HOMEWORK: { label: '作业', cls: 'cat-home' },
  CLASS: { label: '兴趣班', cls: 'cat-class' },
  MEDICINE: { label: '服药', cls: 'cat-med' },
  OTHER: { label: '其他', cls: 'cat-other' }
};
const REPEATS = ['ONCE', 'DAILY', 'WEEKLY'];
const REPEAT_LABELS = ['单次', '每日', '每周'];
const AHEAD_LABELS = ['准点', '提前5分钟', '提前10分钟', '提前15分钟', '提前30分钟', '提前1小时'];
const AHEAD_VALUES = [0, 5, 10, 15, 30, 60];
const WEEKDAY_LABELS = ['周一', '周二', '周三', '周四', '周五', '周六', '周日'];
const WEEKDAY_NAMES = ['周日', '周一', '周二', '周三', '周四', '周五', '周六'];
const RANGE_DAYS = 7;

function shiftDate(date, days) {
  const parsed = new Date(date + 'T00:00:00Z');
  parsed.setUTCDate(parsed.getUTCDate() + days);
  return parsed.toISOString().slice(0, 10);
}

function dayLabel(date, today) {
  const parsed = new Date(date + 'T00:00:00Z');
  const prefix = date === today ? '今天 · ' : '';
  return prefix + date.slice(5) + ' ' + WEEKDAY_NAMES[parsed.getUTCDay()];
}

function decorate(schedule) {
  const category = CATEGORY[schedule.category] || CATEGORY.OTHER;
  return {
    ...schedule,
    categoryLabel: category.label,
    categoryClass: category.cls,
    repeatLabel: REPEAT_LABELS[REPEATS.indexOf(schedule.repeatType)] || schedule.repeatType,
    weekdayText: schedule.repeatWeekdays
      ? ' · ' + schedule.repeatWeekdays.split(',').map(v => WEEKDAY_LABELS[Number(v) - 1]).join('')
      : '',
    remindText: schedule.remindMinutes > 0 ? ' · 提前' + schedule.remindMinutes + '分钟提醒' : ' · 准点提醒'
  };
}

ui.page({
  data: {
    role: '', busy: false, error: '', receipt: '',
    today: '', from: '',
    children: [], childIndex: 0, childId: '',
    schedules: [], groups: [],
    showForm: false, title: '', categoryIndex: 0, time: '08:00', date: '',
    repeatIndex: 0, aheadIndex: 0, weekdays: [], weekdayOptions: [],
    categoryLabels: CATEGORIES.map(code => CATEGORY[code].label),
    repeatLabels: REPEAT_LABELS, aheadLabels: AHEAD_LABELS
  },
  onShow() {
    if (!ui.guard(this)) return;
    return ui.run(this, async () => {
      const today = shanghaiDate();
      const children = await loadChildren();
      this.setData({ today, date: today, from: today, children, childIndex: 0, childId: children[0].childId });
      this.refreshWeekdayOptions();
      await this.read();
    });
  },
  async read() {
    const { childId, role, from } = this.data;
    if (role === 'PARENT') {
      const schedules = (await api.get('/schedule/list', { childId })).map(decorate);
      this.setData({ schedules, groups: [] });
      return;
    }
    const occurrences = await api.get('/schedule/occurrences', {
      childId, from, to: shiftDate(from, RANGE_DAYS - 1)
    });
    const byDate = new Map();
    occurrences.forEach(item => {
      if (!byDate.has(item.date)) byDate.set(item.date, []);
      const category = CATEGORY[item.category] || CATEGORY.OTHER;
      byDate.get(item.date).push({
        ...item, key: item.scheduleId + '-' + item.date,
        categoryLabel: category.label, categoryClass: category.cls
      });
    });
    const groups = Array.from(byDate.entries())
      .map(([date, items]) => ({ date, label: dayLabel(date, this.data.today), items }))
      .sort((a, b) => (a.date < b.date ? -1 : 1));
    this.setData({ groups, schedules: [] });
  },
  child(e) {
    const index = Number(e.detail.value);
    this.setData({ childIndex: index, childId: this.data.children[index].childId });
    return ui.run(this, () => this.read());
  },
  shiftWeek(e) {
    this.setData({ from: shiftDate(this.data.from, Number(e.currentTarget.dataset.delta)) });
    return ui.run(this, () => this.read());
  },
  jumpTo(e) {
    this.setData({ from: e.detail.value });
    return ui.run(this, () => this.read());
  },
  detail(e) {
    const id = e.currentTarget.dataset.id;
    return ui.run(this, async () => {
      const schedule = await api.get('/schedule/' + id);
      const content = schedule.scheduleDate + ' ' + schedule.scheduleTime
        + '（' + (REPEAT_LABELS[REPEATS.indexOf(schedule.repeatType)] || schedule.repeatType) + '）\n'
        + (schedule.note || '没有备注');
      await new Promise(resolve => wx.showModal({
        title: schedule.title, content, showCancel: false, confirmText: '知道了',
        success: resolve, fail: resolve
      }));
    });
  },
  toggleForm() {
    this.setData({
      showForm: !this.data.showForm, title: '', categoryIndex: 0, time: '08:00',
      date: this.data.today, repeatIndex: 0, aheadIndex: 0, weekdays: []
    });
    this.refreshWeekdayOptions();
  },
  onTitle(e) { this.setData({ title: e.detail.value }); },
  category(e) { this.setData({ categoryIndex: Number(e.detail.value) }); },
  time(e) { this.setData({ time: e.detail.value }); },
  date(e) { this.setData({ date: e.detail.value }); },
  repeat(e) { this.setData({ repeatIndex: Number(e.detail.value) }); },
  ahead(e) { this.setData({ aheadIndex: Number(e.detail.value) }); },
  weekdays(e) {
    this.setData({ weekdays: e.detail.value });
    this.refreshWeekdayOptions();
  },
  refreshWeekdayOptions() {
    const selected = this.data.weekdays;
    this.setData({
      weekdayOptions: WEEKDAY_LABELS.map((label, index) => ({
        label, value: String(index + 1), checked: selected.indexOf(String(index + 1)) >= 0
      }))
    });
  },
  createSchedule() {
    return ui.run(this, async () => {
      if (this.data.role !== 'PARENT') return;
      const title = this.data.title.trim();
      if (!title) throw new Error('请输入1至64字日程名称');
      const repeatType = REPEATS[this.data.repeatIndex];
      if (repeatType === 'WEEKLY' && !this.data.weekdays.length) throw new Error('请选择每周的星期');
      const body = {
        childId: this.data.childId, title,
        category: CATEGORIES[this.data.categoryIndex],
        scheduleDate: this.data.date, scheduleTime: this.data.time,
        repeatType,
        repeatWeekdays: repeatType === 'WEEKLY' ? this.data.weekdays.join(',') : '',
        remindMinutes: AHEAD_VALUES[this.data.aheadIndex]
      };
      await api.post('/schedule', body);
      this.setData({ showForm: false, title: '', weekdays: [], receipt: '日程已添加，到点会提醒' });
      this.refreshWeekdayOptions();
      await this.read();
    });
  },
  cancel(e) {
    const id = e.currentTarget.dataset.id;
    return ui.run(this, async () => {
      if (!await ui.confirm('取消后不再提醒，确定取消该日程？')) return;
      await api.post('/schedule/' + id + '/cancel', {});
      this.setData({ receipt: '日程已取消' });
      await this.read();
    });
  },
  onHide() { this.setData({ schedules: [], groups: [], receipt: '', error: '' }); }
});
