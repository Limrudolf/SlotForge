package com.slotforge.api.payment;

import java.time.Clock;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.slotforge.api.booking.Booking;
import com.slotforge.api.booking.BookingItem;
import com.slotforge.api.booking.BookingItemRepository;
import com.slotforge.api.booking.BookingNotFoundException;
import com.slotforge.api.booking.BookingOwnershipException;
import com.slotforge.api.booking.BookingRepository;
import com.slotforge.api.booking.BookingStatus;
import com.slotforge.api.security.CurrentActor;
import com.slotforge.api.security.CurrentActorProvider;

@Service
public class PaymentIntentService {

    private final PaymentIntentRepository paymentIntentRepository;
    private final BookingRepository bookingRepository;
    private final BookingItemRepository bookingItemRepository;
    private final CurrentActorProvider currentActorProvider;
    private final Clock clock;

    public PaymentIntentService(
            PaymentIntentRepository paymentIntentRepository,
            BookingRepository bookingRepository,
            BookingItemRepository bookingItemRepository,
            CurrentActorProvider currentActorProvider,
            Clock clock
    ) {
        this.paymentIntentRepository = paymentIntentRepository;
        this.bookingRepository = bookingRepository;
        this.bookingItemRepository = bookingItemRepository;
        this.currentActorProvider = currentActorProvider;
        this.clock = clock;
    }

    @Transactional
    public PaymentIntentCreationResult create(UUID bookingId) {
        CurrentActor actor = currentActorProvider.currentActor();
        Booking booking = bookingRepository.findByIdForUpdate(bookingId)
                .orElseThrow(() -> new BookingNotFoundException(bookingId));
        requireOwner(booking, actor);

        Optional<PaymentIntent> existing = paymentIntentRepository
                .findByBooking_Id(bookingId);
        if (existing.isPresent()) {
            return new PaymentIntentCreationResult(
                    PaymentIntentResponse.from(existing.get()),
                    true
            );
        }

        requireEligibleForPayment(booking);
        BookingItem bookingItem = bookingItemRepository
                .findByBooking_Id(bookingId)
                .orElseThrow(() -> new IllegalStateException(
                        "Booking item is missing for booking: " + bookingId
                ));

        PaymentIntent paymentIntent = paymentIntentRepository.save(
                new PaymentIntent(
                        booking,
                        bookingItem.totalAmountMinor(),
                        bookingItem.getCurrency()
                )
        );
        paymentIntentRepository.flush();

        return new PaymentIntentCreationResult(
                PaymentIntentResponse.from(paymentIntent),
                false
        );
    }

    @Transactional(readOnly = true)
    public PaymentIntentResponse get(UUID paymentIntentId) {
        CurrentActor actor = currentActorProvider.currentActor();
        PaymentIntent paymentIntent = paymentIntentRepository
                .findById(paymentIntentId)
                .orElseThrow(() ->
                        new PaymentIntentNotFoundException(paymentIntentId)
                );
        requireOwnerOrAdmin(paymentIntent.getBooking(), actor);
        return PaymentIntentResponse.from(paymentIntent);
    }

    private void requireEligibleForPayment(Booking booking) {
        if (booking.getStatus() != BookingStatus.PENDING_PAYMENT) {
            throw new PaymentIntentUnavailableException(
                    "Payment cannot begin for a booking in state "
                            + booking.getStatus()
            );
        }
        if (booking.isPaymentExpired(clock.instant())) {
            throw new PaymentIntentUnavailableException(
                    "The booking payment hold has expired"
            );
        }
    }

    private static void requireOwner(
            Booking booking,
            CurrentActor actor
    ) {
        if (!booking.getUser().getId().equals(actor.userId())) {
            throw new BookingOwnershipException();
        }
    }

    private static void requireOwnerOrAdmin(
            Booking booking,
            CurrentActor actor
    ) {
        if (!actor.isAdmin()
                && !booking.getUser().getId().equals(actor.userId())) {
            throw new BookingOwnershipException();
        }
    }
}
