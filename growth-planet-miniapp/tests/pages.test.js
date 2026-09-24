const test = require('node:test');
const assert = require('node:assert/strict');
const path = require('node:path');
const root = path.resolve(__dirname, '../miniprogram');
const api = require('../miniprogram/services/api');
const session = require('../miniprogram/services/session');
const lifecycle = require('../miniprogram/services/lifecycle');
const { operations } = require('../miniprogram/services/operations');
const context = require('../miniprogram/services/context');
const { childDisplayName, displayChildren } = require('../miniprogram/services/children');
const ui = require('../miniprogram/utils/page');
const { shanghaiDate } = require('../miniprogram/utils/domain');
const methods = { get: api.get, post: api.post, put: api.put, del: api.del, getDocument: api.getDocument };
let modals, navigation;
// 真实 wx 的 setData 支持数据路径（如 formIngredients[0].name），而只做扁平赋值的桩测不出
// 路径写错（例：下标越界后被静默忽略）。这里补上路径支持，让测试与真机行为一致。
function setPath(target, path, value) {
  const keys = String(path).replace(/\[(\d+)\]/g, '.$1').split('.');
  let node = target;
  for (const key of keys.slice(0, -1)) node = node[key];
  node[keys[keys.length - 1]] = value;
}
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
    setData(data) { for (const [key, value] of Object.entries(data)) setPath(this.data, key, value); } };
  page.data.role = role;
  if (page.onLoad) page.onLoad({});
  return page;
}
const event = (dataset = {}, value) => ({ currentTarget: { dataset }, detail: { value } });
function deferred() {
  let resolve;
  let reject;
  const promise = new Promise((res, rej) => { resolve = res; reject = rej; });
  return { promise, resolve, reject };
}
function monthBefore(month) {
  const [year, index] = month.split('-').map(Number);
  return new Date(Date.UTC(year, index - 2, 1)).toISOString().slice(0, 7);
}
const child = { childId: '9007199254740993', bindStatus: 'BOUND', applyId: '12' };
const dish = { dishId: '99', name: '合成餐食', virtualPrice: '10.01', spiceLevel: 0, sourceType: 'PRESET',
  canSelect: true, status: 'ON_SALE', allergenStatus: 'DECLARED', allergyConflict: false };
const familyDish = { dishId: '7', categoryId: '3', name: '家庭番茄炒蛋', virtualPrice: '8.00', spiceLevel: 1,
  sourceType: 'FAMILY', canSelect: true, status: 'ON_SALE', allergenStatus: 'DECLARED', allergyConflict: false };

test('多孩选择器使用关系或昵称拼接 childId，且不改写原儿童对象', () => {
  assert.equal(childDisplayName({ childId: '1', relationLabel: '女儿', nickname: '小星' }), '女儿 · 1');
  assert.equal(childDisplayName({ childId: '2', nickname: '小月' }), '小月 · 2');
  assert.equal(childDisplayName({ childId: '3' }), '孩子 · 3');
  const source = { childId: '4', relationLabel: '儿子' };
  const mapped = displayChildren([source]);
  assert.notEqual(mapped[0], source);
  assert.equal(mapped[0].displayName, '儿子 · 4');
  assert.equal(source.displayName, undefined);
});

// 心愿菜单（P3）固定件。emptyWish = 当天未创建：功能可选，后端不产生占位记录（status=NONE）。
const wishDish = { dishId: '99', type: 'PRESET', name: '合成餐食', categoryId: '3', categoryName: '主食',
  spiceLevel: 0, status: 'ON_SALE', allergenStatus: 'DECLARED', safetyStatus: 'DECLARED',
  allergyConflict: false, disliked: false, selectable: true, marked: false };
const emptyWish = { childId: '9007199254740993', menuDate: shanghaiDate(), status: 'NONE', enabled: true,
  maxDishes: 5, dishCount: 0, submittedCount: 0, canEdit: true, canSubmit: false, locked: false,
  version: 0, submitTime: null, items: [] };

