// 多环境配置：local（本机）/ lan（真机联调）/ test（测试预发）/ prod（生产）。
//
// 指定环境的方式（优先级从高到低）：
//   ① 小程序内切换：「设置 → 后端环境」（仅开发版/体验版出现），写入本地缓存，重启小程序生效
//   ② 本地文件覆盖：miniprogram/config.local.js（.gitignore 已忽略）写 module.exports = { env: 'local' }
//   ③ 代码默认值：下方 DEFAULT_ENV 常量
//   · Node 测试：环境变量 GP_CONFIG_JSON（见文件末尾钩子）
//   · 正式版（envVersion=release）恒强制 prod，①②③ 全部忽略 —— 防止误把测试地址带到线上。
//
// 注意：环境在 require 时就固定（client.js 以 config.apiBase 建客户端），因此 ① 需重启小程序才生效。

// 后端接口地址与登录模式：按环境区分。其余与后端无关注解见 COMMON。
const ENVIRONMENTS = {
  // 本机后端：直连本机的 Spring Boot（默认端口 19100）。开发者工具需关闭域名校验（urlCheck）。
  local: {
    apiBase: 'http://localhost:19100',
    environment: 'local',
    syntheticLogin: true,
    realDataApproved: false
  },
  // 真机联调：手机与电脑同一局域网，填电脑的局域网 IP。IP 变动时改这里。
  lan: {
    apiBase: 'http://192.168.0.103:19100',
    environment: 'lan',
    syntheticLogin: true,
    realDataApproved: false
  },
  // 测试/预发：联调服务器。若该后端也跑合成模式，把 syntheticLogin 改为 true。
  test: {
    apiBase: 'https://test-api.studykid.cn',
    environment: 'test',
    syntheticLogin: false,
    realDataApproved: true
  },
  // 生产：接口走 API 网关域名。
  prod: {
    apiBase: 'https://api.studykid.cn',
    environment: 'production',
    syntheticLogin: false,
    realDataApproved: true
  }
};

const ENV_ORDER = ['local', 'lan', 'test', 'prod'];
const ENV_LABEL = {
  local: '本机 localhost:19100',
  lan: '真机联调（局域网）',
  test: '测试 / 预发',
  prod: '生产'
};

// 编译期默认环境：改这一行即可整体切换（提交前请还原为 prod）。
const DEFAULT_ENV = 'local';
// 运行期覆盖的本地缓存键（与 pages/dev-env 共用）。
const ENV_STORAGE_KEY = 'growth-planet-env-v1';

// 与后端地址无关的公共配置：四套环境共用。
const COMMON = {
  requestTimeout: 12000,
  // These catalogs are synthetic development values, not approved production vocabularies.
  grades: ['一年级', '二年级', '三年级', '四年级', '五年级', '六年级'],
  allergens: ['PEANUT', 'MILK', 'EGG'],
  // 学校发布目录：必须与后端 compliance.schools 一致（预上线联调目录，见
  // deploy/app/config/application.yml）。校名是学校菜单的归属键文本，双方都从这里取值。
  schools: ['联调第一小学', '联调第二小学', '张江竹园中学'],
  // 七牛对象存储读取域名（已绑定并签 DV 证书的公开访问域名）：四套环境统一走生产 CDN，
  // 这样本地/联调环境也能直接看到线上已上传的菜品图片。只有该域名下的图片才会在小程序端
  // 追加七牛 imageView2 缩略参数；用户自填的其他 HTTPS 图片不处理。
  imageCdn: 'https://image.studykid.cn'
};

// 当前小程序版本：develop（开发版）/ trial（体验版）/ release（正式版）。
// Node 测试环境拿不到 wx，返回 null 表示「未知」，按非 release 处理。
function miniProgramVersion() {
  try {
    if (typeof wx !== 'undefined' && typeof wx.getAccountInfoSync === 'function') {
      const info = wx.getAccountInfoSync() || {};
      return (info.miniProgram || {}).envVersion || 'release';
    }
  } catch (e) { /* 启动早期或 Node 环境拿不到版本信息 */ }
  return null;
}

const isRelease = miniProgramVersion() === 'release';

// ① 运行期覆盖：小程序内切换环境写入的缓存，正式版一律不读。
function storedEnv() {
  if (isRelease || ENV_STORAGE_KEY === '') return null;
  try {
    if (typeof wx !== 'undefined' && typeof wx.getStorageSync === 'function') {
      return wx.getStorageSync(ENV_STORAGE_KEY) || null;
    }
  } catch (e) { /* 缓存不可用时回落默认环境 */ }
  return null;
}

// ② 本地文件覆盖：config.local.js 是可选文件（已被 .gitignore 忽略），不存在时 require 抛错，静默忽略。
function localEnv() {
  try {
    const local = require('./config.local');
    return (local && local.env) || null;
  } catch (e) {
    return null;
  }
}

function resolveEnvName() {
  if (isRelease) return 'prod';
  const stored = storedEnv();
  if (stored && ENVIRONMENTS[stored]) return stored;
  const local = localEnv();
  if (local && ENVIRONMENTS[local]) return local;
  return ENVIRONMENTS[DEFAULT_ENV] ? DEFAULT_ENV : 'prod';
}

const envName = resolveEnvName();

const config = Object.assign({}, COMMON, ENVIRONMENTS[envName], {
  envName,
  envLabel: ENV_LABEL[envName] || envName,
  isRelease,
  envStorageKey: ENV_STORAGE_KEY,
  // 供「设置 → 后端环境」列出可选项。
  envOptions: ENV_ORDER.map(name => ({
    name,
    label: ENV_LABEL[name],
    apiBase: ENVIRONMENTS[name].apiBase,
    syntheticLogin: ENVIRONMENTS[name].syntheticLogin,
    current: name === envName
  }))
});

// 测试钩子：仅在 Node 环境（node --test）下生效，让页面级测试固定运行在开发合成配置上；
// 小程序运行时无 process，不会进入此分支。优先级最高，覆盖上面算出的环境。
if (typeof process !== 'undefined' && process.env && process.env.GP_CONFIG_JSON) {
  try { Object.assign(config, JSON.parse(process.env.GP_CONFIG_JSON)); } catch (e) { /* 覆盖串非法则忽略 */ }
}

module.exports = Object.freeze(config);
