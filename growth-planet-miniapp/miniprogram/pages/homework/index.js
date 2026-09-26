const ui = require('../../utils/page');

// 本地示例作业数据：接入学校接口后替换为服务端数据。
const TODO = [
  {
    key: 'math-p12', name: '数学 · 练习册 P12-13', subject: '数学',
    deadlineLabel: '今天交', deadlineClass: 'al',
    note: '完成第 3、4 大题，写清步骤',
    teacher: '王老师布置', deadlineText: '今天 18:00 截止', color: '#2f6bff',
    requirement: '完成练习册第 12–13 页第 3、4 大题，应用题要写出列式和答句，字迹工整。',
    estimate: '20 分钟'
  },
  {
    key: 'cn-poem', name: '语文 · 背古诗', subject: '语文',
    deadlineLabel: '明天交', deadlineClass: 'gold',
    note: '背诵《春晓》，明天抽查',
    teacher: '李老师布置', deadlineText: '明天 08:00 截止', color: '#e5484d',
    requirement: '背诵《春晓》，能做到不看课本背出全文，能说出诗中描写了春天的哪些景物。',
    estimate: '15 分钟'
  },
  {
    key: 'en-unit3', name: '英语 · 听读 15 分钟', subject: '英语',
    deadlineLabel: '周五交', deadlineClass: 'normal',
    note: 'Unit 3 课文跟读，每天 15 分钟',
    teacher: '陈老师布置', deadlineText: '周五 20:00 截止', color: '#e8a013',
    requirement: 'Unit 3 课文跟读 15 分钟，注意模仿语音语调，家长可在签名处记录。',
    estimate: '15 分钟'
  }
];

const DONE = [
  {
    key: 'cn-recitation', name: '语文 · 背诵《静夜思》', subject: '语文',
    note: '已背诵，家长确认',
    teacher: '李老师布置', deadlineText: '昨天截止', color: '#e5484d',
    requirement: '背诵《静夜思》，能说出诗句意思。',
    estimate: '10 分钟'
  },
  {
    key: 'math-p10', name: '数学 · 口算 20 题', subject: '数学',
    note: '全对，家长已确认',
    teacher: '王老师布置', deadlineText: '昨天截止', color: '#2f6bff',
    requirement: '口算 20 题，限时 10 分钟。',
    estimate: '10 分钟'
  }
];

ui.page({
  data: {
    role: '', busy: false,
    segment: 'todo',
    todoList: TODO, doneList: DONE,
    detail: null
  },
  onShow() {
    if (!ui.guard(this)) return;
  },
  changeSegment(e) {
    this.setData({ segment: e.currentTarget.dataset.seg });
  },
  openDetail(e) {
    const key = e.currentTarget.dataset.key;
    const found = this.data.todoList.find(item => item.key === key) || this.data.doneList.find(item => item.key === key);
    if (found) this.setData({ detail: found });
  },
  backList() {
    this.setData({ detail: null });
  },
  markDone() {
    if (!this.data.detail || this.data.busy) return;
    this.setData({ busy: true });
    setTimeout(() => {
      const done = this.data.detail;
      this.setData({
        busy: false,
        todoList: this.data.todoList.filter(item => item.key !== done.key),
        doneList: [done, ...this.data.doneList],
        detail: null
      });
      wx.showToast({ title: '已标记完成', icon: 'success' });
    }, 400);
  }
});
