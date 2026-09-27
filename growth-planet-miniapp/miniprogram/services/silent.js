// 静默登录服务：wx.login 取 code → /auth/wx-login 换取最小账号会话。
// 全程不索取昵称/头像，不弹授权；失败抛错由调用方（登录页）呈现并给重试入口。
const api = require('./api');
const session = require('./session');

function wxCode() {
  const revision = require('./lifecycle').current();
  return new Promise((resolve, reject) => wx.login({
    success: r => {
      if (revision !== require('./lifecycle').current()) {
        return reject(Object.assign(new Error('页面已关闭'), { cancelled: true }));
      }
      r.code ? resolve(r.code) : reject(new Error('微信登录未返回 code'));
    },
    fail: () => reject(new Error('微信登录失败，请检查网络后重试'))
  }));
}

// 用 code 完成静默登录并写入会话；返回 { token, role, isNew, expiresIn }。
async function silentLogin(code) {
  if (!code || !String(code).trim()) {
    throw new Error('缺少登录凭证');
  }
  const auth = await api.post('/auth/wx-login', { code: String(code).trim() });
  session.set(auth);
  return auth;
}

module.exports = { wxCode, silentLogin };
