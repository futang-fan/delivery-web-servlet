# DeliveryWeb —— 在线外卖点餐与配送调度平台

课程设计项目：顾客在线点餐、商家接单经营、骑手抢单配送、管理员审核监管的一站式外卖平台。

技术栈：JDK 8 · Tomcat 9.0.31 · Servlet 4.0 · MyBatis 3.5.13 · MySQL 8.0 · Vue 3 本地 ESM（无构建链）。

## 1. 环境要求

| 组件 | 版本 | 说明 |
|---|---|---|
| JDK | 8 | 正常编译/运行使用 JDK 8；隔离回归脚本需 JDK 9+，编译目标仍为 Java 8 |
| Tomcat | 9.0.31 | 解压版即可，无需安装 |
| MySQL | 8.0 | 默认连接 `localhost:3306`；账号、密码及连接地址按本机配置填写 |
| Node.js | 18+ | 仅运行测试脚本（`node -v` 验证），运行时不需要 |

确保 `java`、`javac` 和 `mysql` 可从终端执行；未加入 PATH 时，使用对应程序的完整路径。

## 2. 快速启动（五步，无需 Maven）

以下命令在项目根目录的 PowerShell 中执行。

1. **准备数据库配置**：首次使用时复制样例，不覆盖已有配置：
   ```powershell
   if (!(Test-Path 'src/resources/mybatis-config.xml')) {
       Copy-Item 'src/resources/mybatis-config.xml.sample' 'src/resources/mybatis-config.xml'
   }
   ```
   编辑 `src/resources/mybatis-config.xml`，将 `CHANGE_ME` 替换为本机数据库密码，并核对 URL、用户名和数据库名 `DeliveryWeb_db`。此文件已被 `.gitignore` 排除。种子账号密码 `123456` 是应用登录密码，不是本机 MySQL 密码。
2. **建库**：仅在首次初始化或确认需要清空演示数据时执行；`init.sql` 会删除并重建 `DeliveryWeb_db`：
   ```powershell
   mysql -uroot -p --default-character-set=utf8mb4 --execute="source sql/init.sql"
   ```
   按提示输入本机 MySQL 密码；如使用其他数据库账号/端口，相应修改命令参数。
3. **编译并准备完整应用**：Java 依赖位于 `webapp/WEB-INF/lib/`；编译还需 Tomcat 自带的 `lib/servlet-api.jar`，该依赖由容器提供，不复制到应用中。下面使用 JDK 8，Tomcat 路径替换为自己的安装目录：
   ```powershell
   $deliveryTomcatHome = 'D:\Dev\Tools\apache-tomcat-9.0.31'
   $deliveryClasses = 'out/DeliveryWeb/WEB-INF/classes'
   New-Item -ItemType Directory -Force -Path $deliveryClasses | Out-Null
   Copy-Item -Path 'webapp/*' -Destination 'out/DeliveryWeb' -Recurse -Force
   $deliverySources = @(Get-ChildItem 'src/com' -Recurse -Filter '*.java' | ForEach-Object FullName)
   & javac -encoding UTF-8 -source 8 -target 8 -cp "webapp/WEB-INF/lib/*;$deliveryTomcatHome/lib/servlet-api.jar" -d $deliveryClasses $deliverySources
   if ($LASTEXITCODE -ne 0) { throw 'Java 编译失败，请勿部署' }
   Copy-Item 'src/resources/mybatis-config.xml' "$deliveryClasses/mybatis-config.xml" -Force
   ```
   JDK 9+ 编译时，将 `-source 8 -target 8` 换为 `--release 8`。最终 `out/DeliveryWeb/` 同时包含页面、JAR、`WEB-INF/classes/com/deliveryweb/` 及 `WEB-INF/classes/mybatis-config.xml`。后者必须位于 classpath 根目录，不能仅放在 `classes/resources/`。源码删除或改名后，应使用空的输出目录重新编译，避免残留旧 `.class`。

   使用 IDEA 时：将 `src/` 标为 Sources Root、`src/resources/` 标为 Resources Root；将上述 JAR 与 Servlet API 加入编译依赖；Web Facet 指向 `webapp/`，部署 Artifact 中将模块编译输出放入 `WEB-INF/classes/`，并确认配置文件位于该目录根部。