test('二级页返回优先退出页面栈，无页面栈时回到角色对应首页', () => {
  const previousWx = global.wx;
  const previousGetCurrentPages = global.getCurrentPages;
  const calls = [];
  global.wx = {
    navigateBack(options) { calls.push({ type: 'back', options }); },
    reLaunch(options) { calls.push({ type: 'reLaunch', options }); }
  };
  try {
    global.getCurrentPages = () => [{ route: 'pages/home/index' }, { route: 'pages/menu/index' }];
    ui.back({ data: { role: 'CHILD' } }, 'task');
    assert.deepEqual(calls, [{ type: 'back', options: { delta: 1 } }]);

    global.getCurrentPages = () => [{ route: 'pages/menu/index' }];
    ui.back({ data: { role: 'CHILD' } }, 'invalid');
    ui.back({ data: { role: 'CHILD' } }, 'task');
    ui.back({ data: { role: 'PARENT' } }, 'me');
    assert.deepEqual(calls.slice(1), [
      { type: 'reLaunch', options: { url: '/pages/home/index?tab=meal' } },
      { type: 'reLaunch', options: { url: '/pages/chore/index' } },
      { type: 'reLaunch', options: { url: '/pages/home/index?tab=me' } }
    ]);
  } finally {
    global.wx = previousWx;
    if (previousGetCurrentPages === undefined) delete global.getCurrentPages;
    else global.getCurrentPages = previousGetCurrentPages;
  }
});

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
  // 「孩子的心愿菜单」卡需要 childId（家长端在这里选孩子），故会顺带取一次绑定儿童列表；
  // 但菜单维护是家庭级的：本用例的 stub 对未知端点返回菜单对象 ⇒ loadChildren 视为"无已绑定儿童"，
  // 必须静默降级（children 为空、心愿卡不渲染），不能影响下面的目录/发布流程。
  assert.equal(calls.filter(call => call.endpoint === '/family/children').length, 1);
  assert.deepEqual(page.data.children, []);
  assert.deepEqual(page.data.childLabels, []);
  assert.equal(page.data.wish, null);
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
test('家长审批列表突出待处理数量并使用服务端总数', async () => {
  const page = loadPage('confirmation');
  const calls = [];
  api.get = async (endpoint, query) => {
    calls.push({ endpoint, query });
    return { items: [{ confirmId: '21', status: 'PENDING', menuDate: '2026-09-23', totalAmount: '20.00' }], total: 7 };
  };
  page.setData({ role: 'PARENT', childId: child.childId, page: 1, status: 'PENDING', statusIndex: 0 });

  await page.readList();

  assert.deepEqual(calls[0], {
    endpoint: '/parent/approvals',
    query: { childId: child.childId, page: 1, pageSize: 20, status: 'PENDING' }
  });
  assert.equal(page.data.pendingTotal, 7);
  assert.equal(page.data.listHeading, '待我处理');
  assert.match(page.data.listDescription, /7/);
  assert.equal(page.data.records[0].label, '等待家长确认');
});
test('审批历史筛选使用后端支持的独立状态', async () => {
  const page = loadPage('confirmation');
  const queries = [];
  api.get = async (endpoint, query) => {
    queries.push(query);
    return { items: [], total: 0 };
  };
  page.setData({ role: 'PARENT', childId: child.childId, page: 1, status: 'PENDING', statusIndex: 0 });

  page.filter(event({}, 1));
  await new Promise(resolve => setImmediate(resolve));

  assert.equal(page.data.status, 'COMPLETED');
  assert.equal(page.data.listHeading, '已完成');
  assert.equal(queries.at(-1).status, 'COMPLETED');
  assert.ok(['PENDING', 'COMPLETED', 'REJECTED', 'CANCELLED'].includes(queries.at(-1).status));
});
test('审批列表总数异常时按空列表处理并回退到有效页码', async () => {
  const page = loadPage('confirmation');
  const pages = [];
  api.get = async (endpoint, query) => {
    pages.push(query.page);
    return { items: [], total: 'invalid' };
  };
  page.setData({ role: 'PARENT', childId: child.childId, page: 2, status: 'PENDING', statusIndex: 0 });

  await page.readList();

  assert.deepEqual(pages, [2, 1]);
  assert.equal(page.data.page, 1);
  assert.equal(page.data.total, 0);
  assert.equal(page.data.pendingTotal, 0);
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
  assert.equal(page.data.children[0].displayName, '小星 · ' + child.childId);
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
test('查看心愿菜单立即跳转，标记已读失败不阻断查看', async () => {
  const page = loadPage('notices');
  let readStarted = false;
  api.post = async endpoint => {
    readStarted = true;
    assert.equal(endpoint, '/notices/9/read');
    throw new Error('网络暂不可用');
  };
  page.viewWish(event({ childId: child.childId, noticeId: '9' }));
  assert.equal(readStarted, true);
  assert.deepEqual(navigation, ['/pages/menu/index?childId=' + child.childId]);
  await Promise.resolve();
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
test('首页、家务和钱包一级导航统一使用根路由并拒绝非法角色入口', () => {
  const childHome = loadPage('home', 'CHILD');
  childHome.setData({ active: 'meal' });
  childHome.changeTab({ detail: { key: 'task' } });
  assert.deepEqual(navigation, ['/pages/chore/index']);
  childHome.changeTab({ detail: { key: 'wallet' } });
  assert.deepEqual(navigation, ['/pages/chore/index']);

  const parentHome = loadPage('home', 'PARENT');
  parentHome.setData({ active: 'home' });
  parentHome.changeTab({ detail: { key: 'approvals' } });
  parentHome.changeTab({ detail: { key: 'wallet' } });
  parentHome.changeTab({ detail: { key: 'home' } });
  assert.deepEqual(navigation, ['/pages/confirmation/index', '/pages/wallet/index']);

  const chore = loadPage('chore', 'CHILD');
  chore.changeTab({ detail: { key: 'growth' } });
  chore.changeTab({ detail: { key: 'approvals' } });
  assert.deepEqual(navigation, ['/pages/home/index?tab=growth']);

  const wallet = loadPage('wallet', 'PARENT');
  wallet.changeTab({ detail: { key: 'approvals' } });
  wallet.changeTab({ detail: { key: 'task' } });
  assert.deepEqual(navigation, ['/pages/confirmation/index']);
});
test('家长首页摘要按选中儿童查询，并使用总数和今日单日范围', async () => {
  const page = loadPage('home');
  const secondChild = { childId: '9007199254740994', relationLabel: '女儿', bindStatus: 'BOUND' };
  const calls = [];
  api.get = async (endpoint, query) => {
    calls.push({ endpoint, query });
    if (endpoint === '/notices/unread-count') return { unreadCount: 2 };
    if (endpoint === '/family/children') return { items: [child, secondChild], total: 2 };
    if (endpoint === '/parent/approvals') return { items: [], total: 7 };
    if (endpoint === '/chore/instances') return [{ instanceId: '1' }, { instanceId: '2' }];
    if (endpoint === '/parent/want-eat') return { summary: { totalItems: 3 } };
    throw new Error('未预期接口 ' + endpoint);
  };
  await page.onShow();
  assert.equal(page.data.childId, child.childId);
  assert.deepEqual(page.data.parentSummary, {
    approvals: { status: 'ready', value: 7, error: '' },
    chores: { status: 'ready', value: 2, error: '' },
    wantEat: { status: 'ready', value: 3, error: '' }
  });
  await page.selectParentChild({ detail: { value: 1 } });
  assert.equal(page.data.childId, secondChild.childId);
  const summaryCalls = calls.filter(call => ['/parent/approvals', '/chore/instances', '/parent/want-eat'].includes(call.endpoint));
  assert.ok(summaryCalls.slice(-3).every(call => call.query.childId === secondChild.childId));
  assert.deepEqual(summaryCalls.at(-1).query, {
    childId: secondChild.childId, from: shanghaiDate(), to: shanghaiDate()
  });
  page.openParentSummary(event({ page: 'want-eat' }));
  assert.equal(navigation.at(-1), '/pages/want-eat/index?childId=' + encodeURIComponent(secondChild.childId) + '&range=today');
});
test('家长首页摘要单项失败只标记该项，不把失败伪装成零', async () => {
  const page = loadPage('home');
  api.get = async endpoint => {
    if (endpoint === '/notices/unread-count') return { unreadCount: 0 };
    if (endpoint === '/family/children') return { items: [child], total: 1 };
    if (endpoint === '/parent/approvals') throw new Error('审批暂不可用');
    if (endpoint === '/chore/instances') return [];
    if (endpoint === '/parent/want-eat') return { summary: { totalItems: 0 } };
    throw new Error('未预期接口 ' + endpoint);
  };
  await page.onShow();
  assert.equal(page.data.parentSummary.approvals.status, 'error');
  assert.match(page.data.parentSummary.approvals.error, /审批暂不可用/);
  assert.deepEqual(page.data.parentSummary.chores, { status: 'ready', value: 0, error: '' });
  assert.deepEqual(page.data.parentSummary.wantEat, { status: 'ready', value: 0, error: '' });
});
test('家长首页无绑定儿童时保留可重试状态，迟到响应不回写', async () => {
  const page = loadPage('home');
  api.get = async endpoint => {
    if (endpoint === '/notices/unread-count') return { unreadCount: 0 };
    if (endpoint === '/family/children') throw new Error('尚无已绑定的儿童，请先前往家庭与绑定');
    throw new Error('不应请求摘要');
  };
  await page.onShow();
  assert.equal(page.data.parentSummaryUnavailable, true);
  assert.match(page.data.parentSummaryMessage, /尚无已绑定/);

  let resolveChildren;
  api.get = async endpoint => {
    if (endpoint === '/family/children') {
      return new Promise(resolve => { resolveChildren = resolve; });
    }
    return { unreadCount: 0 };
  };
  const pending = page.retryParentSummary();
  page.onHide();
  resolveChildren({ items: [child], total: 1 });
  await pending;
  assert.equal(page.data.childId, '');
  assert.equal(page.data.parentSummaryUnavailable, true);
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

  // 编辑会额外懒加载配方明细（v011），必须等它结束再保存：加载期间页面 busy，save 会被拦。
  await page.edit(event({ id: '7' }));
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
test('儿童任务页刷新健康打卡使用上海月份参数', async () => {
  const page = loadPage('chore', 'CHILD');
  page.setData({ childId: child.childId });
  const calls = [];
  api.get = async (endpoint, query) => {
    calls.push({ endpoint, query });
    if (endpoint === '/child/check-in/items') return [{ itemId: '1', name: '喝水', dailyTarget: 3, unit: '次' }];
    if (endpoint === '/child/check-in/today') return [{ itemId: '1', count: 1, dailyTarget: 3, reached: false }];
    if (endpoint === '/child/check-in/calendar') return { currentStreak: 2 };
    throw new Error('未预期接口 ' + endpoint);
  };
  await page.readChild();
  assert.deepEqual(calls.at(-1), {
    endpoint: '/child/check-in/calendar', query: { month: shanghaiDate().slice(0, 7) }
  });
  assert.equal(page.data.streak, 2);
  assert.equal(page.data.checkItems[0].percent, 33);
});
test('儿童任务页健康打卡按完成状态拦截重复写入并释放失败锁', async () => {
  const page = loadPage('chore', 'CHILD');
  page.setData({ checkItems: [
    { key: '1', itemId: '1', name: '喝水', count: 1, dailyTarget: 1, reached: false }
  ] });
  let postCount = 0;
  api.post = async () => {
    postCount += 1;
    if (postCount === 1) throw new Error('任务页网络暂不可用');
    return {};
  };
  page.readChild = async () => {
    page.setData({ checkItems: [{ key: '1', itemId: '1', name: '喝水', reached: true }] });
  };

  await page.checkIn(event({ id: '1' }));
  assert.match(page.data.error, /任务页网络暂不可用/);
  assert.equal(page.data.checkInPending['1'], undefined);

  await page.checkIn(event({ id: '1' }));
  assert.equal(postCount, 2);
  assert.equal(page.data.checkItems[0].reached, true);
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
test('健康打卡：重复点击同一项只发送一次，未知 itemId 不请求', async () => {
  const page = loadPage('health', 'CHILD');
  page.setData({ ready: true, today: shanghaiDate(), month: shanghaiDate().slice(0, 7), checkItems: [
    { key: '11', itemId: '11', name: '喝水', reached: false }
  ] });
  let postCount = 0;
  api.post = async endpoint => {
    postCount += 1;
    assert.equal(endpoint, '/child/check-in?itemId=11');
    return { itemName: '喝水' };
  };
  api.get = async endpoint => {
    if (endpoint === '/child/check-in/items') return [{ itemId: '11', name: '喝水', dailyTarget: 1 }];
    if (endpoint === '/child/check-in/today') return [{ itemId: '11', count: 1, dailyTarget: 1, reached: true }];
    return { currentStreak: 1, checkedDates: [shanghaiDate()] };
  };

  const first = page.checkIn(event({ id: '11' }));
  const second = page.checkIn(event({ id: '11' }));
  await Promise.all([first, second]);
  await page.checkIn(event({ id: 'missing' }));

  assert.equal(postCount, 1);
  assert.equal(page.data.checkItems[0].reached, true);
});
test('健康打卡：打卡失败释放操作锁，下一次可以重试', async () => {
  const page = loadPage('health', 'CHILD');
  page.setData({ ready: true, today: shanghaiDate(), month: shanghaiDate().slice(0, 7),
    checkItems: [{ key: '11', itemId: '11', name: '喝水', reached: false }] });
  let postCount = 0;
  api.post = async () => {
    postCount += 1;
    if (postCount === 1) throw new Error('网络暂不可用');
    return { itemName: '喝水' };
  };
  api.get = async endpoint => {
    if (endpoint === '/child/check-in/items') return [{ itemId: '11', name: '喝水', dailyTarget: 1 }];
    if (endpoint === '/child/check-in/today') return [{ itemId: '11', count: 1, dailyTarget: 1, reached: true }];
    return { currentStreak: 1, checkedDates: [shanghaiDate()] };
  };

  await page.checkIn(event({ id: '11' }));
  assert.match(page.data.error, /网络暂不可用/);
  assert.equal(page.data.checkInPending['11'], undefined);
  await page.checkIn(event({ id: '11' }));

  assert.equal(postCount, 2);
  assert.equal(page.data.checkItems[0].reached, true);
});
test('健康打卡：月份切换后迟到响应不能覆盖当前月份', async () => {
  const page = loadPage('health', 'CHILD');
  const today = shanghaiDate();
  page.setData({ today, month: today.slice(0, 7) });
  const oldCalendar = deferred();
  const newCalendar = deferred();
  api.get = async (endpoint, query) => {
    if (endpoint === '/child/check-in/items' || endpoint === '/child/check-in/today') return [];
    if (query.month === today.slice(0, 7)) return oldCalendar.promise;
    return newCalendar.promise;
  };

  const oldRead = page.readChild();
  const previousMonth = monthBefore(today.slice(0, 7));
  page.setData({ month: previousMonth });
  const newRead = page.readChild();
  newCalendar.resolve({ currentStreak: 1, checkedDates: [previousMonth + '-01'] });
  await newRead;
  oldCalendar.resolve({ currentStreak: 9, checkedDates: [today.slice(0, 7) + '-02'] });
  await oldRead;

  assert.equal(page.data.month, previousMonth);
  assert.equal(page.data.streak, 1);
  assert.equal(page.data.cells.find(cell => cell.checked).key, previousMonth + '-01');
});
test('健康打卡：页面隐藏后迟到响应不回填健康数据', async () => {
  const page = loadPage('health', 'CHILD');
  const waitCalendar = deferred();
  page.setData({ today: shanghaiDate(), month: shanghaiDate().slice(0, 7) });
  api.get = async (endpoint) => {
    if (endpoint === '/child/check-in/items' || endpoint === '/child/check-in/today') return [];
    return waitCalendar.promise;
  };

  const reading = page.readChild();
  page.onHide();
  waitCalendar.resolve({ currentStreak: 4, checkedDates: [shanghaiDate()] });
  await reading;

  assert.equal(page.data.ready, false);
  assert.deepEqual(page.data.checkItems, []);
  assert.deepEqual(page.data.cells, []);
  assert.equal(page.data.streak, 0);
});
test('健康打卡：家长保存失败保留编辑表单，版本冲突可见错误', async () => {
  const page = loadPage('health');
  api.get = async () => [{ itemId: '11', name: '喝水', icon: '💧', unit: '杯', dailyTarget: 6, sortOrder: 1, version: 3 }];
  await page.read();
  page.edit(event({ id: '11' }));
  api.put = async () => { throw Object.assign(new Error('数据已被其他操作更新，请刷新后重试'), { status: 409 }); };

  await page.save();

  assert.equal(page.data.showForm, true);
  assert.equal(page.data.isEdit, true);
  assert.equal(page.data.formName, '喝水');
  assert.match(page.data.error, /其他操作更新/);
  assert.equal(page.data.managed[0].name, '喝水');
});
test('健康打卡：家长表单拒绝空白名称和非法排序', async () => {
  const page = loadPage('health');
  page.toggleForm();
  page.setData({ formName: '   ', formOrder: '1' });
  await page.save();
  assert.match(page.data.error, /1-32/);

  page.setData({ formName: '喝水', formOrder: '1.5' });
  await page.save();
  assert.match(page.data.error, /排序需为/);
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
    // 心愿菜单为增强区块：孩子端每次读菜单会顺带取当天心愿单与可选菜谱目录。
    if (endpoint === '/child/wish-menu') return { ...emptyWish, menuDate: shanghaiDate() };
    if (endpoint === '/child/wish-catalog') return { items: [], total: 0, page: 1, pageSize: 10 };
    return { menuId: '20', sourceType: 'FAMILY', menuDate: shanghaiDate(), mealType: 'LUNCH',
      canSubmit: true, dishes: [{ ...dish, canSelect: true, categoryName: '主食' }] };
  };
  await page.read();
  assert.deepEqual(calls.map(call => call.endpoint),
    ['/menu/daily', '/child/frequent-dish', '/child/recommend', '/child/menu-week', '/child/wish-menu',
      '/child/wish-catalog']);
  assert.equal(page.data.wish.statusLabel, '未创建');
  assert.equal(page.data.wish.canEdit, true);
  assert.deepEqual(page.data.wishDishes, []);
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
test('菜单页：快捷卡和普通菜品行明确都是今天想吃的轻量标记', async () => {
  const page = loadPage('menu', 'CHILD');
  api.get = async endpoint => {
    if (endpoint === '/child/frequent-dish') {
      return { dishes: [{ type: 'PRESET', id: '99', name: '合成餐食', count: 4 }] };
    }
    if (endpoint === '/child/recommend') {
      return { dishes: [{ type: 'PRESET', id: '99', name: '合成餐食', reasons: ['最近常吃'] }] };
    }
    if (endpoint === '/child/menu-week') return { days: [] };
    if (endpoint === '/child/wish-menu') return { ...emptyWish, menuDate: shanghaiDate() };
    if (endpoint === '/child/wish-catalog') return { items: [], total: 0, page: 1, pageSize: 10 };
    return {
      menuId: '20',
      sourceType: 'FAMILY',
      menuDate: shanghaiDate(),
      mealType: 'LUNCH',
      canSubmit: true,
      dishes: [{ ...dish, isFavorite: false, canSelect: true }]
    };
  };

  await page.read();

  assert.equal(page.data.recommend[0].actionLabel, '记入今天想吃');
  assert.equal(page.data.recommend[0].actionHint, '轻量标记，不会提交确认单');
  assert.equal(page.data.frequent[0].actionLabel, '记入今天想吃');
  assert.equal(page.data.dishes[0].favoriteAriaLabel, '标记今天想吃');

  page.allDishes = [{ ...dish, isFavorite: true, canSelect: true }];
  page.quantities = {};
  page.setData({ menu: { menuId: '20', canSubmit: true, menuDate: shanghaiDate(), mealType: 'LUNCH' } });
  page.render();
  assert.equal(page.data.dishes[0].favoriteAriaLabel, '取消今天想吃');
  assert.equal(page.data.recommend[0].actionLabel, '取消今天想吃');
  assert.equal(page.data.frequent[0].actionLabel, '取消今天想吃');
  assert.equal(page.data.frequent[0].actionHint, '轻量标记，不会提交确认单');

  page.allDishes = [];
  page.render();
  for (const item of [...page.data.recommend, ...page.data.frequent]) {
    assert.equal(item.available, false);
    assert.equal(item.actionLabel, '今日餐单暂无');
    assert.equal(item.actionHint, '当前日期没有可标记的菜品');
  }
});
test('学校餐单：前置边界提示，仍可标记但不产生家庭确认单', async () => {
  const page = loadPage('menu', 'CHILD');
  page.setData({ sourceType: 'SCHOOL' });
  page.renderSourceNotice();
  assert.equal(page.data.schoolBoundaryText, '学校餐单只能查看和标记今天想吃，不会生成家长确认单');
  page.allDishes = [{ ...dish, isFavorite: false }];
  page.quantities = { 'PRESET:99': 1 };
  page.setData({ count: 1, childId: child.childId,
    menu: { menuId: '20', canSubmit: true, menuDate: shanghaiDate(), mealType: 'LUNCH' } });
  page.checkout();
  assert.equal(context.takeCart(), null);
  assert.deepEqual(navigation, []);

  const calls = [];
  api.post = async (endpoint, body) => { calls.push({ endpoint, body }); };
  page.read = async () => {};
  await page.favorite(event({ key: 'PRESET:99' }));
  assert.deepEqual(calls, [{ endpoint: '/menu/mark-favorite', body: {
    dishId: '99', dishType: 'PRESET', favorite: true,
    menuId: '20', menuDate: shanghaiDate(), mealType: 'LUNCH'
  } }]);
  assert.equal(context.takeCart(), null);

  page.source(event({ source: 'FAMILY' }));
  assert.equal(page.data.schoolBoundaryText, '');
  await new Promise(resolve => setImmediate(resolve));
  page.read = async () => { throw new Error('餐单暂不可用'); };
  page.source(event({ source: 'SCHOOL' }));
  assert.match(page.data.schoolBoundaryText, /学校餐单只能查看/);
  await new Promise(resolve => setImmediate(resolve));
  assert.equal(page.data.error, '餐单暂不可用');
  assert.match(page.data.schoolBoundaryText, /不会生成家长确认单/);
  page.onHide();
  assert.equal(page.data.schoolBoundaryText, '');
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
test('家长想吃看板：心愿菜谱标记单独标注，不冒充家庭菜单', async () => {
  const page = loadPage('want-eat');
  const today = shanghaiDate();
  page.setData({ today, from: today, to: today, childId: child.childId });
  api.get = async () => ({ childId: child.childId, from: today, to: today, today, expiredCount: 0,
    days: [{ menuDate: today, meals: [
      { mealType: 'ALL', sourceType: 'WISH', menuId: null,
        items: [{ wantEatId: '33', type: 'PRESET', id: '99', name: '心愿菜', status: 'MARKED', version: 0,
          expired: false, allergyConflict: false, disliked: false, missing: false }] },
      { mealType: 'LUNCH', sourceType: 'FAMILY', menuId: '20', items: [] }
    ] }],
    summary: { totalItems: 1, dishes: [] } });
  await page.read();
  const meals = page.data.days[0].meals;
  // 心愿目录标记落 meal_type=ALL / source_type=WISH：必须显示成"未指定餐次 · 心愿菜谱"，
  // 否则会被家长读成"今天家庭菜单里的菜"。
  assert.equal(meals[0].mealLabel, '未指定餐次');
  assert.equal(meals[0].sourceLabel, '心愿菜谱');
  assert.equal(meals[1].sourceLabel, '家庭菜单');
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
// ---------- 心愿菜单（P3）：家长配上限 / 儿童选菜提交 / 锁定与撤回 ----------
const wishPool = (overrides = {}) => ({ ...wishDish, submitted: false, missing: false, ...overrides });

test('心愿菜单：候选池勾选受家长上限约束，超限只提示不改选择', () => {
  const page = loadPage('menu', 'CHILD');
  page.wishRaw = { ...emptyWish, maxDishes: 2, dishCount: 3, items: [
    wishPool(), wishPool({ dishId: '100', name: '第二道' }), wishPool({ dishId: '101', name: '第三道' })
  ] };
  page.renderWish();
  // 未手动勾选过 ⇒ 按上限预勾选候选池前两道，减少孩子操作
  assert.equal(page.data.wish.countText, '2 / 2');
  assert.deepEqual(page.data.wishSelected, ['PRESET:99', 'PRESET:100']);

  page.wishToggle(event({ key: 'PRESET:101' }));
  assert.match(page.data.error, /最多勾选 2 道菜/);
  assert.deepEqual(page.data.wishSelected, ['PRESET:99', 'PRESET:100']);

  // 取消勾选永远允许，且超限提示被清掉
  page.wishToggle(event({ key: 'PRESET:100' }));
  assert.deepEqual(page.data.wishSelected, ['PRESET:99']);
  assert.equal(page.data.error, '');
});
test('心愿菜单：提交按 type/id 拆分并携带乐观锁版本', async () => {
  const page = loadPage('menu', 'CHILD');
  const family = wishPool({ dishId: '7', type: 'FAMILY', name: '家庭菜' });
  page.wishRaw = { ...emptyWish, version: 3, dishCount: 2, items: [wishPool(), family] };
  page.renderWish();
  page.wishSelectionTouched = true;
  page.setData({ wishSelected: ['PRESET:99', 'FAMILY:7'] });
  let sent;
  api.post = async (endpoint, body) => { sent = { endpoint, body }; return emptyWish; };
  api.get = async endpoint => endpoint === '/child/wish-menu' ? emptyWish : { items: [], total: 0 };

  await page.wishSubmit();
  assert.deepEqual(sent, { endpoint: '/child/wish-menu/submit', body: {
    menuDate: shanghaiDate(),
    refs: [{ type: 'PRESET', id: '99' }, { type: 'FAMILY', id: '7' }],
    expectedVersion: 3 } });
});
test('心愿菜单：已下架或待确认的候选菜不能加入，提示复用统一安全口径', () => {
  const page = loadPage('menu', 'CHILD');
  page.wishRaw = { ...emptyWish, items: [
    wishPool({ dishId: '88', name: '待确认菜', allergenStatus: 'UNKNOWN', safetyStatus: 'UNKNOWN', selectable: false }),
    wishPool({ dishId: '7', type: 'FAMILY', name: null, status: null, safetyStatus: null,
      selectable: false, missing: true })
  ] };
  page.renderWish();
  // 预勾选只挑仍可选的菜 ⇒ 这两道都不会被默认选中
  assert.deepEqual(page.data.wishSelected, []);
  assert.match(page.data.wish.items[0].flags, /未登记过敏信息/);
  assert.match(page.data.wish.items[1].flags, /已下架/);

  page.wishToggle(event({ key: 'PRESET:88' }));
  assert.match(page.data.error, /不能加入心愿菜单/);
  assert.deepEqual(page.data.wishSelected, []);
});
test('心愿菜单：锁定态勾选无效，撤回走确认并带版本', async () => {
  const page = loadPage('menu', 'CHILD');
  const locked = { ...emptyWish, status: 'SUBMITTED', locked: true, canEdit: false, canSubmit: false,
    version: 2, submittedCount: 1, items: [wishPool({ submitted: true })] };
  page.wishRaw = locked;
  page.renderWish();
  assert.equal(page.data.wish.statusLabel, '已提交');
  assert.deepEqual(page.data.wishSelected, ['PRESET:99'], '默认勾选上次提交的菜');
  page.wishToggle(event({ key: 'PRESET:99' }));
  assert.deepEqual(page.data.wishSelected, ['PRESET:99'], '锁定态不响应勾选变化');

  let sent;
  api.post = async (endpoint, body) => { sent = { endpoint, body }; return locked; };
  api.get = async endpoint => endpoint === '/child/wish-menu'
    ? { ...locked, status: 'WITHDRAWN', locked: false, canEdit: true } : { items: [], total: 0 };
  await page.wishWithdraw();
  assert.deepEqual(sent, { endpoint: '/child/wish-menu/withdraw',
    body: { menuDate: shanghaiDate(), expectedVersion: 2 } });
});
test('心愿菜单：目录复用统一安全提示，并标注已在候选池的菜', async () => {
  const page = loadPage('menu', 'CHILD');
  page.wishRaw = emptyWish;
  page.renderWish();
  api.get = async () => ({ items: [
    wishDish,
    { ...wishDish, dishId: '88', name: '待确认菜', allergenStatus: 'UNKNOWN', safetyStatus: 'UNKNOWN', selectable: false },
    { ...wishDish, dishId: '7', type: 'FAMILY', name: '家庭菜', marked: true }
  ], total: 3, page: 1, pageSize: 10 });
  await page.loadWishCatalog();
  assert.deepEqual(page.data.wishDishes.map(item => item.key), ['PRESET:99', 'PRESET:88', 'FAMILY:7']);
  assert.deepEqual(page.data.wishDishes.map(item => item.safetyLabel),
    ['过敏信息已声明', '未登记过敏信息，暂不可选择', '过敏信息已声明']);
  assert.deepEqual(page.data.wishDishes.map(item => item.actionLabel), ['加入心愿', '加入心愿', '移出心愿']);
  assert.deepEqual(page.data.wishDishes.map(item => item.typeLabel), ['预置菜谱', '预置菜谱', '家庭菜谱']);
});
test('心愿菜单：目录加入或移出候选池走 wish-mark', async () => {
  const page = loadPage('menu', 'CHILD');
  page.wishRaw = emptyWish;
  page.renderWish();
  let sent;
  api.post = async (endpoint, body) => { sent = { endpoint, body }; return emptyWish; };
  api.get = async endpoint => endpoint === '/child/wish-menu' ? emptyWish : { items: [], total: 0 };
  await page.wishMark(event({ type: 'PRESET', id: '99', marked: 'false' }));
  assert.deepEqual(sent, { endpoint: '/child/wish-mark',
    body: { menuDate: shanghaiDate(), type: 'PRESET', id: '99', selected: true } });
  await page.wishMark(event({ type: 'PRESET', id: '99', marked: 'true' }));
  assert.equal(sent.body.selected, false);
});
test('心愿菜单：四种只读原因分别展示，目录清空且所有操作不发请求', async () => {
  const page = loadPage('menu', 'CHILD');
  const today = shanghaiDate();
  const cases = [
    { raw: { enabled: false, canEdit: true, locked: false, menuDate: today },
      label: '未开启', notice: /家长尚未开启/ },
    { raw: { enabled: true, canEdit: true, locked: true, menuDate: today, status: 'SUBMITTED' },
      label: '已提交并锁定', notice: /已提交.*不可修改/ },
    { raw: { enabled: true, canEdit: true, locked: false, menuDate: '2099-01-01' },
      label: '日期不可编辑', notice: /今天起 7 天内/ },
    { raw: { enabled: true, canEdit: false, locked: false, menuDate: today },
      label: '暂不可编辑', notice: /暂不可编辑/ }
  ];
  const calls = [];
  api.get = async endpoint => { calls.push(endpoint); return { items: [], total: 0 }; };
  api.post = async endpoint => { calls.push(endpoint); return emptyWish; };
  for (const item of cases) {
    page.wishRaw = { ...emptyWish, ...item.raw, items: [wishPool()] };
    page.wishSelectionTouched = true;
    page.setData({ menuDate: item.raw.menuDate, wishSelected: [], wishDishes: [{ key: 'PRESET:99' }],
      wishTotal: 30, wishTab: 'FAMILY', wishPage: 2, wishKeyword: '' });
    page.renderWish();
    assert.equal(page.data.wish.stateLabel, item.label);
    assert.match(page.data.wish.noticeText, item.notice);
    assert.equal(page.data.wish.showCatalog, false);
    assert.equal(page.data.wish.canEdit, false);
    assert.deepEqual(page.data.wishDishes, []);
    assert.equal(page.data.wishTotal, 0);
    assert.equal(page.data.wish.items.length, 1, '候选池仍可只读回看');
    await page.wishMark(event({ type: 'PRESET', id: '99', marked: false }));
    await page.wishCatalogTab(event({ tab: 'PRESET' }));
    await page.wishSearch(event({}, '番茄'));
    await page.wishPageDelta(event({ delta: -1 }));
    await page.loadWishCatalog();
    assert.equal(page.data.wishTab, 'FAMILY');
    assert.equal(page.data.wishPage, 2);
    assert.equal(page.data.wishKeyword, '');
  }
  assert.deepEqual(calls, []);
});
test('心愿菜单：状态未加载时不允许目录操作，撤回后恢复可编辑目录', async () => {
  const page = loadPage('menu', 'CHILD');
  const calls = [];
  api.get = async endpoint => { calls.push(endpoint); return { items: [], total: 0 }; };
  api.post = async endpoint => { calls.push(endpoint); return emptyWish; };
  await page.loadWishCatalog();
  await page.wishMark(event({ type: 'PRESET', id: '99', marked: false }));
  await page.wishCatalogTab(event({ tab: 'PRESET' }));
  await page.wishSearch(event({}, '番茄'));
  assert.deepEqual(calls, []);
  assert.equal(page.data.wishTab, 'FAMILY');
  assert.equal(page.data.wishKeyword, '');

  page.wishRaw = { ...emptyWish, status: 'WITHDRAWN' };
  page.renderWish();
  assert.equal(page.data.wish.stateLabel, '已撤回，可重新编辑');
  assert.equal(page.data.wish.showCatalog, true);
  await page.wishCatalogTab(event({ tab: 'PRESET' }));
  assert.deepEqual(calls, ['/child/wish-catalog']);
  assert.equal(page.data.wishTab, 'PRESET');
});
test('心愿菜单：切换日期读取失败后清空旧目录，不能继续标记旧候选', async () => {
  const page = loadPage('menu', 'CHILD');
  page.wishRaw = emptyWish;
  page.renderWish();
  page.setData({ menuDate: '2099-01-01', wishDishes: [wishDish], wishTotal: 1 });
  const calls = [];
  api.get = async endpoint => { calls.push(endpoint); throw new Error('餐单加载失败'); };
  api.post = async endpoint => { calls.push(endpoint); };
  await assert.rejects(page.read(), /餐单加载失败/);
  assert.equal(page.data.wish, null);
  assert.deepEqual(page.data.wishDishes, []);
  assert.equal(page.data.wishTotal, 0);
  await page.wishMark(event({ type: 'PRESET', id: '99', marked: false }));
  await page.loadWishCatalog();
  assert.deepEqual(calls, ['/menu/daily']);
});
test('心愿菜单：七天窗口含首尾，越界及日期不匹配时不读取目录', async () => {
  const page = loadPage('menu', 'CHILD');
  const today = shanghaiDate();
  const dateAt = delta => {
    const date = new Date(today + 'T00:00:00Z');
    date.setUTCDate(date.getUTCDate() + delta);
    return date.toISOString().slice(0, 10);
  };
  const calls = [];
  api.get = async (endpoint, query) => { calls.push(query.menuDate); return { items: [], total: 0 }; };
  for (const delta of [-1, 0, 6, 7]) {
    const menuDate = dateAt(delta);
    page.setData({ menuDate });
    page.wishRaw = { ...emptyWish, menuDate, today };
    page.renderWish();
    assert.equal(page.data.wish.showCatalog, delta === 0 || delta === 6);
    await page.loadWishCatalog();
  }
  assert.deepEqual(calls, [today, dateAt(6)]);
  page.wishRaw = emptyWish;
  page.renderWish();
  page.setData({ menuDate: dateAt(1) });
  await page.loadWishCatalog();
  await page.wishMark(event({ type: 'PRESET', id: '99', marked: false }));
  assert.deepEqual(calls, [today, dateAt(6)]);
});
test('心愿菜单：关闭、锁定和日期越界均为只读，普通想吃仍可用', async () => {
  const page = loadPage('menu', 'CHILD');
  const today = shanghaiDate();
  const cases = [
    { enabled: false, canEdit: true, locked: false, menuDate: today, notice: /尚未开启/ },
    { enabled: true, canEdit: true, locked: true, menuDate: today, notice: /已提交/ },
    { enabled: true, canEdit: true, locked: false, menuDate: '2099-01-01', notice: /不在可编辑范围/ }
  ];
  for (const item of cases) {
    page.wishRaw = { ...emptyWish, ...item, items: [wishPool()] };
    page.wishSelectionTouched = true;
    page.setData({ menuDate: item.menuDate, wishSelected: [] });
    page.renderWish();
    assert.equal(page.data.wish.canEdit, false);
    assert.match(page.data.wish.noticeText, item.notice);
    page.wishToggle(event({ key: 'PRESET:99' }));
    assert.deepEqual(page.data.wishSelected, []);
    await page.loadWishCatalog();
    assert.deepEqual(page.data.wishDishes, []);
  }

  let sent;
  api.post = async (endpoint, body) => { sent = { endpoint, body }; };
  page.data.menu = { menuId: '20', canSubmit: true, menuDate: today, mealType: 'LUNCH' };
  page.data.menuDate = today;
  page.data.mealType = 'LUNCH';
  page.allDishes = [{ ...dish, sourceType: 'PRESET', isFavorite: false }];
  page.data.quantities = { 'PRESET:99': 1 };
  page.data.childId = child.childId;
  await page.favorite(event({ key: 'PRESET:99' }));
  assert.equal(sent.endpoint, '/menu/mark-favorite');
});
test('心愿菜单：wish-mark兼容布尔和字符串标记，鉴权错误不静默', async () => {
  const page = loadPage('menu', 'CHILD');
  page.wishRaw = emptyWish;
  page.renderWish();
  const calls = [];
  api.post = async (endpoint, body) => { calls.push({ endpoint, body }); return emptyWish; };
  api.get = async endpoint => endpoint === '/child/wish-menu' ? emptyWish : { items: [], total: 0 };
  await page.wishMark(event({ type: 'PRESET', id: '99', marked: false }));
  await page.wishMark(event({ type: 'PRESET', id: '99', marked: 'true' }));
  assert.deepEqual(calls.map(call => call.body.selected), [true, false]);

  api.get = async () => { throw Object.assign(new Error('登录已失效'), { status: 401 }); };
  await assert.rejects(page.loadWish(), error => error.status === 401);
});
test('心愿菜单：家长只看到孩子已提交的菜，保存设置带乐观锁版本', async () => {
  const page = loadPage('menu', 'PARENT');
  const submitted = wishPool({ submitted: true });
  const pending = wishPool({ dishId: '77', name: '只标记没提交' });
  const wish = { ...emptyWish, status: 'SUBMITTED', locked: true, canEdit: false, canSubmit: false,
    version: 4, submittedCount: 1, items: [submitted, pending] };
  api.get = async endpoint => {
    if (endpoint === '/parent/wish-menu') return wish;
    if (endpoint === '/parent/wish-setting') return { maxDishes: 2, enabled: true, version: 4 };
    return { items: [], total: 0, days: [] };
  };
  page.setData({ childId: child.childId, children: [child], childLabels: ['孩子 · 1'] });
  await page.loadWish();
  assert.deepEqual(page.data.wish.items.map(item => item.name), ['合成餐食'], '候选池明细不进家长视图');
  assert.deepEqual(page.data.wishSelected, [], '家长端不参与勾选');
  assert.equal(page.data.wishMaxDraft, 2);

  let sent;
  api.put = async (endpoint, body) => { sent = { endpoint, body }; };
  page.wishMax(event({}, 2));
  assert.equal(page.data.wishMaxDraft, 3);
  await page.wishSettingSave();
  assert.deepEqual(sent, { endpoint: '/parent/wish-setting',
    body: { maxDishes: 3, enabled: true, expectedVersion: 4 } });
});
test('我的菜品：食材与做法整体提交，空行自动丢弃', async () => {
  const page = loadPage('dish-manage');
  let sent;
  api.post = async (endpoint, body) => { sent = { endpoint, body }; return { dishId: '9' }; };
  page.create();
  page.setData({ formName: '番茄炒蛋', formCategoryId: '3', formVirtualPrice: '8.5' });
  // 路径写入走 ui.input（data-field），与真机一致：下标由 wx:for 的 index 拼出。
  page.input(event({ field: 'formIngredients[0].name' }, ' 番茄 '));
  page.input(event({ field: 'formIngredients[0].amount' }, '2 个'));
  page.addIngredient();
  page.input(event({ field: 'formIngredients[1].amount' }, '留空名称会被丢弃'));
  page.addIngredient();
  page.input(event({ field: 'formIngredients[2].name' }, '鸡蛋'));
  page.input(event({ field: 'formCookSteps[0].text' }, '打蛋'));
  page.addStep();
  page.input(event({ field: 'formCookSteps[1].text' }, '   '));
  page.addStep();
  page.input(event({ field: 'formCookSteps[2].text' }, '下锅翻炒'));
  page.input(event({ field: 'formCookTips' }, ' 给孩子吃可少放盐 '));
  page.input(event({ field: 'formCookMinutes' }, '15'));
  page.input(event({ field: 'formServings' }, '3'));
  page.pickDifficulty(event({}, 1));
  await page.save();
  assert.deepEqual(sent.endpoint, '/parent/family-dish');
  assert.deepEqual(sent.body.ingredients, [{ name: '番茄', amount: '2 个' }, { name: '鸡蛋', amount: '' }]);
  assert.deepEqual(sent.body.cookSteps, ['打蛋', '下锅翻炒']);
  assert.equal(sent.body.cookTips, '给孩子吃可少放盐');
  assert.equal(sent.body.cookMinutes, 15);
  assert.equal(sent.body.servings, 3);
  assert.equal(sent.body.difficulty, 'EASY');
});
test('我的菜品：配方行增删移不会越界或串行', async () => {
  const page = loadPage('dish-manage');
  page.create();
  page.setData({ formIngredients: [{ key: 'a', name: '番茄', amount: '' }, { key: 'b', name: '鸡蛋', amount: '' }] });
  page.moveIngredient(event({ index: 0, delta: -1 }));
  assert.deepEqual(page.data.formIngredients.map(row => row.name), ['番茄', '鸡蛋'], '首行上移应原样返回');
  page.moveIngredient(event({ index: 1, delta: 1 }));
  assert.deepEqual(page.data.formIngredients.map(row => row.name), ['番茄', '鸡蛋'], '末行下移应原样返回');
  page.moveIngredient(event({ index: 0, delta: 1 }));
  assert.deepEqual(page.data.formIngredients.map(row => row.name), ['鸡蛋', '番茄']);
  page.removeIngredient(event({ index: 0 }));
  assert.deepEqual(page.data.formIngredients.map(row => row.name), ['番茄']);
  // 容量上限：达到 30 条后「添加食材」不再增长（按钮同时会被 disabled）。
  page.setData({ formIngredients: Array.from({ length: 30 }, (unused, index) => ({ key: 'k' + index, name: '菜' + index })) });
  page.addIngredient();
  assert.equal(page.data.formIngredients.length, 30);
});
test('我的菜品：配方本地校验与后端规则一致', async () => {
  const page = loadPage('dish-manage');
  let posts = 0;
  api.post = async () => { posts++; };
  page.create();
  page.setData({ formName: '测试菜', formCategoryId: '3', formVirtualPrice: '8.00' });
  page.setData({ formIngredients: [{ key: 'a', name: '番茄' }, { key: 'b', name: '番茄' }] });
  await page.save();
  assert.match(page.data.error, /食材名称重复：番茄/);
  page.setData({ formIngredients: [{ key: 'a', name: '番'.repeat(33) }] });
  await page.save();
  assert.match(page.data.error, /食材名称最长 32 字/);
  page.setData({ formIngredients: [{ key: 'a', name: '番茄', amount: 'g'.repeat(33) }] });
  await page.save();
  assert.match(page.data.error, /食材用量最长 32 字/);
  page.setData({ formIngredients: [{ key: 'a', name: '番茄' }], formCookSteps: [{ key: 'b', text: 'x'.repeat(301) }] });
  await page.save();
  assert.match(page.data.error, /第 1 步做法最长 300 字/);
  page.setData({ formCookSteps: [{ key: 'b', text: '打蛋' }], formCookMinutes: '1441' });
  await page.save();
  assert.match(page.data.error, /1 至 1440/);
  page.setData({ formCookMinutes: '0' });
  await page.save();
  assert.match(page.data.error, /1 至 1440/);
  page.setData({ formCookMinutes: '15', formServings: '21' });
  await page.save();
  assert.match(page.data.error, /1 至 20 人份/);
  // 小贴士必须在份量之后再验：collectRecipe 的校验顺序是 食材 → 步骤 → 小贴士 → 时长 → 份量，
  // 若提前把 501 字的贴士留在 data 里，后面「时长越界」那条会先撞上贴士校验而误判。
  page.setData({ formServings: '2', formCookTips: '好'.repeat(501) });
  await page.save();
  assert.match(page.data.error, /小贴士最长 500 字/);
  page.setData({ formCookTips: '少放盐' });
  assert.equal(posts, 0, '校验未通过时绝不发请求');
  page.setData({ formServings: '' });
  await page.save();
  assert.equal(posts, 1);
});
test('我的菜品：列表只显示配方摘要，编辑时才拉取配方明细回填', async () => {
  const page = loadPage('dish-manage');
  const calls = [];
  api.get = async (endpoint) => {
    calls.push(endpoint);
    if (endpoint === '/parent/dish-category') return [{ categoryId: '3', name: '主食', status: 'ENABLED' }];
    return { items: [{ ...familyDish, version: 4, ingredientCount: 2, stepCount: 1, cookMinutes: 15,
      difficulty: 'MEDIUM' }], total: 1, page: 1, pageSize: 20 };
  };
  await page.loadCategories();
  await page.read();
  assert.deepEqual(calls, ['/parent/dish-category', '/parent/family-dish'], '列表不为每行请求配方');
  assert.equal(page.data.dishes[0].recipeSummary, '食材 2 · 步骤 1 · 15 分钟');
  assert.equal(page.data.dishes[0].difficultyLabel, '中等');

  api.get = async endpoint => {
    calls.push(endpoint);
    return { ingredients: [{ name: '番茄', amount: '2 个' }, { name: '鸡蛋', amount: null }],
      cookSteps: ['打蛋', '下锅'], cookTips: '少放盐', cookMinutes: 15, servings: 3, difficulty: 'MEDIUM' };
  };
  await page.edit(event({ id: '7' }));
  assert.deepEqual(calls[2], '/dishes/FAMILY/7/recipe');
  assert.deepEqual(page.data.formIngredients.map(row => row.name + '|' + row.amount), ['番茄|2 个', '鸡蛋|']);
  assert.deepEqual(page.data.formCookSteps.map(row => row.text), ['打蛋', '下锅']);
  assert.equal(page.data.formCookTips, '少放盐');
  assert.equal(page.data.formCookMinutes, '15');
  assert.equal(page.data.formServings, '3');
  assert.equal(page.data.formDifficultyIndex, 2);
  assert.equal(page.data.formRecipeNotice, '');
});
test('我的菜品：配方明细加载失败时警示覆盖风险且不污染页面级错误', async () => {
  const page = loadPage('dish-manage');
  api.get = async endpoint => {
    if (endpoint === '/parent/dish-category') return [{ categoryId: '3', name: '主食', status: 'ENABLED' }];
    if (endpoint.endsWith('/recipe')) throw Object.assign(new Error('服务暂不可用'), { status: 503, code: 'E-005' });
    return { items: [{ ...familyDish, version: 4 }], total: 1, page: 1, pageSize: 20 };
  };
  await page.loadCategories();
  await page.read();
  await page.edit(event({ id: '7' }));
  assert.match(page.data.formRecipeNotice, /覆盖原有食材与做法/);
  assert.equal(page.data.error, '');
  assert.equal(page.data.busy, false);
});
test('家长想吃看板：看做法懒加载一次并缓存，折叠不重发', async () => {
  const page = loadPage('want-eat');
  const today = shanghaiDate();
  page.setData({ today, from: today, to: today, childId: child.childId });
  const calls = [];
  api.get = async endpoint => {
    if (endpoint === '/parent/want-eat') {
      return { childId: child.childId, from: today, to: today, today, expiredCount: 0,
        days: [{ menuDate: today, meals: [{ mealType: 'LUNCH', sourceType: 'FAMILY', menuId: '20',
          items: [{ wantEatId: '31', type: 'PRESET', id: '99', name: '合成餐食', status: 'MARKED', version: 0,
            expired: false, allergyConflict: false, disliked: false, missing: false }] }] }],
        summary: { totalItems: 1, dishes: [] } };
    }
    calls.push(endpoint);
    return { ingredients: [{ name: '番茄', amount: '2 个' }], cookSteps: ['切块', '翻炒'],
      cookTips: '少放盐', cookMinutes: 15, servings: 3, difficulty: 'EASY' };
  };
  await page.read();
  const dish0 = page.data.days[0].meals[0].items[0];
  assert.equal(dish0.recipeKey, 'PRESET:99');
  assert.equal(dish0.canViewRecipe, true);

  await page.toggleRecipe(event({ key: 'PRESET:99' }));
  assert.equal(page.data.recipeOpenKey, 'PRESET:99');
  const state = page.data.recipeStates['PRESET:99'];
  assert.equal(state.status, 'ready');
  assert.equal(state.meta, '15 分钟 · 3 人份 · 简单');
  assert.deepEqual(state.steps.map(step => step.no), [1, 2]);
  assert.equal(state.ingredients[0].amountText, '2 个');
  assert.deepEqual(calls, ['/dishes/PRESET/99/recipe']);

  await page.toggleRecipe(event({ key: 'PRESET:99' }));
  assert.equal(page.data.recipeOpenKey, '', '已加载的条目再点只折叠');
  await page.toggleRecipe(event({ key: 'PRESET:99' }));
  assert.equal(page.data.recipeOpenKey, 'PRESET:99');
  assert.equal(calls.length, 1, '折叠再展开必须命中缓存');
});
test('家长想吃看板：配方加载失败给重试入口，菜品缺失时不给入口', async () => {
  const page = loadPage('want-eat');
  const today = shanghaiDate();
  page.setData({ today, from: today, to: today, childId: child.childId });
  api.get = async endpoint => {
    if (endpoint === '/parent/want-eat') {
      return { childId: child.childId, from: today, to: today, today, expiredCount: 0,
        days: [{ menuDate: today, meals: [{ mealType: 'DINNER', sourceType: 'FAMILY', menuId: '20',
          items: [
            { wantEatId: '31', type: 'FAMILY', id: '7', name: '家庭菜', status: 'MARKED', version: 0,
              expired: false, allergyConflict: false, disliked: false, missing: false },
            { wantEatId: '32', type: 'PRESET', id: '99', name: null, status: 'MARKED', version: 0,
              expired: false, allergyConflict: false, disliked: false, missing: true }] }] }],
        summary: { totalItems: 0, dishes: [] } };
    }
    throw Object.assign(new Error('连接失败，请检查网络后重试'), { status: 0, code: 'E-005' });
  };
  await page.read();
  const items = page.data.days[0].meals[0].items;
  assert.equal(items[0].canViewRecipe, true);
  assert.equal(items[1].canViewRecipe, false, '菜品已下架/删除：不给必然 404 的入口');

  await page.toggleRecipe(event({ key: 'FAMILY:7' }));
  const state = page.data.recipeStates['FAMILY:7'];
  assert.equal(state.status, 'error');
  assert.match(state.message, /连接失败/);
  assert.equal(page.data.error, '', '局部失败不污染页面级错误条');
  assert.equal(page.data.busy, false, 'busy 必须复位，否则重试按钮点不动');
});
test('家长想吃看板：换孩子与换区间清空配方缓存，避免串显示', async () => {
  const page = loadPage('want-eat');
  const today = shanghaiDate();
  page.setData({ today, from: today, to: today, childId: child.childId,
    children: [{ childId: '1' }, { childId: '2' }], childLabels: ['一', '二'],
    recipeStates: { 'PRESET:99': { status: 'ready' } }, recipeOpenKey: 'PRESET:99' });
  api.get = async () => ({ childId: '2', from: today, to: today, today, expiredCount: 0,
    days: [], summary: { totalItems: 0, dishes: [] } });
  await page.switchChild(event({}, 1));
  assert.equal(page.data.childId, '2');
  assert.deepEqual(page.data.recipeStates, {});
  assert.equal(page.data.recipeOpenKey, '');

  page.setData({ recipeStates: { 'PRESET:99': { status: 'ready' } }, recipeOpenKey: 'PRESET:99' });
  await page.shiftRange(event({ delta: '-1' }));
  assert.deepEqual(page.data.recipeStates, {});
  assert.equal(page.data.recipeOpenKey, '');

  page.setData({ recipeStates: { 'PRESET:99': { status: 'ready' } }, recipeOpenKey: 'PRESET:99' });
  page.onHide();
  assert.deepEqual(page.data.recipeStates, {});
});
