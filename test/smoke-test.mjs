/**
 * 冒烟测试：静态资源可访问 + 登录 + 顾客主链路 + 商家处理 + 骑手履约 + 退款闭环 + 购物车下架改数量校验。
 * 用法：node test/smoke-test.mjs [baseUrl] [--with-pay]
 * 说明：订单 A 默认走 create→cancel（取消会恢复库存，可反复执行），--with-pay 时改为 create→pay；
 *       订单 B 默认走拒单（退款记录+库存恢复），--with-pay 时走接单+备餐完成；
 *       订单 C 两条模式均走：支付→接单→备餐→骑手抢单→取餐/配送中/送达→退款申请→商家同意→支付状态已退款。
 * 前置：Tomcat 已启动、init.sql 已执行（种子账号 customer1/shop1/rider1，密码均 123456）。
 */
import { BASE, api, request, login, check, summary } from './lib.mjs';

const withPay = process.argv.includes('--with-pay');
console.log('冒烟测试 BASE=' + BASE + (withPay ? '（含支付链路）' : '（默认取消链路）'));

/* ---------- 1. 静态资源与页面 ---------- */
console.log('1. 静态资源与页面');
// P1-5 起 /pages/* 除登录页外要求登录态：页面可访问性用已登录会话验证，未登录 302 在第 2 节断言
const pageProbe = await login('customer1', '123456');
const statics = [
    ['/pages/login.html', 'id="app"', null],
    ['/pages/shops.html', 'topbar', pageProbe.jar],
    ['/pages/shop-detail.html', 'topbar', pageProbe.jar],
    ['/pages/order-confirm.html', '订单确认', pageProbe.jar],
    ['/pages/address.html', 'topbar', pageProbe.jar],
    ['/common/style.css', '--color-primary', null],
    ['/common/common.js', 'export', null],
    ['/common/img/login-bg.svg', '<svg', null],
    // error.html 移入 pages/：直访需登录态（未登录 302 属预期，由探针覆盖）
    ['/pages/error.html', 'error-card', pageProbe.jar]
];
for (const [path, marker, jar] of statics) {
    const response = await request(path, { jar });
    const text = await response.text();
    check('静态资源 ' + path, response.status === 200 && text.includes(marker),
        'status=' + response.status);
}
const vueResponse = await request('/libs/vue.esm-browser.prod.js');
check('静态资源 /libs/vue.esm-browser.prod.js', vueResponse.status === 200,
    'status=' + vueResponse.status);

/* ---------- 2. 未登录拦截 ---------- */
console.log('2. 未登录拦截');
const anonCurrent = await api('/api/auth/current');
check('未登录 /api/auth/current 业务码非 200', anonCurrent.status === 200 && anonCurrent.json !== null
    && anonCurrent.json.code !== 200, 'status=' + anonCurrent.status);
const anonList = await api('/api/shop/list');
check('未登录 /api/shop/list 返回 401', anonList.status === 401, 'status=' + anonList.status);
const anonPage = await request('/pages/shops.html');
check('未登录访问 /pages/shops.html 302 回登录页（P1-5）', anonPage.status === 302
    && (anonPage.headers.get('location') || '').includes('/pages/login.html'),
    'status=' + anonPage.status + ' location=' + anonPage.headers.get('location'));

/* ---------- 3. 登录 ---------- */
console.log('3. 登录');
const { jar, result: loginResult } = await login('customer1', '123456');
check('customer1 登录成功', loginResult.status === 200 && loginResult.json !== null
    && loginResult.json.code === 200 && loginResult.json.data.role === 'CUSTOMER',
JSON.stringify(loginResult.json));

/* ---------- 4. 主数据读取 ---------- */
console.log('4. 主数据读取');
const shopList = await api('/api/shop/list?page=1&size=5', { jar });
const shops = (shopList.json && shopList.json.data && shopList.json.data.records) || [];
check('店铺列表返回数据', shopList.json !== null && shopList.json.code === 200 && shops.length > 0,
    JSON.stringify(shopList.json));

const dishList = await api('/api/dish/list?shopId=' + shops[0].id, { jar });
const dishes = (dishList.json && dishList.json.data) || [];
check('店铺在售菜品返回数据', dishList.json !== null && dishList.json.code === 200 && dishes.length > 0,
    JSON.stringify(dishList.json));

const addrList = await api('/api/address/list', { jar });
const addresses = (addrList.json && addrList.json.data) || [];
check('收货地址列表返回数据', addrList.json !== null && addrList.json.code === 200 && addresses.length > 0,
    JSON.stringify(addrList.json));

/* ---------- 5. 加购 → 下单 → 取消/支付 ---------- */
console.log('5. 加购 → 下单 → ' + (withPay ? '支付' : '取消'));
await api('/api/cart/clear', { method: 'POST', body: {}, jar });
const add = await api('/api/cart/add', { method: 'POST', body: { dishId: dishes[0].id, quantity: 1 }, jar });
check('加购成功', add.json !== null && add.json.code === 200, JSON.stringify(add.json));

const cartBefore = await api('/api/cart/list', { jar });
check('购物车含 1 件商品', cartBefore.json !== null && cartBefore.json.code === 200
    && cartBefore.json.data.items.length === 1, JSON.stringify(cartBefore.json && cartBefore.json.data));

const token = await api('/api/order/token', { jar });
check('获取订单幂等令牌', token.json !== null && token.json.code === 200
    && token.json.data !== null && !!token.json.data.token, JSON.stringify(token.json));

const created = await api('/api/order/create', {
    method: 'POST',
    body: { addressId: addresses[0].id, remark: '冒烟测试', token: token.json.data.token },
    jar
});
check('创建订单成功', created.json !== null && created.json.code === 200
    && created.json.data !== null && !!created.json.data.orderId, JSON.stringify(created.json));

const replay = await api('/api/order/create', {
    method: 'POST',
    body: { addressId: addresses[0].id, remark: '冒烟测试', token: token.json.data.token },
    jar
});
check('同令牌重复提交被拒（幂等）', replay.json !== null && replay.json.code !== 200,
    JSON.stringify(replay.json));

