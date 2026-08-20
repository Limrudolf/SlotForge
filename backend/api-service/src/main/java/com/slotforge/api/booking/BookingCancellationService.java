package com.slotforge.api.booking;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.slotforge.api.availability.BookingSlot;
import com.slotforge.api.availability.BookingSlotRepository;
import com.slotforge.api.security.CurrentActor;
import com.slotforge.api.security.CurrentActorProvider;

@Service
public class BookingCancellationService {

    private static final String CANCELLATION_REASON =
            "Customer cancelled booking";

    private final BookingRepository bookingRepository;
    private final BookingItemRepository bookingItemRepository;
    private final BookingSlotRepository bookingSlotRepository;
    private final BookingStateTransitionRepository transitionRepository;
    private final CurrentActorProvider currentActorProvider;

    public BookingCancellationService(
            BookingRepository bookingRepository,
            BookingItemRepository bookingItemRepository,
            BookingSlotRepository bookingSlotRepository,
            BookingStateTransitionRepository transitionRepository,
            CurrentActorProvider currentActorProvider
    ) {
        this.bookingRepository = bookingRepository;
        this.bookingItemRepository = bookingItemRepository;
        this.bookingSlotRepository = bookingSlotRepository;
        this.transitionRepository = transitionRepository;
        this.currentActorProvider = currentActorProvider;
    }

    @Transactional
    public BookingResponse cancel(UUID bookingId) {
        CurrentActor actor = currentActorProvider.currentActor();
        Booking booking = bookingRepository
                .findWithDetailsById(bookingId)
                .orElseThrow(() -> new BookingNotFoundException(bookingId));
        requireOwner(booking, actor);

        BookingItem bookingItem = bookingItemRepository
                .findByBooking_Id(bookingId)
                .orElseThrow(() -> new IllegalStateException(
                        "Booking item is missing for booking: " + bookingId
                ));
        BookingStatus previousStatus = booking.getStatus();
        booking.cancel();

        // Claim the booking version before touching shared capacity.
        bookingRepository.flush();

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

        bookingSlot.release(bookingItem.getQuantity());
        transitionRepository.save(
                new BookingStateTransition(
                        booking,
                        previousStatus,
                        BookingStatus.CANCELLED,
                        booking.getUser(),
                        CANCELLATION_REASON
                )
        );
        transitionRepository.flush();

        return BookingResponse.from(booking, bookingItem.getQuantity());
    }

    private void requireOwner(Booking booking, CurrentActor actor) {
        if (!booking.getUser().getId().equals(actor.userId())) {
            throw new BookingOwnershipException();
        }
    }
}
