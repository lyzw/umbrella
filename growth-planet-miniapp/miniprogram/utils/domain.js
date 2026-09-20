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
function filterDishes(dishes, options = {}) {
  return dishes.filter(d => (!options.mildOnly || d.spiceLevel === 0)
    && (!options.favoritesOnly || d.isFavorite)
    && (!options.keyword || d.name.includes(options.keyword.trim())));
}
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
module.exports = { cents, money, shanghaiDate, safeDish, selectable, filterDishes, id, list, statusLabels };
