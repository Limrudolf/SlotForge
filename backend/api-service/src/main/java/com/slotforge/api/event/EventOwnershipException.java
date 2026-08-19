package com.slotforge.api.event;

public class EventOwnershipException extends RuntimeException {

    public EventOwnershipException() {
        super("You do not have permission to modify this event");
    }
}