if (created.json !== null && created.json.code === 200) {
    const orderId = created.json.data.orderId;

    /* IDOR 越权：另一顾客访问他人订单详情应被拒（归属校验 BizException → HTTP 200 + 业务 fail） */
    const other = await login('customer2', '123456');
    const idor = await api('/api/order/detail?orderId=' + orderId, { jar: other.jar });
    check('顾客访问他人订单详情被拒（归属校验）', idor.status === 200 && idor.json !== null
        && idor.json.code !== 200, 'status=' + idor.status + ' ' + JSON.stringify(idor.json));

    if (withPay) {
        const pay = await api('/api/order/pay', { method: 'POST', body: { orderId }, jar });
        check('模拟支付成功', pay.json !== null && pay.json.code === 200, JSON.stringify(pay.json));
        const payAgain = await api('/api/order/pay', { method: 'POST', body: { orderId }, jar });
        check('重复支付被拒（状态机）', payAgain.json !== null && payAgain.json.code !== 200,
            JSON.stringify(payAgain.json));
    } else {
        const cancel = await api('/api/order/cancel', { method: 'POST', body: { orderId }, jar });
        check('取消未支付订单成功（库存恢复）', cancel.json !== null && cancel.json.code === 200,
            JSON.stringify(cancel.json));
        const cancelAgain = await api('/api/order/cancel', { method: 'POST', body: { orderId }, jar });
        check('重复取消被拒（状态机）', cancelAgain.json !== null && cancelAgain.json.code !== 200,
            JSON.stringify(cancelAgain.json));
    }
}

/* ---------- 6. 商家侧：接单/拒单/备餐完成 ---------- */
console.log('6. 商家侧操作');
const shopLogin = await login('shop1', '123456');
check('shop1 登录成功', shopLogin.result.json !== null && shopLogin.result.json.code === 200,
    JSON.stringify(shopLogin.result.json));
const shopJar = shopLogin.jar;

const custAccept = await api('/api/order/accept', { method: 'POST', body: { orderId: 1 }, jar });
check('顾客调用接单返回 403', custAccept.status === 403, 'status=' + custAccept.status);

const shopOrderList = await api('/api/order/list?page=1&size=5', { jar: shopJar });
check('商家订单列表返回分页结构', shopOrderList.json !== null && shopOrderList.json.code === 200
    && shopOrderList.json.data !== null && Array.isArray(shopOrderList.json.data.records),
JSON.stringify(shopOrderList.json && shopOrderList.json.data));

// 订单 B：重新加购（订单 A 下单成功后购物车已被服务端清空）→ 创建 → 支付 → 接单+备餐完成（--with-pay）或 拒单（默认，库存恢复）
const addB = await api('/api/cart/add', { method: 'POST', body: { dishId: dishes[0].id, quantity: 1 }, jar });
check('订单 B 加购成功', addB.json !== null && addB.json.code === 200, JSON.stringify(addB.json));
const tokenB = await api('/api/order/token', { jar });
const createdB = await api('/api/order/create', {
    method: 'POST',
    body: { addressId: addresses[0].id, remark: '冒烟测试B', token: tokenB.json.data.token },
    jar
});
check('订单 B 创建成功', createdB.json !== null && createdB.json.code === 200, JSON.stringify(createdB.json));
if (createdB.json !== null && createdB.json.code === 200) {
    const orderIdB = createdB.json.data.orderId;
    const payB = await api('/api/order/pay', { method: 'POST', body: { orderId: orderIdB }, jar });
    check('订单 B 支付成功', payB.json !== null && payB.json.code === 200, JSON.stringify(payB.json));
    if (withPay) {
        const acceptB = await api('/api/order/accept', { method: 'POST', body: { orderId: orderIdB }, jar: shopJar });
        check('商家接单订单 B', acceptB.json !== null && acceptB.json.code === 200, JSON.stringify(acceptB.json));
        const readyB = await api('/api/order/ready', { method: 'POST', body: { orderId: orderIdB }, jar: shopJar });
        check('订单 B 备餐完成生成配送记录', readyB.json !== null && readyB.json.code === 200, JSON.stringify(readyB.json));
    } else {
        const rejectNoReason = await api('/api/order/reject', { method: 'POST', body: { orderId: orderIdB }, jar: shopJar });
        check('拒单不填原因被拒', rejectNoReason.json !== null && rejectNoReason.json.code !== 200,
            JSON.stringify(rejectNoReason.json));
        const rejectB = await api('/api/order/reject', {
            method: 'POST', body: { orderId: orderIdB, reason: '冒烟测试拒单' }, jar: shopJar
        });
        check('订单 B 拒单成功（生成退款记录、库存恢复）', rejectB.json !== null && rejectB.json.code === 200,
            JSON.stringify(rejectB.json));
    }
    const detailB = await api('/api/order/detail?orderId=' + orderIdB, { jar: shopJar });
    check('商家查看订单 B 详情（含明细快照）', detailB.json !== null && detailB.json.code === 200
        && detailB.json.data !== null && Array.isArray(detailB.json.data.items)
        && detailB.json.data.items.length === 1,
    JSON.stringify(detailB.json && detailB.json.data && detailB.json.data.items));
}

/* ---------- 7. 骑手履约 + 退款闭环（订单 C） ---------- */
console.log('7. 骑手履约与退款闭环');
const riderLogin = await login('rider1', '123456');
check('rider1 登录成功且角色 RIDER', riderLogin.result.json !== null && riderLogin.result.json.code === 200
    && riderLogin.result.json.data.role === 'RIDER', JSON.stringify(riderLogin.result.json));
const riderJar = riderLogin.jar;

const custPool = await api('/api/delivery/pool?page=1&size=5', { jar });
check('顾客调用配送池返回 403', custPool.status === 403, 'status=' + custPool.status);

