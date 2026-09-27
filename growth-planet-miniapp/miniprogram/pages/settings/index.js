const api = require('../../services/api');
const ui = require('../../utils/page');
const { shanghaiDate } = require('../../utils/domain');

ui.page({
  data: {
    role: '', busy: false, error: '',
    // 心愿菜单（基础配置，家长可编辑）
    wishDishRange: [1, 2, 3, 4, 5, 6, 7, 8, 9, 10],
    wishEnabledDraft: true, wishMaxDraft: 5, wishVersion: 0,
    wishLoaded: false, wishEnabled: false, wishMaxDishes: 0
  },
  onShow() {
    if (!ui.guard(this)) return;
    return this.loadSetting();
  },
  refresh() {
    return this.loadSetting();
  },
  loadSetting() {
    return ui.run(this, async () => {
      this.setData({ error: '' });
      if (this.data.role === 'PARENT') {
        try {
          const setting = await api.get('/parent/wish-setting');
          this.setData({
            wishLoaded: true,
            wishEnabledDraft: setting.enabled,
            wishMaxDraft: setting.maxDishes,
            wishVersion: setting.version
          });
        } catch (error) {
          if (error.cancelled || error.status === 401) throw error;
          this.setData({ error: '心愿菜单设置读取失败，请稍后重试' });
        }
      } else {
        try {
          const wish = await api.get('/child/wish-menu', { menuDate: shanghaiDate() });
          this.setData({
            wishLoaded: true,
            wishEnabled: !!(wish && wish.enabled),
            wishMaxDishes: wish ? (wish.maxDishes || 0) : 0
          });
        } catch (error) {
          if (error.cancelled || error.status === 401) throw error;
          this.setData({ wishLoaded: true });
        }
      }
    });
  },
  wishEnabled(e) {
    this.setData({ wishEnabledDraft: e.detail.value });
  },
  wishMax(e) {
    this.setData({ wishMaxDraft: this.data.wishDishRange[Number(e.detail.value)] });
  },
  saveWishSetting() {
    return ui.run(this, async () => {
      this.setData({ error: '' });
      try {
        await api.put('/parent/wish-setting', {
          maxDishes: Number(this.data.wishMaxDraft),
          enabled: this.data.wishEnabledDraft,
          expectedVersion: this.data.wishVersion
        });
        // 保存后重新读取，拿到最新版本号，避免下次并发冲突。
        const setting = await api.get('/parent/wish-setting');
        this.setData({
          wishEnabledDraft: setting.enabled,
          wishMaxDraft: setting.maxDishes,
          wishVersion: setting.version
        });
        wx.showToast({ title: '设置已保存', icon: 'success' });
      } catch (error) {
        if (error.cancelled || error.status === 401) throw error;
        this.setData({ error: '保存失败，请稍后重试' });
      }
    });
  }
});
