const test = require('node:test');
const assert = require('node:assert/strict');
const path = require('node:path');
const root = path.resolve(__dirname, '../miniprogram');
const api = require('../miniprogram/services/api');
const session = require('../miniprogram/services/session');
const lifecycle = require('../miniprogram/services/lifecycle');
const { operations } = require('../miniprogram/services/operations');
const context = require('../miniprogram/services/context');
const ui = require('../miniprogram/utils/page');
const { shanghaiDate } = require('../miniprogram/utils/domain');
const methods = { get: api.get, post: api.post, put: api.put, getDocument: api.getDocument };
let modals, navigation;
function loadPage(name, role = 'PARENT') {
  modals = [];
  navigation = [];
  global.wx = {
    setStorageSync() {}, removeStorageSync() {}, getStorageSync() {},
    showModal(options) { modals.push(options); options.success({ confirm: true }); },
    reLaunch(options) { navigation.push(options.url); }, redirectTo(options) { navigation.push(options.url); },
    navigateTo(options) { navigation.push(options.url); }, showToast() {},
    getAccountInfoSync: () => ({ miniProgram: { envVersion: 'develop' } })
  };
  session.clear();
  session.set({ token: 'test-token', role, expiresIn: 1800 });
  Object.assign(api, methods);
  let definition;
  global.Page = value => { definition = value; };
  const filename = path.join(root, 'pages', name, 'index.js');
  delete require.cache[require.resolve(filename)];
  require(filename);
  const page = { ...definition, data: structuredClone(definition.data),
    setData(data) { Object.assign(this.data, data); } };
  page.data.role = role;
  if (page.onLoad) page.onLoad({});
  return page;
}
const event = (dataset = {}, value) => ({ currentTarget: { dataset }, detail: { value } });
const child = { childId: '9007199254740993', bindStatus: 'BOUND', applyId: '12' };
const dish = { dishId: '99', name: '合成餐食', virtualPrice: '10.01', spiceLevel: 0,
  canSelect: true, status: 'ON_SALE', allergenStatus: 'DECLARED', allergyConflict: false };

