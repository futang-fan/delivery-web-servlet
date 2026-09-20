# DeliveryWeb 接口约定表

> 仲裁规则：接口实现与本文档不一致时，以本文档为准；**改接口必须先改本文档**并通知对方。

## 0. 通用约定

- **Base URL**：`/DeliveryWeb`（下文路径均省略该前缀）。
- **请求体**：除 GET 外均为 `Content-Type: application/json`，字段名小驼峰。
- **统一响应体** `{code, msg, data}`：`code=200` 成功；业务失败 `code=400`（HTTP 仍为 200）。
- **HTTP 状态码**：
  - `200` 正常返回（含业务失败，看 body 里的 code）；
  - `401` 未登录或会话过期（AuthFilter 返回，前端 common.js 收到后自动跳登录页）；
  - `403` 已登录但角色不允许（AuthFilter 返回）；
  - `404` 接口不存在。`/api/**` 已注册前缀下的未知子路径统一为 HTTP 200 + 业务 fail("接口不存在")；仅未匹配任何 Servlet 的请求或静态资源缺失时返回 HTTP 404，并经 web.xml error-page 回落 `pages/error.html` 友好错误页（`500`/未捕获异常同此兜底，不再暴露 Tomcat 默认错误页）。【编码同步 2026-09-12】【2026-09-13】错误页自 webapp 根目录迁至 `pages/error.html`：PageAuthFilter 未声明 dispatcherTypes（默认仅拦 REQUEST 分发），容器 error-page 走 ERROR 分发不受其登录守卫影响；页内资源按“上下文路径+绝对路径”注入，避免错误转发下相对路径失配。
- **鉴权**：`/api/auth/**` 公开；其余 `/api/**` 必须登录（AuthFilter 默认拦截）；`/api/admin/**` 仅 ADMIN。归属类细分校验（本人订单、本店菜品等）在各 Service 内实现。
- **分页**：请求参数 `page`（默认 1）、`size`（默认 10，上限 50）；响应 data 为 `Page` 对象：`{page, size, total, pages, records}`。
- **枚举取值**：一律使用 Constants / common.js 字典（设计书表 2.16）。角色 `CUSTOMER/SHOP/RIDER/ADMIN`；订单状态 `UNPAID/PENDING_ACCEPT/PREPARING/PENDING_DELIVERY/DELIVERING/DELIVERED/COMPLETED/CANCELLED/REJECTED`；支付状态 `UNPAID/PAID/REFUNDED`；配送状态 `PENDING/ACCEPTED/PICKED/DELIVERING/DELIVERED`；审核状态 `PENDING/APPROVED/REJECTED`；营业状态 `OPEN/CLOSED`。

## 1. AuthServlet — /api/auth/**（✅ 全部接口）

| 接口 | 方法 | 角色 | 入参 | 出参 data | 说明 / 主要错误 |
|---|---|---|---|---|---|
| /api/auth/register | POST | 公开 | body: username, password, phone, role(CUSTOMER/SHOP) | null | 用户名 4-16 位字母数字下划线；密码≥6 位；手机号 11 位；角色仅顾客/商家；重复提示"用户名已存在"/"手机号已被注册"。SHOP 注册成功后自动生成待审核店铺记录（UserService 内） |
| /api/auth/login | POST | 公开 | body: username, password | `{id, username, role, phone, status}` | 错误统一"用户名或密码错误"（防枚举）；DISABLED 账号拒绝登录；成功建立 Session（30 分钟） |
| /api/auth/logout | POST | 放行 | - | null | 销毁 Session |
| /api/auth/current | GET | 公开 | - | 同 login 的用户对象（含 orderTimeoutMinutes） | 未登录返回 code=400"未登录"（HTTP 200）；orderTimeoutMinutes 为前端倒计时阈值单源下发（2026-09-12） |
| /api/auth/change-password | POST | 登录 | body: oldPassword, newPassword | null | ✅ 旧密码验证后修改；成功后销毁当前 Session 需重新登录；/api/auth 前缀被 AuthFilter 放行，登录态在 Servlet 内校验 |
| /api/auth/reset-request | POST | 公开 | body: username, phone | null | ✅ 忘记密码第一步（管理员协助找回，2026-09-12）：用户名+注册手机号匹配才受理，不匹配统一提示防枚举；同一用户 60 秒冷却；申请存内存 10 分钟过期 |
| /api/auth/reset-password | POST | 公开 | body: username, code, newPassword | null | ✅ 忘记密码第二步：凭管理员审批后下发的一次性验证码重置；验证码 5 分钟有效、错 5 次作废、一次性使用；成功后需重新登录 |
| /api/auth/update-profile | POST | 登录 | body: phone | 更新后的用户对象 | ✅ 修改手机号：11 位+未被其他账号占用；成功后同步刷新 Session 内手机号（不强制重登）；/api/auth 前缀放行，登录态在 Servlet 内校验 |

