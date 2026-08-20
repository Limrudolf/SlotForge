package com.slotforge.api.payment;

import java.time.Instant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record FakePaymentCallbackRequest(
        @NotBlank(message = "Payment event ID is required")
        @Size(
                max = 255,
                message = "Payment event ID must not exceed 255 characters"
        )
        String eventId,

        @NotNull(message = "Payment event occurrence time is required")
        Instant occurredAt
) {
}
