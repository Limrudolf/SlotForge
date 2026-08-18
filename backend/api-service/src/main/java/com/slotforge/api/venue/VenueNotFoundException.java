package com.slotforge.api.venue;

import java.util.UUID;

public class VenueNotFoundException extends RuntimeException {

    public VenueNotFoundException(UUID venueId) {
        super("Venue not found: " + venueId);
    }
}
