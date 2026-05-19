-- V1.0.1: 持久化登录失败记录（补充 Redis 滑动窗口的内存限制）
CREATE TABLE login_attempt (
    id              BIGINT          NOT NULL AUTO_INCREMENT  COMMENT '记录ID',
    identifier      VARCHAR(254)    NOT NULL                 COMMENT '尝试标识 (邮箱/手机/用户名)',
    attempt_type    VARCHAR(16)     NOT NULL                 COMMENT 'LOGIN|REGISTER|PASSWORD_RESET|TOTP_VERIFY',
    source_ip       VARCHAR(45)     NOT NULL                 COMMENT '来源IP',
    device_id       CHAR(36)        DEFAULT NULL             COMMENT '设备ID',
    success         TINYINT(1)      NOT NULL DEFAULT 0      COMMENT '是否成功',
    failure_reason  VARCHAR(64)     DEFAULT NULL             COMMENT '失败原因 WRONG_PASSWORD|ACCOUNT_LOCKED|CAPTCHA_FAIL|...',
    user_agent      VARCHAR(512)    DEFAULT NULL             COMMENT 'User-Agent',
    created_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_login_attempt_identifier (identifier, created_at),
    KEY idx_login_attempt_ip (source_ip, created_at),
    KEY idx_login_attempt_type_success (attempt_type, success, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='登录尝试记录表';
