package com.slotforge.api.payment;

import java.time.Clock;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.slotforge.api.booking.Booking;
import com.slotforge.api.booking.BookingRepository;
import com.slotforge.api.booking.BookingStateTransition;
import com.slotforge.api.booking.BookingStateTransitionRepository;
import com.slotforge.api.booking.BookingStatus;

@Service
public class PaymentAuthorizationService {

    private static final String AUTHORIZATION_REASON =
            "Fake payment authorized";
    private static final String CONFIRMATION_REASON =
            "Booking confirmed after payment authorization";

    private final PaymentIntentRepository paymentIntentRepository;
    private final PaymentEventRepository paymentEventRepository;
    private final BookingRepository bookingRepository;
    private final BookingStateTransitionRepository transitionRepository;
    private final PaymentCallbackReplayValidator replayValidator;
    private final Clock clock;

    public PaymentAuthorizationService(
            PaymentIntentRepository paymentIntentRepository,
            PaymentEventRepository paymentEventRepository,
            BookingRepository bookingRepository,
            BookingStateTransitionRepository transitionRepository,
            Clock clock,
            PaymentCallbackReplayValidator replayValidator
    ) {
        this.paymentIntentRepository = paymentIntentRepository;
        this.paymentEventRepository = paymentEventRepository;
        this.bookingRepository = bookingRepository;
        this.transitionRepository = transitionRepository;
        this.clock = clock;
        this.replayValidator = replayValidator;
    }

    @Transactional
    public PaymentCallbackResponse authorize(
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
                    PaymentEventType.AUTHORIZED,
                    eventId
            );
            return PaymentCallbackResponse.from(paymentIntent, true);
        }

        requireEligibleForAuthorization(paymentIntent, booking);
        paymentEventRepository.save(
                new PaymentEvent(
                        paymentIntent,
                        eventId,
                        PaymentEventType.AUTHORIZED,
                        request.occurredAt()
                )
        );

        paymentIntent.authorize();
        BookingStatus pendingState = booking.getStatus();
        booking.authorizePayment();
        transitionRepository.save(
                new BookingStateTransition(
                        booking,
                        pendingState,
                        BookingStatus.PAYMENT_AUTHORIZED,
                        null,
                        AUTHORIZATION_REASON
                )
        );
        booking.confirm();
        transitionRepository.save(
                new BookingStateTransition(
                        booking,
                        BookingStatus.PAYMENT_AUTHORIZED,
                        BookingStatus.CONFIRMED,
                        null,
                        CONFIRMATION_REASON
                )
        );

        paymentEventRepository.flush();
        transitionRepository.flush();
        return PaymentCallbackResponse.from(paymentIntent, false);
    }

    private void requireEligibleForAuthorization(
            PaymentIntent paymentIntent,
            Booking booking
    ) {
        if (paymentIntent.getStatus() != PaymentIntentStatus.PENDING) {
            throw new PaymentIntentUnavailableException(
                    "Payment intent cannot be authorized from state "
                            + paymentIntent.getStatus()
            );
        }
        if (booking.getStatus() != BookingStatus.PENDING_PAYMENT) {
            throw new PaymentIntentUnavailableException(
                    "Payment cannot authorize a booking in state "
                            + booking.getStatus()
            );
        }
        if (booking.isPaymentExpired(clock.instant())) {
            throw new PaymentIntentUnavailableException(
                    "The booking payment hold has expired"
            );
        }
    }
}
