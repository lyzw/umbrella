const api = require('../../services/api');
const session = require('../../services/session');
const config = require('../../config');
const ui = require('../../utils/page');
ui.page({
  data: { busy: false, error: '', selecting: false, synthetic: config.syntheticLogin, account: 'parent_demo', accepted: false },
  input: ui.input,
  onShow() {
    const auth = session.get();
    if (auth && auth.role !== 'UNSELECTED') return wx.reLaunch({ url: '/pages/home/index' });
    this.setData({ selecting: !!auth });
  },
  accept(e) { this.setData({ accepted: e.detail.value.includes('yes') }); },
  login() {
    return ui.run(this, async () => {
      if (!this.data.accepted) throw new Error('请先阅读并确认账号处理提示');
      if (!config.syntheticLogin && !config.realDataApproved) throw new Error('真实账号服务尚未开放');
      const code = config.syntheticLogin ? this.data.account.trim() : await ui.loginCode();
      if (config.syntheticLogin && !/^[a-zA-Z0-9_-]{1,51}$/.test(code)) throw new Error('演示账号为1至51位字母、数字、下划线或短横线');
      const auth = await api.post('/auth/wx-login', { code });
      session.set(auth);
      if (auth.role === 'UNSELECTED') this.setData({ selecting: true });
      else wx.reLaunch({ url: '/pages/home/index' });
    });
  },
  choose(e) {
    return ui.run(this, async () => {
      const role = e.currentTarget.dataset.role;
      if (!await ui.confirm('角色选定后不能切换。请使用家长与儿童各自独立的账号。', '确认账号角色')) return;
      const auth = await api.post('/auth/select-role', { role });
      session.set(auth);
      wx.reLaunch({ url: '/pages/family/index' });
    });
  }
});
