-- ============================================================
-- 星选商城 · MySQL Schema (8.0+)
-- 幂等初始化：CREATE TABLE IF NOT EXISTS，重启不会丢失或重复数据。
-- 引擎 InnoDB；字符集 utf8mb4（兼容 emoji 与生僻字）；时区 Asia/Shanghai。
-- 完全重置：在 MySQL 中 DROP TABLE 后重新启动，或删除数据库后重建。
-- ============================================================

-- 初始化标记：记录用户业务示例数据是否已写入，别让「重置数据」后重启又被种子数据还原
CREATE TABLE IF NOT EXISTS seed_meta (
  id         INT      NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS user_table (
  id           BIGINT        NOT NULL AUTO_INCREMENT,
  username     VARCHAR(50)   NOT NULL,
  password     VARCHAR(100)  NOT NULL,
  nickname     VARCHAR(50),
  email        VARCHAR(100),
  phone        VARCHAR(20),
  avatar       TEXT,
  role         VARCHAR(20)   NOT NULL DEFAULT 'USER',
  status       INT           NOT NULL DEFAULT 1,
  created_at   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted      INT           NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS category (
  id          BIGINT      NOT NULL AUTO_INCREMENT,
  name        VARCHAR(50) NOT NULL,
  icon        VARCHAR(255),
  sort_order  INT         NOT NULL DEFAULT 0,
  parent_id   BIGINT      NOT NULL DEFAULT 0,
  created_at  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted     INT         NOT NULL DEFAULT 0,
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 店铺（商家）：一个商家账号对应一个店铺，商品与订单都归属到店铺
CREATE TABLE IF NOT EXISTS shop (
  id          BIGINT       NOT NULL AUTO_INCREMENT,
  owner_id    BIGINT       NOT NULL,
  name        VARCHAR(60)  NOT NULL,
  intro       VARCHAR(200),
  cover       VARCHAR(1000),
  status      INT          NOT NULL DEFAULT 1,
  created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted     INT          NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_shop_owner (owner_id),
  KEY idx_shop_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS product (
  id          BIGINT        NOT NULL AUTO_INCREMENT,
  shop_id     BIGINT        NOT NULL DEFAULT 1,
  name        VARCHAR(100)  NOT NULL,
  description VARCHAR(500),
  keywords    VARCHAR(500)  COMMENT '搜索别名（逗号分隔），与商品名一起参与关键词匹配',
  free_shipping TINYINT     NOT NULL DEFAULT 1 COMMENT '1 包邮 / 0 不包邮，店铺自行维护',
  ship_hours  INT           NOT NULL DEFAULT 48 COMMENT '承诺发货时效（小时），0 表示不承诺',
  return_days INT           NOT NULL DEFAULT 7 COMMENT '无理由退换天数，0 表示不支持',
  price       DECIMAL(10,2) NOT NULL,
  stock       INT           NOT NULL DEFAULT 0,
  cover       VARCHAR(1000),
  category_id BIGINT,
  sales       INT           NOT NULL DEFAULT 0,
  status      INT           NOT NULL DEFAULT 1,
  created_at  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted     INT           NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_product_cat (category_id, status),
  KEY idx_product_shop (shop_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS cart (
  id         BIGINT    NOT NULL AUTO_INCREMENT,
  user_id    BIGINT    NOT NULL,
  product_id BIGINT    NOT NULL,
  quantity   INT       NOT NULL DEFAULT 1,
  selected   INT       NOT NULL DEFAULT 1,
  created_at DATETIME  NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME  NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted    INT       NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_cart (user_id, product_id),
  KEY idx_cart_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS orders (
  id              BIGINT        NOT NULL AUTO_INCREMENT,
  order_no        VARCHAR(50)   NOT NULL,
  shop_id         BIGINT,
  user_id         BIGINT        NOT NULL,
  total_amount    DECIMAL(10,2) NOT NULL,
  discount_amount DECIMAL(10,2) NOT NULL DEFAULT 0,
  coupon_id       BIGINT,
  status          VARCHAR(20)   NOT NULL,
  receiver_name   VARCHAR(50),
  receiver_phone  VARCHAR(20),
  receiver_addr   VARCHAR(255),
  remark          VARCHAR(500),
  paid_at         DATETIME,
  shipped_at      DATETIME,
  completed_at    DATETIME,
  cancelled_at    DATETIME,
  created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted         INT           NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_order_no (order_no),
  KEY idx_orders_user (user_id),
  KEY idx_orders_shop (shop_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS order_item (
  id              BIGINT        NOT NULL AUTO_INCREMENT,
  order_id        BIGINT        NOT NULL,
  product_id      BIGINT        NOT NULL,
  product_name    VARCHAR(100)  NOT NULL,
  product_cover   VARCHAR(1000),
  price           DECIMAL(10,2) NOT NULL,
  quantity        INT           NOT NULL,
  subtotal        DECIMAL(10,2) NOT NULL DEFAULT 0,
  created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted         INT           NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_oi_order (order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS favorite (
  id          BIGINT    NOT NULL AUTO_INCREMENT,
  user_id     BIGINT    NOT NULL,
  product_id  BIGINT    NOT NULL,
  created_at  DATETIME  NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted     INT       NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_fav (user_id, product_id),
  KEY idx_fav_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS review (
  id          BIGINT    NOT NULL AUTO_INCREMENT,
  user_id     BIGINT    NOT NULL,
  product_id  BIGINT    NOT NULL,
  order_id    BIGINT,
  rating      INT       NOT NULL DEFAULT 5,
  content     VARCHAR(500),
  created_at  DATETIME  NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at  DATETIME  NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted     INT       NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  -- 每人每商品仅一条有效评价；deleted 为 0 表示有效，删除时置为自身 id，既保留墓碑又别让复用冲突
  UNIQUE KEY uk_review_user_product (user_id, product_id, deleted),
  KEY idx_review_product (product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 追评：一条评价可追加多条，按时间展示在商品页
CREATE TABLE IF NOT EXISTS review_append (
  id         BIGINT       NOT NULL AUTO_INCREMENT,
  review_id  BIGINT       NOT NULL,
  content    VARCHAR(500) NOT NULL,
  created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_review_append (review_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
;

-- 管理员屏蔽的评价：软屏蔽可恢复，商品页与评分汇总会过滤
CREATE TABLE IF NOT EXISTS review_hidden (
  review_id  BIGINT   NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (review_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
;

-- 浏览足迹：按用户存储，可跨设备同步，随账号「重置数据 / 删除用户」一并清空
CREATE TABLE IF NOT EXISTS browse_history (
  id         BIGINT   NOT NULL AUTO_INCREMENT,
  user_id    BIGINT   NOT NULL,
  product_id BIGINT   NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_browse (user_id, product_id),
  KEY idx_browse_user (user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS address (
  id             BIGINT       NOT NULL AUTO_INCREMENT,
  user_id        BIGINT       NOT NULL,
  receiver_name  VARCHAR(50)  NOT NULL,
  receiver_phone VARCHAR(20)  NOT NULL,
  region         VARCHAR(100) NOT NULL,
  detail         VARCHAR(200) NOT NULL,
  is_default     INT          NOT NULL DEFAULT 0,
  created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted        INT          NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_addr_user (user_id, is_default)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS coupon (
  id           BIGINT        NOT NULL AUTO_INCREMENT,
  shop_id      BIGINT,                                  -- NULL = 全平台券；非空 = 仅该店铺可用
  name         VARCHAR(60)   NOT NULL,
  description  VARCHAR(200),
  threshold    DECIMAL(10,2) NOT NULL DEFAULT 0,
  discount     DECIMAL(10,2) NOT NULL DEFAULT 0,
  total        INT           NOT NULL DEFAULT 0,
  claimed      INT           NOT NULL DEFAULT 0,
  start_at     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  end_at       DATETIME,
  status       INT           NOT NULL DEFAULT 1,
  created_at   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted      INT           NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_coupon_status (status),
  KEY idx_coupon_shop (shop_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS user_coupon (
  id         BIGINT       NOT NULL AUTO_INCREMENT,
  user_id    BIGINT       NOT NULL,
  coupon_id  BIGINT       NOT NULL,
  status     VARCHAR(20)  NOT NULL DEFAULT 'UNUSED',
  order_id   BIGINT,
  used_at    DATETIME,
  created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted    INT          NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_user_coupon (user_id, coupon_id),
  KEY idx_uc_user (user_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- 增量迁移（幂等）：把历史库的 avatar 扩为 TEXT，用来存放自定义头像（base64）
-- MySQL 不支持 MODIFY COLUMN IF NOT EXISTS，这里先查 information_schema 再决定是否跑
-- ============================================================
SET @avatar_type := (SELECT DATA_TYPE FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user_table' AND COLUMN_NAME = 'avatar');
SET @avatar_ddl := IF(@avatar_type IS NOT NULL AND @avatar_type <> 'text',
  'ALTER TABLE user_table MODIFY COLUMN avatar TEXT', 'SELECT 1');
PREPARE avatar_stmt FROM @avatar_ddl;
EXECUTE avatar_stmt;
DEALLOCATE PREPARE avatar_stmt;

-- ============================================================
-- 增量迁移（幂等）：评价唯一键由「每单一条」调整为「每人每商品一条有效」。
-- 旧键 (user_id, order_id) 会导致一单多商品时第二件评价冲突；
-- 新键 (user_id, product_id, deleted) 中 deleted=0 为有效评价，
-- 删除时把 deleted 置为自身 id（墓碑），既允许重新评价，也不会被种子数据复活。
-- ============================================================
SET @review_idx_old := (SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'review' AND INDEX_NAME = 'uk_review_user_order');
SET @review_ddl_old := IF(@review_idx_old > 0,
  'ALTER TABLE review DROP INDEX uk_review_user_order', 'SELECT 1');
PREPARE review_stmt_old FROM @review_ddl_old;
EXECUTE review_stmt_old;
DEALLOCATE PREPARE review_stmt_old;

-- 旧的两列唯一键（若存在）先删除，再按三列重建
SET @review_idx_cols := (SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'review' AND INDEX_NAME = 'uk_review_user_product');
SET @review_ddl_drop2 := IF(@review_idx_cols = 2,
  'ALTER TABLE review DROP INDEX uk_review_user_product', 'SELECT 1');
PREPARE review_stmt_drop2 FROM @review_ddl_drop2;
EXECUTE review_stmt_drop2;
DEALLOCATE PREPARE review_stmt_drop2;

-- 历史逻辑删除行统一改成「deleted = id」，别让多行墓碑在唯一键上互相冲突
UPDATE review SET deleted = id WHERE deleted <> 0;

-- 极端情况：旧版允许同一用户对同一商品从不同订单各留一条有效评价。
-- 建新唯一键前只保留最新一条，其余转为墓碑，别让迁移失败。
UPDATE review r
JOIN (
  SELECT user_id, product_id, MAX(id) AS keep_id
  FROM review
  WHERE deleted = 0
  GROUP BY user_id, product_id
  HAVING COUNT(*) > 1
) d ON r.user_id = d.user_id AND r.product_id = d.product_id
SET r.deleted = r.id
WHERE r.deleted = 0 AND r.id <> d.keep_id;

SET @review_idx_cols2 := (SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'review' AND INDEX_NAME = 'uk_review_user_product');
SET @review_ddl_new := IF(@review_idx_cols2 = 0,
  'ALTER TABLE review ADD UNIQUE KEY uk_review_user_product (user_id, product_id, deleted)', 'SELECT 1');
PREPARE review_stmt_new FROM @review_ddl_new;
EXECUTE review_stmt_new;
DEALLOCATE PREPARE review_stmt_new;

-- 清理历史收藏墓碑：收藏改为物理删除后，旧逻辑删除行会占住 (user_id, product_id) 唯一键
DELETE FROM favorite WHERE deleted <> 0;

-- 清理历史孤儿数据：评价已删除但追评 / 屏蔽记录残留（旧版重置逻辑的遗留）
DELETE a FROM review_append a LEFT JOIN review r ON r.id = a.review_id WHERE r.id IS NULL;
DELETE h FROM review_hidden h LEFT JOIN review r ON r.id = h.review_id WHERE r.id IS NULL;

-- ============================================================
-- 增量迁移（幂等）：多商家改造
-- product.shop_id：商品归属店铺（老库补列，默认 1 号店）
-- orders.shop_id ：订单归属店铺（跨店下单时按店铺拆成多笔订单）
-- ============================================================
SET @has_product_shop := (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'product' AND COLUMN_NAME = 'shop_id');
SET @ddl_product_shop := IF(@has_product_shop = 0,
  'ALTER TABLE product ADD COLUMN shop_id BIGINT NOT NULL DEFAULT 1 AFTER id', 'SELECT 1');
PREPARE product_shop_stmt FROM @ddl_product_shop;
EXECUTE product_shop_stmt;
DEALLOCATE PREPARE product_shop_stmt;

SET @has_order_shop := (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'orders' AND COLUMN_NAME = 'shop_id');
SET @ddl_order_shop := IF(@has_order_shop = 0,
  'ALTER TABLE orders ADD COLUMN shop_id BIGINT AFTER order_no', 'SELECT 1');
PREPARE order_shop_stmt FROM @ddl_order_shop;
EXECUTE order_shop_stmt;
DEALLOCATE PREPARE order_shop_stmt;

SET @has_product_shop_idx := (SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'product' AND INDEX_NAME = 'idx_product_shop');
SET @ddl_product_shop_idx := IF(@has_product_shop_idx = 0,
  'ALTER TABLE product ADD INDEX idx_product_shop (shop_id, status)', 'SELECT 1');
PREPARE product_shop_idx_stmt FROM @ddl_product_shop_idx;
EXECUTE product_shop_idx_stmt;
DEALLOCATE PREPARE product_shop_idx_stmt;

SET @has_order_shop_idx := (SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'orders' AND INDEX_NAME = 'idx_orders_shop');
SET @ddl_order_shop_idx := IF(@has_order_shop_idx = 0,
  'ALTER TABLE orders ADD INDEX idx_orders_shop (shop_id, status)', 'SELECT 1');
PREPARE order_shop_idx_stmt FROM @ddl_order_shop_idx;
EXECUTE order_shop_idx_stmt;
DEALLOCATE PREPARE order_shop_idx_stmt;

-- 历史订单归属：按订单内商品回填店铺，保证升级后商家的历史订单不丢失
UPDATE `orders` o
JOIN (
  SELECT oi.order_id, MIN(p.shop_id) AS shop_id
  FROM order_item oi
  JOIN product p ON p.id = oi.product_id
  WHERE p.shop_id IS NOT NULL
  GROUP BY oi.order_id
) t ON t.order_id = o.id
SET o.shop_id = t.shop_id
WHERE o.shop_id IS NULL;

-- ============================================================
-- 增量迁移（幂等）：优惠券按店铺发放
-- coupon.shop_id 为 NULL 表示全平台券，非空表示仅该店铺订单可用
-- ============================================================
SET @has_coupon_shop := (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'coupon' AND COLUMN_NAME = 'shop_id');
SET @ddl_coupon_shop := IF(@has_coupon_shop = 0,
  'ALTER TABLE coupon ADD COLUMN shop_id BIGINT AFTER id', 'SELECT 1');
PREPARE coupon_shop_stmt FROM @ddl_coupon_shop;
EXECUTE coupon_shop_stmt;
DEALLOCATE PREPARE coupon_shop_stmt;

SET @has_coupon_shop_idx := (SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'coupon' AND INDEX_NAME = 'idx_coupon_shop');
SET @ddl_coupon_shop_idx := IF(@has_coupon_shop_idx = 0,
  'ALTER TABLE coupon ADD INDEX idx_coupon_shop (shop_id, status)', 'SELECT 1');
PREPARE coupon_shop_idx_stmt FROM @ddl_coupon_shop_idx;
EXECUTE coupon_shop_idx_stmt;
DEALLOCATE PREPARE coupon_shop_idx_stmt;

-- ============================================================
-- 增量迁移（幂等）：评价支持匿名
-- anonymous = 1 时商品页与分享卡片隐藏评价人，仅本人与平台管理员可见真实账号
-- ============================================================
SET @has_review_anonymous := (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'review' AND COLUMN_NAME = 'anonymous');
SET @ddl_review_anonymous := IF(@has_review_anonymous = 0,
  'ALTER TABLE review ADD COLUMN anonymous TINYINT NOT NULL DEFAULT 0 AFTER content', 'SELECT 1');
PREPARE review_anon_stmt FROM @ddl_review_anonymous;
EXECUTE review_anon_stmt;
DEALLOCATE PREPARE review_anon_stmt;

-- ============================================================
-- 社交域：个人设置 / 好友 / 私聊与客服消息 / 举报
-- ============================================================

-- 个人设置：一个账号一行，缺行时后端按默认值返回
CREATE TABLE IF NOT EXISTS user_setting (
  id                  BIGINT      NOT NULL AUTO_INCREMENT,
  user_id             BIGINT      NOT NULL,
  searchable          TINYINT     NOT NULL DEFAULT 1 COMMENT '允许通过昵称/用户名/ID 被搜索到',
  friend_policy       VARCHAR(20) NOT NULL DEFAULT 'ALL' COMMENT 'ALL 所有人可加 / NOBODY 不允许',
  show_email          TINYINT     NOT NULL DEFAULT 0,
  show_phone          TINYINT     NOT NULL DEFAULT 0,
  show_reviews        TINYINT     NOT NULL DEFAULT 1 COMMENT '允许他人查看我的评价',
  receive_friend_msg  TINYINT     NOT NULL DEFAULT 1,
  receive_service_msg TINYINT     NOT NULL DEFAULT 1,
  receive_system_msg  TINYINT     NOT NULL DEFAULT 1,
  notify_sound        TINYINT     NOT NULL DEFAULT 1,
  created_at          DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at          DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted             INT         NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_setting_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 好友关系：申请-同意制。申请时写入一行 (申请人, 目标, status=0)，
-- 对方同意后更新为 1 并补写反向行 (目标, 申请人, status=1)，双方各持一条已同意记录
CREATE TABLE IF NOT EXISTS friend (
  id         BIGINT      NOT NULL AUTO_INCREMENT,
  user_id    BIGINT      NOT NULL,
  friend_id  BIGINT      NOT NULL,
  status     TINYINT     NOT NULL DEFAULT 1,
  remark     VARCHAR(50),
  created_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted    INT         NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_friend_pair (user_id, friend_id),
  KEY idx_friend_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 私聊消息：好友之间为 FRIEND，用户向店铺客服咨询为 SERVICE
-- conversation_key = 较小 userId : 较大 userId : scene，便于按会话聚合
CREATE TABLE IF NOT EXISTS message (
  id               BIGINT       NOT NULL AUTO_INCREMENT,
  conversation_key VARCHAR(64)  NOT NULL,
  scene            VARCHAR(20)  NOT NULL DEFAULT 'FRIEND' COMMENT 'FRIEND / SERVICE',
  from_id          BIGINT       NOT NULL COMMENT '发送人，0 表示系统通知',
  to_id            BIGINT       NOT NULL,
  type             VARCHAR(20)  NOT NULL DEFAULT 'TEXT' COMMENT 'TEXT / PRODUCT / SHOP / REVIEW',
  content          VARCHAR(2000),
  target_id        BIGINT       COMMENT '分享的商品 / 店铺 / 评价 id',
  shop_id          BIGINT       COMMENT '客服会话所属店铺',
  read_at          DATETIME,
  created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted          INT          NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_msg_conv (conversation_key, id),
  KEY idx_msg_to (to_id, read_at),
  KEY idx_msg_from (from_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 会话隐藏：用户从会话列表「删除」会话时记录隐藏点（该会话内自己见过的最大消息 id），
-- 只是不再显示；对方再来新消息（id 大于隐藏点）时会话自动重新出现
CREATE TABLE IF NOT EXISTS conversation_hide (
  id              BIGINT       NOT NULL AUTO_INCREMENT,
  user_id         BIGINT       NOT NULL,
  conversation_key VARCHAR(64) NOT NULL,
  last_message_id BIGINT       NOT NULL DEFAULT 0,
  created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted         INT          NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_hide_pair (user_id, conversation_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 注：已存在的旧库由后端启动时的 SchemaUpgradeRunner 自动补齐 friend.status 列（0 申请中 / 1 已同意）

-- 举报：商品 / 店铺；商家看本店，平台管理员看全部，处理结果回馈举报人
CREATE TABLE IF NOT EXISTS report (
  id           BIGINT       NOT NULL AUTO_INCREMENT,
  reporter_id  BIGINT       NOT NULL,
  target_type  VARCHAR(20)  NOT NULL COMMENT 'PRODUCT / SHOP',
  target_id    BIGINT       NOT NULL,
  shop_id      BIGINT       COMMENT '被举报对象所属店铺',
  type         VARCHAR(30)  NOT NULL COMMENT '假冒伪劣 / 虚假宣传 / 违规内容 / 侵权 / 其他',
  content      VARCHAR(500) NOT NULL,
  status       VARCHAR(20)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING / ACCEPTED / REJECTED',
  handle_note  VARCHAR(500),
  handler_id   BIGINT,
  handled_at   DATETIME,
  created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted      INT          NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_report_shop (shop_id, status),
  KEY idx_report_reporter (reporter_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
