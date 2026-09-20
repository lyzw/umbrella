const api = require('../../services/api');
const ui = require('../../utils/page');
const { loadChildren } = require('../../services/children');

ui.page({
  data: {
    role: '', active: 'medal', busy: false, error: '', receipt: '',
    children: [], childIndex: 0, childId: '', medals: []
  },
  onShow() {
    if (!ui.guard(this)) return;
    return ui.run(this, async () => {
      const children = await loadChildren();
      this.setData({ children, childId: children[0].childId, childIndex: 0 });
      await this.read();
    });
  },
  async read() {
    const medals = await api.get('/medal/awards', { childId: this.data.childId });
    this.setData({ medals });
  },
  child(e) {
    const index = Number(e.detail.value);
    this.setData({ childIndex: index, childId: this.data.children[index].childId });
    return ui.run(this, () => this.read());
  },
  changeTab(e) {
    const key = e.detail.key;
    if (key === 'meal') return wx.navigateTo({ url: '/pages/menu/index' });
    if (key === 'wallet' || key === 'approvals') return wx.navigateTo({ url: '/pages/wallet/index' });
    if (key === 'chore') return wx.navigateTo({ url: '/pages/chore/index' });
    if (key === 'home') return wx.navigateTo({ url: '/pages/home/index' });
    this.setData({ active: key });
  },
  onHide() { this.setData({ medals: [] }); }
});
