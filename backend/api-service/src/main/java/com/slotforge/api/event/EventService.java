package com.slotforge.api.event;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.slotforge.api.audit.AuditAction;
import com.slotforge.api.audit.AuditEntityType;
import com.slotforge.api.audit.AuditService;
import com.slotforge.api.common.PageResponse;
import com.slotforge.api.security.CurrentActor;
import com.slotforge.api.security.CurrentActorProvider;
import com.slotforge.api.user.AuthenticatedAccountUnavailableException;
import com.slotforge.api.user.UserAccount;
import com.slotforge.api.user.UserAccountRepository;

@Service
public class EventService {

    private final EventRepository eventRepository;
    private final UserAccountRepository userAccountRepository;
    private final CurrentActorProvider currentActorProvider;
    private final EventAuthorizationService eventAuthorizationService;
    private final AuditService auditService;

    public EventService(
            EventRepository eventRepository,
            UserAccountRepository userAccountRepository,
            CurrentActorProvider currentActorProvider,
            EventAuthorizationService eventAuthorizationService,
            AuditService auditService
    ) {
        this.eventRepository = eventRepository;
        this.userAccountRepository = userAccountRepository;
        this.currentActorProvider = currentActorProvider;
        this.eventAuthorizationService = eventAuthorizationService;
        this.auditService = auditService;
    }

    @Transactional
    public EventResponse create(CreateEventRequest request) {
        CurrentActor actor = currentActorProvider.currentActor();
        UserAccount organizer = userAccountRepository
                .findById(actor.userId())
                .filter(UserAccount::isActive)
                .orElseThrow(
                        AuthenticatedAccountUnavailableException::new
                );

        Event event = new Event(
                request.name().trim(),
                normalizeDescription(request.description()),
                organizer
        );

        Event savedEvent = eventRepository.saveAndFlush(event);

        auditService.record(
                actor,
                AuditAction.EVENT_CREATED,
                AuditEntityType.EVENT,
                savedEvent.getId(),
                java.util.Map.of(
                        "name", savedEvent.getName(),
                        "status", savedEvent.getStatus().name()
                )
        );

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
        CurrentActor actor = currentActorProvider.currentActor();
        eventAuthorizationService.requireOwnerOrAdmin(event, actor);
        EventStatus previousStatus = event.getStatus();

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

        auditService.record(
                actor,
                AuditAction.EVENT_UPDATED,
                AuditEntityType.EVENT,
                event.getId(),
                java.util.Map.of(
                        "name", event.getName(),
                        "previousStatus", previousStatus.name(),
                        "status", event.getStatus().name()
                )
        );

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
