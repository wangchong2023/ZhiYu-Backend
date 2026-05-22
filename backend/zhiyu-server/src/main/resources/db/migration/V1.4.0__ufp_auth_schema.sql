-- ============================================================
-- V1.4.0: UFP 统一基础平台认证授权 DDL
-- 数据库: ufp_auth，共 18 张表
-- 已移除: auth_user_boundary, auth_user_boundary_config,
--         auth_res_boundary 及其关联字段
-- ============================================================

-- ── 编程账号（API Token） ───────────────────────────────────
CREATE TABLE auth_access_token
(
    auth_access_token_id           int(11) NOT NULL AUTO_INCREMENT COMMENT '编程账号编号',
    auth_access_token_key          varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '编程账号Key',
    auth_access_token_secret       varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '编程账号秘钥',
    auth_access_token_expire       datetime                                                      DEFAULT NULL COMMENT '编程账号过期时间',
    auth_access_token_grant_enable int(11)                                                       DEFAULT NULL COMMENT '编程账号授权检查启停',
    auth_access_token_enable       int(11)                                                       DEFAULT NULL COMMENT '编程账号是否启用',
    auth_access_token_desc         varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '编程账号备注',
    created_user                   varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '创建人',
    created_time                   datetime                                                      DEFAULT NULL COMMENT '创建时间',
    modify_user                    varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '修改人',
    modify_time                    datetime                                                      DEFAULT NULL COMMENT '修改时间',
    PRIMARY KEY (auth_access_token_id) USING BTREE
) ENGINE = InnoDB
  AUTO_INCREMENT = 1
  CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT = '编程账号'
  ROW_FORMAT = Dynamic;

-- ── 授权 ──────────────────────────────────────────────────
CREATE TABLE auth_grant
(
    auth_grant_id           int(11) NOT NULL AUTO_INCREMENT COMMENT '授权编号',
    auth_grant_owner_type   int(11)                                                       DEFAULT NULL COMMENT '授权主体类型(用户|角色|组织|应用)',
    auth_grant_owner_value  varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '授权主体值(用户标识|角色标识|组织标识|应用标识)',
    auth_grant_target_type  int(11)                                                       DEFAULT NULL COMMENT '授权目标类型(资源|应用)',
    auth_grant_target_value varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '授权目标值(资源标识符|应用标识符|用户标识符)',
    auth_grant_action       int(11)                                                       DEFAULT NULL COMMENT '授权动作(允许|拒绝)',
    created_user            varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '创建人',
    created_time            datetime                                                      DEFAULT NULL COMMENT '创建时间',
    modify_user             varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '修改人',
    modify_time             datetime                                                      DEFAULT NULL COMMENT '修改时间',
    PRIMARY KEY (auth_grant_id) USING BTREE,
    INDEX auth_grant_target_type (auth_grant_target_type, auth_grant_target_value) USING BTREE
) ENGINE = InnoDB
  AUTO_INCREMENT = 1
  CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT = '授权'
  ROW_FORMAT = Dynamic;