## 2. ShopServlet — /api/shop/**（✅ 全部接口）

> 编码补充（D3）：shop 表新增可空列 `contact`（联系人）、`qualification`（资质说明），承载设计书表 2.3 的入驻资料，表 2.9 原文未列；执行过的库需重新运行 `sql/init.sql`。

| 接口 | 方法 | 角色 | 入参 | 出参 data | 说明 / 主要错误 |
|---|---|---|---|---|---|
| /api/shop/apply | POST | SHOP | body: name(2-50字), contact, phone(11位), address, qualification | null | ✅ 入驻申请/整改重提（表 2.3 五字段）；注册时已自动生成待审核占位记录，APPROVED 后拒绝重复提交，PENDING 允许更新资料，REJECTED 整改重提后回到 PENDING |
| /api/shop/audit | POST | ADMIN | body: shopId, approved(boolean), reason(拒绝必填) | null | ✅ 仅 PENDING 可审（防重复审核）；拒绝必填原因，通过可留空；保存 audit_status/audit_reason/audited_at/auditor_id。因属 /api/shop 前缀，角色校验在 ShopServlet 内完成 |
| /api/shop/update | POST | SHOP（本店） | body: name, description, phone, address | null | ✅ 店铺资料维护（设计书 2.2.2）：归属校验，只能改自己的店铺；仅审核通过可维护 |
| /api/shop/business-status | POST | SHOP（本店） | body: businessStatus(OPEN/CLOSED) | null | ✅ 营业/休息切换；仅审核通过店铺可切换；休息店可浏览不可下单 |
| /api/shop/list | GET | 登录 | query: keyword(店名或菜品名), businessStatus, sort?(DEFAULT/RATING/SALES，缺省 DEFAULT，非法值 400), page, size | Page\<Shop\>（含 avgRating/reviewCount/salesCount 聚合字段） | ✅ 店铺搜索（设计书 2.2.2）：仅审核通过店铺；keyword 匹配店名或菜品名（dish 子查询）；"营业中排前"恒为第一排序键，sort 仅决定其后的次排序（RATING=评分、SALES=近 30 天已支付销量）；评分/评价数/销量为左连接聚合，无数据为 0 |
| /api/shop/my | GET | SHOP | - | Shop（含审核状态与拒绝原因） | ✅ P07 加载本人店铺；入驻资料未填写时 name 为空串，前端据此显示表单 |
| /api/shop/detail | GET | 登录 | query: shopId | Shop（含 ratingSummary：averageRating/reviewCount/star5-star1） | ✅ P03 店铺详情头部数据 + 评分摘要；无评价时平均 0、各星级 0。【2026-09-13 P2-1】字段投影：qualification/auditReason/auditorId 仅本店店主本人与 ADMIN 可见，其余登录用户返回 null |
| /api/shop/stats | GET | SHOP（本店） | query: startDate?, endDate? | {orderCount, validAmount, refundAmount, completedCount, hotDishRank} | ✅ 商家经营数据：仅本人店铺；有效交易额=PAID、退款金额=REFUNDED、完成订单数；日期含结束日整天；热销菜品按件数前 5 |
| /api/shop/stats/export | GET | SHOP（本店） | query: startDate?, endDate? | text/csv 附件（filename=shop-stats.csv） | ✅ 经营数据 CSV 导出：绕过 JSON 统一响应直写流；开头 UTF-8 BOM（\uFEFF）防 Excel 中文乱码；含逗号/引号/换行的单元格按 RFC4180 加引号转义；非商家 403 |

