-- =====================================================================
-- 在线外卖点餐与配送调度平台 数据库初始化脚本
-- 数据库：DeliveryWeb_db（MySQL 8.0，utf8mb4）
-- 依据：《需求分析与详细设计书》表2.7~表2.16（9 表 + 索引 + 种子数据）
-- 说明：脚本自带删库重建，可反复执行
-- 种子账号（密码均为 123456）：
--   admin 管理员；customer1/customer2 顾客（customer3 为 DISABLED 演示）；
--   shop1~shop5 商家；rider1/rider2 骑手。
-- 种子覆盖情境：店铺 APPROVED+OPEN / APPROVED+CLOSED / PENDING / REJECTED；菜品 在售 /
-- 低库存(<10) / 售罄(在售库存0) / 下架；订单九态 + 支付三态 + 退款三态；配送四态；
-- 评价 1~5 星（三店均分 4.5 / 3.5 / 2.0 供评分排序）；订单日期跨近 40 天，
-- 供统计日期区间、环比与"近 30 天销量"口径展示。
-- 两个硬约束：5 号店（美味小馆，shop1 所有）为 OPEN 中 id 最大者，即 /api/shop/list
-- 首条，冒烟测试以其菜品下单并由 shop1 接单；3 号订单送达时间距种子不足 24 小时，
-- 避免被"已送达超时自动完成"定时任务在演示前流转。
-- 密码存储格式"盐:摘要"，摘要 = SHA-256(盐 + 明文密码) 的小写十六进制，
-- 编码阶段 PasswordUtil 必须与此算法保持一致。
-- =====================================================================

DROP DATABASE IF EXISTS DeliveryWeb_db;
CREATE DATABASE DeliveryWeb_db DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE DeliveryWeb_db;

SET NAMES utf8mb4;