const addC = await api('/api/cart/add', { method: 'POST', body: { dishId: dishes[0].id, quantity: 1 }, jar });
check('订单 C 加购成功', addC.json !== null && addC.json.code === 200, JSON.stringify(addC.json));
const tokenC = await api('/api/order/token', { jar });
const createdC = await api('/api/order/create', {
    method: 'POST',
    body: { addressId: addresses[0].id, remark: '冒烟测试C', token: tokenC.json.data.token },
    jar
});
check('订单 C 创建成功', createdC.json !== null && createdC.json.code === 200, JSON.stringify(createdC.json));
if (createdC.json !== null && createdC.json.code === 200) {
    const orderIdC = createdC.json.data.orderId;
    const payC = await api('/api/order/pay', { method: 'POST', body: { orderId: orderIdC }, jar });
    check('订单 C 支付成功', payC.json !== null && payC.json.code === 200, JSON.stringify(payC.json));
    const acceptC = await api('/api/order/accept', { method: 'POST', body: { orderId: orderIdC }, jar: shopJar });
    check('商家接单订单 C', acceptC.json !== null && acceptC.json.code === 200, JSON.stringify(acceptC.json));
    const readyC = await api('/api/order/ready', { method: 'POST', body: { orderId: orderIdC }, jar: shopJar });
    check('订单 C 备餐完成进入配送池', readyC.json !== null && readyC.json.code === 200, JSON.stringify(readyC.json));

    const pool = await api('/api/delivery/pool?page=1&size=20', { jar: riderJar });
    const poolRecords = (pool.json && pool.json.data && pool.json.data.records) || [];
    const poolItem = poolRecords.find(r => r.orderId === orderIdC);
    check('配送池含订单 C（含店铺/地址聚合）', poolItem !== undefined
        && !!poolItem.shopName && !!poolItem.addressSnapshot,
    JSON.stringify(poolRecords.map(r => r.orderId)));

    const skipNode = await api('/api/delivery/node', {
        method: 'POST', body: { deliveryId: poolItem.deliveryId, status: 'DELIVERED' }, jar: riderJar
    });
    check('跳步确认送达被拒（节点顺序校验）', skipNode.json !== null && skipNode.json.code !== 200,
        JSON.stringify(skipNode.json));

    const grab = await api('/api/delivery/grab', { method: 'POST', body: { deliveryId: poolItem.deliveryId }, jar: riderJar });
    check('骑手抢单成功', grab.json !== null && grab.json.code === 200, JSON.stringify(grab.json));
    const grabAgain = await api('/api/delivery/grab', { method: 'POST', body: { deliveryId: poolItem.deliveryId }, jar: riderJar });
    check('重复抢单被拒（条件更新并发控制）', grabAgain.json !== null && grabAgain.json.code !== 200,
        JSON.stringify(grabAgain.json));

    for (const [status, name] of [['PICKED', '取餐'], ['DELIVERED', '送达']]) {
        const nodeResult = await api('/api/delivery/node', {
            method: 'POST', body: { deliveryId: poolItem.deliveryId, status }, jar: riderJar
        });
        check('履约节点' + name + '成功', nodeResult.json !== null && nodeResult.json.code === 200,
            JSON.stringify(nodeResult.json));
    }
    const detailC = await api('/api/order/detail?orderId=' + orderIdC, { jar });
    check('订单 C 经履约节点推进至已送达', detailC.json !== null && detailC.json.code === 200
        && detailC.json.data.order.orderStatus === 'DELIVERED',
    JSON.stringify(detailC.json && detailC.json.data && detailC.json.data.order));

    const remarkR = await api('/api/delivery/remark', {
        method: 'POST', body: { deliveryId: poolItem.deliveryId, remark: '冒烟测试配送说明' }, jar: riderJar
    });
    check('配送说明反馈成功', remarkR.json !== null && remarkR.json.code === 200, JSON.stringify(remarkR.json));

    /* 退款闭环：申请（原因必填/查重）→ 商家待处理列表 → 同意 → 支付状态已退款 */
    const applyNoReason = await api('/api/refund/apply', { method: 'POST', body: { orderId: orderIdC }, jar });
    check('退款申请不填原因被拒', applyNoReason.json !== null && applyNoReason.json.code !== 200,
        JSON.stringify(applyNoReason.json));
    const applyC = await api('/api/refund/apply', {
        method: 'POST', body: { orderId: orderIdC, reason: '冒烟测试退款' }, jar
    });
    check('退款申请成功（已送达订单）', applyC.json !== null && applyC.json.code === 200, JSON.stringify(applyC.json));
    const applyDup = await api('/api/refund/apply', {
        method: 'POST', body: { orderId: orderIdC, reason: '重复申请' }, jar
    });
    check('重复退款申请被拒', applyDup.json !== null && applyDup.json.code !== 200, JSON.stringify(applyDup.json));

    const pending = await api('/api/refund/pending?page=1&size=10', { jar: shopJar });
    const pendingRecords = (pending.json && pending.json.data && pending.json.data.records) || [];
    const refundItem = pendingRecords.find(r => r.orderId === orderIdC);
    check('商家待处理列表含订单 C', refundItem !== undefined && !!refundItem.orderNo,
        JSON.stringify(pendingRecords.map(r => r.orderNo)));

    const handle = await api('/api/refund/handle', {
        method: 'POST', body: { refundId: refundItem.refundId, agree: true, remark: '冒烟测试同意' }, jar: shopJar
    });
    check('商家同意退款', handle.json !== null && handle.json.code === 200, JSON.stringify(handle.json));
    const handleDup = await api('/api/refund/handle', {
        method: 'POST', body: { refundId: refundItem.refundId, agree: true, remark: '重复处理' }, jar: shopJar
    });
    check('重复处理退款被拒', handleDup.json !== null && handleDup.json.code !== 200, JSON.stringify(handleDup.json));
    const detailRefunded = await api('/api/order/detail?orderId=' + orderIdC, { jar });
    check('同意退款后订单 C 支付状态置已退款', detailRefunded.json !== null && detailRefunded.json.code === 200
        && detailRefunded.json.data.order.payStatus === 'REFUNDED',
    JSON.stringify(detailRefunded.json && detailRefunded.json.data && detailRefunded.json.data.order));
}

