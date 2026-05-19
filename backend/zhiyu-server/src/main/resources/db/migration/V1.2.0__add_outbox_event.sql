-- V1.2.0: Outbox 事件表（事务后异步通知）
CREATE TABLE outbox_event (
    id              BIGINT          NOT NULL AUTO_INCREMENT  COMMENT '事件ID',
    event_type      VARCHAR(32)     NOT NULL                 COMMENT '事件类型 PAYMENT_SUCCESS|REFUND_APPROVED|ACCOUNT_DELETED|...',
    aggregate_id    VARCHAR(64)     DEFAULT NULL             COMMENT '聚合根ID (orderNo/userId)',
    payload_json    JSON            NOT NULL                 COMMENT '事件负载',
    status          VARCHAR(16)     NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING|PROCESSING|SENT|FAILED',
    retry_count     INT             NOT NULL DEFAULT 0      COMMENT '重试次数',
    max_retries     INT             NOT NULL DEFAULT 5      COMMENT '最大重试次数',
    next_retry_at   DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '下次重试时间',
    error_message   VARCHAR(500)    DEFAULT NULL             COMMENT '最后一次失败原因',
    created_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_outbox_status_next_retry (status, next_retry_at),
    KEY idx_outbox_event_type_created (event_type, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Outbox事件表（异步通知、补偿任务）';
