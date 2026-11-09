package com.amandadamata.ticketing.booking;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Gives back the seats of holds that were never confirmed. */
@Component
@ConditionalOnProperty(name = "booking.expiry-job.enabled", havingValue = "true", matchIfMissing = true)
class HoldExpiryJob {

    private static final Logger log = LoggerFactory.getLogger(HoldExpiryJob.class);

    private final ReservationService reservations;

    HoldExpiryJob(ReservationService reservations) {
        this.reservations = reservations;
    }

    @Scheduled(fixedDelayString = "${booking.expiry-job.interval:PT30S}")
    void run() {
        int expired;
        do {
            expired = reservations.expireRunOutHolds();
            if (expired > 0) {
                log.info("Expired {} hold(s) and released their seats", expired);
            }
        } while (expired == ReservationService.EXPIRY_BATCH_SIZE);
    }
}
