-- ============================================================
-- V1.0.0__init_schema.sql
-- 业务主库 zhiyu — 初始化全部核心业务表
-- ============================================================
-- 用户认证与授权表由 UFP auth 模块管理，位于 ufp_auth 库
-- 业务表通过跨库外键引用 ufp_auth.auth_user(auth_user_id)
-- 注意: 原 user / admin_user / admin_role / admin_permission /
--       admin_role_permission / user_auth_identity / audit_log
--       已由 UFP auth 系列表替代，不再创建。
-- ============================================================

CREATE TABLE user_device (
    id              BIGINT          NOT NULL AUTO_INCREMENT  COMMENT '记录ID',
    user_id         BIGINT          NOT NULL                 COMMENT '用户ID',
    device_id       CHAR(36)        NOT NULL                 COMMENT '设备UUID (客户端生成)',
    device_name     VARCHAR(128)    DEFAULT NULL             COMMENT '设备名称',
    platform        VARCHAR(16)     DEFAULT NULL             COMMENT 'IOS|ANDROID|WEB',
    push_token      VARCHAR(512)    DEFAULT NULL             COMMENT '推送token (FCM/APNs/个推)',
    trusted_for_totp TINYINT(1)     NOT NULL DEFAULT 0      COMMENT '是否信任跳过TOTP',
    last_active_at  DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '最后活跃时间',
    created_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_device (user_id, device_id),
    KEY idx_user_device_last_active (user_id, last_active_at),
    CONSTRAINT fk_device_user FOREIGN KEY (user_id) REFERENCES ufp_auth.auth_user(auth_user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户设备表';

CREATE TABLE user_profile (
    user_id             BIGINT          NOT NULL               COMMENT '用户ID (1:1)',
    notification_email  TINYINT(1)      NOT NULL DEFAULT 1    COMMENT '邮件通知',
    notification_push   TINYINT(1)      NOT NULL DEFAULT 1    COMMENT '推送通知',
    notification_sms    TINYINT(1)      NOT NULL DEFAULT 0    COMMENT '短信通知',
    show_online_status  TINYINT(1)      NOT NULL DEFAULT 1    COMMENT '在线状态可见',
    language            VARCHAR(8)      NOT NULL DEFAULT 'zh-CN',
    timezone            VARCHAR(32)     NOT NULL DEFAULT 'Asia/Shanghai',
    updated_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (user_id),
    CONSTRAINT fk_profile_user FOREIGN KEY (user_id) REFERENCES ufp_auth.auth_user(auth_user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户信息表';

CREATE TABLE user_totp (
    user_id         BIGINT          NOT NULL                   COMMENT '用户ID',
    secret          VARCHAR(64)     NOT NULL                   COMMENT 'TOTP secret (Base32)',
    enabled         TINYINT(1)      NOT NULL DEFAULT 0        COMMENT '是否已启用',
    recovery_codes  JSON            DEFAULT NULL               COMMENT '恢复码列表 (h(bcrypt) 存储)',
    created_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (user_id),
    CONSTRAINT fk_totp_user FOREIGN KEY (user_id) REFERENCES ufp_auth.auth_user(auth_user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户TOTP设置表';

CREATE TABLE subscription_plan (
    id              BIGINT          NOT NULL AUTO_INCREMENT  COMMENT '套餐ID',
    plan_key        VARCHAR(32)     NOT NULL                 COMMENT '套餐标识 free|lite|pro',
    name            VARCHAR(64)     NOT NULL                 COMMENT '套餐名称',
    description     VARCHAR(512)    DEFAULT NULL             COMMENT '简介',
    price_monthly   INT             NOT NULL DEFAULT 0      COMMENT '月价(分)',
    price_yearly    INT             NOT NULL DEFAULT 0      COMMENT '年价(分)',
    trial_days      INT             NOT NULL DEFAULT 0      COMMENT '试用天数 (0=不开放)',
    features_json   JSON            NOT NULL                 COMMENT '功能列表JSON ["basic_chat",...]',
    quotas_json     JSON            NOT NULL                 COMMENT '配额JSON {"daily_chat":200,...}',
    is_active       TINYINT(1)      NOT NULL DEFAULT 1      COMMENT '是否上架',
    sort_order      INT             NOT NULL DEFAULT 0      COMMENT '排序权重',
    created_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_plan_key (plan_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='套餐定义表';

CREATE TABLE user_subscription (
    id                  BIGINT          NOT NULL AUTO_INCREMENT  COMMENT '订阅ID',
    user_id             BIGINT          NOT NULL                 COMMENT '用户ID',
    plan_id             BIGINT          NOT NULL                 COMMENT '套餐ID',
    status              VARCHAR(16)     NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE|CANCELING|EXPIRED|REFUNDED',
    start_date          DATE            NOT NULL                 COMMENT '开始日期',
    end_date            DATE            NOT NULL                 COMMENT '结束日期',
    auto_renew          TINYINT(1)      NOT NULL DEFAULT 0      COMMENT '是否自动续费',
    pending_downgrade   VARCHAR(32)     DEFAULT NULL             COMMENT '预约降级目标 plan_key',
    trial_used          TINYINT(1)      NOT NULL DEFAULT 0      COMMENT '是否已使用试用',
    cancelled_at        DATETIME(3)     DEFAULT NULL             COMMENT '取消时间',
    created_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_subscription_user (user_id),
    KEY idx_subscription_status (status),
    KEY idx_subscription_end_date (end_date),
    CONSTRAINT fk_subscription_user FOREIGN KEY (user_id) REFERENCES ufp_auth.auth_user(auth_user_id),
    CONSTRAINT fk_subscription_plan FOREIGN KEY (plan_id) REFERENCES subscription_plan(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户订阅表';

CREATE TABLE subscription_order (
    id                  BIGINT          NOT NULL AUTO_INCREMENT  COMMENT '订单ID',
    order_no            VARCHAR(32)     NOT NULL                 COMMENT '订单号 ZY+yyyyMMdd+6位序号',
    user_id             BIGINT          NOT NULL                 COMMENT '用户ID',
    plan_id             BIGINT          NOT NULL                 COMMENT '套餐ID',
    plan_key            VARCHAR(32)     NOT NULL                 COMMENT '冗余套餐标识',
    period              VARCHAR(8)      NOT NULL                 COMMENT 'MONTHLY|YEARLY',
    amount              INT             NOT NULL                 COMMENT '金额(分)',
    original_amount     INT             DEFAULT NULL             COMMENT '原始金额(分)(升级折价时)',
    currency            VARCHAR(4)      NOT NULL DEFAULT 'CNY'  COMMENT '币种',
    channel             VARCHAR(16)     NOT NULL                 COMMENT 'WECHAT|ALIPAY|APPLE|GOOGLE',
    status              VARCHAR(16)     NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING|PAID|CANCELLED|EXPIRED|REFUNDED',
    transaction_id      VARCHAR(128)    DEFAULT NULL             COMMENT '第三方交易号',
    prepay_info_json    JSON            DEFAULT NULL             COMMENT '预支付信息 (仅服务端起单的渠道)',
    paid_at             DATETIME(3)     DEFAULT NULL             COMMENT '支付时间',
    created_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_order_no (order_no),
    KEY idx_order_user (user_id),
    KEY idx_order_status (status),
    KEY idx_order_channel_trans (channel, transaction_id),
    KEY idx_order_created (created_at),
    CONSTRAINT fk_order_user FOREIGN KEY (user_id) REFERENCES ufp_auth.auth_user(auth_user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='订阅订单表';

CREATE TABLE payment_record (
    id                  BIGINT          NOT NULL AUTO_INCREMENT  COMMENT '支付记录ID',
    order_id            BIGINT          NOT NULL                 COMMENT '订单ID',
    user_id             BIGINT          NOT NULL                 COMMENT '冗余用户ID',
    channel             VARCHAR(16)     NOT NULL                 COMMENT 'WECHAT|ALIPAY|APPLE|GOOGLE',
    transaction_id      VARCHAR(128)    NOT NULL                 COMMENT '第三方交易号',
    amount              INT             NOT NULL                 COMMENT '金额(分)',
    currency            VARCHAR(4)      NOT NULL DEFAULT 'CNY'  COMMENT '币种',
    status              VARCHAR(16)     NOT NULL                 COMMENT 'SUCCESS|REFUND|FAIL',
    raw_notification    JSON            DEFAULT NULL             COMMENT '原始回调JSON (审计)',
    reconciliation_status VARCHAR(16)   DEFAULT NULL             COMMENT 'MATCH|MISMATCH|ONLY_LOCAL|ONLY_REMOTE',
    reconciliation_note VARCHAR(255)    DEFAULT NULL             COMMENT '对账差异备注',
    paid_at             DATETIME(3)     DEFAULT NULL,
    created_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_payment_transaction (channel, transaction_id),
    KEY idx_payment_order (order_id),
    KEY idx_payment_user (user_id),
    KEY idx_payment_reconciliation (reconciliation_status),
    CONSTRAINT fk_payment_order FOREIGN KEY (order_id) REFERENCES subscription_order(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='支付记录表';

CREATE TABLE quota_usage (
    id              BIGINT          NOT NULL AUTO_INCREMENT  COMMENT '用量ID',
    user_id         BIGINT          NOT NULL                 COMMENT '用户ID',
    quota_key       VARCHAR(32)     NOT NULL                 COMMENT '配额标识 daily_chat|file_upload_mb|image_gen_daily',
    used_count      BIGINT          NOT NULL DEFAULT 0      COMMENT '已用量',
    version         INT             NOT NULL DEFAULT 0      COMMENT '乐观锁版本号',
    period_start    DATE            NOT NULL                 COMMENT '周期开始',
    period_end      DATE            NOT NULL                 COMMENT '周期结束',
    created_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_quota_user_key_period (user_id, quota_key, period_start),
    KEY idx_quota_period (quota_key, period_start)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='配额用量表';

CREATE TABLE refund_record (
    id              BIGINT          NOT NULL AUTO_INCREMENT  COMMENT '退款记录ID',
    refund_no       VARCHAR(32)     NOT NULL                 COMMENT '退款单号 RF+日期+序号',
    order_id        BIGINT          NOT NULL                 COMMENT '原订单ID',
    user_id         BIGINT          NOT NULL                 COMMENT '用户ID',
    amount          INT             NOT NULL                 COMMENT '退款金额(分)',
    reason          VARCHAR(16)     NOT NULL                 COMMENT 'DUPLICATE_PURCHASE|ACCIDENTAL|NOT_SATISFIED|OTHER',
    description     VARCHAR(500)    DEFAULT NULL             COMMENT '用户描述',
    status          VARCHAR(16)     NOT NULL DEFAULT 'PENDING_REVIEW' COMMENT 'PENDING_REVIEW|APPROVED|REJECTED|REFUNDED',
    reviewer_id     BIGINT          DEFAULT NULL             COMMENT '审核人ID (引用 ufp_auth.auth_user)',
    review_note     VARCHAR(255)    DEFAULT NULL             COMMENT '审核备注',
    channel_refund_id VARCHAR(128)  DEFAULT NULL             COMMENT '渠道退款单号',
    applied_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    reviewed_at     DATETIME(3)     DEFAULT NULL,
    refunded_at     DATETIME(3)     DEFAULT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_refund_no (refund_no),
    KEY idx_refund_order (order_id),
    KEY idx_refund_user (user_id),
    KEY idx_refund_status (status),
    CONSTRAINT fk_refund_order FOREIGN KEY (order_id) REFERENCES subscription_order(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='退款记录表';

CREATE TABLE account_recovery_ticket (
    id              BIGINT          NOT NULL AUTO_INCREMENT  COMMENT '工单ID',
    ticket_no       VARCHAR(32)     NOT NULL                 COMMENT '工单号 AR+日期+序号',
    user_id         BIGINT          DEFAULT NULL             COMMENT '匹配到的用户ID (审核后确定, 引用 ufp_auth.auth_user)',
    submitted_info  JSON            NOT NULL                 COMMENT '用户提交的信息 {"email":"...","phone":"..."}',
    status          VARCHAR(16)     NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING|APPROVED|REJECTED|EXPIRED',
    reviewer_id     BIGINT          DEFAULT NULL             COMMENT '审核人ID (引用 ufp_auth.auth_user)',
    review_note     VARCHAR(255)    DEFAULT NULL,
    recovery_token  VARCHAR(128)    DEFAULT NULL             COMMENT '恢复链接token (审核通过后生成)',
    recovery_token_expires DATETIME(3) DEFAULT NULL,
    applied_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    reviewed_at     DATETIME(3)     DEFAULT NULL,
    expires_at      DATETIME(3)     NOT NULL                 COMMENT '7天后自动关闭',
    PRIMARY KEY (id),
    UNIQUE KEY uk_recovery_ticket_no (ticket_no),
    KEY idx_recovery_ticket_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='账户恢复工单表';

CREATE TABLE notification_template (
    id              BIGINT          NOT NULL AUTO_INCREMENT  COMMENT '模板ID',
    template_key    VARCHAR(64)     NOT NULL                 COMMENT '模板标识 register_welcome|reset_password|...',
    type            VARCHAR(8)      NOT NULL                 COMMENT '通知类型 EMAIL|SMS|PUSH',
    subject         VARCHAR(256)    DEFAULT NULL             COMMENT '标题 (EMAIL类型使用)',
    body            TEXT            NOT NULL                 COMMENT '模板正文，变量用 {{varName}} 占位',
    variables_json  JSON            NOT NULL                 COMMENT '变量列表 ["username","app_name","reset_link"]',
    description     VARCHAR(256)    DEFAULT NULL             COMMENT '模板用途说明',
    is_active       TINYINT(1)      NOT NULL DEFAULT 1      COMMENT '是否启用',
    created_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_notification_template_key (template_key),
    KEY idx_notification_template_type (type, is_active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='通知模板表';
