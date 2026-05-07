package com.mediqueue.appointment.repository;

import com.mediqueue.appointment.domain.OutboxEvent;
import com.mediqueue.appointment.domain.enums.OutboxPublicationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link OutboxEvent} entities.
 *
 * @since 0.0.1
 */
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    /**
     * Retrieves up to 50 outbox events with the given publication status,
     * ordered by creation time ascending (FIFO). Used by the background
     * relay that publishes pending events to RabbitMQ.
     *
     * @param status the publication status to filter by (typically {@code PENDING})
     * @return ordered list of events ready for publication
     */
    List<OutboxEvent> findTop50ByPublicationStatusOrderByCreatedAtAsc(OutboxPublicationStatus status);
}
