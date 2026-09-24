const api = require('../../services/api');
const ui = require('../../utils/page');
const { loadChildren, displayChildren } = require('../../services/children');
const { summarizeMedals } = require('../../utils/medal');

ui.page({
  data: {
    role: '', busy: false, error: '', receipt: '',
    children: [], childIndex: 0, childId: '', medals: [], medalEarned: 0, medalTotal: 0
  },
  onShow() {
    if (!ui.guard(this)) return;
    return ui.run(this, async () => {
      const children = displayChildren(await loadChildren());
      this.setData({ children, childId: children[0].childId, childIndex: 0 });
      await this.read();
    });
  },
  async read() {
    const medalSummary = summarizeMedals(await api.get('/medal/awards', { childId: this.data.childId }));
    this.setData(medalSummary);
  },
  child(e) {
    const index = Number(e.detail.value);
    this.setData({ childIndex: index, childId: this.data.children[index].childId });
    return ui.run(this, () => this.read());
  },
  onHide() { this.setData({ medals: [], medalEarned: 0, medalTotal: 0 }); }
});