-- ── 授权策略（条件访问规则） ────────────────────────────────
CREATE TABLE auth_grant_policy
(
    auth_grant_policy_id             int(11) NOT NULL AUTO_INCREMENT COMMENT '授权扩展规则编号',
    auth_grant_policy_unique         varchar(40) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci  DEFAULT NULL COMMENT '授权策略唯一标识',
    auth_grant_policy_owner_type     varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci  DEFAULT NULL COMMENT '授权扩展主体类型(用户|角色|组织|应用)',
    auth_grant_policy_owner_value    varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '授权扩展主体值(用户标识|角色标识|组织标识|应用标识)',
    auth_grant_policy_source         varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '授权策略规则源(登录次数,登录失败次数,用户IP|用户省份|用户注册来源|用户请求来源省份|当前时间)',
    auth_grant_policy_condition      varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '授权扩展规则条件(完全匹配|大于|小于|等等)',
    auth_grant_policy_value          varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '授权策略规则值(窗口频率(1m,10次),192.168.1.1|上海|9:00|17:00等等)',
    auth_grant_policy_action         varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '授权策略规则动作(允许|拒绝|封禁IP-X秒|封禁用户-X秒|自定义)',
    auth_grant_policy_action_message varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '授权策略规则动作触发提示',
    auth_grant_policy_enable         int(2)                                                        DEFAULT NULL COMMENT '授权策略规则启用',
    created_user                     varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '创建人',
    created_time                     datetime                                                      DEFAULT NULL COMMENT '创建时间',
    modify_user                      varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '修改人',
    modify_time                      datetime                                                      DEFAULT NULL COMMENT '修改时间',
    PRIMARY KEY (auth_grant_policy_id) USING BTREE
) ENGINE = InnoDB
  AUTO_INCREMENT = 1
  CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT = '授权策略'
  ROW_FORMAT = Dynamic;

-- ── 组织机构 ────────────────────────────────────────────────
CREATE TABLE auth_org
(
    auth_org_id        int(11) NOT NULL AUTO_INCREMENT COMMENT '组织机构编号',
    auth_org_pid       int(11)                                                       DEFAULT NULL COMMENT '组织机构父编号',
    auth_org_name      varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '组织机构名称',
    auth_org_code      varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '组织机构标识符',
    auth_org_data_code varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '组织机构数据标识符',
    auth_org_level     varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '组织机构层级',
    auth_org_index     int(11)                                                       DEFAULT NULL COMMENT '组织机构排序',
    auth_org_enable    int(2)                                                        DEFAULT NULL COMMENT '组织机构启用停用',
    auth_org_desc      varchar(300) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '组织机构描述',
    created_user       varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '创建人',
    created_time       datetime                                                      DEFAULT NULL COMMENT '创建时间',
    modify_user        varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '修改人',
    modify_time        datetime                                                      DEFAULT NULL COMMENT '修改时间',
    PRIMARY KEY (auth_org_id) USING BTREE
) ENGINE = InnoDB
  AUTO_INCREMENT = 1
  CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT = '组织机构'
  ROW_FORMAT = Dynamic;

-- ── 组织机构扩展 ────────────────────────────────────────────
CREATE TABLE auth_org_additional
(
    auth_org_additional_id    int(11) NOT NULL AUTO_INCREMENT COMMENT '组织机构扩展编号',
    auth_org_id               int(11)                                                       DEFAULT NULL COMMENT '组织机构编号',
    auth_org_additional_name  varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '组织机构扩展名称',
    auth_org_additional_key   varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci  DEFAULT NULL COMMENT '组织机构扩展键',
    auth_org_additional_value varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '组织机构扩展值',
    PRIMARY KEY (auth_org_additional_id) USING BTREE
) ENGINE = InnoDB
  AUTO_INCREMENT = 1
  CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT = '组织机构扩展'
  ROW_FORMAT = Dynamic;

-- ── 组织机构用户关系 ────────────────────────────────────────
CREATE TABLE auth_org_user_relation
(
    auth_org_user_relation_id int(11) NOT NULL AUTO_INCREMENT COMMENT '组织机构用户关系编号',
    auth_org_id               int(11) DEFAULT NULL COMMENT '组织机构编号',
    auth_user_id              BIGINT  DEFAULT NULL COMMENT '用户编号',
    PRIMARY KEY (auth_org_user_relation_id) USING BTREE,
    INDEX idx_auth_org_user_relation_user (auth_user_id)
) ENGINE = InnoDB
  AUTO_INCREMENT = 1
  CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT = '组织机构用户关系'
  ROW_FORMAT = Dynamic;

