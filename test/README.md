# DeliveryWeb 测试脚本

零依赖自动化测试（Node 18+ 原生 `fetch`，无需 npm install），覆盖冒烟与鉴权两类。

## 前置条件

1. Tomcat 已启动且应用部署于 `/DeliveryWeb`（IDEA 运行配置或 war 部署均可）；
2. `sql/init.sql` 已执行（种子账号 `admin / customer1 / shop1 / rider1`，密码均 `123456`）；
3. 安装 Node.js 18+（`node -v` 验证）。

## 运行方式

```powershell
# 默认基地址 http://localhost:8080/DeliveryWeb
node test/auth-test.mjs
node test/smoke-test.mjs

# 自定义基地址（如 war 部署到其他端口/上下文）
node test/smoke-test.mjs http://localhost:9090/DeliveryWeb

# 冒烟测试含支付链路（会留下一笔 PENDING_ACCEPT 订单并消耗 1 件库存）
node test/smoke-test.mjs --with-pay
```

退出码：`0` 全部通过，`1` 存在失败项。

### 辅助静态检查（不依赖 Tomcat / MySQL，无需先启动服务）

```powershell
# 前端静态校验：抽取 14 页内联 module 脚本 + common.js 逐个 node --check，
# 并对 common.js 中无 DOM 依赖的纯函数段跑 18 条断言（倒计时/取餐码/库存状态/快捷区间）
node test/dw-syntax-check.mjs

# 无用样式交叉比对：扫描 style.css 的 class 选择器与全部页面引用，输出 UNUSED 计数
node test/check-unused-css.mjs
```

期望输出结尾分别为 `ALL_SYNTAX_OK` + `ALL_HELPERS_OK`、`UNUSED=0`。

## 覆盖范围

| 脚本 | 覆盖点 |
|---|---|
| `auth-test.mjs` | 未登录 401（shop/order/admin/cart 四类前缀）；错误密码与不存在用户拒绝；四角色登录与角色断言；顾客/商家/管理员/骑手越权 403（管理端、订单令牌）；商家/顾客/骑手各自合法接口 200；退出登录后会话失效 401 |
| `smoke-test.mjs` | 10 个静态资源与页面可访问（含 vue.esm、style.css、login-bg.svg、error.html 错误页）；未登录拦截；customer1 登录；店铺/菜品/地址列表有数据；订单 A 加购→令牌→创建→幂等重放拒→**顾客 2 越权访问他人订单详情被拒（IDOR 归属校验）**→取消/支付；订单 B 支付→拒单（原因必填+退款记录+库存恢复，默认）或接单+备餐（`--with-pay`）+详情明细快照；订单 C（两模式均跑）：履约全链→退款闭环；订单 D：确认收货→评价（越界/重复拒）→exists/by-shop/详情聚合；管理员：用户列表脱敏→创建骑手→禁用/启用→禁自己拒→强制休息→重复拒→自恢复；订单查询筛选+统计概览+销量排行；收尾购物车清空 |

## 超时取消手工验证（自动化脚本不覆盖，等待 15 分钟阈值不现实）

1. 临时将 `Constants.ORDER_TIMEOUT_MINUTES` 改为 `1` 并重启 Tomcat；
2. 顾客下单不支付（P04 提交后不点支付），等待 ≥ 70 秒；
3. 刷新 P05 我的订单（惰性检查）或等待定时器扫描（Tomcat 日志输出“超时取消未支付订单 1 笔”），订单应变为已取消且菜品库存恢复；
4. 验证完毕将阈值改回 `15` 并重启。

## 数据影响说明

- `auth-test.mjs`：只读 + 登录/退出，**不产生业务数据**，可任意次执行；
- `smoke-test.mjs` 默认链路：订单 A 创建后取消、订单 B 支付后拒单，两者均恢复库存，仅留下 CANCELLED/REJECTED 订单与一条 SHOP_AGREED 退款记录，可反复执行；
- `smoke-test.mjs --with-pay`：订单 A 支付后留 PENDING_ACCEPT，订单 B 接单+备餐完成后留 PENDING_DELIVERY 订单与一条 PENDING 配送记录（可供 D7 骑手链路测试复用），并消耗 2 件库存；重复执行多次后如报“库存不足”，重跑 `init.sql` 复位种子数据即可。

## 文件说明

| 文件 | 职责 |
|---|---|
| `lib.mjs` | Cookie 罐（维持 Session）、`request/api/login` 客户端、`check/summary` 断言计数 |
| `smoke-test.mjs` | 冒烟测试主流程 |
| `auth-test.mjs` | 鉴权矩阵测试 |
| `dw-syntax-check.mjs` | 前端静态校验：抽取 `webapp/pages/` 下 14 页（`error.html` 无内联脚本，跳过）的内联 `module` 脚本与 `common.js`，逐个 `node --check`；再截取 `common.js` 中无 DOM 依赖的纯函数段，断言 `DEFAULT_ORDER_TIMEOUT_MINUTES` / `parseDateTime` / `payDeadline` / `formatCountdown` / `pickupCode` / `stockState` / `quickRange` 共 18 条 |
| `check-unused-css.mjs` | 无用样式交叉比对：提取 `webapp/common/style.css` 全部 class 选择器，与页面 HTML 及 `common.js` 文本比对，列出未被引用的 class 并输出 `TOTAL/UNUSED` 计数 |

> 两个辅助脚本均为**纯静态分析**，不访问网络与数据库，可在服务未启动时独立运行；执行过程中会在工程根目录 `out/check/` 下写入 `chk-*.mjs` 中间产物，可随时删除。

