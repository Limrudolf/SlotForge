package com.slotforge.api.session;

import java.time.ZoneId;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.slotforge.api.availability.AvailabilityResponse;
import com.slotforge.api.availability.BookingSlot;
import com.slotforge.api.availability.BookingSlotRepository;
import com.slotforge.api.common.PageResponse;
import com.slotforge.api.event.Event;
import com.slotforge.api.event.EventNotFoundException;
import com.slotforge.api.event.EventRepository;
import com.slotforge.api.venue.Venue;
import com.slotforge.api.venue.VenueNotFoundException;
import com.slotforge.api.venue.VenueRepository;

@Service
public class EventSessionService {

    private final EventRepository eventRepository;
    private final VenueRepository venueRepository;
    private final EventSessionRepository eventSessionRepository;
    private final BookingSlotRepository bookingSlotRepository;

    public EventSessionService(
            EventRepository eventRepository,
            VenueRepository venueRepository,
            EventSessionRepository eventSessionRepository,
            BookingSlotRepository bookingSlotRepository
    ) {
        this.eventRepository = eventRepository;
        this.venueRepository = venueRepository;
        this.eventSessionRepository = eventSessionRepository;
        this.bookingSlotRepository = bookingSlotRepository;
    }

    @Transactional
    public EventSessionResponse create(
            UUID eventId,
            CreateEventSessionRequest request
    ) {
        Event event = findEvent(eventId);
        Venue venue = findVenue(request.venueId());

        String displayTimezone = ZoneId
                .of(request.displayTimezone().trim())
                .getId();

        EventSession session = new EventSession(
                event,
                venue,
                request.startTime().toInstant(),
                request.endTime().toInstant(),
                displayTimezone
        );

        eventSessionRepository.save(session);

        BookingSlot bookingSlot = new BookingSlot(
                session,
                request.totalCapacity()
        );

        bookingSlotRepository.save(bookingSlot);
        bookingSlotRepository.flush();

        return EventSessionResponse.from(session);
    }

    @Transactional(readOnly = true)
    public PageResponse<EventSessionResponse> listForEvent(
            UUID eventId,
            int page,
            int size
    ) {
        findEvent(eventId);

        PageRequest pageRequest = PageRequest.of(
                page,
                size,
                Sort.by(Sort.Direction.ASC, "startTimeUtc")
                        .and(Sort.by(Sort.Direction.ASC, "id"))
        );

        Page<EventSessionResponse> sessions = eventSessionRepository
                .findByEvent_Id(eventId, pageRequest)
                .map(EventSessionResponse::from);

        return PageResponse.from(sessions);
    }

    @Transactional(readOnly = true)
    public EventSessionResponse get(UUID sessionId) {
        EventSession session = findSession(sessionId);
        return EventSessionResponse.from(session);
    }

    @Transactional(readOnly = true)
    public AvailabilityResponse getAvailability(UUID sessionId) {
        findSession(sessionId);

        BookingSlot bookingSlot = bookingSlotRepository
                .findByEventSession_Id(sessionId)
                .orElseThrow(() -> new IllegalStateException(
                        "Capacity state is missing for session: " + sessionId
                ));

        return AvailabilityResponse.from(bookingSlot);
    }

    private Event findEvent(UUID eventId) {
        return eventRepository.findById(eventId)
                .orElseThrow(() -> new EventNotFoundException(eventId));
    }

    private Venue findVenue(UUID venueId) {
        return venueRepository.findById(venueId)
                .orElseThrow(() -> new VenueNotFoundException(venueId));
    }

    private EventSession findSession(UUID sessionId) {
        return eventSessionRepository.findById(sessionId)
                .orElseThrow(
                        () -> new EventSessionNotFoundException(sessionId)
                );
    }
}
