package com.slotforge.api.booking;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.slotforge.api.common.PageResponse;
import com.slotforge.api.security.CurrentActor;
import com.slotforge.api.security.CurrentActorProvider;

@Service
public class BookingQueryService {

    private final BookingRepository bookingRepository;
    private final BookingItemRepository bookingItemRepository;
    private final BookingStateTransitionRepository transitionRepository;
    private final CurrentActorProvider currentActorProvider;

    public BookingQueryService(
            BookingRepository bookingRepository,
            BookingItemRepository bookingItemRepository,
            BookingStateTransitionRepository transitionRepository,
            CurrentActorProvider currentActorProvider
    ) {
        this.bookingRepository = bookingRepository;
        this.bookingItemRepository = bookingItemRepository;
        this.transitionRepository = transitionRepository;
        this.currentActorProvider = currentActorProvider;
    }

    @Transactional(readOnly = true)
    public BookingResponse get(UUID bookingId) {
        CurrentActor actor = currentActorProvider.currentActor();
        Booking booking = findAuthorizedBooking(bookingId, actor);
        BookingItem item = findItem(bookingId);
        return BookingResponse.from(booking, item.getQuantity());
    }

    @Transactional(readOnly = true)
    public PageResponse<BookingResponse> listCurrentUser(int page, int size) {
        CurrentActor actor = currentActorProvider.currentActor();
        PageRequest pageRequest = PageRequest.of(
                page,
                size,
                Sort.by(Sort.Direction.DESC, "createdAt")
                        .and(Sort.by(Sort.Direction.DESC, "id"))
        );
        Page<Booking> bookings = bookingRepository.findByUser_Id(
                actor.userId(),
                pageRequest
        );

        Map<UUID, BookingItem> itemsByBookingId;
        if (bookings.isEmpty()) {
            itemsByBookingId = Map.of();
        } else {
            List<UUID> bookingIds = bookings.getContent()
                    .stream()
                    .map(Booking::getId)
                    .toList();
            itemsByBookingId = bookingItemRepository
                    .findAllByBooking_IdIn(bookingIds)
                    .stream()
                    .collect(Collectors.toMap(
                            item -> item.getBooking().getId(),
                            Function.identity()
                    ));
        }

        Page<BookingResponse> responses = bookings.map(booking -> {
            BookingItem item = itemsByBookingId.get(booking.getId());
            if (item == null) {
                throw new IllegalStateException(
                        "Booking item is missing for booking: "
                                + booking.getId()
                );
            }
            return BookingResponse.from(booking, item.getQuantity());
        });
        return PageResponse.from(responses);
    }

    @Transactional(readOnly = true)
    public List<BookingStateTransitionResponse> listTransitions(
            UUID bookingId
    ) {
        CurrentActor actor = currentActorProvider.currentActor();
        findAuthorizedBooking(bookingId, actor);
        return transitionRepository
                .findAllByBooking_IdOrderByOccurredAtAscIdAsc(bookingId)
                .stream()
                .map(BookingStateTransitionResponse::from)
                .toList();
    }

    private Booking findAuthorizedBooking(
            UUID bookingId,
            CurrentActor actor
    ) {
        Booking booking = bookingRepository
                .findWithDetailsById(bookingId)
                .orElseThrow(() -> new BookingNotFoundException(bookingId));
        boolean ownsBooking = booking.getUser()
                .getId()
                .equals(actor.userId());
        if (!ownsBooking && !actor.isAdmin()) {
            throw new BookingOwnershipException();
        }
        return booking;
    }

    private BookingItem findItem(UUID bookingId) {
        return bookingItemRepository
                .findByBooking_Id(bookingId)
                .orElseThrow(() -> new IllegalStateException(
                        "Booking item is missing for booking: " + bookingId
                ));
    }
}
