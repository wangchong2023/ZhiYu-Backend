-- ============================================================
-- V1.4.1: UFP auth 初始种子数据（ufp_auth 库）
-- 包含角色、资源树、授权、默认管理员
-- ============================================================

-- ── 角色 ────────────────────────────────────────────────────
INSERT INTO auth_role (auth_role_name, auth_role_code, auth_role_enable, auth_role_desc, created_user, created_time) VALUES
('超级管理员', 'SUPER_ADMIN', 1, '所有权限 + 后台用户管理', 'SYSTEM', NOW()),
('管理员',     'ADMIN',       1, '用户管理 + 订阅管理',   'SYSTEM', NOW()),
('客服',       'CS',          1, '仅查看用户信息',        'SYSTEM', NOW());

-- ── 资源树（管理后台菜单 + 按钮）─────────────────────────────
-- auth_res_type: 1=MENU, 2=BUTTON

-- 顶层菜单
INSERT INTO auth_res (auth_res_pid, auth_res_name, auth_res_type, auth_res_code, auth_res_index, auth_res_enable, auth_res_desc) VALUES
(NULL, '仪表盘',       1, 'dashboard',         1, 1, '仪表盘概览'),
(NULL, '用户管理',     1, 'users',             2, 1, '用户列表与管理'),
(NULL, '订阅管理',     1, 'subscriptions',     3, 1, '订阅与套餐管理'),
(NULL, '支付管理',     1, 'payments',          4, 1, '支付记录与对账'),
(NULL, '退款审核',     1, 'refunds',           5, 1, '退款申请审核'),
(NULL, '审计日志',     1, 'audit',             6, 1, '操作审计日志'),
(NULL, '后台用户管理', 1, 'admins',            7, 1, '管理员账户管理'),
(NULL, '系统配置',     1, 'config',            8, 1, '系统配置管理'),
(NULL, '运行监控',     1, 'monitor',           9, 1, '服务运行监控'),
(NULL, '日志管理',     1, 'logs',             10, 1, '应用日志管理'),
(NULL, '通知模板',     1, 'notifications',    11, 1, '通知模板管理');

-- 用户管理子按钮
SET @res_users_id = (SELECT auth_res_id FROM auth_res WHERE auth_res_code = 'users');
INSERT INTO auth_res (auth_res_pid, auth_res_name, auth_res_type, auth_res_code, auth_res_index, auth_res_enable, auth_res_desc) VALUES
(@res_users_id, '用户导出',  2, 'users.export',        1, 1, '导出用户列表'),
(@res_users_id, '批量禁用',  2, 'users.batch-disable', 2, 1, '批量禁用用户'),
(@res_users_id, '查看身份',  2, 'users.view-identity', 3, 1, '查看用户认证身份');

-- 订阅管理子按钮
SET @res_subscriptions_id = (SELECT auth_res_id FROM auth_res WHERE auth_res_code = 'subscriptions');
INSERT INTO auth_res (auth_res_pid, auth_res_name, auth_res_type, auth_res_code, auth_res_index, auth_res_enable, auth_res_desc) VALUES
(@res_subscriptions_id, '手动修改', 2, 'subscriptions.modify', 1, 1, '手动修改用户订阅');

-- 支付管理子按钮
SET @res_payments_id = (SELECT auth_res_id FROM auth_res WHERE auth_res_code = 'payments');
INSERT INTO auth_res (auth_res_pid, auth_res_name, auth_res_type, auth_res_code, auth_res_index, auth_res_enable, auth_res_desc) VALUES
(@res_payments_id, '支付导出', 2, 'payments.export',    1, 1, '导出支付记录'),
(@res_payments_id, '对账操作', 2, 'payments.reconcile', 2, 1, '执行对账操作');

-- 退款审核子按钮
SET @res_refunds_id = (SELECT auth_res_id FROM auth_res WHERE auth_res_code = 'refunds');
INSERT INTO auth_res (auth_res_pid, auth_res_name, auth_res_type, auth_res_code, auth_res_index, auth_res_enable, auth_res_desc) VALUES
(@res_refunds_id, '退款审批', 2, 'refunds.approve', 1, 1, '审批退款申请');

