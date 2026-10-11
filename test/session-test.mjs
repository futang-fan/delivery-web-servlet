/** 多会话回归：使用管理员创建临时骑手，不修改种子账号密码。 */
import assert from 'node:assert/strict';
import { api, request, login } from './lib.mjs';

const post = (path, body, jar) => api(path, { method: 'POST', body, jar });
const ok = result => result.status === 200 && result.json?.code === 200;
const must = async promise => {
    const result = await promise;
    assert.ok(ok(result), JSON.stringify(result));
    return result.json.data;
};
let count = 0;
const verify = (name, value) => { assert.ok(value, name); count++; console.log('[通过] ' + name); };
const suffix = Date.now().toString().slice(-9);
const username = 'sess_' + suffix;
let phone = '19' + suffix;
let password = 'session_old_123';
const sessions = [];
const admin = await login('admin', '123456');
assert.ok(ok(admin.result), '管理员登录失败');
let userId;
async function signIn() {
    const session = await login(username, password);
    assert.ok(ok(session.result), '临时账号登录失败：' + JSON.stringify(session.result));
    sessions.push(session.jar);
    return session.jar;
}
async function valid(jar) {
    return ok(await api('/api/delivery/my?page=1&size=5', { jar }));
}
async function revoked(jars, label) {
    for (let i = 0; i < jars.length; i++) {
        const jar = jars[i];
        verify(`${label}：会话${i + 1}业务接口返回401`, (await api('/api/delivery/my', { jar })).status === 401);
        const current = await api('/api/auth/current', { jar });
        verify(`${label}：会话${i + 1}不再返回用户资料`, current.status === 200 && !ok(current) && current.json?.data == null);
        verify(`${label}：会话${i + 1}不能修改手机号`,
            (await post('/api/auth/update-profile', { phone }, jar)).status === 401);
        verify(`${label}：会话${i + 1}不能修改密码`,
            (await post('/api/auth/change-password', { oldPassword: password, newPassword: 'must_not_apply' }, jar)).status === 401);
        const page = await request('/pages/rider-pool.html', { jar });
        verify(`${label}：会话${i + 1}页面返回登录页`, page.status === 302
            && (page.headers.get('location') || '').includes('/pages/login.html'));
    }
    verify(`${label}：其他账号会话保持有效`, ok(await api('/api/admin/users', { jar: admin.jar })));
}
try {
    userId = (await must(post('/api/admin/create-rider', { username, password, phone }, admin.jar))).id;
    assert.ok(Number.isSafeInteger(userId));
    verify('匿名修改资料与改密都要求登录',
        (await post('/api/auth/update-profile', { phone })).status === 401
        && (await post('/api/auth/change-password', { oldPassword: password, newPassword: 'abcdefg' })).status === 401);
    const first = await signIn();
    const second = await signIn();
    verify('同账号两个独立会话均有效', await valid(first) && await valid(second));
    const newPhone = '18' + suffix;
    await must(post('/api/auth/update-profile', { phone: newPhone }, first));
    phone = newPhone;
    const otherCurrent = await must(api('/api/auth/current', { jar: second }));
    verify('修改手机号保留两个会话并刷新资料', await valid(first) && await valid(second) && otherCurrent.phone === phone);

    await must(post('/api/admin/user-status', { userId, status: 'DISABLED' }, admin.jar));
    verify('禁用后拒绝新登录', !ok((await login(username, password)).result));
    // 在旧会话发起任何请求之前重新启用，保证验证主动撤销而非仅检查当前状态。
    await must(post('/api/admin/user-status', { userId, status: 'NORMAL' }, admin.jar));
    await revoked([first, second], '禁用后重新启用');
    const third = await signIn();
    const fourth = await signIn();
    verify('重新启用后新登录正常', await valid(third));

    const wrong = await post('/api/auth/change-password', { oldPassword: 'wrong', newPassword: 'wrong_should_not_apply' }, third);
    verify('错误旧密码不撤销任何会话', !ok(wrong) && await valid(third) && await valid(fourth));
    const oldPassword = password;
    password = 'session_changed_123';
    await must(post('/api/auth/change-password', { oldPassword, newPassword: password }, third));
    await revoked([third, fourth], '修改密码');
    verify('改密后旧密码不能登录', !ok((await login(username, oldPassword)).result));
    const fifth = await signIn();
    const sixth = await signIn();
    verify('改密后新密码可登录', await valid(fifth));

    const badReset = await post('/api/auth/reset-password', { username, code: 'invalid', newPassword: 'must_not_apply' });
    verify('失败的密码重置不撤销会话', !ok(badReset) && await valid(fifth) && await valid(sixth));
    await must(post('/api/auth/reset-request', { username, phone }));
    const resets = await must(api('/api/admin/reset-requests', { jar: admin.jar }));
    const reset = resets.find(item => item.username === username && item.status === 'PENDING');
    assert.ok(reset, '找回申请不存在');
    const approval = await must(post('/api/admin/reset-decide', { requestId: reset.id, agree: true }, admin.jar));
    const beforeReset = password;
    password = 'session_reset_123';
    // 使用匿名请求重置，确保撤销的是目标账号全部会话，而非提交请求的Session。
    await must(post('/api/auth/reset-password', { username, code: approval.code, newPassword: password }));
    await revoked([fifth, sixth], '匿名密码重置');
    verify('重置后原密码不能登录', !ok((await login(username, beforeReset)).result));
    const seventh = await signIn();
    const eighth = await signIn();
    verify('重置后新密码可登录', await valid(seventh));

    // 同时以旧密码改密，条件更新应只允许一方成功，且撤销两个请求的会话。
    const old = password;
    const options = ['session_race_A', 'session_race_B'];
    const races = await Promise.all([seventh, eighth].map((jar, i) =>
        post('/api/auth/change-password', { oldPassword: old, newPassword: options[i] }, jar)));
    verify('两个会话并发改密只有一个成功', races.filter(ok).length === 1);
    password = options[races.findIndex(ok)];
    await revoked([seventh, eighth], '并发修改密码');
    const finalSession = await signIn();
    verify('并发改密成功方的新密码可登录', await valid(finalSession));
    await must(post('/api/auth/logout', {}, finalSession));
    verify('退出后资料修改不复活会话', (await post('/api/auth/update-profile', { phone }, finalSession)).status === 401);
    console.log(`会话回归：通过 ${count} 项`);
} finally {
    try {
        if (userId) await must(post('/api/admin/user-status', { userId, status: 'DISABLED' }, admin.jar));
    } finally {
        await Promise.all([...sessions, admin.jar].map(jar => post('/api/auth/logout', {}, jar)));
    }
}
