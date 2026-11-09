package com.amandadamata.ticketing.event;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EventRepository extends JpaRepository<Event, Long> {

    /**
     * Takes seats only if enough are left, in one atomic statement.
     *
     * <p>PostgreSQL locks the row while it runs the update, so concurrent requests are applied
     * one after the other and each one sees the result of the previous. When the count isn't
     * enough, no row matches and the method returns 0.
     *
     * @return 1 if the seats were taken, 0 if there weren't enough
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update Event e set e.availableSeats = e.availableSeats - :quantity
            where e.id = :eventId and e.availableSeats >= :quantity""")
    int takeSeats(@Param("eventId") long eventId, @Param("quantity") int quantity);

    /**
     * Gives seats back. The table CHECK refuses a result above the capacity, so a bug that
     * released the same seats twice would fail instead of creating seats out of nothing.
     */
    @Modifying(flushAutomatically = true)
    @Query("update Event e set e.availableSeats = e.availableSeats + :quantity where e.id = :eventId")
    int releaseSeats(@Param("eventId") long eventId, @Param("quantity") int quantity);
}
