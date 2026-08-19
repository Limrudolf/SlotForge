package com.slotforge.api.event;

import org.springframework.stereotype.Component;

import com.slotforge.api.security.CurrentActor;

@Component
public class EventAuthorizationService {

    public void requireOwnerOrAdmin(
            Event event,
            CurrentActor actor
    ) {
        if (actor.isAdmin()) {
            return;
        }

        if (!event.isOwnedBy(actor.userId())) {
            throw new EventOwnershipException();
        }
    }
}
