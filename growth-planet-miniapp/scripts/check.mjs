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
let javascript = 0, json = 0, bindings = 0;
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
  for (const match of template.matchAll(/\bsrc=["']([^'"]+)["']/g)) {
    if (match[1].includes('{{') || /^https?:/.test(match[1])) continue;
    const target = match[1].startsWith('/') ? path.join(mini, match[1]) : path.resolve(path.dirname(path.join(mini, view)), match[1]);
    assert.ok(fs.existsSync(target), `${view}: asset ${match[1]} missing`);
  }
}
console.log(`Structure PASS: ${app.pages.length} pages, ${views.size - app.pages.length} component, ${javascript} JS, ${json} JSON, ${bindings} event bindings.`);
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