-- ── 资源（权限资源树） ──────────────────────────────────────
CREATE TABLE auth_res
(
    auth_res_id    int(11) NOT NULL AUTO_INCREMENT COMMENT '资源编号',
    auth_res_pid   int(11)                                                       DEFAULT NULL COMMENT '资源父级编号',
    auth_res_name  varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci  DEFAULT NULL COMMENT '资源名称',
    auth_res_type  int(11)                                                       DEFAULT NULL COMMENT '资源类型',
    auth_res_code  varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '资源标识符',
    auth_res_index int(11)                                                       DEFAULT NULL COMMENT '资源排序',
    auth_res_enable int(2)                                                       DEFAULT NULL COMMENT '资源启用停用',
    auth_res_desc  varchar(300) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '资源描述',
    PRIMARY KEY (auth_res_id) USING BTREE
) ENGINE = InnoDB
  AUTO_INCREMENT = 1
  CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT = '资源'
  ROW_FORMAT = Dynamic;

-- ── 资源扩展 ────────────────────────────────────────────────
CREATE TABLE auth_res_additional
(
    auth_res_additional_id    int(11) NOT NULL AUTO_INCREMENT COMMENT '资源扩展编号',
    auth_res_id               int(11)                                                       DEFAULT NULL COMMENT '资源编号',
    auth_res_additional_name  varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '资源扩展名称',
    auth_res_additional_key   varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci  DEFAULT NULL COMMENT '资源扩展键',
    auth_res_additional_value varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '资源扩展值',
    PRIMARY KEY (auth_res_additional_id) USING BTREE
) ENGINE = InnoDB
  AUTO_INCREMENT = 1
  CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT = '资源扩展'
  ROW_FORMAT = Dynamic;

-- ── 角色 ────────────────────────────────────────────────────
CREATE TABLE auth_role
(
    auth_role_id    int(11) NOT NULL AUTO_INCREMENT COMMENT '角色编号',
    auth_role_name  varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '角色名称',
    auth_role_code  varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '角色标识符',
    auth_role_enable int(11)                                                       DEFAULT NULL COMMENT '角色启停',
    auth_role_desc  varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '角色描述',
    created_user    varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '创建人',
    created_time    datetime                                                      DEFAULT NULL COMMENT '创建时间',
    modify_user     varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '修改人',
    modify_time     datetime                                                      DEFAULT NULL COMMENT '修改时间',
    PRIMARY KEY (auth_role_id) USING BTREE
) ENGINE = InnoDB
  AUTO_INCREMENT = 1
  CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT = '角色'
  ROW_FORMAT = Dynamic;

-- ── 角色扩展 ────────────────────────────────────────────────
CREATE TABLE auth_role_additional
(
    auth_role_additional_id    int(11) NOT NULL AUTO_INCREMENT COMMENT '角色扩展编号',
    auth_role_id               int(11)                                                       DEFAULT NULL COMMENT '角色编号',
    auth_role_additional_name  varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '角色扩展名称',
    auth_role_additional_key   varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci  DEFAULT NULL COMMENT '角色扩展键',
    auth_role_additional_value varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '角色扩展值',
    PRIMARY KEY (auth_role_additional_id) USING BTREE
) ENGINE = InnoDB
  AUTO_INCREMENT = 1
  CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT = '角色扩展'
  ROW_FORMAT = Dynamic;

-- ── 角色-用户关系 ───────────────────────────────────────────
CREATE TABLE auth_role_user_relation
(
    auth_role_user_relation_id int(11) NOT NULL AUTO_INCREMENT COMMENT '角色用户关系编号',
    auth_role_id               int(11) DEFAULT NULL COMMENT '角色编号',
    auth_user_id               BIGINT  DEFAULT NULL COMMENT '用户编号',
    PRIMARY KEY (auth_role_user_relation_id) USING BTREE,
    INDEX auth_role_user_relation_idx (auth_role_id, auth_user_id) USING BTREE,
    INDEX idx_auth_role_user_relation_user (auth_user_id)
) ENGINE = InnoDB
  AUTO_INCREMENT = 1
  CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT = '角色用户关系'
  ROW_FORMAT = Dynamic;

