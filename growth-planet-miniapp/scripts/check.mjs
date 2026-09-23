import fs from 'node:fs';
import path from 'node:path';
import vm from 'node:vm';
import { fileURLToPath } from 'node:url';
import { execFileSync } from 'node:child_process';
import assert from 'node:assert/strict';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const mini = path.join(root, 'miniprogram');
const read = file => fs.readFileSync(file, 'utf8');
function files(directory) {
  return fs.readdirSync(directory, { withFileTypes: true }).flatMap(entry => {
    const full = path.join(directory, entry.name);
    return entry.isDirectory() ? files(full) : [full];
  });
}
const all = files(mini);
const app = JSON.parse(read(path.join(mini, 'app.json')));
const project = JSON.parse(read(path.join(root, 'project.config.json')));
assert.equal(project.miniprogramRoot, 'miniprogram/');
assert.equal(project.compileType, 'miniprogram');
assert.equal(new Set(app.pages).size, app.pages.length);
const views = new Set(app.pages);
let javascript = 0, json = 0, bindings = 0, identifiers = 0, loops = 0;

// ---- 模板静态核对 ----
// 本机没有微信开发者工具（wcc/wcsc 不存在），WXML/WXSS 原生编译无法在本地覆盖，
// 下面三项静态核对用来兜住「改了模板、忘了改 JS」这类错误：真机上的表现是空白或点击无反应，
// 排查成本远高于一条断言。参考：v011 配方表单新增数据路径输入时踩过模板标识符漏改。
// 注意这些核对刻意做得宽松（只要标识符在 JS 文本里出现过就算存在），
// 目的是抓拼写错误而不是证明语义正确 —— 宁可漏报也不要用误报逼人加例外。

/** 小程序基础组件中的空元素：不成对出现，不入栈。 */
const VOID_TAGS = new Set([
  'image', 'input', 'icon', 'progress', 'slider', 'switch', 'checkbox', 'radio',
  'import', 'include', 'wxs', 'br', 'hr', 'slot'
]);

