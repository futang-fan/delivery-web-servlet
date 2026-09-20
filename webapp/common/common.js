/**
 * DeliveryWeb 前端公共模块（ESM）。
 * 1. fetch 封装：JSON 提交、统一错误提示、401 自动跳登录页；
 * 2. 状态字典：设计书表2.16 全部枚举取值与中文名；
 * 3. 角色跳转：登录成功后按角色进入各自主页面。
 * 所有页面通过 <script type="module"> 引入本文件，禁止各页面重复实现。
 */

/* ---------- 上下文路径 ---------- */

/** 应用上下文：部署为 /DeliveryWeb，本地直开文件时为空串 */
import { reactive } from '../libs/vue.esm-browser.prod.js';

export const CTX = location.pathname.startsWith('/DeliveryWeb') ? '/DeliveryWeb' : '';

/* ---------- fetch 封装 ---------- */

/**
 * 统一请求入口：JSON 提交，业务失败（code != 200）与网络异常均抛出 Error，
 * 页面侧只需 try/catch 后展示 e.message。
 * 未登录（HTTP 401）时自动跳转登录页；auth 接口自身除外，避免登录页死循环。
 *
 * @param {string} path   接口路径，如 '/api/auth/login'
 * @param {object} [options]
 * @param {string} [options.method='POST'] 请求方法
 * @param {object} [options.body]          请求体对象（自动 JSON 序列化）
 * @returns {Promise<object>} 标准响应 { code, msg, data }
 */
export async function request(path, { method = 'POST', body } = {}) {
    const options = {
        method,
        headers: { 'Content-Type': 'application/json' }
    };
    if (body !== undefined && method !== 'GET') {
        options.body = JSON.stringify(body);
    }

    let response;
    try {
        response = await fetch(CTX + path, options);
    } catch (e) {
        throw new Error('无法连接服务器，请确认后端已启动');
    }

    if (response.status === 401 && !path.startsWith('/api/auth/')) {
        location.href = CTX + '/pages/login.html';
        throw new Error('登录已过期，正在返回登录页');
    }

    let result = null;
    try {
        result = await response.json();
    } catch (e) {
        // 非 JSON 响应，保留 HTTP 状态码提示
    }

    if (!response.ok) {
        throw new Error((result && result.msg) || '请求失败（HTTP ' + response.status + '）');
    }
    if (result === null || Number(result.code) !== 200) {
        throw new Error((result && result.msg) || '操作失败');
    }
    return result;
}

/** GET 请求快捷方式 */
export const get = (path) => request(path, { method: 'GET' });

/** POST 请求快捷方式 */
export const post = (path, body) => request(path, { method: 'POST', body });

/** 退出登录：失效会话后回登录页；会话已过期时同样跳转 */
export async function logout() {
    try {
        await post('/api/auth/logout', {});
    } catch (e) {
        // 会话可能已失效，忽略错误继续跳转
    }
    clearCachedUser();
    location.href = CTX + '/pages/login.html';
}

/* ---------- 角色跳转 ---------- */

/** 角色 -> 主页面*/
export const HOME_PAGES = {
    CUSTOMER: 'shops.html',
    SHOP: 'merchant.html',
    RIDER: 'rider-pool.html',
    ADMIN: 'admin.html'
};

/** 角色 -> 顶栏/侧边栏导航链接（账户中心统一入列；各登录态页经 AppTopbar/AppSidenav 单源渲染） */
export const NAV_PAGES = {
    CUSTOMER: [
        { href: 'shops.html', text: '找店点餐' },
        { href: 'my-orders.html', text: '我的订单' },
        { href: 'address.html', text: '收货地址' },
        { href: 'account.html', text: '账户中心' }
    ],
    SHOP: [
        { href: 'merchant.html', text: '商家中心' },
        { href: 'dishes.html', text: '菜品管理' },
        { href: 'merchant-orders.html', text: '订单管理' },
        { href: 'account.html', text: '账户中心' }
    ],
    RIDER: [
        { href: 'rider-pool.html', text: '配送池' },
        { href: 'rider-delivery.html', text: '配送详情' },
        { href: 'account.html', text: '账户中心' }
    ],
    ADMIN: [
        { href: 'admin.html?tab=audit', text: '商家审核' },
        { href: 'admin.html?tab=users', text: '用户管理' },
        { href: 'admin.html?tab=shops', text: '店铺监管' },
        { href: 'admin.html?tab=orders', text: '订单查询' },
        { href: 'admin.html?tab=refunds', text: '退款仲裁' },
        { href: 'admin.html?tab=resets', text: '找回审批' },
        { href: 'admin.html?tab=stats', text: '数据统计' },
        { href: 'account.html', text: '账户中心' }
    ]
};