test('新家长无家庭的403展示建家庭入口，但保留权限提醒', async () => {
  const page = loadPage('family');
  api.get = async () => { throw Object.assign(new Error('无权限'), { status: 403, code: 'E-009' }); };
  await page.refresh();
  assert.equal(page.data.hasFamily, false);
  assert.match(page.data.error, /尚无可访问的家庭/);
});
test('家长同意失效时绝不读取或显示档案', async () => {
  const page = loadPage('profile');
  page.data.childId = child.childId;
  page.data.nickname = '旧表单';
  const calls = [];
  api.get = async endpoint => { calls.push(endpoint); return { currentStatus: 'REVOKED' }; };
  await assert.rejects(page.read(), /有效同意/);
  assert.deepEqual(calls, ['/compliance/consent']);
  assert.equal(page.data.ready, false);
  assert.equal(page.data.nickname, '');
});
test('儿童仅提交非安全偏好，不注入childId或allergies', async () => {
  const page = loadPage('profile', 'CHILD');
  page.setData({ ready: true, childId: child.childId, allergies: ['PEANUT'], dislikesText: '芹菜', tastesText: '清淡' });
  let sent;
  api.put = async (endpoint, body) => { sent = { endpoint, body }; };
  api.get = async () => ({ dislikes: ['芹菜'], tastes: ['清淡'] });
  await page.save();
  assert.deepEqual(sent, { endpoint: '/child/preferences', body: { dislikes: ['芹菜'], tastes: ['清淡'] } });
  page.onHide();
  assert.equal(page.data.dislikesText, '');
});
test('档案已有的非配置过敏原仍保留在回显选项中', async () => {
  const page = loadPage('profile');
  api.get = async endpoint => endpoint.includes('consent') ? { currentStatus: 'GRANTED' }
    : { allergies: ['FISH'], dislikes: [], tastes: [] };
  await page.read();
  assert.ok(page.data.allergyOptions.some(option => option.code === 'FISH' && option.checked));
});
test('处理中不允许菜单来源或儿童选择变化', () => {
  const page = loadPage('menu');
  page.data.busy = true;
  page.data.children = [child];
  page.date(event({}, '2026-09-01'));
  page.source(event({ source: 'SCHOOL' }));
  page.child(event({}, 0));
  assert.equal(page.data.menuDate, shanghaiDate());
  assert.equal(page.data.sourceType, 'FAMILY');
  assert.equal(page.data.childId, '');
});
test('家长只读，学校餐单和过期餐单都不能进入提报', () => {
  const page = loadPage('menu');
  page.allDishes = [dish];
  page.quantities = { '99': 1 };
  page.setData({ count: 1, childId: child.childId, menu: { menuId: '20', canSubmit: true, menuDate: shanghaiDate(), mealType: 'LUNCH' } });
  page.checkout();
  assert.equal(context.takeCart(), null);
  page.data.role = 'CHILD';
  page.data.sourceType = 'SCHOOL';
  page.checkout();
  assert.equal(context.takeCart(), null);
  page.data.sourceType = 'FAMILY';
  page.data.menuDate = '2020-01-01';
  page.checkout();
  assert.equal(context.takeCart(), null);
  page.data.menuDate = shanghaiDate();
  page.checkout();
  assert.equal(context.takeCart().items[0].dishId, '99');
});
test('晚餐重新提报恢复原餐次及建议，并保留原单关联', async () => {
  const page = loadPage('menu', 'CHILD');
  page.onLoad({ previousConfirmId: '5' });
  api.get = async (endpoint, query) => {
    if (endpoint === '/family/binding') return child;
    if (endpoint === '/menu/confirm/5') return { status: 'REJECTED', menuId: '20', menuDate: shanghaiDate(),
      mealType: 'DINNER', suggestedItems: [{ dishId: '99', quantity: 2 }] };
    assert.equal(query.mealType, 'DINNER');
    return { menuId: '20', sourceType: 'FAMILY', menuDate: shanghaiDate(), mealType: 'DINNER', canSubmit: true, dishes: [dish] };
  };
  // onShow schedules the common busy action; wait one event-loop turn.
  page.onShow();
  await new Promise(resolve => setImmediate(resolve));
  assert.equal(page.data.mealType, 'DINNER');
  assert.equal(page.data.total, '20.02');
  page.checkout();
  assert.equal(context.takeCart().previousConfirmId, '5');
});
test('提报只传服务端允许的字段，未知结果保留原键和请求', async () => {
  const page = loadPage('confirmation', 'CHILD');
  page.setData({ childId: child.childId, draft: { menuId: '20', previousConfirmId: '5',
    items: [{ dishId: '99', quantity: 1, unitPrice: '999.00', allergies: ['PEANUT'] }] } });
  const sent = [];
  api.post = async (endpoint, body, key) => {
    sent.push({ endpoint, body, key });
    if (sent.length === 1) throw Object.assign(new Error('timeout'), { unknown: true });
    return { confirmId: '21' };
  };
  api.get = async () => ({ confirmId: '21', status: 'COMPLETED' });
  await page.submit();
  assert.equal(page.data.pendingSubmit, true);
  await page.retrySubmit();
  assert.equal(sent[0].key, sent[1].key);
  assert.deepEqual(sent[0].body, { menuId: '20', items: [{ dishId: '99', quantity: 1 }], remark: '', previousConfirmId: '5' });
  assert.equal(page.data.pendingSubmit, false);
});
test('E-011绝不自动批准；二次同意使用完整且最新的预览版本', async () => {
  const page = loadPage('confirmation');
  page.data.detail = { confirmId: '21', version: 2, totalAmount: '20.00', status: 'PENDING' };
  const preview = { confirmVersion: 3, walletVersion: 4, ruleVersion: 5, usageDate: shanghaiDate(), totalAmount: '20.00' };
  const sent = [];
  api.post = async (endpoint, body) => {
    sent.push(body);
    if (sent.length === 1) throw Object.assign(new Error('额度超限'), { code: 'E-011', data: preview });
    return { confirmId: '21', status: 'COMPLETED' };
  };
  await page.approve();
  assert.equal(sent.length, 1);
  assert.equal(modals.length, 1);
  wx.showModal = options => options.success({ confirm: false });
  await page.explicitApprove();
  assert.equal(sent.length, 1);
  wx.showModal = options => options.success({ confirm: true });
  await page.explicitApprove();
  assert.deepEqual(sent[1], { expectedVersion: 3, explicitConfirm: true, walletVersion: 4,
    ruleVersion: 5, confirmVersion: 3, usageDate: shanghaiDate() });
});
test('离开确认页停止轮询并清空快照', () => {
  const page = loadPage('confirmation', 'CHILD');
  page.visible = true;
  page.data.detail = { confirmId: '21', status: 'PENDING' };
  page.poll();
  assert.ok(page.timer);
  page.onHide();
  assert.equal(page.visible, false);
  assert.equal(page.data.detail, null);
  assert.ok(page.timer._destroyed);
});
test('家长建议使用安全餐食，即使只读预览的canSelect为false', async () => {
  const page = loadPage('confirmation');
  page.data.detail = { confirmId: '21', childId: child.childId, menuDate: shanghaiDate(), mealType: 'DINNER' };
  api.get = async () => ({ dishes: [
    { ...dish, canSelect: false },
    { ...dish, dishId: '100', canSelect: false, allergenStatus: 'UNKNOWN' },
    { ...dish, dishId: '101', canSelect: false, allergyConflict: true }
  ] });
  await page.editSuggestion();
  assert.deepEqual(page.data.suggestions.map(d => d.dishId), ['99']);
});
test('钱包金额范围及版本控制；版本冲突不自动覆盖', async () => {
  const page = loadPage('wallet');
  page.setData({ childId: child.childId, rule: { version: 2 }, singleLimit: '20', dailyLimit: '10', weeklyLimit: '50' });
  let calls = 0;
  api.put = async (endpoint, body) => {
    calls++;
    assert.equal(body.expectedVersion, 2);
    throw Object.assign(new Error('版本冲突'), { code: 'E-007' });
  };
  await page.saveRule();
  assert.equal(calls, 0);
  page.data.dailyLimit = '30';
  await page.saveRule();
  assert.equal(calls, 1);
  assert.equal(page.data.rule, null);
  assert.match(page.data.error, /刷新后核对/);
});
test('钱包未知发放重试不能生成新键，查询超过31天在本地拒绝', async () => {
  const page = loadPage('wallet');
  page.setData({ childId: child.childId, amount: '10.50', reason: '合成奖励', startDate: '2026-08-01', endDate: '2026-09-18' });
  await assert.rejects(page.readLogs(), /31天/);
  let key;
  api.post = async (endpoint, body, requestKey) => {
    if (!key) { key = requestKey; throw Object.assign(new Error('timeout'), { unknown: true }); }
    assert.equal(requestKey, key);
    assert.equal(body.amount, '10.50');
    return { logId: '51' };
  };
  page.read = async () => {};
  await page.grant();
  assert.equal(page.data.pendingGrant, true);
  await page.retryGrant();
  assert.equal(operations.pending('grant:' + child.childId), null);
});
test('隐私删除需确认并核验，申请受理不显示为完成', async () => {
  const page = loadPage('privacy');
  page.setData({ childId: child.childId, account: 'parent_demo' });
  let body;
  api.post = async (endpoint, value, key) => {
    assert.equal(endpoint, '/compliance/data-delete');
    assert.ok(key);
    body = value;
    return { taskId: '88', status: 'RECEIVED', requestType: 'DELETE' };
  };
  await page.deleteRequest();
  assert.deepEqual(body, { childId: child.childId, confirmed: true, code: 'parent_demo' });
  assert.equal(page.data.task.label, '已受理');
  assert.equal(page.data.account, '');
  page.data.documentText = '{"profile":"sensitive"}';
  page.onHide();
  assert.equal(page.data.documentText, '');
});
test('未读消息映射真实后端事件，已读写入不伪造订阅送达', async () => {
  const page = loadPage('notices');
  api.get = async () => ({ items: [{ id: '9', eventType: 'CONFIRM_SUBMIT', read: false }], total: 1 });
  api.post = async endpoint => assert.equal(endpoint, '/notices/9/read');
  await page.refresh();
  assert.equal(page.data.records[0].title, '有新的餐单待确认');
  await page.read(event({ id: '9' }));
  assert.equal(page.data.records[0].read, true);
});
test('隐藏期间的迟到响应不会恢复敏感表单', async () => {
  const page = loadPage('profile');
  Object.assign(api, methods);
  let request;
  wx.request = options => { request = options; };
  page.data.childId = child.childId;
  const pending = ui.run(page, () => page.read());
  page.onHide();
  request.success({ statusCode: 200, data: { code: 0, data: { currentStatus: 'GRANTED' } } });
  await pending;
  assert.equal(page.data.ready, false);
  assert.equal(page.data.nickname, '');
  assert.equal(page.data.error, '');
});
test('页面离开期间的迟到确认框不会继续写操作', async () => {
  loadPage('home');
  let modal;
  wx.showModal = value => { modal = value; };
  const pending = ui.confirm('测试确认');
  lifecycle.invalidate();
  modal.success({ confirm: true });
  assert.equal(await pending, false);
});
test('请求期间会话到期仍回到登录页，而不是保留受保护页面', async () => {
  const page = loadPage('profile');
  Object.assign(api, methods);
  let request;
  wx.request = options => { request = options; };
  const pending = ui.run(page, () => page.read());
  session.get().expiresAt = Date.now() - 1;
  request.success({ statusCode: 200, data: { code: 0, data: { currentStatus: 'GRANTED' } } });
  await pending;
  assert.deepEqual(navigation, ['/pages/login/index']);
  assert.equal(page.data.ready, false);
});
test('合成配置不能在体验版或正式版发起网络请求', async () => {
  loadPage('home');
  Object.assign(api, methods);
  wx.getAccountInfoSync = () => ({ miniProgram: { envVersion: 'trial' } });
  let calls = 0;
  wx.request = () => { calls++; };
  await assert.rejects(api.get('/notices'), error => error.status === 403);
  assert.equal(calls, 0);
});