-- ── 通用 Token 存储 ─────────────────────────────────────────
CREATE TABLE auth_token
(
    token_id int(11) NOT NULL AUTO_INCREMENT COMMENT 'Token主键',
    id       varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci  DEFAULT NULL COMMENT 'Token编号',
    type     int(11)                                                        DEFAULT NULL COMMENT 'Token类型',
    expire   datetime                                                       DEFAULT NULL COMMENT 'Token过期时间',
    value    varchar(2000) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'Token值',
    relation varchar(2000) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'Token关系值',
    PRIMARY KEY (token_id) USING BTREE
) ENGINE = InnoDB
  AUTO_INCREMENT = 1
  CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT = '存储各类token信息'
  ROW_FORMAT = Dynamic;

-- ── 用户 ────────────────────────────────────────────────────
CREATE TABLE auth_user
(
    auth_user_id                    BIGINT NOT NULL AUTO_INCREMENT COMMENT '用户编号',
    auth_user_code                  varchar(80) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci   DEFAULT NULL COMMENT '用户标识符',
    auth_user_nick                  varchar(80) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci   DEFAULT NULL COMMENT '用户昵称',
    auth_user_username              varchar(80) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci   DEFAULT NULL COMMENT '用户登录名',
    auth_user_username_login_enable int(11)                                                        DEFAULT NULL COMMENT '用户登录名是否启用登录',
    auth_user_mail                  varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci  DEFAULT NULL COMMENT '用户邮箱',
    auth_user_mail_verified         int(11)                                                        DEFAULT NULL COMMENT '用户邮箱是否验证',
    auth_user_mail_login_enable     int(11)                                                        DEFAULT NULL COMMENT '用户邮箱是否启用登录',
    auth_user_mobile                varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci  DEFAULT NULL COMMENT '用户手机',
    auth_user_mobile_verified       int(11)                                                        DEFAULT NULL COMMENT '用户手机是否验证',
    auth_user_mobile_login_enable   int(11)                                                        DEFAULT NULL COMMENT '用户手机是否启用登录',
    auth_user_password              varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci  DEFAULT NULL COMMENT '用户密码',
    auth_user_password_salt         varchar(80) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci   DEFAULT NULL COMMENT '用户密码盐值',
    auth_user_password_expire       datetime                                                       DEFAULT NULL COMMENT '用户密码有效期',
    auth_user_password_history      varchar(2000) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '用户密码历史',
    auth_user_enable                int(11)                                                        DEFAULT NULL COMMENT '用户是否启用',
    auth_user_enable_expire         datetime                                                       DEFAULT NULL COMMENT '用户禁用(即不启用)时间-无时间为长期启用',
    auth_user_deleted               int(2)                                                         DEFAULT NULL COMMENT '用户是否删除',
    created_user                    varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci  DEFAULT NULL COMMENT '创建人',
    created_time                    datetime                                                       DEFAULT NULL COMMENT '创建时间',
    modify_user                     varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci  DEFAULT NULL COMMENT '修改人',
    modify_time                     datetime                                                       DEFAULT NULL COMMENT '修改时间',
    PRIMARY KEY (auth_user_id) USING BTREE,
    UNIQUE KEY uk_auth_user_username (auth_user_username),
    UNIQUE KEY uk_auth_user_mail (auth_user_mail),
    UNIQUE KEY uk_auth_user_mobile (auth_user_mobile)
) ENGINE = InnoDB
  AUTO_INCREMENT = 1
  CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT = '用户'
  ROW_FORMAT = Dynamic;

