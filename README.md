# DeliveryWeb —— 在线外卖点餐与配送调度平台

课程设计项目：顾客在线点餐、商家接单经营、骑手抢单配送、管理员审核监管的一站式外卖平台。

技术栈：JDK 8 · Tomcat 9.0.31 · Servlet 4.0 · MyBatis 3.5.13 · MySQL 8.0 · Vue 3 本地 ESM（无构建链）。

## 1. 环境要求

| 组件 | 版本 | 说明 |
|---|---|---|
| JDK | 8 | 需配置 `JAVA_HOME` 指向 JDK 8 安装目录 |
| Tomcat | 9.0.31 | 解压版即可，无需安装 |
| MySQL | 8.0 | root 账号，连接 `localhost:3306` |
| Node.js | 18+ | 仅运行测试脚本（`node -v` 验证），运行时不需要 |

## 2. 快速启动（五步）

1. **建库**：执行 `sql/init.sql`（删库重建，可反复执行）：
   ```bash
   mysql -uroot -p123456 --default-character-set=utf8mb4 < sql/init.sql
   ```
2. **打开工程**：IDEA 打开本目录，配置 Web Facet 与 Tomcat 运行配置（上下文路径 `/DeliveryWeb`）；或直接把 `webapp/` 内容同步到 Tomcat `webapps/DeliveryWeb`。
3. **编译依赖**：`webapp/WEB-INF/lib/` 已含全部 jar（jackson/mybatis/mysql-connector），无需下载。
4. **启动**：IDEA 运行配置启动 Tomcat，或 `catalina.bat run`。
5. **访问**：`http://localhost:8080/DeliveryWeb/pages/login.html`（端口以实际部署配置为准）。

> 修改 Java 代码后：IDEA 内 Reload All from Disk → Rebuild Project → 重启 Tomcat；只改页面/静态资源则浏览器 Ctrl+F5 强刷即可（静态资源有 5 分钟缓存）。

## 3. 种子账号（密码均为 `123456`）

| 账号 | 角色 | 用途 |
|---|---|---|
| admin | 管理员 | 商家审核、用户管理、店铺监管、订单查询、退款仲裁、找回审批、数据统计 |
| customer1 | 顾客 | 点餐主链路（种子含 7 笔各状态订单） |
| customer2 / customer3 | 顾客 | 越权测试 / **禁用演示态**（登录被拒） |
| shop1 | 商家 | 美味小馆（已审核、营业中），接单/拒单/经营数据 |
| shop2~shop5 | 商家 | 其他店铺状态演示（含待审核、被拒店铺） |
| rider1 / rider2 | 骑手 | 配送池抢单、配送节点 |

## 4. 自动化测试（零依赖，Node 18+ 原生 fetch）

```bash
# 鉴权矩阵（35 断言，只读不产生业务数据）
node test/auth-test.mjs http://localhost:8080/DeliveryWeb

# 冒烟主链路（默认取消链路）
node test/smoke-test.mjs http://localhost:8080/DeliveryWeb
# 含支付链路（会留 PENDING 订单并消耗库存）
node test/smoke-test.mjs http://localhost:8080/DeliveryWeb --with-pay

# 辅助检查（纯静态，无需启动 Tomcat / MySQL）
node test/dw-syntax-check.mjs          # 14 页内联脚本 + common.js 语法 + 18 条纯函数断言
node test/check-unused-css.mjs         # 无用样式交叉比对（应 UNUSED=0）
```

退出码 `0` 全部通过、`1` 存在失败，可直接接 CI。**跑测试前建议先复位 init.sql**（冒烟会产生业务数据，`--with-pay` 会消耗库存）。

## 5. GUI 冒烟检查单（浏览器手工过一遍）

| 角色 | 页面 | 关键点 |
|---|---|---|
| 未登录 | 登录页 | 毛玻璃卡渲染；忘记密码两步式（提交申请 → 提示联系管理员） |
| 未登录 | 任意不存在路径 | 回落 `pages/error.html` 友好错误页（非 Tomcat 默认页） |
| 顾客 | 店铺列表 | 店铺评分 ★ 展示、营业中排前、搜索/排序 |
| 顾客 | 店铺详情 → 加购 → 订单确认 | 售罄置灰禁购、热销标、购物车栏、金额正确（**不要真提交**可免脏数据） |
| 商家 | 商家中心 | 经营数据按日期查询、热销榜图表、导出 CSV |
| 商家 | 菜品管理 | 低库存琥珀行、售罄徽章、图例 |
| 骑手 | 配送池 | 今日战绩卡、可抢单角标 |
| 管理员 | 平台管理各页签 | 审核通过/拒绝、ECharts 图表、退款仲裁、找回审批（同意后展示验证码） |

主题：任意页顶栏 🌙 切换暗色，刷新后主题应保持（localStorage 记忆）。

## 6. 手工验证：未支付订单超时自动取消

自动化不覆盖（等待 15 分钟不现实）：

1. 临时把 `src/com/deliveryweb/util/Constants.java` 的 `ORDER_TIMEOUT_MINUTES` 改为 `1` 并重启 Tomcat；
2. 顾客下单不支付，等待 ≥ 70 秒；
3. 刷新"我的订单"，订单应变为已取消且库存恢复；
4. **验证完把阈值改回 `15` 并重启**。

## 7. 数据复位与常见问题

- **任何时候数据弄脏**：重跑 `sql/init.sql` 即回到全情境种子态（15 订单覆盖九态、退款三态、配送四态、评价 1~5 星）。
- 找回密码流程说明：用户提交申请（用户名+手机号匹配）→ 管理员在"找回审批"页签同意并获取一次性验证码 → **线下告知申请人** → 用户凭码重置。验证码 5 分钟有效、错 5 次作废；申请与验证码存内存，服务重启即清空。
- 接口契约与错误码：见 `docs/api.md`（实现与文档不一致以文档为准）。
