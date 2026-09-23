const api = require('../../services/api');
const ui = require('../../utils/page');
const { loadChildren } = require('../../services/children');
const { operations } = require('../../services/operations');
const context = require('../../services/context');
const { statusLabels, safeDish, dishRef, dishKey, id } = require('../../utils/domain');
const approvalFilters = [
  { value: 'PENDING', label: '待我处理' },
  { value: 'COMPLETED', label: '已完成' },
  { value: 'REJECTED', label: '需调整' },
  { value: 'CANCELLED', label: '已撤回' }
];
const childStatusLabels = ['待确认', '已完成', '需调整', '已撤回'];
const approvalStatusValues = approvalFilters.map(item => item.value);
ui.page({
  data: { role: '', busy: false, error: '', children: [], childId: '', childIndex: 0, records: [], page: 1, total: 0,
    pendingTotal: 0, listHeading: '待我处理', listDescription: '正在加载待处理餐单',
    status: 'PENDING', statuses: approvalFilters.map(item => item.label), statusIndex: 0,
    detail: null, draft: null, remark: '', reason: '', preview: null, suggestions: [], editing: false,
    showAdjustment: false, pendingSubmit: false, pendingApprove: false, pendingDecision: false },
  input: ui.input,
  onLoad(query) { this.query = query; },
  onShow() {
    if (!ui.guard(this)) return;
    this.visible = true;
    return ui.run(this, async () => {
      const children = await loadChildren();
      const draft = context.takeCart();
      const requested = children.find(item => item.childId === (this.query && this.query.childId));
      const existing = children.find(item => item.childId === this.data.childId);
      const childId = draft ? draft.childId : (requested || existing || children[0]).childId;
      if (this.query) this.query.childId = '';
      this.setData({ children, childId, childIndex: Math.max(0, children.findIndex(c => c.childId === childId)),
        statuses: this.data.role === 'PARENT' ? approvalFilters.map(item => item.label) : childStatusLabels,
        draft, pendingSubmit: !!operations.pending('submit:' + childId) });
      if (this.query && this.query.id) { const confirmId = id(this.query.id); this.query.id = null; await this.readDetail(confirmId); }
      else if (!draft) await this.readList();
    });
  },
  async readList(recoverPage = true) {
    const { childId, page, status, role } = this.data;
    const pageSize = 20;
    this.setData({ records: [] });
    const result = await api.get(role === 'PARENT' ? '/parent/approvals' : '/menu/confirms', { childId, page, pageSize, status });
    const items = result && Array.isArray(result.items) ? result.items : [];
    const rawTotal = Number(result && result.total);
    const total = Number.isFinite(rawTotal) && rawTotal >= 0 ? Math.floor(rawTotal) : 0;
    const lastPage = Math.max(1, Math.ceil(total / pageSize));
    if (recoverPage && page > lastPage) {
      this.setData({ page: lastPage });
      return this.readList(false);
    }
    const filter = approvalFilters.find(item => item.value === status) || approvalFilters[0];
    const listHeading = role === 'PARENT'
      ? filter.label
      : this.data.statuses[this.data.statusIndex] || statusLabels[status] || '确认记录';
    const listDescription = role === 'PARENT'
      ? (status === 'PENDING' ? `还有 ${total} 份餐单待你处理` : `共 ${total} 份${filter.label}餐单`)
      : `共 ${total} 份确认记录`;
    this.setData({
      records: items.map(item => ({ ...item, label: statusLabels[item.status] || item.status })),
      total,
      pendingTotal: role === 'PARENT' && status === 'PENDING' ? total : this.data.pendingTotal,
      listHeading,
      listDescription
    });
  },
  async readDetail(confirmId) {
    const detail = await api.get('/menu/confirm/' + confirmId);
    const items = (detail.items || []).map(item => ({ ...item, key: dishKey(item) }));
    this.setData({ detail: { ...detail, items, label: statusLabels[detail.status] }, draft: null, preview: null,
      editing: false, showAdjustment: false,
      pendingApprove: !!operations.pending('approve:' + confirmId), pendingDecision: !!operations.pending('decision:' + confirmId) });
    this.poll();
  },
  poll() {
    clearTimeout(this.timer);
    if (!this.visible || this.data.role !== 'CHILD' || !this.data.detail || this.data.detail.status !== 'PENDING') return;
    this.timer = setTimeout(async () => {
      if (!this.visible || this.data.busy || !this.data.detail) return this.poll();
      try {
        const status = await api.get('/menu/confirm/status', { confirmId: this.data.detail.confirmId });
        if (!this.visible) return;
        if (status.status !== this.data.detail.status || status.version !== this.data.detail.version) {
          await ui.run(this, () => this.readDetail(status.confirmId));
        } else this.poll();
      } catch (error) {
        await ui.run(this, () => Promise.reject(error));
        // A failing poll stops; only an explicit refresh restarts it.
      }
    }, 5000);
  },
  open(e) { ui.run(this, () => this.readDetail(e.currentTarget.dataset.id)); },
  refresh() { return ui.run(this, () => this.data.detail ? this.readDetail(this.data.detail.confirmId) : this.readList()); },
  child(e) {
    const index = Number(e.detail.value);
    const selected = this.data.children[index];
    if (!selected) return;
    this.setData({ childIndex: index, childId: selected.childId, page: 1, detail: null,
      pendingSubmit: !!operations.pending('submit:' + selected.childId) });
    return ui.run(this, () => this.readList());
  },
  filter(e) {
    const index = Number(e.detail.value);
    const status = approvalStatusValues[index];
    if (!status) return;
    this.setData({ statusIndex: index, status, page: 1 });
    return ui.run(this, () => this.readList());
  },
  next(e) {
    const delta = Number(e.currentTarget.dataset.delta);
    const nextPage = this.data.page + delta;
    if (![-1, 1].includes(delta) || nextPage < 1) return;
    if (delta > 0 && this.data.page * 20 >= this.data.total) return;
    this.setData({ page: nextPage });
    return ui.run(this, () => this.readList());
  },
  submit() {
    return ui.run(this, async () => {
      const draft = this.data.draft;
      if (!draft || this.data.role !== 'CHILD') return;
      const body = { menuId: draft.menuId, items: draft.items.map(item => ({ dishRef: item.dishRef, quantity: item.quantity })),
        remark: this.data.remark };
      if (draft.previousConfirmId) body.previousConfirmId = draft.previousConfirmId;
      await this.submitBody(body);
    });
  },
  async submitBody(body) {
    const scope = 'submit:' + this.data.childId;
    try {
      const result = await operations.run(scope, body, (value, key) => api.post('/menu/confirm', value, key));
      this.setData({ draft: null, remark: '', pendingSubmit: false });
      await this.readDetail(result.confirmId);
    } finally { this.setData({ pendingSubmit: !!operations.pending(scope) }); }
  },
  retrySubmit() {
    return ui.run(this, async () => {
      const pending = operations.pending('submit:' + this.data.childId);
      if (pending) await this.submitBody(pending.body);
    });
  },
  approve() {
    return ui.run(this, async () => {
      const detail = this.data.detail;
      if (!await ui.confirm('同意后将从虚拟零花钱中扣除 ' + detail.totalAmount + '，请确认餐食和金额。', '同意并记账')) return;
      await this.approveBody({ expectedVersion: detail.version });
    });
  },
  async approveBody(body) {
    const confirmId = this.data.detail.confirmId, scope = 'approve:' + confirmId;
    try {
      const result = await operations.run(scope, body, value => api.post('/parent/approve/' + confirmId + '/approve', value));
      this.setData({ preview: null, pendingApprove: false, showAdjustment: false,
        detail: { ...result, label: statusLabels[result.status] } });
    } catch (error) {
      if (error.code === 'E-011') { this.setData({ preview: error.data }); return; }
      throw error;
    } finally { this.setData({ pendingApprove: !!operations.pending(scope) }); }
  },
  explicitApprove() {
    return ui.run(this, async () => {
      const preview = this.data.preview;
      if (!preview || !await ui.confirm('这次确认会超出当前额度，仍要批准 ' + preview.totalAmount + ' 虚拟单位吗？', '再次确认超额')) return;
      await this.approveBody({ expectedVersion: preview.confirmVersion, explicitConfirm: true,
        walletVersion: preview.walletVersion, ruleVersion: preview.ruleVersion, confirmVersion: preview.confirmVersion, usageDate: preview.usageDate });
    });
  },
  retryApprove() { return ui.run(this, async () => {
    const pending = operations.pending('approve:' + this.data.detail.confirmId);
    if (pending) await this.approveBody(pending.body);
  }); },
  reject() { return ui.run(this, () => this.decide('reject')); },
  async decide(action, original) {
    const detail = this.data.detail;
    const body = original ? original.body : { expectedVersion: detail.version, reason: this.data.reason.trim() };
    if (!original && !body.reason) throw new Error('请填写给孩子的温和说明');
    if (!original && action === 'modify') {
      body.items = this.data.suggestions.filter(d => d.quantity > 0).map(d => ({ dishRef: dishRef(d), quantity: d.quantity }));
      if (!body.items.length || body.items.length > 20) throw new Error('请选择1至20种建议餐食');
    }
    if (!original && !await ui.confirm('说明会展示给孩子，原单将标记为需调整，不会扣款。')) return;
    const command = original || { action, body };
    const scope = 'decision:' + detail.confirmId;
    try {
      const result = await operations.run(scope, command, value =>
        api.post('/parent/approve/' + detail.confirmId + '/' + value.action, value.body));
      this.setData({ detail: { ...result, label: statusLabels[result.status] }, editing: false, showAdjustment: false,
        suggestions: [], reason: '', preview: null });
    } finally { this.setData({ pendingDecision: !!operations.pending(scope) }); }
  },
  retryDecision() { return ui.run(this, async () => {
    const pending = operations.pending('decision:' + this.data.detail.confirmId);
    if (pending) await this.decide(pending.body.action, pending.body);
  }); },
  toggleAdjustment() {
    this.setData({ showAdjustment: !this.data.showAdjustment });
  },
  editSuggestion() {
    return ui.run(this, async () => {
      const detail = this.data.detail;
      const menu = await api.get('/menu/daily', { sourceType: 'FAMILY', menuDate: detail.menuDate, mealType: detail.mealType, childId: detail.childId });
      // Parent previews never set canSelect; suggestions still enforce dish safety.
      this.setData({ editing: true, suggestions: menu.dishes.filter(safeDish).map(d => ({ ...d, quantity: 0, key: dishKey(d) })) });
    });
  },
  suggestion(e) {
    const key = e.currentTarget.dataset.key;
    this.setData({ suggestions: this.data.suggestions.map(d => d.key === key
      ? { ...d, quantity: Math.max(0, Math.min(9, d.quantity + Number(e.currentTarget.dataset.delta))) } : d) });
  },
  modify() { return ui.run(this, () => this.decide('modify')); },
  withdraw() {
    return ui.run(this, async () => {
      if (!await ui.confirm('确认撤回这份餐单？')) return;
      const detail = this.data.detail;
      const result = await api.post('/menu/confirm/' + detail.confirmId + '/withdraw', { expectedVersion: detail.version });
      this.setData({ detail: { ...result, label: statusLabels[result.status] } });
      clearTimeout(this.timer);
    });
  },
  resubmit() { wx.navigateTo({ url: '/pages/menu/index?previousConfirmId=' + this.data.detail.confirmId }); },
  backList() {
    clearTimeout(this.timer);
    this.setData({ detail: null, draft: null, preview: null, showAdjustment: false, editing: false, suggestions: [], reason: '' });
    ui.run(this, () => this.readList());
  },
  onHide() {
    this.visible = false;
    clearTimeout(this.timer);
    this.setData({ draft: null, detail: null, records: [], suggestions: [], preview: null, remark: '', reason: '',
      showAdjustment: false, editing: false });
  },
  onUnload() { this.onHide(); }
});
