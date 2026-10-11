/**
 * 并发与事务回归，只允许本机隔离环境：18080 / 13306 / DeliveryWeb_reliability。
 * MYSQL_TEST_BIN 指向 mysql.exe；该实例使用空密码 root，禁止连接业务数据库。
 * node test/reliability-test.mjs http://127.0.0.1:18080/DeliveryWeb
 */
import assert from 'node:assert/strict';
import { execFileSync, spawn } from 'node:child_process';
import { BASE, api, login } from './lib.mjs';

const url = new URL(BASE);
assert.equal(url.hostname, '127.0.0.1', '仅允许本机隔离测试实例');
assert.equal(url.port, '18080', '仅允许隔离 Tomcat 的 18080 端口');
const mysql = process.env.MYSQL_TEST_BIN;
assert.ok(mysql, '请设置 MYSQL_TEST_BIN 为 mysql.exe 的路径');
const mysqlArgs = ['--no-defaults', '--host=127.0.0.1', '--port=13306', '--user=root',
    '--default-character-set=utf8mb4', '--batch', '--skip-column-names', '--database=DeliveryWeb_reliability'];
const sql = query => execFileSync(mysql, [...mysqlArgs, '--execute=' + query],
    { encoding: 'utf8', timeout: 15000 }).trim();
assert.equal(sql('SELECT @@port'), '13306');

let checks = 0;
const verify = (name, condition, detail = '') => {
    assert.ok(condition, name + (detail ? ': ' + detail : ''));
    checks++;
    console.log('[通过] ' + name);
};
const ok = result => result.status === 200 && result.json?.code === 200;
const must = async promise => {
    const result = await promise;
    assert.ok(ok(result), JSON.stringify(result));
    return result.json.data;
};
const post = (path, body, jar) => api(path, { method: 'POST', body, jar });
const sessions = [];
let dishId;
let secondDishId;

async function customer() {
    const session = await login('customer1', '123456');
    assert.ok(ok(session.result), '顾客登录失败');
    sessions.push(session.jar);
    return session.jar;
}

// 已获取行锁的数据库连接暂缓提交，验证支付等待行锁后重新判断截止时间。
async function holdOrderLock(orderId) {
    const child = spawn(mysql, [...mysqlArgs, '--unbuffered', '--execute='
        + `START TRANSACTION; SELECT id FROM orders WHERE id=${orderId} FOR UPDATE; `
        + "SELECT 'LOCKED'; SELECT SLEEP(3); COMMIT;"], { windowsHide: true });
    const finished = new Promise((resolve, reject) => {
        let error = '';
        child.stderr.on('data', data => { error += data; });
        child.once('error', reject);
        child.once('exit', code => code === 0 ? resolve() : reject(new Error(error || '锁连接异常退出')));
    });
    // 防止锁准备失败时未处理 finished 的拒绝。
    finished.catch(() => {});
    await new Promise((resolve, reject) => {
        let output = '';
        const timer = setTimeout(() => { child.kill(); reject(new Error('获取测试行锁超时')); }, 10000);
        child.stdout.on('data', data => {
            output += data;
            if (output.includes('LOCKED')) { clearTimeout(timer); resolve(); }
        });
        child.once('error', error => { clearTimeout(timer); reject(error); });
        child.once('exit', () => {
            clearTimeout(timer);
            if (!output.includes('LOCKED')) reject(new Error('未取得测试行锁'));
        });
    });
    return { finished };
}

