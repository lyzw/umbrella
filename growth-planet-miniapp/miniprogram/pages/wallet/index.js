const api = require('../../services/api');
const ui = require('../../utils/page');
const { loadChildren } = require('../../services/children');
const { operations } = require('../../services/operations');
const { cents, money, shanghaiDate } = require('../../utils/domain');
ui.page({
  data: { role: '', busy: false, error: '', children: [], childIndex: 0, childId: '', wallet: null, rule: null,
    singleLimit: '', dailyLimit: '', weeklyLimit: '', amount: '', reason: '', pendingGrant: false,
    records: [], page: 1, total: 0, startDate: '', endDate: '', receipt: '' },
  input: ui.input,
  onShow() {
    if (!ui.guard(this)) return;
    return ui.run(this, async () => {
      const children = await loadChildren();
      const index = Math.max(0, children.findIndex(c => c.childId === this.data.childId));
      this.setData({ children, childIndex: index, childId: children[index].childId,
        startDate: shanghaiDate(new Date(Date.now() - 30 * 86400000)), endDate: shanghaiDate(), page: 1 });
      await this.read();
    });
  },
  async read() {
    this.setData({ wallet: null, rule: null, records: [], pendingGrant: !!operations.pending('grant:' + this.data.childId) });
    const query = { childId: this.data.childId };
    const wallet = await api.get('/wallet/balance', query);
    const rule = await api.get('/wallet/allowance-rule', query);
    this.setData({ wallet, rule, singleLimit: rule.singleLimit, dailyLimit: rule.dailyLimit, weeklyLimit: rule.weeklyLimit });
    await this.readLogs();
  },
  async readLogs() {
    const { childId, startDate, endDate, page } = this.data;
    const days = (Date.parse(endDate) - Date.parse(startDate)) / 86400000;
    if (!Number.isInteger(days) || days < 0 || days > 30) throw new Error('请选择连续1至31天的流水日期');
    const result = await api.get('/wallet/allowance-log', { childId, startDate, endDate, page, pageSize: 20 });
    this.setData({ records: result.items, total: result.total });
  },
  child(e) {
    const index = Number(e.detail.value);
    this.setData({ childIndex: index, childId: this.data.children[index].childId, page: 1, amount: '', reason: '', receipt: '' });
    return this.refresh();
  },
  date(e) { this.setData({ [e.currentTarget.dataset.field]: e.detail.value, page: 1, records: [] }); },
  logs() { this.setData({ page: 1, records: [] }); return ui.run(this, () => this.readLogs()); },
  next(e) { this.setData({ page: this.data.page + Number(e.currentTarget.dataset.delta), records: [] }); return ui.run(this, () => this.readLogs()); },
  refresh() { return ui.run(this, () => this.read()); },
  grant() {
    return ui.run(this, async () => {
      if (this.data.role !== 'PARENT') return;
      const amount = cents(this.data.amount), reason = this.data.reason.trim();
      if (amount < 1 || amount > 999999) throw new Error('每次发放为0.01至9999.99虚拟单位');
      if (!reason || reason.length > 100) throw new Error('请填写1至100字的发放原因');
      const body = { childId: this.data.childId, amount: money(amount), reason };
      if (!await ui.confirm('向儿童 ' + body.childId + ' 发放 ' + body.amount + ' 虚拟单位？', '确认发放')) return;
      await this.grantBody(body);
    });
  },
  async grantBody(body) {
    const scope = 'grant:' + body.childId;
    try {
      const result = await operations.run(scope, body, (value, key) => api.post('/wallet/grant', value, key));
      this.setData({ amount: '', reason: '', receipt: '发放成功，流水号 ' + result.logId, pendingGrant: false });
      await this.read();
    } finally { this.setData({ pendingGrant: !!operations.pending(scope) }); }
  },
  retryGrant() {
    return ui.run(this, async () => {
      const pending = operations.pending('grant:' + this.data.childId);
      if (pending) await this.grantBody(pending.body);
    });
  },
  saveRule() {
    return ui.run(this, async () => {
      if (this.data.role !== 'PARENT' || !this.data.rule) return;
      const single = cents(this.data.singleLimit), daily = cents(this.data.dailyLimit), weekly = cents(this.data.weeklyLimit);
      if (single > daily || daily > weekly || weekly > 999999) throw new Error('额度需满足：单笔 ≤ 每日 ≤ 每周 ≤ 9999.99');
      const body = { childId: this.data.childId, singleLimit: money(single), dailyLimit: money(daily),
        weeklyLimit: money(weekly), expectedVersion: this.data.rule.version };
      if (!await ui.confirm('保存新的虚拟零花钱额度？')) return;
      try {
        const rule = await api.put('/wallet/allowance-rule', body);
        this.setData({ rule, receipt: '额度已保存' });
      } catch (error) {
        // Never silently reapply changed limits with a newer version.
        this.setData({ rule: null });
        if (error.code === 'E-007' || error.unknown) error.message = '额度可能已变化，请刷新后核对，再决定是否修改';
        throw error;
      }
    });
  },
  onHide() { this.setData({ wallet: null, rule: null, records: [], amount: '', reason: '', singleLimit: '', dailyLimit: '', weeklyLimit: '', receipt: '' }); }
});