/** 角色 -> 订单列表页（订单详情“返回列表”的无浏览历史兜底，与 HOME_PAGES 同族的角色跳转口径） */
export const ORDER_LIST_PAGES = {
    CUSTOMER: 'my-orders.html',
    SHOP: 'merchant-orders.html',
    RIDER: 'rider-delivery.html',
    ADMIN: 'admin.html?tab=orders'
};

/* ---------- 主题管理（light/dark，localStorage 记忆） ---------- */

const THEME_KEY = 'DW_THEME';

/** 应用 localStorage 记忆的主题到 <html data-theme>；模块加载即调用 */
export function applyTheme() {
    const t = localStorage.getItem(THEME_KEY) || 'light';
    document.documentElement.dataset.theme = t;
    return t;
}

/** 记忆并应用主题 */
export function setTheme(t) {
    localStorage.setItem(THEME_KEY, t);
    document.documentElement.dataset.theme = t;
}

/** 当前主题 */
export function currentTheme() {
    return document.documentElement.dataset.theme || 'light';
}

/** 切换亮<->暗，返回切换后是否为暗色 */
export function toggleTheme() {
    const next = currentTheme() === 'dark' ? 'light' : 'dark';
    setTheme(next);
    return next === 'dark';
}

applyTheme();

/** Vue mixin：提供 dark 状态与 toggleTheme 方法。
 *  而 merchant/admin 页的 ECharts 依赖 watch dark 重绘——共享状态保证壳内切换能驱动页面监听。 */
const themeState = reactive({ dark: currentTheme() === 'dark' });

/** Vue mixin：dark 为共享状态的计算属性映射，toggleTheme 写共享状态，所有实例同步 */
export const themeMixin = {
    computed: {
        dark: {
            get() {
                return themeState.dark;
            },
            set(value) {
                themeState.dark = value;
            }
        }
    },
    methods: {
        toggleTheme() {
            this.dark = toggleTheme();
        }
    }
};

/** 按角色跳转到对应主页面；角色无效时不跳转 */
export function goHome(role) {
    const page = HOME_PAGES[role];
    if (page) {
        location.href = CTX + '/pages/' + page;
    }
}

/* ---------- 用户缓存（首屏先渲染，后台校验） ---------- */

const USER_CACHE_KEY = 'DW_USER';

/** 写入 sessionStorage 用户缓存（仅首屏展示用，权限仍以服务端 Session 为准） */
export function setCachedUser(user) {
    try {
        sessionStorage.setItem(USER_CACHE_KEY, JSON.stringify(user));
    } catch (e) {
        // 隐私模式等不可用时忽略
    }
}

/** 读取 sessionStorage 用户缓存；无缓存或解析失败返回 null */
export function getCachedUser() {
    try {
        const raw = sessionStorage.getItem(USER_CACHE_KEY);
        return raw ? JSON.parse(raw) : null;
    } catch (e) {
        return null;
    }
}

/** 清除用户缓存（登出/校验失败时） */
export function clearCachedUser() {
    try {
        sessionStorage.removeItem(USER_CACHE_KEY);
    } catch (e) {
        // 忽略
    }
}

