package com.slotforge.api.event;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.slotforge.api.common.PageResponse;

@Service
public class EventService {

    private final EventRepository eventRepository;

    public EventService(EventRepository eventRepository) {
        this.eventRepository = eventRepository;
    }

    @Transactional
    public EventResponse create(CreateEventRequest request) {
        Event event = new Event(
                request.name().trim(),
                normalizeDescription(request.description())
        );

        Event savedEvent = eventRepository.saveAndFlush(event);

        return EventResponse.from(savedEvent);
    }

    @Transactional(readOnly = true)
    public EventResponse get(UUID eventId) {
        Event event = findEvent(eventId);
        return EventResponse.from(event);
    }

    @Transactional(readOnly = true)
    public PageResponse<EventResponse> list(
            int page,
            int size,
            EventStatus status
    ) {
        PageRequest pageRequest = PageRequest.of(
                page,
                size,
                Sort.by(Sort.Direction.DESC, "createdAt")
                        .and(Sort.by(Sort.Direction.DESC, "id"))
        );

        Page<Event> events = status == null
                ? eventRepository.findAll(pageRequest)
                : eventRepository.findByStatus(status, pageRequest);

        Page<EventResponse> responses = events.map(EventResponse::from);

        return PageResponse.from(responses);
    }

    @Transactional
    public EventResponse update(
            UUID eventId,
            UpdateEventRequest request
    ) {
        Event event = findEvent(eventId);

        String updatedName = request.name() == null
                ? event.getName()
                : request.name().trim();

        String updatedDescription = request.description() == null
                ? event.getDescription()
                : normalizeDescription(request.description());

        event.updateDetails(updatedName, updatedDescription);

        if (request.status() != null) {
            event.changeStatus(request.status());
        }

        eventRepository.flush();

        return EventResponse.from(event);
    }

    private Event findEvent(UUID eventId) {
        return eventRepository.findById(eventId)
                .orElseThrow(() -> new EventNotFoundException(eventId));
    }

    private String normalizeDescription(String description) {
        if (description == null) {
            return null;
        }

        String trimmed = description.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}