const ui = require('../../utils/page');

ui.page({
  data: {
    role: '', busy: false
  },
  onShow() {
    if (!ui.guard(this)) return;
  },
  goExamTimer() {
    ui.openPage(this, '/pages/exam-timer/index');
  },
  tomato() {
    wx.showToast({ title: '番茄钟演示中', icon: 'none' });
  },
  comingSoon() {
    wx.showToast({ title: '即将上线，敬请期待', icon: 'none' });
  }
});