/** 后台异步校验会话：成功刷新缓存，失败清缓存跳登录 */
function verifySessionAsync(role) {
    get('/api/auth/current').then(result => {
        const user = (result && result.data) || null;
        if (!user) {
            clearCachedUser();
            location.href = CTX + '/pages/login.html';
            return;
        }
        setCachedUser(user);
        if (role && user.role !== role) {
            goHome(user.role);
        }
    }).catch(() => {
        clearCachedUser();
        location.href = CTX + '/pages/login.html';
    });
}

/* ---------- 页面守卫 ---------- */

/**
 * 页面守卫：校验登录与角色，返回用户对象或 null。
 * 未登录跳登录页；已登录但角色不符时跳往其角色主页；校验通过返回用户供页面取用。
 * @param {string} [role] 期望角色；传空表示只要求已登录
 */
export async function requireRole(role) {
    const cached = getCachedUser();
    if (cached) {
        // 有缓存：同步返回供首屏立即渲染，后台异步校验会话
        if (role && cached.role !== role) {
            goHome(cached.role);
            return null;
        }
        verifySessionAsync(role);
        applyUserConfig(cached);
        return cached;
    }
    // 无缓存：首访/新标签需等待接口
    let user = null;
    try {
        const result = await get('/api/auth/current');
        user = (result && result.data) || null;
    } catch (e) {
        clearCachedUser();
        location.href = CTX + '/pages/login.html';
        return null;
    }
    if (!user) {
        clearCachedUser();
        location.href = CTX + '/pages/login.html';
        return null;
    }
    setCachedUser(user);
    if (role && user.role !== role) {
        goHome(user.role);
        return null;
    }
    applyUserConfig(user);
    return user;
}

/* ---------- 状态字典（设计书表2.16，中文名对齐表1.7） ---------- */

/** 角色 */
export const ROLE_TEXT = {
    CUSTOMER: '顾客',
    SHOP: '商家',
    RIDER: '骑手',
    ADMIN: '管理员'
};

/** 账户状态 status（user） */
export const USER_STATUS_TEXT = {
    NORMAL: '正常',
    DISABLED: '禁用'
};

/** 店铺审核状态 audit_status */
export const AUDIT_STATUS_TEXT = {
    PENDING: '待审核',
    APPROVED: '审核通过',
    REJECTED: '审核拒绝'
};

/** 店铺营业状态 business_status */
export const BUSINESS_STATUS_TEXT = {
    OPEN: '营业中',
    CLOSED: '休息中'
};

/** 菜品上下架状态 status（dish） */
export const DISH_STATUS_TEXT = {
    ON_SALE: '上架',
    OFF_SALE: '下架'
};

/** 支付状态 pay_status */
export const PAY_STATUS_TEXT = {
    UNPAID: '未支付',
    PAID: '已支付',
    REFUNDED: '已退款'
};

/** 订单状态 order_status（表1.7） */
export const ORDER_STATUS_TEXT = {
    UNPAID: '待支付',
    PENDING_ACCEPT: '待商家处理',
    PREPARING: '备餐中',
    PENDING_DELIVERY: '待配送',
    DELIVERING: '配送中',
    DELIVERED: '已送达',
    COMPLETED: '已完成',
    CANCELLED: '已取消',
    REJECTED: '已拒单'
};

/** 配送状态 status（delivery）；取餐为动作（PICKED）不作驻留状态，取餐后自动转配送中 */
export const DELIVERY_STATUS_TEXT = {
    PENDING: '待抢单',
    ACCEPTED: '已接单',
    DELIVERING: '配送中',
    DELIVERED: '已送达'
};

/** 退款状态 status（refund） */
export const REFUND_STATUS_TEXT = {
    PENDING: '待商家处理',
    SHOP_AGREED: '商家同意',
    SHOP_REJECTED: '商家拒绝',
    ADMIN_AGREED: '平台裁定同意',
    ADMIN_REJECTED: '平台裁定驳回'
};

/**
 * 从字典取中文名；字典缺失时原样返回枚举值，空值返回空串。
 * 例：label(ORDER_STATUS_TEXT, 'PREPARING') -> '备餐中'
 */
