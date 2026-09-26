const ui = require('../../utils/page');

function pad(n) {
  return (n < 10 ? '0' : '') + n;
}

function mmss(total) {
  const m = Math.floor(total / 60);
  const s = total % 60;
  return pad(m) + ':' + pad(s);
}

let keySeq = 3;

ui.page({
  data: {
    role: '', busy: false,
    // 题型阶段：分批倒计时，每阶段自定义时长（分钟）
    stages: [
      { key: 'k1', name: '选择题', minutes: 15 },
      { key: 'k2', name: '填空题', minutes: 10 },
      { key: 'k3', name: '解答题', minutes: 20 }
    ],
    stageIndex: 0,
    remaining: 15 * 60,
    running: false,
    started: false,
    addName: '',
    addMinutes: '5',
    // 派生显示
    totalMinutes: 45,
    remainingText: '15:00',
    usedText: '0 分钟',
    totalProgress: 0,
    ringOffset: 0
  },
  onShow() {
    if (!ui.guard(this)) return;
  },
  onHide() {
    this.stopTimer();
  },
  onUnload() {
    this.stopTimer();
  },
  stopTimer() {
    if (this.timer) {
      clearInterval(this.timer);
      this.timer = null;
    }
  },
  // ---- 配置 ----
  input(e) {
    this.setData({ [e.currentTarget.dataset.field]: e.detail.value });
  },
  refreshTotals() {
    const totalMinutes = this.data.stages.reduce((sum, s) => sum + s.minutes, 0);
    this.setData({ totalMinutes });
  },
  addStage() {
    const name = this.data.addName.trim();
    const minutes = Number(this.data.addMinutes);
    if (!name || !minutes || minutes <= 0 || minutes > 180) return;
    const key = 'k' + (++keySeq);
    this.setData({
      stages: [...this.data.stages, { key, name, minutes }],
      addName: '',
      addMinutes: '5'
    });
    this.refreshTotals();
  },
  adjustStage(e) {
    const key = e.currentTarget.dataset.key;
    const delta = Number(e.currentTarget.dataset.delta);
    const stages = this.data.stages.map(s => {
      if (s.key !== key) return s;
      const minutes = Math.max(1, Math.min(180, s.minutes + delta));
      return Object.assign({}, s, { minutes });
    });
    this.setData({ stages });
    this.refreshTotals();
  },
  removeStage(e) {
    const key = e.currentTarget.dataset.key;
    this.setData({ stages: this.data.stages.filter(s => s.key !== key) });
    this.refreshTotals();
  },
  startExam() {
    if (!this.data.stages.length || this.data.busy) return;
    this.setData({
      started: true,
      stageIndex: 0,
      remaining: this.data.stages[0].minutes * 60,
      running: true
    });
    this.refresh();
    this.tick();
  },
  // ---- 倒计时 ----
  tick() {
    this.stopTimer();
    this.timer = setInterval(() => {
      let remaining = this.data.remaining - 1;
      if (remaining <= 0) {
        remaining = 0;
        this.stopTimer();
        if (wx.vibrateLong) wx.vibrateLong();
        if (this.data.stageIndex < this.data.stages.length - 1) {
          wx.showToast({ title: '本阶段时间到', icon: 'none' });
          this.nextStage();
          return;
        }
        this.setData({ running: false });
        wx.showToast({ title: '考试结束', icon: 'none' });
      }
      this.setData({ remaining });
      this.refresh();
    }, 1000);
  },
  toggleRun() {
    if (this.data.running) {
      this.stopTimer();
      this.setData({ running: false });
      return;
    }
    if (this.data.remaining <= 0) {
      wx.showToast({ title: '时间已到，跳到下一阶段', icon: 'none' });
      return;
    }
    this.setData({ running: true });
    this.tick();
  },
  nextStage() {
    this.stopTimer();
    const next = this.data.stageIndex + 1;
    if (next >= this.data.stages.length) {
      this.setData({ running: false, remaining: 0 });
      this.refresh();
      wx.showToast({ title: '考试结束', icon: 'none' });
      return;
    }
    this.setData({ stageIndex: next, remaining: this.data.stages[next].minutes * 60, running: true });
    this.refresh();
    this.tick();
  },
  addTime() {
    this.setData({ remaining: this.data.remaining + 60 });
    this.refresh();
  },
  endExam() {
    this.stopTimer();
    this.setData({ started: false, running: false, stageIndex: 0, remaining: 0 });
    this.refresh();
    this.refreshTotals();
  },
  refresh() {
    const stages = this.data.stages;
    const totalSeconds = stages.reduce((sum, s) => sum + s.minutes * 60, 0);
    const doneSeconds = stages.slice(0, this.data.stageIndex).reduce((sum, s) => sum + s.minutes * 60, 0);
    const usedSeconds = doneSeconds + (stages[this.data.stageIndex].minutes * 60 - this.data.remaining);
    const ratio = totalSeconds ? usedSeconds / totalSeconds : 0;
    const stageSeconds = stages[this.data.stageIndex].minutes * 60;
    const stageRatio = stageSeconds ? (stageSeconds - this.data.remaining) / stageSeconds : 0;
    this.setData({
      remainingText: mmss(this.data.remaining),
      usedText: Math.floor(usedSeconds / 60) + ' 分钟',
      totalProgress: Math.round(ratio * 100),
      ringOffset: Math.round(578 * stageRatio)
    });
  }
});
