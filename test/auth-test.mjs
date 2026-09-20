/**
 * 鉴权测试：AuthFilter 401（未登录/禁用存量会话）/ 403（角色不匹配）/ 业务角色校验 / 退出后会话失效 / 错误密码拒绝。
 * 用法：node test/auth-test.mjs [baseUrl]
 * 前置：Tomcat 已启动、init.sql 已执行（种子账号 admin/customer1/shop1/rider1，密码均 123456）。
 * 只读 + 登录/退出，不产生业务数据。
 */
import { BASE, api, login, check, summary } from './lib.mjs';

console.log('鉴权测试 BASE=' + BASE);

/* ---------- 1. 未登录一律 401 ---------- */
console.log('1. 未登录拦截（AuthFilter 401）');
const anonList = await api('/api/shop/list');
check('未登录 /api/shop/list 返回 401', anonList.status === 401, 'status=' + anonList.status);
const anonCreate = await api('/api/order/create', { method: 'POST', body: {} });
check('未登录 /api/order/create 返回 401', anonCreate.status === 401, 'status=' + anonCreate.status);
const anonAdmin = await api('/api/admin/shops');
check('未登录 /api/admin/shops 返回 401', anonAdmin.status === 401, 'status=' + anonAdmin.status);
const anonCart = await api('/api/cart/list');
check('未登录 /api/cart/list 返回 401', anonCart.status === 401, 'status=' + anonCart.status);

/* ---------- 2. 错误密码拒绝 ---------- */
console.log('2. 登录校验');
const bad = await api('/api/auth/login', { method: 'POST', body: { username: 'customer1', password: 'wrong-password' } });
check('错误密码登录被拒（业务码非 200）', bad.status === 200 && bad.json !== null && bad.json.code !== 200,
    JSON.stringify(bad.json));
const nonexistent = await api('/api/auth/login', { method: 'POST', body: { username: 'no-such-user', password: '123456' } });
check('不存在用户登录被拒且提示统一', nonexistent.status === 200 && nonexistent.json !== null
    && nonexistent.json.code !== 200, JSON.stringify(nonexistent.json));

/* ---------- 3. 顾客角色边界 ---------- */
console.log('3. 顾客角色边界');
const customer = await login('customer1', '123456');
check('customer1 登录成功且角色 CUSTOMER', customer.result.json !== null
    && customer.result.json.code === 200 && customer.result.json.data.role === 'CUSTOMER',
JSON.stringify(customer.result.json));
const custAdmin = await api('/api/admin/shops', { jar: customer.jar });
check('顾客访问管理端返回 403', custAdmin.status === 403, 'status=' + custAdmin.status);
const custToken = await api('/api/order/token', { jar: customer.jar });
check('顾客可获取订单令牌', custToken.json !== null && custToken.json.code === 200,
    JSON.stringify(custToken.json));
const custAddress = await api('/api/address/list', { jar: customer.jar });
check('顾客可查本人地址', custAddress.json !== null && custAddress.json.code === 200,
    JSON.stringify(custAddress.json));
const custPool = await api('/api/delivery/pool?page=1&size=5', { jar: customer.jar });
check('顾客调用配送池返回 403', custPool.status === 403, 'status=' + custPool.status);

/* ---------- 4. 商家角色边界 ---------- */
console.log('4. 商家角色边界');
const shop = await login('shop1', '123456');
check('shop1 登录成功且角色 SHOP', shop.result.json !== null && shop.result.json.code === 200
    && shop.result.json.data.role === 'SHOP', JSON.stringify(shop.result.json));
const shopAdmin = await api('/api/admin/shops', { jar: shop.jar });
check('商家访问管理端返回 403', shopAdmin.status === 403, 'status=' + shopAdmin.status);
const shopToken = await api('/api/order/token', { jar: shop.jar });
check('商家获取订单令牌返回 403（仅顾客可下单）', shopToken.status === 403, 'status=' + shopToken.status);
const shopMy = await api('/api/shop/my', { jar: shop.jar });
check('商家可查本人店铺', shopMy.json !== null && shopMy.json.code === 200 && shopMy.json.data !== null,
    JSON.stringify(shopMy.json));
const shopPool = await api('/api/delivery/pool?page=1&size=5', { jar: shop.jar });
check('商家调用配送池返回 403', shopPool.status === 403, 'status=' + shopPool.status);
const shopPending = await api('/api/refund/pending?page=1&size=5', { jar: shop.jar });
check('商家可查本店待处理退款列表', shopPending.json !== null && shopPending.json.code === 200,
    JSON.stringify(shopPending.json));