/* ---------- 8. 确认收货与评价（订单 D） ---------- */
console.log('8. 确认收货与评价');
const addD = await api('/api/cart/add', { method: 'POST', body: { dishId: dishes[0].id, quantity: 1 }, jar });
check('订单 D 加购成功', addD.json !== null && addD.json.code === 200, JSON.stringify(addD.json));
const tokenD = await api('/api/order/token', { jar });
const createdD = await api('/api/order/create', {
    method: 'POST',
    body: { addressId: addresses[0].id, remark: '冒烟测试D', token: tokenD.json.data.token },
    jar
});
check('订单 D 创建成功', createdD.json !== null && createdD.json.code === 200, JSON.stringify(createdD.json));
if (createdD.json !== null && createdD.json.code === 200) {
    const orderIdD = createdD.json.data.orderId;
    const payD = await api('/api/order/pay', { method: 'POST', body: { orderId: orderIdD }, jar });
    check('订单 D 支付成功', payD.json !== null && payD.json.code === 200, JSON.stringify(payD.json));
    await api('/api/order/accept', { method: 'POST', body: { orderId: orderIdD }, jar: shopJar });
    const readyD = await api('/api/order/ready', { method: 'POST', body: { orderId: orderIdD }, jar: shopJar });
    check('订单 D 备餐完成进入配送池', readyD.json !== null && readyD.json.code === 200, JSON.stringify(readyD.json));

    const poolD = await api('/api/delivery/pool?page=1&size=20', { jar: riderJar });
    const itemD = ((poolD.json && poolD.json.data && poolD.json.data.records) || []).find(r => r.orderId === orderIdD);
    check('配送池含订单 D', itemD !== undefined, JSON.stringify(poolD.json && poolD.json.data));
    await api('/api/delivery/grab', { method: 'POST', body: { deliveryId: itemD.deliveryId }, jar: riderJar });
    for (const status of ['PICKED', 'DELIVERED']) {
        await api('/api/delivery/node', { method: 'POST', body: { deliveryId: itemD.deliveryId, status }, jar: riderJar });
    }

    const receiveD = await api('/api/order/receive', { method: 'POST', body: { orderId: orderIdD }, jar });
    check('确认收货成功（DELIVERED→COMPLETED）', receiveD.json !== null && receiveD.json.code === 200,
        JSON.stringify(receiveD.json));
    const receiveAgain = await api('/api/order/receive', { method: 'POST', body: { orderId: orderIdD }, jar });
    check('重复确认收货被拒（状态机）', receiveAgain.json !== null && receiveAgain.json.code !== 200,
        JSON.stringify(receiveAgain.json));

    const revBad = await api('/api/review/submit', { method: 'POST', body: { orderId: orderIdD, rating: 6 }, jar });
    check('评分越界被拒', revBad.json !== null && revBad.json.code !== 200, JSON.stringify(revBad.json));
    const rev = await api('/api/review/submit', {
        method: 'POST', body: { orderId: orderIdD, rating: 5, content: '冒烟测试好评' }, jar
    });
    check('评价提交成功', rev.json !== null && rev.json.code === 200, JSON.stringify(rev.json));
    const revDup = await api('/api/review/submit', { method: 'POST', body: { orderId: orderIdD, rating: 4 }, jar });
    check('重复评价被拒（一单一次）', revDup.json !== null && revDup.json.code !== 200, JSON.stringify(revDup.json));
    const exists = await api('/api/review/exists?orderId=' + orderIdD, { jar });
    check('评价存在性查询 reviewed=true', exists.json !== null && exists.json.code === 200
        && exists.json.data.reviewed === true, JSON.stringify(exists.json));
    const byShop = await api('/api/review/by-shop?shopId=' + shops[0].id + '&page=1&size=5', { jar });
    check('店铺评价列表含新评价', byShop.json !== null && byShop.json.code === 200
        && (byShop.json.data.records || []).some(r => r.orderId === orderIdD),
    JSON.stringify(byShop.json && byShop.json.data));
    const detailD = await api('/api/order/detail?orderId=' + orderIdD, { jar });
    check('订单详情聚合评价与店名', detailD.json !== null && detailD.json.code === 200
        && detailD.json.data.review !== null && !!detailD.json.data.order.shopName,
    JSON.stringify(detailD.json && detailD.json.data && detailD.json.data.order));
}

/* ---------- 9. 管理员用户管理与店铺监管 ---------- */
console.log('9. 管理员用户管理与店铺监管');
const adminLogin = await login('admin', '123456');
check('admin 登录成功', adminLogin.result.json !== null && adminLogin.result.json.code === 200,
    JSON.stringify(adminLogin.result.json));
const adminJar = adminLogin.jar;

const users = await api('/api/admin/users?page=1&size=10', { jar: adminJar });
const userRecords = (users.json && users.json.data && users.json.data.records) || [];
check('用户列表分页且密码脱敏', users.json !== null && users.json.code === 200
    && userRecords.length > 0 && userRecords.every(u => u.password === null || u.password === undefined),
JSON.stringify(users.json && users.json.data));

const riderName = 'rider' + String(Date.now()).slice(-10);
const riderPhone = '139' + String(Date.now()).slice(-8);
const createdRider = await api('/api/admin/create-rider', {
    method: 'POST', body: { username: riderName, password: '123456', phone: riderPhone }, jar: adminJar
});
check('创建骑手账号成功', createdRider.json !== null && createdRider.json.code === 200
    && createdRider.json.data !== null && !!createdRider.json.data.id, JSON.stringify(createdRider.json));
if (createdRider.json !== null && createdRider.json.code === 200) {
    const riderId = createdRider.json.data.id;
    const disable = await api('/api/admin/user-status', {
        method: 'POST', body: { userId: riderId, status: 'DISABLED' }, jar: adminJar
    });
    check('禁用骑手成功', disable.json !== null && disable.json.code === 200, JSON.stringify(disable.json));
    const disabledLogin = await api('/api/auth/login', {
        method: 'POST', body: { username: riderName, password: '123456' }
    });
    check('被禁用账号登录被拒', disabledLogin.json !== null && disabledLogin.json.code !== 200,
        JSON.stringify(disabledLogin.json));
    const enable = await api('/api/admin/user-status', {
        method: 'POST', body: { userId: riderId, status: 'NORMAL' }, jar: adminJar
    });
    check('重新启用骑手成功', enable.json !== null && enable.json.code === 200, JSON.stringify(enable.json));
}
const selfDisable = await api('/api/admin/user-status', {
    method: 'POST', body: { userId: 1, status: 'DISABLED' }, jar: adminJar
});
check('管理员禁用自己被拒', selfDisable.json !== null && selfDisable.json.code !== 200,
    JSON.stringify(selfDisable.json));

