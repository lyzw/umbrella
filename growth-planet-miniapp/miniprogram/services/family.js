// 多家庭：当前「激活家庭」的客户端上下文。
//
// 后端以 X-Family-Id 选择家庭（缺省回落首家庭，见后端 LoginUser.activeFamilyId）。
// 本模块负责在客户端记住用户选择，并由 services/client.js 注入到每个请求的 header。
//
// 重要：切换家庭 ≠ 登出。绝不要在此调用 session.clear()（那会清空登录态）。
// 切换后由调用方（家庭页 switchFamily）reLaunch 到首页刷新各页数据即可。

const KEY = 'growth-planet-active-family-v1';
let activeId = null;

function clear() {
  activeId = null;
  try { wx.removeStorageSync(KEY); } catch (e) { /* storage 不可用时忽略 */ }
}

function get() {
  return activeId;
}

function set(id) {
  activeId = id ? String(id) : null;
  try {
    if (activeId) wx.setStorageSync(KEY, activeId);
    else wx.removeStorageSync(KEY);
  } catch (e) { /* storage 不可用时忽略 */ }
}

function restore() {
  try {
    const v = wx.getStorageSync(KEY);
    activeId = (typeof v === 'string' && v) ? v : null;
  } catch (e) { activeId = null; }
}

module.exports = { get, set, clear, restore, activeFamilyId: get };
