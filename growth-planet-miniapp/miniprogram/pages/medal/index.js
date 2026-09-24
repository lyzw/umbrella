const api = require('../../services/api');
const ui = require('../../utils/page');
const { loadChildren, displayChildren } = require('../../services/children');

ui.page({
  data: {
    role: '', busy: false, error: '', receipt: '',
    children: [], childIndex: 0, childId: '', medals: []
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
    const medals = (await api.get('/medal/awards', { childId: this.data.childId })).map(item => ({
      ...item,
      definitionId: item.definition.definitionId
    }));
    this.setData({ medals });
  },
  child(e) {
    const index = Number(e.detail.value);
    this.setData({ childIndex: index, childId: this.data.children[index].childId });
    return ui.run(this, () => this.read());
  },
  onHide() { this.setData({ medals: [] }); }
});
