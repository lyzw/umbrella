// 生产默认配置（发布形态）。Node 测试环境可通过 GP_CONFIG_JSON 覆盖（见下方钩子）；
// 微信小程序运行时没有 process 对象，恒走此默认值。
const config = {
  // 生产：接口走 API 网关域名（nginx 反代 127.0.0.1:19100，见 deploy/nginx/conf.d/growth-planet.conf）。
  // 发布自检（services/api.js）：正式版要求 apiBase 为 https 且 syntheticLogin=false、realDataApproved=true，
  // 任一不满足则所有请求返回 E-403「当前版本尚未开放服务」。
  apiBase: 'https://api.studykid.cn',
  environment: 'production',
  syntheticLogin: false,
  realDataApproved: true,
  requestTimeout: 12000,
  // These catalogs are synthetic development values, not approved production vocabularies.
  grades: ['一年级', '二年级', '三年级', '四年级', '五年级', '六年级'],
  allergens: ['PEANUT', 'MILK', 'EGG'],
  // 学校发布目录：必须与后端 compliance.schools 一致（预上线联调目录，见
  // deploy/app/config/application.yml）。校名是学校菜单的归属键文本，双方都从这里取值。
  schools: ['联调第一小学', '联调第二小学', '张江竹园中学'],
  // 七牛对象存储读取域名（已绑定并签 DV 证书的公开访问域名）。只有该域名下的菜品图片，
  // 才会在小程序端追加七牛 imageView2 缩略处理参数；用户自填的其他 HTTPS 图片不处理。
  imageCdn: 'https://image.studykid.cn'
};

// 测试钩子：仅在 Node 环境（node --test）下生效，让页面级测试固定运行在开发合成配置上；
// 小程序运行时无 process，不会进入此分支。
if (typeof process !== 'undefined' && process.env && process.env.GP_CONFIG_JSON) {
  try { Object.assign(config, JSON.parse(process.env.GP_CONFIG_JSON)); } catch (e) { /* 覆盖串非法则忽略 */ }
}

module.exports = Object.freeze(config);