## 3. DishServlet — /api/dish/**（✅ 全部接口）

| 接口 | 方法 | 角色 | 入参 | 出参 data | 说明 / 主要错误 |
|---|---|---|---|---|---|
| /api/dish/save | POST | SHOP（本店） | body: id?, name(1-50字,同店唯一), price(正数,两位小数), stock(非负整数), imageUrl?, description(≤200字)? | {id} | ✅ 菜品新增/修改（设计书表 2.4）；id 有值为修改；新增默认 OFF_SALE；仅审核通过店铺可操作 |
| /api/dish/status | POST | SHOP（本店） | body: id, status(ON_SALE/OFF_SALE) | null | ✅ 上下架切换；下架即软删除，可重新上架 |
| /api/dish/my | GET | SHOP | - | List\<Dish\>（含下架） | ✅ 本店全部菜品（P08），归属校验，店铺须审核通过 |
| /api/dish/list | GET | 登录 | query: shopId | List\<Dish\>（仅上架，含 salesCount） | ✅ 店铺在售菜品（P03），顾客浏览用；salesCount=近 30 天已支付销量件数（左连接聚合，无销量为 0），供前端热销标 |
| /api/dish/search | GET | 登录 | query: keyword(菜品名/店铺名), shopId?, minPrice?, maxPrice?, page, size | Page\<Dish 含 shopName/businessStatus/salesCount\> | ✅ 顾客全局菜品搜索：仅在售+审核通过店铺，跨店分页，联表返回店铺名、营业状态与近 30 天已支付销量件数 |

## 4. AddressServlet — /api/address/**（✅ D4，顾客）

| 接口 | 方法 | 角色 | 入参 | 出参 data | 说明 / 主要错误 |
|---|---|---|---|---|---|
| /api/address/list | GET | 登录 | - | List\<Address\> | ✅ 本人地址列表（越权防护） |
| /api/address/save | POST | 登录 | body: id?, receiver, phone(11位), detail, isDefault(boolean) | {id} | ✅ 新增/修改；默认地址唯一由应用层保证（先清后设） |
| /api/address/delete | POST | 登录 | body: id | null | ✅ 只能删除自己的地址 |
| /api/address/set-default | POST | 登录 | body: id | null | ✅ 设默认前先清空旧默认 |

## 5. CartServlet — /api/cart/**（✅ D4，顾客，Session 存储不落库）

| 接口 | 方法 | 角色 | 入参 | 出参 data | 说明 / 主要错误 |
|---|---|---|---|---|---|
| /api/cart/list | GET | 登录 | - | {shopId, shopName, items:[CartItem...], totalAmount} | ✅ CartItem：{dishId, dishName, price, quantity, subtotal}；空购物车返回空结构 |
| /api/cart/add | POST | 登录 | body: dishId, quantity(≥1) | null | ✅ 仅上架且库存≥数量的菜品可加购；跨店加入提示"需先清空或切换购物车"（同店约束） |
| /api/cart/count | POST | 登录 | body: dishId, quantity | null | ✅ 数量增减，最小 1（删除走 remove） |
| /api/cart/remove | POST | 登录 | body: dishId | null | ✅ 删除单件 |
| /api/cart/clear | POST | 登录 | - | null | ✅ 清空；下单成功后由 OrderService 内部调用 |

## 6. OrderServlet — /api/order/**（✅ token/create/pay/cancel/list/accept/reject/ready/receive/detail；review 聚合 ✅ D8）

