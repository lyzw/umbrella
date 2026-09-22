function cents(value) {
  const text = String(value);
  if (!/^\d{1,8}(\.\d{1,2})?$/.test(text)) throw new Error('请输入最多两位小数的非负金额');
  const parts = text.split('.');
  return Number(parts[0]) * 100 + Number(((parts[1] || '') + '00').slice(0, 2));
}
function money(value) {
  if (!Number.isSafeInteger(value) || value < 0) throw new Error('金额超出范围');
  return Math.floor(value / 100) + '.' + String(value % 100).padStart(2, '0');
}
function shanghaiDate(date = new Date()) {
  return new Date(date.getTime() + 8 * 3600000).toISOString().slice(0, 10);
}
function safeDish(dish) {
  return dish.status === 'ON_SALE'
    && dish.allergenStatus === 'DECLARED' && !dish.allergyConflict;
}
function selectable(dish) { return dish.canSelect === true && safeDish(dish); }
// 孩子端菜品安全提示。后端 safetyStatus=UNKNOWN 有两个来源，用户能做的事不同，文案必须分开：
// ① 菜品自身未登记过敏原 ⇒ 需管理员补录；② 菜品已声明、但孩子档案的过敏原不在最新发布目录
// （fail-closed）⇒ 需家长更新档案，孩子自己无法解决。避免统一显示成「不可点」而无人可处理。
function safetyLabel(dish) {
  if (dish.status !== 'ON_SALE') return '已下架';
  if (dish.allergyConflict) return '含过敏原，不可选择';
  if (dish.allergenStatus !== 'DECLARED') return '未登记过敏信息，暂不可选择';
  if (dish.safetyStatus === 'UNKNOWN') return '档案过敏信息需家长更新，暂不可选择';
  return '过敏信息已声明';
}
function filterDishes(dishes, options = {}) {
  return dishes.filter(d => (!options.mildOnly || d.spiceLevel === 0)
    && (!options.favoritesOnly || d.isFavorite)
    && (!options.keyword || d.name.includes(options.keyword.trim())));
}
// 混合菜品（预置 PRESET / 家庭私有 FAMILY）用 {type,id} 标识，避免两来源自增 id 撞号。
// dishRef 只接受菜品对象；dishKey 同时兼容菜品对象和 {type,id} 引用，便于目录与已选项统一比对。
function dishRef(dish) { return { type: dish.sourceType || 'PRESET', id: String(dish.dishId) }; }
function dishKey(value) { return (value.type || value.sourceType || 'PRESET') + ':' + (value.id !== undefined ? value.id : value.dishId); }
function id(value) {
  if (typeof value !== 'string' || !/^[1-9]\d{0,18}$/.test(value)) throw new Error('无效的业务编号');
  return value;
}
function list(text) {
  const items = text.split(/[,，\n]/).map(item => item.trim()).filter(Boolean);
  if (items.length > 20 || items.some(item => item.length > 64)) throw new Error('最多20项，每项最多64字');
  return Array.from(new Set(items));
}
const statusLabels = { PENDING: '等待家长确认', COMPLETED: '已完成', REJECTED: '请调整后再提交', CANCELLED: '已撤回',
  NONE: '尚未申请', BOUND: '已绑定', RECEIVED: '已受理', PROCESSING: '办理中', READY: '可查看导出', FAILED: '办理失败', EXPIRED: '已过期' };
module.exports = { cents, money, shanghaiDate, safeDish, selectable, safetyLabel, filterDishes, dishRef, dishKey, id, list, statusLabels };
