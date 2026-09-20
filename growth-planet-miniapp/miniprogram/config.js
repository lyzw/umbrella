module.exports = Object.freeze({
  apiBase: 'http://localhost:8080',
  environment: 'development',
  syntheticLogin: true,
  realDataApproved: false,
  requestTimeout: 12000,
  // These catalogs are synthetic development values, not approved production vocabularies.
  grades: ['一年级', '二年级', '三年级', '四年级', '五年级', '六年级'],
  allergens: ['PEANUT', 'MILK', 'EGG']
});