const force = await api('/api/admin/shop-status', {
    method: 'POST', body: { shopId: shops[0].id, businessStatus: 'CLOSED' }, jar: adminJar
});
check('强制店铺休息成功', force.json !== null && force.json.code === 200, JSON.stringify(force.json));
const forceAgain = await api('/api/admin/shop-status', {
    method: 'POST', body: { shopId: shops[0].id, businessStatus: 'CLOSED' }, jar: adminJar
});
check('重复强制休息被拒', forceAgain.json !== null && forceAgain.json.code !== 200, JSON.stringify(forceAgain.json));
const reopen = await api('/api/shop/business-status', { method: 'POST', body: { businessStatus: 'OPEN' }, jar: shopJar });
check('商家自行恢复营业（还原状态供下轮冒烟）', reopen.json !== null && reopen.json.code === 200,
    JSON.stringify(reopen.json));

/* ---------- 10. 管理员订单查询与数据统计 ---------- */
console.log('10. 管理员订单查询与数据统计');
const adminOrders = await api('/api/admin/orders?page=1&size=5&orderStatus=COMPLETED', { jar: adminJar });
const adminOrderRecords = (adminOrders.json && adminOrders.json.data && adminOrders.json.data.records) || [];
check('订单查询按状态筛选且含顾客名/店名', adminOrders.json !== null && adminOrders.json.code === 200
    && adminOrderRecords.length > 0
    && adminOrderRecords.every(o => o.orderStatus === 'COMPLETED' && !!o.customerName && !!o.shopName),
JSON.stringify(adminOrders.json && adminOrders.json.data));

const overview = await api('/api/admin/stats/overview', { jar: adminJar });
check('统计概览返回订单数与交易金额', overview.json !== null && overview.json.code === 200
    && typeof overview.json.data.orderCount === 'number' && overview.json.data.totalAmount !== undefined,
JSON.stringify(overview.json));

const ranking = await api('/api/admin/stats/ranking', { jar: adminJar });
check('销量排行返回四口径榜单', ranking.json !== null && ranking.json.code === 200
    && Array.isArray(ranking.json.data.shopAmountRank) && Array.isArray(ranking.json.data.dishQuantityRank)
    && ranking.json.data.shopAmountRank.length > 0 && ranking.json.data.dishQuantityRank.length > 0,
JSON.stringify(ranking.json));

/* ---------- 11. 下单后购物车已清空 ---------- */
console.log('11. 收尾状态');
const cartAfter = await api('/api/cart/list', { jar });
check('下单后购物车已清空', cartAfter.json !== null && cartAfter.json.code === 200
    && cartAfter.json.data.isEmpty === true, JSON.stringify(cartAfter.json && cartAfter.json.data));

/* ---------- 12. 购物车下架菜品改数量校验（P1 收口） ---------- */
console.log('12. 购物车下架菜品改数量校验');
const myDishes = await api('/api/dish/my', { jar: shopJar });
const onSaleDish = ((myDishes.json && myDishes.json.data) || []).find(d => d.status === 'ON_SALE');
check('商家本店存在在售菜品供 P1 用例', onSaleDish !== undefined,
    JSON.stringify(myDishes.json && myDishes.json.data));
if (onSaleDish !== undefined) {
    const addP1 = await api('/api/cart/add', { method: 'POST', body: { dishId: onSaleDish.id, quantity: 1 }, jar });
    check('P1 加购在售菜品成功', addP1.json !== null && addP1.json.code === 200, JSON.stringify(addP1.json));
    const off = await api('/api/dish/status', { method: 'POST', body: { id: onSaleDish.id, status: 'OFF_SALE' }, jar: shopJar });
    check('商家下架该菜品成功', off.json !== null && off.json.code === 200, JSON.stringify(off.json));
    const countOff = await api('/api/cart/count', { method: 'POST', body: { dishId: onSaleDish.id, quantity: 2 }, jar });
    check('下架后修改购物车数量被拒', countOff.json !== null && countOff.json.code !== 200, JSON.stringify(countOff.json));
    const on = await api('/api/dish/status', { method: 'POST', body: { id: onSaleDish.id, status: 'ON_SALE' }, jar: shopJar });
    check('商家恢复上架该菜品成功', on.json !== null && on.json.code === 200, JSON.stringify(on.json));
    const countOn = await api('/api/cart/count', { method: 'POST', body: { dishId: onSaleDish.id, quantity: 2 }, jar });
    check('恢复上架后修改数量成功', countOn.json !== null && countOn.json.code === 200, JSON.stringify(countOn.json));
    await api('/api/cart/clear', { method: 'POST', body: {}, jar });
}

/* ---------- 13. D11 增量接口（搜索/评分/统计/改手机号） ---------- */
console.log('13. D11 增量接口');
const dishSearch = await api('/api/dish/search?page=1&size=5', { jar });
const dishRecords = (dishSearch.json && dishSearch.json.data && dishSearch.json.data.records) || [];
check('全局菜品搜索返回在售菜品含店铺名/营业状态', dishSearch.json !== null && dishSearch.json.code === 200
    && dishRecords.every(d => d.status === 'ON_SALE' && !!d.shopName && !!d.businessStatus),
JSON.stringify(dishSearch.json && dishSearch.json.data));
const detailRating = await api('/api/shop/detail?shopId=' + shops[0].id, { jar });
check('店铺详情含评分摘要（无评价时全 0）', detailRating.json !== null && detailRating.json.code === 200
    && detailRating.json.data.ratingSummary !== null && detailRating.json.data.ratingSummary !== undefined
    && typeof detailRating.json.data.ratingSummary.reviewCount === 'number',
JSON.stringify(detailRating.json && detailRating.json.data && detailRating.json.data.ratingSummary));
const shopStats = await api('/api/shop/stats', { jar: shopJar });
check('商家经营数据返回核心指标与热销排行', shopStats.json !== null && shopStats.json.code === 200
    && typeof shopStats.json.data.orderCount === 'number' && Array.isArray(shopStats.json.data.hotDishRank),
JSON.stringify(shopStats.json));
const custStats = await api('/api/shop/stats', { jar });
check('顾客调用商家统计返回 403', custStats.status === 403, 'status=' + custStats.status);
const cur = await api('/api/auth/current', { jar });
const origPhone = cur.json && cur.json.data ? cur.json.data.phone : null;
const dupPhone = await api('/api/auth/update-profile', { method: 'POST', body: { phone: '13800000003' }, jar });
check('改手机号为已占用号被拒', dupPhone.json !== null && dupPhone.json.code !== 200, JSON.stringify(dupPhone.json));
const backPhone = await api('/api/auth/update-profile', { method: 'POST', body: { phone: origPhone }, jar });
check('改回原手机号成功', backPhone.json !== null && backPhone.json.code === 200, JSON.stringify(backPhone.json));

