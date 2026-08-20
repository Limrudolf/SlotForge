package com.slotforge.api.payment;

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
public class PaymentFailureService {

    private static final String FAILURE_REASON = "Fake payment failed";

    private final PaymentIntentRepository paymentIntentRepository;
    private final PaymentEventRepository paymentEventRepository;
    private final BookingRepository bookingRepository;
    private final BookingItemRepository bookingItemRepository;
    private final BookingSlotRepository bookingSlotRepository;
    private final BookingStateTransitionRepository transitionRepository;
    private final PaymentCallbackReplayValidator replayValidator;

    public PaymentFailureService(
            PaymentIntentRepository paymentIntentRepository,
            PaymentEventRepository paymentEventRepository,
            BookingRepository bookingRepository,
            BookingItemRepository bookingItemRepository,
            BookingSlotRepository bookingSlotRepository,
            BookingStateTransitionRepository transitionRepository,
            PaymentCallbackReplayValidator replayValidator
    ) {
        this.paymentIntentRepository = paymentIntentRepository;
        this.paymentEventRepository = paymentEventRepository;
        this.bookingRepository = bookingRepository;
        this.bookingItemRepository = bookingItemRepository;
        this.bookingSlotRepository = bookingSlotRepository;
        this.transitionRepository = transitionRepository;
        this.replayValidator = replayValidator;
    }

    @Transactional
    public PaymentCallbackResponse fail(
            UUID paymentIntentId,
            FakePaymentCallbackRequest request
    ) {
        PaymentIntent paymentIntent = paymentIntentRepository
                .findByIdForUpdate(paymentIntentId)
                .orElseThrow(() ->
                        new PaymentIntentNotFoundException(paymentIntentId)
                );
        Booking booking = bookingRepository
                .findByIdForUpdate(paymentIntent.getBooking().getId())
                .orElseThrow(() -> new IllegalStateException(
                        "Booking is missing for payment intent: "
                                + paymentIntentId
                ));
        String eventId = request.eventId().trim();
        Optional<PaymentEvent> existingEvent = paymentEventRepository
                .findByExternalEventId(eventId);
        if (existingEvent.isPresent()) {
            replayValidator.requireMatchingReplay(
                    existingEvent.get(),
                    paymentIntentId,
                    PaymentEventType.FAILED,
                    eventId
            );
            return PaymentCallbackResponse.from(paymentIntent, true);
        }

        requireEligibleForFailure(paymentIntent, booking);
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

        paymentEventRepository.save(
                new PaymentEvent(
                        paymentIntent,
                        eventId,
                        PaymentEventType.FAILED,
                        request.occurredAt()
                )
        );
        paymentIntent.fail();
        BookingStatus previousState = booking.getStatus();
        booking.failPayment();
        bookingSlot.release(bookingItem.getQuantity());
        transitionRepository.save(
                new BookingStateTransition(
                        booking,
                        previousState,
                        BookingStatus.PAYMENT_FAILED,
                        null,
                        FAILURE_REASON
                )
        );

        paymentEventRepository.flush();
        transitionRepository.flush();
        return PaymentCallbackResponse.from(paymentIntent, false);
    }

    private static void requireEligibleForFailure(
            PaymentIntent paymentIntent,
            Booking booking
    ) {
        if (paymentIntent.getStatus() != PaymentIntentStatus.PENDING) {
            throw new PaymentIntentUnavailableException(
                    "Payment intent cannot fail from state "
                            + paymentIntent.getStatus()
            );
        }
        if (booking.getStatus() != BookingStatus.PENDING_PAYMENT) {
            throw new PaymentIntentUnavailableException(
                    "Payment cannot fail a booking in state "
                            + booking.getStatus()
            );
        }
    }
}
