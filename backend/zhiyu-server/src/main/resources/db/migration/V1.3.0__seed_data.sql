-- ============================================================
-- V1.3.0: 业务初始种子数据（zhiyu 库）
-- 包含套餐定义、通知模板
-- UFP auth 种子数据（角色、权限、管理员）见 V1.4.1
-- ============================================================

-- 套餐初始数据
INSERT INTO subscription_plan (plan_key, name, price_monthly, price_yearly, trial_days, features_json, quotas_json, sort_order) VALUES
('free', '免费游客', 0,    0,    0, '["basic_chat","text_search"]',
   '{"daily_chat":10,"file_upload_mb":5}', 0),
('lite', 'Lite',    2900, 29000, 7, '["basic_chat","text_search","file_upload","image_gen"]',
   '{"daily_chat":200,"file_upload_mb":50,"image_gen_daily":20}', 1),
('pro',  'Pro',     9900, 99000, 0, '["basic_chat","text_search","file_upload","image_gen","priority_queue"]',
   '{"daily_chat":-1,"file_upload_mb":500,"image_gen_daily":200,"priority_queue":true}', 2);

-- 通知模板初始数据
INSERT INTO notification_template (template_key, type, subject, body, variables_json, description) VALUES
('register_welcome',  'EMAIL', '欢迎注册 ZhiYu', '<html><body><h2>欢迎 {{username}}！</h2><p>您已成功注册 {{app_name}}，开始探索 AI 的无限可能吧。</p></body></html>', '["username","app_name"]', '注册欢迎邮件'),
('reset_password',    'EMAIL', '重置密码 - ZhiYu', '<html><body><h2>密码重置</h2><p>点击下方链接重置密码，有效期 {{expire_hours}} 小时：</p><a href="{{reset_link}}">重置密码</a></body></html>', '["username","reset_link","expire_hours","app_name"]', '密码重置邮件'),
('email_verify',      'EMAIL', '验证邮箱 - ZhiYu', '<html><body><h2>邮箱验证</h2><p>您的验证码是：<strong>{{code}}</strong>，有效期 {{expire_minutes}} 分钟。</p></body></html>', '["code","expire_minutes","app_name"]', '邮箱验证码'),
('welcome_sms',       'SMS',   NULL, '【{{app_name}}】欢迎 {{username}}，您已成功注册！', '["username","app_name"]', '注册欢迎短信'),
('login_alert',       'EMAIL', '登录提醒 - ZhiYu', '<html><body><h2>登录提醒</h2><p>您的账户于 {{login_time}} 在 {{ip_geo}} (IP: {{login_ip}}) 通过 {{device_name}} 登录。</p><p>如非本人操作，请立即修改密码。</p></body></html>', '["username","login_time","login_ip","ip_geo","device_name","app_name"]', '新设备登录提醒');