export function label(dict, value) {
    if (value === null || value === undefined) {
        return '';
    }
    return dict[value] || value;
}

/* ---------- 未支付倒计时（A1） ---------- */

/**
 * 未支付订单超时阈值的默认值（分钟）：登录后由 /api/auth/current 下发的
 * orderTimeoutMinutes 覆盖（applyUserConfig），后端 Constants 为唯一事实源。
 * 真正的取消以后端惰性检查/定时器为准，前端仅用于展示倒计时。
 */
export const DEFAULT_ORDER_TIMEOUT_MINUTES = 15;

/** 当前生效的超时阈值（登录用户就绪后由 applyUserConfig 更新） */
let orderTimeoutMinutes = DEFAULT_ORDER_TIMEOUT_MINUTES;

/** 用 /api/auth/current 返回的用户信息更新前端展示阈值（requireRole 内自动调用） */
export function applyUserConfig(user) {
    if (user && typeof user.orderTimeoutMinutes === 'number' && user.orderTimeoutMinutes > 0) {
        orderTimeoutMinutes = user.orderTimeoutMinutes;
    }
    return orderTimeoutMinutes;
}

/**
 * 解析后端 LocalDateTime 文本（yyyy-MM-dd HH:mm:ss）为 Date；无法解析返回 null。
 * 统一把 '-' 换成 '/' 再交给 Date，规避部分浏览器对非 ISO 格式的解析差异。
 */
export function parseDateTime(text) {
    if (!text) {
        return null;
    }
    const normalized = String(text).trim().replace('T', ' ').split('.')[0].replace(/-/g, '/');
    const date = new Date(normalized);
    return isNaN(date.getTime()) ? null : date;
}

/** 快捷日期区间：今日/近3天/近7天/近30天 按钮共用。
 * 返回 { start, end }（yyyy-MM-dd，含头含尾，end 恒为今天）；days 非法返回 null */
export function quickRange(days) {
    const n = Number(days);
    if (!isFinite(n) || n <= 0) {
        return null;
    }
    const fmt = (d) => d.getFullYear() + '-' + String(d.getMonth() + 1).padStart(2, '0')
        + '-' + String(d.getDate()).padStart(2, '0');
    const end = new Date();
    const start = new Date();
    start.setDate(end.getDate() - (n - 1));
    return { start: fmt(start), end: fmt(end) };
}

/** 支付截止时刻（毫秒时间戳）= 下单时间 + 超时阈值；下单时间无效返回 null */
export function payDeadline(createdAt) {
    const created = parseDateTime(createdAt);
    return created === null ? null : created.getTime() + orderTimeoutMinutes * 60 * 1000;
}

/** 剩余毫秒格式化为 mm:ss；已归零或负值统一显示 00:00 */
export function formatCountdown(remainMillis) {
    const totalSeconds = Math.max(0, Math.floor(remainMillis / 1000));
    const minutes = Math.floor(totalSeconds / 60);
    const seconds = totalSeconds % 60;
    return String(minutes).padStart(2, '0') + ':' + String(seconds).padStart(2, '0');
}

/* ---------- 取餐码（A3） ---------- */

/**
 * 取餐码：订单号后四位，供商家出餐与骑手到店取餐口头核对。
 * 订单号形如 yyyyMMddHHmmssSSS + 3 位随机数，后四位在同期订单中足够区分。
 */
export function pickupCode(orderNo) {
    if (!orderNo) {
        return '—';
    }
    const no = String(orderNo);
    return no.length <= 4 ? no : no.slice(-4);
}

/* ---------- 菜品库存状态（A4） ---------- */

/** 低库存预警阈值：库存小于该值时商家端菜品行高亮（纯前端提示口径，后端不做限制） */
export const LOW_STOCK_THRESHOLD = 10;

/** 库存状态：SOLD_OUT 售罄（顾客端禁加购）/ LOW 告急（商家端预警）/ OK 正常 */
export function stockState(stock) {
    const value = Number(stock);
    if (!isFinite(value) || value <= 0) {
        return 'SOLD_OUT';
    }
    return value < LOW_STOCK_THRESHOLD ? 'LOW' : 'OK';
}

