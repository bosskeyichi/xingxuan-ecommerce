package com.ecommerce.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 旧库平滑升级：Spring SQL init 不支持条件 DDL，
 * 这里在启动时检查并补齐新增列（幂等，可重复跑）。
 * 当前升级项：
 * 1. friend.status（0 申请中 / 1 已同意）—— 好友申请-同意制。
 * 2. product.free_shipping / ship_hours / return_days —— 商品服务承诺（包邮 / 发货时效 / 无理由天数）。
 * 3. orders.tracking_no / tracking_company —— 发货的快递公司与运单号。
 * 4. user_table.email 唯一索引（uk_user_email）—— 别让同一邮箱注册多个账号产生脏数据；
 *    补建前先排查重复的非空邮箱，存在重复则跳过并告警，不阻断启动。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SchemaUpgradeRunner implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        try {
            ensureFriendStatusColumn();
        } catch (Exception e) {
            // 升级失败不阻断启动（例如首次建库时 schema 已含该列），只记录日志
            log.warn("Schema upgrade skipped: {}", e.getMessage());
        }
        // 商品服务承诺列：逐列独立兜底，任何一列失败都不影响其余列
        ensureColumn("product", "free_shipping",
                "ALTER TABLE product ADD COLUMN free_shipping TINYINT NOT NULL DEFAULT 1 "
                        + "COMMENT '1 包邮 / 0 不包邮' AFTER keywords");
        ensureColumn("product", "ship_hours",
                "ALTER TABLE product ADD COLUMN ship_hours INT NOT NULL DEFAULT 48 "
                        + "COMMENT '承诺发货时效（小时），0 表示不承诺' AFTER free_shipping");
        ensureColumn("product", "return_days",
                "ALTER TABLE product ADD COLUMN return_days INT NOT NULL DEFAULT 7 "
                        + "COMMENT '无理由退换天数，0 表示不支持' AFTER ship_hours");
        // 订单物流信息：发货时记录快递公司与单号，便于用户端查询物流
        ensureColumn("orders", "tracking_no",
                "ALTER TABLE `orders` ADD COLUMN tracking_no VARCHAR(64) NULL DEFAULT NULL "
                        + "COMMENT '快递单号' AFTER cancelled_at");
        ensureColumn("orders", "tracking_company",
                "ALTER TABLE `orders` ADD COLUMN tracking_company VARCHAR(64) NULL DEFAULT NULL "
                        + "COMMENT '快递公司' AFTER tracking_no");
        // 注册邮箱唯一索引：别让脏数据（同一邮箱注册多个账号）
        ensureEmailUniqueIndex();
    }

    /**
     * 注册邮箱唯一索引（user_table.email）：别让同一邮箱注册多个账号产生脏数据。
     * 加索引前先排查重复的非空邮箱，若存在重复则跳过（不阻断启动），交由人工处理。
     */
    private void ensureEmailUniqueIndex() {
        try {
            Integer dup = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM (SELECT email FROM user_table "
                            + "WHERE email IS NOT NULL AND email <> '' "
                            + "GROUP BY email HAVING COUNT(*) > 1) t",
                    Integer.class);
            if (dup != null && dup > 0) {
                log.warn("Schema upgrade skipped uk_user_email: found {} duplicate non-empty emails", dup);
                return;
            }
            jdbcTemplate.execute("ALTER TABLE user_table ADD UNIQUE INDEX uk_user_email (email)");
            log.info("Schema upgrade: added unique index uk_user_email on user_table.email");
        } catch (Exception e) {
            // 索引已存在（errno 1061）或其它 DDL 错误：不阻断启动
            log.warn("Schema upgrade skipped uk_user_email: {}", e.getMessage());
        }
    }

    private void ensureFriendStatusColumn() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.COLUMNS "
                        + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'friend' AND COLUMN_NAME = 'status'",
                Integer.class);
        if (count != null && count > 0) return;
        jdbcTemplate.execute("ALTER TABLE friend ADD COLUMN status TINYINT NOT NULL DEFAULT 1 AFTER friend_id");
        log.info("Schema upgrade: added friend.status column");
    }

    private void ensureColumn(String table, String column, String ddl) {
        try {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.COLUMNS "
                            + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?",
                    Integer.class, table, column);
            if (count != null && count > 0) return;
            jdbcTemplate.execute(ddl);
            log.info("Schema upgrade: added {}.{} column", table, column);
        } catch (Exception e) {
            log.warn("Schema upgrade skipped for {}.{}: {}", table, column, e.getMessage());
        }
    }
}
