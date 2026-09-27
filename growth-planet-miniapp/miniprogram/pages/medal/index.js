const api = require('../../services/api');
const ui = require('../../utils/page');
const { loadChildren, displayChildren } = require('../../services/children');
const { summarizeMedals } = require('../../utils/medal');
const { shanghaiDate } = require('../../utils/domain');

ui.page({
  data: {
    role: '', busy: false, error: '', receipt: '',
    children: [], childIndex: 0, childId: '', medals: [], medalEarned: 0, medalTotal: 0,
    // 连续打卡环（30 天一轮）
    streak: 0, ringSvg: ''
  },
  onShow() {
    if (!ui.guard(this)) return;
    return ui.run(this, async () => {
      const children = displayChildren(await loadChildren());
      this.setData({ children, childId: children[0].childId, childIndex: 0 });
      await this.read();
    });
  },
  buildRing(streak) {
    const days = Math.max(0, streak || 0);
    const pct = Math.min(1, (days % 30) / 30);
    const r = 45, c = 2 * Math.PI * r;
    const svg = '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 120 120">'
      + '<circle cx="60" cy="60" r="' + r + '" fill="none" stroke="#f0e6cf" stroke-width="10"/>'
      + '<circle cx="60" cy="60" r="' + r + '" fill="none" stroke="#e8a013" stroke-width="10" stroke-linecap="round"'
      + ' stroke-dasharray="' + c + '" stroke-dashoffset="' + (c * (1 - pct)) + '" transform="rotate(-90 60 60)"/>'
      + '<text x="60" y="67" text-anchor="middle" font-size="34" font-weight="700" fill="#8a6a1e" font-family="sans-serif">' + days + '</text>'
      + '<text x="60" y="86" text-anchor="middle" font-size="12" fill="#b08a3a" font-family="sans-serif">天</text>'
      + '</svg>';
    return 'data:image/svg+xml,' + encodeURIComponent(svg);
  },
  async read() {
    const [medalSummary, calendar] = await Promise.all([
      api.get('/medal/awards', { childId: this.data.childId }).then(summarizeMedals),
      api.get('/child/check-in/calendar', { month: shanghaiDate().slice(0, 7) }).catch(() => null)
    ]);
    const streak = calendar ? (calendar.currentStreak || 0) : 0;
    this.setData(Object.assign({}, medalSummary, { streak, ringSvg: this.buildRing(streak) }));
  },
  child(e) {
    const index = Number(e.detail.value);
    this.setData({ childIndex: index, childId: this.data.children[index].childId });
    return ui.run(this, () => this.read());
  },
  onHide() { this.setData({ medals: [], medalEarned: 0, medalTotal: 0, streak: 0, ringSvg: '' }); }
});
