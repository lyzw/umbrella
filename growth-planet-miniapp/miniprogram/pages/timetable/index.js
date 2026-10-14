const ui = require('../../utils/page');

const WEEK = [
  { label: '一' }, { label: '二' }, { label: '三' }, { label: '四' },
  { label: '五' }, { label: '六' }, { label: '日' }
];

// 本地示例课表：按星期几取当日节次。接入学校接口后替换为服务端数据。
const PERIODS = {
  '一': [
    { no: '1', name: '语文', time: '08:00', place: '教室2', color: '#e5484d' },
    { no: '2', name: '数学', time: '08:55', place: '教室2', color: '#2f6bff' },
    { no: '3', name: '英语', time: '10:00', place: '语音室', color: '#e8a013' },
    { no: '4', name: '体育', time: '10:55', place: '操场', color: '#22a06b' },
    { no: '5', name: '科学', time: '14:00', place: '实验室', color: '#7c5cff' },
    { no: '6', name: '美术', time: '14:55', place: '画室', color: '#e8590c' }
  ],
  '二': [
    { no: '1', name: '数学', time: '08:00', place: '教室2', color: '#2f6bff' },
    { no: '2', name: '语文', time: '08:55', place: '教室2', color: '#e5484d' },
    { no: '3', name: '科学', time: '10:00', place: '实验室', color: '#7c5cff' },
    { no: '4', name: '音乐', time: '10:55', place: '音乐室', color: '#12b5a5' },
    { no: '5', name: '英语', time: '14:00', place: '语音室', color: '#e8a013' }
  ],
  '三': [
    { no: '1', name: '语文', time: '08:00', place: '教室2', color: '#e5484d' },
    { no: '2', name: '数学', time: '08:55', place: '教室2', color: '#2f6bff' },
    { no: '3', name: '英语', time: '10:00', place: '语音室', color: '#e8a013' },
    { no: '4', name: '体育', time: '10:55', place: '操场', color: '#22a06b' },
    { no: '5', name: '科学', time: '14:00', place: '实验室', color: '#7c5cff' },
    { no: '6', name: '美术', time: '14:55', place: '画室', color: '#e8590c' }
  ],
  '四': [
    { no: '1', name: '数学', time: '08:00', place: '教室2', color: '#2f6bff' },
    { no: '2', name: '语文', time: '08:55', place: '教室2', color: '#e5484d' },
    { no: '3', name: '道德与法治', time: '10:00', place: '教室2', color: '#f5b301' },
    { no: '4', name: '英语', time: '10:55', place: '语音室', color: '#e8a013' },
    { no: '5', name: '体育', time: '14:00', place: '操场', color: '#22a06b' }
  ],
  '五': [
    { no: '1', name: '语文', time: '08:00', place: '教室2', color: '#e5484d' },
    { no: '2', name: '数学', time: '08:55', place: '教室2', color: '#2f6bff' },
    { no: '3', name: '英语', time: '10:00', place: '语音室', color: '#e8a013' },
    { no: '4', name: '劳动', time: '10:55', place: '劳技室', color: '#e8590c' },
    { no: '5', name: '班会', time: '14:00', place: '教室2', color: '#12b5a5' }
  ]
};

ui.page({
  data: {
    role: '', busy: false,
    weekDays: WEEK.map((d, i) => ({ label: d.label, isToday: i === 2 })),
    activeDay: '三',
    periods: PERIODS['三'],
    lunchBreak: true
  },
  onShow() {
    if (!ui.guard(this)) return;
  },
  pickDay(e) {
    const day = e.currentTarget.dataset.day;
    this.setData({ activeDay: day, periods: PERIODS[day] || [] });
  },
  go: ui.go
});