/* ---------- 14. 退款仲裁（平台双向裁定） ---------- */
console.log('14. 退款仲裁');
// 自造两笔前置退款单：种子 PENDING 退款可能被前一轮冒烟裁掉，故每轮自行申请，保证断言恒定
const myOrders = await api('/api/order/list?page=1&size=50', { jar });
const myRecords = (myOrders.json && myOrders.json.data && myOrders.json.data.records) || [];
const eligibles = myRecords.filter(o => o.payStatus === 'PAID'
    && ['PENDING_ACCEPT', 'PREPARING', 'PENDING_DELIVERY', 'DELIVERING', 'DELIVERED'].includes(o.orderStatus));
check('存在两笔可申请退款的已支付订单（仲裁前置）', eligibles.length >= 2,
JSON.stringify(myRecords.map(o => o.orderStatus)));
const applyA = await api('/api/refund/apply', {
    method: 'POST', body: { orderId: eligibles[0].id, reason: '冒烟测试仲裁前置申请A' }, jar
});
const applyB = await api('/api/refund/apply', {
    method: 'POST', body: { orderId: eligibles[1].id, reason: '冒烟测试仲裁前置申请B' }, jar
});
check('两笔退款申请均成功（仲裁前置）', applyA.json !== null && applyA.json.code === 200
    && applyB.json !== null && applyB.json.code === 200, JSON.stringify(applyA.json) + JSON.stringify(applyB.json));
const arbList = await api('/api/admin/refunds?page=1&size=10', { jar: adminJar });
const arbRecords = (arbList.json && arbList.json.data && arbList.json.data.records) || [];
const arbA = arbRecords.find(r => r.orderId === eligibles[0].id);
const arbB = arbRecords.find(r => r.orderId === eligibles[1].id);
check('仲裁列表含两笔待裁定退款（带店铺/顾客名）', arbList.json !== null && arbList.json.code === 200
    && arbA !== undefined && arbB !== undefined && !!arbA.shopName && !!arbA.customerName,
JSON.stringify(arbList.json && arbList.json.data));
const decideA = await api('/api/admin/refund-decide', {
    method: 'POST', body: { refundId: arbA.refundId, agree: true, adminRemark: '冒烟测试平台裁定同意' }, jar: adminJar
});
check('平台裁定同意成功', decideA.json !== null && decideA.json.code === 200, JSON.stringify(decideA.json));
const decideDup = await api('/api/admin/refund-decide', {
    method: 'POST', body: { refundId: arbA.refundId, agree: true, adminRemark: '重复裁定' }, jar: adminJar
});
check('重复裁定被拒（条件更新）', decideDup.json !== null && decideDup.json.code !== 200, JSON.stringify(decideDup.json));
const decideNoReason = await api('/api/admin/refund-decide', {
    method: 'POST', body: { refundId: arbB.refundId, agree: false, adminRemark: '' }, jar: adminJar
});
check('驳回不填理由被拒', decideNoReason.json !== null && decideNoReason.json.code !== 200, JSON.stringify(decideNoReason.json));
const decideB = await api('/api/admin/refund-decide', {
    method: 'POST', body: { refundId: arbB.refundId, agree: false, adminRemark: '冒烟测试平台裁定驳回' }, jar: adminJar
});
const detailB = await api('/api/order/detail?orderId=' + eligibles[1].id, { jar: adminJar });
check('平台裁定驳回成功且不退款（状态 ADMIN_REJECTED、支付状态仍 PAID）',
    decideB.json !== null && decideB.json.code === 200
    && detailB.json !== null && detailB.json.data !== null
    && detailB.json.data.refund !== null && detailB.json.data.refund.status === 'ADMIN_REJECTED'
    && detailB.json.data.order.payStatus === 'PAID',
JSON.stringify(detailB.json && detailB.json.data && detailB.json.data.refund));
const arbCust = await api('/api/admin/refund-decide', { method: 'POST', body: { refundId: 1, agree: true }, jar });
check('顾客调用裁定接口返回 403', arbCust.status === 403, 'status=' + arbCust.status);

