const ui = require('../../utils/page');

function pad(n) {
  return (n < 10 ? '0' : '') + n;
}

function mmss(total) {
  const m = Math.floor(total / 60);
  const s = total % 60;
  return pad(m) + ':' + pad(s);
}

let keySeq = 100;

ui.page({
  data: {
    role: '', busy: false,
    // 科目 + 各自独立的题型阶段（分批倒计时）
    subjects: [
      {
        key: 's1', name: '数学',
        stages: [
          { key: 'k1', name: '选择题', minutes: 15 },
          { key: 'k2', name: '填空题', minutes: 10 },
          { key: 'k3', name: '解答题', minutes: 20 }
        ]
      },
      {
        key: 's2', name: '语文',
        stages: [
          { key: 'k4', name: '基础知识', minutes: 15 },
          { key: 'k5', name: '阅读理解', minutes: 20 },
          { key: 'k6', name: '作文', minutes: 30 }
        ]
      },
      {
        key: 's3', name: '英语',
        stages: [
          { key: 'k7', name: '听力', minutes: 10 },
          { key: 'k8', name: '单项选择', minutes: 10 },
          { key: 'k9', name: '阅读理解', minutes: 15 }
        ]
      }
    ],
    subjectIndex: 0,
    stageIndex: 0,
    remaining: 15 * 60,
    running: false,
    started: false,
    showAddSubject: false,
    addSubject: '',
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
  currentStages() {
    return (this.data.subjects[this.data.subjectIndex] || { stages: [] }).stages || [];
  },
  // ---- 科目 ----
  input(e) {
    this.setData({ [e.currentTarget.dataset.field]: e.detail.value });
  },
  pickSubject(e) {
    const index = Number(e.currentTarget.dataset.index);
    if (index === this.data.subjectIndex) return;
    this.stopTimer();
    this.setData({ subjectIndex: index, started: false, running: false, stageIndex: 0, remaining: 0 });
    this.refreshTotals();
    this.refresh();
  },
  toggleAddSubject() {
    this.setData({ showAddSubject: !this.data.showAddSubject, addSubject: '' });
  },
  addSubject() {
    const name = this.data.addSubject.trim();
    if (!name) return;
    const subjects = [...this.data.subjects, {
      key: 's' + (++keySeq),
      name,
      stages: [{ key: 'k' + (++keySeq), name: '第一部分', minutes: 15 }]
    }];
    this.setData({ subjects, subjectIndex: subjects.length - 1, addSubject: '', showAddSubject: false, started: false });
    this.refreshTotals();
  },
  // ---- 题型阶段配置 ----
  refreshTotals() {
    const stages = this.currentStages();
    const totalMinutes = stages.reduce((sum, s) => sum + s.minutes, 0);
    this.setData({ totalMinutes });
  },
  addStage() {
    const name = this.data.addName.trim();
    const minutes = Number(this.data.addMinutes);
    if (!name || !minutes || minutes <= 0 || minutes > 180) return;
    const stages = [...this.currentStages(), { key: 'k' + (++keySeq), name, minutes }];
    this.setData({ ['subjects[' + this.data.subjectIndex + '].stages']: stages, addName: '', addMinutes: '5' });
    this.refreshTotals();
  },
  adjustStage(e) {
    const key = e.currentTarget.dataset.key;
    const delta = Number(e.currentTarget.dataset.delta);
    const stages = this.currentStages().map(s => {
      if (s.key !== key) return s;
      return Object.assign({}, s, { minutes: Math.max(1, Math.min(180, s.minutes + delta)) });
    });
    this.setData({ ['subjects[' + this.data.subjectIndex + '].stages']: stages });
    this.refreshTotals();
  },
  removeStage(e) {
    const key = e.currentTarget.dataset.key;
    const stages = this.currentStages().filter(s => s.key !== key);
    this.setData({ ['subjects[' + this.data.subjectIndex + '].stages']: stages });
    this.refreshTotals();
  },
  startExam() {
    const stages = this.currentStages();
    if (!stages.length || this.data.busy) return;
    this.setData({ started: true, stageIndex: 0, remaining: stages[0].minutes * 60, running: true });
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
        if (this.data.stageIndex < this.currentStages().length - 1) {
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
    const stages = this.currentStages();
    const next = this.data.stageIndex + 1;
    if (next >= stages.length) {
      this.setData({ running: false, remaining: 0 });
      this.refresh();
      wx.showToast({ title: '考试结束', icon: 'none' });
      return;
    }
    this.setData({ stageIndex: next, remaining: stages[next].minutes * 60, running: true });
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
    const stages = this.currentStages();
    if (!stages.length) {
      this.setData({ remainingText: '00:00', usedText: '0 分钟', totalProgress: 0, ringOffset: 0 });
      return;
    }
    const safeIndex = Math.min(this.data.stageIndex, stages.length - 1);
    const totalSeconds = stages.reduce((sum, s) => sum + s.minutes * 60, 0);
    const doneSeconds = stages.slice(0, safeIndex).reduce((sum, s) => sum + s.minutes * 60, 0);
    const usedSeconds = doneSeconds + (stages[safeIndex].minutes * 60 - this.data.remaining);
    const ratio = totalSeconds ? usedSeconds / totalSeconds : 0;
    const stageSeconds = stages[safeIndex].minutes * 60;
    const stageRatio = stageSeconds ? (stageSeconds - this.data.remaining) / stageSeconds : 0;
    this.setData({
      remainingText: mmss(this.data.remaining),
      usedText: Math.floor(usedSeconds / 60) + ' 分钟',
      totalProgress: Math.round(ratio * 100),
      ringOffset: Math.round(578 * stageRatio)
    });
  }
});
