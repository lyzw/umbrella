const session = require('../services/session');
const lifecycle = require('../services/lifecycle');
function page(definition) {
  const show = definition.onShow, hide = definition.onHide, unload = definition.onUnload;
  for (const [name, handler] of Object.entries(definition)) {
    if (typeof handler === 'function' && !name.startsWith('on')) {
      definition[name] = function (...args) {
        if (name !== 'imageError' && args[0] && args[0].currentTarget && this.data.busy) return;
        return handler.apply(this, args);
      };
    }
  }
  definition.onShow = function () {
    lifecycle.invalidate();
    this.setData({ busy: false, error: '' });
    return show && show.call(this);
  };
  definition.onHide = function () {
    lifecycle.invalidate();
    return hide && hide.call(this);
  };
  definition.onUnload = function () {
    lifecycle.invalidate();
    return (unload || hide) && (unload || hide).call(this);
  };
  Page(definition);
}
function guard(page, role) {
  const auth = session.get();
  if (!auth || auth.role === 'UNSELECTED') {
    wx.redirectTo({ url: '/pages/login/index' });
    return false;
  }
  if (role && auth.role !== role) {
    wx.redirectTo({ url: '/pages/home/index' });
    return false;
  }
  page.setData({ role: auth.role, error: '' });
  return true;
}
async function run(page, action) {
  if (page.data.busy) return;
  const generation = session.generation();
  const revision = lifecycle.current();
  page.setData({ busy: true, error: '' });
  try { await action(); }
  catch (error) {
    if (revision !== lifecycle.current()) return;
    if (error.cancelled) {
      if (!session.get()) wx.reLaunch({ url: '/pages/login/index' });
      return;
    }
    page.setData({ error: error.message || '操作失败，请稍后重试' });
    if (error.status === 401 || (generation !== session.generation() && !session.get())) {
      wx.reLaunch({ url: '/pages/login/index' });
    } else if (error.code === 'E-010') {
      require('../services/context').clear();
      wx.reLaunch({ url: '/pages/family/index' });
    }
  } finally { if (revision === lifecycle.current()) page.setData({ busy: false }); }
}
function input(event) {
  this.setData({ [event.currentTarget.dataset.field]: event.detail.value });
}
function go(event) { wx.navigateTo({ url: '/pages/' + event.currentTarget.dataset.page + '/index' }); }
function confirm(content, title = '请确认') {
  const revision = lifecycle.current();
  return new Promise(resolve => wx.showModal({ title, content, confirmText: '确认', cancelText: '取消',
    success: result => resolve(revision === lifecycle.current() && result.confirm), fail: () => resolve(false) }));
}
function loginCode() {
  const revision = lifecycle.current();
  return new Promise((resolve, reject) => wx.login({ success: r => {
    if (revision !== lifecycle.current()) return reject(Object.assign(new Error('页面已关闭'), { cancelled: true }));
    r.code ? resolve(r.code) : reject(new Error('微信登录失败'));
  },
    fail: () => reject(new Error('微信登录失败，请重试')) }));
}
module.exports = { page, guard, run, input, go, confirm, loginCode };