4. **部署并启动**：停止 Tomcat 后，用完整的 `out/DeliveryWeb/` 替换其 `webapps/DeliveryWeb/` 应用目录；或者在 IDEA 中部署上述 Artifact，上下文路径设为 `/DeliveryWeb`。再启动 Tomcat（例如运行其 `bin/catalina.bat run`）。只复制 `webapp/` 不包含 Java 编译产物，不能完成部署。
5. **访问**：`http://localhost:8080/DeliveryWeb/pages/login.html`（端口以实际部署配置为准）。

> 修改 Java 或数据库连接配置后，重新编译/复制配置并更新部署，再重启 Tomcat；只改静态资源时也需同步至部署目录，再 Ctrl+F5 刷新。本次会话修复部署后，原有登录会话需重新登录。

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

无需引入 Maven。Windows 下可用 `test/run-reliability.ps1` 编译并启动隔离的 Tomcat / MySQL，运行完整回归；参数及环境要求见 [测试说明](test/README.md)。该入口不修改原项目数据库。

```bash
# 鉴权矩阵（35 断言，含临时禁用/恢复账号及撤销会话）
node test/auth-test.mjs http://localhost:8080/DeliveryWeb

# 冒烟主链路（默认取消链路）
node test/smoke-test.mjs http://localhost:8080/DeliveryWeb
# 含支付链路（会留 PENDING 订单并消耗库存）
node test/smoke-test.mjs http://localhost:8080/DeliveryWeb --with-pay

# 辅助检查（纯静态，无需启动 Tomcat / MySQL）
node test/dw-syntax-check.mjs          # 14 页内联脚本 + common.js 语法 + 18 条纯函数断言
node test/check-unused-css.mjs         # 无用样式交叉比对（应 UNUSED=0）
```

鉴权、冒烟及语法检查退出码 `0` 表示通过、`1` 表示失败；CSS 检查输出候选项供查看。业务测试建议使用隔离入口，冒烟会产生数据并消耗库存。仅在专用演示/测试库中按需运行 `init.sql` 复位，不能用它保留并升级已有业务数据。

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

现有可靠性回归已覆盖过期支付取消与库存恢复；定时扫描及页面显示仍可按下面步骤单独观察：

1. 临时把 `src/com/deliveryweb/util/Constants.java` 的 `ORDER_TIMEOUT_MINUTES` 改为 `1` 并重启 Tomcat；
2. 顾客下单不支付，等待 ≥ 70 秒；
3. 刷新"我的订单"，订单应变为已取消且库存恢复；
4. **验证完把阈值改回 `15` 并重启**。

## 7. 数据复位与常见问题

- **演示数据复位**：确认库内数据可丢弃后，重跑 `sql/init.sql` 回到种子态；该操作删除原有业务数据。
- 找回密码流程说明：用户提交申请（用户名+手机号匹配）→ 管理员在"找回审批"页签同意并获取一次性验证码 → **线下告知申请人** → 用户凭码重置。验证码 5 分钟有效、错 5 次作废；申请与验证码存内存，服务重启即清空。
- 接口契约与错误码：见 `docs/api.md`（实现与文档不一致以文档为准）。
- 启动报 `mybatis-config.xml 加载失败`：检查已复制实际配置而非 `.sample`，且文件位于部署目录 `WEB-INF/classes/` 根部。数据库连接失败时，检查数据库服务、地址/端口、账号权限及密码。
- 已知待观察项：库存回滚回归曾有一次断言失败，增加请求结果、前后库存及订单数诊断后，后续四轮完整回归均通过，原因尚未确认。若再次发生，应根据诊断定位，不能仅靠重复运行判定问题已解决。