| 接口 | 方法 | 角色 | 入参 | 出参 data | 说明 / 主要错误 |
|---|---|---|---|---|---|
| /api/order/token | GET | CUSTOMER | - | {token} | ✅ 幂等令牌：Session 保存，创建订单时校验并销毁；重复签发以最后一次为准 |
| /api/order/create | POST | CUSTOMER | body: addressId, remark(≤100字)?, token | {orderId, orderNo} | ✅ 下单（设计书 2.2.4 + 表 2.5）：事务内校验店铺营业/菜品上架/库存→写主表+明细+行级扣库存→存地址与价格快照；初始 UNPAID；金额以服务端计算为准；令牌无效拒“订单令牌无效或已使用” |
| /api/order/pay | POST | 登录（归属） | body: orderId | null | ✅ 模拟支付：payStatus→PAID，order_status→PENDING_ACCEPT；条件更新防重复支付 |
| /api/order/cancel | POST | 登录（归属） | body: orderId | null | ✅ 仅 UNPAID 可取消：→CANCELLED，同事务恢复库存 |
| /api/order/accept | POST | SHOP（本店） | body: orderId | null | ✅ 接单：PENDING_ACCEPT→PREPARING，记录处理时间；归属校验在 Service |
| /api/order/reject | POST | SHOP（本店） | body: orderId, reason(必填) | null | ✅ 拒单：→REJECTED；同事务生成 SHOP_AGREED 退款记录、订单 payStatus→REFUNDED 并恢复库存；原因为空不能提交 |
| /api/order/ready | POST | SHOP（本店） | body: orderId | null | ✅ 备餐完成：PREPARING→PENDING_DELIVERY；同事务生成配送记录 PENDING，订单进入配送池 |
| /api/order/receive | POST | 登录（本人） | body: orderId | null | ✅ 确认收货：DELIVERED→COMPLETED；归属校验在 Service（findOwned）；条件更新防重复 |
| /api/order/list | GET | 登录（四角色） | query: status?, payStatus?, page, size | Page\<Order 含 shopName\> | ✅ 按角色自动过滤：顾客本人/商家本店/骑手承接/管理员全部；联表店铺返回 shopName 供列表展示（P09/P05 复用） |
| /api/order/detail | GET | 登录（归属） | query: orderId | OrderDetailVO{order(含 shopName/acceptedAt/preparedAt), items[], delivery, refund, review} | ✅ 归属校验：本人/本店/承接骑手/管理员；聚合明细快照+配送+退款+评价；状态时间线由各 *_at 拼装（C1 起含商家接单/备餐完成两节点） |

## 7. DeliveryServlet — /api/delivery/**（✅ D7 全部接口）

| 接口 | 方法 | 角色 | 入参 | 出参 data | 说明 / 主要错误 |
|---|---|---|---|---|---|
| /api/delivery/pool | GET | RIDER | query: page, size | Page\<DeliveryVO\> | ✅ 配送池：status=PENDING 待抢单；DeliveryVO={deliveryId, orderId, orderNo, orderStatus, totalAmount, payAmount, shopName, shopAddress(取餐), addressSnapshot(收货), riderId, status, 各节点时间戳, remark} |
| /api/delivery/grab | POST | RIDER | body: deliveryId | {deliveryId} | ✅ 抢单（DeliveryService.grab，设计书图 2.5）：事务内二次校验 + 条件更新 `WHERE status='PENDING'`，并发仅一人成功；失败提示“手慢了，订单已被抢走” |
| /api/delivery/node | POST | RIDER | body: deliveryId, status(PICKED=取餐动作/DELIVERED=送达动作) | null | ✅ 履约节点（对齐美团/饿了么真实流转）：取餐自动转配送状态 ACCEPTED→DELIVERING 并记 picked_at，订单 PENDING_DELIVERY→DELIVERING；送达转 DELIVERING→DELIVERED 并记 delivered_at，订单→DELIVERED；禁跳步；同事务完成 |
| /api/delivery/remark | POST | RIDER | body: deliveryId, remark(≤200字) | null | ✅ 配送说明/异常反馈；仅本人承接的配送可写 |
| /api/delivery/my | GET | RIDER | query: status?, page, size | Page\<DeliveryVO\> | ✅ 本人履约记录（归属校验）；进行中优先排序（配送中→已接单→待抢→已送达），同状态按更新时间新到老 |
| /api/delivery/stats | GET | RIDER | - | {todayDelivered, activeCount, deliveredTotal} | ✅ 骑手战绩卡（P10 顶部）：今日送达（delivered_at 当天）/在途（ACCEPTED+DELIVERING）/累计送达；纯聚合零新表 |
| /api/delivery/detail | GET | 登录（归属） | query: orderId | DeliveryVO | ✅ 顾客本人/本店商家/承接骑手/管理员可见，其余拒“无权查看”；无配送记录拒“该订单还没有配送记录” |