-- ---------------------------------------------------------------------
-- 1. user 用户信息表（表2.6）：四类角色统一账号
-- ---------------------------------------------------------------------
CREATE TABLE user (
    id         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '用户编号',
    username   VARCHAR(50)  NOT NULL                COMMENT '用户名',
    password   VARCHAR(100) NOT NULL                COMMENT '密码加密摘要（盐:摘要），禁止明文',
    role       VARCHAR(20)  NOT NULL                COMMENT '角色：CUSTOMER、SHOP、RIDER、ADMIN',
    phone      VARCHAR(20)      NULL                COMMENT '手机号',
    status     VARCHAR(20)  NOT NULL DEFAULT 'NORMAL' COMMENT '账户状态：NORMAL、DISABLED',
    created_at DATETIME     NOT NULL                COMMENT '注册时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_username (username),
    UNIQUE KEY uk_phone (phone),
    KEY idx_created_at (created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '用户信息表';

-- ---------------------------------------------------------------------
-- 2. address 收货地址表（表2.7）
-- ---------------------------------------------------------------------
CREATE TABLE address (
    id         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '地址编号',
    user_id    BIGINT       NOT NULL                COMMENT '所属用户编号，逻辑外键',
    receiver   VARCHAR(50)  NOT NULL                COMMENT '收货人姓名',
    phone      VARCHAR(20)  NOT NULL                COMMENT '收货人电话',
    detail     VARCHAR(200) NOT NULL                COMMENT '详细收货地址',
    is_default TINYINT      NOT NULL DEFAULT 0      COMMENT '是否默认地址：1 是、0 否',
    created_at DATETIME     NOT NULL                COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_user_id (user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '收货地址表';

-- ---------------------------------------------------------------------
-- 3. shop 店铺表（表2.8）：入驻审核状态与营业状态，一个商家对应一个店铺
--     编码补充：表2.3 入驻申请要求填写"联系人"与"资质说明"，表2.9 未含对应列，
--     此处增补两个可空列，不影响既有数据；执行过旧版脚本的库需重新执行本脚本。
-- ---------------------------------------------------------------------
CREATE TABLE shop (
    id              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '店铺编号',
    owner_id        BIGINT       NOT NULL                COMMENT '商家用户编号，逻辑外键，一商家对应一店铺',
    name            VARCHAR(100) NOT NULL                COMMENT '店铺名称',
    description     VARCHAR(500)     NULL                COMMENT '店铺简介',
    phone           VARCHAR(20)      NULL                COMMENT '联系电话',
    address         VARCHAR(200)     NULL                COMMENT '经营地址，兼作取餐地址',
    contact         VARCHAR(50)      NULL                COMMENT '联系人姓名',
    qualification   VARCHAR(200)     NULL                COMMENT '资质说明',
    audit_status    VARCHAR(20)  NOT NULL DEFAULT 'PENDING' COMMENT '审核状态：PENDING、APPROVED、REJECTED',
    audit_reason    VARCHAR(500)     NULL                COMMENT '审核原因，拒绝时必填',
    audited_at      DATETIME         NULL                COMMENT '审核时间',
    auditor_id      BIGINT           NULL                COMMENT '审核人编号，逻辑外键',
    business_status VARCHAR(20)  NOT NULL DEFAULT 'CLOSED' COMMENT '营业状态：OPEN、CLOSED',
    created_at      DATETIME     NOT NULL                COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_owner_id (owner_id),
    KEY idx_audit_status (audit_status),
    KEY idx_business_status (business_status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '店铺表';

-- ---------------------------------------------------------------------
-- 4. dish 菜品表（表2.9）：同店名称唯一
-- ---------------------------------------------------------------------
CREATE TABLE dish (
    id          BIGINT        NOT NULL AUTO_INCREMENT COMMENT '菜品编号',
    shop_id     BIGINT        NOT NULL                COMMENT '所属店铺编号，逻辑外键',
    name        VARCHAR(100)  NOT NULL                COMMENT '菜品名称',
    description VARCHAR(500)      NULL                COMMENT '菜品描述',
    price       DECIMAL(10,2) NOT NULL DEFAULT 0.00   COMMENT '售价，非负，单位为元',
    stock       INT           NOT NULL DEFAULT 0      COMMENT '库存数量，非负',
    image_url   VARCHAR(200)      NULL                COMMENT '菜品图片地址',
    status      VARCHAR(20)   NOT NULL DEFAULT 'OFF_SALE' COMMENT '上下架状态：ON_SALE、OFF_SALE',
    created_at  DATETIME      NOT NULL                COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_shop_name (shop_id, name),
    KEY idx_shop_status (shop_id, status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '菜品表';

-- ---------------------------------------------------------------------
-- 5. orders 订单主表（表2.10）：对外展示使用 order_no 订单号
-- ---------------------------------------------------------------------
CREATE TABLE orders (
    id               BIGINT        NOT NULL AUTO_INCREMENT COMMENT '订单编号，仅内部关联用',
    order_no         VARCHAR(32)   NOT NULL                COMMENT '订单号，对外展示和查询',
    customer_id      BIGINT        NOT NULL                COMMENT '顾客编号，逻辑外键',
    shop_id          BIGINT        NOT NULL                COMMENT '店铺编号，逻辑外键',
    address_snapshot VARCHAR(300)  NOT NULL                COMMENT '收货地址快照，格式：收货人，电话，详细地址',
    total_amount     DECIMAL(10,2) NOT NULL DEFAULT 0.00   COMMENT '商品总金额',
    pay_amount       DECIMAL(10,2) NOT NULL DEFAULT 0.00   COMMENT '实付金额，无优惠活动时等于商品总金额',
    pay_status       VARCHAR(20)   NOT NULL DEFAULT 'UNPAID' COMMENT '支付状态：UNPAID、PAID、REFUNDED',
    paid_at          DATETIME          NULL                COMMENT '支付时间',
    accepted_at      DATETIME          NULL                COMMENT '商家接单时间（C1 时间线补全，状态流转时写入）',
    prepared_at      DATETIME          NULL                COMMENT '备餐完成时间（C1 时间线补全，状态流转时写入）',
    order_status     VARCHAR(20)   NOT NULL DEFAULT 'UNPAID' COMMENT '订单状态，取值见表1.7',
    remark           VARCHAR(200)      NULL                COMMENT '订单备注',
    created_at       DATETIME      NOT NULL                COMMENT '下单时间',
    updated_at       DATETIME      NOT NULL                COMMENT '最后更新时间，状态变更时刷新',
    PRIMARY KEY (id),
    UNIQUE KEY uk_order_no (order_no),
    KEY idx_customer_id (customer_id),
    KEY idx_shop_id (shop_id),
    KEY idx_order_status (order_status),
    KEY idx_shop_order_status (shop_id, order_status),
    KEY idx_created_at (created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '订单主表';

-- ---------------------------------------------------------------------
-- 6. order_item 订单明细表（表2.11）：菜品名称与价格快照
-- ---------------------------------------------------------------------
CREATE TABLE order_item (
    id               BIGINT        NOT NULL AUTO_INCREMENT COMMENT '明细编号',
    order_id         BIGINT        NOT NULL                COMMENT '所属订单编号，逻辑外键',
    dish_id          BIGINT        NOT NULL                COMMENT '菜品编号，逻辑外键',
    dish_name_snapshot VARCHAR(100) NOT NULL               COMMENT '下单时菜品名称快照',
    price_snapshot   DECIMAL(10,2) NOT NULL DEFAULT 0.00   COMMENT '下单时价格快照',
    quantity         INT           NOT NULL DEFAULT 1      COMMENT '购买数量，大于零',
    subtotal         DECIMAL(10,2) NOT NULL DEFAULT 0.00   COMMENT '小计金额，价格快照乘以数量',
    PRIMARY KEY (id),
    KEY idx_order_id (order_id),
    KEY idx_dish_id (dish_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '订单明细表';

-- ---------------------------------------------------------------------
-- 7. delivery 配送记录表（表2.12）：一单一记录，五段配送状态
-- ---------------------------------------------------------------------
CREATE TABLE delivery (
    id           BIGINT      NOT NULL AUTO_INCREMENT COMMENT '配送记录编号',
    order_id     BIGINT      NOT NULL                COMMENT '所属订单编号，唯一约束，一单一记录',
    rider_id     BIGINT          NULL                COMMENT '骑手编号，抢单前为空，逻辑外键',
    status       VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '配送状态：PENDING、ACCEPTED、DELIVERING、DELIVERED（取餐为动作，自动转入 DELIVERING）',
    accepted_at  DATETIME        NULL                COMMENT '抢单时间',
    picked_at    DATETIME        NULL                COMMENT '取餐时间',
    delivered_at DATETIME        NULL                COMMENT '送达时间',
    remark       VARCHAR(200)    NULL                COMMENT '配送说明',
    updated_at   DATETIME    NOT NULL                COMMENT '最后更新时间，节点变更时刷新',
    PRIMARY KEY (id),
    UNIQUE KEY uk_order_id (order_id),
    KEY idx_rider_id (rider_id),
    KEY idx_status (status),
    KEY idx_status_updated (status, updated_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '配送记录表';

-- ---------------------------------------------------------------------
-- 8. review 评价表（表2.13）：订单号唯一约束保证一单一次
-- ---------------------------------------------------------------------
CREATE TABLE review (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '评价编号',
    order_id    BIGINT       NOT NULL                COMMENT '所属订单编号，唯一约束保证一单一次',
    customer_id BIGINT       NOT NULL                COMMENT '顾客编号，逻辑外键',
    shop_id     BIGINT       NOT NULL                COMMENT '店铺编号，便于店铺维度统计评分，逻辑外键',
    rating      TINYINT      NOT NULL                COMMENT '评分，1 至 5 星',
    content     VARCHAR(500)     NULL                COMMENT '评价文字',
    created_at  DATETIME     NOT NULL                COMMENT '评价时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_order_id (order_id),
    KEY idx_customer_id (customer_id),
    KEY idx_shop_id (shop_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '评价表';

-- ---------------------------------------------------------------------
-- 9. refund 退款记录表（表2.14）：三态状态
-- ---------------------------------------------------------------------
CREATE TABLE refund (
    id         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '退款记录编号',
    order_id   BIGINT       NOT NULL                COMMENT '所属订单编号，逻辑外键',
    reason     VARCHAR(200) NOT NULL                COMMENT '退款原因',
    status     VARCHAR(20)  NOT NULL DEFAULT 'PENDING' COMMENT '退款状态：PENDING、SHOP_AGREED、SHOP_REJECTED、ADMIN_AGREED',
    remark     VARCHAR(200)     NULL                COMMENT '退款处理说明',
    admin_remark VARCHAR(200)   NULL                COMMENT '平台裁定说明（退款仲裁轻量版，ADMIN_AGREED 时写入）',
    created_at DATETIME     NOT NULL                COMMENT '申请时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_order_id (order_id),
    KEY idx_status (status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '退款记录表';

-- =====================================================================
-- 种子数据
-- =====================================================================

-- 用户（密码均为 123456，格式 盐:SHA-256(盐+明文)；customer3 为禁用态演示）
INSERT INTO user (id, username, password, role, phone, status, created_at) VALUES
(1,  'admin',     '9f8e7d6c5b4a3928:530ac1e892b6d1ef5eeef03f785445bd7b43043ddcf05e3efd772f2257bb0d52', 'ADMIN',    '13800000001', 'NORMAL',   '2026-08-01 09:00:00'),
(2,  'customer1', '1a2b3c4d5e6f7081:ebe10a224f1b670dba8325df90ebc56a98a78f44f13dbf4b5bc7798240ba9e04', 'CUSTOMER', '13800000002', 'NORMAL',   '2026-08-02 10:00:00'),
(3,  'shop1',     'fedcba9876543210:5788e45c33e20185762eedf598e4e9018ab64031728ef9e8eb0931a877786575', 'SHOP',     '13800000003', 'NORMAL',   '2026-08-03 11:00:00'),
(4,  'rider1',    '0123456789abcdef:bd40fff07c089100cc7e65ee68a59d4529f993c03eb964ddb0c5763b1b88cc78', 'RIDER',    '13800000004', 'NORMAL',   '2026-08-04 12:00:00'),
(5,  'customer2', '0cda5d3e6e2a17b5:a91922f8699473fd55a5ac533ddf5564d54242ffae04ada97ea589c9bd256f5a', 'CUSTOMER', '13800000005', 'NORMAL',   '2026-08-05 09:30:00'),
(6,  'shop2',     '953b7f1f80e48139:bda43b5853f4cbf0b2d201f5f5d35b2471130a3a9d3b19935deab6dd7c5cef55', 'SHOP',     '13800000006', 'NORMAL',   '2026-08-06 10:30:00'),
(7,  'shop3',     'd0cc72ae638db7cb:ebab00855cc79fe9088fd044d5e0fa248187ba6b014d220d8aedb84b80632079', 'SHOP',     '13800000007', 'NORMAL',   '2026-08-07 11:30:00'),
(8,  'rider2',    '964c60ebd041fa9e:c3eabbcf1f0339e6c0c7a00906bc886a2a940a4e18b68230ff20cb66b1bf6e66', 'RIDER',    '13800000008', 'NORMAL',   '2026-08-08 12:30:00'),
(9,  'shop4',     '14677715a511818e:8bb68e3ea3f08e743862e76e1cb3261178e1b8d14b15824d8c2ce445acf0be62', 'SHOP',     '13800000009', 'NORMAL',   '2026-08-09 09:00:00'),
(10, 'shop5',     '711f082b0c356bdc:7837cd2acb711960bd9b4aa7192588a0ea3555e8012c133c49c030bb2a6c395f', 'SHOP',     '13800000010', 'NORMAL',   '2026-08-10 10:00:00'),
(11, 'customer3', '5f1db7bb6f1fc845:21c8aa67b503b9d6956436ed1b9ad9d1c611adc9b988ac75e9a68975272e908b', 'CUSTOMER', '13800000011', 'DISABLED', '2026-08-11 11:00:00');

-- 店铺：覆盖 APPROVED+OPEN / APPROVED+CLOSED / PENDING / REJECTED 四种情境；
-- 5 号美味小馆为 shop1 所有且是 OPEN 中 id 最大者（/api/shop/list 默认首条）
INSERT INTO shop (id, owner_id, name, contact, qualification, description, phone, address, audit_status, audit_reason, audited_at, auditor_id, business_status, created_at) VALUES
(1, 6,  '川香居',     '周老板', '营业执照与食品经营许可证齐全，后厨明档可视。',   '正宗川味小炒，麻辣鲜香，现点现做。',     '13800000006', '锦江路 12 号临街铺面 2 层',   'APPROVED', NULL, '2026-08-08 09:00:00', 1, 'OPEN',   '2026-08-06 10:00:00'),
(2, 7,  '轻食沙拉屋', '吴店长', '营业执照与食品经营许可证齐全，冷链配送达标。',   '低卡轻食与冷压果汁，健身人群首选。',     '13800000007', '科技园南路 5 号食堂 1 层 8 档', 'APPROVED', NULL, '2026-08-09 09:00:00', 1, 'CLOSED', '2026-08-07 11:00:00'),
(3, 9,  '山野烘焙坊', '郑师傅', '营业执照已提交，食品经营许可证办理中。',         '每日现烤欧包与可颂，无添加防腐剂。',     '13800000009', '青山东路 33 号 1 层',         'PENDING',  NULL, NULL, NULL, 'CLOSED', '2026-08-10 09:00:00'),
(4, 10, '街角快餐',   '孙经理', '营业执照在有效期内，食品经营许可证已过期。',     '快餐简餐，出餐快，价格实惠。',           '13800000010', '城北站西广场 3 号门面',       'REJECTED', '食品经营许可证已过期，请更新后重新提交', '2026-08-11 15:00:00', 1, 'CLOSED', '2026-08-10 14:00:00'),
(5, 3,  '美味小馆',   '王师傅', '营业执照与食品经营许可证齐全，门店面积 60 平方米。', '家常菜现炒现做，出餐快，欢迎品尝。', '13800000003', '幸福路 88 号美食城 1 层 101', 'APPROVED', NULL, '2026-08-05 09:00:00', 1, 'OPEN',   '2026-08-03 11:30:00');

-- 菜品：三店共 17 道，覆盖 在售 / 低库存(<10) / 售罄(在售但库存0) / 下架 四种情境
INSERT INTO dish (id, shop_id, name, description, price, stock, image_url, status, created_at) VALUES
(1,  5, '宫保鸡丁',   '经典川菜，微辣下饭。',         28.00, 50,  NULL, 'ON_SALE',  '2026-08-05 10:00:00'),
(2,  5, '鱼香肉丝',   '酸甜微辣，米饭搭档。',         26.00, 8,   NULL, 'ON_SALE',  '2026-08-05 10:05:00'),
(3,  5, '麻婆豆腐',   '麻辣烫嫩，素菜价格荤菜口感。', 22.00, 0,   NULL, 'ON_SALE',  '2026-08-05 10:10:00'),
(4,  5, '米饭',       '东北大米，现蒸。',             2.00,  200, NULL, 'ON_SALE',  '2026-08-05 10:15:00'),
(5,  5, '可乐',       '冰镇罐装 330ml。',             5.00,  100, NULL, 'ON_SALE',  '2026-08-05 10:20:00'),
(6,  5, '酸辣汤',     '季节性供应，暂停售卖。',       12.00, 0,   NULL, 'OFF_SALE', '2026-08-05 10:25:00'),
(7,  1, '水煮鱼',     '草鱼现杀，麻辣汤底。',         58.00, 30,  NULL, 'ON_SALE',  '2026-08-08 10:00:00'),
(8,  1, '毛血旺',     '鸭血毛肚黄喉一锅鲜。',         48.00, 12,  NULL, 'ON_SALE',  '2026-08-08 10:05:00'),
(9,  1, '夫妻肺片',   '凉拌牛肉牛杂，红油提香。',     32.00, 6,   NULL, 'ON_SALE',  '2026-08-08 10:10:00'),
(10, 1, '担担面',     '芝麻酱与芽菜打底，微辣。',     18.00, 80,  NULL, 'ON_SALE',  '2026-08-08 10:15:00'),
(11, 1, '红糖冰粉',   '手搓冰粉，红糖醪糟配料。',     8.00,  40,  NULL, 'ON_SALE',  '2026-08-08 10:20:00'),
(12, 1, '泡椒鸡杂',   '泡椒风味重，暂不供应。',       36.00, 5,   NULL, 'OFF_SALE', '2026-08-08 10:25:00'),
(13, 2, '凯撒沙拉',   '罗马生菜配帕玛森与面包丁。',   26.00, 25,  NULL, 'ON_SALE',  '2026-08-09 10:00:00'),
(14, 2, '鸡胸肉卷',   '低温慢煮鸡胸，全麦卷饼。',     24.00, 18,  NULL, 'ON_SALE',  '2026-08-09 10:05:00'),
(15, 2, '藜麦能量碗', '三色藜麦配烤蔬菜与油醋汁。',   32.00, 9,   NULL, 'ON_SALE',  '2026-08-09 10:10:00'),
(16, 2, '冷压橙汁',   '当日冷压，无添加糖。',         15.00, 60,  NULL, 'ON_SALE',  '2026-08-09 10:15:00'),
(17, 2, '牛油果吐司', '牛油果泥配水波蛋，今日售罄。', 20.00, 0,   NULL, 'ON_SALE',  '2026-08-09 10:20:00');

-- 收货地址：customer1 默认+备用、customer2 一条
INSERT INTO address (id, user_id, receiver, phone, detail, is_default, created_at) VALUES
(1, 2, '小明', '13800000002', '大学城南区 6 号楼 305 宿舍',   1, '2026-08-06 09:00:00'),
(2, 2, '小明', '13800000002', '实验楼 B 座 3 层 305 工位',    0, '2026-08-20 09:30:00'),
(3, 5, '李婷', '13800000005', '高新区天府三街 199 号 3 栋 1204', 1, '2026-08-12 10:00:00');

-- 订单 1（COMPLETED+PAID，40 天前）：水煮鱼x1 + 担担面x1 = 76.00
INSERT INTO orders (id, order_no, customer_id, shop_id, address_snapshot, total_amount, pay_amount, pay_status, paid_at, accepted_at, prepared_at, order_status, remark, created_at, updated_at) VALUES
(1, '20260802120000120001', 5, 1, '李婷，13800000005，高新区天府三街 199 号 3 栋 1204', 76.00, 76.00, 'PAID', '2026-08-02 12:00:05', '2026-08-02 12:12:00', '2026-08-02 12:14:00', 'COMPLETED', '鱼要微辣', '2026-08-02 12:00:00', '2026-08-02 12:40:00');

-- 订单 2（COMPLETED+PAID，25 天前）：米饭x1 + 宫保鸡丁x1 = 30.00
INSERT INTO orders (id, order_no, customer_id, shop_id, address_snapshot, total_amount, pay_amount, pay_status, paid_at, accepted_at, prepared_at, order_status, remark, created_at, updated_at) VALUES
(2, '20260817180000345002', 2, 5, '小明，13800000002，大学城南区 6 号楼 305 宿舍', 30.00, 30.00, 'PAID', '2026-08-17 18:00:05', '2026-08-17 18:10:00', '2026-08-17 18:12:00', 'COMPLETED', NULL, '2026-08-17 18:00:00', '2026-08-17 18:45:00');

-- 订单 3（DELIVERED+PAID，送达不足 24 小时待确认收货）：鱼香肉丝x2 + 米饭x1 = 54.00
INSERT INTO orders (id, order_no, customer_id, shop_id, address_snapshot, total_amount, pay_amount, pay_status, paid_at, accepted_at, prepared_at, order_status, remark, created_at, updated_at) VALUES
(3, '20260908120000210003', 5, 5, '李婷，13800000005，高新区天府三街 199 号 3 栋 1204', 54.00, 54.00, 'PAID', '2026-09-08 12:00:05', '2026-09-08 12:15:00', '2026-09-08 12:18:00', 'DELIVERED', NULL, '2026-09-08 12:00:00', '2026-09-11 08:00:00');

-- 订单 4（DELIVERING+PAID，1 天前）：水煮鱼x1 + 红糖冰粉x2 = 74.00
INSERT INTO orders (id, order_no, customer_id, shop_id, address_snapshot, total_amount, pay_amount, pay_status, paid_at, accepted_at, prepared_at, order_status, remark, created_at, updated_at) VALUES
(4, '20260910120000455004', 2, 1, '小明，13800000002，大学城南区 6 号楼 305 宿舍', 74.00, 74.00, 'PAID', '2026-09-10 12:00:05', '2026-09-10 12:18:00', '2026-09-10 12:22:00', 'DELIVERING', '放门口即可', '2026-09-10 12:00:00', '2026-09-10 12:35:00');

-- 订单 5（PENDING_DELIVERY+PAID，配送待抢单）：凯撒沙拉x1 + 冷压橙汁x1 = 41.00
INSERT INTO orders (id, order_no, customer_id, shop_id, address_snapshot, total_amount, pay_amount, pay_status, paid_at, accepted_at, prepared_at, order_status, remark, created_at, updated_at) VALUES
(5, '20260911060000118005', 5, 2, '李婷，13800000005，高新区天府三街 199 号 3 栋 1204', 41.00, 41.00, 'PAID', '2026-09-11 06:00:05', '2026-09-11 06:10:00', '2026-09-11 06:20:00', 'PENDING_DELIVERY', NULL, '2026-09-11 06:00:00', '2026-09-11 06:20:00');

-- 订单 6（PREPARING+PAID，备餐中）：鸡胸肉卷x1 = 24.00
INSERT INTO orders (id, order_no, customer_id, shop_id, address_snapshot, total_amount, pay_amount, pay_status, paid_at, accepted_at, prepared_at, order_status, remark, created_at, updated_at) VALUES
(6, '20260911090000322006', 2, 2, '小明，13800000002，实验楼 B 座 3 层 305 工位', 24.00, 24.00, 'PAID', '2026-09-11 09:00:05', '2026-09-11 09:05:00', NULL, 'PREPARING', NULL, '2026-09-11 09:00:00', '2026-09-11 09:12:00');

-- 订单 7（PENDING_ACCEPT+PAID，待接单角标数据）：担担面x1 = 18.00
INSERT INTO orders (id, order_no, customer_id, shop_id, address_snapshot, total_amount, pay_amount, pay_status, paid_at, accepted_at, prepared_at, order_status, remark, created_at, updated_at) VALUES
(7, '20260911100000207007', 5, 1, '李婷，13800000005，高新区天府三街 199 号 3 栋 1204', 18.00, 18.00, 'PAID', '2026-09-11 10:00:05', NULL, NULL, 'PENDING_ACCEPT', NULL, '2026-09-11 10:00:00', '2026-09-11 10:00:05');

-- 订单 8（UNPAID，待支付倒计时演示；超 15 分钟由定时任务自动取消）：米饭x1 = 2.00
INSERT INTO orders (id, order_no, customer_id, shop_id, address_snapshot, total_amount, pay_amount, pay_status, paid_at, accepted_at, prepared_at, order_status, remark, created_at, updated_at) VALUES
(8, '20260911113000486008', 2, 5, '小明，13800000002，大学城南区 6 号楼 305 宿舍', 2.00, 2.00, 'UNPAID', NULL, NULL, NULL, 'UNPAID', NULL, '2026-09-11 11:30:00', '2026-09-11 11:30:00');

-- 订单 9（CANCELLED+UNPAID，超时自动取消）：冷压橙汁x1 = 15.00
INSERT INTO orders (id, order_no, customer_id, shop_id, address_snapshot, total_amount, pay_amount, pay_status, paid_at, accepted_at, prepared_at, order_status, remark, created_at, updated_at) VALUES
(9, '20260906200000153009', 5, 2, '李婷，13800000005，高新区天府三街 199 号 3 栋 1204', 15.00, 15.00, 'UNPAID', NULL, NULL, NULL, 'CANCELLED', NULL, '2026-09-06 20:00:00', '2026-09-06 20:16:00');

-- 订单 10（REJECTED+REFUNDED，拒单退款闭环）：毛血旺x1 = 48.00
INSERT INTO orders (id, order_no, customer_id, shop_id, address_snapshot, total_amount, pay_amount, pay_status, paid_at, accepted_at, prepared_at, order_status, remark, created_at, updated_at) VALUES
(10, '20260903120000264010', 2, 1, '小明，13800000002，大学城南区 6 号楼 305 宿舍', 48.00, 48.00, 'REFUNDED', '2026-09-03 12:00:05', NULL, NULL, 'REJECTED', NULL, '2026-09-03 12:00:00', '2026-09-03 12:30:00');

-- 订单 11（COMPLETED+PAID，12 天前）：凯撒沙拉x1 + 鸡胸肉卷x1 = 50.00
INSERT INTO orders (id, order_no, customer_id, shop_id, address_snapshot, total_amount, pay_amount, pay_status, paid_at, accepted_at, prepared_at, order_status, remark, created_at, updated_at) VALUES
(11, '20260830120000371011', 5, 2, '李婷，13800000005，高新区天府三街 199 号 3 栋 1204', 50.00, 50.00, 'PAID', '2026-08-30 12:00:05', '2026-08-30 12:10:00', '2026-08-30 12:14:00', 'COMPLETED', NULL, '2026-08-30 12:00:00', '2026-08-30 12:50:00');

-- 订单 12（PENDING_DELIVERY+PAID，骑手已接单未取餐）：藜麦能量碗x1 = 32.00
INSERT INTO orders (id, order_no, customer_id, shop_id, address_snapshot, total_amount, pay_amount, pay_status, paid_at, accepted_at, prepared_at, order_status, remark, created_at, updated_at) VALUES
(12, '20260909120000428012', 2, 2, '小明，13800000002，实验楼 B 座 3 层 305 工位', 32.00, 32.00, 'PAID', '2026-09-09 12:00:05', '2026-09-09 12:10:00', '2026-09-09 12:18:00', 'PENDING_DELIVERY', NULL, '2026-09-09 12:00:00', '2026-09-09 12:20:00');

-- 订单 13（COMPLETED+PAID，22 天前）：水煮鱼x2 = 116.00
INSERT INTO orders (id, order_no, customer_id, shop_id, address_snapshot, total_amount, pay_amount, pay_status, paid_at, accepted_at, prepared_at, order_status, remark, created_at, updated_at) VALUES
(13, '20260820190000516013', 5, 1, '李婷，13800000005，高新区天府三街 199 号 3 栋 1204', 116.00, 116.00, 'PAID', '2026-08-20 19:00:05', '2026-08-20 19:12:00', '2026-08-20 19:16:00', 'COMPLETED', '多加一份蘸碟', '2026-08-20 19:00:00', '2026-08-20 19:50:00');

-- 订单 14（COMPLETED+PAID，17 天前，含售罄菜的历史快照）：宫保鸡丁x1 + 麻婆豆腐x1 = 50.00
INSERT INTO orders (id, order_no, customer_id, shop_id, address_snapshot, total_amount, pay_amount, pay_status, paid_at, accepted_at, prepared_at, order_status, remark, created_at, updated_at) VALUES
(14, '20260825120000602014', 2, 5, '小明，13800000002，大学城南区 6 号楼 305 宿舍', 50.00, 50.00, 'PAID', '2026-08-25 12:00:05', '2026-08-25 12:12:00', '2026-08-25 12:14:00', 'COMPLETED', NULL, '2026-08-25 12:00:00', '2026-08-25 12:40:00');

-- 订单 15（COMPLETED+PAID，10 天前）：凯撒沙拉x1 = 26.00
INSERT INTO orders (id, order_no, customer_id, shop_id, address_snapshot, total_amount, pay_amount, pay_status, paid_at, accepted_at, prepared_at, order_status, remark, created_at, updated_at) VALUES
(15, '20260901120000739015', 5, 2, '李婷，13800000005，高新区天府三街 199 号 3 栋 1204', 26.00, 26.00, 'PAID', '2026-09-01 12:00:05', '2026-09-01 12:12:00', '2026-09-01 12:14:00', 'COMPLETED', NULL, '2026-09-01 12:00:00', '2026-09-01 12:45:00');

INSERT INTO order_item (id, order_id, dish_id, dish_name_snapshot, price_snapshot, quantity, subtotal) VALUES
(1,  1,  7,  '水煮鱼',     58.00, 1, 58.00),
(2,  1,  10, '担担面',     18.00, 1, 18.00),
(3,  2,  4,  '米饭',       2.00,  1, 2.00),
(4,  2,  1,  '宫保鸡丁',   28.00, 1, 28.00),
(5,  3,  2,  '鱼香肉丝',   26.00, 2, 52.00),
(6,  3,  4,  '米饭',       2.00,  1, 2.00),
(7,  4,  7,  '水煮鱼',     58.00, 1, 58.00),
(8,  4,  11, '红糖冰粉',   8.00,  2, 16.00),
(9,  5,  13, '凯撒沙拉',   26.00, 1, 26.00),
(10, 5,  16, '冷压橙汁',   15.00, 1, 15.00),
(11, 6,  14, '鸡胸肉卷',   24.00, 1, 24.00),
(12, 7,  10, '担担面',     18.00, 1, 18.00),
(13, 8,  4,  '米饭',       2.00,  1, 2.00),
(14, 9,  16, '冷压橙汁',   15.00, 1, 15.00),
(15, 10, 8,  '毛血旺',     48.00, 1, 48.00),
(16, 11, 13, '凯撒沙拉',   26.00, 1, 26.00),
(17, 11, 14, '鸡胸肉卷',   24.00, 1, 24.00),
(18, 12, 15, '藜麦能量碗', 32.00, 1, 32.00),
(19, 13, 7,  '水煮鱼',     58.00, 2, 116.00),
(20, 14, 1,  '宫保鸡丁',   28.00, 1, 28.00),
(21, 14, 3,  '麻婆豆腐',   22.00, 1, 22.00),
(22, 15, 13, '凯撒沙拉',   26.00, 1, 26.00);

-- 配送：覆盖 PENDING（待抢单）/ ACCEPTED（已接单）/ DELIVERING / DELIVERED 四态
INSERT INTO delivery (id, order_id, rider_id, status, accepted_at, picked_at, delivered_at, remark, updated_at) VALUES
(1, 1,  8,    'DELIVERED',  '2026-08-02 12:12:00', '2026-08-02 12:16:00', '2026-08-02 12:32:00', '已放置前台',         '2026-08-02 12:32:00'),
(2, 2,  4,    'DELIVERED',  '2026-08-17 18:10:00', '2026-08-17 18:14:00', '2026-08-17 18:30:00', NULL,                   '2026-08-17 18:30:00'),
(3, 3,  4,    'DELIVERED',  '2026-09-08 12:15:00', '2026-09-08 12:20:00', '2026-09-11 08:00:00', '顾客要求暂缓送达',     '2026-09-11 08:00:00'),
(4, 4,  8,    'DELIVERING', '2026-09-10 12:18:00', '2026-09-10 12:35:00', NULL,                  '按导航骑行中',         '2026-09-10 12:35:00'),
(5, 5,  NULL, 'PENDING',    NULL,                  NULL,                  NULL,                  NULL,                   '2026-09-11 06:20:00'),
(6, 12, 4,    'ACCEPTED',   '2026-09-09 12:20:00', NULL,                  NULL,                  NULL,                   '2026-09-09 12:20:00'),
(7, 13, 8,    'DELIVERED',  '2026-08-20 19:12:00', '2026-08-20 19:18:00', '2026-08-20 19:36:00', NULL,                   '2026-08-20 19:36:00'),
(8, 14, 4,    'DELIVERED',  '2026-08-25 12:12:00', '2026-08-25 12:16:00', '2026-08-25 12:30:00', NULL,                   '2026-08-25 12:30:00'),
(9, 15, 8,    'DELIVERED',  '2026-09-01 12:12:00', '2026-09-01 12:16:00', '2026-09-01 12:34:00', '放物业代收点',         '2026-09-01 12:34:00');

-- 评价：1~5 星分布，三店均分分别为 4.5 / 3.5 / 2.0，供评分排序与分布展示
INSERT INTO review (id, order_id, customer_id, shop_id, rating, content, created_at) VALUES
(1, 1,  5, 1, 4, '水煮鱼嫩滑分量足，就是等位久了点。',   '2026-08-02 12:50:00'),
(2, 13, 5, 1, 5, '担担面正宗，回购多次，配送也快。',     '2026-08-20 20:00:00'),
(3, 2,  2, 5, 5, '宫保鸡丁下饭，出餐速度满意。',         '2026-08-17 19:00:00'),
(4, 14, 2, 5, 2, '上菜慢，米饭偏硬，体验一般。',         '2026-08-25 13:00:00'),
(5, 11, 5, 2, 3, '沙拉新鲜但酱汁偏少，包装还行。',       '2026-08-30 13:00:00'),
(6, 15, 5, 2, 1, '送达时橙汁洒了半袋，希望加固包装。',   '2026-09-01 13:00:00');

-- 退款：覆盖 PENDING（商家待处理）/ SHOP_AGREED（拒单退款）/ SHOP_REJECTED（商家拒绝）三态
INSERT INTO refund (id, order_id, reason, status, remark, created_at) VALUES
(1, 10, '商家备餐不足主动拒单', 'SHOP_AGREED',   '已同意退款，款项原路返回',   '2026-09-03 12:20:00'),
(2, 3,  '菜品口味与描述不符',   'PENDING',       NULL,                         '2026-09-11 09:30:00'),
(3, 11, '送达超时申请退款',     'SHOP_REJECTED', '配送在承诺时间内，不予退款', '2026-08-30 13:20:00');