try {
    const seller = await login('shop1', '123456');
    assert.ok(ok(seller.result), '商家登录失败');
    sessions.push(seller.jar);
    await must(api('/api/shop/my', { jar: seller.jar }));
    const jar = await customer();
    const addresses = await must(api('/api/address/list', { jar }));
    assert.ok(addresses.length);
    const addressId = addresses[0].id;
    const name = '可靠性回归_' + Date.now();
    const info = { name, price: 10, imageUrl: null, description: '可靠性隔离测试' };
    await must(post('/api/dish/save', { ...info, stock: 100 }, seller.jar));
    let dishes = await must(api('/api/dish/my', { jar: seller.jar }));
    dishId = dishes.find(d => d.name === name)?.id;
    assert.ok(Number.isSafeInteger(dishId));
    assert.equal(sql(`SELECT name FROM dish WHERE id=${dishId}`), name,
        '部署的应用必须连接隔离数据库');
    await must(post('/api/dish/status', { id: dishId, status: 'ON_SALE' }, seller.jar));
    const stock = () => Number(sql(`SELECT stock FROM dish WHERE id=${dishId}`));
    const orders = () => Number(sql(`SELECT COUNT(*) FROM order_item WHERE dish_id=${dishId}`));
    const cancel = (id, owner = jar) => must(post('/api/order/cancel', { orderId: id }, owner));
    const prepare = async (owner = jar, quantity = 1, extraDishId = null) => {
        await must(post('/api/cart/clear', {}, owner));
        await must(post('/api/cart/add', { dishId, quantity }, owner));
        if (extraDishId) await must(post('/api/cart/add', { dishId: extraDishId, quantity: 1 }, owner));
        const token = await must(api('/api/order/token', { jar: owner }));
        return { addressId, token: token.token, remark: '并发可靠性回归' };
    };
    const create = async owner => must(post('/api/order/create', await prepare(owner), owner));

    const before = orders();
    const body = await prepare();
    const attempts = await Promise.all(Array.from({ length: 24 }, () => post('/api/order/create', body, jar)));
    const successes = attempts.filter(ok);
    verify('同一令牌24个并发请求仅创建一个订单', successes.length === 1 && orders() === before + 1);
    verify('重复提交只扣一次库存', stock() === 99);
    const order = successes[0].json.data;
    await cancel(order.orderId);
    verify('取消恢复一次库存', stock() === 100);
    verify('重复取消被拒且不重复恢复库存', !ok(await post('/api/order/cancel', { orderId: order.orderId }, jar))
        && stock() === 100);

    const validBody = await prepare();
    verify('错误令牌被拒', !ok(await post('/api/order/create', { ...validBody, token: 'invalid' }, jar)));
    const afterInvalid = await must(post('/api/order/create', validBody, jar));
    verify('错误令牌不会销毁有效令牌', Number.isSafeInteger(afterInvalid.orderId));
    await cancel(afterInvalid.orderId);

    await must(post('/api/cart/clear', {}, jar));
    await Promise.all(Array.from({ length: 24 }, () => must(post('/api/cart/add', { dishId, quantity: 1 }, jar))));
    const cart = await must(api('/api/cart/list', { jar }));
    verify('并发加购没有丢失数量', cart.items.length === 1 && cart.items[0].quantity === 24);

    const stockOrder = await create(jar);
    await must(post('/api/dish/save', { ...info, id: dishId, description: '仅编辑资料', stock: null }, seller.jar));
    verify('只编辑资料保留实时扣减后的库存', stock() === 99);
    const stale = await post('/api/dish/save', { ...info, id: dishId, stock: 110, originalStock: 100 }, seller.jar);
    verify('使用旧库存调整被拒且整次保存回滚', !ok(stale) && stock() === 99
        && sql(`SELECT description FROM dish WHERE id=${dishId}`) === '仅编辑资料');
    verify('缺少原库存的覆盖写入被拒', !ok(await post('/api/dish/save', { ...info, id: dishId, stock: 110 }, seller.jar)));
    verify('大整数库存不会被截断', !ok(await post('/api/dish/save',
        { ...info, id: dishId, stock: 4294967296, originalStock: 99 }, seller.jar)));
    await must(post('/api/dish/save', { ...info, id: dishId, stock: 101, originalStock: 99 }, seller.jar));
    verify('原库存匹配时可主动调整', stock() === 101);
    await cancel(stockOrder.orderId);
    verify('调整后取消仍正确恢复预留库存', stock() === 102);

    // 不依赖等待15分钟，在隔离库中设置该测试订单的创建时间。
    const expired = await create(jar);
    sql(`UPDATE orders SET created_at=DATE_SUB(NOW(), INTERVAL 16 MINUTE) WHERE id=${expired.orderId}`);
    const expiredPay = await post('/api/order/pay', { orderId: expired.orderId }, jar);
    verify('过期订单拒绝支付', !ok(expiredPay));
    verify('过期支付触发取消并提交库存恢复', stock() === 102
        && sql(`SELECT CONCAT(order_status, ':', pay_status) FROM orders WHERE id=${expired.orderId}`) === 'CANCELLED:UNPAID');
    await post('/api/order/pay', { orderId: expired.orderId }, jar);
    verify('过期订单重试不重复恢复库存', stock() === 102);

    const waiting = await create(jar);
    sql(`UPDATE orders SET created_at=DATE_SUB(DATE_SUB(NOW(), INTERVAL 14 MINUTE), INTERVAL 58 SECOND) WHERE id=${waiting.orderId}`);
    const { finished: lockDone } = await holdOrderLock(waiting.orderId);
    const waitedPay = await post('/api/order/pay', { orderId: waiting.orderId }, jar);
    await lockDone;
    verify('支付等行锁跨过截止时间后被拒绝', !ok(waitedPay) && stock() === 102
        && sql(`SELECT order_status FROM orders WHERE id=${waiting.orderId}`) === 'CANCELLED');

    const racing = await create(jar);
    const [payResult, cancelResult] = await Promise.all([
        post('/api/order/pay', { orderId: racing.orderId }, jar),
        post('/api/order/cancel', { orderId: racing.orderId }, jar)
    ]);
    const state = sql(`SELECT CONCAT(order_status, ':', pay_status) FROM orders WHERE id=${racing.orderId}`);
    verify('支付与取消竞争只有一个成功', Number(ok(payResult)) + Number(ok(cancelResult)) === 1);
    verify('竞争后的订单状态与库存一致', (state === 'PENDING_ACCEPT:PAID' && stock() === 101)
        || (state === 'CANCELLED:UNPAID' && stock() === 102));

    // 多菜品扣减中途失败：前一个菜品扣减和订单写入都应回滚。
    await must(post('/api/dish/save', { ...info, name: name + '_2', stock: 1 }, seller.jar));
    dishes = await must(api('/api/dish/my', { jar: seller.jar }));
    secondDishId = dishes.find(d => d.name === name + '_2')?.id;
    assert.ok(Number.isSafeInteger(secondDishId));
    await must(post('/api/dish/status', { id: secondDishId, status: 'ON_SALE' }, seller.jar));
    const rollbackBody = await prepare(jar, 1, secondDishId);
    const rollbackStock = stock();
    const rollbackOrders = orders();
    sql(`UPDATE dish SET stock=0 WHERE id=${secondDishId}`);
    const rollbackResult = await post('/api/order/create', rollbackBody, jar);
    const stockAfterRollback = stock();
    const ordersAfterRollback = orders();
    verify('后一个菜品库存不足时整笔下单回滚', !ok(rollbackResult)
        && stockAfterRollback === rollbackStock && ordersAfterRollback === rollbackOrders,
        JSON.stringify({ rollbackResult, rollbackStock, stockAfterRollback, rollbackOrders, ordersAfterRollback }));

    // 两个独立Session争抢最后一件，校验数据库层防超卖。
    const one = await customer();
    const two = await customer();
    sql(`UPDATE dish SET stock=1 WHERE id=${dishId}`);
    const oneBody = await prepare(one);
    const twoBody = await prepare(two);
    const lastAttempts = await Promise.all([
        post('/api/order/create', oneBody, one), post('/api/order/create', twoBody, two)
    ]);
    verify('不同会话争抢最后一件仅一个订单成功', lastAttempts.filter(ok).length === 1 && stock() === 0);
    const winner = lastAttempts.findIndex(ok);
    await cancel(lastAttempts[winner].json.data.orderId, winner === 0 ? one : two);
    verify('最后一件取消后只恢复一件', stock() === 1);
    console.log(`可靠性回归：通过 ${checks} 项`);
} finally {
    // 测试只操作专用菜品及其订单，清理失败应直接报错。
    await Promise.all(sessions.map(jar => post('/api/auth/logout', {}, jar)));
    if (dishId) {
        const ids = sql(`SELECT DISTINCT order_id FROM order_item WHERE dish_id=${dishId}`)
            .split(/\s+/).filter(Boolean).map(Number);
        assert.ok(ids.every(Number.isSafeInteger));
        if (ids.length) {
            const list = ids.join(',');
            sql(`START TRANSACTION; DELETE FROM review WHERE order_id IN (${list}); `
                + `DELETE FROM refund WHERE order_id IN (${list}); DELETE FROM delivery WHERE order_id IN (${list}); `
                + `DELETE FROM order_item WHERE order_id IN (${list}); DELETE FROM orders WHERE id IN (${list}); COMMIT;`);
        }
        const dishIds = [dishId, secondDishId].filter(Boolean);
        assert.ok(dishIds.every(Number.isSafeInteger));
        sql(`DELETE FROM dish WHERE id IN (${dishIds.join(',')})`);
    }
}
