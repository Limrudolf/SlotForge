package com.slotforge.api.session;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.slotforge.api.common.validation.ValidTimeZone;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CreateEventSessionRequest(

        @NotNull(message = "Venue ID is required")
        UUID venueId,

        @NotNull(message = "Start time is required")
        OffsetDateTime startTime,

        @NotNull(message = "End time is required")
        OffsetDateTime endTime,

        @NotBlank(message = "Display timezone is required")
        @Size(
                max = 100,
                message = "Display timezone must not exceed 100 characters"
        )
        @ValidTimeZone
        String displayTimezone,

        @Positive(message = "Total capacity must be greater than zero")
        int totalCapacity
) {

    @JsonIgnore
    @AssertTrue(message = "End time must be after start time")
    public boolean isEndTimeAfterStartTime() {
        if (startTime == null || endTime == null) {
            return true;
        }

        return endTime.toInstant().isAfter(startTime.toInstant());
    }
}