-- ── 用户自定义字段扩展 ──────────────────────────────────────
CREATE TABLE auth_user_field_additional
(
    auth_user_field_id    int(11) NOT NULL AUTO_INCREMENT COMMENT '用户自定义扩展编号',
    auth_user_id          BIGINT                                                       DEFAULT NULL COMMENT '用户编号',
    auth_user_field_name  varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '用户自定义扩展字段名',
    auth_user_field_key   varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci  DEFAULT NULL COMMENT '用户自定义扩展字段类型',
    auth_user_field_value varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '用户自定义扩展字段值',
    PRIMARY KEY (auth_user_field_id) USING BTREE,
    INDEX idx_auth_user_field_user (auth_user_id)
) ENGINE = InnoDB
  AUTO_INCREMENT = 1
  CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT = '用户自定义字段扩展'
  ROW_FORMAT = Dynamic;

-- ── 用户行为日志 ────────────────────────────────────────────
CREATE TABLE auth_user_log
(
    auth_user_log_id          int(11) NOT NULL AUTO_INCREMENT COMMENT '用户行为日志编号',
    auth_user_log_app_id      int(11)                                                       DEFAULT NULL COMMENT '用户行为日志应用编号',
    auth_user_log_user_id     BIGINT                                                        DEFAULT NULL COMMENT '用户行为日志用户编号',
    auth_user_log_user_display varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '用户行为日志用户名',
    auth_user_log_location    varchar(300) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '用户行为日志位置',
    auth_user_log_ip          varchar(60) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci  DEFAULT NULL COMMENT '用户行为日志IP',
    auth_user_log_browse      varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '用户浏览器',
    auth_user_log_device      varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '用户设备',
    auth_user_log_action      varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci  DEFAULT NULL COMMENT '用户行为日志操作类型',
    auth_user_log_state       int(11)                                                       DEFAULT NULL COMMENT '用户行为日志操作状态(succ|fail)',
    auth_user_log_desc        varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '用户行为日志操作备注',
    auth_user_log_time        datetime                                                      DEFAULT NULL COMMENT '用户行为日志操作时间',
    PRIMARY KEY (auth_user_log_id) USING BTREE,
    INDEX idx_auth_user_log_user (auth_user_log_user_id, auth_user_log_time)
) ENGINE = InnoDB
  AUTO_INCREMENT = 1
  CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT = '用户行为日志'
  ROW_FORMAT = Dynamic;

-- ── 用户详细扩展信息 ────────────────────────────────────────
CREATE TABLE auth_user_personal_additional
(
    auth_user_personal_id       int(11)                                                   NOT NULL AUTO_INCREMENT COMMENT '用户扩展编号',
    auth_user_id                BIGINT                                                       DEFAULT NULL COMMENT '用户编号',
    auth_user_personal_photo    longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '用户头像',
    auth_user_personal_website  varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '用户网站',
    auth_user_personal_name     varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci  DEFAULT NULL COMMENT '用户姓名',
    auth_user_personal_sex      varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci  DEFAULT NULL COMMENT '用户性别',
    auth_user_personal_birthday datetime                                                      DEFAULT NULL COMMENT '用户生日',
    auth_user_personal_locale   varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci  DEFAULT NULL COMMENT '用户语言',
    auth_user_personal_country  varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '用户国家',
    auth_user_personal_province varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '用户省',
    auth_user_personal_city     varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '用户市',
    auth_user_personal_district varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '用户区',
    auth_user_personal_address  varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '用户地址',
    auth_user_personal_location varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '用户经纬度',
    PRIMARY KEY (auth_user_personal_id) USING BTREE,
    UNIQUE KEY uk_auth_user_personal_user (auth_user_id)
) ENGINE = InnoDB
  AUTO_INCREMENT = 1
  CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT = '用户详细扩展信息'
  ROW_FORMAT = Dynamic;

