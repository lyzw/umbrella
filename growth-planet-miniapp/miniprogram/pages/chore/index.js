const api = require('../../services/api');
const ui = require('../../utils/page');
const { loadChildren } = require('../../services/children');
const { cents, money, shanghaiDate } = require('../../utils/domain');

const STATUS = {
  CLAIMED:   { label: '待完成', cls: 'normal' },
  SUBMITTED: { label: '待确认', cls: 'warn' },
  CONFIRMED: { label: '已完成', cls: 'ok' },
  REJECTED:  { label: '已驳回', cls: 'err' }
};
const CYCLE = { ONCE: '一次性', DAILY: '每日', WEEKLY: '每周' };

ui.page({
  data: {
    role: '', active: 'task', busy: false, error: '', receipt: '',
    children: [], childIndex: 0, childId: '',
    tasks: [], instances: [],
    showForm: false, title: '', reward: '', cycleIndex: 0,
    cycles: ['ONCE', 'DAILY', 'WEEKLY'], cycleLabels: ['一次性', '每日', '每周'],
    // 健康打卡（仅孩子端，并入「任务」tab）
    checkItems: [], doneCount: 0, totalCount: 0, streak: 0
  },
  onLoad(query) {
    this.requestedChildId = query && query.childId ? query.childId : '';
  },
  go: ui.go,
  onShow() {
    if (!ui.guard(this)) return;
    return ui.run(this, async () => {
      const children = await loadChildren();
      const requestedChildId = this.requestedChildId || this.data.childId;
      const childIndex = Math.max(0, children.findIndex(item => item.childId === requestedChildId));
      this.requestedChildId = '';
      this.setData({ children, childId: children[childIndex].childId, childIndex });
      await this.read();
      if (this.data.role === 'CHILD') await this.readChild();
    });
  },
  async read() {
    const { childId, role } = this.data;
    const tasks = (await api.get('/chore/tasks')).map(t => ({
      ...t, cycleLabel: CYCLE[t.cycle] || t.cycle
    }));
    const rawInstances = await api.get('/chore/instances', {
      childId, status: role === 'PARENT' ? 'SUBMITTED' : ''
    });
    const instances = rawInstances.map(i => ({
      ...i, statusLabel: (STATUS[i.status] || {}).label || i.status,
      statusClass: (STATUS[i.status] || {}).cls || 'normal'
    }));
    this.setData({ tasks, instances });
  },
  onTitle(e) { this.setData({ title: e.detail.value }); },
  onReward(e) { this.setData({ reward: e.detail.value }); },
  child(e) {
    const index = Number(e.detail.value);
    this.setData({ childIndex: index, childId: this.data.children[index].childId });
    return ui.run(this, () => this.read());
  },
  claim(e) {
    const id = e.currentTarget.dataset.id;
    return ui.run(this, async () => {
      await api.post('/chore/claim', { taskId: id });
      this.setData({ receipt: '已认领，完成后记得提交哦' });
      await this.read();
    });
  },
  submit(e) {
    const id = e.currentTarget.dataset.id;
    return ui.run(this, async () => {
      await api.post('/chore/submit', { instanceId: id });
      this.setData({ receipt: '已提交，等待家长确认' });
      await this.read();
    });
  },
  confirm(e) {
    const id = e.currentTarget.dataset.id;
    const version = Number(e.currentTarget.dataset.version);
    return ui.run(this, async () => {
      if (!await ui.confirm('确认完成并发放奖励？')) return;
      await api.post('/chore/confirm', { id, expectedVersion: version });
      this.setData({ receipt: '已确认，奖励已发放' });
      await this.read();
    });
  },
  reject(e) {
    const id = e.currentTarget.dataset.id;
    const version = Number(e.currentTarget.dataset.version);
    return ui.run(this, async () => {
      if (!await ui.confirm('驳回该家务？儿童可调整后重新提交')) return;
      await api.post('/chore/reject', { id, expectedVersion: version, reason: '' });
      this.setData({ receipt: '已驳回' });
      await this.read();
    });
  },
  toggleForm() {
    this.setData({ showForm: !this.data.showForm, title: '', reward: '', cycleIndex: 0 });
  },
  cycle(e) { this.setData({ cycleIndex: Number(e.detail.value) }); },
  createTask() {
    return ui.run(this, async () => {
      if (this.data.role !== 'PARENT') return;
      const title = this.data.title.trim();
      if (!title || title.length > 64) throw new Error('请输入1至64字任务名称');
      const raw = this.data.reward.trim();
      if (!raw) throw new Error('请输入奖励金额');
      const amount = cents(raw);
      if (amount > 999999) throw new Error('奖励为0.00至9999.99虚拟单位');
      const body = { title, rewardAmount: money(amount), cycle: this.data.cycles[this.data.cycleIndex], icon: '🧹' };
      await api.post('/chore/task', body);
      this.setData({ showForm: false, title: '', reward: '', receipt: '任务已添加' });
      await this.read();
    });
  },
  // 健康打卡今日视图（与 health 页同口径，并入「任务」tab，日历等深度功能仍走 health 页）。
  async readChild() {
    const [items, todayRows, calendar] = await Promise.all([
      api.get('/child/check-in/items'),
      api.get('/child/check-in/today'),
      api.get('/child/check-in/calendar', { month: shanghaiDate().slice(0, 7) })
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
        key: String(item.itemId), count, dailyTarget, reached, percent,
        targetText: dailyTarget > 0 ? count + ' / ' + dailyTarget + unit : '已打卡 ' + count + ' 次',
        limitText: dailyTarget > 0 ? '每日上限 ' + dailyTarget + unit : '不限次数',
        actionLabel: reached ? '今日已完成' : '打卡'
      });
    });
    const doneCount = checkItems.filter(item => item.dailyTarget > 0 ? item.reached : item.count > 0).length;
    this.setData({ checkItems, totalCount: checkItems.length, doneCount, streak: (calendar && calendar.currentStreak) || 0 });
  },
  checkIn(e) {
    const itemId = e.currentTarget.dataset.id;
    return ui.run(this, async () => {
      if (this.data.role !== 'CHILD') return;
      await api.post('/child/check-in?itemId=' + itemId, {});
      await this.readChild();
      const item = this.data.checkItems.find(entry => entry.key === String(itemId));
      const name = (item && item.name) || '打卡项';
      this.setData({ receipt: '「' + name + '」打卡成功，已连续打卡 ' + this.data.streak + ' 天，继续保持 💪' });
    });
  },
  changeTab(e) {
    const key = e.detail.key;
    if (key === 'task') return;
    if (['meal', 'growth', 'me'].includes(key)) {
      return wx.reLaunch({ url: '/pages/home/index?tab=' + key });
    }
  },
  onHide() { this.setData({ tasks: [], instances: [], receipt: '', error: '' }); }
});
