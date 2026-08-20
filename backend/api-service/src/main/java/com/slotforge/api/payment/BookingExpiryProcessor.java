package com.slotforge.api.payment;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.slotforge.api.availability.BookingSlot;
import com.slotforge.api.availability.BookingSlotRepository;
import com.slotforge.api.booking.Booking;
import com.slotforge.api.booking.BookingItem;
import com.slotforge.api.booking.BookingItemRepository;
import com.slotforge.api.booking.BookingRepository;
import com.slotforge.api.booking.BookingStateTransition;
import com.slotforge.api.booking.BookingStateTransitionRepository;
import com.slotforge.api.booking.BookingStatus;

@Service
public class BookingExpiryProcessor {

    private static final String EXPIRY_REASON =
            "Payment reservation expired";
    private static final String SCHEDULER_EVENT_PREFIX =
            "scheduler-timeout:";

    private final BookingRepository bookingRepository;
    private final BookingItemRepository bookingItemRepository;
    private final BookingSlotRepository bookingSlotRepository;
    private final BookingStateTransitionRepository transitionRepository;
    private final PaymentIntentRepository paymentIntentRepository;
    private final PaymentEventRepository paymentEventRepository;
    private final Clock clock;

    public BookingExpiryProcessor(
            BookingRepository bookingRepository,
            BookingItemRepository bookingItemRepository,
            BookingSlotRepository bookingSlotRepository,
            BookingStateTransitionRepository transitionRepository,
            PaymentIntentRepository paymentIntentRepository,
            PaymentEventRepository paymentEventRepository,
            Clock clock
    ) {
        this.bookingRepository = bookingRepository;
        this.bookingItemRepository = bookingItemRepository;
        this.bookingSlotRepository = bookingSlotRepository;
        this.transitionRepository = transitionRepository;
        this.paymentIntentRepository = paymentIntentRepository;
        this.paymentEventRepository = paymentEventRepository;
        this.clock = clock;
    }

    @Transactional
    public boolean expire(UUID bookingId) {
        Optional<PaymentIntent> observedIntent = paymentIntentRepository
                .findByBooking_Id(bookingId);
        if (observedIntent.isPresent()) {
            PaymentIntent lockedIntent = paymentIntentRepository
                    .findByIdForUpdate(observedIntent.get().getId())
                    .orElseThrow(() -> new IllegalStateException(
                            "Observed payment intent disappeared"
                    ));
            return expireLocked(
                    lockBooking(bookingId),
                    Optional.of(lockedIntent)
            );
        }

        Booking lockedBooking = lockBooking(bookingId);
        if (paymentIntentRepository.findByBooking_Id(bookingId).isPresent()) {
            return false;
        }
        return expireLocked(lockedBooking, Optional.empty());
    }

    private Booking lockBooking(UUID bookingId) {
        return bookingRepository.findByIdForUpdate(bookingId)
                .orElseThrow(() -> new IllegalStateException(
                        "Expiry candidate booking disappeared: " + bookingId
                ));
    }

    private boolean expireLocked(
            Booking booking,
            Optional<PaymentIntent> paymentIntent
    ) {
        if (booking.getStatus() != BookingStatus.PENDING_PAYMENT
                || !booking.isPaymentExpired(clock.instant())) {
            return false;
        }
        if (paymentIntent.isPresent()
                && paymentIntent.get().getStatus()
                        != PaymentIntentStatus.PENDING) {
            return false;
        }

        BookingItem bookingItem = bookingItemRepository
                .findByBooking_Id(booking.getId())
                .orElseThrow(() -> new IllegalStateException(
                        "Booking item is missing for booking: "
                                + booking.getId()
                ));
        UUID sessionId = booking.getEventSession().getId();
        BookingSlot bookingSlot = bookingSlotRepository
                .findByEventSessionIdForUpdate(sessionId)
                .orElseThrow(() -> new IllegalStateException(
                        "Capacity state is missing for session: " + sessionId
                ));
        if (!bookingSlot.getId().equals(
                bookingItem.getBookingSlot().getId()
        )) {
            throw new IllegalStateException(
                    "Booking item references an unexpected capacity row"
            );
        }

        Instant now = clock.instant();
        paymentIntent.ifPresent(intent -> {
            intent.timeOut();
            paymentEventRepository.save(
                    new PaymentEvent(
                            intent,
                            SCHEDULER_EVENT_PREFIX + booking.getId(),
                            PaymentEventType.TIMED_OUT,
                            now
                    )
            );
        });
        BookingStatus previousState = booking.getStatus();
        booking.expire();
        bookingSlot.release(bookingItem.getQuantity());
        transitionRepository.save(
                new BookingStateTransition(
                        booking,
                        previousState,
                        BookingStatus.EXPIRED,
                        null,
                        EXPIRY_REASON
                )
        );
        paymentEventRepository.flush();
        transitionRepository.flush();
        return true;
    }
}
