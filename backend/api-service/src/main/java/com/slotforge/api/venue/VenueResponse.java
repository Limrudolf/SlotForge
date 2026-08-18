package com.slotforge.api.venue;

import java.time.Instant;
import java.util.UUID;

public record VenueResponse(
        UUID id,
        String name,
        String addressLine1,
        String addressLine2,
        String city,
        String region,
        String postalCode,
        String countryCode,
        Instant createdAt,
        Instant updatedAt,
        long version
) {

    public static VenueResponse from(Venue venue) {
        return new VenueResponse(
                venue.getId(),
                venue.getName(),
                venue.getAddressLine1(),
                venue.getAddressLine2(),
                venue.getCity(),
                venue.getRegion(),
                venue.getPostalCode(),
                venue.getCountryCode(),
                venue.getCreatedAt(),
                venue.getUpdatedAt(),
                venue.getVersion()
        );
    }
}