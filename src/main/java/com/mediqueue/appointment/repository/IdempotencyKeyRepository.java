package com.mediqueue.appointment.repository;

import com.mediqueue.appointment.domain.IdempotencyKey;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link IdempotencyKey} entities.
 *
 * @since 0.0.1
 */
public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKey, UUID> {

    /**
     * Looks up an existing idempotency record by operation type and client-provided key.
     *
     * @param operationType  the type of operation (e.g. {@code "CREATE_APPOINTMENT"})
     * @param idempotencyKey the client-supplied idempotency key
     * @return the matching record, if any
     */
    Optional<IdempotencyKey> findByOperationTypeAndIdempotencyKey(String operationType, String idempotencyKey);
}
