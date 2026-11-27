package com.friendlyeshop.order.repository;

import com.friendlyeshop.order.model.OrderOutbox;
import com.friendlyeshop.order.model.enums.KafkaOutboxStatus;
import com.friendlyeshop.order.model.enums.OutboxStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderOutboxRepository extends JpaRepository<OrderOutbox, UUID> {

    @Query("SELECT o FROM OrderOutbox o WHERE o.status = :status AND o.nextAttemptAt <= :now "
            + "ORDER BY o.occurredAt ASC")
    List<OrderOutbox> findReadyToPublish(
            @Param("status") OutboxStatus status, @Param("now") Instant now, Pageable pageable);

    long countByStatus(OutboxStatus status);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE OrderOutbox o SET o.status = :status, o.publishedAt = :publishedAt "
            + "WHERE o.eventId = :eventId")
    int markPublished(
            @Param("eventId") UUID eventId,
            @Param("status") OutboxStatus status,
            @Param("publishedAt") Instant publishedAt);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE OrderOutbox o SET o.attempts = o.attempts + 1, o.nextAttemptAt = :nextAttemptAt "
            + "WHERE o.eventId = :eventId")
    int recordFailure(@Param("eventId") UUID eventId, @Param("nextAttemptAt") Instant nextAttemptAt);

    @Query("SELECT o FROM OrderOutbox o WHERE o.kafkaStatus = :status "
            + "AND o.kafkaNextAttemptAt <= :now ORDER BY o.occurredAt ASC, o.eventId ASC")
    List<OrderOutbox> findReadyForKafka(
            @Param("status") KafkaOutboxStatus status, @Param("now") Instant now, Pageable pageable);

    long countByKafkaStatus(KafkaOutboxStatus status);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE OrderOutbox o SET o.kafkaStatus = :status, o.kafkaPublishedAt = :publishedAt "
            + "WHERE o.eventId = :eventId")
    int markKafkaPublished(
            @Param("eventId") UUID eventId,
            @Param("status") KafkaOutboxStatus status,
            @Param("publishedAt") Instant publishedAt);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE OrderOutbox o SET o.kafkaAttempts = o.kafkaAttempts + 1, "
            + "o.kafkaNextAttemptAt = :nextAttemptAt WHERE o.eventId = :eventId")
    int recordKafkaFailure(
            @Param("eventId") UUID eventId, @Param("nextAttemptAt") Instant nextAttemptAt);
}
