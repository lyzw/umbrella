const api = require('../../services/api');
const ui = require('../../utils/page');
const { loadChildren } = require('../../services/children');
const { list } = require('../../utils/domain');
const config = require('../../config');
ui.page({
  data: { role: '', busy: false, error: '', ready: false, children: [], childIndex: 0, childId: '',
    nickname: '', school: '', grade: '', grades: config.grades, allergens: config.allergens,
    allergies: [], dislikesText: '', tastesText: '', allergyOptions: [] },
  input: ui.input,
  onLoad(query) { this.requestedChild = query.childId; },
  onShow() { if (ui.guard(this)) this.load(); },
  load() {
    return ui.run(this, async () => {
      const children = await loadChildren();
      const index = Math.max(0, children.findIndex(c => c.childId === (this.requestedChild || this.data.childId)));
      this.setData({ children, childIndex: index, childId: children[index].childId });
      await this.read();
    });
  },
  async read() {
    this.clearForm();
    const childId = this.data.childId;
    let profile;
    if (this.data.role === 'PARENT') {
      const consent = await api.get('/compliance/consent', { childId, consentType: 'PROFILE' });
      if (consent.currentStatus !== 'GRANTED') throw new Error('请先在家庭页面完成有效同意');
      try { profile = await api.get('/child/profile', { childId }); }
      catch (error) { if (error.status !== 404) throw error; }
    } else profile = await api.get('/child/preferences', { childId });
    profile = profile || { allergies: [], dislikes: [], tastes: [], grade: config.grades[0] };
    this.setData({ ready: true, nickname: profile.nickname || '', school: profile.school || '', grade: profile.grade || '',
      allergies: profile.allergies || [], dislikesText: (profile.dislikes || []).join('，'), tastesText: (profile.tastes || []).join('，'),
      allergyOptions: Array.from(new Set([...config.allergens, ...(profile.allergies || [])]))
        .map(code => ({ code, checked: (profile.allergies || []).includes(code) })) });
  },
  child(e) {
    this.requestedChild = null;
    const index = Number(e.detail.value);
    this.setData({ childIndex: index, childId: this.data.children[index].childId });
    ui.run(this, () => this.read());
  },
  grade(e) { this.setData({ grade: this.data.grades[Number(e.detail.value)] }); },
  allergies(e) { this.setData({ allergies: e.detail.value }); },
  save() {
    return ui.run(this, async () => {
      if (!this.data.ready) throw new Error('请先完成授权检查');
      const dislikes = list(this.data.dislikesText), tastes = list(this.data.tastesText);
      if (this.data.role === 'PARENT') {
        const { childId, nickname, school, grade, allergies } = this.data;
        if (!nickname.trim() || !school.trim()) throw new Error('请填写昵称和学校');
        await api.post('/child/profile', { childId, nickname: nickname.trim(), school: school.trim(), grade, allergies, dislikes, tastes });
      } else await api.put('/child/preferences', { dislikes, tastes });
      wx.showToast({ title: '已保存', icon: 'success' });
      await this.read();
    });
  },
  clearForm() { this.setData({ ready: false, nickname: '', school: '', grade: '', allergies: [], dislikesText: '', tastesText: '', allergyOptions: [] }); },
  onHide() { this.clearForm(); },
  onUnload() { this.clearForm(); }
});
