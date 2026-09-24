const api = require('../../services/api');
const session = require('../../services/session');
const ui = require('../../utils/page');
const lifecycle = require('../../services/lifecycle');
const { loadChildren, displayChildren } = require('../../services/children');
const { shanghaiDate, dishKey, selectable } = require('../../utils/domain');
const { summarizeMedals } = require('../../utils/medal');

const CHILD_HOME_TABS = ['meal', 'task', 'growth', 'me'];
const PARENT_HOME_TABS = ['home', 'me'];
const PARENT_SUMMARY_PAGE_SIZE = 1;
const CHILD_MEAL_SOURCES = ['FAMILY', 'SCHOOL'];
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
    childId: '', children: [], childLabels: [], childIndex: 0,
    parentSummaryUnavailable: false, parentSummaryMessage: '',
    parentSummary: {
      approvals: { status: 'idle', value: 0, error: '' },
      chores: { status: 'idle', value: 0, error: '' },
      wantEat: { status: 'idle', value: 0, error: '' }
    },
    // 点餐（孩子首页）增强区块
    familyCount: 0, schoolCount: 0, familyMenuId: '', mealMenuDishes: [], reminderStatus: '',
    recommend: [], frequent: [], wish: null,
    confirmStatus: null, confirmId: null,
    // 成长增强区块（只读）
    medals: [], medalEarned: 0, medalTotal: 0, balance: '', streak: 0
  },
  go: ui.go,
  onLoad(query) {
    this.requestedTab = query && query.tab ? query.tab : '';
    this.requestedChildId = query && query.childId ? query.childId : '';
  },
  onShow() {
    if (!ui.guard(this)) return;
    const tabs = this.data.role === 'PARENT' ? PARENT_HOME_TABS : CHILD_HOME_TABS;
    const fallback = this.data.role === 'PARENT' ? 'home' : 'meal';
    const active = tabs.includes(this.requestedTab) ? this.requestedTab : fallback;
    this.requestedTab = '';
    this.setData({ active });
    const revision = lifecycle.current();
    const generation = session.generation();
    return ui.run(this, async () => {
      const tasks = [this.loadUnread(revision, generation)];
      if (this.data.role === 'PARENT') tasks.push(this.loadParentHome(revision, generation));
      else tasks.push(this.loadChildTab(active));
      const results = await Promise.allSettled(tasks);
      const fatal = results.find(result => result.status === 'rejected'
        && this.isFatalLoadError(result.reason));
      if (fatal) throw fatal.reason;
    });
  },
  isFatalLoadError(error) {
    return Boolean(error && (error.cancelled || error.status === 401 || error.code === 'E-010'));
  },
  isCurrentLoad(revision, generation) {
    return revision === lifecycle.current() && generation === session.generation();
  },
  async loadUnread(revision, generation) {
    try {
      const data = await api.get('/notices/unread-count');
      if (this.isCurrentLoad(revision, generation)) this.setData({ unread: data.unreadCount });
    } catch (error) {
      if (this.isFatalLoadError(error)) throw error;
      // 通知角标是增强信息，失败不阻断家长待办摘要。
    }
  },
  summaryState(status, value, error) {
    return { status, value: value == null ? 0 : value, error: error || '' };
  },
  async loadParentHome(revision, generation) {
    let children;
    try {
      children = displayChildren(await loadChildren());
    } catch (error) {
      if (this.isFatalLoadError(error)) throw error;
      if (!this.isCurrentLoad(revision, generation)) return;
      const message = error.message || '暂无已绑定的儿童';
      this.setData({
        children: [], childLabels: [], childIndex: 0, childId: '',
        parentSummaryUnavailable: true, parentSummaryMessage: message,
        parentSummary: {
          approvals: this.summaryState('error', 0, message),
          chores: this.summaryState('error', 0, message),
          wantEat: this.summaryState('error', 0, message)
        }
      });
      return;
    }
    const requestedChildId = this.requestedChildId || this.data.childId;
    const index = Math.max(0, children.findIndex(item => item.childId === requestedChildId));
    const childId = children[index] && children[index].childId;
    if (!childId) {
      this.setData({ children: [], childLabels: [], childIndex: 0, childId: '',
        parentSummaryUnavailable: true, parentSummaryMessage: '暂无已绑定的儿童' });
      return;
    }
    if (!this.isCurrentLoad(revision, generation)) return;
    this.requestedChildId = '';
    this.setData({
      children,
      childLabels: children.map(item => item.displayName),
      childIndex: index,
      childId,
      parentSummaryUnavailable: false,
      parentSummaryMessage: '',
      parentSummary: {
        approvals: this.summaryState('loading'),
        chores: this.summaryState('loading'),
        wantEat: this.summaryState('loading')
      }
    });
    await this.loadParentMetrics(revision, generation, childId);
  },
  async loadParentMetrics(revision, generation, childId) {
    const today = shanghaiDate();
    const metrics = [
      ['approvals', api.get('/parent/approvals', {
        childId, page: 1, pageSize: PARENT_SUMMARY_PAGE_SIZE, status: 'PENDING'
      }).then(result => Number(result.total) || 0)],
      ['chores', api.get('/chore/instances', { childId, status: 'SUBMITTED' })
        .then(result => Array.isArray(result) ? result.length : 0)],
      ['wantEat', api.get('/parent/want-eat', { childId, from: today, to: today })
        .then(result => Number(result.summary && result.summary.totalItems) || 0)]
    ];
    await Promise.all(metrics.map(async ([name, request]) => {
      try {
        const value = await request;
        if (!this.isCurrentLoad(revision, generation) || this.data.childId !== childId) return;
        this.setData({ ['parentSummary.' + name]: this.summaryState('ready', value) });
      } catch (error) {
        if (this.isFatalLoadError(error)) throw error;
        if (!this.isCurrentLoad(revision, generation) || this.data.childId !== childId) return;
        this.setData({ ['parentSummary.' + name]: this.summaryState('error', 0, error.message || '暂时无法加载') });
      }
    }));
  },
  selectParentChild(e) {
    const index = Number(e.detail.value);
    const child = this.data.children[index];
    if (!child || child.childId === this.data.childId) return;
    const revision = lifecycle.current();
    const generation = session.generation();
    this.setData({
      childIndex: index, childId: child.childId,
      parentSummaryUnavailable: false, parentSummaryMessage: '',
      parentSummary: {
        approvals: this.summaryState('loading'),
        chores: this.summaryState('loading'),
        wantEat: this.summaryState('loading')
      }
    });
    return ui.run(this, () => this.loadParentMetrics(revision, generation, child.childId));
  },
  retryParentSummary() {
    if (!this.data.childId) {
      const revision = lifecycle.current();
      const generation = session.generation();
      return ui.run(this, () => this.loadParentHome(revision, generation));
    }
    const revision = lifecycle.current();
    const generation = session.generation();
    this.setData({
      parentSummary: {
        approvals: this.summaryState('loading'),
        chores: this.summaryState('loading'),
        wantEat: this.summaryState('loading')
      }
    });
    return ui.run(this, () => this.loadParentMetrics(revision, generation, this.data.childId));
  },
  openParentSummary(e) {
    const page = e.currentTarget.dataset.page;
    const childId = this.data.childId;
    if (!page || !childId) return;
    let query = '?childId=' + encodeURIComponent(childId);
    if (page === 'want-eat') query += '&range=today';
    return ui.openPage(this, '/pages/' + page + '/index' + query);
  },
  openMealSource(e) {
    if (this.data.busy || this.data.role !== 'CHILD') return;
    const sourceType = e && e.currentTarget && e.currentTarget.dataset.source;
    if (!CHILD_MEAL_SOURCES.includes(sourceType)) return;
    return ui.openPage(this, '/pages/menu/index?sourceType=' + sourceType);
  },
  // 孩子端各 tab 的增强数据：失败一律静默降级，绝不阻断首页骨架。
  async loadChildTab(active) {
    let children;
    try { children = displayChildren(await loadChildren()); } catch (error) { return; }
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
        noticeText: !wish.enabled ? '心愿菜单尚未开启，普通想吃标记仍可使用'
          : wish.locked ? '已提交给爸妈，如需调整请先撤回'
            : wish.canEdit ? '挑几道心愿菜，提交给爸妈' : '当前心愿菜单仅可查看',
        countText: (wish.submittedCount || 0) + ' / ' + (wish.maxDishes || 0)
      } : null,
      confirmStatus: confirm ? confirm.status : null,
      confirmId: confirm ? confirm.confirmId : null
    });
  },
  remindParent() {
    if (this.data.role !== 'CHILD' || this.data.familyCount !== 0) return;
    return ui.run(this, async () => {
      const result = await api.post('/child/menu-reminder', {});
      const status = result && result.status ? result.status : 'SENT';
      this.setData({ reminderStatus: status });
      const message = status === 'CREATED' ? '已提醒爸妈'
        : status === 'ALREADY_EXISTS' ? '今天已经提醒过爸妈啦' : '提醒已发送';
      wx.showToast({ title: message, icon: 'none' });
    });
  },
  async loadGrowth(childId) {
    const [medals, overview, calendar] = await Promise.all([
      api.get('/medal/awards', { childId }).catch(() => []),
      api.get('/wallet/overview', { childId }).catch(() => null),
      api.get('/child/check-in/calendar', { month: shanghaiDate().slice(0, 7) }).catch(() => null)
    ]);
    const medalSummary = summarizeMedals(medals);
    this.setData({
      medals: medalSummary.medals,
      medalEarned: medalSummary.medalEarned,
      medalTotal: medalSummary.medalTotal,
      balance: overview ? overview.balance : '',
      streak: calendar ? (calendar.currentStreak || 0) : 0
    });
  },
  // 点餐首页快捷卡：点击直接收藏，复用菜单页同口径校验（必须在今日家庭餐单内）。
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
      wx.showToast({ title: dish.isFavorite ? '已取消收藏' : '已收藏', icon: 'none' });
      await this.loadMealHome(this.data.childId);
    });
  },
  changeTab(e) {
    const key = e.detail.key;
    const childTabs = ['meal', 'task', 'growth', 'me'];
    const parentTabs = ['home', 'approvals', 'wallet', 'me'];
    const tabs = this.data.role === 'PARENT' ? parentTabs : childTabs;
    if (!tabs.includes(key) || key === this.data.active) return;
    if ((this.data.role === 'PARENT' && ['approvals', 'wallet'].includes(key))
      || (this.data.role === 'CHILD' && key === 'task')) {
      return ui.openTab(this, key);
    }
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
