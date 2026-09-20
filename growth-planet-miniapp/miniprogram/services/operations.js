let sequence = 0;
function newKey() {
  sequence++;
  return 'mini_' + Date.now().toString(36) + '_' + sequence.toString(36) + '_' + Math.random().toString(36).slice(2, 12);
}
function createOperations(keyFactory = newKey) {
  const entries = new Map();
  return {
    async run(scope, body, send) {
      const serialized = JSON.stringify(body);
      let entry = entries.get(scope);
      if (entry && entry.serialized !== serialized) throw new Error('请先查询原操作或原样重试，不能变更尚未确认的请求');
      if (entry && entry.running) throw new Error('请求正在处理中');
      if (!entry) {
        entry = { serialized, body: JSON.parse(serialized), key: keyFactory() };
        entries.set(scope, entry);
      }
      entry.running = true;
      try {
        const result = await send(entry.body, entry.key);
        if (entries.get(scope) === entry) entries.delete(scope);
        return result;
      } catch (error) {
        if (!error.unknown && entries.get(scope) === entry) entries.delete(scope);
        throw error;
      } finally { entry.running = false; }
    },
    pending(scope) { const e = entries.get(scope); return e ? { body: JSON.parse(e.serialized), key: e.key } : null; },
    clear() { entries.clear(); }
  };
}
module.exports = { createOperations, operations: createOperations() };
