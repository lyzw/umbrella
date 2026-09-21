const api = require('../../services/api');
const session = require('../../services/session');
const ui = require('../../utils/page');
const CHILD_HOME_TABS = ['meal', 'growth', 'me'];
const PARENT_HOME_TABS = ['home', 'me'];
ui.page({
  data: { role: '', active: 'meal', busy: false, error: '', unread: 0, synthetic: require('../../config').syntheticLogin },
  go: ui.go,
  onLoad(query) {
    this.requestedTab = query && query.tab ? query.tab : '';
  },
  onShow() {
    if (!ui.guard(this)) return;
    const tabs = this.data.role === 'PARENT' ? PARENT_HOME_TABS : CHILD_HOME_TABS;
    const fallback = this.data.role === 'PARENT' ? 'home' : 'meal';
    const active = tabs.includes(this.requestedTab) ? this.requestedTab : fallback;
    this.requestedTab = '';
    this.setData({ active });
    ui.run(this, async () => {
      const data = await api.get('/notices/unread-count');
      this.setData({ unread: data.unreadCount });
    });
  },
  changeTab(e) {
    const key = e.detail.key;
    if (key === 'approvals') return wx.navigateTo({ url: '/pages/confirmation/index' });
    if (key === 'wallet') return wx.navigateTo({ url: '/pages/wallet/index' });
    if (key === 'chore') return wx.navigateTo({ url: '/pages/chore/index' });
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
