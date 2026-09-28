const api = require('../../services/api');
const ui = require('../../utils/page');

function metricValue(item, metric) {
  if (!item) return NaN;
  if (metric === 'HEIGHT') return Number(item.heightCm);
  if (metric === 'WEIGHT') return Number(item.weightKg);
  return Number(item.bmi);
}

ui.page({
  data: {
    role: '', busy: false, error: '',
    childId: '', childName: '', latest: null, records: [],
    metricTab: 'HEIGHT', standardRows: [], gender: 'BOY'
  },
  onLoad(options) {
    this.setData({ childId: decodeURIComponent(options.childId || '') });
  },
  onShow() {
    if (!ui.guard(this, ['PARENT', 'CHILD'])) return;
    return this.refresh();
  },
  refresh() {
    return ui.run(this, async () => {
      const childId = this.data.childId;
      if (!childId) {
        this.setData({ error: '缺少儿童信息，请从家庭页进入' });
        return;
      }
      const recordsResp = await api.get('/growth/records', { childId, limit: 30 });
      const latest = recordsResp.items[0] || null;
      const gender = (latest && latest.gender) || 'BOY';
      const standardResp = await api.get('/growth/standard', {
        ageBand: 'WHO 2007', gender, metric: this.data.metricTab
      });
      this.setData({
        records: recordsResp.items, latest, gender,
        standardRows: standardResp.rows, error: ''
      });
      this.drawCurve();
    });
  },
  switchMetric(e) {
    const metric = e.currentTarget.dataset.metric;
    if (metric === this.data.metricTab) return;
    this.setData({ metricTab: metric });
    this.refresh();
  },
  openStandard() {
    ui.openPage(this, '/pages/growth-standard/index');
  },
  goEdit() {
    ui.openPage(this, '/pages/growth-record-edit/index?childId=' + encodeURIComponent(this.data.childId));
  },
  drawCurve() {
    const { records, metricTab, standardRows } = this.data;
    const points = records
      .filter(r => r.ageYears)
      .map(r => ({ x: r.ageYears, y: metricValue(r, metricTab) }))
      .filter(p => Number.isFinite(p.y))
      .sort((a, b) => a.x - b.x);
    const refs = standardRows || [];
    const hasRef = refs.length > 0;

    wx.createSelectorQuery().in(this).select('#growth-curve').fields({ node: true, size: true })
      .exec((res) => {
        if (!res || !res[0] || !res[0].node) return;
        const canvas = res[0].node;
        const width = res[0].width;
        const height = res[0].height;
        const dpr = (wx.getWindowInfo && wx.getWindowInfo().pixelRatio) || 2;
        canvas.width = width * dpr;
        canvas.height = height * dpr;
        const ctx = canvas.getContext('2d');
        ctx.scale(dpr, dpr);
        ctx.clearRect(0, 0, width, height);

        const ages = refs.map(r => r.ageYears).concat(points.map(p => p.x));
        if (!ages.length) return;
        const xMin = Math.max(0, Math.min(...ages) - 0.5);
        const xMax = Math.max(...ages) + 0.5;
        const ys = refs.flatMap(r => [Number(r.p3), Number(r.p97)])
          .concat(points.map(p => p.y));
        const rawMin = ys.length ? Math.min(...ys) : 0;
        const rawMax = ys.length ? Math.max(...ys) : 1;
        const pad = (rawMax - rawMin) * 0.15 || 1;
        const yMin = rawMin - pad;
        const yMax = rawMax + pad;
        const X = x => (x - xMin) / (xMax - xMin) * width;
        const Y = y => height - (y - yMin) / (yMax - yMin) * height;

        // 横向网格
        ctx.strokeStyle = '#eef0f4';
        ctx.lineWidth = 1;
        for (let i = 1; i < 4; i++) {
          const gy = Math.round(height * i / 4);
          ctx.beginPath();
          ctx.moveTo(0, gy);
          ctx.lineTo(width, gy);
          ctx.stroke();
        }

        // P3–P97 参考带
        if (hasRef) {
          ctx.beginPath();
          refs.forEach((r, i) => {
            const x = X(r.ageYears);
            const y = Y(Number(r.p3));
            i ? ctx.lineTo(x, y) : ctx.moveTo(x, y);
          });
          for (let i = refs.length - 1; i >= 0; i--) {
            const r = refs[i];
            ctx.lineTo(X(r.ageYears), Y(Number(r.p97)));
          }
          ctx.closePath();
          ctx.fillStyle = 'rgba(11,138,102,.08)';
          ctx.fill();

          // P50 中位线
          ctx.beginPath();
          refs.forEach((r, i) => {
            const x = X(r.ageYears);
            const y = Y(Number(r.p50));
            i ? ctx.lineTo(x, y) : ctx.moveTo(x, y);
          });
          ctx.setLineDash([4, 4]);
          ctx.strokeStyle = '#8fb3a4';
          ctx.lineWidth = 1.5;
          ctx.stroke();
          ctx.setLineDash([]);
        }

        // 孩子折线 + 数据点
        if (points.length > 1) {
          ctx.beginPath();
          points.forEach((p, i) => {
            const x = X(p.x);
            const y = Y(p.y);
            i ? ctx.lineTo(x, y) : ctx.moveTo(x, y);
          });
          ctx.strokeStyle = '#0b8a66';
          ctx.lineWidth = 2.5;
          ctx.stroke();
        }
        points.forEach(p => {
          ctx.beginPath();
          ctx.arc(X(p.x), Y(p.y), 3, 0, Math.PI * 2);
          ctx.fillStyle = '#0b8a66';
          ctx.fill();
        });

        // 坐标刻度
        ctx.fillStyle = '#9aa0ad';
        ctx.font = '10px sans-serif';
        const xticks = [Math.round(xMin), Math.round((xMin + xMax) / 2), Math.round(xMax)];
        xticks.forEach(v => ctx.fillText(String(v) + '岁', X(v) - 8, height - 2));
        [rawMin, rawMax].forEach(v => {
          ctx.fillText(String(Math.round(v)), 3, Y(v) + 3);
        });
      });
  }
});
