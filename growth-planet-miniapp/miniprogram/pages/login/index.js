const api = require('../../services/api');
const session = require('../../services/session');
const silent = require('../../services/silent');
const config = require('../../config');
const ui = require('../../utils/page');

const DEFAULT_DEMO_ACCOUNT = 'parent_demo';

ui.page({
  data: {
    busy: false, error: '', phase: 'auto', synthetic: config.syntheticLogin,
    account: DEFAULT_DEMO_ACCOUNT, accepted: false
  },
  input: ui.input,
  onShow() {
    const auth = session.get();
    if (auth) {
      if (auth.role === 'UNSELECTED') return this.setData({ phase: 'role', error: '' });
      return wx.reLaunch({ url: '/pages/home/index' });
    }
    this.setData({ phase: 'auto', error: '' });
    return this.autoLogin();
  },
  accept(e) { this.setData({ accepted: e.detail.value.includes('yes') }); },
  // 静默登录：自动 wx.login（或用预热 code）→ 最小账号会话；老账号直达首页，新账号进入角色选择。
  async autoLogin() {
    if (this.data.busy) return;
    this.setData({ phase: 'auto', error: '', busy: true });
    try {
      let code;
      if (config.syntheticLogin) {
        code = (this.data.account || DEFAULT_DEMO_ACCOUNT).trim();
        if (!/^[a-zA-Z0-9_-]{1,51}$/.test(code)) throw new Error('演示账号为1至51位字母、数字、下划线或短横线');
      } else {
        const app = getApp();
        code = (app && app.globalData.wxLoginCode) || await silent.wxCode();
      }
      const auth = await silent.silentLogin(code);
      if (auth.role === 'UNSELECTED') this.setData({ phase: 'role', busy: false });
      else wx.reLaunch({ url: '/pages/home/index' });
    } catch (error) {
      if (error && error.cancelled) return;
      this.setData({ phase: 'error', error: error.message || '登录失败', busy: false });
    }
  },
  goManual() {
    this.setData({ phase: 'manual', error: '' });
  },
  login() {
    return ui.run(this, async () => {
      if (!this.data.accepted) throw new Error('请先阅读并确认账号处理提示');
      if (!config.syntheticLogin && !config.realDataApproved) throw new Error('真实账号服务尚未开放');
      const code = config.syntheticLogin ? this.data.account.trim() : await ui.loginCode();
      if (config.syntheticLogin && !/^[a-zA-Z0-9_-]{1,51}$/.test(code)) throw new Error('演示账号为1至51位字母、数字、下划线或短横线');
      const auth = await silent.silentLogin(code);
      if (auth.role === 'UNSELECTED') this.setData({ phase: 'role', busy: false });
      else wx.reLaunch({ url: '/pages/home/index' });
    });
  },
  choose(e) {
    return ui.run(this, async () => {
      if (!this.data.accepted) throw new Error('请先阅读并确认账号处理提示');
      const role = e.currentTarget.dataset.role;
      if (!await ui.confirm('角色选定后不能切换。请使用家长与儿童各自独立的账号。', '确认账号角色')) return;
      const auth = await api.post('/auth/select-role', { role });
      session.set(auth);
      wx.reLaunch({ url: '/pages/family/index' });
    });
  },
  goDev() {
    wx.navigateTo({ url: '/pages/dev-child-login/index' });
  }
});