/* ---------- 5. 管理员角色边界 ---------- */
console.log('5. 管理员角色边界');
const admin = await login('admin', '123456');
check('admin 登录成功且角色 ADMIN', admin.result.json !== null && admin.result.json.code === 200
    && admin.result.json.data.role === 'ADMIN', JSON.stringify(admin.result.json));
const adminShops = await api('/api/admin/shops', { jar: admin.jar });
check('管理员店铺列表成功', adminShops.json !== null && adminShops.json.code === 200,
    JSON.stringify(adminShops.json));
const adminToken = await api('/api/order/token', { jar: admin.jar });
check('管理员获取订单令牌返回 403', adminToken.status === 403, 'status=' + adminToken.status);

/* ---------- 6. 骑手角色边界 ---------- */
console.log('6. 骑手角色边界');
const rider = await login('rider1', '123456');
check('rider1 登录成功且角色 RIDER', rider.result.json !== null && rider.result.json.code === 200
    && rider.result.json.data.role === 'RIDER', JSON.stringify(rider.result.json));
const riderAdmin = await api('/api/admin/shops', { jar: rider.jar });
check('骑手访问管理端返回 403', riderAdmin.status === 403, 'status=' + riderAdmin.status);
const riderToken = await api('/api/order/token', { jar: rider.jar });
check('骑手获取订单令牌返回 403', riderToken.status === 403, 'status=' + riderToken.status);
const riderPool = await api('/api/delivery/pool?page=1&size=5', { jar: rider.jar });
check('骑手可查配送池', riderPool.json !== null && riderPool.json.code === 200,
    JSON.stringify(riderPool.json));
const riderMy = await api('/api/delivery/my?page=1&size=5', { jar: rider.jar });
check('骑手可查本人履约记录', riderMy.json !== null && riderMy.json.code === 200,
    JSON.stringify(riderMy.json));
const riderRefund = await api('/api/refund/pending?page=1&size=5', { jar: rider.jar });
check('骑手调用退款处理返回 403', riderRefund.status === 403, 'status=' + riderRefund.status);

/* ---------- 7. 退出后会话失效 ---------- */
console.log('7. 退出登录');
const logout = await api('/api/auth/logout', { method: 'POST', body: {}, jar: customer.jar });
check('退出登录成功', logout.json !== null && logout.json.code === 200, JSON.stringify(logout.json));
const afterLogout = await api('/api/address/list', { jar: customer.jar });
check('退出后访问受保护接口返回 401', afterLogout.status === 401, 'status=' + afterLogout.status);

/* ---------- 8. 禁用账号存量会话失效（安全收口） ---------- */
console.log('8. 禁用账号存量会话失效');
const cust2 = await login('customer1', '123456');
const cust2Data = (cust2.result.json && cust2.result.json.data) || null;
check('customer1 禁用前重新登录成功', cust2Data !== null, JSON.stringify(cust2.result.json));
if (cust2Data === null) {
    console.log('  [跳过] customer1 种子密码疑似被手动修改，跳过本节剩余用例；重跑 init.sql 恢复种子后重试');
} else {
    const cust2Id = cust2Data.id;
    const beforeDisable = await api('/api/address/list', { jar: cust2.jar });
    check('禁用前存量会话可访问业务接口', beforeDisable.status === 200 && beforeDisable.json !== null
        && beforeDisable.json.code === 200, 'status=' + beforeDisable.status);
    const disable = await api('/api/admin/user-status', { method: 'POST', body: { userId: cust2Id, status: 'DISABLED' }, jar: admin.jar });
    check('管理员禁用 customer1 成功', disable.json !== null && disable.json.code === 200, JSON.stringify(disable.json));
    const afterDisable = await api('/api/address/list', { jar: cust2.jar });
    check('禁用后存量会话访问业务接口返回 401', afterDisable.status === 401, 'status=' + afterDisable.status);
    const relogin = await api('/api/auth/login', { method: 'POST', body: { username: 'customer1', password: '123456' } });
    check('禁用后重新登录被拒', relogin.status === 200 && relogin.json !== null && relogin.json.code !== 200,
        JSON.stringify(relogin.json));
    const enable = await api('/api/admin/user-status', { method: 'POST', body: { userId: cust2Id, status: 'NORMAL' }, jar: admin.jar });
    check('管理员恢复启用 customer1 成功', enable.json !== null && enable.json.code === 200, JSON.stringify(enable.json));
    const afterEnable = await login('customer1', '123456');
    check('恢复启用后 customer1 可重新登录', afterEnable.result.json !== null && afterEnable.result.json.code === 200,
        JSON.stringify(afterEnable.result.json));
}

summary('鉴权测试');
