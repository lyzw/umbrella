const api = require('../../services/api');
const session = require('../../services/session');
const ui = require('../../utils/page');
const { loadChildren } = require('../../services/children');
const { shanghaiDate, dishKey, selectable } = require('../../utils/domain');

const CHILD_HOME_TABS = ['meal', 'task', 'growth', 'me'];
const PARENT_HOME_TABS = ['home', 'me'];
// 心愿菜单状态文案（与 menu 页同源，避免重复依赖）。
const WISH_STATUS_LABELS = { NONE: '未创建', SUBMITTED: '已提交', WITHDRAWN: '已撤回' };
// 爸妈确认状态 → 儿童化文案（确认单全页已降级为点餐内状态条）。
const CONFIRM_LABELS = {
  PENDING: { text: '已提交，爸妈在看 👀', cls: 'warn' },
  COMPLETED: { text: '今天就能吃到 🎉', cls: 'ok' },
  REJECTED: { text: '爸妈说要调整一下，去点餐看看', cls: 'err' },
  CANCELLED: { text: '已撤回，可以重新点餐', cls: 'normal' }
};
ui.page({
  data: {
    role: '', active: 'meal', busy: false, error: '', unread: 0, synthetic: require('../../config').syntheticLogin,
    childId: '',
    // 点餐（孩子首页）增强区块
    familyCount: 0, schoolCount: 0, familyMenuId: '', mealMenuDishes: [],
    recommend: [], frequent: [], wish: null,
    confirmStatus: null, confirmId: null,
    // 成长增强区块（只读）
    medals: [], balance: '', streak: 0
  },
  go: ui.go,
  onLoad(query) {
    this.requestedTab = query && query.tab ? query.tab : '';
  },
  onShow() {
    if (!ui.guard(this)) return;
    const tabs = this.data.role === 'PARENT' ? PARENT_HOME_TABS : CHILD_HOME_TABS;
    const fallback = this.data.role === 'PARENT' ? 'home' : 'meal';
    const active = tabs.includes(this.requestedTab) ? this.requestedTab : fallback;
    this.requestedTab = '';
    this.setData({ active });
    ui.run(this, async () => {
      const data = await api.get('/notices/unread-count');
      this.setData({ unread: data.unreadCount });
      if (this.data.role === 'CHILD') await this.loadChildTab(active);
    });
  },
  // 孩子端各 tab 的增强数据：失败一律静默降级，绝不阻断首页骨架。
  async loadChildTab(active) {
    let children;
    try { children = await loadChildren(); } catch (error) { return; }
    const childId = children[0] ? children[0].childId : '';
    if (!childId) return;
    this.setData({ childId });
    if (active === 'meal') await this.loadMealHome(childId);
    else if (active === 'growth') await this.loadGrowth(childId);
  },
  async loadMealHome(childId) {
    const today = shanghaiDate();
    const [familyMenu, schoolMenu] = await Promise.all([
      api.get('/menu/daily', { sourceType: 'FAMILY', menuDate: today, mealType: 'LUNCH', childId }).catch(() => null),
      api.get('/menu/daily', { sourceType: 'SCHOOL', menuDate: today, mealType: 'LUNCH', childId }).catch(() => null)
    ]);
    const menuId = familyMenu ? familyMenu.menuId : '';
    const [recommend, frequent, wish] = await Promise.all([
      (menuId ? api.get('/child/recommend', { menuId, childId, limit: 3 }) : Promise.resolve({ dishes: [] })).catch(() => ({ dishes: [] })),
      api.get('/child/frequent-dish', { childId, limit: 6 }).catch(() => ({ dishes: [] })),
      api.get('/child/wish-menu', { menuDate: today }).catch(() => null)
    ]);
    const recommendDishes = (recommend.dishes || []).map(item => ({
      ...item, key: dishKey(item), reasonText: (item.reasons || []).join(' · ')
    }));
    const frequentDishes = (frequent.dishes || []).map(item => ({
      ...item, key: dishKey(item), countText: '近30天 ' + item.count + ' 次'
    }));
    let confirm = null;
    try {
      const list = await api.get('/menu/confirms', { childId, page: 1, pageSize: 1, status: 'PENDING' });
      confirm = (list.items || [])[0] || null;
    } catch (error) { confirm = null; }
    this.setData({
      familyCount: familyMenu ? (familyMenu.dishes || []).length : 0,
      schoolCount: schoolMenu ? (schoolMenu.dishes || []).length : 0,
      familyMenuId: menuId,
      mealMenuDishes: familyMenu ? (familyMenu.dishes || []) : [],
      recommend: recommendDishes, frequent: frequentDishes,
      wish: wish ? {
        status: wish.status,
        statusLabel: WISH_STATUS_LABELS[wish.status] || wish.status,
        countText: (wish.submittedCount || 0) + ' / ' + (wish.maxDishes || 0)
      } : null,
      confirmStatus: confirm ? confirm.status : null,
      confirmId: confirm ? confirm.confirmId : null
    });
  },
  async loadGrowth(childId) {
    const [medals, overview, calendar] = await Promise.all([
      api.get('/medal/awards', { childId }).catch(() => []),
      api.get('/wallet/overview', { childId }).catch(() => null),
      api.get('/child/check-in/calendar', { month: shanghaiDate().slice(0, 7) }).catch(() => null)
    ]);
    this.setData({
      medals: (medals || []).map(item => ({ ...item, definitionId: item.definition.definitionId })),
      balance: overview ? overview.balance : '',
      streak: calendar ? (calendar.currentStreak || 0) : 0
    });
  },
  // 点餐首页快捷卡：点击直接加入「想吃」，复用菜单页同口径校验（必须在今日家庭餐单内）。
  quickFavorite(e) {
    const key = e.currentTarget.dataset.key;
    const item = [...this.data.recommend, ...this.data.frequent].find(row => row.key === key);
    const dish = this.data.mealMenuDishes.find(row => dishKey(row) === key);
    if (!item || !dish || !selectable(dish)) {
      wx.showToast({ title: '今日餐单暂无可标记的菜品', icon: 'none' });
      return;
    }
    return ui.run(this, async () => {
      await api.post('/menu/mark-favorite', {
        dishId: item.id,
        dishType: item.type,
        favorite: !dish.isFavorite,
        menuId: this.data.familyMenuId,
        menuDate: shanghaiDate(),
        mealType: 'LUNCH'
      });
      wx.showToast({ title: dish.isFavorite ? '已取消想吃' : '已加入想吃', icon: 'none' });
      await this.loadMealHome(this.data.childId);
    });
  },
  changeTab(e) {
    const key = e.detail.key;
    if (key === 'approvals') return wx.navigateTo({ url: '/pages/confirmation/index' });
    if (key === 'wallet') return wx.navigateTo({ url: '/pages/wallet/index' });
    if (key === 'task') return wx.navigateTo({ url: '/pages/chore/index' });
    this.setData({ active: key });
    if (this.data.role === 'CHILD' && (key === 'meal' || key === 'growth')) {
      ui.run(this, () => this.loadChildTab(key));
    }
  },
  logout() {
    return ui.run(this, async () => {
      if (!await ui.confirm('退出会清除本机登录状态及未保存内容。')) return;
      await api.post('/auth/logout', {});
      session.clear();
      wx.reLaunch({ url: '/pages/login/index' });
    });
  }
});
