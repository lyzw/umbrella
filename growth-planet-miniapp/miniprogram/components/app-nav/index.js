Component({
  properties: { role: String, active: String },
  data: {
    childTabs: [
      { key: 'meal', text: '点餐', icon: '🍽️' },
      { key: 'tasks', text: '任务', icon: '✓' },
      { key: 'learn', text: '学习', icon: '📚' },
      { key: 'growth', text: '成长', icon: '🌱' },
      { key: 'me', text: '我', icon: '●' }
    ],
    parentTabs: [
      { key: 'home', text: '首页', icon: '⌂' },
      { key: 'approvals', text: '审批', icon: '✓' },
      { key: 'wallet', text: '看板', icon: '▥' },
      { key: 'me', text: '设置', icon: '⚙' }
    ]
  },
  methods: { change(e) { this.triggerEvent('change', { key: e.currentTarget.dataset.key }); } }
});
