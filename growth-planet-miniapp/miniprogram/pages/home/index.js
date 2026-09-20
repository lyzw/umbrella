const api = require('../../services/api');
const session = require('../../services/session');
const ui = require('../../utils/page');
ui.page({
  data: { role: '', active: 'meal', busy: false, error: '', unread: 0, synthetic: require('../../config').syntheticLogin },
  go: ui.go,
  onShow() {
    if (!ui.guard(this)) return;
    this.setData({ active: this.data.role === 'PARENT' ? 'home' : 'meal' });
    ui.run(this, async () => {
      const data = await api.get('/notices/unread-count');
      this.setData({ unread: data.unreadCount });
    });
  },
  changeTab(e) {
    const key = e.detail.key;
    if (key === 'approvals') return wx.navigateTo({ url: '/pages/confirmation/index' });
    if (key === 'wallet') return wx.navigateTo({ url: '/pages/wallet/index' });
    this.setData({ active: key });
  },
  logout() {
    return ui.run(this, async () => {
      if (!await ui.confirm('退出会清除本机登录状态及未保存内容。')) return;
      await api.post('/auth/logout', {});
      session.clear();
      wx.reLaunch({ url: '/pages/login/index' });
    });
  }
});
