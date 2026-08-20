package com.slotforge.api.payment;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;

import com.slotforge.api.booking.BookingRepository;
import com.slotforge.api.booking.BookingStatus;

class BookingExpirySchedulerTests {

    @Test
    void oneRunUsesBoundedBatchAndContinuesAfterCandidateFailure() {
        BookingRepository bookingRepository = mock(BookingRepository.class);
        BookingExpiryProcessor expiryProcessor =
                mock(BookingExpiryProcessor.class);
        Instant now = Instant.parse("2026-08-21T00:00:00Z");
        Clock clock = Clock.fixed(now, ZoneOffset.UTC);
        List<UUID> candidates = List.of(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID()
        );
        when(bookingRepository.findExpiredCandidateIds(
                BookingStatus.PENDING_PAYMENT,
                now,
                PageRequest.of(0, 100)
        )).thenReturn(candidates);
        when(expiryProcessor.expire(candidates.get(1)))
                .thenThrow(new IllegalStateException("forced failure"));

        new BookingExpiryScheduler(
                bookingRepository,
                expiryProcessor,
                clock
        ).expireOverdueBookings();

        verify(bookingRepository).findExpiredCandidateIds(
                BookingStatus.PENDING_PAYMENT,
                now,
                PageRequest.of(0, 100)
        );
        verify(expiryProcessor, times(3)).expire(any(UUID.class));
        verify(expiryProcessor).expire(candidates.get(2));
    }
}
