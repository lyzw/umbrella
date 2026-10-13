// 多环境配置解析：验证环境优先级与正式版强制生产。
const test = require('node:test');
const assert = require('node:assert');
const path = require('node:path');

const CONFIG_MODULE = path.resolve(__dirname, '../miniprogram/config');
const STORAGE_KEY = 'growth-planet-env-v1';

// 模拟小程序运行时后重新加载 config（config 在 require 时求值，必须清缓存）。
function loadConfig({ envVersion = 'develop', storage = {}, configJson = null } = {}) {
  const store = Object.assign({}, storage);
  global.wx = {
    getAccountInfoSync: () => ({ miniProgram: { envVersion } }),
    getStorageSync: key => (key in store ? store[key] : ''),
    setStorageSync: (key, value) => { store[key] = value; },
    removeStorageSync: key => { delete store[key]; }
  };
  if (configJson) process.env.GP_CONFIG_JSON = JSON.stringify(configJson);
  else delete process.env.GP_CONFIG_JSON;
  delete require.cache[require.resolve(CONFIG_MODULE)];
  return { config: require(CONFIG_MODULE), store };
}

test('默认环境是生产：接口走 https 网关域名且关闭合成登录', () => {
  const { config } = loadConfig();
  assert.equal(config.envName, 'prod');
  assert.equal(config.apiBase, 'https://api.studykid.cn');
  assert.equal(config.syntheticLogin, false);
  assert.equal(config.realDataApproved, true);
});

test('四套环境齐备：local/lan/test/prod，且当前项被标记', () => {
  const { config } = loadConfig();
  assert.deepEqual(config.envOptions.map(o => o.name), ['local', 'lan', 'test', 'prod']);
  const current = config.envOptions.filter(o => o.current);
  assert.equal(current.length, 1);
  assert.equal(current[0].name, config.envName);
  assert.equal(config.envOptions.filter(o => o.name === 'local')[0].apiBase, 'http://localhost:19100');
});

test('开发版读取运行期切换缓存：切到 local 后走本机后端并启用合成登录', () => {
  const { config } = loadConfig({ storage: { [STORAGE_KEY]: 'local' } });
  assert.equal(config.envName, 'local');
  assert.equal(config.apiBase, 'http://localhost:19100');
  assert.equal(config.syntheticLogin, true);
});

test('体验版同样允许切换到 test', () => {
  const { config } = loadConfig({ envVersion: 'trial', storage: { [STORAGE_KEY]: 'test' } });
  assert.equal(config.envName, 'test');
  assert.equal(config.apiBase, 'https://test-api.studykid.cn');
});

test('正式版强制生产：即使缓存里写了 local 也忽略', () => {
  const { config } = loadConfig({ envVersion: 'release', storage: { [STORAGE_KEY]: 'local' } });
  assert.equal(config.envName, 'prod');
  assert.equal(config.apiBase, 'https://api.studykid.cn');
  assert.equal(config.isRelease, true);
});

test('未知环境名（脏缓存）回落到默认环境', () => {
  const { config } = loadConfig({ storage: { [STORAGE_KEY]: 'not-a-env' } });
  assert.equal(config.envName, 'prod');
});

test('图片 CDN 四套环境统一走生产域名', () => {
  ['local', 'lan', 'test', 'prod'].forEach(name => {
    const { config } = loadConfig({ storage: { [STORAGE_KEY]: name } });
    assert.equal(config.imageCdn, 'https://image.studykid.cn');
  });
});

test('Node 测试钩子 GP_CONFIG_JSON 优先级最高', () => {
  const { config } = loadConfig({
    storage: { [STORAGE_KEY]: 'test' },
    configJson: { apiBase: 'http://localhost:8080', envName: 'development', syntheticLogin: true, realDataApproved: false }
  });
  assert.equal(config.apiBase, 'http://localhost:8080');
  assert.equal(config.envName, 'development');
  assert.equal(config.syntheticLogin, true);
});
