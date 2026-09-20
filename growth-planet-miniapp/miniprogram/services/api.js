const config = require('../config');
const session = require('./session');
const { createClient } = require('./client');
const api = createClient({
  request(options) {
    const version = wx.getAccountInfoSync().miniProgram.envVersion;
    const release = version !== 'develop';
    if (release && (config.syntheticLogin || !config.realDataApproved || !/^https:\/\//.test(config.apiBase))) {
      options.success({ statusCode: 403, data: { code: 'E-403', message: '当前版本尚未开放服务' } });
      return;
    }
    wx.request(options);
  }
}, session.get, session.clear, config.apiBase, config.requestTimeout, require('./lifecycle').current);
module.exports = api;
