package com.slotforge.api.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LogoutRequest(

        @NotBlank(message = "Refresh token is required")
        @Size(
                max = 512,
                message = "Refresh token must not exceed 512 characters"
        )
        String refreshToken
) {
}
