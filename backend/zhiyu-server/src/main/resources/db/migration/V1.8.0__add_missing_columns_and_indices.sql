-- ==============================================================================
-- ZhiYu-Backend 数据库迁移 V1.8.0
-- 描述: 补齐 user_profile.push_token / auth_operation_log.detail_json 列 &
--       补齐生产环境常用索引
-- 数据库: zhiyu (业务表) / ufp_auth (认证表)
-- 依赖: V1.7.0__ufp_auth_scope_and_identity.sql
-- ==============================================================================

-- ── 1. user_profile 追加 push_token 列 ─────────────────────────────────
-- V1.5.0 注释已声明 "push_token 已合并到 user_profile"，
-- 但 V1.0.0 初始 DDL 未包含该列，此处补齐
ALTER TABLE user_profile
    ADD COLUMN push_token VARCHAR(512) DEFAULT NULL COMMENT '推送Token'
    AFTER notification_sms;

-- ── 2. auth_operation_log 追加 detail_json 列 ──────────────────────────
-- 存储操作变更的旧值/新值详情，见 DATABASE.md §4.8
ALTER TABLE auth_operation_log
    ADD COLUMN detail_json JSON DEFAULT NULL COMMENT '操作详情JSON'
    AFTER operation_desc;

-- ── 3. auth_user 业务索引补齐 ──────────────────────────────────────────
-- idx_user_status: 后台用户列表按启用状态筛选
CREATE INDEX idx_user_status ON auth_user (auth_user_enable);

-- idx_user_deleted: 定时任务查找已标记删除的注销用户
CREATE INDEX idx_user_deleted ON auth_user (auth_user_deleted);

-- ── 4. auth_operation_log 审计索引补齐 ─────────────────────────────────
-- idx_audit_event_type: 审计日志按事件类型 + 时间范围检索
CREATE INDEX idx_audit_event_type ON auth_operation_log (operation_type, log_time);

-- idx_audit_operator: 审计日志按操作人检索
CREATE INDEX idx_audit_operator ON auth_operation_log (user_id);
