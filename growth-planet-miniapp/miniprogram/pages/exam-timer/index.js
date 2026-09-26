const ui = require('../../utils/page');

const SUBJECTS = ['数学', '语文', '英语'];

function mmss(total) {
  const m = Math.floor(total / 60);
  const s = total % 60;
  return (m < 10 ? '0' : '') + m + ':' + (s < 10 ? '0' : '') + s;
}

ui.page({
  data: {
    role: '', busy: false,
    subjects: SUBJECTS.map(name => ({ name })),
    subjectIndex: 0,
    duration: 30 * 60,
    remaining: 29 * 60 + 59,
    running: false,
    durationMinutes: 30,
    remainingText: '29:59',
    usedText: '1 秒',
    durationText: '30 分钟',
    ringOffset: 0,
    ringColor: '#2f6bff'
  },
  onShow() {
    if (!ui.guard(this)) return;
    this.refresh();
  },
  onHide() {
    this.stopTimer();
  },
  onUnload() {
    this.stopTimer();
  },
  refresh() {
    const duration = this.data.duration;
    const remaining = this.data.remaining;
    const used = duration - remaining;
    const ratio = duration ? used / duration : 0;
    this.setData({
      remainingText: mmss(remaining),
      usedText: used + ' 秒',
      durationText: mmss(duration),
      ringOffset: Math.round(578 * ratio)
    });
  },
  stopTimer() {
    if (this.timer) {
      clearInterval(this.timer);
      this.timer = null;
    }
  },
  pickSubject(e) {
    this.stopTimer();
    const index = Number(e.currentTarget.dataset.index);
    this.setData({
      subjectIndex: index,
      running: false,
      ringColor: index === 0 ? '#2f6bff' : index === 1 ? '#e5484d' : '#e8a013'
    });
  },
  toggleRun() {
    if (this.data.running) {
      this.stopTimer();
      this.setData({ running: false });
      return;
    }
    if (this.data.remaining <= 0) {
      wx.showToast({ title: '时间已到，先重置时长', icon: 'none' });
      return;
    }
    this.setData({ running: true });
    this.timer = setInterval(() => {
      let remaining = this.data.remaining - 1;
      if (remaining <= 0) {
        remaining = 0;
        this.stopTimer();
        this.setData({ running: false });
        if (wx.vibrateLong) wx.vibrateLong();
        wx.showToast({ title: '时间到', icon: 'none' });
      }
      this.setData({ remaining });
      this.refresh();
    }, 1000);
  },
  addTime() {
    if (this.data.remaining <= 0) {
      this.setData({ remaining: this.data.duration });
    } else {
      this.setData({ remaining: Math.min(this.data.remaining + 300, this.data.duration + 600) });
    }
    this.refresh();
  },
  resetDuration() {
    this.stopTimer();
    this.setData({ running: false, remaining: this.data.duration });
    this.refresh();
  },
  endExam() {
    this.stopTimer();
    this.setData({ running: false, remaining: 0 });
    this.refresh();
    wx.showToast({ title: '考试结束', icon: 'none' });
  }
});
