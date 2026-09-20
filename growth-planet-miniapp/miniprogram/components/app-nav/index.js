Component({
  properties: { role: String, active: String },
  data: {
    childTabs: [{ key: 'meal', text: '点餐' }, { key: 'tasks', text: '任务' }, { key: 'learn', text: '学习' }, { key: 'growth', text: '成长' }, { key: 'me', text: '我' }],
    parentTabs: [{ key: 'home', text: '首页' }, { key: 'approvals', text: '审批' }, { key: 'wallet', text: '看板' }, { key: 'me', text: '设置' }]
  },
  methods: { change(e) { this.triggerEvent('change', { key: e.currentTarget.dataset.key }); } }
});
