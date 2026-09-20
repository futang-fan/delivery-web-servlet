// 前端静态语法校验：抽取页面内联 module 脚本 + common.js，逐个 node --check，
// 并对 common.js 中无 DOM 依赖的纯函数段跑断言。
// 用法：node test/dw-syntax-check.mjs（纯静态，无需启动 Tomcat / MySQL）
import fs from 'fs';
import path from 'path';
import { execFileSync } from 'child_process';
import { fileURLToPath, pathToFileURL } from 'url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const tmp = path.join(root, 'out', 'check');
fs.mkdirSync(tmp, { recursive: true });
const pages = fs.readdirSync(path.join(root, 'webapp/pages')).filter(f => f.endsWith('.html')).sort();

let bad = 0;
for (const p of pages) {
    const html = fs.readFileSync(path.join(root, 'webapp/pages', p), 'utf8');
    const m = html.match(/<script type="module">([\s\S]*?)<\/script>/);
    if (!m) { console.log('SKIP(no module script) ' + p); continue; }
    const file = path.join(tmp, 'chk-' + p.replace('.html', '') + '.mjs');
    fs.writeFileSync(file, m[1], 'utf8');
    try {
        execFileSync(process.execPath, ['--check', file], { stdio: 'pipe' });
        console.log('OK   ' + p);
    } catch (e) {
        bad++;
        console.log('FAIL ' + p + '\n' + (e.stderr ? e.stderr.toString() : e.message));
    }
}

const cj = path.join(tmp, 'chk-common.mjs');
fs.copyFileSync(path.join(root, 'webapp/common/common.js'), cj);
try {
    execFileSync(process.execPath, ['--check', cj], { stdio: 'pipe' });
    console.log('OK   common.js');
} catch (e) {
    bad++;
    console.log('FAIL common.js\n' + (e.stderr ? e.stderr.toString() : e.message));
}

console.log(bad === 0 ? 'ALL_SYNTAX_OK' : 'SYNTAX_FAIL=' + bad);

// 纯函数行为校验：截取 common.js 末尾无 DOM 依赖的 A1/A3/A4 工具段，导入后断言。
// 截取范围 = A1 标记 → 壳组件标记（壳组件依赖 Vue 运行时编译与 themeMixin，不能进独立模块求值）
const commonSrc = fs.readFileSync(path.join(root, 'webapp/common/common.js'), 'utf8');
const marker = '/* ---------- 未支付倒计时（A1） ---------- */';
const endMarker = '/* ---------- 页面壳组件（依赖 Vue 运行时编译；纯函数断言区到此为止，dw-syntax-check 据此截取） ---------- */';
const idx = commonSrc.indexOf(marker);
const endIdx = commonSrc.indexOf(endMarker);
if (idx < 0 || endIdx < 0 || endIdx < idx) {
    console.log('HELPERS_SECTION_NOT_FOUND');
} else {
    const helperFile = path.join(tmp, 'chk-helpers.mjs');
    fs.writeFileSync(helperFile, commonSrc.slice(idx, endIdx), 'utf8');
    const h = await import(pathToFileURL(helperFile).href);
    const asserts = [
        ['DEFAULT_ORDER_TIMEOUT_MINUTES=15', h.DEFAULT_ORDER_TIMEOUT_MINUTES === 15],
        ['parseDateTime 正常', h.parseDateTime('2026-09-11 12:30:45').getTime() === new Date(2026, 8, 11, 12, 30, 45).getTime()],
        ['parseDateTime 空值 null', h.parseDateTime('') === null && h.parseDateTime(null) === null],
        ['parseDateTime 脏值 null', h.parseDateTime('not-a-date') === null],
        ['payDeadline = +15min', h.payDeadline('2026-09-11 12:30:45') === new Date(2026, 8, 11, 12, 45, 45).getTime()],
        ['payDeadline 无效 null', h.payDeadline('x') === null],
        ['formatCountdown 15min', h.formatCountdown(15 * 60 * 1000) === '15:00'],
        ['formatCountdown 65s', h.formatCountdown(65 * 1000) === '01:05'],
        ['formatCountdown 负值归零', h.formatCountdown(-1000) === '00:00'],
        ['pickupCode 后四位', h.pickupCode('20260911123045123456') === '3456'],
        ['pickupCode 短号原样', h.pickupCode('12') === '12'],
        ['pickupCode 空值', h.pickupCode(null) === '—'],
        ['stockState 0 售罄', h.stockState(0) === 'SOLD_OUT'],
        ['stockState 9 告急', h.stockState(9) === 'LOW'],
        ['stockState 10 正常', h.stockState(10) === 'OK'],
        ['stockState 脏值售罄', h.stockState(null) === 'SOLD_OUT'],
        ['quickRange 7天含今天', (() => {
            const r = h.quickRange(7);
            const f = (d) => d.getFullYear() + '-' + String(d.getMonth() + 1).padStart(2, '0') + '-' + String(d.getDate()).padStart(2, '0');
            const end = new Date();
            const start = new Date();
            start.setDate(end.getDate() - 6);
            return r.end === f(end) && r.start === f(start);
        })()],
        ['quickRange 非法 null', h.quickRange(0) === null && h.quickRange(-1) === null]
    ];
    let hbad = 0;
    for (const [name, ok] of asserts) {
        if (!ok) hbad++;
        console.log((ok ? 'OK   ' : 'FAIL ') + name);
    }
    console.log(hbad === 0 ? 'ALL_HELPERS_OK' : 'HELPERS_FAIL=' + hbad);
    if (bad === 0 && hbad > 0) process.exitCode = 1;
}
