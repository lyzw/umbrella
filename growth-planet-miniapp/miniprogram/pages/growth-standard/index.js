const api = require('../../services/api');
const ui = require('../../utils/page');

ui.page({
  data: {
    role: '', busy: false, error: '',
    ageBand: 'WHO 2007', gender: 'BOY', metric: 'HEIGHT',
    rows: [], unit: ''
  },
  onShow() {
    if (!ui.guard(this, 'PARENT')) return;
    return this.refresh();
  },
  refresh() {
    return ui.run(this, async () => {
      const resp = await api.get('/growth/standard', {
        ageBand: this.data.ageBand, gender: this.data.gender, metric: this.data.metric
      });
      this.setData({ rows: resp.rows, unit: resp.unit });
    });
  },
  pickBand(e) {
    this.setData({ ageBand: e.currentTarget.dataset.band });
    return this.refresh();
  },
  pickGender(e) {
    this.setData({ gender: e.currentTarget.dataset.gender });
    return this.refresh();
  },
  pickMetric(e) {
    this.setData({ metric: e.currentTarget.dataset.metric });
    return this.refresh();
  }
});
