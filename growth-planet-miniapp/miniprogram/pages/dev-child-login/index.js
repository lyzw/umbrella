const api = require('../../services/api');
const session = require('../../services/session');
const config = require('../../config');
const ui = require('../../utils/page');

ui.page({
  data: {
    synthetic: config.syntheticLogin,
    childAccount: 'child_demo',
    parentAccount: 'parent_demo',
    childReady: false,
    result: null,
    error: ''
  },
  input: ui.input,
  onShow() {
    const auth = session.get();
    if (auth && auth.role === 'CHILD') this.setData({ childReady: true });
  },
  // ① 儿童一键登录：合成 code -> wx-login -> select-role(CHILD)
  childLogin() {
    return ui.run(this, async () => {
      const code = this.data.childAccount.trim();
      if (!/^[a-zA-Z0-9_-]{1,51}$/.test(code)) throw new Error('儿童账号为1至51位字母、数字、下划线或短横线');
      await api.post('/auth/wx-login', { code });
      const selected = await api.post('/auth/select-role', { role: 'CHILD' });
      session.set(selected);
      this.setData({ childReady: true, result: null });
    });
  },
  // ② 输入家长账号一键绑定：调用 dev-only 端点，原子完成建家庭 + join + 审批 + 刷新儿童 token
  bindParent() {
    return ui.run(this, async () => {
      if (!session.get() || session.get().role !== 'CHILD') throw new Error('请先以儿童身份一键登录');
      const parentAccount = this.data.parentAccount.trim();
      if (!/^[a-zA-Z0-9_-]{1,51}$/.test(parentAccount)) throw new Error('家长账号为1至51位字母、数字、下划线或短横线');
      const resp = await api.post('/dev/quick-bind-parent', { parentAccount });
      session.set({ token: resp.token, role: resp.role, expiresIn: resp.expiresIn });
      this.setData({ result: { familyId: resp.familyId, bindStatus: resp.bindStatus } });
    });
  },
  // 一键登录并绑定（连续执行两步）
  quickAll() {
    return ui.run(this, async () => {
      const code = this.data.childAccount.trim();
      if (!/^[a-zA-Z0-9_-]{1,51}$/.test(code)) throw new Error('儿童账号为1至51位字母、数字、下划线或短横线');
      await api.post('/auth/wx-login', { code });
      const selected = await api.post('/auth/select-role', { role: 'CHILD' });
      session.set(selected);
      const parentAccount = this.data.parentAccount.trim();
      if (!/^[a-zA-Z0-9_-]{1,51}$/.test(parentAccount)) throw new Error('家长账号为1至51位字母、数字、下划线或短横线');
      const resp = await api.post('/dev/quick-bind-parent', { parentAccount });
      session.set({ token: resp.token, role: resp.role, expiresIn: resp.expiresIn });
      this.setData({ childReady: true, result: { familyId: resp.familyId, bindStatus: resp.bindStatus } });
      wx.showToast({ title: '已绑定，进入首页', icon: 'success' });
      setTimeout(() => wx.reLaunch({ url: '/pages/home/index' }), 800);
    });
  }
});
