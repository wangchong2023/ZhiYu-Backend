-- ============================================================
-- V1.6.0: 应用运行时日志存储
-- 数据库: zhiyu
-- 新增: app_log 表（30 天 TTL，定时任务清理）
-- 决策: ADR-014
-- ============================================================

CREATE TABLE app_log (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY  COMMENT '日志ID',
    created_at  DATETIME(3)  NOT NULL              COMMENT '日志时间',
    level       VARCHAR(10)  NOT NULL              COMMENT '日志级别: INFO/WARN/ERROR/DEBUG',
    logger      VARCHAR(255) NOT NULL              COMMENT 'Logger 类全限定名',
    message     MEDIUMTEXT   NOT NULL              COMMENT '日志消息',
    stack_trace MEDIUMTEXT   NULL                  COMMENT '异常堆栈',
    thread_name VARCHAR(100) NULL                  COMMENT '线程名',
    trace_id    CHAR(32)     NULL                  COMMENT '请求链路追踪ID (MDC)',
    user_id     BIGINT       NULL                  COMMENT '关联用户ID',
    module      VARCHAR(50)  NULL                  COMMENT '模块: auth/user/subscription/admin/notification',

    INDEX idx_app_log_ts    (created_at),
    INDEX idx_app_log_level (level, created_at),
    INDEX idx_app_log_trace (trace_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='应用运行时日志（30天TTL，定时清理）'
  ROW_FORMAT=Dynamic;
