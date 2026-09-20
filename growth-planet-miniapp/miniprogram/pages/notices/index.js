const api = require('../../services/api');
const ui = require('../../utils/page');
const titles = { CONFIRM_SUBMIT: '有新的餐单待确认', CONFIRM_COMPLETED: '餐单已确认',
  CONFIRM_REJECTED: '餐单需要调整', CONFIRM_MODIFIED: '家长提供了餐食建议', CONFIRM_CANCELLED: '餐单已撤回' };
ui.page({
  data: { role: '', busy: false, error: '', records: [], page: 1, total: 0, unreadOnly: false },
  onShow() { if (ui.guard(this)) return this.refresh(); },
  refresh() {
    return ui.run(this, async () => {
      const result = await api.get('/notices', { page: this.data.page, pageSize: 20, unreadOnly: this.data.unreadOnly });
      this.setData({ records: result.items.map(item => ({ ...item, title: titles[item.eventType] || '家庭消息更新' })), total: result.total });
    });
  },
  filter(e) { this.setData({ unreadOnly: e.detail.value, page: 1, records: [] }); return this.refresh(); },
  next(e) { this.setData({ page: this.data.page + Number(e.currentTarget.dataset.delta), records: [] }); return this.refresh(); },
  read(e) {
    return ui.run(this, async () => {
      await api.post('/notices/' + e.currentTarget.dataset.id + '/read', {});
      this.setData({ records: this.data.records.map(item => item.id === e.currentTarget.dataset.id ? { ...item, read: true } : item) });
    });
  },
  go: ui.go,
  onHide() { this.setData({ records: [] }); }
});
