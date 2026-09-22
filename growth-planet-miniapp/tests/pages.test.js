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
const methods = { get: api.get, post: api.post, put: api.put, del: api.del, getDocument: api.getDocument };
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
const dish = { dishId: '99', name: '合成餐食', virtualPrice: '10.01', spiceLevel: 0, sourceType: 'PRESET',
  canSelect: true, status: 'ON_SALE', allergenStatus: 'DECLARED', allergyConflict: false };
const familyDish = { dishId: '7', categoryId: '3', name: '家庭番茄炒蛋', virtualPrice: '8.00', spiceLevel: 1,
  sourceType: 'FAMILY', canSelect: true, status: 'ON_SALE', allergenStatus: 'DECLARED', allergyConflict: false };

test('新家长无家庭的403展示建家庭入口，但保留权限提醒', async () => {
  const page = loadPage('family');
  api.get = async () => { throw Object.assign(new Error('无权限'), { status: 403, code: 'E-009' }); };
  await page.refresh();
  assert.equal(page.data.hasFamily, false);
  assert.match(page.data.error, /尚无可访问的家庭/);
});
test('家庭绑定按同意与绑定状态推进四步流程', async () => {
  const page = loadPage('family');
  page.data.children = [{ ...child, nickname: '小星', displayName: '小星', bindStatusLabel: '已绑定' }];
  api.get = async () => ({ currentStatus: 'PENDING', agreementText: '协议', version: 'v1' });
  await page.inspect(event({ id: child.applyId }));
  assert.equal(page.data.currentBindingStep, 2);
  assert.deepEqual(page.data.bindingSteps.map(item => item.status), ['done', 'current', 'upcoming', 'upcoming']);

  api.get = async () => ({ currentStatus: 'GRANTED', agreementText: '协议', version: 'v1' });
  await page.inspect(event({ id: child.applyId }));
  assert.equal(page.data.currentBindingStep, 4);
  assert.deepEqual(page.data.bindingSteps.map(item => item.status), ['done', 'done', 'done', 'current']);
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
  page.quantities = { 'PRESET:99': 1 };
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
  assert.deepEqual(context.takeCart().items[0].dishRef, { type: 'PRESET', id: '99' });
});
test('家长加载当前家庭菜单并仅提交服务端允许的维护字段', async () => {
  const page = loadPage('menu');
  const secondDish = { ...dish, dishId: '100', name: '第二道餐食' };
  const calls = [];
  api.get = async (endpoint, query) => {
    calls.push({ endpoint, query });
    if (endpoint === '/parent/dish') {
      return { items: [dish, secondDish], total: 2, page: 1, pageSize: 100 };
    }
    if (endpoint === '/parent/family-dish') {
      return { items: [familyDish], total: 1, page: 1, pageSize: 100 };
    }
    return { menuId: '20', menuDate: shanghaiDate(), mealType: 'LUNCH', status: 'PUBLISHED',
      dishes: [dish], missingDishIds: [] };
  };
  let sent;
  api.post = async (endpoint, body) => { sent = { endpoint, body }; return { menuId: '20' }; };
  await page.loadParent();
  assert.deepEqual(page.selectedRefs, [{ type: 'PRESET', id: '99' }]);
  assert.equal(calls.some(call => call.endpoint === '/family/children'), false);
  // 家庭私有菜品与预置菜品合并进同一份目录，来源由 type 区分。
  assert.deepEqual(page.catalogDishes.map(item => item.sourceType + ':' + item.dishId), ['FAMILY:7', 'PRESET:99', 'PRESET:100']);
  page.toggleDish(event({ key: 'FAMILY:7' }));
  page.toggleDish(event({ key: 'PRESET:100' }));
  await page.saveMenu();
  assert.deepEqual(sent, { endpoint: '/parent/menu-daily', body: {
    menuDate: shanghaiDate(), mealType: 'LUNCH',
    dishIds: [{ type: 'PRESET', id: '99' }, { type: 'FAMILY', id: '7' }, { type: 'PRESET', id: '100' }],
    status: 'PUBLISHED'
  } });
  assert.equal(Object.hasOwn(sent.body, 'familyId'), false);
  assert.equal(page.data.count, 1);
});
test('家长首次维护时将空响应或旧版404视为未发布菜单', async () => {
  const page = loadPage('menu');
  page.catalogDishes = [dish];
  api.get = async () => null;
  await page.readParent();
  assert.equal(page.data.menu, null);
  assert.equal(page.data.ready, true);
  assert.deepEqual(page.selectedRefs, []);
  assert.deepEqual(page.data.dishes.map(item => item.dishId), ['99']);

  api.get = async () => {
    throw Object.assign(new Error('资源不存在'), { status: 404, code: 'E-005' });
  };
  await page.readParent();
  assert.equal(page.data.menu, null);
  assert.equal(page.data.ready, true);
  assert.deepEqual(page.selectedRefs, []);
});
test('家长菜单限制50项并要求先清理下架或删除引用', () => {
  const page = loadPage('menu');
  page.catalogDishes = Array.from({ length: 51 }, (_, index) => ({
    ...dish, dishId: String(index + 1), name: '餐食' + index
  }));
  page.allDishes = [...page.catalogDishes, { ...dish, dishId: 'old', status: 'OFF_SALE' }];
  page.selectedRefs = page.catalogDishes.slice(0, 50).map(item => ({ type: 'PRESET', id: item.dishId }))
    .concat({ type: 'PRESET', id: 'old' }, { type: 'FAMILY', id: 'missing' });
  page.render();
  assert.equal(page.data.invalidSelectedCount, 2);
  page.clearUnavailable();
  assert.equal(page.selectedRefs.length, 50);
  page.toggleDish(event({ key: 'PRESET:51' }));
  assert.match(page.data.error, /最多选择50种/);
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
    items: [{ key: 'PRESET:99', dishRef: { type: 'PRESET', id: '99' }, quantity: 1, unitPrice: '999.00', allergies: ['PEANUT'] }] } });
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
  assert.deepEqual(sent[0].body, { menuId: '20',
    items: [{ dishRef: { type: 'PRESET', id: '99' }, quantity: 1 }], remark: '', previousConfirmId: '5' });
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
test('审批调整面板只改变展示状态，不触发决策请求', () => {
  const page = loadPage('confirmation');
  let calls = 0;
  api.post = async () => { calls++; };
  page.toggleAdjustment();
  assert.equal(page.data.showAdjustment, true);
  page.toggleAdjustment();
  assert.equal(page.data.showAdjustment, false);
  assert.equal(calls, 0);
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
test('钱包首次只加载概览，流水与规则按视图读取', async () => {
  const page = loadPage('wallet');
  page.setData({ childId: child.childId, startDate: '2026-09-01', endDate: '2026-09-21' });
  const calls = [];
  api.get = async endpoint => {
    calls.push(endpoint);
    if (endpoint === '/wallet/overview') return { balance: '20.00' };
    if (endpoint === '/wallet/board') return { totalBalance: '20.00', children: [] };
    if (endpoint === '/wallet/stats') {
      return { spendTrend: [], spendCategories: [], grantCategories: [], totalSpend: '0.00', totalGrant: '0.00' };
    }
    if (endpoint === '/wallet/allowance-log') return { items: [], total: 0 };
    if (endpoint === '/wallet/allowance-rule') {
      return { singleLimit: '10.00', dailyLimit: '20.00', weeklyLimit: '50.00', version: 1 };
    }
    throw new Error('未预期接口 ' + endpoint);
  };

  await page.read();
  assert.equal(page.data.view, 'overview');
  assert.deepEqual(calls, ['/wallet/overview', '/wallet/board', '/wallet/stats']);
  await page.changeView(event({ view: 'logs' }));
  assert.equal(page.data.view, 'logs');
  assert.equal(calls.at(-1), '/wallet/allowance-log');
  await page.changeView(event({ view: 'rules' }));
  assert.equal(page.data.view, 'rules');
  assert.equal(calls.at(-1), '/wallet/allowance-rule');
});
test('钱包儿童选择优先显示昵称，家长一级导航不重复压栈', async () => {
  const page = loadPage('wallet');
  api.get = async endpoint => {
    if (endpoint === '/family/children') {
      return { items: [{ ...child, nickname: '小星' }], total: 1 };
    }
    if (endpoint === '/wallet/overview') return { balance: '20.00' };
    if (endpoint === '/wallet/board') return { totalBalance: '20.00', children: [] };
    if (endpoint === '/wallet/stats') {
      return { spendTrend: [], spendCategories: [], grantCategories: [], totalSpend: '0.00', totalGrant: '0.00' };
    }
    throw new Error('未预期接口 ' + endpoint);
  };
  await page.onShow();
  assert.equal(page.data.children[0].displayName, '小星');
  page.changeTab({ detail: { key: 'me' } });
  page.changeTab({ detail: { key: 'approvals' } });
  assert.deepEqual(navigation, ['/pages/home/index?tab=me', '/pages/confirmation/index']);
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
test('首页只接受当前角色的一级视图，家务页返回对应儿童一级视图', () => {
  const home = loadPage('home', 'CHILD');
  home.onLoad({ tab: 'growth' });
  api.get = async () => ({ unreadCount: 0 });
  home.onShow();
  assert.equal(home.data.active, 'growth');

  const chore = loadPage('chore', 'CHILD');
  chore.changeTab({ detail: { key: 'me' } });
  assert.deepEqual(navigation, ['/pages/home/index?tab=me']);
  chore.changeTab({ detail: { key: 'task' } });
  assert.deepEqual(navigation, ['/pages/home/index?tab=me']);
});
test('勋章列表生成顶层稳定键供视图循环使用', async () => {
  const page = loadPage('medal');
  page.data.childId = child.childId;
  api.get = async (endpoint, query) => {
    assert.equal(endpoint, '/medal/awards');
    assert.deepEqual(query, { childId: child.childId });
    return [{
      earned: true,
      progress: 1,
      definition: { definitionId: 'medal-1', name: '初次完成', description: '完成一次家务', threshold: 1 }
    }];
  };
  await page.read();
  assert.equal(page.data.medals[0].definitionId, 'medal-1');
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
test('我的菜品：列表按状态筛选，新增只提交服务端允许的字段', async () => {
  const page = loadPage('dish-manage');
  const calls = [];
  api.get = async (endpoint, query) => {
    calls.push({ endpoint, query });
    if (endpoint === '/parent/dish-category') {
      return [{ categoryId: '3', name: '主食', status: 'ENABLED' }];
    }
    return { items: [{ ...familyDish, version: 0 }], total: 1, page: 1, pageSize: 20 };
  };
  await page.loadCategories();
  await page.read();
  assert.deepEqual(page.data.categoryNames, ['主食']);
  assert.deepEqual(page.data.dishes.map(item => item.dishId), ['7']);
  assert.equal(calls[1].query.status, 'ON_SALE');
  assert.equal(page.data.allergenOptions.find(item => item.code === 'EGG').label, '鸡蛋');

  let sent;
  api.post = async (endpoint, body) => { sent = { endpoint, body }; return { dishId: '9' }; };
  page.create();
  assert.equal(page.data.showForm, true);
  page.setData({ formName: '番茄炒蛋', formCategoryId: '3', formVirtualPrice: '8.5', formCalories: '220',
    formTagsText: '家常, 快手', formAllergens: ['EGG'] });
  await page.save();
  assert.deepEqual(sent, { endpoint: '/parent/family-dish', body: { categoryId: '3', name: '番茄炒蛋',
    virtualPrice: '8.5', allergens: ['EGG'], allergenStatus: 'DECLARED', spiceLevel: 0,
    tags: '家常,快手', calories: 220 } });
  assert.equal(Object.hasOwn(sent.body, 'familyId'), false);
  assert.equal(Object.hasOwn(sent.body, 'visibility'), false);
  assert.equal(page.data.showForm, false);
});
test('我的菜品：校验拦截非法价格与未声明过敏原', async () => {
  const page = loadPage('dish-manage');
  page.create();
  page.setData({ formName: '测试菜', formCategoryId: '3', formVirtualPrice: '8.999' });
  await page.save();
  assert.match(page.data.error, /最多两位小数/);
  let posts = 0;
  api.post = async () => { posts++; };
  page.setData({ formVirtualPrice: '8.00', formAllergens: ['EGG'], formAllergenStatus: 'UNKNOWN' });
  await page.save();
  assert.match(page.data.error, /过敏原状态/);
  assert.equal(posts, 0);
  page.setData({ formAllergens: [], formAllergenStatus: 'DECLARED', formImageUrl: 'http://example.com/dish.png' });
  await page.save();
  assert.match(page.data.error, /HTTPS/);
  assert.equal(posts, 0);
});
test('我的菜品：编辑带版本号，上下架与删除需二次确认', async () => {
  const page = loadPage('dish-manage');
  api.get = async endpoint => {
    if (endpoint === '/parent/dish-category') return [{ categoryId: '3', name: '主食', status: 'ENABLED' }];
    if (endpoint.endsWith('/references')) return { menuCount: 2 };
    return { items: [{ ...familyDish, version: 4 }], total: 1, page: 1, pageSize: 20 };
  };
  await page.loadCategories();
  await page.read();
  const calls = [];
  api.put = async (endpoint, body) => { calls.push({ method: 'put', endpoint, body }); return { dishId: '7' }; };
  api.post = async (endpoint, body) => { calls.push({ method: 'post', endpoint, body }); return { dishId: '7' }; };
  api.del = async (endpoint, query) => { calls.push({ method: 'del', endpoint, query }); };

  page.edit(event({ id: '7' }));
  assert.equal(page.data.showForm, true);
  assert.equal(page.data.formVersion, 4);
  assert.equal(page.data.formAllergenIndex, 0);
  page.setData({ formName: '改名后的菜' });
  await page.save();
  assert.deepEqual(calls[0], { method: 'put', endpoint: '/parent/family-dish/7?expectedVersion=4',
    body: { categoryId: '3', name: '改名后的菜', virtualPrice: '8.00', allergens: [],
      allergenStatus: 'DECLARED', spiceLevel: 1 } });

  await page.toggleStatus(event({ id: '7' }));
  assert.deepEqual(calls[1], { method: 'post',
    endpoint: '/parent/family-dish/7/status?targetStatus=OFF_SALE&expectedVersion=4', body: undefined });

  await page.remove(event({ id: '7' }));
  assert.ok(modals.some(options => /2 份菜单引用/.test(options.content)));
  assert.deepEqual(calls[2], { method: 'del', endpoint: '/parent/family-dish/7', query: { expectedVersion: 4 } });
});
test('儿童混合点单：家庭与预置菜品用来源复合键选择并提报', async () => {
  const page = loadPage('menu', 'CHILD');
  const calls = [];
  api.get = async (endpoint, query) => {
    calls.push({ endpoint, query });
    assert.equal(query.sourceType, 'FAMILY');
    return { menuId: '20', sourceType: 'FAMILY', menuDate: shanghaiDate(), mealType: 'LUNCH',
      canSubmit: true, dishes: [{ ...dish, canSelect: true }, { ...familyDish, canSelect: true }] };
  };
  await page.read();
  assert.deepEqual(page.data.dishes.map(item => item.key), ['PRESET:99', 'FAMILY:7']);
  page.quantity(event({ key: 'PRESET:99', delta: 1 }));
  page.quantity(event({ key: 'FAMILY:7', delta: 1 }));
  page.quantity(event({ key: 'FAMILY:7', delta: 1 }));
  assert.equal(page.data.total, '26.01');
  page.checkout();
  assert.deepEqual(context.takeCart().items.map(item => item.dishRef),
    [{ type: 'PRESET', id: '99' }, { type: 'FAMILY', id: '7' }]);
});
test('家长建议家庭菜品时重建 dishRef，而不是只传裸 id', async () => {
  const page = loadPage('confirmation');
  page.data.detail = { confirmId: '21', childId: child.childId, menuDate: shanghaiDate(), mealType: 'DINNER' };
  api.get = async () => ({ dishes: [{ ...dish, canSelect: false }, { ...familyDish, canSelect: false }] });
  await page.editSuggestion();
  assert.deepEqual(page.data.suggestions.map(d => d.key), ['PRESET:99', 'FAMILY:7']);
  page.setData({ reason: '换成这两样' });
  page.suggestion(event({ key: 'FAMILY:7', delta: 1 }));
  let sent;
  api.post = async (endpoint, body) => { sent = { endpoint, body }; return { confirmId: '21', status: 'REJECTED' }; };
  await page.modify();
  assert.equal(sent.endpoint, '/parent/approve/21/modify');
  assert.deepEqual(sent.body.items, [{ dishRef: { type: 'FAMILY', id: '7' }, quantity: 1 }]);
});
test('健康打卡：家长用预设建项，提交体不含 familyId', async () => {
  const page = loadPage('health');
  api.get = async endpoint => {
    assert.equal(endpoint, '/parent/check-item');
    return [{ itemId: '11', familyId: '5', name: '喝水', icon: '💧', unit: '杯', dailyTarget: 6, sortOrder: 1, version: 0 }];
  };
  await page.read();
  assert.deepEqual(page.data.managed.map(item => item.key), ['11']);
  assert.equal(page.data.managed[0].targetText, '每日上限 6杯');

  page.toggleForm();
  assert.equal(page.data.showForm, true);
  assert.equal(page.data.formOrder, '2');

  page.applyPreset(event({ index: 1 }));
  assert.equal(page.data.formName, '睡眠');
  assert.equal(page.data.formIcon, '😴');
  assert.equal(page.data.formTarget, '1');

  let sent;
  api.post = async (endpoint, body) => { sent = { endpoint, body }; return { itemId: '12' }; };
  await page.save();
  assert.deepEqual(sent, { endpoint: '/parent/check-item',
    body: { name: '睡眠', dailyTarget: 1, sortOrder: 2, icon: '😴', unit: '小时' } });
  assert.equal(Object.hasOwn(sent.body, 'familyId'), false);
  assert.equal(page.data.showForm, false);
  assert.match(page.data.receipt, /孩子现在就能打卡/);
});
test('健康打卡：编辑校验上限并带 expectedVersion，删除需二次确认', async () => {
  const page = loadPage('health');
  api.get = async () => [{ itemId: '11', name: '喝水', icon: '💧', unit: '杯', dailyTarget: 6, sortOrder: 1, version: 3 }];
  await page.read();
  const calls = [];
  api.put = async (endpoint, body) => { calls.push({ method: 'put', endpoint, body }); return {}; };
  api.del = async (endpoint, query) => { calls.push({ method: 'del', endpoint, query }); };

  page.edit(event({ id: '11' }));
  assert.equal(page.data.isEdit, true);
  assert.equal(page.data.formVersion, 3);
  page.setData({ formTarget: '6.5' });
  await page.save();
  assert.match(page.data.error, /0-9999/);
  assert.equal(calls.length, 0);

  page.setData({ formTarget: '8' });
  await page.save();
  assert.deepEqual(calls[0], { method: 'put', endpoint: '/parent/check-item/11?expectedVersion=3',
    body: { name: '喝水', dailyTarget: 8, sortOrder: 1, icon: '💧', unit: '杯' } });

  await page.remove(event({ id: '11' }));
  assert.ok(modals.some(options => /历史打卡记录仍会保留/.test(options.content)));
  assert.deepEqual(calls[1], { method: 'del', endpoint: '/parent/check-item/11', query: { expectedVersion: 3 } });
});
test('健康打卡：儿童打卡刷新今日次数与连续天数，达上限标记已完成', async () => {
  const page = loadPage('health', 'CHILD');
  const calls = [];
  let todayRows = [{ itemId: '11', itemName: '喝水', count: 1, dailyTarget: 2, reached: false },
    { itemId: '12', itemName: '洗手', count: 3, dailyTarget: 0, reached: false }];
  api.get = async (endpoint, query) => {
    calls.push({ endpoint, query });
    if (endpoint === '/child/check-in/items') {
      return [{ itemId: '11', name: '喝水', icon: '💧', unit: '杯', dailyTarget: 2, sortOrder: 1 },
        { itemId: '12', name: '洗手', icon: '🧼', unit: '次', dailyTarget: 0, sortOrder: 2 }];
    }
    if (endpoint === '/child/check-in/today') return todayRows;
    assert.equal(endpoint, '/child/check-in/calendar');
    return { childId: child.childId, currentStreak: 2, checkedDates: [shanghaiDate()] };
  };
  await page.onShow();
  assert.deepEqual(page.data.checkItems.map(item => item.key), ['11', '12']);
  assert.equal(page.data.checkItems[0].targetText, '1 / 2杯');
  assert.equal(page.data.checkItems[0].percent, 50);
  assert.equal(page.data.checkItems[1].limitText, '不限次数');
  assert.equal(page.data.doneCount, 1);
  assert.equal(page.data.streak, 2);
  assert.equal(page.data.monthChecked, 1);

  let sent;
  api.post = async (endpoint, body) => {
    sent = { endpoint, body };
    todayRows = [{ itemId: '11', itemName: '喝水', count: 2, dailyTarget: 2, reached: true },
      { itemId: '12', itemName: '洗手', count: 3, dailyTarget: 0, reached: false }];
    return { recordId: '31', itemId: '11', itemName: '喝水' };
  };
  await page.checkIn(event({ id: '11' }));
  assert.deepEqual(sent, { endpoint: '/child/check-in?itemId=11', body: {} });
  assert.equal(page.data.checkItems[0].reached, true);
  assert.equal(page.data.checkItems[0].actionLabel, '今日已完成');
  assert.equal(page.data.checkItems[0].percent, 100);
  assert.equal(page.data.doneCount, 2);
  assert.match(page.data.receipt, /「喝水」打卡成功，已连续打卡 2 天/);
  assert.equal(calls.at(-1).endpoint, '/child/check-in/calendar');
});
test('健康打卡：日历格子按周日起排，且不能翻到未来月份', async () => {
  const page = loadPage('health', 'CHILD');
  const months = [];
  api.get = async (endpoint, query) => {
    if (endpoint === '/child/check-in/items') return [];
    if (endpoint === '/child/check-in/today') return [];
    months.push(query.month);
    return { childId: child.childId, currentStreak: 0, checkedDates: [query.month + '-15'] };
  };
  await page.onShow();
  const today = shanghaiDate();
  const [year, month] = today.split('-').map(Number);
  const prevMonth = month === 1 ? (year - 1) + '-12' : year + '-' + String(month - 1).padStart(2, '0');
  assert.equal(page.data.month, today.slice(0, 7));
  assert.equal(page.data.canNextMonth, false);
  assert.equal(page.data.checkItems.length, 0);
  assert.equal(page.data.monthChecked, 1);
  assert.equal(page.data.cells.find(cell => cell.checked).key, today.slice(0, 7) + '-15');

  await page.shiftMonth(event({ delta: 1 }));
  assert.deepEqual(months, [today.slice(0, 7)]);
  assert.equal(page.data.month, today.slice(0, 7));
  await page.shiftMonth(event({ delta: -1 }));
  assert.deepEqual(months, [today.slice(0, 7), prevMonth]);
  assert.equal(page.data.canNextMonth, true);
  assert.equal(page.data.monthLabel, prevMonth.slice(0, 4) + ' 年 ' + Number(prevMonth.slice(5)) + ' 月');
  assert.equal(page.data.cells.find(cell => cell.checked).key, prevMonth + '-15');

  page.setData({ month: '2026-02' });
  await page.readChild();
  assert.equal(page.data.cells.filter(cell => cell.pad).length, 0);
  assert.equal(page.data.cells.length, 28);
  assert.equal(page.data.cells.filter(cell => cell.future).length, 0);
});
test('菜单页：分类 chips 按 categoryId 聚合，可切换过滤', async () => {
  const page = loadPage('menu', 'CHILD');
  api.get = async endpoint => {
    if (endpoint === '/child/frequent-dish') return { dishes: [] };
    return { menuId: '20', sourceType: 'FAMILY', menuDate: shanghaiDate(), mealType: 'LUNCH', canSubmit: true,
      dishes: [{ ...dish, dishId: '99', categoryId: '3', categoryName: '主食', canSelect: true },
        { ...dish, dishId: '100', categoryId: '4', categoryName: '汤羹', canSelect: true }] };
  };
  await page.read();
  assert.deepEqual(page.data.categories.map(item => item.id), ['', '3', '4']);
  assert.deepEqual(page.data.categories.map(item => item.name), ['全部', '主食', '汤羹']);
  assert.deepEqual(page.data.categories.map(item => item.count), [2, 1, 1]);
  assert.deepEqual(page.data.dishes.map(item => item.dishId), ['99', '100']);

  page.category(event({ id: '4' }));
  assert.equal(page.data.categoryId, '4');
  assert.deepEqual(page.data.dishes.map(item => item.dishId), ['100']);
  page.category(event({ id: '' }));
  assert.deepEqual(page.data.dishes.map(item => item.dishId), ['99', '100']);
  // 搜索与分类叠加时两者都生效
  page.setData({ categoryId: '3', keyword: '不存在' });
  page.render();
  assert.deepEqual(page.data.dishes, []);
});
test('菜单页：常吃快捷区只允许标记今日餐单已有的菜品', async () => {
  const page = loadPage('menu', 'CHILD');
  const calls = [];
  api.get = async (endpoint, query) => {
    calls.push({ endpoint, query });
    if (endpoint === '/child/frequent-dish') {
      return { dishes: [
        { type: 'PRESET', id: '99', name: '合成餐食', count: 4, categoryId: '3', categoryName: '主食' },
        { type: 'FAMILY', id: '7', name: '家庭番茄炒蛋', count: 2, categoryId: '3', categoryName: '主食' }
      ] };
    }
    if (endpoint === '/child/recommend') return { dishes: [] };
    if (endpoint === '/child/menu-week' || endpoint === '/parent/menu-week') return { days: [] };
    return { menuId: '20', sourceType: 'FAMILY', menuDate: shanghaiDate(), mealType: 'LUNCH',
      canSubmit: true, dishes: [{ ...dish, canSelect: true, categoryName: '主食' }] };
  };
  await page.read();
  assert.deepEqual(calls.map(call => call.endpoint),
    ['/menu/daily', '/child/frequent-dish', '/child/recommend', '/child/menu-week']);
  assert.equal(calls[1].query.limit, 6);
  assert.deepEqual(page.data.frequent.map(item => item.key), ['PRESET:99', 'FAMILY:7']);
  // 今日餐单里没有 FAMILY:7，标记它后端会 404，所以前端直接置为不可点。
  assert.equal(page.data.frequent[0].available, true);
  assert.equal(page.data.frequent[1].available, false);

  let sent;
  api.post = async (endpoint, body) => { sent = { endpoint, body }; return { wantEat: [] }; };
  await page.quickFavorite(event({ key: 'PRESET:99' }));
  assert.deepEqual(sent, { endpoint: '/menu/mark-favorite', body: {
    dishId: '99', dishType: 'PRESET', favorite: true,
    menuId: '20', menuDate: shanghaiDate(), mealType: 'LUNCH' } });

  sent = null;
  const toasts = [];
  global.wx.showToast = options => toasts.push(options.title);
  await page.quickFavorite(event({ key: 'FAMILY:7' }));
  assert.equal(sent, null);
  assert.deepEqual(toasts, ['今日餐单暂无可标记的菜品']);
});
test('菜单页：常吃快捷区接口失败时静默降级，不影响主流程', async () => {
  const page = loadPage('menu', 'CHILD');
  api.get = async endpoint => {
    if (endpoint === '/child/frequent-dish') throw Object.assign(new Error('服务不可用'), { status: 500 });
    return { menuId: '20', sourceType: 'FAMILY', menuDate: shanghaiDate(), mealType: 'LUNCH',
      canSubmit: true, dishes: [{ ...dish, canSelect: true }] };
  };
  await page.read();
  assert.deepEqual(page.data.frequent, []);
  assert.equal(page.data.ready, true);
  assert.deepEqual(page.data.dishes.map(item => item.key), ['PRESET:99']);
});
test('家长想吃看板：按日期与餐次分组，状态流转回传乐观锁版本', async () => {
  const page = loadPage('want-eat');
  const today = shanghaiDate();
  page.setData({ today, from: today, to: today, childId: child.childId });
  api.get = async (endpoint, query) => {
    assert.equal(endpoint, '/parent/want-eat');
    assert.deepEqual(query, { childId: child.childId, from: today, to: today });
    return { childId: child.childId, from: today, to: today, today, expiredCount: 1,
      days: [{ menuDate: today, meals: [{ mealType: 'LUNCH', sourceType: 'FAMILY', menuId: '20',
        items: [{ wantEatId: '31', type: 'PRESET', id: '99', name: '合成餐食', status: 'MARKED',
          version: 0, expired: false, allergyConflict: false, disliked: true, missing: false }] }] }],
      summary: { totalItems: 1, dishes: [{ type: 'PRESET', id: '99', name: '合成餐食', count: 1, dates: [today] }] } };
  };
  await page.read();
  assert.equal(page.data.ready, true);
  assert.equal(page.data.expiredCount, 1);
  assert.equal(page.data.rangeLabel, today);
  const dish0 = page.data.days[0].meals[0].items[0];
  assert.equal(dish0.statusLabel, '未处理');
  assert.equal(dish0.flags, '孩子忌口');
  assert.equal(page.data.summary[0].countText, '共 1 天');

  let sent;
  api.post = async (endpoint, body) => { sent = { endpoint, body }; return { wantEat: [] }; };
  await page.mark(event({ id: '31', status: 'COOKED', version: '0' }));
  assert.deepEqual(sent, { endpoint: '/parent/want-eat/31/status',
    body: { status: 'COOKED', expectedVersion: 0 } });
  assert.match(page.data.receipt, /已做/);

  page.onHide();
  assert.deepEqual(page.data.days, []);
  assert.equal(page.data.ready, false);
});
test('家长想吃看板：菜品已下架时保留行占位并标注', async () => {
  const page = loadPage('want-eat');
  const today = shanghaiDate();
  page.setData({ today, from: today, to: today, childId: child.childId });
  api.get = async () => ({ childId: child.childId, from: today, to: today, today, expiredCount: 0,
    days: [{ menuDate: today, meals: [{ mealType: 'DINNER', sourceType: 'FAMILY', menuId: '20',
      items: [{ wantEatId: '32', type: 'FAMILY', id: '7', name: null, status: 'ADOPTED',
        version: 2, expired: true, allergyConflict: false, disliked: false, missing: true }] }] }],
    summary: { totalItems: 0, dishes: [] } });
  await page.read();
  const dish0 = page.data.days[0].meals[0].items[0];
  assert.equal(dish0.displayName, '（菜品信息不可用）');
  assert.equal(dish0.statusLabel, '已采购');
  assert.equal(dish0.flags, '菜品已下架 · 已过期');
  assert.deepEqual(dish0.actions.map(action => action.status), ['COOKED', 'MARKED']);
});
test('菜单页：推荐卡渲染、点击复用今日餐单校验', async () => {
  const page = loadPage('menu', 'CHILD');
  api.get = async (endpoint, query) => {
    if (endpoint === '/child/frequent-dish') return { dishes: [] };
    if (endpoint === '/child/menu-week') return { days: [] };
    if (endpoint === '/child/recommend') {
      return { menuId: '20', dishes: [
        { type: 'PRESET', id: '99', name: '合成餐食', categoryName: '主食', score: 6, reasons: ['最近常吃', '本周还没吃'] },
        { type: 'FAMILY', id: '7', name: '家庭番茄炒蛋', categoryName: '主食', score: 3, reasons: ['口味清淡'] }
      ] };
    }
    return { menuId: '20', sourceType: 'FAMILY', menuDate: shanghaiDate(), mealType: 'LUNCH',
      canSubmit: true, dishes: [{ ...dish, canSelect: true }] };
  };
  const calls = [];
  api.post = async (endpoint, body) => { calls.push({ endpoint, body }); return { wantEat: [] }; };
  await page.read();
  assert.deepEqual(page.data.recommend.map(item => item.key), ['PRESET:99', 'FAMILY:7']);
  assert.equal(page.data.recommend[0].reasonText, '最近常吃 · 本周还没吃');
  // 今日餐单有 PRESET:99、没有 FAMILY:7，所以只有前者可点（复用 quickFavorite 的前置校验）。
  assert.equal(page.data.recommend[0].available, true);
  assert.equal(page.data.recommend[1].available, false);

  await page.quickFavorite(event({ key: 'PRESET:99' }));
  assert.deepEqual(calls, [{ endpoint: '/menu/mark-favorite', body: {
    dishId: '99', dishType: 'PRESET', favorite: true,
    menuId: '20', menuDate: shanghaiDate(), mealType: 'LUNCH' } }]);

  const toasts = [];
  global.wx.showToast = options => toasts.push(options.title);
  await page.quickFavorite(event({ key: 'FAMILY:7' }));
  assert.equal(calls.length, 1);
  assert.deepEqual(toasts, ['今日餐单暂无可标记的菜品']);
});
test('菜单页：推荐与周条接口失败时静默降级', async () => {
  const page = loadPage('menu', 'CHILD');
  api.get = async endpoint => {
    if (endpoint === '/menu/daily') {
      return { menuId: '20', sourceType: 'FAMILY', menuDate: shanghaiDate(), mealType: 'LUNCH',
        canSubmit: true, dishes: [{ ...dish, canSelect: true }] };
    }
    throw Object.assign(new Error('服务不可用'), { status: 500 });
  };
  await page.read();
  assert.deepEqual(page.data.recommend, []);
  assert.deepEqual(page.data.frequent, []);
  assert.deepEqual(page.data.week, []);
  assert.equal(page.data.ready, true);
  assert.deepEqual(page.data.dishes.map(item => item.dishId), ['99']);
});
test('菜单页：周条标注已发布天数，点选可切到该天', async () => {
  const page = loadPage('menu', 'CHILD');
  const today = shanghaiDate();
  const day = delta => new Date(Date.UTC(...today.split('-').map(Number).map((v, i) => i === 1 ? v - 1 : v))
    + delta * 86400000).toISOString().slice(0, 10);
  const tomorrow = day(1);
  const meals = { menuId: '20', status: 'PUBLISHED', dishCount: 1 };
  api.get = async (endpoint, query) => {
    if (endpoint === '/child/frequent-dish') return { dishes: [] };
    if (endpoint === '/child/recommend') return { dishes: [] };
    if (endpoint === '/child/menu-week') {
      assert.equal(query.from, today);
      assert.equal(query.to, day(6));
      return { from: today, to: day(6), today, days: [
        { menuDate: today, meals: [{ mealType: 'LUNCH', ...meals, wantEatCount: 1 }, { mealType: 'DINNER' }] },
        { menuDate: tomorrow, meals: [{ mealType: 'LUNCH' }] }
      ] };
    }
    return { menuId: '20', sourceType: 'FAMILY', menuDate: query.menuDate, mealType: 'LUNCH',
      canSubmit: true, dishes: [{ ...dish, canSelect: true }] };
  };
  await page.read();
  assert.equal(page.data.week.length, 2);
  assert.deepEqual(page.data.week[0], { date: today, label: '今天', dayText: today.slice(5).replace('-', '/'),
    publishedCount: 1, hasMenu: true, wantEatCount: 1, isToday: true, active: true });
  assert.equal(page.data.week[1].hasMenu, false);

  await page.weekDay(event({ date: tomorrow }));
  assert.equal(page.data.menuDate, tomorrow);
  assert.equal(page.data.week[1].active, true);
  assert.equal(page.data.week[0].active, false);
});
test('整周发布：格子点选、模板铺菜与批量发布部分成功', async () => {
  const page = loadPage('menu-week', 'PARENT');
  const today = shanghaiDate();
  const days = Array.from({ length: 7 }, (unused, index) => ({
    menuDate: new Date(Date.UTC(...today.split('-').map(Number).map((v, i) => i === 1 ? v - 1 : v))
      + index * 86400000).toISOString().slice(0, 10),
    meals: ['BREAKFAST', 'LUNCH', 'DINNER'].map(mealType => mealType === 'LUNCH' && index === 0
      ? { mealType, menuId: '30', status: 'PUBLISHED', dishCount: 1 }
      : { mealType, menuId: null, dishCount: 0 })
  }));
  const requests = [];
  api.get = async (endpoint, query) => {
    requests.push({ endpoint, query });
    if (endpoint === '/parent/dish') return { items: [dish], total: 1 };
    if (endpoint === '/parent/family-dish') return { items: [familyDish], total: 1 };
    if (endpoint === '/parent/menu-week') return { from: days[0].menuDate, to: days[6].menuDate, days };
    if (endpoint === '/parent/menu-daily') return { menuId: '30', dishes: [{ ...dish, canSelect: true }] };
    throw new Error('unexpected ' + endpoint);
  };
  await page.onShow();
  assert.equal(page.data.days.length, 7);
  assert.equal(page.data.days[0].meals[1].statusText, '1 道');
  assert.equal(page.data.days[0].meals[0].empty, true);
  assert.deepEqual(page.data.catalog.map(item => item.key), ['FAMILY:7', 'PRESET:99']);
  assert.equal(page.data.dishCount, 0);

  page.toggleCell(event({ date: days[0].menuDate, meal: 'LUNCH' }));
  assert.equal(page.data.cellCount, 1);
  assert.equal(page.data.days[0].meals[1].selected, true);
  page.toggleCell(event({ date: days[1].menuDate, meal: 'DINNER' }));
  assert.equal(page.data.cellCount, 2);

  await page.useAsTemplate(event({ date: days[0].menuDate, meal: 'LUNCH' }));
  assert.equal(page.data.dishCount, 1);
  assert.match(page.data.receipt, /已用 .* 的 1 道菜作为模板/);

  let sent;
  api.post = async (endpoint, body) => {
    sent = { endpoint, body };
    return { okCount: 1, failCount: 1, results: [
      { menuDate: days[0].menuDate, mealType: 'LUNCH', ok: true, code: null },
      { menuDate: days[1].menuDate, mealType: 'DINNER', ok: false, code: 'E-404' }
    ] };
  };
  await page.publish();
  assert.equal(sent.endpoint, '/parent/menu-daily/batch');
  assert.equal(sent.body.items.length, 2);
  assert.deepEqual(sent.body.items[0], { menuDate: days[0].menuDate, mealType: 'LUNCH',
    dishIds: [{ type: 'PRESET', id: '99' }], status: 'PUBLISHED' });
  assert.deepEqual(page.data.results.map(row => row.ok), [true, false]);
  assert.match(page.data.receipt, /成功 1 条，失败 1 条/);
  // 部分成功语义：失败项保留勾选，家长可直接重试。
  assert.equal(page.data.cellCount, 1);
  assert.equal(page.data.days[1].meals[2].selected, true);

  page.onHide();
  assert.deepEqual(page.data.days, []);
  assert.equal(page.data.cellCount, 0);
});
test('整周发布：未选餐次或未选菜品时拒绝提交', async () => {
  const page = loadPage('menu-week', 'PARENT');
  const today = shanghaiDate();
  api.get = async endpoint => {
    if (endpoint === '/parent/dish') return { items: [dish], total: 1 };
    if (endpoint === '/parent/family-dish') return { items: [], total: 0 };
    if (endpoint === '/parent/menu-week') return { days: [{ menuDate: today, meals: [
      { mealType: 'LUNCH', menuId: null, dishCount: 0 }] }] };
    throw new Error('unexpected ' + endpoint);
  };
  let posted = false;
  api.post = async () => { posted = true; return {}; };
  await page.onShow();
  await page.publish();
  assert.equal(posted, false);
  assert.match(page.data.error, /请先点选要发布的餐次/);

  page.toggleCell(event({ date: today, meal: 'LUNCH' }));
  await page.publish();
  assert.equal(posted, false);
  assert.match(page.data.error, /请先选择要铺的菜品/);
});
test('档案页学校改为发布目录选择，历史校名仍可回显', async () => {
  const config = require('../miniprogram/config');
  const page = loadPage('profile');
  api.get = async endpoint => endpoint.includes('consent') ? { currentStatus: 'GRANTED' }
    : { school: '历史老校', allergies: [], dislikes: [], tastes: [] };
  await page.read();
  assert.equal(page.data.school, '历史老校');
  assert.equal(page.data.schoolOptions[0], config.schools[0]);
  assert.ok(page.data.schoolOptions.includes('历史老校'));

  page.school(event({}, 1));
  assert.equal(page.data.school, config.schools[1]);
  let sent;
  api.post = async (endpoint, body) => { sent = { endpoint, body }; };
  page.setData({ nickname: '小明', grade: '三年级' });
  await page.save();
  assert.equal(sent.endpoint, '/child/profile');
  assert.equal(sent.body.school, config.schools[1]);
  page.onHide();
  assert.deepEqual(page.data.schoolOptions, []);
});
test('孩子端安全提示按 UNKNOWN 来源区分，且指向能处理的人', () => {
  const { safetyLabel } = require('../miniprogram/utils/domain');
  // 菜品自身未声明 ⇒ 需管理员补录
  assert.equal(safetyLabel({ status: 'ON_SALE', allergenStatus: 'UNKNOWN', allergyConflict: false }),
    '未登记过敏信息，暂不可选择');
  // 菜品已声明、但档案过敏原不在最新目录（fail-closed）⇒ 只有家长能处理
  assert.equal(safetyLabel({ status: 'ON_SALE', allergenStatus: 'DECLARED', safetyStatus: 'UNKNOWN', allergyConflict: false }),
    '档案过敏信息需家长更新，暂不可选择');
  assert.equal(safetyLabel({ status: 'ON_SALE', allergenStatus: 'DECLARED', safetyStatus: 'DECLARED', allergyConflict: false }),
    '过敏信息已声明');
  assert.equal(safetyLabel({ status: 'ON_SALE', allergenStatus: 'DECLARED', allergyConflict: true }), '含过敏原，不可选择');
  assert.equal(safetyLabel({ status: 'OFF_SALE', allergenStatus: 'DECLARED', allergyConflict: false }), '已下架');
});
test('菜单页孩子端渲染使用统一安全提示', () => {
  const page = loadPage('menu', 'CHILD');
  page.allDishes = [{ ...dish, allergenStatus: 'UNKNOWN', canSelect: false, safetyStatus: 'UNKNOWN' }];
  page.quantities = {};
  page.selectedRefs = [];
  page.catalogDishes = page.allDishes;
  page.setData({ menu: { menuId: '20', canSubmit: false, menuDate: shanghaiDate(), mealType: 'LUNCH' } });
  page.render();
  assert.equal(page.data.dishes[0].safetyLabel, '未登记过敏信息，暂不可选择');
  assert.equal(page.data.dishes[0].selectable, false);
});
