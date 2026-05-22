# UFP Auth 多认证方式支持 — 数据库设计

> ⚠️ **以实际 Flyway 迁移为准** — 本文档为设计讨论记录，最终 DDL 见
> `V1.4.0__ufp_auth_schema.sql` + `V1.5.0__ufp_auth_multi_auth.sql`。

> 基于 2026-05-21 brainstorming 决策，补充 ufp_auth 库表结构以支持完整的多种用户认证方式。

## 1. 背景

当前 `ufp_auth` 库（V1.4.0，18 张表）的 `auth_user` 表已支持密码认证（用户名/邮箱/手机+密码）和 API Token，但缺失：
- 第三方 OAuth 身份关联表（V1.0.0 注释说"已替代"但实际未创建）
- TOTP 双因素认证存储
- WebAuthn 通行密钥存储
- 多设备管理（当前 `auth_user_secure_additional` 仅存最后一次登录设备）
- 登录尝试记录持久化

与 `zhiyu` 业务库重叠检查结论：
- `zhiyu.user_totp` → 迁移到 ufp_auth，废弃旧表
- `zhiyu.login_attempt` → 迁移到 ufp_auth，废弃旧表
- `zhiyu.user_device` → 迁移到 ufp_auth，废弃旧表（除 push_token 保留在业务层）
- `zhiyu.user_auth_identity` → 不存在（V1.0.0 注释已移除），由 `auth_user_identity` 填补

## 2. 设计决策

| 决策项 | 选择 | 说明 |
|--------|------|------|
| OAuth identity 存储 | 新建 `auth_user_identity` 表 | 取代原计划的 `user_auth_identity` |
| TOTP/WebAuthn 存储 | 两张独立表 | `auth_user_totp`（含 recovery_codes）+ `auth_user_web_authn` |
| TOTP 恢复码 | `auth_user_totp.recovery_codes` JSON | bcrypt 哈希存储，从 `zhiyu.user_totp` 迁移 |
| 登录尝试记录 | 迁移到 ufp_auth，更名 `auth_login_attempt` | 原 `zhiyu.login_attempt`，保持字段结构 |
| 多设备管理 | 新建 `auth_user_device` 表 | 取代 `zhiyu.user_device`，push_token 保留在业务层 |
| 注册流程 | 单一主标识 + 可选绑定 | 注册后可在个人中心绑定其他登录方式 |
| 验证码登录 | password 可为 NULL | 支持无密码的纯验证码登录，后续可设置密码 |

## 3. 新增表 DDL

### 3.1 auth_user_identity — 第三方 OAuth 身份关联

```sql
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
    INDEX idx_identity_user (auth_user_id)
) ENGINE = InnoDB
  AUTO_INCREMENT = 1
  CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT = '第三方OAuth身份关联'
  ROW_FORMAT = Dynamic;
```

### 3.2 auth_user_totp — TOTP 双因素认证

```sql
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
    UNIQUE KEY uk_totp_user (auth_user_id)
) ENGINE = InnoDB
  AUTO_INCREMENT = 1
  CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT = 'TOTP双因素认证'
  ROW_FORMAT = Dynamic;
```

### 3.3 auth_user_web_authn — WebAuthn 通行密钥

```sql
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
    INDEX idx_web_authn_user (auth_user_id)
) ENGINE = InnoDB
  AUTO_INCREMENT = 1
  CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT = 'WebAuthn通行密钥'
  ROW_FORMAT = Dynamic;
```

### 3.4 auth_login_attempt — 登录尝试记录

> 从 `zhiyu.login_attempt` 迁移，更名为 `auth_login_attempt` 以符合 ufp 命名规范。

```sql
CREATE TABLE auth_login_attempt
(
    auth_login_attempt_id BIGINT       NOT NULL AUTO_INCREMENT COMMENT '记录ID',
    identifier            VARCHAR(254) NOT NULL COMMENT '尝试标识(邮箱/手机/用户名)',
    attempt_type          VARCHAR(16)  NOT NULL COMMENT 'LOGIN|REGISTER|PASSWORD_RESET|TOTP_VERIFY',
    source_ip             VARCHAR(45)  NOT NULL COMMENT '来源IP',
    device_id             CHAR(36)     DEFAULT NULL COMMENT '设备ID',
    success               TINYINT      NOT NULL DEFAULT 0 COMMENT '是否成功',
    failure_reason        VARCHAR(64)  DEFAULT NULL COMMENT '失败原因: WRONG_PASSWORD|ACCOUNT_LOCKED|CAPTCHA_FAIL|...',
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
```

