package com.slotforge.api.event;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateEventRequest(

        @Pattern(
                regexp = ".*\\S.*",
                message = "Event name must not be blank"
        )
        @Size(max = 200, message = "Event name must not exceed 200 characters")
        String name,

        @Size(max = 5000, message = "Description must not exceed 5000 characters")
        String description,

        EventStatus status
) {

    @JsonIgnore
    @AssertTrue(message = "At least one event field must be provided")
    public boolean isUpdatePresent() {
        return name != null
                || description != null
                || status != null;
    }
}