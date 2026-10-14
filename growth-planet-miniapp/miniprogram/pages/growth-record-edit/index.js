const api = require('../../services/api');
const ui = require('../../utils/page');

ui.page({
  data: {
    role: '', busy: false, error: '',
    childId: '', gender: 'BOY', ageYears: '', heightCm: '', weightKg: '', note: '',
    ref: { height: null, weight: null }
  },
  input: ui.input,
  onLoad(options) {
    this.setData({ childId: decodeURIComponent(options.childId || '') });
  },
  onShow() {
    if (!ui.guard(this, 'PARENT')) return;
  },
  back() { ui.back(); },
  pickGender(e) {
    this.setData({ gender: e.currentTarget.dataset.gender });
    this.loadRef();
  },
  // 年龄输入完成后加载 WHO 参考区间（同性别同年岁的身高 / 体重 P50 与 P3–P97）
  loadRef() {
    const age = Number(this.data.ageYears);
    if (!Number.isFinite(age) || age < 1 || age > 19 || this.data.busy) return;
    return ui.run(this, async () => {
      const [height, weight] = await Promise.all([
        api.get('/growth/standard', { ageBand: 'WHO 2007', gender: this.data.gender, metric: 'HEIGHT' }),
        api.get('/growth/standard', { ageBand: 'WHO 2007', gender: this.data.gender, metric: 'WEIGHT' })
      ]);
      const pick = (resp) => {
        const row = resp.rows.find(r => r.ageYears === age);
        return row ? { p3: row.p3, p50: row.p50, p97: row.p97 } : null;
      };
      this.setData({ ref: { height: pick(height), weight: pick(weight) } });
    });
  },
  submit() {
    return ui.run(this, async () => {
      const heightCm = Number(this.data.heightCm);
      const weightKg = Number(this.data.weightKg);
      if (!Number.isFinite(heightCm) || heightCm < 30 || heightCm > 250) throw new Error('身高需在 30–250cm 之间');
      if (!Number.isFinite(weightKg) || weightKg < 1 || weightKg > 200) throw new Error('体重需在 1–200kg 之间');
      const age = this.data.ageYears === '' ? null : Number(this.data.ageYears);
      if (age !== null && (!Number.isFinite(age) || age < 1 || age > 19)) throw new Error('年龄需在 1–19 岁之间');
      if (!this.data.childId) throw new Error('缺少儿童信息，请返回家庭页重试');
      const body = {
        childId: this.data.childId,
        gender: this.data.gender,
        heightCm: String(heightCm),
        weightKg: String(weightKg),
        note: this.data.note.trim() || null
      };
      if (age !== null) body.ageYears = age;
      await api.post('/growth/records', body);
      wx.showToast({ title: '已保存', icon: 'success' });
      setTimeout(() => ui.back(), 600);
    });
  }
});
