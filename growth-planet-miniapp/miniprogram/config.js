module.exports = Object.freeze({
  apiBase: 'http://localhost:8080',
  environment: 'development',
  syntheticLogin: true,
  realDataApproved: false,
  requestTimeout: 12000,
  // These catalogs are synthetic development values, not approved production vocabularies.
  grades: ['一年级', '二年级', '三年级', '四年级', '五年级', '六年级'],
  allergens: ['PEANUT', 'MILK', 'EGG'],
  // 学校发布目录：必须与后端 compliance.schools 一致。校名是学校菜单的归属键文本，
  // 双方都从这里取值，避免手输错字导致「该校孩子看不到菜单」。
  schools: ['合成第一小学', '合成第二小学', '合成第三小学']
});
