-- ============================================================
-- V1.9.0: 运行监控 — 日志级别调整历史记录
-- 数据库: zhiyu
-- 新增: log_level_history 表
-- ============================================================

CREATE TABLE log_level_history (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY  COMMENT '记录ID',
    logger_name     VARCHAR(255)    NOT NULL           COMMENT 'Logger 名称',
    old_level       VARCHAR(10)     NOT NULL           COMMENT '旧日志级别',
    new_level       VARCHAR(10)     NOT NULL           COMMENT '新日志级别',
    changed_by      VARCHAR(64)     NOT NULL           COMMENT '操作人',
    expire_at       DATETIME(3)     DEFAULT NULL       COMMENT '自动回滚时间',
    rolled_back_at  DATETIME(3)     DEFAULT NULL       COMMENT '实际回滚时间',
    created_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',

    INDEX idx_logger_name (logger_name),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='日志级别调整审计记录'
  ROW_FORMAT=Dynamic;
