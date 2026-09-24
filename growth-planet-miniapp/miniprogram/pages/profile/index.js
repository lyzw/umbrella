const api = require('../../services/api');
const ui = require('../../utils/page');
const { loadChildren, displayChildren } = require('../../services/children');
const { list } = require('../../utils/domain');
const config = require('../../config');

function normalizePreferenceList(value) {
  const text = Array.isArray(value) ? value.join('，') : String(value || '');
  return list(text);
}

function preferenceField(field) {
  return field === 'dislikes' || field === 'tastes' ? field : '';
}

ui.page({
  data: { role: '', busy: false, error: '', ready: false, children: [], childIndex: 0, childId: '',
    nickname: '', school: '', grade: '', grades: config.grades, allergens: config.allergens, schools: config.schools,
    schoolOptions: [], allergies: [], dislikes: [], tastes: [], dislikesText: '', tastesText: '', allergyOptions: [] },
  input: ui.input,
  onLoad(query) { this.requestedChild = query.childId; },
  onShow() { if (ui.guard(this)) this.load(); },
  load() {
    return ui.run(this, async () => {
      const children = displayChildren(await loadChildren());
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
    // 学校与过敏原同策略：目录值 + 档案里的历史值合并，既保证可选，也不让老数据在界面上凭空消失。
    const schoolOptions = Array.from(new Set([...config.schools, ...(profile.school ? [profile.school] : [])]));
    const dislikes = normalizePreferenceList(profile.dislikes);
    const tastes = normalizePreferenceList(profile.tastes);
    this.setData({ ready: true, nickname: profile.nickname || '', school: profile.school || config.schools[0] || '',
      schoolOptions, grade: profile.grade || '',
      allergies: profile.allergies || [], dislikes, tastes, dislikesText: '', tastesText: '',
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
  school(e) { this.setData({ school: this.data.schoolOptions[Number(e.detail.value)] }); },
  allergies(e) { this.setData({ allergies: e.detail.value }); },
  addPreference(e) {
    return ui.run(this, async () => {
      const field = preferenceField(String(e.currentTarget.dataset.field || '').replace(/Text$/, ''));
      if (!field) throw new Error('偏好字段无效');
      const textField = field + 'Text';
      const values = list([...(this.data[field] || []), this.data[textField] || ''].join('，'));
      this.setData({ [field]: values, [textField]: '' });
    });
  },
  removePreference(e) {
    const field = preferenceField(e.currentTarget.dataset.field);
    const index = Number(e.currentTarget.dataset.index);
    if (!field || !Number.isInteger(index) || index < 0 || index >= this.data[field].length) {
      this.setData({ error: '偏好项已失效，请刷新后重试' });
      return;
    }
    const values = this.data[field].slice();
    values.splice(index, 1);
    this.setData({ [field]: values, error: '' });
  },
  save() {
    return ui.run(this, async () => {
      if (!this.data.ready) throw new Error('请先完成授权检查');
      // 保存前合并已选标签与输入框草稿，确保未点击「添加」的内容也不会丢失。
      const dislikes = list([...(this.data.dislikes || []), this.data.dislikesText || ''].join('，'));
      const tastes = list([...(this.data.tastes || []), this.data.tastesText || ''].join('，'));
      if (this.data.role === 'PARENT') {
        const { childId, nickname, school, grade, allergies } = this.data;
        if (!nickname.trim() || !school) throw new Error('请填写昵称并选择学校');
        await api.post('/child/profile', { childId, nickname: nickname.trim(), school: school.trim(), grade, allergies, dislikes, tastes });
      } else await api.put('/child/preferences', { dislikes, tastes });
      wx.showToast({ title: '已保存', icon: 'success' });
      await this.read();
    });
  },
  clearForm() { this.setData({ ready: false, nickname: '', school: '', grade: '', allergies: [], dislikes: [], tastes: [], dislikesText: '', tastesText: '', allergyOptions: [], schoolOptions: [] }); },
  onHide() { this.clearForm(); },
  onUnload() { this.clearForm(); }
});
