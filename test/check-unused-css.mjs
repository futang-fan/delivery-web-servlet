// 临时脚本：交叉比对 style.css 的 class 选择器与页面/脚本引用，输出未被引用的 class 清单
// 用法：node test/check-unused-css.mjs
import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const cssPath = path.join(root, 'webapp', 'common', 'style.css');
const css = fs.readFileSync(cssPath, 'utf8');

// 去注释后提取所有选择器块（{ 之前的文本）
const cssNoComment = css.replace(/\/\*[\s\S]*?\*\//g, '');
const classes = new Set();
const blockRe = /([^{}]+)\{/g;
let m;
while ((m = blockRe.exec(cssNoComment))) {
    const sel = m[1];
    const classRe = /\.(-?[a-zA-Z_][-\w]*)/g;
    let cm;
    while ((cm = classRe.exec(sel))) classes.add(cm[1]);
}

// 引用源：全部页面 HTML（pages/ 与 webapp 根目录如 error.html）+ common.js（含 Vue 模板 class/:class 与 JS 字符串）
const pagesDir = path.join(root, 'webapp', 'pages');
const files = fs.readdirSync(pagesDir).filter(f => f.endsWith('.html')).map(f => path.join(pagesDir, f));
fs.readdirSync(path.join(root, 'webapp')).filter(f => f.endsWith('.html'))
    .forEach(f => files.push(path.join(root, 'webapp', f)));
files.push(path.join(root, 'webapp', 'common', 'common.js'));
const haystack = files.map(f => fs.readFileSync(f, 'utf8')).join('\n');

const unused = [...classes].filter(c => !haystack.includes(c)).sort();
console.log('== 未被引用的 class（候选注释） ==');
console.log(unused.join('\n') || '(无)');
console.log(`TOTAL=${classes.size} UNUSED=${unused.length}`);
