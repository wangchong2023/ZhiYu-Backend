-- ============================================================
-- V1.0.0__init_schema.sql
-- 初始化全部核心表结构
-- ============================================================

CREATE TABLE user (
    id              BIGINT          NOT NULL AUTO_INCREMENT  COMMENT '用户ID',
    username        VARCHAR(32)     NOT NULL                 COMMENT '用户名',
    email           VARCHAR(254)    DEFAULT NULL             COMMENT '邮箱',
    email_verified  TINYINT(1)      NOT NULL DEFAULT 0      COMMENT '邮箱是否已验证',
    phone           VARCHAR(20)     DEFAULT NULL             COMMENT '手机号 (E.164)',
    phone_verified  TINYINT(1)      NOT NULL DEFAULT 0      COMMENT '手机号是否已验证',
    password_hash   VARCHAR(60)     DEFAULT NULL             COMMENT 'BCrypt hash (PASSWORD登录方式才有)',
    nickname        VARCHAR(64)     DEFAULT NULL             COMMENT '昵称',
    avatar_url      VARCHAR(512)    DEFAULT NULL             COMMENT '头像OSS URL',
    status          VARCHAR(16)     NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE|DISABLED|DELETED',
    deleted_at      DATETIME(3)     DEFAULT NULL             COMMENT '注销时间 (30天冷却)',
    created_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '注册时间',
    updated_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_username (username),
    UNIQUE KEY uk_user_email (email),
    UNIQUE KEY uk_user_phone (phone),
    KEY idx_user_status (status),
    KEY idx_user_created_at (created_at),
    KEY idx_user_deleted_at (deleted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';

CREATE TABLE user_auth_identity (
    id              BIGINT          NOT NULL AUTO_INCREMENT  COMMENT '身份ID',
    user_id         BIGINT          NOT NULL                 COMMENT '用户ID',
    identity_type   VARCHAR(16)     NOT NULL                 COMMENT 'WECHAT|QQ|PHONE|EMAIL|GOOGLE|APPLE|WEBAUTHN|PASSWORD',
    identifier      VARCHAR(512)    NOT NULL                 COMMENT 'openid/手机号/邮箱/credentialId',
    credential      VARCHAR(255)    DEFAULT NULL             COMMENT '仅PASSWORD存BCrypt hash，其他类型为NULL',
    last_used_at    DATETIME(3)     DEFAULT NULL             COMMENT '最后使用时间',
    created_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_auth_identity_type_idfr (identity_type, identifier),
    KEY idx_user_auth_identity_user (user_id),
    CONSTRAINT fk_auth_identity_user FOREIGN KEY (user_id) REFERENCES user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户认证身份表';

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
    KEY idx_user_device_last_active (user_id, last_active_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户设备表';

CREATE TABLE user_settings (
    user_id             BIGINT          NOT NULL               COMMENT '用户ID (1:1)',
    notification_email  TINYINT(1)      NOT NULL DEFAULT 1    COMMENT '邮件通知',
    notification_push   TINYINT(1)      NOT NULL DEFAULT 1    COMMENT '推送通知',
    notification_sms    TINYINT(1)      NOT NULL DEFAULT 0    COMMENT '短信通知',
    show_online_status  TINYINT(1)      NOT NULL DEFAULT 1    COMMENT '在线状态可见',
    language            VARCHAR(8)      NOT NULL DEFAULT 'zh-CN',
    timezone            VARCHAR(32)     NOT NULL DEFAULT 'Asia/Shanghai',
    updated_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (user_id),
    CONSTRAINT fk_settings_user FOREIGN KEY (user_id) REFERENCES user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户设置表';

CREATE TABLE user_totp (
    user_id         BIGINT          NOT NULL                   COMMENT '用户ID',
    secret          VARCHAR(64)     NOT NULL                   COMMENT 'TOTP secret (Base32)',
    enabled         TINYINT(1)      NOT NULL DEFAULT 0        COMMENT '是否已启用',
    recovery_codes  JSON            DEFAULT NULL               COMMENT '恢复码列表 (h(bcrypt) 存储)',
    created_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (user_id),
    CONSTRAINT fk_totp_user FOREIGN KEY (user_id) REFERENCES user(id)
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
    CONSTRAINT fk_subscription_user FOREIGN KEY (user_id) REFERENCES user(id),
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
    CONSTRAINT fk_order_user FOREIGN KEY (user_id) REFERENCES user(id)
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
    reviewer_id     BIGINT          DEFAULT NULL             COMMENT '审核人ID (admin_user)',
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
    user_id         BIGINT          DEFAULT NULL             COMMENT '匹配到的用户ID (审核后确定)',
    submitted_info  JSON            NOT NULL                 COMMENT '用户提交的信息 {"email":"...","phone":"..."}',
    status          VARCHAR(16)     NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING|APPROVED|REJECTED|EXPIRED',
    reviewer_id     BIGINT          DEFAULT NULL             COMMENT '审核人ID',
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

CREATE TABLE audit_log (
    id              BIGINT          NOT NULL AUTO_INCREMENT  COMMENT '审计ID',
    event_type      VARCHAR(32)     NOT NULL                 COMMENT '事件类型 USER_LOGIN|IDENTITY_BIND|ADMIN_ACTION|CONFIG_CHANGE|...',
    operator_id     BIGINT          DEFAULT NULL             COMMENT '操作人ID',
    operator_type   VARCHAR(8)      NOT NULL                 COMMENT 'ADMIN|USER|SYSTEM',
    target_type     VARCHAR(32)     DEFAULT NULL             COMMENT '目标类型 USER|ORDER|CONFIG|...',
    target_id       VARCHAR(64)     DEFAULT NULL             COMMENT '目标ID',
    action          VARCHAR(64)     NOT NULL                 COMMENT '具体动作',
    detail_json     JSON            DEFAULT NULL             COMMENT '变更详情 {field, oldValue, newValue}',
    source_ip       VARCHAR(45)     DEFAULT NULL             COMMENT '来源IP (IPv4/IPv6)',
    ip_geo          VARCHAR(64)     DEFAULT NULL             COMMENT 'IP地理位置',
    device_id       CHAR(36)        DEFAULT NULL             COMMENT '设备ID',
    user_agent      VARCHAR(512)    DEFAULT NULL             COMMENT 'User-Agent',
    request_id      CHAR(36)        DEFAULT NULL             COMMENT '请求ID (traceId)',
    created_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_audit_event_type (event_type, created_at),
    KEY idx_audit_operator (operator_id, created_at),
    KEY idx_audit_target (target_type, target_id),
    KEY idx_audit_created (created_at),
    KEY idx_audit_request (request_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='审计日志表';

CREATE TABLE admin_role (
    id              BIGINT          NOT NULL AUTO_INCREMENT  COMMENT '角色ID',
    role_code       VARCHAR(16)     NOT NULL                 COMMENT 'SUPER_ADMIN|ADMIN|CS',
    role_name       VARCHAR(32)     NOT NULL                 COMMENT '角色名称',
    description     VARCHAR(128)    DEFAULT NULL,
    created_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_admin_role_code (role_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='管理后台角色表';

CREATE TABLE admin_permission (
    id              BIGINT          NOT NULL AUTO_INCREMENT  COMMENT '权限ID',
    perm_code       VARCHAR(64)     NOT NULL                 COMMENT '权限码 users|users.export|dashboard|...',
    perm_name       VARCHAR(64)     NOT NULL                 COMMENT '权限名称',
    perm_type       VARCHAR(8)      NOT NULL                 COMMENT 'MENU|BUTTON|API',
    parent_id       BIGINT          DEFAULT NULL             COMMENT '父权限ID (菜单层级)',
    created_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_admin_permission_code (perm_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='管理后台权限表';

CREATE TABLE admin_role_permission (
    role_id         BIGINT          NOT NULL,
    permission_id   BIGINT          NOT NULL,
    PRIMARY KEY (role_id, permission_id),
    CONSTRAINT fk_arp_role FOREIGN KEY (role_id) REFERENCES admin_role(id),
    CONSTRAINT fk_arp_permission FOREIGN KEY (permission_id) REFERENCES admin_permission(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色-权限关联表';

CREATE TABLE admin_user (
    id              BIGINT          NOT NULL AUTO_INCREMENT  COMMENT '管理员ID',
    username        VARCHAR(32)     NOT NULL                 COMMENT '后台用户名',
    password_hash   VARCHAR(60)     NOT NULL                 COMMENT 'BCrypt hash',
    email           VARCHAR(254)    DEFAULT NULL             COMMENT '邮箱',
    phone           VARCHAR(20)     DEFAULT NULL             COMMENT '手机号',
    wechat_openid   VARCHAR(128)    DEFAULT NULL             COMMENT '微信openid',
    wecom_userid    VARCHAR(64)     DEFAULT NULL             COMMENT '企业微信userid',
    totp_secret     VARCHAR(64)     DEFAULT NULL             COMMENT 'TOTP secret',
    totp_enabled    TINYINT(1)      NOT NULL DEFAULT 0      COMMENT 'TOTP是否启用',
    role_id         BIGINT          NOT NULL                 COMMENT '角色ID',
    status          VARCHAR(16)     NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE|DISABLED',
    last_login_at   DATETIME(3)     DEFAULT NULL,
    last_login_ip   VARCHAR(45)     DEFAULT NULL,
    created_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_admin_user_username (username),
    KEY idx_admin_user_role (role_id),
    CONSTRAINT fk_admin_user_role FOREIGN KEY (role_id) REFERENCES admin_role(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='管理后台用户表';

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
