/**
 * 测试公共库：带 Cookie 罐的最小 HTTP 客户端 + 断言计数。
 * 零依赖，Node 18+ 原生 fetch；用法见 test/README.md。
 */
const baseUrlArg = process.argv.slice(2).find(arg => !arg.startsWith('--'));
export const BASE = baseUrlArg || process.env.BASE_URL || 'http://localhost:8080/DeliveryWeb';

/** 极简 Cookie 罐：吸收 Set-Cookie 并在后续请求带回（维持 Session） */
export class CookieJar {
    constructor() {
        this.map = new Map();
    }

    absorb(response) {
        const cookies = typeof response.headers.getSetCookie === 'function'
            ? response.headers.getSetCookie()
            : (response.headers.get('set-cookie') ? [response.headers.get('set-cookie')] : []);
        for (const cookie of cookies) {
            const pair = cookie.split(';')[0];
            const idx = pair.indexOf('=');
            if (idx > 0) {
                this.map.set(pair.slice(0, idx).trim(), pair.slice(idx + 1).trim());
            }
        }
    }

    header() {
        return [...this.map.entries()].map(([k, v]) => k + '=' + v).join('; ');
    }
}

/** 原始请求：返回 Response（自行读取 body），jar 存在时自动维持会话 */
export async function request(path, { method = 'GET', body, jar } = {}) {
    const headers = {};
    if (body !== undefined) {
        headers['Content-Type'] = 'application/json';
    }
    if (jar) {
        headers['Cookie'] = jar.header();
    }
    let response;
    try {
        response = await fetch(BASE + path, {
            method,
            headers,
            body: body === undefined ? undefined : JSON.stringify(body),
            redirect: 'manual'
        });
    } catch (e) {
        throw new Error('无法连接 ' + BASE + '，请先启动 Tomcat（或以参数/环境变量 BASE_URL 指定基地址）：' + e.message);
    }
    if (jar) {
        jar.absorb(response);
    }
    return response;
}

/** JSON 接口请求：返回 { status, json }（非 JSON 响应 json 为 null） */
export async function api(path, options = {}) {
    const response = await request(path, options);
    let json = null;
    try {
        json = await response.json();
    } catch (e) {
        // 非 JSON 响应，保留 HTTP 状态码判断
    }
    return { status: response.status, json };
}

/** 登录并返回独立 Cookie 罐（各角色会话隔离） */
export async function login(username, password) {
    const jar = new CookieJar();
    const result = await api('/api/auth/login', { method: 'POST', body: { username, password }, jar });
    return { jar, result };
}

let passed = 0;
let failed = 0;

export function check(name, condition, detail = '') {
    if (condition) {
        passed++;
        console.log('  [通过] ' + name);
    } else {
        failed++;
        console.log('  [失败] ' + name + (detail ? ' -> ' + detail : ''));
    }
}

/** 输出汇总并设置进程退出码（0 全过 / 1 有失败）；不用 process.exit，避免 Windows 下 libuv 句柄关闭断言 */
export function summary(suite) {
    console.log('');
    console.log(suite + '：通过 ' + passed + ' 项，失败 ' + failed + ' 项');
    process.exitCode = failed === 0 ? 0 : 1;
}
