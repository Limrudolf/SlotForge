package com.slotforge.api.booking;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.slotforge.api.availability.BookingSlot;
import com.slotforge.api.availability.BookingSlotRepository;
import com.slotforge.api.session.EventSession;
import com.slotforge.api.session.EventSessionNotFoundException;
import com.slotforge.api.session.EventSessionRepository;
import com.slotforge.api.user.AuthenticatedAccountUnavailableException;
import com.slotforge.api.user.UserAccount;
import com.slotforge.api.user.UserAccountRepository;

@Service
public class BookingCreationTransaction {

    private static final String INITIAL_TRANSITION_REASON = "Booking created";
    private static final Duration PAYMENT_HOLD_DURATION =
            Duration.ofMinutes(15);

    private final BookingRepository bookingRepository;
    private final BookingItemRepository bookingItemRepository;
    private final BookingStateTransitionRepository transitionRepository;
    private final IdempotencyKeyRepository idempotencyKeyRepository;
    private final BookingSlotRepository bookingSlotRepository;
    private final EventSessionRepository eventSessionRepository;
    private final UserAccountRepository userAccountRepository;
    private final Clock clock;

    public BookingCreationTransaction(
            BookingRepository bookingRepository,
            BookingItemRepository bookingItemRepository,
            BookingStateTransitionRepository transitionRepository,
            IdempotencyKeyRepository idempotencyKeyRepository,
            BookingSlotRepository bookingSlotRepository,
            EventSessionRepository eventSessionRepository,
            UserAccountRepository userAccountRepository,
            Clock clock
    ) {
        this.bookingRepository = bookingRepository;
        this.bookingItemRepository = bookingItemRepository;
        this.transitionRepository = transitionRepository;
        this.idempotencyKeyRepository = idempotencyKeyRepository;
        this.bookingSlotRepository = bookingSlotRepository;
        this.eventSessionRepository = eventSessionRepository;
        this.userAccountRepository = userAccountRepository;
        this.clock = clock;
    }

    @Transactional
    public BookingCreationResult create(
            UUID userId,
            UUID sessionId,
            int quantity,
            String idempotencyKey,
            String requestFingerprint
    ) {
        Optional<BookingCreationResult> existing = findExisting(
                userId,
                idempotencyKey,
                requestFingerprint
        );
        if (existing.isPresent()) {
            return existing.get();
        }

        UserAccount user = findAuthenticatedUser(userId);
        EventSession session = findSession(sessionId);
        BookingSlot bookingSlot = bookingSlotRepository
                .findByEventSessionIdForUpdate(sessionId)
                .orElseThrow(() -> new IllegalStateException(
                        "Capacity state is missing for session: " + sessionId
                ));

        existing = findExisting(
                userId,
                idempotencyKey,
                requestFingerprint
        );
        if (existing.isPresent()) {
            return existing.get();
        }

        if (!bookingSlot.canReserve(quantity)) {
            throw new InsufficientCapacityException(
                    sessionId,
                    quantity,
                    bookingSlot.getRemainingCapacity()
            );
        }
        bookingSlot.reserve(quantity);

        Instant paymentExpiresAt = clock.instant()
                .plus(PAYMENT_HOLD_DURATION);
        Booking booking = bookingRepository.save(
                new Booking(user, session, paymentExpiresAt)
        );
        bookingItemRepository.save(
                new BookingItem(
                        booking,
                        bookingSlot,
                        quantity,
                        session.getUnitPriceMinor(),
                        session.getCurrency()
                )
        );
        transitionRepository.save(
                new BookingStateTransition(
                        booking,
                        null,
                        BookingStatus.PENDING_PAYMENT,
                        user,
                        INITIAL_TRANSITION_REASON
                )
        );
        idempotencyKeyRepository.save(
                new IdempotencyKey(
                        user,
                        idempotencyKey,
                        requestFingerprint,
                        booking
                )
        );

        idempotencyKeyRepository.flush();
        return new BookingCreationResult(
                BookingResponse.from(booking, quantity),
                false
        );
    }

    @Transactional(readOnly = true)
    public Optional<BookingCreationResult> resolveExisting(
            UUID userId,
            String idempotencyKey,
            String requestFingerprint
    ) {
        return findExisting(userId, idempotencyKey, requestFingerprint);
    }

    private Optional<BookingCreationResult> findExisting(
            UUID userId,
            String idempotencyKey,
            String requestFingerprint
    ) {
        return idempotencyKeyRepository
                .findByUser_IdAndKeyValue(userId, idempotencyKey)
                .map(storedKey -> {
                    if (!storedKey.getRequestFingerprint()
                            .equals(requestFingerprint)) {
                        throw new IdempotencyKeyConflictException();
                    }

                    Booking booking = storedKey.getBooking();
                    BookingItem item = bookingItemRepository
                            .findByBooking_Id(booking.getId())
                            .orElseThrow(() -> new IllegalStateException(
                                    "Booking item is missing for booking: "
                                            + booking.getId()
                            ));

                    return new BookingCreationResult(
                            BookingResponse.from(booking, item.getQuantity()),
                            true
                    );
                });
    }

    private UserAccount findAuthenticatedUser(UUID userId) {
        return userAccountRepository.findById(userId)
                .filter(UserAccount::isActive)
                .orElseThrow(
                        AuthenticatedAccountUnavailableException::new
                );
    }

    private EventSession findSession(UUID sessionId) {
        return eventSessionRepository.findById(sessionId)
                .orElseThrow(
                        () -> new EventSessionNotFoundException(sessionId)
                );
    }
}
