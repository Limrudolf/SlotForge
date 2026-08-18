package com.slotforge.api.session;

import java.util.UUID;

public class EventSessionNotFoundException extends RuntimeException {

    public EventSessionNotFoundException(UUID sessionId) {
        super("Event session not found: " + sessionId);
    }
}
