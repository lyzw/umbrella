const api = require('../../services/api');
const ui = require('../../utils/page');

const BAND_LABEL = {
  '<P15': '偏低于 P15',
  'P15–P50': '落在 P15–P50 之间',
  'P50–P85': '落在 P50–P85 之间',
  '≥P85': '接近或高于 P85'
};
const BAND_COLS = {
  '<P15': ['p3'],
  'P15–P50': ['p15', 'p50'],
  'P50–P85': ['p50', 'p85'],
  '≥P85': ['p85', 'p97']
};
const METRIC_LABEL = { HEIGHT: '身高', WEIGHT: '体重', BMI: 'BMI' };

function round1(v) { return Math.round(v * 10) / 10; }

function interp(rows, age) {
  // rows 按 ageYears 升序；在相邻两个整岁锚点间线性插值出月点
  if (age === Math.floor(age)) {
    const hit = rows.find(r => r.ageYears === age);
    return hit || null;
  }
  const lo = Math.floor(age);
  const a = rows.find(r => r.ageYears === lo);
  const b = rows.find(r => r.ageYears === lo + 1);
  if (!a || !b) return null;
  const t = age - lo;
  return {
    p3: round1(a.p3 + (b.p3 - a.p3) * t),
    p15: round1(a.p15 + (b.p15 - a.p15) * t),
    p50: round1(a.p50 + (b.p50 - a.p50) * t),
    p85: round1(a.p85 + (b.p85 - a.p85) * t),
    p97: round1(a.p97 + (b.p97 - a.p97) * t)
  };
}

function ageLabel(age) {
  const y = Math.floor(age);
  const m = Math.round((age - y) * 12);
  return m === 0 ? y + '岁' : y + '岁' + m + '月';
}

function bandFor(row, value) {
  if (!row || !Number.isFinite(value)) return null;
  if (value < row.p15) return '<P15';
  if (value < row.p50) return 'P15–P50';
  if (value < row.p85) return 'P50–P85';
  return '≥P85';
}

function metricValue(item, metric) {
  if (!item) return null;
  const v = metric === 'HEIGHT' ? item.heightCm : metric === 'WEIGHT' ? item.weightKg : item.bmi;
  const n = Number(v);
  return Number.isFinite(n) ? n : null;
}

function monthRows(base, ageYears) {
  // 以孩子岁为中心，前后各一岁，按每 3 个月一个点（含整岁锚点）
  const lo = Math.max(1, ageYears - 1);
  const hi = Math.min(19, ageYears + 1);
  const out = [];
  for (let y = lo; y <= hi; y++) {
    for (let m = 0; m < 4; m++) {
      const age = y + m * 0.25;
      if (age > hi) break;
      const row = interp(base, age);
      if (!row) continue;
      out.push({ ...row, ageYears: age, label: ageLabel(age), isTarget: false, hl: {} });
    }
  }
  return out;
}

