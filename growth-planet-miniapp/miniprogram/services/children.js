const api = require('./api');
const session = require('./session');
async function loadChildren() {
  if (session.get().role === 'CHILD') {
    const binding = await api.get('/family/binding');
    if (binding.bindStatus !== 'BOUND') throw new Error('请先完成家庭绑定，再请家长同意并建立档案');
    return [binding];
  }
  // Only minimal relationship data is loaded here; profiles require a separate consent check.
  const items = [];
  let page = 1;
  let total;
  do {
    const result = await api.get('/family/children', { page, pageSize: 100, bindStatus: 'BOUND' });
    items.push(...result.items);
    total = result.total;
    if (!result.items.length) break;
    page++;
  } while (items.length < total);
  if (!items.length) throw new Error('尚无已绑定的儿童，请先前往家庭与绑定');
  return items;
}
module.exports = { loadChildren };
