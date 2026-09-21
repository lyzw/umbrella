Component({
  properties: { role: String, active: String },
  data: {
    childTabs: [
      { key: 'meal', text: '点餐', icon: '餐' },
      { key: 'chore', text: '家务', icon: '务' },
      { key: 'growth', text: '成长', icon: '长' },
      { key: 'me', text: '我的', icon: '我' }
    ],
    parentTabs: [
      { key: 'home', text: '首页', icon: '首' },
      { key: 'approvals', text: '审批', icon: '审' },
      { key: 'wallet', text: '钱包', icon: '账' },
      { key: 'me', text: '我的', icon: '我' }
    ]
  },
  methods: { change(e) { this.triggerEvent('change', { key: e.currentTarget.dataset.key }); } }
});
