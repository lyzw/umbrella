const config = require('../../config');
const session = require('../../services/session');
const ui = require('../../utils/page');

// 环境切换页：仅开发版/体验版可用（入口见「设置」页）。
// 环境在 config.js require 时就固定了，因此这里写完缓存后必须重启小程序才生效。
ui.page({
  data: {
    effective: '',      // 本次启动实际生效的环境
    effectiveApi: '',   // 生效环境的后端地址
    pending: '',        // 已选择、待重启生效的环境
    options: [],        // 可选项（来自 config.envOptions）
    isRelease: false,
    error: ''
  },
  onShow() {
    const pending = this.readPending();
    this.setData({
      effective: config.envName,
      effectiveApi: config.apiBase,
      pending,
      options: config.envOptions,
      isRelease: !!config.isRelease
    });
  },
  readPending() {
    try {
      const value = wx.getStorageSync(config.envStorageKey);
      return value && value !== config.envName ? value : '';
    } catch (e) {
      return '';
    }
  },
  // 选择环境：确认后写缓存并清登录态（不同环境的 token 不通用）。
  pickEnv(e) {
    const name = (e.currentTarget && e.currentTarget.dataset ? e.currentTarget.dataset.env : '') || '';
    if (!name) return;
    if (config.isRelease) {
      this.setData({ error: '正式版固定使用生产环境，不可切换' });
      return;
    }
    const option = config.envOptions.filter(item => item.name === name)[0];
    if (!option || name === config.envName) return;
    const self = this;
    wx.showModal({
      title: '切换到「' + option.label + '」',
      content: '后端接口将改为 ' + option.apiBase + '。会同时清除当前登录状态，重启小程序后生效。',
      confirmText: '切换',
      success(result) {
        if (!result.confirm) return;
        try { wx.setStorageSync(config.envStorageKey, name); } catch (err) { self.setData({ error: '环境保存失败，请重试' }); return; }
        session.clear();
        self.setData({ pending: name, error: '' });
        wx.showModal({ title: '已保存', content: '请关闭并重新打开小程序，以使用新环境的后端接口。', showCancel: false });
      }
    });
  },
  // 清除切换缓存：回落到 config.local.js 或 DEFAULT_ENV 指定的环境。
  resetEnv() {
    const self = this;
    wx.showModal({
      title: '恢复默认环境',
      content: '将清除本机的环境切换记录，回落到代码默认环境（当前为 ' + config.envName + '），重启小程序后生效。',
      confirmText: '清除',
      success(result) {
        if (!result.confirm) return;
        try { wx.removeStorageSync(config.envStorageKey); } catch (err) { self.setData({ error: '清除失败，请重试' }); return; }
        session.clear();
        self.setData({ pending: '', error: '' });
      }
    });
  }
});