/* ---------- 15. 退款交叉路径（申请退款后商家拒单，应收敛为一条记录） ---------- */
console.log('15. 退款交叉路径');
// 构造订单 E：加购 → 下单 → 支付（PENDING_ACCEPT）
await api('/api/cart/clear', { method: 'POST', body: {}, jar });
const addE = await api('/api/cart/add', { method: 'POST', body: { dishId: dishes[0].id, quantity: 1 }, jar });
check('订单 E 加购成功', addE.json !== null && addE.json.code === 200, JSON.stringify(addE.json));
const tokenE = await api('/api/order/token', { jar });
check('订单 E 获取幂等令牌', tokenE.json !== null && tokenE.json.code === 200, JSON.stringify(tokenE.json));
const createdE = await api('/api/order/create', {
    method: 'POST',
    body: { addressId: addresses[0].id, remark: '冒烟测试E', token: tokenE.json.data.token },
    jar
});
check('订单 E 创建成功', createdE.json !== null && createdE.json.code === 200, JSON.stringify(createdE.json));
if (createdE.json !== null && createdE.json.code === 200) {
    const orderIdE = createdE.json.data.orderId;
    const payE = await api('/api/order/pay', { method: 'POST', body: { orderId: orderIdE }, jar });
    check('订单 E 支付成功', payE.json !== null && payE.json.code === 200, JSON.stringify(payE.json));
    // 顾客先提交退款申请（此时商家未处理，生成 PENDING 记录）
    const applyE = await api('/api/refund/apply', {
        method: 'POST', body: { orderId: orderIdE, reason: '冒烟测试交叉退款' }, jar
    });
    check('订单 E 退款申请成功', applyE.json !== null && applyE.json.code === 200,
        JSON.stringify(applyE.json));
    // 商家直接拒单（交叉路径）：应收敛为同一条 SHOP_AGREED，而非新增第二条
    const rejectE = await api('/api/order/reject', {
        method: 'POST', body: { orderId: orderIdE, reason: '冒烟测试交叉拒单' }, jar: shopJar
    });
    check('订单 E 商家拒单成功（与退款申请并存）', rejectE.json !== null && rejectE.json.code === 200,
        JSON.stringify(rejectE.json));
    const detailE = await api('/api/order/detail?orderId=' + orderIdE, { jar });
    check('订单 E 退款收敛为一条 SHOP_AGREED', detailE.json !== null && detailE.json.code === 200
        && detailE.json.data.refund !== null && detailE.json.data.refund.status === 'SHOP_AGREED',
    JSON.stringify(detailE.json && detailE.json.data && detailE.json.data.refund));
    const listE = await api('/api/order/list?page=1&size=50', { jar });
    const eRows = (listE.json.data.records || []).filter(o => o.id === orderIdE);
    check('订单 E 在列表中仅一行（无双记录放大）', eRows.length === 1, 'rows=' + eRows.length);
    const applyAgain = await api('/api/refund/apply', {
        method: 'POST', body: { orderId: orderIdE, reason: '冒烟测试重复申请' }, jar
    });
    check('订单 E 重复申请退款仍被拒', applyAgain.json !== null && applyAgain.json.code !== 200,
        JSON.stringify(applyAgain.json));
}

/* 回归：申请退款 → 商家驳回 → 商家拒单，拒单必须成功且退款升级为 SHOP_AGREED */
await api('/api/cart/clear', { method: 'POST', body: {}, jar });
const addF = await api('/api/cart/add', { method: 'POST', body: { dishId: dishes[0].id, quantity: 1 }, jar });
check('订单 F 加购成功', addF.json !== null && addF.json.code === 200, JSON.stringify(addF.json));
const tokenF = await api('/api/order/token', { jar });
const createdF = await api('/api/order/create', {
    method: 'POST',
    body: { addressId: addresses[0].id, remark: '冒烟测试F', token: tokenF.json.data.token },
    jar
});
check('订单 F 创建成功', createdF.json !== null && createdF.json.code === 200, JSON.stringify(createdF.json));
if (createdF.json !== null && createdF.json.code === 200) {
    const orderIdF = createdF.json.data.orderId;
    const payF = await api('/api/order/pay', { method: 'POST', body: { orderId: orderIdF }, jar });
    check('订单 F 支付成功', payF.json !== null && payF.json.code === 200, JSON.stringify(payF.json));
    const applyF = await api('/api/refund/apply', {
        method: 'POST', body: { orderId: orderIdF, reason: '冒烟测试F退款' }, jar
    });
    check('订单 F 退款申请成功', applyF.json !== null && applyF.json.code === 200,
        JSON.stringify(applyF.json));
    const pendingF = await api('/api/refund/pending?page=1&size=50', { jar: shopJar });
    const refundF = ((pendingF.json && pendingF.json.data && pendingF.json.data.records) || [])
        .find(r => r.orderId === orderIdF);
    check('订单 F 退款进入待处理列表', refundF !== undefined,
        JSON.stringify(pendingF.json && pendingF.json.data));
    const rejectRefundF = await api('/api/refund/handle', {
        method: 'POST', body: { refundId: refundF.refundId, agree: false, remark: '冒烟测试驳回退款' },
        jar: shopJar
    });
    check('订单 F 商家驳回退款成功', rejectRefundF.json !== null && rejectRefundF.json.code === 200,
        JSON.stringify(rejectRefundF.json));
    // P1 回归断言：退款被驳回（SHOP_REJECTED）后商家拒单必须成功，且记录收敛升级为 SHOP_AGREED
    const rejectF = await api('/api/order/reject', {
        method: 'POST', body: { orderId: orderIdF, reason: '冒烟测试F拒单' }, jar: shopJar
    });
    check('订单 F 退款被驳回后拒单成功（P1 回归修复）', rejectF.json !== null && rejectF.json.code === 200,
        JSON.stringify(rejectF.json));
    const detailF = await api('/api/order/detail?orderId=' + orderIdF, { jar });
    check('订单 F 退款升级为 SHOP_AGREED', detailF.json !== null && detailF.json.code === 200
        && detailF.json.data.refund !== null && detailF.json.data.refund.status === 'SHOP_AGREED',
    JSON.stringify(detailF.json && detailF.json.data && detailF.json.data.refund));
}

/* ---------- 16. 找回审批 ---------- */
console.log('16. 找回审批流程');
// 用 admin 创建临时骑手账号走全流程，避免改动种子账号密码
const tempName = 'rider' + String(Date.now()).slice(-10);
const tempPhone = '137' + String(Date.now()).slice(-8);
const createTemp = await api('/api/admin/create-rider', {
    method: 'POST', body: { username: tempName, password: '123456', phone: tempPhone }, jar: adminJar
});
check('创建找回流程临时骑手', createTemp.json !== null && createTemp.json.code === 200,
    JSON.stringify(createTemp.json));
// ① 提交申请：手机号与用户名不匹配被拒（防枚举口径）
const resetBad = await api('/api/auth/reset-request', {
    method: 'POST', body: { username: tempName, phone: '13800000000' }
});
check('找回申请手机号不匹配被拒', resetBad.json !== null && resetBad.json.code !== 200,
    JSON.stringify(resetBad.json));
// ② 匹配的申请提交成功
const resetReq = await api('/api/auth/reset-request', {
    method: 'POST', body: { username: tempName, phone: tempPhone }
});
check('找回申请提交成功', resetReq.json !== null && resetReq.json.code === 200,
    JSON.stringify(resetReq.json));
// ③ 管理端列表可见（PENDING）
const resetList = await api('/api/admin/reset-requests', { jar: adminJar });
const resetItem = ((resetList.json && resetList.json.data) || []).find(r => r.username === tempName);
check('找回审批列表含临时骑手申请', resetItem !== undefined && resetItem.status === 'PENDING',
    JSON.stringify(resetList.json));
