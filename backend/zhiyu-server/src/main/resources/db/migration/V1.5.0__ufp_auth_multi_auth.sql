-- ============================================================
-- V1.5.0: UFP Auth 多认证方式 DDL
-- 新增 5 张表: auth_user_identity, auth_user_totp,
--             auth_user_web_authn, auth_login_attempt,
--             auth_user_device
-- ufp_auth 库: 18 → 23 张表
-- ============================================================

-- ── 第三方 OAuth 身份关联 ──────────────────────────────────
CREATE TABLE auth_user_identity
(
    auth_user_identity_id BIGINT       NOT NULL AUTO_INCREMENT COMMENT '第三方身份编号',
    auth_user_id          BIGINT       NOT NULL COMMENT '用户编号',
    provider              VARCHAR(20)  NOT NULL COMMENT '平台: WECHAT|QQ|GOOGLE|APPLE',
    openid                VARCHAR(200) NOT NULL COMMENT '平台用户唯一标识',
    unionid               VARCHAR(200) DEFAULT NULL COMMENT '微信开放平台统一ID',
    credential            VARCHAR(2000) DEFAULT NULL COMMENT '平台凭证(AES加密)',
    refresh_token         VARCHAR(500)  DEFAULT NULL COMMENT '刷新令牌(AES加密)',
    token_expire          DATETIME      DEFAULT NULL COMMENT '凭证过期时间',
    enabled               TINYINT       DEFAULT 1 COMMENT '是否启用此绑定',
    created_time          DATETIME      DEFAULT NULL COMMENT '绑定时间',
    PRIMARY KEY (auth_user_identity_id),
    UNIQUE KEY uk_identity_provider_openid (provider, openid),
    INDEX idx_identity_user (auth_user_id),
    CONSTRAINT fk_identity_user FOREIGN KEY (auth_user_id) REFERENCES auth_user(auth_user_id)
) ENGINE = InnoDB
  AUTO_INCREMENT = 1
  CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT = '第三方OAuth身份关联'
  ROW_FORMAT = Dynamic;

-- ── TOTP 双因素认证 ───────────────────────────────────────
CREATE TABLE auth_user_totp
(
    auth_user_totp_id BIGINT       NOT NULL AUTO_INCREMENT COMMENT 'TOTP编号',
    auth_user_id      BIGINT       NOT NULL COMMENT '用户编号',
    secret            VARCHAR(200) NOT NULL COMMENT 'TOTP密钥(AES加密存储)',
    recovery_codes    JSON         DEFAULT NULL COMMENT '恢复码列表(bcrypt哈希存储,每码仅用一次)',
    enabled           TINYINT      DEFAULT 0 COMMENT '是否已激活(0=待扫码确认,1=已激活)',
    created_time      DATETIME     DEFAULT NULL COMMENT '创建时间',
    activated_time    DATETIME     DEFAULT NULL COMMENT '激活时间',
    PRIMARY KEY (auth_user_totp_id),
    UNIQUE KEY uk_totp_user (auth_user_id),
    CONSTRAINT fk_auth_totp_user FOREIGN KEY (auth_user_id) REFERENCES auth_user(auth_user_id)
) ENGINE = InnoDB
  AUTO_INCREMENT = 1
  CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT = 'TOTP双因素认证'
  ROW_FORMAT = Dynamic;

