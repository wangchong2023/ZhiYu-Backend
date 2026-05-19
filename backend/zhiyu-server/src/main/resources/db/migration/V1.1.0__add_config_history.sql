-- V1.1.0: Nacos 配置变更本地审计快照
CREATE TABLE config_history (
    id              BIGINT          NOT NULL AUTO_INCREMENT  COMMENT '历史ID',
    group_id        VARCHAR(64)     NOT NULL                 COMMENT 'Nacos groupId',
    data_id         VARCHAR(128)    NOT NULL                 COMMENT 'Nacos dataId',
    content         MEDIUMTEXT      NOT NULL                 COMMENT '配置内容快照',
    format          VARCHAR(8)      NOT NULL DEFAULT 'YAML'  COMMENT 'YAML|JSON|PROPERTIES',
    version         INT             NOT NULL                 COMMENT 'Nacos 版本号',
    operator_id     BIGINT          DEFAULT NULL             COMMENT '操作人ID (admin_user)',
    operator_type   VARCHAR(8)      NOT NULL DEFAULT 'ADMIN' COMMENT 'ADMIN|SYSTEM',
    change_summary  VARCHAR(255)    DEFAULT NULL             COMMENT '变更摘要',
    created_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_config_history_group_data (group_id, data_id, version),
    KEY idx_config_history_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Nacos配置变更历史本地快照表';