/** 标签闭合平衡（含自闭合写法），先剔注释避免把注释里的标签算进栈。 */
function assertBalancedTags(view, template) {
  const body = template.replace(/<!--[\s\S]*?-->/g, '');
  const stack = [];
  for (const match of body.matchAll(/<(\/?)([a-zA-Z][\w-]*)((?:"[^"]*"|'[^']*'|[^>"'])*?)(\/?)>/g)) {
    const [, closing, tag, , selfClosing] = match;
    if (closing) {
      const opened = stack.pop();
      assert.equal(opened, tag, `${view}: </${tag}> 与 <${opened ?? '无对应开标签'}> 不匹配`);
    } else if (!selfClosing && !VOID_TAGS.has(tag)) {
      stack.push(tag);
    }
  }
  assert.deepEqual(stack, [], `${view}: 存在未闭合标签 ${stack.join(', ')}`);
}

/** wx:for 必须带 wx:key：缺 key 时列表增删会错位复用节点，表现为输入框串行。 */
function assertWxForKeys(view, template) {
  let count = 0;
  for (const match of template.matchAll(/<([a-zA-Z][\w-]*)((?:"[^"]*"|'[^']*'|[^>"'])*?)>/g)) {
    const [, tag, attrs] = match;
    if (!/\bwx:for\s*=/.test(attrs)) continue;
    assert.match(attrs, /\bwx:key\s*=/, `${view}: <${tag} wx:for> 缺少 wx:key`);
    count++;
  }
  return count;
}

/** 模板 {{ }} 引用的标识符必须在页面 JS 中出现，抓「模板改了、JS 没改」。 */
function assertTemplateIdentifiers(view, template, pageSource) {
  // wx:for 的作用域变量与 wxs 模块名由模板自己声明，不在页面 JS 里，先收集成白名单。
  const scoped = new Set(['item', 'index']);
  for (const match of template.matchAll(/\bwx:for-(?:item|index)\s*=\s*["']([\w$]+)["']/g)) scoped.add(match[1]);
  for (const match of template.matchAll(/\bmodule\s*=\s*["']([\w$]+)["']/g)) scoped.add(match[1]);
  // ui.page / ui.guard 运行时注入的框架字段（前者 onShow 必 setData busy/error，
  // 后者注入 role）。多数页面在 data 里也显式声明，个别页面（dev-child-login）没声明 ——
  // 模板引用它们是合法的，不算未知标识符。
  const framework = new Set(['busy', 'error', 'role']);
  const keywords = new Set(['true', 'false', 'null', 'undefined', 'this']);
  const seen = new Set();
  for (const match of template.matchAll(/\{\{([\s\S]*?)\}\}/g)) {
    // 先抹掉字符串字面量：中文提示语里的词不是标识符，留着必然误报
    const expression = match[1]
      .replace(/'(?:[^'\\]|\\.)*'/g, "''")
      .replace(/"(?:[^"\\]|\\.)*"/g, '""');
    // 负向后行断言排除属性名：item.name 里只有 item 才算标识符
    for (const token of expression.matchAll(/(?<![.\w$])[A-Za-z_$][\w$]*/g)) {
      const name = token[0];
      if (keywords.has(name) || scoped.has(name) || framework.has(name)) continue;
      seen.add(name);
      assert.match(pageSource, new RegExp(`\\b${name}\\b`), `${view}: 模板引用的 ${name} 在页面 JS 中不存在`);
    }
  }
  return seen.size;
}
for (const file of all) {
  if (file.endsWith('.json')) {
    const config = JSON.parse(read(file));
    json++;
    for (const component of Object.values(config.usingComponents || {})) {
      const relative = component.startsWith('/') ? component.slice(1) : path.relative(mini, path.resolve(path.dirname(file), component));
      views.add(relative);
    }
  }
  if (file.endsWith('.js')) {
    const source = read(file);
    new vm.Script(source, { filename: file });
    javascript++;
    for (const match of source.matchAll(/require\(['"](\.[^'"]+)['"]\)/g)) {
      assert.ok(fs.existsSync(path.resolve(path.dirname(file), match[1] + '.js')), `${file}: unresolved ${match[1]}`);
    }
  }
}
for (const view of views) {
  for (const extension of ['.js', '.json', '.wxml']) assert.ok(fs.existsSync(path.join(mini, view + extension)), `${view}${extension} missing`);
  const source = read(path.join(mini, view + '.js'));
  const template = read(path.join(mini, view + '.wxml'));
  for (const match of template.matchAll(/\b(?:bind|catch):?[\w-]+=["']([A-Za-z_$][\w$]*)["']/g)) {
    assert.match(source, new RegExp('\\b' + match[1] + '\\s*(?:\\(|:)'), `${view}: handler ${match[1]} missing`);
    bindings++;
  }
  assertBalancedTags(view, template);
  loops += assertWxForKeys(view, template);
  identifiers += assertTemplateIdentifiers(view, template, source);
  for (const match of template.matchAll(/\bsrc=["']([^'"]+)["']/g)) {
    if (match[1].includes('{{') || /^https?:/.test(match[1])) continue;
    const target = match[1].startsWith('/') ? path.join(mini, match[1]) : path.resolve(path.dirname(path.join(mini, view)), match[1]);
    assert.ok(fs.existsSync(target), `${view}: asset ${match[1]} missing`);
  }
}
console.log(`Structure PASS: ${app.pages.length} pages, ${views.size - app.pages.length} component, ${javascript} JS, ${json} JSON, ${bindings} event bindings, ${identifiers} template identifiers, ${loops} wx:for all keyed (tags balanced).`);
if (process.argv.includes('--native')) {
  const compilerRoot = process.env.WECHAT_COMPILER_DIR ||
    '/Applications/wechatwebdevtools.app/Contents/Resources/package.nw/node_modules/wcc-exec';
  for (const [binary, extension, flags] of [['wcc', '.wxml', []], ['wcsc', '.wxss', ['-js']]]) {
    const inputs = all.filter(file => file.endsWith(extension)).map(file => './' + path.relative(mini, file));
    const output = execFileSync(path.join(compilerRoot, binary), [...flags, ...inputs],
      { cwd: mini, encoding: 'utf8', maxBuffer: 16 * 1024 * 1024, timeout: 30000 });
    assert.ok(output.length > 0, `${binary} generated no output`);
    console.log(`Native ${binary} PASS: ${inputs.length} files, ${Buffer.byteLength(output)} compiled bytes (not saved).`);
  }
}
