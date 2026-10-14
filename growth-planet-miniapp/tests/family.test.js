// 多家庭激活上下文：get/set/clear/restore 与 storage 持久化。
const test = require('node:test');
const assert = require('node:assert');
const path = require('node:path');

const FAMILY_MODULE = path.resolve(__dirname, '../miniprogram/services/family');
const KEY = 'growth-planet-active-family-v1';

function load({ store = {} } = {}) {
  const s = Object.assign({}, store);
  global.wx = {
    getStorageSync: k => (k in s ? s[k] : ''),
    setStorageSync: (k, v) => { s[k] = v; },
    removeStorageSync: k => { delete s[k]; }
  };
  delete require.cache[require.resolve(FAMILY_MODULE)];
  return { family: require(FAMILY_MODULE), store: s };
}

test('默认无激活家庭：get 返回 null', () => {
  const { family } = load();
  assert.equal(family.get(), null);
});

test('set 后 get 返回同值，并写入 storage（统一为字符串）', () => {
  const { family, store } = load();
  family.set(12);
  assert.equal(family.get(), '12');
  assert.equal(store[KEY], '12');
});

test('set(null) 等价于清除', () => {
  const { family, store } = load({ store: { [KEY]: '9' } });
  family.set(null);
  assert.equal(family.get(), null);
  assert.equal(KEY in store, false);
});

test('clear 清空内存与 storage', () => {
  const { family, store } = load({ store: { [KEY]: '9' } });
  family.clear();
  assert.equal(family.get(), null);
  assert.equal(KEY in store, false);
});

test('restore 从 storage 恢复', () => {
  const { family } = load({ store: { [KEY]: '7' } });
  family.restore();
  assert.equal(family.get(), '7');
});

test('restore 无 storage 时为 null', () => {
  const { family } = load();
  family.restore();
  assert.equal(family.get(), null);
});