ui.page({
  data: {
    role: '', busy: false, error: '',
    childId: '', ageBand: 'WHO 2007', gender: 'BOY', metric: 'HEIGHT',
    rows: [], unit: '', loc: '', targetLabel: '', targetValue: '', targetBand: '',
    hasChild: false, childValue: null, childAge: null, baseRows: []
  },
  onLoad(options) {
    this.setData({ childId: decodeURIComponent(options.childId || '') });
  },
  onShow() {
    if (!ui.guard(this, 'PARENT')) return;
    return this.refresh();
  },
  refresh() {
    return ui.run(this, async () => {
      const childId = this.data.childId;
      const [stdResp, latest] = await Promise.all([
        api.get('/growth/standard', { ageBand: this.data.ageBand, gender: this.data.gender, metric: this.data.metric }),
        childId ? api.get('/growth/records/latest', { childId }) : Promise.resolve(null)
      ]);
      const base = stdResp.rows || [];
      const l = latest || null;
      const childValue = l ? metricValue(l, this.data.metric) : null;
      const childAge = l && l.ageYears ? Number(l.ageYears) : null;

      let rows = base.map(r => ({ ...r, label: r.ageYears + '岁', isTarget: false, hl: {} }));
      let loc = '';
      let target = null;
      if (l && childAge && childValue !== null) {
        rows = monthRows(base, childAge);
        const targetAge = Math.floor(childAge * 4) / 4;
        const row = rows.find(r => Math.abs(r.ageYears - targetAge) < 0.001)
          || rows.find(r => r.ageYears === Math.floor(childAge))
          || rows[0];
        const band = bandFor(row, childValue);
        target = { row, band, value: childValue };
        loc = (l.gender === 'GIRL' ? '女孩' : '男孩') + ' · ' + ageLabel(childAge)
          + ' · ' + METRIC_LABEL[this.data.metric] + ' ' + childValue
          + (band ? '，' + BAND_LABEL[band] : '');
        const cols = band ? BAND_COLS[band] : [];
        rows = rows.map(r => {
          const isTarget = Math.abs(r.ageYears - targetAge) < 0.001;
          const hl = isTarget ? {
            p3: cols.includes('p3'), p15: cols.includes('p15'),
            p50: cols.includes('p50'), p85: cols.includes('p85'), p97: cols.includes('p97')
          } : {};
          return { ...r, isTarget, hl };
        });
      }

      this.setData({
        rows, unit: stdResp.unit, loc,
        targetLabel: target ? ageLabel(target.row.ageYears) : '',
        targetValue: target ? String(target.value) : '',
        targetBand: target && target.band ? BAND_LABEL[target.band] : '',
        hasChild: !!target, childValue, childAge, baseRows: base, gender: stdResp.gender || this.data.gender
      });
      this.drawBand();
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
  },
  // 孩子所在分位带图：P3–P97 参考带 + P50 中位线 + 孩子点
  drawBand() {
    const { baseRows, childValue, childAge, hasChild } = this.data;
    if (!hasChild || !baseRows.length || !childValue) return;
    wx.createSelectorQuery().in(this).select('#std-band').fields({ node: true, size: true })
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

        const xMin = baseRows[0].ageYears - 0.5;
        const xMax = baseRows[baseRows.length - 1].ageYears + 0.5;
        const ys = baseRows.flatMap(r => [Number(r.p3), Number(r.p97)]).concat(childValue || []);
        const rawMin = Math.min(...ys);
        const rawMax = Math.max(...ys);
        const pad = (rawMax - rawMin) * 0.15 || 1;
        const yMin = rawMin - pad;
        const yMax = rawMax + pad;
        const X = x => (x - xMin) / (xMax - xMin) * width;
        const Y = y => height - (y - yMin) / (yMax - yMin) * height;

        ctx.strokeStyle = '#eef0f4';
        ctx.lineWidth = 1;
        for (let i = 1; i < 4; i++) {
          const gy = Math.round(height * i / 4);
          ctx.beginPath();
          ctx.moveTo(0, gy);
          ctx.lineTo(width, gy);
          ctx.stroke();
        }

        // P3–P97 带
        ctx.beginPath();
        baseRows.forEach((r, i) => {
          const x = X(r.ageYears);
          const y = Y(Number(r.p3));
          i ? ctx.lineTo(x, y) : ctx.moveTo(x, y);
        });
        for (let i = baseRows.length - 1; i >= 0; i--) {
          ctx.lineTo(X(baseRows[i].ageYears), Y(Number(baseRows[i].p97)));
        }
        ctx.closePath();
        ctx.fillStyle = 'rgba(11,138,102,.08)';
        ctx.fill();

        // P50
        ctx.beginPath();
        baseRows.forEach((r, i) => {
          const x = X(r.ageYears);
          const y = Y(Number(r.p50));
          i ? ctx.lineTo(x, y) : ctx.moveTo(x, y);
        });
        ctx.setLineDash([4, 4]);
        ctx.strokeStyle = '#8fb3a4';
        ctx.lineWidth = 1.5;
        ctx.stroke();
        ctx.setLineDash([]);

        // 孩子点
        ctx.beginPath();
        ctx.arc(X(childAge), Y(childValue), 4.5, 0, Math.PI * 2);
        ctx.fillStyle = '#0b8a66';
        ctx.fill();
        ctx.strokeStyle = '#fff';
        ctx.lineWidth = 2;
        ctx.stroke();

        ctx.fillStyle = '#9aa0ad';
        ctx.font = '10px sans-serif';
        [xMin, (xMin + xMax) / 2, xMax].forEach(v => ctx.fillText(String(Math.round(v)) + '岁', X(v) - 8, height - 2));
        [rawMin, rawMax].forEach(v => ctx.fillText(String(Math.round(v)), 3, Y(v) + 3));
      });
  }
});
