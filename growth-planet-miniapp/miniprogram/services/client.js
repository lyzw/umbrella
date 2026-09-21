function createClient(platform, getSession, clearSession, base, timeout = 12000, getRevision = () => 0) {
  const request = (method, path, data, key, document = false) => new Promise((resolve, reject) => {
    const auth = getSession();
    const revision = getRevision();
    const stale = () => {
      const current = getSession();
      return revision !== getRevision() || (auth && auth.token) !== (current && current.token);
    };
    const cancel = () => reject(Object.assign(new Error('页面或登录状态已变化，请重新查询'), {
      cancelled: true, unknown: method !== 'GET'
    }));
    // GET/DELETE 通过 query string 传参；POST/PUT 通过 body。
    const queryInUrl = method === 'GET' || method === 'DELETE';
    let url = base.replace(/\/$/, '') + '/api' + path;
    if (queryInUrl && data) {
      const query = Object.keys(data).filter(k => data[k] !== undefined && data[k] !== null && data[k] !== '')
        .map(k => encodeURIComponent(k) + '=' + encodeURIComponent(data[k])).join('&');
      if (query) url += '?' + query;
    }
    const header = { 'Content-Type': 'application/json' };
    if (auth) header.Authorization = 'Bearer ' + auth.token;
    if (key) header['Idempotency-Key'] = key;
    platform.request({
      url, method, header, timeout, data: queryInUrl ? undefined : data,
      success(response) {
        if (stale()) return cancel();
        const body = response.data || {};
        if (response.statusCode === 401) clearSession();
        if (document && response.statusCode >= 200 && response.statusCode < 300 && body.format === 'PROFILE_RIGHTS_V1') return resolve(body);
        if (response.statusCode >= 200 && response.statusCode < 300 && body.code === 0) return resolve(body.data);
        const unknown = method !== 'GET' && (response.statusCode >= 500 || !body.code);
        reject(Object.assign(new Error(body.message || '服务暂不可用，请稍后重试'), {
          status: response.statusCode, code: body.code || 'E-005', data: body.data,
          requestId: body.requestId, unknown
        }));
      },
      fail() {
        if (stale()) return cancel();
        reject(Object.assign(new Error(method === 'GET' ? '连接失败，请检查网络后重试' : '结果尚未确认，请查询原操作或原样重试'), {
          code: 'E-005', unknown: method !== 'GET', status: 0
        }));
      }
    });
  });
  return {
    get: (path, query) => request('GET', path, query),
    getDocument: path => request('GET', path, undefined, undefined, true),
    post: (path, body, key) => request('POST', path, body, key),
    put: (path, body) => request('PUT', path, body),
    del: (path, query) => request('DELETE', path, query)
  };
}
module.exports = { createClient };
