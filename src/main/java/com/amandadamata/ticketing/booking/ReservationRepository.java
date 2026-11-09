package com.amandadamata.ticketing.booking;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;

public interface ReservationRepository extends JpaRepository<Reservation, UUID> {

    Optional<Reservation> findByIdempotencyKey(String idempotencyKey);

    /**
     * {@code SELECT ... FOR UPDATE}: a confirmation, a cancellation and the expiry job can't
     * change the same reservation at the same time. The second one waits and then sees the
     * new state.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Reservation r where r.id = :id")
    Optional<Reservation> findByIdForUpdate(@Param("id") UUID id);

    /**
     * Holds whose time ran out, locked with {@code SKIP LOCKED}: rows that a request is
     * confirming right now are skipped and picked up by the next run.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query("""
            select r from Reservation r
            where r.status = :status and r.expiresAt <= :now
            order by r.expiresAt""")
    List<Reservation> findRunOutHolds(@Param("status") ReservationStatus status,
            @Param("now") Instant now, Limit limit);

    long countByEventIdAndStatus(Long eventId, ReservationStatus status);
}