-- ── WebAuthn 通行密钥 ─────────────────────────────────────
CREATE TABLE auth_user_web_authn
(
    auth_user_web_authn_id BIGINT        NOT NULL AUTO_INCREMENT COMMENT 'WebAuthn编号',
    auth_user_id           BIGINT        NOT NULL COMMENT '用户编号',
    credential_id          VARCHAR(500)  NOT NULL COMMENT 'WebAuthn凭证ID',
    public_key             VARCHAR(2000) NOT NULL COMMENT '公钥',
    sign_count             INT           DEFAULT 0 COMMENT '签名计数器(防重放)',
    device_name            VARCHAR(100)  DEFAULT NULL COMMENT '设备名称(用户可识别)',
    enabled                TINYINT       DEFAULT 1 COMMENT '是否启用',
    created_time           DATETIME      DEFAULT NULL COMMENT '注册时间',
    last_used_time         DATETIME      DEFAULT NULL COMMENT '最近使用时间',
    PRIMARY KEY (auth_user_web_authn_id),
    UNIQUE KEY uk_web_authn_credential (credential_id),
    INDEX idx_web_authn_user (auth_user_id),
    CONSTRAINT fk_web_authn_user FOREIGN KEY (auth_user_id) REFERENCES auth_user(auth_user_id)
) ENGINE = InnoDB
  AUTO_INCREMENT = 1
  CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT = 'WebAuthn通行密钥'
  ROW_FORMAT = Dynamic;

-- ── 登录尝试记录 ──────────────────────────────────────────
-- 从 zhiyu.login_attempt 迁移，更名为 auth_login_attempt
CREATE TABLE auth_login_attempt
(
    auth_login_attempt_id BIGINT       NOT NULL AUTO_INCREMENT COMMENT '记录ID',
    identifier            VARCHAR(254) NOT NULL COMMENT '尝试标识(邮箱/手机/用户名)',
    attempt_type          VARCHAR(16)  NOT NULL COMMENT 'LOGIN|REGISTER|PASSWORD_RESET|TOTP_VERIFY',
    source_ip             VARCHAR(45)  NOT NULL COMMENT '来源IP',
    device_id             CHAR(36)     DEFAULT NULL COMMENT '设备ID',
    success               TINYINT      NOT NULL DEFAULT 0 COMMENT '是否成功',
    failure_reason        VARCHAR(64)  DEFAULT NULL COMMENT '失败原因: WRONG_PASSWORD|ACCOUNT_LOCKED|CAPTCHA_FAIL|TOTP_FAIL|WEB_AUTHN_FAIL',
    user_agent            VARCHAR(512) DEFAULT NULL COMMENT 'User-Agent',
    created_time          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (auth_login_attempt_id),
    INDEX idx_login_attempt_identifier (identifier, created_time),
    INDEX idx_login_attempt_ip (source_ip, created_time),
    INDEX idx_login_attempt_type_success (attempt_type, success, created_time)
) ENGINE = InnoDB
  AUTO_INCREMENT = 1
  CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT = '登录尝试记录'
  ROW_FORMAT = Dynamic;

-- ── 用户设备管理 ──────────────────────────────────────────
-- 从 zhiyu.user_device 迁移，push_token 已合并到 user_profile
CREATE TABLE auth_user_device
(
    auth_user_device_id BIGINT       NOT NULL AUTO_INCREMENT COMMENT '设备记录编号',
    auth_user_id        BIGINT       NOT NULL COMMENT '用户编号',
    device_id           CHAR(36)     NOT NULL COMMENT '设备UUID(客户端生成)',
    device_name         VARCHAR(128) DEFAULT NULL COMMENT '设备名称',
    platform            VARCHAR(16)  DEFAULT NULL COMMENT '平台: IOS|ANDROID|WEB',
    trusted_for_totp    TINYINT      DEFAULT 0 COMMENT '是否信任跳过TOTP验证',
    last_active_at      DATETIME     DEFAULT NULL COMMENT '最后活跃时间',
    created_time        DATETIME     DEFAULT NULL COMMENT '首次注册时间',
    PRIMARY KEY (auth_user_device_id),
    UNIQUE KEY uk_device_user (auth_user_id, device_id),
    INDEX idx_device_user_active (auth_user_id, last_active_at),
    CONSTRAINT fk_auth_device_user FOREIGN KEY (auth_user_id) REFERENCES auth_user(auth_user_id)
) ENGINE = InnoDB
  AUTO_INCREMENT = 1
  CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT = '用户设备管理'
  ROW_FORMAT = Dynamic;
