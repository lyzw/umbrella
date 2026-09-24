const api = require('../../services/api');
const ui = require('../../utils/page');
const { loadChildren, displayChildren } = require('../../services/children');
const { operations } = require('../../services/operations');
const { cents, money, shanghaiDate } = require('../../utils/domain');
const uCharts = require('../../libs/ucharts/u-charts');

// 流水分类：后端只回传 scene 枚举原值，中文与配色统一在此映射（F-025/F-026），避免后端硬编码展示文案。
const SCENES = {
  MENU_CONFIRM: { label: '点餐', badge: 'b-c' },
  CHORE_REWARD: { label: '家务奖励', badge: 'b-ac' },
  MANUAL: { label: '家长发放', badge: 'b-po' },
  SHOPPING: { label: '购物', badge: 'b-sh' },
  EXCHANGE: { label: '兑换', badge: 'b-ex' }
};
const SCENE_OPTIONS = [{ value: '', label: '全部分类' }]
  .concat(Object.keys(SCENES).map(value => ({ value, label: SCENES[value].label })));
const sceneMeta = scene => SCENES[scene] || { label: '其他', badge: 'b-po' };
const decorate = list => (list || []).map(item => {
  const meta = sceneMeta(item.scene);
  return Object.assign({}, item, { sceneLabel: meta.label, badgeClass: meta.badge });
});
const emptyStats = () => ({
  spendTrend: [], spendCategories: [], grantCategories: [], totalSpend: '0.00', totalGrant: '0.00'
});
ui.page({
  data: { role: '', busy: false, error: '', receipt: '', children: [], childIndex: 0, childId: '',
    overview: null, board: null, rule: null,
    stats: emptyStats(), hasTrend: false, range: 'WEEK', view: 'overview', statsTab: 'trend',
    overviewLoaded: false, statsLoaded: false, logsLoaded: false, rulesLoaded: false,
    direction: '', scene: '', sceneOptions: SCENE_OPTIONS,
    singleLimit: '', dailyLimit: '', weeklyLimit: '', amount: '', reason: '', pendingGrant: false,
    records: [], page: 1, total: 0, startDate: '', endDate: '' },
  input: ui.input,
  onShow() {
    if (!ui.guard(this)) return;
    return ui.run(this, async () => {
      const children = displayChildren(await loadChildren());
      const index = Math.max(0, children.findIndex(c => c.childId === this.data.childId));
      const startDate = this.data.startDate || shanghaiDate(new Date(Date.now() - 30 * 86400000));
      const endDate = this.data.endDate || shanghaiDate();
      this.setData({ children, childIndex: index, childId: children[index].childId,
        startDate, endDate, page: 1 });
      await this.read();
    });
  },
  async read() {
    const childId = this.data.childId;
    this.setData({ pendingGrant: !!operations.pending('grant:' + childId) });
    if (this.data.view === 'logs') return this.readLogs();
    if (this.data.view === 'rules') return this.readRules();
    return this.readOverview();
  },
  async readOverview() {
    const query = { childId: this.data.childId };
    const overview = await api.get('/wallet/overview', query);
    // 家长端看板按当前登录家长的家庭在后端派生，familyId 不接受客户端传入。
    const board = this.data.role === 'PARENT' ? await api.get('/wallet/board', {}) : null;
    this.setData({
      overview, board: board ? Object.assign({}, board, { children: displayChildren(board.children || []) }) : null,
      overviewLoaded: true
    });
    await this.readStats();
  },
  async readRules() {
    const childId = this.data.childId;
    const query = { childId };
    const rule = await api.get('/wallet/allowance-rule', query);
    this.setData({ rule, rulesLoaded: true, singleLimit: rule.singleLimit, dailyLimit: rule.dailyLimit,
      weeklyLimit: rule.weeklyLimit });
  },
  async readLogs() {
    const { childId, startDate, endDate, page, direction, scene } = this.data;
    const days = (Date.parse(endDate) - Date.parse(startDate)) / 86400000;
    if (!Number.isInteger(days) || days < 0 || days > 30) throw new Error('请选择连续1至31天的流水日期');
    const query = { childId, startDate, endDate, page, pageSize: 20 };
    if (direction) query.direction = direction;
    if (scene) query.scene = scene;
    const result = await api.get('/wallet/allowance-log', query);
    this.setData({ records: decorate(result.items), total: result.total, logsLoaded: true });
  },
  async readStats() {
    const stats = await api.get('/wallet/stats', { childId: this.data.childId, range: this.data.range });
    const spendTrend = stats.spendTrend || [];
    // 全为 0 视为无数据：展示占位态而非空图（F-025 空态要求）。
    const hasTrend = spendTrend.some(point => Number(point.amount) > 0);
    this.setData({ hasTrend, statsLoaded: true,
      stats: Object.assign({}, stats, { spendTrend,
        spendCategories: decorate(stats.spendCategories), grantCategories: decorate(stats.grantCategories) }) });
    if (hasTrend && this.data.view === 'overview' && this.data.statsTab === 'trend') this.drawTrend();
  },
  drawTrend() {
    const trend = this.data.stats.spendTrend || [];
    const categories = trend.map(point => point.label);
    const values = trend.map(point => Number(point.amount));
    wx.createSelectorQuery().select('#trendChart').fields({ node: true, size: true }).exec(result => {
      const item = result && result[0];
      if (!item || !item.node) return;
      const canvas = item.node;
      const ratio = (wx.getWindowInfo ? wx.getWindowInfo() : wx.getSystemInfoSync()).pixelRatio || 1;
      const chartColor = this.data.role === 'PARENT' ? '#15966a' : '#2878ff';
      // uCharts 以画布物理像素为绘制坐标系（pixelRatio 用于触摸坐标换算），故此处传物理尺寸。
      canvas.width = item.width * ratio;
      canvas.height = item.height * ratio;
      this.chart = new uCharts({
        type: 'column', context: canvas.getContext('2d'), canvas2d: true, pixelRatio: ratio,
        width: item.width * ratio, height: item.height * ratio,
        categories, series: [{ name: '支出', data: values }], color: [chartColor],
        animation: true, dataLabel: true, legend: { show: false }, padding: [16, 16, 14, 8],
        xAxis: { disableGrid: true, fontColor: '#667085' },
        yAxis: { gridType: 'dash', gridColor: '#eef2f7', data: [{ min: 0 }] }
      });
    });
  },
  changeView(e) {
    const view = e.currentTarget.dataset.view;
    if (!['overview', 'logs', 'rules'].includes(view) || view === this.data.view) return;
    if (!this.data.childId) return;
    this.setData({ view, error: '' });
    const loaded = view === 'overview' ? this.data.overviewLoaded && this.data.statsLoaded
      : view === 'logs' ? this.data.logsLoaded : this.data.rulesLoaded;
    if (loaded) {
      if (view === 'overview' && this.data.hasTrend && this.data.statsTab === 'trend') this.drawTrend();
      return;
    }
    return ui.run(this, () => this.read());
  },
  changeStats(e) {
    const statsTab = e.currentTarget.dataset.tab;
    if (!['trend', 'category'].includes(statsTab)) return;
    this.setData({ statsTab }, () => {
      if (statsTab === 'trend' && this.data.hasTrend) this.drawTrend();
    });
  },
  range(e) {
    const range = e.currentTarget.dataset.range;
    if (!['WEEK', 'MONTH'].includes(range) || range === this.data.range) return;
    this.setData({ range, statsLoaded: false });
    return ui.run(this, () => this.readStats());
  },
  filter(e) {
    const { field, value } = e.currentTarget.dataset;
    this.setData({ [field]: value, page: 1, records: [] });
    return ui.run(this, () => this.readLogs());
  },
  child(e) {
    const index = Number(e.detail.value);
    const selected = this.data.children[index];
    if (!selected) return;
    this.chart = null;
    this.setData({ childIndex: index, childId: selected.childId, page: 1, amount: '', reason: '', receipt: '',
      overview: null, board: null, rule: null, records: [], total: 0, stats: emptyStats(), hasTrend: false,
      overviewLoaded: false, statsLoaded: false, logsLoaded: false, rulesLoaded: false });
    return this.refresh();
  },
  date(e) { this.setData({ [e.currentTarget.dataset.field]: e.detail.value, page: 1, records: [] }); },
  logs() { this.setData({ page: 1, records: [] }); return ui.run(this, () => this.readLogs()); },
  next(e) { this.setData({ page: this.data.page + Number(e.currentTarget.dataset.delta), records: [] }); return ui.run(this, () => this.readLogs()); },
  refresh() { return ui.run(this, () => this.read()); },
  changeTab(e) {
    const key = e.detail.key;
    if (this.data.role !== 'PARENT' || key === 'wallet') return;
    if (['home', 'me', 'approvals'].includes(key)) return ui.openTab(this, key);
  },
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
  onHide() {
    this.chart = null;
    this.setData({ overview: null, board: null, rule: null, records: [], amount: '', reason: '',
      singleLimit: '', dailyLimit: '', weeklyLimit: '', receipt: '',
      stats: emptyStats(), hasTrend: false, view: 'overview', statsTab: 'trend',
      overviewLoaded: false, statsLoaded: false, logsLoaded: false, rulesLoaded: false,
      direction: '', scene: '' });
  }
});
