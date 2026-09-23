const test = require('node:test');
const assert = require('node:assert/strict');
const { cents, money, shanghaiDate, selectable, filterDishes } = require('../miniprogram/utils/domain');
const { createOperations } = require('../miniprogram/services/operations');
const { createClient } = require('../miniprogram/services/client');
const recipes = require('../miniprogram/services/recipes');

test('金额整数分运算，不接受负数、小数截断或科学计数法', () => {
  assert.equal(cents('18.01') + cents('0.09'), 1810);
  assert.equal(money(1810), '18.10');
  for (const value of ['-1', '1.001', '1e2', '', 'NaN']) assert.throws(() => cents(value));
});
test('上海日期按 UTC+8，与设备所在时区无关', () => {
  assert.equal(shanghaiDate(new Date('2026-09-20T16:00:00Z')), '2026-09-21');
});
test('无辣严格等于0，未知过敏原不可选择', () => {
  const dishes = [0, 1, 2, 3].map(spiceLevel => ({ spiceLevel, name: '餐食' }));
  assert.equal(filterDishes(dishes, { mildOnly: true }).length, 1);
  assert.equal(selectable({ canSelect: true, allergenStatus: 'UNKNOWN', status: 'ON_SALE' }), false);
  assert.equal(selectable({ canSelect: true, allergenStatus: 'DECLARED', allergyConflict: true, status: 'ON_SALE' }), false);
});
test('结果未知后同参重试保留键，变更入参被阻止', async () => {
  const ops = createOperations(() => 'fixed-key');
  let first;
  await assert.rejects(ops.run('grant', { amount: '10.00' }, (body, key) => {
    first = key;
    return Promise.reject(Object.assign(new Error('网络中断'), { unknown: true }));
  }));
  await assert.rejects(ops.run('grant', { amount: '11.00' }, () => {}), /查询原操作/);
  assert.equal(await ops.run('grant', { amount: '10.00' }, (body, key) => key), first);
  assert.equal(ops.pending('grant'), null);
});
test('请求客户端保留 E-011 预览，不自动重复写请求', async () => {
  let calls = 0;
  const client = createClient({ request(options) {
    calls++;
    options.success({ statusCode: 409, data: { code: 'E-011', message: '额度变化', data: { walletVersion: 2 } } });
  } }, () => ({ token: 't' }), () => {}, 'http://localhost:8080');
  await assert.rejects(client.post('/parent/approve/1/approve', { expectedVersion: 0 }), e =>
    e.code === 'E-011' && e.data.walletVersion === 2 && !e.unknown);
  assert.equal(calls, 1);
});
test('401清除会话，网络中断写操作标为结果未知', async () => {
  let cleared = 0;
  const client = createClient({ request(options) {
    options.success({ statusCode: 401, data: { code: 'E-001', message: '登录过期' } });
  } }, () => ({ token: 't' }), () => { cleared++; }, 'http://localhost:8080');
  await assert.rejects(client.get('/wallet/balance', { childId: '9007199254740993' }));
  assert.equal(cleared, 1);
  const failed = createClient({ request(options) { options.fail({ errMsg: 'timeout' }); } },
    () => ({ token: 't' }), () => {}, 'http://localhost:8080');
  await assert.rejects(failed.post('/wallet/grant', {}, 'key'), e => e.code === 'E-005' && e.unknown);
});
test('旧账号迟到的401不能清除新账号会话', async () => {
  let response, cleared = 0, token = 'old';
  const client = createClient({ request(options) { response = options; } },
    () => ({ token }), () => { cleared++; }, 'http://localhost:8080');
  const pending = client.get('/wallet/balance');
  token = 'new';
  response.success({ statusCode: 401, data: { code: 'E-001' } });
  await assert.rejects(pending, error => error.cancelled);
  assert.equal(cleared, 0);
});
test('旧页面写请求迟到仍标为未知，不允许新页面误认失败', async () => {
  let response, revision = 1;
  const client = createClient({ request(options) { response = options; } },
    () => ({ token: 'same' }), () => {}, 'http://localhost:8080', 12000, () => revision);
  const pending = client.post('/wallet/grant', {}, 'key');
  revision++;
  response.success({ statusCode: 200, data: { code: 0, data: {} } });
  await assert.rejects(pending, error => error.cancelled && error.unknown);
});
test('清理后的旧请求不能删除新会话同名操作', async () => {
  const ops = createOperations();
  let finish;
  const old = ops.run('grant:1', {}, () => new Promise(resolve => { finish = resolve; }));
  ops.clear();
  await assert.rejects(ops.run('grant:1', { amount: '2' }, () => Promise.reject(Object.assign(new Error('timeout'), { unknown: true }))));
  finish({});
  await old;
  assert.equal(ops.pending('grant:1').body.amount, '2');
});
test('鉴权JSON导出使用原始文档，错误仍遵循统一错误处理', async () => {
  const client = createClient({ request(options) {
    assert.equal(options.header.Authorization, 'Bearer t');
    options.success({ statusCode: 200, data: { format: 'PROFILE_RIGHTS_V1', childId: '999' } });
  } }, () => ({ token: 't' }), () => {}, 'http://localhost:8080');
  assert.equal((await client.getDocument('/compliance/requests/1/download')).childId, '999');
});
test('会话到期清理token及未知结果命令，不保存儿童档案字段', async () => {
  const session = require('../miniprogram/services/session');
  const { operations } = require('../miniprogram/services/operations');
  const storage = new Map();
  global.wx = { getStorageSync: key => storage.get(key), setStorageSync: (key, value) => storage.set(key, value),
    removeStorageSync: key => storage.delete(key) };
  session.set({ token: 't', role: 'PARENT', expiresIn: 1800, profile: { nickname: '敏感' } });
  assert.deepEqual(Object.keys(Array.from(storage.values())[0]).sort(), ['expiresAt', 'role', 'token']);
  await assert.rejects(operations.run('grant:1', {}, () => Promise.reject(Object.assign(new Error('timeout'), { unknown: true }))));
  session.get().expiresAt = Date.now() - 1;
  assert.equal(session.get(), null);
  assert.equal(storage.size, 0);
  assert.equal(operations.pending('grant:1'), null);
});
test('配方：难度映射与下标互转对称，未知值不越位', () => {
  assert.equal(recipes.difficultyLabel('EASY'), '简单');
  assert.equal(recipes.difficultyLabel('HARD'), '有挑战');
  assert.equal(recipes.difficultyLabel(''), '');
  assert.equal(recipes.difficultyLabel(null), '');
  assert.equal(recipes.difficultyLabel('LEGACY'), 'LEGACY', '未知枚举原样展示，不静默吞掉');
  assert.equal(recipes.difficultyIndex('MEDIUM'), 2);
  assert.equal(recipes.difficultyIndex('LEGACY'), 0, '未知值落到「未设置」，不误选相邻档位');
  assert.equal(recipes.difficultyValue(3), 'HARD');
  assert.equal(recipes.difficultyValue(undefined), '');
});
test('配方：展示归一化区分「空配方」与「字段为空」', () => {
  const full = recipes.presentRecipe({
    ingredients: [{ name: '番茄', amount: '2 个' }, { name: '鸡蛋', amount: null }],
    cookSteps: ['打蛋', '下锅'], cookTips: '少放盐', cookMinutes: 15, servings: 3, difficulty: 'EASY'
  });
  assert.equal(full.status, 'ready');
  assert.equal(full.empty, false);
  assert.equal(full.ingredients[1].amountText, '适量', '未填用量显示「适量」而不是空白');
  assert.deepEqual(full.steps.map(step => step.no), [1, 2]);
  assert.equal(full.meta, '15 分钟 · 3 人份 · 简单');
  assert.equal(recipes.presentRecipe({ ingredients: [], cookSteps: [], cookTips: null }).empty, true);
  assert.equal(recipes.presentRecipe(null).empty, true, '响应缺字段不能当成有配方');
});
test('配方：列表摘要缺项省略，全缺时给出明确文案', () => {
  assert.equal(recipes.recipeSummary({ ingredientCount: 3, stepCount: 5, cookMinutes: 30 }),
    '食材 3 · 步骤 5 · 30 分钟');
  assert.equal(recipes.recipeSummary({ ingredientCount: 3 }), '食材 3');
  assert.equal(recipes.recipeSummary({}), '未填写配方');
  assert.equal(recipes.recipeSummary(null), '未填写配方');
  assert.equal(recipes.recipeKey('FAMILY', '7'), 'FAMILY:7', '两表自增序列独立，必须带 type');
});
