import assert from 'node:assert/strict';
import { existsSync, readdirSync, readFileSync, statSync } from 'node:fs';
import { dirname, extname, relative, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = dirname(fileURLToPath(import.meta.url));
const baselineName = '成长星球_V0.0.1_审计修订基线.md';
const planName = '成长星球_V0.0.1_开发计划.html';
const uiName = '成长星球_高保真UI设计方案.html';
const failures = [];
let checks = 0;

function check(condition, message) {
  checks += 1;
  if (!condition) failures.push(message);
}

function walk(directory) {
  return readdirSync(directory, { withFileTypes: true }).flatMap(entry => {
    const path = resolve(directory, entry.name);
    return entry.isDirectory() ? walk(path) : [path];
  });
}

function idsIn(html) {
  return [...html.matchAll(/\bid\s*=\s*["']([^"']+)["']/g)].map(match => match[1]);
}

function duplicateIds(html) {
  const ids = idsIn(html);
  return ids.filter((id, index) => ids.indexOf(id) !== index);
}

function localTarget(source, raw) {
  if (!raw || /^(?:[a-z][a-z\d+.-]*:|\/\/)/i.test(raw)) return null;
  const [address, fragment = ''] = raw.replaceAll('&amp;', '&').split('#');
  return {
    path: address ? resolve(dirname(source), decodeURIComponent(address.split('?')[0])) : source,
    fragment: decodeURIComponent(fragment),
  };
}

function linkIssue(source, raw, fileExists = existsSync, read = path => readFileSync(path, 'utf8')) {
  let target;
  try {
    target = localTarget(source, raw);
  } catch {
    return `invalid URL encoding: ${raw}`;
  }
  if (!target) return null;
  if (!fileExists(target.path)) return `missing target: ${raw}`;
  if (target.fragment && extname(target.path) === '.html' && !idsIn(read(target.path)).includes(target.fragment)) {
    return `missing HTML anchor: ${raw}`;
  }
  return null;
}

// Deliberately supports only the dependency diagram's declared node/chain grammar.
// Unknown syntax fails closed rather than silently omitting an edge.
function parseDependencies(graph) {
  const nodes = new Map();
  const edges = [];
  let sprint = null;
  for (const raw of graph.split('\n')) {
    const line = raw.trim();
    if (!line || line === 'graph TD') continue;
    const group = line.match(/^subgraph S([1-4])\[.+\]$/);
    if (group) {
      if (sprint !== null) throw new Error('nested sprint');
      sprint = Number(group[1]);
      continue;
    }
    if (line === 'end') {
      if (sprint === null) throw new Error('unexpected end');
      sprint = null;
      continue;
    }
    const node = line.match(/^([A-D]\d+)\[.+\]$/);
    if (node) {
      if (sprint === null || nodes.has(node[1])) throw new Error(`invalid node: ${line}`);
      nodes.set(node[1], sprint);
      continue;
    }
    if (/^[A-D]\d+(?:\s+-->\s+[A-D]\d+)+$/.test(line)) {
      const chain = line.split(/\s+-->\s+/);
      for (let index = 1; index < chain.length; index += 1) edges.push([chain[index - 1], chain[index]]);
      continue;
    }
    throw new Error(`unsupported dependency syntax: ${line}`);
  }
  if (sprint !== null || !nodes.size) throw new Error('incomplete dependency graph');
  for (const [from, to] of edges) {
    if (!nodes.has(from) || !nodes.has(to)) throw new Error(`undeclared dependency: ${from} -> ${to}`);
  }
  return { nodes, edges };
}

function hasCycle(nodes, edges) {
  const adjacency = new Map([...nodes.keys()].map(id => [id, []]));
  for (const [from, to] of edges) adjacency.get(from).push(to);
  const visiting = new Set();
  const visited = new Set();
  function visit(id) {
    if (visiting.has(id)) return true;
    if (visited.has(id)) return false;
    visiting.add(id);
    if (adjacency.get(id).some(visit)) return true;
    visiting.delete(id);
    visited.add(id);
    return false;
  }
  return [...nodes.keys()].some(visit);
}

function luminance(hex) {
  const rgb = hex.match(/[\da-f]{2}/gi).map(value => parseInt(value, 16) / 255);
  const linear = rgb.map(value => value <= 0.04045 ? value / 12.92 : ((value + 0.055) / 1.055) ** 2.4);
  return linear[0] * 0.2126 + linear[1] * 0.7152 + linear[2] * 0.0722;
}

function selfTest() {
  assert.deepEqual(duplicateIds('<p id="x"></p><p id="x"></p>'), ['x']);
  assert.equal(linkIssue('/docs/a.html', 'missing.html', () => false), 'missing target: missing.html');
  assert.equal(linkIssue('/docs/a.html', '#lost', () => true, () => '<p id="ok"></p>'), 'missing HTML anchor: #lost');
  assert.equal(linkIssue('/docs/a.html', '%xx'), 'invalid URL encoding: %xx');
  const fixture = 'graph TD\nsubgraph S1[Test]\nA1[One]\nA2[Two]\nend\nA1 --> A2';
  const { nodes, edges } = parseDependencies(fixture);
  assert.equal(hasCycle(nodes, edges), false);
  assert.equal(hasCycle(nodes, [...edges, ['A2', 'A1']]), true);
  assert.throws(() => parseDependencies(`${fixture}\nA2 --> B99`), /undeclared/);
  assert.throws(() => parseDependencies(`${fixture}\nA1 -.-> A2`), /unsupported/);
  assert.ok((1.05 / (luminance('#3b82f6') + 0.05)) < 4.5);
}

function main() {
  selfTest();
  const files = walk(root).filter(path => ['.html', '.md'].includes(extname(path)));
  const contents = new Map(files.map(path => [path, readFileSync(path, 'utf8')]));
  const htmlFiles = files.filter(path => extname(path) === '.html');
  check(htmlFiles.length === 11, 'expected 11 reviewed HTML documents; update coverage when adding documents');
  const index = contents.get(resolve(root, 'README.md'));
  for (const [path, text] of contents) {
    const label = relative(root, path);
    const isHtml = extname(path) === '.html';
    const searchable = isHtml ? text.replace(/<!--[\s\S]*?-->/g, '') : text.replace(/```[^\n]*\n[\s\S]*?```/g, '');
    const links = isHtml
      ? [...searchable.matchAll(/\b(?:href|src)\s*=\s*["']([^"']+)["']/g)].map(match => match[1])
      : [...searchable.matchAll(/!?\[[^\]]*\]\(([^)\s]+)\)/g)].map(match => match[1]);
    for (const link of links) check(!linkIssue(path, link), `${label}: ${linkIssue(path, link)}`);
    if (!isHtml) continue;
    check(!duplicateIds(searchable).length, `${label}: duplicate HTML IDs`);
    for (const tag of ['html', 'head', 'body', 'style', 'table', 'tr', 'td', 'th', 'div', 'aside', 'ul', 'ol', 'li']) {
      const opens = [...searchable.matchAll(new RegExp(`<${tag}(?:\\s[^>]*?)?>`, 'gi'))].length;
      const closes = [...searchable.matchAll(new RegExp(`</${tag}\\s*>`, 'gi'))].length;
      check(opens === closes, `${label}: unbalanced ${tag}: ${opens}/${closes}`);
    }
    check(/<!doctype html>/i.test(text), `${label}: missing HTML doctype`);
    check(/<html\b[^>]*lang=["']zh/i.test(text), `${label}: missing Chinese language`);
    check(/<meta\b[^>]*charset=["']?utf-8/i.test(text), `${label}: missing UTF-8`);
    check(text.includes(baselineName) && text.includes('README.md'), `${label}: missing audit navigation`);
    check(/审计修订|历史报告勘误/.test(text), `${label}: missing audit status`);
    check(index.includes(label), `${label}: missing from README`);
    if (label.startsWith('评估记录/')) {
      check(text.includes('id="audit-errata"'), `${label}: missing historical errata`);
      continue;
    }
    for (const old of ['自报年龄核验', 'token 失效与刷新', '双通道触达 ≥ 99%', 'uk_school_date_meal', 'channel 三态', 'F-012 零花钱流水', 'F-012 消费记录']) {
      check(!text.includes(old), `${label}: stale contract: ${old}`);
    }
    const diagrams = [...text.matchAll(/<div class="mermaid">([\s\S]*?)<\/div>/g)].map(match => match[1]);
    if (diagrams.length) {
      check(text.includes('mermaid@10.9.8/dist/mermaid.min.js'), `${label}: unpinned Mermaid`);
      check(text.includes('if(window.mermaid)'), `${label}: missing CDN failure guard`);
      for (const graph of diagrams) {
        check(!/\bAPPROVED\b/.test(graph), `${label}: obsolete APPROVED state in diagram`);
      }
    }
  }

  const baseline = contents.get(resolve(root, baselineName));
  for (const [prefix, count] of [['AUD', 24], ['AC', 10], ['Q', 8]]) {
    for (let number = 1; number <= count; number += 1) {
      const id = `${prefix}-${String(number).padStart(2, '0')}`;
      check(baseline.includes(id), `baseline: missing ${id}`);
    }
  }
  for (const contract of ['SELF_ATTESTED', 'Idempotency-Key', 'expectedVersion', 'previous_confirm_id', 'daily_period', 'weekly_period', 'Asia/Shanghai', 'E-011', 'E-012', 'sys_privacy_request', 'UNKNOWN', 'canSubmit', 'SCHOOL', 'FAMILY']) {
    check(baseline.includes(contract), `baseline: missing ${contract}`);
  }
  const plan = contents.get(resolve(root, planName));
  const graph = plan.match(/<div class="mermaid">\s*(graph TD[\s\S]*?)<\/div>/)?.[1];
  if (!graph) throw new Error('missing Sprint dependency diagram');
  const { nodes, edges } = parseDependencies(graph);
  check(!hasCycle(nodes, edges), 'Sprint dependency cycle');
  for (const [from, to] of edges) check(nodes.get(from) <= nodes.get(to), `backward Sprint dependency: ${from} -> ${to}`);
  for (const [from, to] of [['A5', 'A7'], ['A7', 'A6'], ['A6', 'A3'], ['A10', 'A5'], ['A10', 'B3'], ['B4', 'C5']]) {
    check(edges.some(edge => edge[0] === from && edge[1] === to), `missing critical dependency: ${from} -> ${to}`);
  }

  const ui = contents.get(resolve(root, uiName));
  for (const value of ['虚拟 ¥30（单日上限 ¥20）', '今日已用 ¥18 / 单日上限 ¥20', 'width:90%', '+虚拟¥50', '−虚拟¥18', '家庭菜单 · 3 道可选']) {
    check(ui.includes(value), `UI: missing corrected sample ${value}`);
  }
  check(/\.stepper button\{width:44px;height:44px/.test(ui), 'UI: stepper targets smaller than project standard');
  for (const match of ui.matchAll(/font-size:\s*([\d.]+)px/g)) check(Number(match[1]) >= 12, `UI: font smaller than 12px: ${match[1]}`);
  const contrasts = [];
  for (const token of ['kid', 'parent']) {
    const color = ui.match(new RegExp(`--${token}:(#[\\da-fA-F]{6})`))?.[1];
    if (!color) throw new Error(`missing UI token ${token}`);
    const ratio = 1.05 / (luminance(color) + 0.05);
    contrasts.push(`${token}=${ratio.toFixed(2)}:1`);
    check(ratio >= 4.5, `UI: ${token} white-text contrast below 4.5:1`);
  }
  check(statSync(resolve(root, 'check-docs.mjs')).isFile(), 'checker missing');
  if (failures.length) {
    for (const failure of failures) console.error(`FAIL ${failure}`);
    console.error(`${failures.length} failures / ${checks} checks`);
    process.exitCode = 1;
    return;
  }
  console.log(`PASS ${checks} checks; ${htmlFiles.length} HTML / ${files.length - htmlFiles.length} Markdown files`);
  console.log(`Dependency graph: ${nodes.size} nodes / ${edges.length} edges; acyclic, no backward Sprint edges`);
  console.log(`White-text contrast: ${contrasts.join(', ')}; malformed-fixture self-tests passed`);
  console.log('Scope: static source checks only; not a full HTML/Markdown/Mermaid parser, browser test, API test, migration or compliance review.');
}

try {
  main();
} catch (error) {
  console.error(`Document check failed: ${error.stack ?? error.message}`);
  process.exitCode = 1;
}
