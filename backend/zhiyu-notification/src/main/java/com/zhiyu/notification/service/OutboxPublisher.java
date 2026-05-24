package com.zhiyu.notification.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Simple outbox event publisher.
 * Logs events at INFO level. Full outbox pattern (with outbox_event table and
 * transactional polling) requires additional infrastructure to be added later.
 */
@Slf4j
@Service
public class OutboxPublisher {

    /**
     * Publish an event via the outbox pattern.
     * Currently logs the event; a full implementation would insert into an
     * outbox_event table within the same transaction as the business write.
     *
     * @param eventType    event type identifier (e.g., "notification.sent")
     * @param aggregateId  the aggregate root ID (e.g., template id or user id)
     * @param payloadJson  the JSON-serialized event payload
     */
    public void publish(final String eventType, final String aggregateId,
                        final String payloadJson) {
        log.info("[Outbox] eventType={} aggregateId={} payload={}",
                eventType, aggregateId, payloadJson);
    }
}
