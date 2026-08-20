package com.slotforge.api.payment;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.slotforge.api.booking.BookingRepository;
import com.slotforge.api.booking.BookingStatus;

@Component
@ConditionalOnProperty(
        name = "slotforge.booking-expiry.enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class BookingExpiryScheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(
            BookingExpiryScheduler.class
    );
    private static final int BATCH_SIZE = 100;

    private final BookingRepository bookingRepository;
    private final BookingExpiryProcessor expiryProcessor;
    private final Clock clock;

    public BookingExpiryScheduler(
            BookingRepository bookingRepository,
            BookingExpiryProcessor expiryProcessor,
            Clock clock
    ) {
        this.bookingRepository = bookingRepository;
        this.expiryProcessor = expiryProcessor;
        this.clock = clock;
    }

    @Scheduled(
            fixedDelayString =
                    "${slotforge.booking-expiry.fixed-delay-ms:30000}",
            initialDelayString =
                    "${slotforge.booking-expiry.initial-delay-ms:30000}"
    )
    public void expireOverdueBookings() {
        List<UUID> candidates = bookingRepository.findExpiredCandidateIds(
                BookingStatus.PENDING_PAYMENT,
                clock.instant(),
                PageRequest.of(0, BATCH_SIZE)
        );
        for (UUID bookingId : candidates) {
            try {
                expiryProcessor.expire(bookingId);
            } catch (RuntimeException exception) {
                LOGGER.error("Failed to expire booking {}", bookingId,
                        exception);
            }
        }
    }
}
