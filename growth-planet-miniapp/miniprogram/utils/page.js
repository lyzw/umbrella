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
function openPage(page, url) {
  if (!page || !url || page.__navigationPending || typeof wx.navigateTo !== 'function') return null;
  page.__navigationPending = true;
  let released = false;
  const release = () => {
    if (released) return;
    released = true;
    page.__navigationPending = false;
  };
  try {
    return wx.navigateTo({ url, complete: release });
  } catch (error) {
    release();
    throw error;
  }
}
function go(event) {
  const page = event && event.currentTarget && event.currentTarget.dataset && event.currentTarget.dataset.page;
  if (!page) return null;
  return openPage(this, '/pages/' + page + '/index');
}
const CHILD_TAB_ROUTES = {
  meal: '/pages/home/index?tab=meal',
  task: '/pages/chore/index',
  growth: '/pages/home/index?tab=growth',
  me: '/pages/home/index?tab=me'
};
const PARENT_TAB_ROUTES = {
  home: '/pages/home/index?tab=home',
  approvals: '/pages/confirmation/index',
  wallet: '/pages/wallet/index',
  me: '/pages/home/index?tab=me'
};
function openTab(page, key) {
  const role = page && page.data && page.data.role;
  const routes = role === 'CHILD' ? CHILD_TAB_ROUTES : PARENT_TAB_ROUTES;
  const url = routes[key];
  if (!url) return null;
  if (typeof wx.reLaunch === 'function') return wx.reLaunch({ url });
  if (typeof wx.navigateTo === 'function') return wx.navigateTo({ url });
  return null;
}
function back(page, fallbackTab) {
  let pages = [];
  if (typeof getCurrentPages === 'function') {
    try { pages = getCurrentPages() || []; } catch (error) { pages = []; }
  }
  if (pages.length > 1 && typeof wx.navigateBack === 'function') {
    return wx.navigateBack({ delta: 1 });
  }
  const role = page && page.data && page.data.role;
  const isChild = role === 'CHILD';
  const routes = isChild ? CHILD_TAB_ROUTES : PARENT_TAB_ROUTES;
  const tab = routes[fallbackTab] ? fallbackTab : (isChild ? 'meal' : 'home');
  return openTab(page, tab);
}
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
module.exports = { page, guard, run, input, go, openPage, openTab, back, confirm, loginCode };