/* ---------- 待办角标（A6 轻量版） ---------- */

/**
 * 角色 -> 待办口径：全部复用已有列表接口的分页 total（page=1&size=1 只为拿计数），
 * 不新增汇总接口、不用长连接，进页拉一次，切页自然刷新。
 * nav 为角标挂载的导航 href（与 NAV_PAGES / 各页静态导航保持一致）；
 * 角标只显示首项计数，其余计数并入 title 悬停提示。
 */
const TODO_SPECS = {
    SHOP: {
        nav: 'merchant-orders.html',
        items: [
            { label: '待接单', path: '/api/order/list', query: { status: 'PENDING_ACCEPT' } },
            { label: '备餐中', path: '/api/order/list', query: { status: 'PREPARING' } }
        ]
    },
    RIDER: {
        nav: 'rider-pool.html',
        items: [
            { label: '可抢订单', path: '/api/delivery/pool', query: {} }
        ]
    },
    ADMIN: {
        nav: 'admin.html?tab=audit',
        items: [
            { label: '待审核店铺', path: '/api/admin/shops', query: { auditStatus: 'PENDING' } }
        ]
    }
};

/**
 * 拉取当前角色的待办角标：{ nav, badge, title }；无对应口径返回 null。
 * 角标仅为提示，单项请求失败按 0 处理并吞掉异常，不阻塞页面主流程。
 */
export async function fetchTodoBadge(role) {
    const spec = TODO_SPECS[role];
    if (!spec) {
        return null;
    }
    const counts = [];
    for (const item of spec.items) {
        const params = new URLSearchParams();
        params.set('page', '1');
        params.set('size', '1');
        Object.keys(item.query).forEach(key => params.set(key, item.query[key]));
        let total = 0;
        try {
            const result = await get(item.path + '?' + params.toString());
            total = Number((result && result.data && result.data.total) || 0);
        } catch (e) {
            total = 0;
        }
        counts.push({ label: item.label, total });
    }
    return {
        nav: spec.nav,
        badge: counts[0].total > 0 ? String(counts[0].total) : '',
        title: counts.map(c => c.label + ' ' + c.total).join(' · ')
    };
}

/**
 * Vue mixin：待办角标。页面在 requireRole 拿到用户后调 loadTodo(role)，
 * 模板用 todoBadge(href) / todoTitle(href) 按导航入口取值，无需各页重复写拉取逻辑。
 */
export const todoMixin = {
    data() {
        return { todo: null };
    },
    methods: {
        async loadTodo(role) {
            this.todo = await fetchTodoBadge(role);
        },
        todoBadge(href) {
            return this.todo && this.todo.nav === href ? this.todo.badge : '';
        },
        todoTitle(href) {
            return this.todo && this.todo.nav === href ? this.todo.title : '';
        }
    }
};

/* ---------- 页面壳组件（依赖 Vue 运行时编译；纯函数断言区到此为止，dw-syntax-check 据此截取） ---------- */

/**
 * 页面壳组件（导航单源化）：登录态页的顶栏/侧边栏统一由本组件渲染，
 * 导航项取自 NAV_PAGES[role]，杜绝"NAV_PAGES 改了页面没改"的漂移缺陷。
 * - <app-topbar>：顶栏。showBrand=true 时渲染品牌链接（C端/骑手），否则渲染纯标题（管理端顶栏）。
 * - <app-sidenav>：管理端侧边栏（brand + nav）。
 * 两组件均内含 themeMixin（主题按钮）与 todoMixin（待办角标，user 就绪后自动拉取一次）。
 * 页面用法：import { installShell } 后 createApp 之前 installShell(app)，模板中：
 *   <app-topbar :user="user" active-href="shops.html" :show-brand="true" brand="外卖配送平台" logo="送" brand-href="shops.html"/>
 *   <app-sidenav :user="user" active-href="merchant.html" brand="商家中心" logo="店"/>
 * admin.html 侧边栏因需 SPA 页签切换（switchTab 阻止默认跳转）保持自有实现，但页签清单仍取自 NAV_PAGES.ADMIN。
 */