// ④ 管理员同意 → 生成 6 位一次性验证码
const decide = await api('/api/admin/reset-decide', {
    method: 'POST', body: { requestId: resetItem.id, agree: true }, jar: adminJar
});
const resetCode = decide.json && decide.json.data && decide.json.data.code;
check('管理员同意生成 6 位验证码', typeof resetCode === 'string' && /^\d{6}$/.test(resetCode),
    JSON.stringify(decide.json));
// ⑤ 错误验证码被拒
const resetWrong = await api('/api/auth/reset-password', {
    method: 'POST', body: { username: tempName, code: '000000', newPassword: '123456' }
});
check('错误验证码重置被拒', resetWrong.json !== null && resetWrong.json.code !== 200,
    JSON.stringify(resetWrong.json));
// ⑥ 正确验证码重置成功
const resetOk = await api('/api/auth/reset-password', {
    method: 'POST', body: { username: tempName, code: resetCode, newPassword: 'abc12345' }
});
check('正确验证码重置成功', resetOk.json !== null && resetOk.json.code === 200,
    JSON.stringify(resetOk.json));
// ⑦ 验证码一次性：重放被拒
const resetReplay = await api('/api/auth/reset-password', {
    method: 'POST', body: { username: tempName, code: resetCode, newPassword: '123456' }
});
check('验证码重放被拒（一次性）', resetReplay.json !== null && resetReplay.json.code !== 200,
    JSON.stringify(resetReplay.json));
// ⑧ 新密码可登录
const tempLogin = await api('/api/auth/login', {
    method: 'POST', body: { username: tempName, password: 'abc12345' }
});
check('新密码登录成功', tempLogin.json !== null && tempLogin.json.code === 200,
    JSON.stringify(tempLogin.json));

/* ---------- 16. 对象归属越权补充（address / dish / delivery） ---------- */
console.log('17. 对象归属越权补充');
const otherCust = await login('customer2', '123456');
const addrDel = await api('/api/address/delete', {
    method: 'POST', body: { addressId: addresses[0].id }, jar: otherCust.jar
});
check('顾客2 删除顾客1 地址被拒（归属校验）', addrDel.json !== null && addrDel.json.code !== 200,
    JSON.stringify(addrDel.json));
const otherShop = await login('shop2', '123456');
const dishOther = await api('/api/dish/status', {
    method: 'POST', body: { id: dishes[0].id, status: 'ON_SALE' }, jar: otherShop.jar
});
check('商家2 上下架商家1 菜品被拒（归属校验）', dishOther.json !== null && dishOther.json.code !== 200,
    JSON.stringify(dishOther.json));
const otherRider = await login('rider2', '123456');
const nodeOther = await api('/api/delivery/node', {
    method: 'POST', body: { deliveryId: 6, status: 'PICKED' }, jar: otherRider.jar
});
check('骑手2 处理骑手1 配送单被拒（归属校验）', nodeOther.json !== null && nodeOther.json.code !== 200,
    JSON.stringify(nodeOther.json));

/* ---------- 18. P3-1 回归：平台裁定驳回后商家拒单，退款记录收敛为 SHOP_AGREED（2026-09-13） ---------- */
console.log('18. P3-1 回归（申请 → 管理员驳回 → 拒单）');
await api('/api/cart/clear', { method: 'POST', body: {}, jar });
const addG = await api('/api/cart/add', { method: 'POST', body: { dishId: dishes[0].id, quantity: 1 }, jar });
check('订单 G 加购成功', addG.json !== null && addG.json.code === 200, JSON.stringify(addG.json));
const tokenG = await api('/api/order/token', { jar });
const createdG = await api('/api/order/create', {
    method: 'POST', body: { addressId: addresses[0].id, remark: 'P3-1 回归', token: tokenG.json.data.token }, jar
});
check('订单 G 创建成功', createdG.json !== null && createdG.json.code === 200, JSON.stringify(createdG.json));
const orderIdG = createdG.json.data.orderId;
const payG = await api('/api/order/pay', { method: 'POST', body: { orderId: orderIdG }, jar });
check('订单 G 支付成功', payG.json !== null && payG.json.code === 200, JSON.stringify(payG.json));
const applyG = await api('/api/refund/apply', {
    method: 'POST', body: { orderId: orderIdG, reason: 'P3-1 回归申请' }, jar
});
check('订单 G 退款申请成功', applyG.json !== null && applyG.json.code === 200, JSON.stringify(applyG.json));
const arbG = await api('/api/admin/refunds?page=1&size=10', { jar: adminJar });
const recG = ((arbG.json && arbG.json.data && arbG.json.data.records) || []).find(r => r.orderId === orderIdG);
check('仲裁列表含订单 G 待裁定记录', recG !== undefined, JSON.stringify(arbG.json && arbG.json.data));
const decideG = await api('/api/admin/refund-decide', {
    method: 'POST', body: { refundId: recG.refundId, agree: false, adminRemark: 'P3-1 回归驳回' }, jar: adminJar
});
check('平台裁定驳回成功（ADMIN_REJECTED）', decideG.json !== null && decideG.json.code === 200,
    JSON.stringify(decideG.json));
const rejectG = await api('/api/order/reject', {
    method: 'POST', body: { orderId: orderIdG, reason: 'P3-1 回归拒单' }, jar: shopJar
});
check('裁定驳回后商家拒单成功（收敛不再静默跳过）', rejectG.json !== null && rejectG.json.code === 200,
    JSON.stringify(rejectG.json));
const detailG = await api('/api/order/detail?orderId=' + orderIdG, { jar: adminJar });
const refundG = detailG.json && detailG.json.data && detailG.json.data.refund;
const orderG = detailG.json && detailG.json.data && detailG.json.data.order;
check('退款记录收敛为 SHOP_AGREED 且与 pay_status=REFUNDED 一致',
    refundG !== null && refundG.status === 'SHOP_AGREED'
    && orderG !== null && orderG.payStatus === 'REFUNDED' && orderG.orderStatus === 'REJECTED',
JSON.stringify(refundG) + JSON.stringify(orderG));

summary('冒烟测试');