-- ── 用户安全扩展信息 ────────────────────────────────────────
CREATE TABLE auth_user_secure_additional
(
    auth_user_secure_id           int(11) NOT NULL AUTO_INCREMENT COMMENT '用户源扩展信息',
    auth_user_id                  BIGINT                                                      DEFAULT NULL COMMENT '用户编号',
    auth_user_secure_created_at   datetime                                                     DEFAULT NULL COMMENT '用户创建时间',
    auth_user_secure_source       varchar(80) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '用户来源',
    auth_user_secure_reg_ip       varchar(80) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '用户注册来源IP',
    auth_user_secure_reg_device   varchar(80) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '用户注册来源设备',
    auth_user_secure_reg_browse   varchar(80) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '用户注册来源浏览器',
    auth_user_secure_reg_time     datetime                                                     DEFAULT NULL COMMENT '用户注册时间',
    auth_user_secure_login_count  int(11)                                                      DEFAULT NULL COMMENT '用户登录次数',
    auth_user_secure_login_time   datetime                                                     DEFAULT NULL COMMENT '用户登录时间',
    auth_user_secure_login_device varchar(80) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '用户登录设备',
    auth_user_secure_login_browse varchar(80) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '用户登录浏览器',
    auth_user_secure_login_ip     varchar(80) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '用户登录IP',
    PRIMARY KEY (auth_user_secure_id) USING BTREE,
    UNIQUE KEY uk_auth_user_secure_user (auth_user_id)
) ENGINE = InnoDB
  AUTO_INCREMENT = 1
  CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT = '用户安全扩展信息'
  ROW_FORMAT = Dynamic;

-- ── 操作审计日志 ────────────────────────────────────────────
CREATE TABLE auth_operation_log
(
    log_time       datetime                                                       NULL DEFAULT NULL COMMENT '日志时间',
    log_id         varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci  NOT NULL COMMENT '日志ID',
    start_time     bigint(20)                                                     NULL DEFAULT NULL COMMENT '开始时间',
    end_time       bigint(20)                                                     NULL DEFAULT NULL COMMENT '结束时间',
    app_name       varchar(150) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci  NULL DEFAULT NULL COMMENT '应用名称',
    module_name    varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci  NULL DEFAULT NULL COMMENT '模块名称',
    class_name     varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci  NULL DEFAULT NULL COMMENT '类名称',
    method_name    varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci  NULL DEFAULT NULL COMMENT '方法名称',
    operation_type varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci  NULL DEFAULT NULL COMMENT '操作类型 - 枚举',
    operation_desc varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci  NULL DEFAULT NULL COMMENT '操作描述',
    method         varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci   NULL DEFAULT NULL COMMENT '请求方法',
    uri            varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '请求URI',
    remote_ip      varchar(80) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci   NULL DEFAULT NULL COMMENT '远程IP',
    local_ip       varchar(80) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci   NULL DEFAULT NULL COMMENT '本地IP',
    duration       bigint(20)                                                     NULL DEFAULT NULL COMMENT '持续时间',
    status         varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci   NULL DEFAULT NULL COMMENT '状态码',
    status_name    varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci   NULL DEFAULT NULL COMMENT '状态名称',
    error          varchar(300) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci  NULL DEFAULT NULL COMMENT '异常堆栈信息',
    error_code     varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci   NULL DEFAULT NULL COMMENT '异常编码',
    error_message  varchar(300) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci  NULL DEFAULT NULL COMMENT '异常提示信息',
    user_id        varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci   NULL DEFAULT NULL COMMENT '用户ID',
    user_name      varchar(300) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci  NULL DEFAULT NULL COMMENT '用户名称',
    loc_country    varchar(300) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci  NULL DEFAULT NULL COMMENT '国家',
    loc_prov       varchar(300) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci  NULL DEFAULT NULL COMMENT '省份',
    loc_city       varchar(300) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci  NULL DEFAULT NULL COMMENT '城市',
    loc_isp        varchar(300) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci  NULL DEFAULT NULL COMMENT '运营商',
    PRIMARY KEY (log_id) USING BTREE
) ENGINE = InnoDB
  CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT = '公共-操作日志'
  ROW_FORMAT = Dynamic;
