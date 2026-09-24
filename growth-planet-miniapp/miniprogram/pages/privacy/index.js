const api = require('../../services/api');
const ui = require('../../utils/page');
const config = require('../../config');
const { loadChildren, displayChildren } = require('../../services/children');
const { operations } = require('../../services/operations');
const { id, statusLabels } = require('../../utils/domain');
ui.page({
  data: { role: '', busy: false, error: '', children: [], childIndex: 0, childId: '', consent: null, task: null,
    taskId: '', account: '', synthetic: config.syntheticLogin, pendingExport: false, pendingDelete: false, documentText: '' },
  input: ui.input,
  onShow() {
    if (!ui.guard(this, 'PARENT')) return;
    return ui.run(this, async () => {
      const children = displayChildren(await loadChildren());
      const index = Math.max(0, children.findIndex(c => c.childId === this.data.childId));
      this.setData({ children, childIndex: index, childId: children[index].childId });
      await this.readConsent();
    });
  },
  async readConsent() {
    this.setData({ consent: null, pendingExport: !!operations.pending('export:' + this.data.childId),
      pendingDelete: !!operations.pending('delete:' + this.data.childId) });
    const consent = await api.get('/compliance/consent', { childId: this.data.childId, consentType: 'PROFILE' });
    this.setData({ consent });
  },
  child(e) {
    const index = Number(e.detail.value);
    this.setData({ childIndex: index, childId: this.data.children[index].childId, task: null, taskId: '', documentText: '', account: '' });
    return ui.run(this, () => this.readConsent());
  },
  refresh() { return ui.run(this, () => this.readConsent()); },
  revoke() {
    return ui.run(this, async () => {
      if (!this.data.consent || this.data.consent.currentStatus !== 'GRANTED') return;
      const body = { childId: this.data.childId, consentType: 'PROFILE', version: this.data.consent.version };
      if (!await ui.confirm('撤回后，相关档案、点餐和钱包服务将停止访问。撤回不会自动删除历史数据。', '撤回档案同意')) return;
      await api.post('/compliance/consent/revoke', body);
      require('../../services/context').clear();
      this.setData({ documentText: '' });
      await this.readConsent();
    });
  },
  exportRequest() {
    return ui.run(this, async () => {
      const body = { childId: this.data.childId };
      if (!await ui.confirm('申请获取当前档案、偏好及本人同意记录。当前服务不包含全量历史数据。', '申请导出')) return;
      await this.sendRequest('export', body);
    });
  },
  deleteRequest() {
    return ui.run(this, async () => {
      const childId = this.data.childId;
      if (!await ui.confirm('提交后进入人工办理，不会立即删除。需重新验证当前家长身份。', '申请删除数据')) return;
      const code = config.syntheticLogin ? this.data.account.trim() : await ui.loginCode();
      if (!code || (config.syntheticLogin && !/^[a-zA-Z0-9_-]{1,51}$/.test(code))) throw new Error('请填写当前家长的合成登录账号');
      await this.sendRequest('delete', { childId, confirmed: true, code });
    });
  },
  async sendRequest(type, body) {
    const scope = type + ':' + body.childId;
    try {
      const result = await operations.run(scope, body, (value, key) => api.post('/compliance/data-' + type, value, key));
      this.showTask(result);
    } finally {
      this.setData({ account: '', pendingExport: !!operations.pending('export:' + body.childId),
        pendingDelete: !!operations.pending('delete:' + body.childId) });
    }
  },
  retry(e) {
    return ui.run(this, async () => {
      const type = e.currentTarget.dataset.type;
      if (!['export', 'delete'].includes(type)) return;
      const pending = operations.pending(type + ':' + this.data.childId);
      if (pending) await this.sendRequest(type, pending.body);
    });
  },
  showTask(task) {
    this.setData({ task: { ...task, label: statusLabels[task.status] || task.status }, taskId: task.taskId, documentText: '' });
  },
  query() {
    return ui.run(this, async () => {
      const taskId = id(this.data.taskId.trim());
      this.setData({ task: null, documentText: '' });
      this.showTask(await api.get('/compliance/requests/' + taskId));
    });
  },
  document() {
    return ui.run(this, async () => {
      const task = this.data.task;
      if (!task || !task.downloadAvailable) throw new Error('该申请尚不能查看导出内容');
      if (!await ui.confirm('导出含儿童资料，请确认周围环境安全。离开本页后将清空展示。', '查看敏感资料')) return;
      const document = await api.getDocument('/compliance/requests/' + id(task.taskId) + '/download');
      this.setData({ documentText: JSON.stringify(document, null, 2) });
    });
  },
  closeDocument() { this.setData({ documentText: '' }); },
  onHide() { this.setData({ consent: null, account: '', documentText: '', task: null }); }
});