const AppTopbar = {
    name: 'AppTopbar',
    mixins: [themeMixin, todoMixin],
    props: {
        user: { type: Object, default: null },
        activeHref: { type: String, default: '' },
        showBrand: { type: Boolean, default: false },
        showNav: { type: Boolean, default: true },
        brand: { type: String, default: '' },
        logo: { type: String, default: '' },
        brandHref: { type: String, default: '' }
    },
    computed: {
        links() {
            return (this.user && NAV_PAGES[this.user.role]) || [];
        }
    },
    watch: {
        user: {
            immediate: true,
            handler(u) {
                if (u && u.role && this.showNav && this.links.length) {
                    this.loadTodo(u.role);
                }
            }
        }
    },
    methods: {
        roleName(role) {
            return ROLE_TEXT[role] || role;
        },
        doLogout() {
            logout();
        }
    },
    template:
        '<header class="topbar">' +
        '  <div class="topbar-inner">' +
        '    <a v-if="showBrand" class="topbar-brand" :href="brandHref">' +
        '      <span class="topbar-logo">{{ logo }}</span>' +
        '      <span class="topbar-title">{{ brand }}</span>' +
        '    </a>' +
        '    <span v-else-if="brand" class="topbar-title">{{ brand }}</span>' +
        '    <nav v-if="showNav" class="topbar-nav">' +
        '      <a v-for="n in links" :key="n.href" :href="n.href" :class="{ active: n.href === activeHref }"' +
        '         :title="todoTitle(n.href)">{{ n.text }}<span' +
        '          class="nav-badge" v-if="todoBadge(n.href)">{{ todoBadge(n.href) }}</span></a>' +
        '    </nav>' +
        '    <div class="topbar-user" v-if="user">' +
        '      <button class="theme-toggle" type="button"' +
        '              :title="dark ? \'切换亮色\' : \'切换暗色\'"' +
        '              @click="toggleTheme">{{ dark ? \'☀️\' : \'🌙\' }}</button>' +
        '      <span class="badge" v-if="user.role">{{ roleName(user.role) }}</span>' +
        '      <span class="topbar-name">{{ user.username }}</span>' +
        '      <button class="btn" type="button" @click="doLogout">退出</button>' +
        '    </div>' +
        '  </div>' +
        '</header>'
};

const AppSidenav = {
    name: 'AppSidenav',
    mixins: [todoMixin],
    props: {
        user: { type: Object, default: null },
        activeHref: { type: String, default: '' },
        brand: { type: String, default: '商家中心' },
        logo: { type: String, default: '店' }
    },
    computed: {
        links() {
            return (this.user && NAV_PAGES[this.user.role]) || [];
        }
    },
    watch: {
        user: {
            immediate: true,
            handler(u) {
                if (u && u.role && this.links.length) {
                    this.loadTodo(u.role);
                }
            }
        }
    },
    template:
        '<aside class="admin-side">' +
        '  <div class="admin-side-brand">' +
        '    <span class="topbar-logo">{{ logo }}</span>' +
        '    <span>{{ brand }}</span>' +
        '  </div>' +
        '  <nav class="admin-side-nav">' +
        '    <a v-for="n in links" :key="n.href" :href="n.href" :class="{ active: n.href === activeHref }"' +
        '       :title="todoTitle(n.href)">{{ n.text }}<span' +
        '        class="nav-badge" v-if="todoBadge(n.href)">{{ todoBadge(n.href) }}</span></a>' +
        '  </nav>' +
        '</aside>'
};

/** 向 Vue 应用注册壳组件（页面在 createApp 之后、mount 之前调用一次） */
export function installShell(app) {
    app.component('app-topbar', AppTopbar);
    app.component('app-sidenav', AppSidenav);
}
