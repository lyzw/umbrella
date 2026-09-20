const KEY = 'growth-planet-session-v1';
let current = null;
let generation = 0;
function clear() {
  current = null;
  generation++;
  wx.removeStorageSync(KEY);
  require('./context').clear();
  require('./operations').operations.clear();
}
function get() {
  if (current && current.expiresAt <= Date.now()) clear();
  return current;
}
function set(auth) {
  if (!auth.token || !['CHILD', 'PARENT', 'UNSELECTED'].includes(auth.role)) {
    throw new Error('当前账号不能使用小程序');
  }
  const expiresIn = Number(auth.expiresIn || 1800);
  if (!Number.isFinite(expiresIn) || expiresIn <= 0) throw new Error('会话期限无效');
  current = { token: auth.token, role: auth.role, expiresAt: Date.now() + expiresIn * 1000 };
  generation++;
  wx.setStorageSync(KEY, current);
}
function restore() {
  const stored = wx.getStorageSync(KEY);
  if (stored && typeof stored.token === 'string' && ['CHILD', 'PARENT', 'UNSELECTED'].includes(stored.role)
      && Number.isFinite(stored.expiresAt) && stored.expiresAt > Date.now()) current = stored;
  else clear();
}
module.exports = { get, set, clear, restore, generation: () => generation };
