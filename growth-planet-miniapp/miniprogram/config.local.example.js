// 本地环境覆盖模板：复制为 config.local.js（已被 .gitignore 忽略，不会进提交）即可生效。
//
//   cp miniprogram/config.local.example.js miniprogram/config.local.js
//
// config.local.js 的 env 优先级高于 config.js 里的 DEFAULT_ENV，但低于小程序内
// 「设置 → 后端环境」的切换缓存，且正式版恒强制 prod。
module.exports = {
  // 可选值：local（本机）/ lan（真机联调）/ test（测试预发）/ prod（生产）
  env: 'local'
};