-- 审计日志子按钮
SET @res_audit_id = (SELECT auth_res_id FROM auth_res WHERE auth_res_code = 'audit');
INSERT INTO auth_res (auth_res_pid, auth_res_name, auth_res_type, auth_res_code, auth_res_index, auth_res_enable, auth_res_desc) VALUES
(@res_audit_id, '日志导出', 2, 'audit.export', 1, 1, '导出审计日志');

-- 系统配置子按钮
SET @res_config_id = (SELECT auth_res_id FROM auth_res WHERE auth_res_code = 'config');
INSERT INTO auth_res (auth_res_pid, auth_res_name, auth_res_type, auth_res_code, auth_res_index, auth_res_enable, auth_res_desc) VALUES
(@res_config_id, '编辑配置', 2, 'config.edit',    1, 1, '编辑系统配置'),
(@res_config_id, '回滚配置', 2, 'config.rollback', 2, 1, '回滚配置版本');

-- 日志管理子按钮
SET @res_logs_id = (SELECT auth_res_id FROM auth_res WHERE auth_res_code = 'logs');
INSERT INTO auth_res (auth_res_pid, auth_res_name, auth_res_type, auth_res_code, auth_res_index, auth_res_enable, auth_res_desc) VALUES
(@res_logs_id, '日志级别', 2, 'logs.settings', 1, 1, '动态调整日志级别');

-- ── 授权（角色 → 资源）──────────────────────────────────────
-- auth_grant_owner_type: 1=角色
-- auth_grant_target_type: 1=资源
-- auth_grant_action: 1=允许

-- SUPER_ADMIN: 拥有全部资源
INSERT INTO auth_grant (auth_grant_owner_type, auth_grant_owner_value, auth_grant_target_type, auth_grant_target_value, auth_grant_action, created_user, created_time)
SELECT 1, 'SUPER_ADMIN', 1, auth_res_code, 1, 'SYSTEM', NOW()
FROM auth_res;

-- ADMIN: 除 admins, config.edit, config.rollback, logs.settings 外全部
INSERT INTO auth_grant (auth_grant_owner_type, auth_grant_owner_value, auth_grant_target_type, auth_grant_target_value, auth_grant_action, created_user, created_time)
SELECT 1, 'ADMIN', 1, auth_res_code, 1, 'SYSTEM', NOW()
FROM auth_res
WHERE auth_res_code NOT IN ('admins', 'config.edit', 'config.rollback', 'logs.settings');

-- CS: 仅 dashboard, users, subscriptions, payments, refunds（查看权限）
INSERT INTO auth_grant (auth_grant_owner_type, auth_grant_owner_value, auth_grant_target_type, auth_grant_target_value, auth_grant_action, created_user, created_time)
SELECT 1, 'CS', 1, auth_res_code, 1, 'SYSTEM', NOW()
FROM auth_res
WHERE auth_res_code IN ('dashboard', 'users', 'subscriptions', 'payments', 'refunds');

-- ── 默认管理员用户 ───────────────────────────────────────────
-- 密码: admin123 (BCrypt, cost=10) — 首次部署后请立即修改
INSERT INTO auth_user (
    auth_user_code, auth_user_nick, auth_user_username,
    auth_user_username_login_enable, auth_user_mail, auth_user_mail_verified,
    auth_user_password,
    auth_user_enable, auth_user_deleted, created_user, created_time
) VALUES (
    'super-admin', '超级管理员', 'admin',
    1, 'admin@zhiyu.local', 0,
    '$2a$10$EixZaYVK1fsbw1ZfbX3OXePaWxn96p36WQoeG6Lruj3vjPGga31lW',
    1, 0, 'SYSTEM', NOW()
);

-- 管理员 → SUPER_ADMIN 角色
SET @admin_user_id = (SELECT auth_user_id FROM auth_user WHERE auth_user_username = 'admin');
SET @super_admin_role_id = (SELECT auth_role_id FROM auth_role WHERE auth_role_code = 'SUPER_ADMIN');
INSERT INTO auth_role_user_relation (auth_role_id, auth_user_id) VALUES
(@super_admin_role_id, @admin_user_id);