### 3.5 auth_user_device — 用户设备管理

> 从 `zhiyu.user_device` 迁移，push_token 保留在业务层。

```sql
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
    INDEX idx_device_user_active (auth_user_id, last_active_at)
) ENGINE = InnoDB
  AUTO_INCREMENT = 1
  CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT = '用户设备管理'
  ROW_FORMAT = Dynamic;
```

## 4. 认证方式与字段映射

| 认证方式 | auth_user 必填字段 | 关联表 | password 约束 |
|---------|-------------------|--------|:---:|
| 用户名+密码 | username, password, password_salt | — | NOT NULL |
| 邮箱+密码 | mail, mail_verified=1, password | — | NOT NULL |
| 手机+密码 | mobile, mobile_verified=1, password | — | NOT NULL |
| 邮箱验证码 | mail, mail_verified=1 | — | NULLABLE |
| 手机验证码 | mobile, mobile_verified=1 | — | NULLABLE |
| 第三方 OAuth | — | auth_user_identity | NULLABLE |
| +TOTP | — | auth_user_totp | — |
| +WebAuthn | — | auth_user_web_authn | — |
| 登录安全检查 | — | auth_login_attempt | — |

## 5. 注册流程

```
新用户注册
  │
  ├── 密码注册: username/email/mobile + password → INSERT auth_user
  │     └── 后续可绑定: email(验证), mobile(验证), OAuth, TOTP, WebAuthn
  │
  ├── 验证码注册: email/mobile + captcha → INSERT auth_user (password=NULL)
  │     └── 后续可设置密码 + 绑定其他方式
  │
  └── 第三方注册: OAuth authorize → callback → INSERT auth_user (password=NULL)
                     → INSERT auth_user_identity
        └── 后续可绑定 email/mobile/密码
```

## 6. zhiyu 库重叠表清理

| zhiyu 表 | 状态 | 说明 |
|------|:---:|------|
| `user_totp` | 🔴 废弃 | 迁移到 `ufp_auth.auth_user_totp` |
| `login_attempt` | 🔴 废弃 | 迁移到 `ufp_auth.auth_login_attempt` |
| `user_device` | 🔴 废弃 | 迁移到 `ufp_auth.auth_user_device`（push_token → 合并到 `user_profile`） |
| `user_auth_identity` | — | 不存在，由 `auth_user_identity` 填补 |
| `user_profile` | ✅ 保留 | 用户偏好（语言/时区/通知开关），与认证无关 |

## 7. 表变更汇总

| 变更 | 表名 | 说明 |
|------|------|------|
| 保留 | 原 18 张表 | 不变 |
| 新增 | `auth_user_identity` | OAuth 第三方关联 |
| 新增 | `auth_user_totp` | TOTP 双因素（含 recovery_codes） |
| 新增 | `auth_user_web_authn` | WebAuthn 通行密钥 |
| 新增 | `auth_login_attempt` | 登录尝试记录（从 zhiyu 迁移） |
| 新增 | `auth_user_device` | 用户设备管理（从 zhiyu 迁移，不含 push_token） |
| 废弃 | `zhiyu.user_totp` | 迁移后删除 |
| 废弃 | `zhiyu.login_attempt` | 迁移后删除 |
| 废弃 | `zhiyu.user_device` | 迁移后删除（push_token → `user_profile`） |

**ufp_auth 库总计：23 张表**

## 8. 迁移路径

1. **V1.5.0** — 创建 `auth_user_identity`、`auth_user_totp`、`auth_user_web_authn`、`auth_login_attempt`、`auth_user_device` 五张表
2. **V1.5.1** — 数据迁移：`zhiyu.login_attempt` → `ufp_auth.auth_login_attempt`，`zhiyu.user_totp` → `ufp_auth.auth_user_totp`，`zhiyu.user_device` → `ufp_auth.auth_user_device`（push_token → `user_profile`）
3. **V1.5.2** — 删除 `zhiyu.login_attempt`、`zhiyu.user_totp`、`zhiyu.user_device` 表
4. **P1 阶段** — 全面使用 ufp_auth 统一认证存储
