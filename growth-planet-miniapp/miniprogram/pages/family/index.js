const api = require('../../services/api');
const session = require('../../services/session');
const ui = require('../../utils/page');
const { statusLabels } = require('../../utils/domain');
ui.page({
  data: { role: '', busy: false, error: '', familyName: '', inviteCode: '', invite: null, binding: null, children: [],
    hasFamily: false, selected: null, consent: null, agreed: false, age: '', relationLabel: '', page: 1, total: 0 },
  input: ui.input,
  onShow() { if (ui.guard(this)) this.refresh(); },
  refresh() {
    return ui.run(this, async () => {
      if (this.data.role === 'CHILD') {
        const binding = await api.get('/family/binding');
        this.setData({ binding: { ...binding, label: statusLabels[binding.bindStatus] || binding.bindStatus } });
      } else {
        try {
          const result = await api.get('/family/children', { page: this.data.page, pageSize: 20 });
          this.setData({ hasFamily: true, children: result.items, total: result.total });
        } catch (error) {
          if (error.status === 404 || (error.status === 403 && error.code === 'E-009')) {
            this.setData({ hasFamily: false, children: [] });
            if (error.status === 403) this.setData({ error: '尚无可访问的家庭。可创建家庭；已有家庭请重新登录后查询。' });
          }
          else throw error;
        }
      }
    });
  },
  create() {
    return ui.run(this, async () => {
      const familyName = this.data.familyName.trim();
      if (!familyName || familyName.length > 64) throw new Error('家庭名称为1至64字');
      const result = await api.post('/family/create', { familyName });
      session.set({ ...session.get(), ...result });
      this.setData({ hasFamily: true, invite: result, familyName: '' });
    });
  },
  invitation() { return ui.run(this, async () => this.setData({ invite: await api.get('/family/invite-code') })); },
  join() {
    return ui.run(this, async () => {
      const inviteCode = this.data.inviteCode.trim().toUpperCase();
      if (!/^[A-Z0-9]{6}$/.test(inviteCode)) throw new Error('请输入6位邀请码');
      await api.post('/family/join', { inviteCode });
      const binding = await api.get('/family/binding');
      this.setData({ inviteCode: '', binding: { ...binding, label: statusLabels[binding.bindStatus] } });
    });
  },
  inspect(e) {
    const selected = this.data.children.find(c => c.applyId === e.currentTarget.dataset.id);
    if (!selected) return;
    this.setData({ selected, consent: null, agreed: false, age: '' });
    return ui.run(this, async () => {
      const consent = await api.get('/compliance/consent', { childId: selected.childId, consentType: 'PROFILE' });
      this.setData({ consent });
    });
  },
  agree(e) { this.setData({ agreed: e.detail.value.includes('yes') }); },
  grantConsent() {
    return ui.run(this, async () => {
      const age = Number(this.data.age);
      if (!this.data.agreed || !Number.isInteger(age) || age < 18 || age > 120) throw new Error('请独立勾选同意，并填写18至120岁的整数年龄');
      const selected = this.data.selected;
      await api.post('/compliance/consent', { childId: selected.childId, applyId: selected.applyId,
        consentType: 'PROFILE', version: this.data.consent.version, selfReportedAge: age, agreed: true });
      this.setData({ consent: await api.get('/compliance/consent', { childId: selected.childId, consentType: 'PROFILE' }), age: '', agreed: false });
    });
  },
  approve(e) {
    return ui.run(this, async () => {
      const approve = e.currentTarget.dataset.approve === 'yes';
      if (!await ui.confirm(approve ? '确认该儿童属于你的家庭？同意记录不代表监护关系已核验。' : '确认拒绝这次绑定申请？')) return;
      await api.post('/family/bind-approve', { applyId: this.data.selected.applyId, approve, relationLabel: this.data.relationLabel.trim() || null });
      this.setData({ selected: null, consent: null });
      const result = await api.get('/family/children', { page: this.data.page, pageSize: 20 });
      this.setData({ children: result.items, total: result.total });
    });
  },
  profile() { wx.navigateTo({ url: '/pages/profile/index?childId=' + this.data.selected.childId }); },
  home() { wx.reLaunch({ url: '/pages/home/index' }); },
  next(e) { this.setData({ page: this.data.page + Number(e.currentTarget.dataset.delta) }); this.refresh(); },
  onHide() { this.setData({ consent: null, selected: null, age: '', agreed: false, familyName: '', inviteCode: '', invite: null, relationLabel: '', children: [], binding: null }); }
});
