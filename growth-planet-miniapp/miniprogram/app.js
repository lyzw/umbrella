const session = require('./services/session');
App({
  globalData: { wxLoginCode: '', postLogout: false },
  onLaunch() {
    session.restore();
    require('./services/family').restore();
    // 预热静默登录 code（微信 code 约 5 分钟内有效）：冷启动即取，登录页不再等待 wx.login 往返。
    if (!require('./config').syntheticLogin) {
      wx.login({ success: r => { if (r.code) this.globalData.wxLoginCode = r.code; } });
    }
  },
  onHide() {
    // No child profile or cart draft is persisted when the app enters the background.
    require('./services/context').clear();
  }
});