## 8. ReviewServlet — /api/review/**（✅ D8 全部接口）

| 接口 | 方法 | 角色 | 入参 | 出参 data | 说明 / 主要错误 |
|---|---|---|---|---|---|
| /api/review/submit | POST | 登录（本人） | body: orderId, rating(1-5), content(≤500字)? | null | ✅ 仅 COMPLETED 订单可评价；一单一次（业务查重 + 订单号唯一约束双保险）；shopId 取自订单 |
| /api/review/by-shop | GET | 登录 | query: shopId, page, size | Page\<Review\> | ✅ 店铺评价列表（P03 展示，新评价排前） |
| /api/review/exists | GET | 登录 | query: orderId | {reviewed} | ✅ 评价存在性查询，控制评价入口显隐。【2026-09-13 P2-2】补归属校验：非本人订单返回业务 fail（code=400，“无权查询他人订单的评价状态”）。【预留接口】当前前端零调用（评价入口显隐走订单详情的 review 载荷），保留作纵深防御 |

## 9. RefundServlet — /api/refund/**（✅ D7 全部接口；apply 自 D9 提前交付）

| 接口 | 方法 | 角色 | 入参 | 出参 data | 说明 / 主要错误 |
|---|---|---|---|---|---|
| /api/refund/apply | POST | 登录（本人） | body: orderId, reason(必填≤200字) | null | ✅ 退款申请：已支付且未完成（PENDING_ACCEPT~DELIVERED）；校验无既有退款记录；按实付金额全额；→PENDING。refund.order_id 有唯一约束（2026-09-12 审查 P0-1）；**申请后商家拒单的订单收敛为同一条 SHOP_AGREED 记录** |
| /api/refund/handle | POST | SHOP（本店） | body: refundId, agree(boolean), remark(选填) | null | ✅ 商家处理：条件更新 `WHERE status='PENDING'` 防重复处理；同意→SHOP_AGREED 且同事务订单 payStatus→REFUNDED（订单已是 REFUNDED 时幂等成功，2026-09-12）；拒绝→SHOP_REJECTED 终态，顾客不能再次申请 |
| /api/refund/pending | GET | SHOP（本店） | query: page, size | Page\<RefundVO\> | ✅ 本店待处理退款列表；RefundVO={refundId, orderId, orderNo, payAmount, reason, status, remark, createdAt} |
| /api/admin/refunds | GET | ADMIN | query: page, size | Page\<RefundVO 含 shopName/customerName/adminRemark\> | ✅ 全平台待处理退款列表（退款仲裁轻量版，P12 仲裁页签数据源）；早申请排前 |
| /api/admin/refund-decide | POST | ADMIN | body: refundId, agree(boolean，**必填，缺省/非法按驳回处理**——2026-09-12 审查 P1-3 由 fail-open 改为 fail-safe), adminRemark(驳回必填≤200字) | null | ✅ 平台裁定（退款仲裁轻量版）：同意→ADMIN_AGREED 并同事务订单 payStatus→REFUNDED；驳回→ADMIN_REJECTED 终态、理由必填、**不改动支付状态（不退款）**；均为条件更新 `WHERE status='PENDING'`，重复裁定/非 PENDING 拒 |
| /api/admin/reset-requests | GET | ADMIN | - | List\<ResetRequest\>（id/username/phoneMasked/createdAt/status/visibleCode） | ✅ 找回审批列表（2026-09-12）：内存存储的找回密码申请，新申请排前；visibleCode 仅已发码且未过期时返回 |
| /api/admin/reset-decide | POST | ADMIN | body: requestId, agree(boolean) | data: 同意时 {code} | ✅ 找回审批裁定：同意→生成 6 位一次性验证码（5 分钟有效、错 5 次作废）返回给管理端线下告知申请人；拒绝→申请作废；仅 PENDING 可裁定，重复裁定/非 PENDING 拒 |

