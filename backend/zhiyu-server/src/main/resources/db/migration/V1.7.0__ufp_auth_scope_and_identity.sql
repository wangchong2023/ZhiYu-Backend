-- ============================================================
-- V1.7.0: UFP Auth scope + OAuth identity user info
-- 1. auth_user: 新增 auth_user_scope 字段（LIMITED/FULL）
-- 2. auth_user_identity: 新增 nickname + avatar_url
-- ============================================================

-- ── auth_user: 新增权限范围字段 ──────────────────────────────
ALTER TABLE auth_user
    ADD COLUMN auth_user_scope VARCHAR(16) DEFAULT 'FULL' COMMENT '权限范围: LIMITED(仅/auth/* + /user/bind-email) | FULL(全部权限)'
    AFTER auth_user_deleted;

-- ── auth_user_identity: 新增第三方用户信息字段 ──────────────────
ALTER TABLE auth_user_identity
    ADD COLUMN nickname   VARCHAR(100)  DEFAULT NULL COMMENT '第三方平台昵称'
    AFTER unionid,
    ADD COLUMN avatar_url VARCHAR(500)  DEFAULT NULL COMMENT '第三方平台头像URL'
    AFTER nickname;