## 10. AdminServlet — /api/admin/**（✅ D3-D9 全部接口，仅 ADMIN）

| 接口 | 方法 | 角色 | 入参 | 出参 data | 说明 / 主要错误 |
|---|---|---|---|---|---|
| /api/admin/users | GET | ADMIN | query: role?, status?, keyword?, page, size | Page\<User 脱敏\> | ✅ 用户列表（password 置 null 脱敏），含注册时间与角色；keyword 匹配用户名或手机号 |
| /api/admin/user-status | POST | ADMIN | body: userId, status(NORMAL/DISABLED) | null | ✅ 禁用/启用；被禁用账号不能登录；不能禁用当前登录账号 |
| /api/admin/create-rider | POST | ADMIN | body: username, password, phone | {id} | ✅ 创建骑手账号（角色固定 RIDER，不开放注册）；校验口径与注册一致 |
| /api/admin/shops | GET | ADMIN | query: auditStatus?, businessStatus?, page, size | Page\<Shop\> | ✅ 店铺列表（D3 审核区数据源，待审核排前） |
| /api/admin/shop-status | POST | ADMIN | body: shopId, businessStatus(CLOSED) | null | ✅ 强制暂停违规店铺营业；仅营业中店铺可被强制关闭，重复操作拒 |
| /api/admin/orders | GET | ADMIN | query: orderNo?, username?, shopName?, startDate?, endDate?, orderStatus?, page, size | Page\<Order 含 shopName/customerName\> | ✅ 全订单动态条件查询（设计书 2.2.5 订单监管）；日期区间含 endDate 当日 |
| /api/admin/orders/export | GET | ADMIN | query: 同 /api/admin/orders | text/csv 附件（filename=admin-orders.csv） | ✅ 监管留痕凭证：与订单查询同条件不分页（上限 5000 行）；CsvUtil 统一 BOM+RFC4180 转义；列=订单号/顾客/店铺/实付/支付状态/订单状态/下单时间。【2026-09-13 P3-2】新增响应头 X-Export-Truncated（0/1）与 X-Export-Rows（本次行数）；前端 fetch+Blob 下载，超上限页内提示截断 |
| /api/admin/stats/overview | GET | ADMIN | query: startDate?, endDate?（缺省=建库以来全量，含结束日整天） | {orderCount, totalAmount} | ✅ 平台统计：交易订单数、交易金额（已支付且未退款口径，REFUNDED 自然排除）；日期可选，P12 日期区间与环比（前端再查一次等长上一周期）依赖此参数 |
| /api/admin/stats/ranking | GET | ADMIN | query: startDate?, endDate?（缺省=全量） | {shopAmountRank, shopQuantityRank, dishQuantityRank, dishAmountRank: [{name,sales}...]} | ✅ 四口径排行一次返回（店铺交易额/店铺件数/菜品件数/菜品交易额），前端选口径切换展示、默认店铺交易额，各取前 5，纯聚合 SQL；日期条件落在 orders.created_at |

> 超时取消（设计书 2.2.4）：`OrderTimeoutListener`（controller 包，@WebListener）容器启动时启动守护定时器，每 60s 扫描超过 15 分钟（`Constants.ORDER_TIMEOUT_MINUTES`）未支付订单自动取消并释放库存；与 `/api/order/list` 进入时的惰性检查共用 `OrderService.cancelExpiredOrders()`，每笔独立事务条件流转。

## 11. 响应示例

```json
// POST /DeliveryWeb/api/auth/login 成功
{ "code": 200, "msg": "登录成功", "data": { "id": 3, "username": "shop1", "role": "SHOP", "phone": "13800000003", "status": "NORMAL" } }

// 未登录访问受保护接口（AuthFilter，HTTP 401）
{ "code": 400, "msg": "未登录或登录已过期", "data": null }

// 分页 data 结构（Page）
{ "page": 1, "size": 10, "total": 23, "pages": 3, "records": [ "...业务对象数组..." ] }
```
